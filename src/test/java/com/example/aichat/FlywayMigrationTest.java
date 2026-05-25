package com.example.aichat;

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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTest {

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
    private JdbcTemplate jdbcTemplate;

    @Test
    void allMigrationsApplied() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT version, description, success FROM flyway_schema_history ORDER BY version"
        );

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).get("version")).isEqualTo("1");
        assertThat(rows.get(0).get("description")).isEqualTo("init schema");
        assertThat(rows.get(0).get("success")).isEqualTo(true);
        assertThat(rows.get(1).get("version")).isEqualTo("2");
        assertThat(rows.get(1).get("description")).isEqualTo("alter user settings add default model");
        assertThat(rows.get(1).get("success")).isEqualTo(true);
        assertThat(rows.get(2).get("version")).isEqualTo("3");
        assertThat(rows.get(2).get("description")).isEqualTo("alter messages add status");
        assertThat(rows.get(2).get("success")).isEqualTo(true);
    }

    @Test
    void allTablesExist() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = ?",
                String.class,
                mysql.getDatabaseName()
        );

        assertThat(tables)
                .contains("users", "conversations", "messages",
                        "model_configs", "user_settings", "flyway_schema_history");
    }

    @Test
    void userSettingsHasDefaultModelIdColumn() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = 'user_settings'",
                String.class,
                mysql.getDatabaseName()
        );

        assertThat(columns).contains("default_model_id");
    }

    @Test
    void messagesHasStatusColumn() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = 'messages'",
                String.class,
                mysql.getDatabaseName()
        );

        assertThat(columns).contains("status");
    }
}
