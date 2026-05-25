package com.example.aichat.web.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIT {

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
        jdbcTemplate.update("DELETE FROM model_configs");
        jdbcTemplate.update("DELETE FROM user_settings");
        jdbcTemplate.update("DELETE FROM users");

        // 插入一条启用的内置模型（BuiltInModelInitializer 在 test profile 下不运行）
        jdbcTemplate.update("""
            INSERT INTO model_configs (id, user_id, display_name, provider, api_base_url, api_key, model_name, is_builtin, is_enabled, created_at, updated_at)
            VALUES ('gpt-4o-mini', NULL, 'GPT-4o Mini', 'openai', 'https://api.openai.com', 'enc-key', 'gpt-4o-mini', true, true, NOW(), NOW())
            """);
    }

    @Test
    void register_success() throws Exception {
        String requestBody = """
            {"username":"alice","email":"alice@example.com","password":"alice123"}
            """;

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.accessToken").exists())
            .andExpect(jsonPath("$.data.user.username").value("alice"));

        // 验证数据库
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE username = ?", Long.class, "alice");
        assertThat(count).isEqualTo(1);

        String passwordHash = jdbcTemplate.queryForObject(
            "SELECT password_hash FROM users WHERE username = ?", String.class, "alice");
        assertThat(passwordHash).startsWith("$2a$");

        Long settingsCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_settings WHERE user_id = (SELECT id FROM users WHERE username = ?)",
            Long.class, "alice");
        assertThat(settingsCount).isEqualTo(1);

        // 验证 default_model_id 已设置为第一个启用的内置模型
        String defaultModelId = jdbcTemplate.queryForObject(
            "SELECT default_model_id FROM user_settings WHERE user_id = (SELECT id FROM users WHERE username = ?)",
            String.class, "alice");
        assertThat(defaultModelId).isEqualTo("gpt-4o-mini");
    }

    @Test
    void register_duplicateUsername_400() throws Exception {
        String requestBody = """
            {"username":"bob","password":"bob123"}
            """;

        // 第一次注册成功
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk());

        // 第二次注册同样用户名，返回 400
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PARAM_ERROR"));
    }

    @Test
    void login_success() throws Exception {
        // 先注册
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"carol","password":"carol123"}
                    """))
            .andExpect(status().isOk());

        // 再登录
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"carol","password":"carol123"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.accessToken").exists());
    }

    @Test
    void login_wrongPassword_401() throws Exception {
        // 先注册
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"dave","password":"dave123"}
                    """))
            .andExpect(status().isOk());

        // 错误密码登录
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"dave","password":"wrongpass"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void accessProtected_withToken_200() throws Exception {
        // 注册获取 token
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"eve","password":"eve123"}
                    """))
            .andExpect(status().isOk())
            .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseJson)
            .path("data").path("accessToken").asText();

        // 用 token 访问受保护接口
        mockMvc.perform(get("/api/ping")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.message").value("pong"));
    }

    @Test
    void accessProtected_withoutToken_401() throws Exception {
        mockMvc.perform(get("/api/ping"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
