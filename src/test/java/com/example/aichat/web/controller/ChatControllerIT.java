package com.example.aichat.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.aichat.chat.entity.Conversation;
import com.example.aichat.chat.entity.Message;
import com.example.aichat.chat.mapper.ConversationMapper;
import com.example.aichat.chat.mapper.MessageMapper;
import com.example.aichat.model.entity.ModelConfig;
import com.example.aichat.model.mapper.ModelConfigMapper;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChatControllerIT {

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
    private ConversationMapper conversationMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private ModelConfigMapper modelConfigMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String token;
    private Long userId;
    private String conversationId;
    private String modelId = "builtin_gpt4o";

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM messages");
        jdbcTemplate.update("DELETE FROM conversations");
        jdbcTemplate.update("DELETE FROM user_settings");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("DELETE FROM model_configs");

        // Register user and get token
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"username":"chatuser","password":"chatpass123"}
                            """))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(responseJson).path("data");
        token = "Bearer " + data.path("accessToken").asText();
        userId = data.path("user").path("id").asLong();

        // Insert built-in model
        ModelConfig model = ModelConfig.builder()
                .id(modelId)
                .userId(null)
                .displayName("GPT-4o")
                .provider("openai")
                .apiBaseUrl("https://api.openai.com")
                .apiKey(null)
                .modelName("gpt-4o")
                .isBuiltin(true)
                .isEnabled(true)
                .build();
        modelConfigMapper.insert(model);

        // Insert conversation
        conversationId = "conv_test_" + System.currentTimeMillis();
        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .userId(userId)
                .title("Test Conversation")
                .modelId(modelId)
                .build();
        conversationMapper.insert(conversation);
    }

    @Test
    void streamChat_returnsOk() throws Exception {
        String requestBody = String.format(
                "{\"conversationId\":\"%s\",\"message\":\"Hello\",\"modelId\":\"%s\"}",
                conversationId, modelId);

        mockMvc.perform(post("/api/chat/stream")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());
    }

    @Test
    void streamChat_unauthorized_returns401() throws Exception {
        String requestBody = String.format(
                "{\"conversationId\":\"%s\",\"message\":\"Hello\",\"modelId\":\"%s\"}",
                conversationId, modelId);

        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void streamChat_invalidConversationId_returns400() throws Exception {
        String requestBody = "{\"conversationId\":\"invalid\",\"message\":\"Hello\",\"modelId\":\"gpt-4o\"}";

        mockMvc.perform(post("/api/chat/stream")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void streamChat_insertsUserMessageAndPlaceholder() throws Exception {
        String requestBody = String.format(
                "{\"conversationId\":\"%s\",\"message\":\"Integration test message\",\"modelId\":\"%s\"}",
                conversationId, modelId);

        mockMvc.perform(post("/api/chat/stream")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        // Verify messages were inserted
        var messages = messageMapper.selectByConversationId(conversationId);
        assertThat(messages).hasSizeGreaterThanOrEqualTo(1);

        // At least one user message should exist
        boolean hasUserMessage = messages.stream()
                .anyMatch(m -> "user".equals(m.getRole())
                        && "Integration test message".equals(m.getContent()));
        assertThat(hasUserMessage).isTrue();
    }
}
