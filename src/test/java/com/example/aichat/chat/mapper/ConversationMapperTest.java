package com.example.aichat.chat.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aichat.chat.entity.Conversation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class ConversationMapperTest {

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
    private ConversationMapper conversationMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM messages");
        jdbcTemplate.update("DELETE FROM conversations");
        jdbcTemplate.update("DELETE FROM users");

        jdbcTemplate.update("""
            INSERT INTO users (id, username, password_hash, created_at, updated_at)
            VALUES (1, 'testuser', 'hash', NOW(), NOW())
            """);
    }

    @Test
    void testInsertAndSelect() {
        Conversation conv = Conversation.builder()
                .id("conv_test1")
                .userId(1L)
                .title("Test Conversation")
                .modelId("gpt-4o")
                .temperature(new BigDecimal("0.7"))
                .maxTokens(2048)
                .topP(new BigDecimal("1.0"))
                .build();

        conversationMapper.insert(conv);

        Conversation found = conversationMapper.selectById("conv_test1");
        assertThat(found).isNotNull();
        assertThat(found.getTitle()).isEqualTo("Test Conversation");
        assertThat(found.getUserId()).isEqualTo(1L);
        assertThat(found.getModelId()).isEqualTo("gpt-4o");
    }

    @Test
    void testSelectByUserIdPage() {
        // Insert 3 conversations
        for (int i = 1; i <= 3; i++) {
            Conversation conv = Conversation.builder()
                    .id("conv_page" + i)
                    .userId(1L)
                    .title("Conv " + i)
                    .modelId("gpt-4o")
                    .build();
            conversationMapper.insert(conv);
        }

        // Insert a deleted conversation (should be filtered)
        Conversation deleted = Conversation.builder()
                .id("conv_deleted")
                .userId(1L)
                .title("Deleted")
                .modelId("gpt-4o")
                .deletedAt(Instant.now())
                .build();
        conversationMapper.insert(deleted);

        // Insert user 2 and their conversation
        jdbcTemplate.update("""
            INSERT INTO users (id, username, password_hash, created_at, updated_at)
            VALUES (2, 'otheruser', 'hash', NOW(), NOW())
            """);
        Conversation other = Conversation.builder()
                .id("conv_other")
                .userId(2L)
                .title("Other User")
                .modelId("gpt-4o")
                .build();
        conversationMapper.insert(other);

        Page<Conversation> page = new Page<>(1, 10);
        var result = conversationMapper.selectByUserIdPage(page, 1L);

        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getRecords()).hasSize(3);
        // Verify deleted conversation is not included
        assertThat(result.getRecords())
                .noneMatch(c -> c.getId().equals("conv_deleted"));
        // Verify other user's conversation is not included
        assertThat(result.getRecords())
                .noneMatch(c -> c.getId().equals("conv_other"));
    }

    @Test
    void testSoftDelete() {
        Conversation conv = Conversation.builder()
                .id("conv_softdel")
                .userId(1L)
                .title("To Delete")
                .modelId("gpt-4o")
                .build();
        conversationMapper.insert(conv);

        Instant now = Instant.now();
        int rows = conversationMapper.softDelete("conv_softdel", 1L, now);

        assertThat(rows).isEqualTo(1);

        Conversation found = conversationMapper.selectById("conv_softdel");
        assertThat(found.getDeletedAt()).isNotNull();
    }

    @Test
    void testSoftDelete_wrongUser_returnsZero() {
        Conversation conv = Conversation.builder()
                .id("conv_wronguser")
                .userId(1L)
                .title("Mine")
                .modelId("gpt-4o")
                .build();
        conversationMapper.insert(conv);

        int rows = conversationMapper.softDelete("conv_wronguser", 2L, Instant.now());
        assertThat(rows).isEqualTo(0);
    }

    @Test
    void testSelectByIdAndUserId() {
        Conversation conv = Conversation.builder()
                .id("conv_byid")
                .userId(1L)
                .title("My Conv")
                .modelId("gpt-4o")
                .build();
        conversationMapper.insert(conv);

        Conversation found = conversationMapper.selectByIdAndUserId("conv_byid", 1L);
        assertThat(found).isNotNull();
        assertThat(found.getTitle()).isEqualTo("My Conv");

        // Wrong user
        assertThat(conversationMapper.selectByIdAndUserId("conv_byid", 2L)).isNull();

        // After soft delete
        conversationMapper.softDelete("conv_byid", 1L, Instant.now());
        assertThat(conversationMapper.selectByIdAndUserId("conv_byid", 1L)).isNull();
    }

    @Test
    void testIdIsStringType() {
        Conversation conv = Conversation.builder()
                .id("conv_string_test")
                .userId(1L)
                .title("String ID Test")
                .modelId("gpt-4o")
                .build();
        conversationMapper.insert(conv);

        Conversation found = conversationMapper.selectById("conv_string_test");
        assertThat(found).isNotNull();
        assertThat(found.getId()).isInstanceOf(String.class);
        assertThat(found.getId()).startsWith("conv_");
    }

    @Test
    void testAutoFillCreatedAtUpdatedAt() {
        Conversation conv = Conversation.builder()
                .id("conv_autofill")
                .userId(1L)
                .title("Auto Fill")
                .modelId("gpt-4o")
                .build();
        conversationMapper.insert(conv);

        Conversation found = conversationMapper.selectById("conv_autofill");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }
}
