package com.danasea.backend.configs;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;

import com.rabbitmq.client.Channel;

@SpringBootTest
@ActiveProfiles("test")
class ExternalInfrastructureOwnershipIntegrationTest extends AbstractExternalInfrastructureIntegrationTest {

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired(required = false)
    private ConnectionFactory rabbitConnectionFactory;

    @Autowired(required = false)
    private DataSource dataSource;

    @Test
    void externalInfrastructureIsOwnedByFixtureAndNotDefaultLocalhost() throws Exception {
        assertThat(redisConnectionFactory)
                .as("RedisConnectionFactory must be present in application context")
                .isNotNull();
        assertThat(rabbitConnectionFactory)
                .as("RabbitMQ ConnectionFactory must be present in application context")
                .isNotNull();

        assertThat(redis.isRunning()).isTrue();
        assertThat(rabbit.isRunning()).isTrue();

        int redisPort = redis.getFirstMappedPort();
        int rabbitPort = rabbit.getFirstMappedPort();

        assertThat(redisPort).isNotEqualTo(6379);
        assertThat(rabbitPort).isNotEqualTo(5672);

        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            assertThat(connection.ping()).isEqualTo("PONG");
        }

        assertThat(rabbitConnectionFactory.getHost()).isEqualTo(rabbit.getHost());
        assertThat(rabbitConnectionFactory.getPort()).isEqualTo(rabbitPort);

        try (Connection amqpConnection = rabbitConnectionFactory.createConnection();
             Channel channel = amqpConnection.createChannel(false)) {
            assertThat(channel.isOpen()).isTrue();
        }

        if (dataSource != null) {
            try (java.sql.Connection conn = dataSource.getConnection()) {
                assertThat(conn.getMetaData().getURL()).contains("h2:mem:");
            }
        }
    }
}
