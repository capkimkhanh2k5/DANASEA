package com.danasea.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class AiChatStorageMigrationIntegrationTest {
    @Container
    static final GenericContainer<?> POSTGRES = new GenericContainer<>(DockerImageName.parse("postgres:17-alpine"))
            .withEnv("POSTGRES_DB", "ai_chat_storage")
            .withEnv("POSTGRES_USER", "test").withEnv("POSTGRES_PASSWORD", "test")
            .withExposedPorts(5432).waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));

    @Test
    void migrationPreservesMessagesAndStoresLongConversationAndToolPayloads() throws Exception {
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getFirstMappedPort() + "/ai_chat_storage";
        Flyway.configure().dataSource(url, "test", "test").target("41").load().migrate();
        UUID conversation = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, "test", "test");
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO ai_conversations(id,locale,started_at,created_at,updated_at) VALUES ('"
                    + conversation + "','vi',now(),now(),now())");
            try (var insert = connection.prepareStatement("INSERT INTO ai_messages(id,conversation_id,role,content,tool_calls,created_at,updated_at) VALUES (?,?,?,?,?,now(),now())")) {
                insert.setObject(1, UUID.randomUUID()); insert.setObject(2, conversation);
                insert.setString(3, "USER"); insert.setString(4, "Original message"); insert.setString(5, null);
                insert.executeUpdate();
                insert.setObject(1, UUID.randomUUID()); insert.setString(4, "a".repeat(1800));
                assertThrows(SQLException.class, insert::executeUpdate);
            }
        }
        Flyway.configure().dataSource(url, "test", "test").load().migrate();
        String content = "Ngữ cảnh tiếng Việt. ".repeat(400);
        String tools = "[{\"id\":\"call-1\",\"name\":\"search_services\",\"arguments\":\"" + "x".repeat(2000) + "\"}]";
        try (var connection = DriverManager.getConnection(url, "test", "test");
             var insert = connection.prepareStatement("INSERT INTO ai_messages(id,conversation_id,role,content,tool_calls,created_at,updated_at) VALUES (?,?,?,?,?,now(),now())")) {
            UUID message = UUID.randomUUID();
            insert.setObject(1, message); insert.setObject(2, conversation); insert.setString(3, "ASSISTANT");
            insert.setString(4, content); insert.setString(5, tools); insert.executeUpdate();
            try (var query = connection.prepareStatement("SELECT content,tool_calls FROM ai_messages WHERE id=?")) {
                query.setObject(1, message);
                try (var rows = query.executeQuery()) {
                    rows.next(); assertEquals(content, rows.getString(1)); assertEquals(tools, rows.getString(2));
                }
            }
            try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT count(*) FROM ai_messages WHERE content='Original message'")) {
                rows.next(); assertEquals(1, rows.getInt(1));
            }
        }
    }
}
