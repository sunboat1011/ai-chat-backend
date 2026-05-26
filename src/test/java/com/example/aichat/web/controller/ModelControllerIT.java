package com.example.aichat.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModelControllerIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("aichat")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 清掉模型和用户数据
        jdbcTemplate.update("DELETE FROM model_configs");
        jdbcTemplate.update("DELETE FROM user_settings");
        jdbcTemplate.update("DELETE FROM users");

        // 手动插入一条内置模型（BuiltInModelInitializer 在 test profile 下不运行）
        jdbcTemplate.update("""
            INSERT INTO model_configs (id, user_id, display_name, provider, api_base_url, api_key, model_name, is_builtin, is_enabled, created_at, updated_at)
            VALUES ('gpt-4o', NULL, 'GPT-4o', 'openai', 'https://api.openai.com', 'enc-key', 'gpt-4o', true, true, NOW(), NOW())
            """);
    }

    private String obtainToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"modeluser","password":"modelpass123"}
                    """))
            .andExpect(status().isOk())
            .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        return objectMapper.readTree(responseJson)
            .path("data").path("accessToken").asText();
    }

    @Test
    void listModels_emptyInitially() throws Exception {
        String token = obtainToken();

        mockMvc.perform(get("/api/models")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void createCustomModel_success() throws Exception {
        String token = obtainToken();

        String requestBody = """
            {"displayName":"My Custom GPT","modelId":"my-custom-gpt","apiBaseUrl":"https://api.example.com","apiKey":"sk-test123","modelName":"gpt-4o","provider":"openai"}
            """;

        MvcResult result = mockMvc.perform(post("/api/models/custom")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.id").exists())
            .andExpect(jsonPath("$.data.id").value("my-custom-gpt"))
            .andExpect(jsonPath("$.data.displayName").value("My Custom GPT"))
            .andExpect(jsonPath("$.data.isBuiltin").value(false))
            .andExpect(jsonPath("$.data.isCustom").value(true))
            // apiKey 绝不能出现在响应中
            .andExpect(jsonPath("$.data.apiKey").doesNotExist())
            .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(responseJson).path("data");
        String modelId = data.path("id").asText();

        // 验证数据库：api_key 为加密后的密文
        String apiKeyInDb = jdbcTemplate.queryForObject(
            "SELECT api_key FROM model_configs WHERE id = ?", String.class, modelId);
        assertThat(apiKeyInDb).isNotNull();
        assertThat(apiKeyInDb).isNotEqualTo("sk-test123"); // 不是明文

        // list 接口能查到
        mockMvc.perform(get("/api/models")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.id == '" + modelId + "')]").exists());
    }

    @Test
    void updateCustomModel_success() throws Exception {
        String token = obtainToken();

        // 先创建
        MvcResult createResult = mockMvc.perform(post("/api/models/custom")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"displayName":"Old Name","modelId":"my-update-gpt","apiBaseUrl":"https://old.com","apiKey":"sk-old","modelName":"gpt-4o","provider":"openai"}
                    """))
            .andExpect(status().isOk())
            .andReturn();

        String modelId = objectMapper.readTree(createResult.getResponse().getContentAsString())
            .path("data").path("id").asText();

        // 再更新
        mockMvc.perform(put("/api/models/custom/{id}", modelId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"displayName":"New Name","apiBaseUrl":"https://new.com","apiKey":"sk-new","modelName":"gpt-4o","provider":"openai"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.displayName").value("New Name"))
            .andExpect(jsonPath("$.data.apiBaseUrl").value("https://new.com"));
    }

    @Test
    void deleteCustomModel_success() throws Exception {
        String token = obtainToken();

        // 先创建
        MvcResult createResult = mockMvc.perform(post("/api/models/custom")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"displayName":"ToDelete","modelId":"my-delete-gpt","apiBaseUrl":"https://del.com","apiKey":"sk-del","modelName":"gpt-4o","provider":"openai"}
                    """))
            .andExpect(status().isOk())
            .andReturn();

        String modelId = objectMapper.readTree(createResult.getResponse().getContentAsString())
            .path("data").path("id").asText();

        // 删除
        mockMvc.perform(delete("/api/models/custom/{id}", modelId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"));

        // 验证数据库已删除
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM model_configs WHERE id = ?", Integer.class, modelId);
        assertThat(count).isZero();
    }

    @Test
    void deleteBuiltinModel_forbidden() throws Exception {
        String token = obtainToken();

        // 尝试删除内置模型
        mockMvc.perform(delete("/api/models/custom/{id}", "gpt-4o")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void createCustomModel_withoutApiKey_allowsNullKey() throws Exception {
        String token = obtainToken();

        mockMvc.perform(post("/api/models/custom")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"displayName":"Local Ollama","modelId":"llama3","apiBaseUrl":"http://localhost:11434","modelName":"llama3","provider":"ollama"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    void updateNonExistentModel_notFound() throws Exception {
        String token = obtainToken();

        mockMvc.perform(put("/api/models/custom/{id}", "custom_nonexistent")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"displayName":"New","apiBaseUrl":"https://new.com","modelName":"gpt-4","provider":"openai"}
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("MODEL_NOT_FOUND"));
    }
}
