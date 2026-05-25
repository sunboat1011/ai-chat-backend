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
class ConversationControllerIT {

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
        jdbcTemplate.update("DELETE FROM messages");
        jdbcTemplate.update("DELETE FROM conversations");
        jdbcTemplate.update("DELETE FROM user_settings");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("DELETE FROM model_configs");

        // Insert a built-in model for default model resolution
        jdbcTemplate.update("""
            INSERT INTO model_configs (id, user_id, display_name, provider, api_base_url, api_key, model_name, is_builtin, is_enabled, created_at, updated_at)
            VALUES ('gpt-4o-mini', NULL, 'GPT-4o Mini', 'openai', 'https://api.openai.com', 'enc-key', 'gpt-4o-mini', true, true, NOW(), NOW())
            """);
    }

    private String obtainToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"username":"convuser","password":"convpass123"}
                            """))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        return objectMapper.readTree(responseJson)
                .path("data").path("accessToken").asText();
    }

    @Test
    void createConversation_success() throws Exception {
        String token = obtainToken();

        mockMvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title":"测试会话","modelId":"gpt-4o"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.title").value("测试会话"))
                .andExpect(jsonPath("$.data.modelId").value("gpt-4o"));
    }

    @Test
    void createConversation_withoutModelId_usesDefault() throws Exception {
        String token = obtainToken();

        MvcResult result = mockMvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title":"Default Model Test"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelId").value("gpt-4o-mini"))
                .andReturn();
    }

    @Test
    void listConversations_pagination() throws Exception {
        String token = obtainToken();

        // Create 3 conversations
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/conversations")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Conv " + i + "\",\"modelId\":\"gpt-4o\"}"))
                    .andExpect(status().isOk());
        }

        // List with page=0, size=2
        mockMvc.perform(get("/api/conversations?page=0&size=2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.hasNext").value(true));

        // Page 1 should have 1 item
        mockMvc.perform(get("/api/conversations?page=1&size=2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void getConversationDetail_success() throws Exception {
        String token = obtainToken();

        // Create conversation
        MvcResult createResult = mockMvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title":"Detail Test","modelId":"gpt-4o"}
                            """))
                .andExpect(status().isOk())
                .andReturn();

        String convId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // Insert messages
        jdbcTemplate.update("""
            INSERT INTO messages (id, conversation_id, role, content, status, is_deleted, created_at, updated_at)
            VALUES ('msg_1', ?, 'user', 'Hello', 'done', false, NOW(), NOW())
            """, convId);
        jdbcTemplate.update("""
            INSERT INTO messages (id, conversation_id, role, content, status, is_deleted, created_at, updated_at)
            VALUES ('msg_2', ?, 'assistant', 'Hi there', 'done', false, NOW(), NOW())
            """, convId);

        mockMvc.perform(get("/api/conversations/{id}", convId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.conversation.id").value(convId))
                .andExpect(jsonPath("$.data.conversation.title").value("Detail Test"))
                .andExpect(jsonPath("$.data.messages.content").isArray())
                .andExpect(jsonPath("$.data.messages.totalElements").value(2));
    }

    @Test
    void getConversationDetail_notFound() throws Exception {
        String token = obtainToken();

        mockMvc.perform(get("/api/conversations/nonexistent")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void updateConversation_success() throws Exception {
        String token = obtainToken();

        MvcResult createResult = mockMvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title":"Old Title","modelId":"gpt-4o"}
                            """))
                .andExpect(status().isOk())
                .andReturn();

        String convId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(put("/api/conversations/{id}", convId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title":"New Title","systemPrompt":"New prompt"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.title").value("New Title"))
                .andExpect(jsonPath("$.data.systemPrompt").value("New prompt"));
    }

    @Test
    void deleteConversation_softDelete() throws Exception {
        String token = obtainToken();

        MvcResult createResult = mockMvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title":"To Delete","modelId":"gpt-4o"}
                            """))
                .andExpect(status().isOk())
                .andReturn();

        String convId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(delete("/api/conversations/{id}", convId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        // Verify soft delete in DB
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM conversations WHERE id = ? AND deleted_at IS NULL",
                Integer.class, convId);
        assertThat(count).isZero();

        // Record still exists with deleted_at set
        Integer totalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM conversations WHERE id = ?",
                Integer.class, convId);
        assertThat(totalCount).isEqualTo(1);
    }

    @Test
    void deleteConversation_notFound() throws Exception {
        String token = obtainToken();

        mockMvc.perform(delete("/api/conversations/nonexistent")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void accessOtherUserConversation_forbidden() throws Exception {
        // Register user1
        String token1 = obtainToken();

        // Create conversation for user1
        MvcResult createResult = mockMvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title":"Private","modelId":"gpt-4o"}
                            """))
                .andExpect(status().isOk())
                .andReturn();

        String convId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // Register user2
        MvcResult result2 = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"username":"user2","password":"pass123456"}
                            """))
                .andExpect(status().isOk())
                .andReturn();
        String token2 = objectMapper.readTree(result2.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // user2 tries to access user1's conversation
        mockMvc.perform(get("/api/conversations/{id}", convId)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
