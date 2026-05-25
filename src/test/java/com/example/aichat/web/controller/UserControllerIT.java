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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerIT {

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

        jdbcTemplate.update("""
            INSERT INTO model_configs (id, user_id, display_name, provider, api_base_url, api_key, model_name, is_builtin, is_enabled, created_at, updated_at)
            VALUES ('gpt-4o-mini', NULL, 'GPT-4o Mini', 'openai', 'https://api.openai.com', 'enc-key', 'gpt-4o-mini', true, true, NOW(), NOW())
            """);
    }

    private String obtainToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"username":"userctrl","password":"userpass123"}
                            """))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        return objectMapper.readTree(responseJson)
                .path("data").path("accessToken").asText();
    }

    @Test
    void getCurrentUser_success() throws Exception {
        String token = obtainToken();

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.username").value("userctrl"))
                .andExpect(jsonPath("$.data.id").exists());
    }

    @Test
    void getSettings_success() throws Exception {
        String token = obtainToken();

        mockMvc.perform(get("/api/users/me/settings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.theme").exists())
                .andExpect(jsonPath("$.data.defaultModelId").exists());
    }

    @Test
    void updateSettings_success() throws Exception {
        String token = obtainToken();

        mockMvc.perform(put("/api/users/me/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"theme":"dark","accentColor":"#ff0000","defaultModelId":"gpt-4o"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.theme").value("dark"))
                .andExpect(jsonPath("$.data.accentColor").value("#ff0000"))
                .andExpect(jsonPath("$.data.defaultModelId").value("gpt-4o"));

        // Verify DB
        String theme = jdbcTemplate.queryForObject(
                "SELECT theme FROM user_settings WHERE user_id = (SELECT id FROM users WHERE username = ?)",
                String.class, "userctrl");
        assertThat(theme).isEqualTo("dark");
    }

    @Test
    void migrate_success() throws Exception {
        String token = obtainToken();

        String requestBody = """
            {
              "conversations": [
                {
                  "id": "conv_migrate1",
                  "title": "Migrated Conversation",
                  "modelId": "gpt-4o",
                  "temperature": 0.7,
                  "createdAt": 1700000000000,
                  "messages": [
                    {"id": "msg_m1", "role": "user", "content": "Hello", "timestamp": 1700000001000},
                    {"id": "msg_m2", "role": "assistant", "content": "Hi!", "timestamp": 1700000002000}
                  ]
                }
              ],
              "settings": {
                "theme": "dark",
                "defaultModelId": "gpt-4o"
              }
            }
            """;

        mockMvc.perform(post("/api/users/migrate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.importedConversations").value(1))
                .andExpect(jsonPath("$.data.importedMessages").value(2));

        // Verify DB
        Integer convCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM conversations WHERE id = ?", Integer.class, "conv_migrate1");
        assertThat(convCount).isEqualTo(1);

        Integer msgCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE conversation_id = ?", Integer.class, "conv_migrate1");
        assertThat(msgCount).isEqualTo(2);

        // Verify settings were updated
        String theme = jdbcTemplate.queryForObject(
                "SELECT theme FROM user_settings WHERE user_id = (SELECT id FROM users WHERE username = ?)",
                String.class, "userctrl");
        assertThat(theme).isEqualTo("dark");
    }

    @Test
    void migrate_skipsEmptyMessages() throws Exception {
        String token = obtainToken();

        String requestBody = """
            {
              "conversations": [
                {
                  "id": "conv_empty_test",
                  "title": "Test",
                  "modelId": "gpt-4o",
                  "messages": [
                    {"id": "msg_valid", "role": "user", "content": "Valid"},
                    {"id": "msg_empty", "role": "user", "content": ""},
                    {"id": "msg_null", "role": "user"}
                  ]
                }
              ]
            }
            """;

        mockMvc.perform(post("/api/users/migrate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.importedConversations").value(1))
                .andExpect(jsonPath("$.data.importedMessages").value(1));
    }

    @Test
    void migrate_truncatesLongTitle() throws Exception {
        String token = obtainToken();

        String longTitle = "a".repeat(250);
        String requestBody = String.format("""
            {
              "conversations": [
                {
                  "id": "conv_long_title",
                  "title": "%s",
                  "modelId": "gpt-4o"
                }
              ]
            }
            """, longTitle);

        mockMvc.perform(post("/api/users/migrate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        String titleInDb = jdbcTemplate.queryForObject(
                "SELECT title FROM conversations WHERE id = ?", String.class, "conv_long_title");
        assertThat(titleInDb).hasSize(200);
    }
}
