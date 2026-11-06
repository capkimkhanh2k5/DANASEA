package com.danasea.backend.configs;

import java.sql.DriverManager;
import java.sql.SQLException;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public abstract class AbstractPostgresIntegrationTest extends AbstractExternalInfrastructureIntegrationTest {

    protected static final String SHARED_DATABASE = "testdb";
    private static final String POSTGRES_USER = "test";
    private static final String POSTGRES_PASSWORD = "test";

    protected static final GenericContainer<?> postgres = new GenericContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withEnv("POSTGRES_DB", SHARED_DATABASE)
            .withEnv("POSTGRES_USER", POSTGRES_USER)
            .withEnv("POSTGRES_PASSWORD", POSTGRES_PASSWORD)
            .withExposedPorts(5432)
            .waitingFor(Wait.forListeningPort());

    static {
        postgres.start();
    }

    protected static void createPostgresDatabase(String database) {
        try (var connection = DriverManager.getConnection(postgresJdbcUrl(SHARED_DATABASE), POSTGRES_USER, POSTGRES_PASSWORD);
                var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + statement.enquoteIdentifier(database, false));
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create integration test database " + database, e);
        }
    }

    protected static void configurePostgresProperties(DynamicPropertyRegistry registry, String database) {
        String dbUrl = postgresJdbcUrl(database);

        registry.add("spring.datasource.url", () -> dbUrl);
        registry.add("spring.datasource.username", () -> POSTGRES_USER);
        registry.add("spring.datasource.password", () -> POSTGRES_PASSWORD);
        registry.add("app.scheduler.enabled", () -> "false");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "5");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "1");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");

        registry.add("spring.flyway.url", () -> dbUrl);
        registry.add("spring.flyway.user", () -> POSTGRES_USER);
        registry.add("spring.flyway.password", () -> POSTGRES_PASSWORD);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        registry.add("app.jwt.secret", () -> "test-secret-key-that-is-at-least-256-bits-long-for-danasea-rbac-testing-purposes");
        registry.add("app.checkin.qr-secret", () -> "test-checkin-qr-secret-that-is-at-least-256-bits-long");
        registry.add("spring.mail.username", () -> "test@example.com");
        registry.add("spring.mail.password", () -> "testpassword");
    }

    private static String postgresJdbcUrl(String database) {
        return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getFirstMappedPort() + "/" + database;
    }
}
