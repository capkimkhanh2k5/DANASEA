package com.danasea.backend.configs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.ReactiveKeyCommands;
import org.springframework.data.redis.connection.ReactiveStringCommands;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.SetCondition;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.types.Expiration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import reactor.core.publisher.Mono;

@Testcontainers
class CacheWriteVisibilityIntegrationTest {

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.0-alpine")
            .withExposedPorts(6379);

    private static LettuceConnectionFactory realConnections;
    private Cache cache;

    @BeforeAll
    static void connect() {
        realConnections = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        realConnections.afterPropertiesSet();
        realConnections.start();
    }

    @AfterAll
    static void disconnect() {
        if (realConnections != null) {
            realConnections.destroy();
        }
    }

    @BeforeEach
    void configureCacheWithDelayedReactiveWrites() {
        try (var connection = realConnections.getConnection()) {
            connection.serverCommands().flushDb();
        }

        // Delay only the external async Redis path. Synchronous operations use real Redis.
        var delayedConnection = mock(ReactiveRedisConnection.class);
        var delayedKeys = mock(ReactiveKeyCommands.class);
        var delayedStrings = mock(ReactiveStringCommands.class);
        when(delayedConnection.keyCommands()).thenReturn(delayedKeys);
        when(delayedConnection.stringCommands()).thenReturn(delayedStrings);
        when(delayedConnection.closeLater()).thenReturn(Mono.empty());
        when(delayedKeys.unlink(any(ByteBuffer.class))).thenReturn(Mono.never());
        when(delayedKeys.keys(any(ByteBuffer.class))).thenReturn(Mono.never());
        when(delayedStrings.set(any(ByteBuffer.class), any(ByteBuffer.class),
                any(SetCondition.class), any(Expiration.class))).thenReturn(Mono.never());

        var connections = mock(RedisConnectionFactory.class,
                withSettings().extraInterfaces(ReactiveRedisConnectionFactory.class));
        when(connections.getConnection()).thenAnswer(invocation -> realConnections.getConnection());
        when(((ReactiveRedisConnectionFactory) connections).getReactiveConnection())
                .thenReturn(delayedConnection);

        var manager = new CacheConfig().cacheManager(connections);
        manager.afterPropertiesSet();
        cache = manager.getCache("usersById");
        assertThat(cache).isNotNull();
    }

    @Test
    void userCacheWritesAreVisibleWhenPutReturns() {
        cache.put("account", "locked");

        assertThat(cache.get("account", String.class)).isEqualTo("locked");
    }

    @Test
    void userCacheEvictionIsVisibleWhenEvictReturns() {
        cache.putIfAbsent("account", "active");
        assertThat(cache.get("account", String.class)).isEqualTo("active");

        cache.evict("account");

        assertThat(cache.get("account")).isNull();
    }

    @Test
    void cacheClearCompletesBeforeTheNextRead() {
        cache.putIfAbsent("account", "active");
        assertThat(cache.get("account", String.class)).isEqualTo("active");

        cache.clear();

        assertThat(cache.get("account")).isNull();
    }
}
