package com.example.aichat.chat.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aichat.chat.entity.Message;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class MessageMapperTest {

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
    private MessageMapper messageMapper;

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

        jdbcTemplate.update("""
            INSERT INTO conversations (id, user_id, title, model_id, created_at, updated_at)
            VALUES ('conv_test1', 1, 'Test Conv', 'gpt-4o', NOW(), NOW())
            """);
    }

    @Test
    void testInsertAndSelect() {
        Message msg = Message.builder()
                .id("msg_test1")
                .conversationId("conv_test1")
                .role("user")
                .content("Hello")
                .status("done")
                .isDeleted(false)
                .build();

        messageMapper.insert(msg);

        Message found = messageMapper.selectById("msg_test1");
        assertThat(found).isNotNull();
        assertThat(found.getContent()).isEqualTo("Hello");
        assertThat(found.getRole()).isEqualTo("user");
        assertThat(found.getStatus()).isEqualTo("done");
    }

    @Test
    void testSelectByConversationIdPage() {
        for (int i = 1; i <= 5; i++) {
            Message msg = Message.builder()
                    .id("msg_page" + i)
                    .conversationId("conv_test1")
                    .role(i % 2 == 0 ? "assistant" : "user")
                    .content("Message " + i)
                    .status("done")
                    .isDeleted(false)
                    .build();
            messageMapper.insert(msg);
        }

        // Insert deleted message (should be filtered)
        Message deleted = Message.builder()
                .id("msg_deleted")
                .conversationId("conv_test1")
                .role("user")
                .content("Deleted")
                .status("done")
                .isDeleted(true)
                .build();
        messageMapper.insert(deleted);

        // Insert other conversation and message
        jdbcTemplate.update("""
            INSERT INTO conversations (id, user_id, title, model_id, created_at, updated_at)
            VALUES ('conv_other', 1, 'Other Conv', 'gpt-4o', NOW(), NOW())
            """);
        Message other = Message.builder()
                .id("msg_other")
                .conversationId("conv_other")
                .role("user")
                .content("Other conv")
                .status("done")
                .isDeleted(false)
                .build();
        messageMapper.insert(other);

        Page<Message> page = new Page<>(1, 10);
        var result = messageMapper.selectByConversationIdPage(page, "conv_test1");

        assertThat(result.getTotal()).isEqualTo(5);
        assertThat(result.getRecords()).hasSize(5);
        assertThat(result.getRecords())
                .noneMatch(m -> m.getId().equals("msg_deleted"))
                .noneMatch(m -> m.getId().equals("msg_other"));
    }

    @Test
    void testSelectByConversationId() {
        for (int i = 1; i <= 3; i++) {
            Message msg = Message.builder()
                    .id("msg_list" + i)
                    .conversationId("conv_test1")
                    .role("user")
                    .content("Msg " + i)
                    .status("done")
                    .isDeleted(false)
                    .build();
            messageMapper.insert(msg);
        }

        List<Message> messages = messageMapper.selectByConversationId("conv_test1");
        assertThat(messages).hasSize(3);
        // Should be ordered by created_at ASC
        assertThat(messages.get(0).getContent()).isEqualTo("Msg 1");
    }

    @Test
    void testSoftDelete() {
        Message msg = Message.builder()
                .id("msg_softdel")
                .conversationId("conv_test1")
                .role("user")
                .content("To delete")
                .status("done")
                .isDeleted(false)
                .build();
        messageMapper.insert(msg);

        int rows = messageMapper.softDelete("msg_softdel", Instant.now());
        assertThat(rows).isEqualTo(1);

        Message found = messageMapper.selectById("msg_softdel");
        assertThat(found.getIsDeleted()).isTrue();
        assertThat(found.getDeletedAt()).isNotNull();
    }

    @Test
    void testRestore_withinWindow() {
        Message msg = Message.builder()
                .id("msg_restore")
                .conversationId("conv_test1")
                .role("user")
                .content("To restore")
                .status("done")
                .isDeleted(false)
                .build();
        messageMapper.insert(msg);

        // Soft delete
        messageMapper.softDelete("msg_restore", Instant.now());

        // Restore within 5 seconds
        Instant fiveSecondsAgo = Instant.now().minus(5, ChronoUnit.SECONDS);
        int rows = messageMapper.restore("msg_restore", fiveSecondsAgo);
        assertThat(rows).isEqualTo(1);

        Message found = messageMapper.selectById("msg_restore");
        assertThat(found.getIsDeleted()).isFalse();
        assertThat(found.getDeletedAt()).isNull();
    }

    @Test
    void testRestore_afterWindow() {
        Message msg = Message.builder()
                .id("msg_norestore")
                .conversationId("conv_test1")
                .role("user")
                .content("No restore")
                .status("done")
                .isDeleted(false)
                .build();
        messageMapper.insert(msg);

        // Soft delete with an old timestamp
        messageMapper.softDelete("msg_norestore", Instant.now().minus(10, ChronoUnit.SECONDS));

        // Try restore (window is 5 seconds ago, but deleted 10 seconds ago)
        Instant fiveSecondsAgo = Instant.now().minus(5, ChronoUnit.SECONDS);
        int rows = messageMapper.restore("msg_norestore", fiveSecondsAgo);
        assertThat(rows).isEqualTo(0);
    }

    @Test
    void testSelectRecentByConversationIdDesc() {
        for (int i = 1; i <= 5; i++) {
            Message msg = Message.builder()
                    .id("msg_recent" + i)
                    .conversationId("conv_test1")
                    .role("user")
                    .content("Msg " + i)
                    .status("done")
                    .isDeleted(false)
                    .build();
            messageMapper.insert(msg);
            // Small delay to ensure different timestamps
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        List<Message> recent = messageMapper.selectRecentByConversationIdDesc("conv_test1", 3);
        assertThat(recent).hasSize(3);
        // DESC order: most recent first
        assertThat(recent.get(0).getContent()).isEqualTo("Msg 5");
        assertThat(recent.get(1).getContent()).isEqualTo("Msg 4");
        assertThat(recent.get(2).getContent()).isEqualTo("Msg 3");
    }

    @Test
    void testIdIsStringType() {
        Message msg = Message.builder()
                .id("msg_string_test")
                .conversationId("conv_test1")
                .role("user")
                .content("String ID")
                .status("done")
                .isDeleted(false)
                .build();
        messageMapper.insert(msg);

        Message found = messageMapper.selectById("msg_string_test");
        assertThat(found).isNotNull();
        assertThat(found.getId()).isInstanceOf(String.class);
        assertThat(found.getId()).startsWith("msg_");
    }

    @Test
    void testAutoFillCreatedAtUpdatedAt() {
        Message msg = Message.builder()
                .id("msg_autofill")
                .conversationId("conv_test1")
                .role("user")
                .content("Auto Fill")
                .status("done")
                .isDeleted(false)
                .build();
        messageMapper.insert(msg);

        Message found = messageMapper.selectById("msg_autofill");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }
}
