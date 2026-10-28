package com.danasea.backend.modules.flashsale;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/** Owned, disposable infrastructure: these destructive tests never accept an external JDBC URL. */
abstract class BaseFlashSaleIntegrationTest {
    private static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:17-alpine")
            .withEnv("POSTGRES_DB", "danasea_flashsale_test")
            .withEnv("POSTGRES_USER", "postgres")
            .withEnv("POSTGRES_PASSWORD", "isolated-test-password")
            .withExposedPorts(5432)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withCommand("redis-server", "--requirepass", "isolated-redis-password")
            .withExposedPorts(6379).waitingFor(Wait.forListeningPort());
    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:postgresql://" + POSTGRES.getHost()
                + ":" + POSTGRES.getMappedPort(5432) + "/danasea_flashsale_test");
        properties.add("spring.datasource.username", () -> "postgres");
        properties.add("spring.datasource.password", () -> "isolated-test-password");
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        properties.add("spring.data.redis.password", () -> "isolated-redis-password");
    }
}
