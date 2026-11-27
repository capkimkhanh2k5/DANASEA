package com.danasea.backend;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.sql.SQLException;

@Testcontainers(disabledWithoutDocker = true)
class ChatMigrationIntegrationTest {
    @Container
    static final GenericContainer<?> POSTGRES = new GenericContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withEnv("POSTGRES_DB", "chat_migration")
            .withEnv("POSTGRES_USER", "test")
            .withEnv("POSTGRES_PASSWORD", "test")
            .withExposedPorts(5432)
            .waitingFor(Wait.forListeningPort());

    private static void waitForPostgres(String url, String user, String password) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        Exception lastEx = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection conn = DriverManager.getConnection(url, user, password);
                 Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1");
                return;
            } catch (Exception e) {
                lastEx = e;
                Thread.sleep(250);
            }
        }
        throw new IllegalStateException("PostgreSQL did not become ready in time: " + (lastEx != null ? lastEx.getMessage() : "timeout"), lastEx);
    }

    @Test
    void v29MergesDuplicatesPreservesMessagesAndBackfillsSequence() throws Exception {
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getFirstMappedPort() + "/chat_migration";
        waitForPostgres(url, "test", "test");
        Flyway.configure().dataSource(url, "test", "test").connectRetries(30).target("28").load().migrate();
        UUID customer = UUID.randomUUID(), vendor = UUID.randomUUID(), order = UUID.randomUUID();
        UUID first = UUID.randomUUID(), duplicate = UUID.randomUUID(), otherVendorConversation = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, "test", "test")) {
            try (var statement = connection.prepareStatement("INSERT INTO conversations(id,customer_id,vendor_id,master_order_id,created_at,updated_at) VALUES (?,?,?,?,?,?)")) {
                for (UUID id : new UUID[]{first, duplicate, otherVendorConversation}) {
                    OffsetDateTime time = OffsetDateTime.parse(id.equals(first) ? "2026-10-01T00:00:00Z" : "2026-10-02T00:00:00Z");
                    statement.setObject(1, id); statement.setObject(2, customer);
                    statement.setObject(3, id.equals(otherVendorConversation) ? UUID.randomUUID() : vendor);
                    statement.setObject(4, order); statement.setObject(5, time); statement.setObject(6, time);
                    statement.executeUpdate();
                }
            }
            try (var statement = connection.prepareStatement("INSERT INTO messages(id,conversation_id,sender_id,content,is_read,created_at,updated_at) VALUES (?,?,?,'Preserved',false,NOW(),NOW())")) {
                for (UUID conversationId : new UUID[]{first, duplicate, duplicate, otherVendorConversation}) {
                    statement.setObject(1, UUID.randomUUID()); statement.setObject(2, conversationId);
                    statement.setObject(3, customer); statement.executeUpdate();
                }
            }
        }
        Flyway flyway = Flyway.configure().dataSource(url, "test", "test").connectRetries(30).target("29").load();
        assertTrue(flyway.migrate().success); flyway.validate();
        assertEquals("29", flyway.info().current().getVersion().getVersion());
        try (var connection = DriverManager.getConnection(url, "test", "test"); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT count(*) FROM conversations")) { assertTrue(rows.next()); assertEquals(2, rows.getInt(1)); }
            try (var rows = statement.executeQuery("SELECT count(*),min(message_sequence),max(message_sequence) FROM messages WHERE conversation_id='" + first + "'")) {
                assertTrue(rows.next()); assertEquals(3, rows.getInt(1)); assertEquals(1, rows.getInt(2)); assertEquals(3, rows.getInt(3));
            }
            try (var rows = statement.executeQuery("SELECT count(*) FROM messages")) { assertTrue(rows.next()); assertEquals(4, rows.getInt(1)); }
            try (var rows = statement.executeQuery("SELECT last_message_sequence,updated_at FROM conversations WHERE id='" + first + "'")) {
                assertTrue(rows.next()); assertEquals(3, rows.getLong(1));
                assertEquals(OffsetDateTime.parse("2026-10-02T00:00:00Z").toInstant(), rows.getObject(2, OffsetDateTime.class).toInstant());
            }
            assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO conversations(id,customer_id,vendor_id,master_order_id,created_at,updated_at) VALUES ('"
                    + UUID.randomUUID() + "','" + customer + "','" + vendor + "','" + order + "',NOW(),NOW())"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO messages(id,conversation_id,message_sequence,created_at,updated_at) VALUES ('"
                    + UUID.randomUUID() + "','" + first + "',3,NOW(),NOW())"));
        }
    }
}
