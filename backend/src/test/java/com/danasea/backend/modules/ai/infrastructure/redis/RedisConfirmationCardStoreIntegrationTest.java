package com.danasea.backend.modules.ai.infrastructure.redis;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.danasea.backend.modules.ai.domain.models.ConfirmationCard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
class RedisConfirmationCardStoreIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.0-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connections;
    private static StringRedisTemplate redis;
    private RedisConfirmationCardStore writer;
    private RedisConfirmationCardStore independentReader;
    private final Set<String> ownedKeys = new HashSet<>();

    @BeforeAll
    static void connectToRealRedis() {
        connections = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connections.afterPropertiesSet();
        connections.start();
        redis = new StringRedisTemplate(connections);
    }

    @AfterAll
    static void closeConnections() {
        if (connections != null) connections.destroy();
    }

    @BeforeEach
    void setUp() {
        writer = new RedisConfirmationCardStore(redis, new ObjectMapper().findAndRegisterModules());
        independentReader = new RedisConfirmationCardStore(redis, new ObjectMapper().findAndRegisterModules());
    }

    @AfterEach
    void removeOnlyThisCasesKeys() {
        if (!ownedKeys.isEmpty()) redis.delete(ownedKeys);
    }

    @Test
    void pendingCardRoundTripsThroughAnIndependentStoreWithFifteenMinuteRedisTtl() {
        ConfirmationCard card = card(nextId());

        writer.save(card);

        assertThat(redis.opsForValue().get(key(card.getId()))).contains("PENDING");
        assertThat(independentReader.findById(card.getId())).contains(card);
        assertTtl(card.getId(), Duration.ofMinutes(15));
        card.setReason("Caller mutated its original object");
        assertThat(independentReader.findById(card.getId()).orElseThrow().getReason()).isEqualTo("Confirm the selected package");
    }

    @Test
    void confirmedOutcomeOverwritesPendingCardAndExtendsItsRedisTtlToOneDay() {
        ConfirmationCard card = card(nextId());
        writer.save(card);
        assertTtl(card.getId(), Duration.ofMinutes(15));
        card.markConfirmed();
        card.setConfirmedBy(card.getOwnerId());
        card.setConfirmationOutcome(Map.of("bookingId", UUID.randomUUID().toString(), "quantity", 2, "success", true));

        independentReader.save(card);

        assertThat(writer.findById(card.getId())).contains(card);
        assertTtl(card.getId(), Duration.ofDays(1));
    }

    @Test
    void missingAndCorruptCardsReturnEmptyWithoutHidingAnotherValidCard() {
        ConfirmationCard valid = card(nextId());
        writer.save(valid);
        assertThat(independentReader.findById(nextId())).isEmpty();
        for (String malformed : List.of("not-json", "null", "{\"id\":")) {
            String corruptId = nextId();
            redis.opsForValue().set(key(corruptId), malformed, Duration.ofMinutes(1));
            assertThat(independentReader.findById(corruptId)).isEmpty();
        }
        assertThat(independentReader.findById(valid.getId())).contains(valid);
    }

    @Test
    void deletionRemovesOnlyItsCardAndIsIdempotentWithoutReleasingItsProcessingLock() {
        ConfirmationCard deleted = card(nextId());
        ConfirmationCard retained = card(nextId());
        writer.save(deleted);
        writer.save(retained);
        String ownerToken = writer.tryAcquireProcessingLock(deleted.getId(), Duration.ofSeconds(30)).orElseThrow();

        independentReader.delete(deleted.getId());
        independentReader.delete(deleted.getId());

        assertThat(writer.findById(deleted.getId())).isEmpty();
        assertThat(redis.hasKey(key(deleted.getId()))).isFalse();
        assertThat(writer.findById(retained.getId())).contains(retained);
        assertThat(redis.opsForValue().get(lockKey(deleted.getId()))).isEqualTo(ownerToken);
    }

    @Test
    void expiredRedisCardCannotBeReadFromAnInProcessCache() {
        ConfirmationCard card = card(nextId());
        writer.save(card);
        assertThat(redis.expire(key(card.getId()), Duration.ofMillis(150))).isTrue();

        await().pollDelay(Duration.ZERO).pollInterval(Duration.ofMillis(25)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(redis.hasKey(key(card.getId()))).isFalse());

        assertThat(writer.findById(card.getId())).isEmpty();
        assertThat(independentReader.findById(card.getId())).isEmpty();
    }

    @Test
    void simultaneousIndependentStoresHaveExactlyOneLockWinnerAndOnlyThatTokenCanReleaseIt() throws Exception {
        String id = nextId();
        int contenders = 12;
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        List<String> winners = new ArrayList<>();
        try (var workers = Executors.newFixedThreadPool(contenders)) {
            List<java.util.concurrent.Future<Optional<String>>> results = new ArrayList<>();
            for (int i = 0; i < contenders; i++) {
                RedisConfirmationCardStore contender = i % 2 == 0 ? writer : independentReader;
                results.add(workers.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Lock contenders were not released");
                    return contender.tryAcquireProcessingLock(id, Duration.ofSeconds(30));
                }));
            }
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                for (var result : results) result.get(10, TimeUnit.SECONDS).ifPresent(winners::add);
            } finally {
                start.countDown();
            }
        }
        assertThat(winners).hasSize(1);
        String token = winners.getFirst();
        assertThat(UUID.fromString(token)).isNotNull();
        assertThat(redis.opsForValue().get(lockKey(id))).isEqualTo(token);
        assertThat(redis.getExpire(lockKey(id), TimeUnit.MILLISECONDS)).isBetween(1L, 30_000L);
        independentReader.releaseProcessingLock(id, "not-the-owner");
        independentReader.releaseProcessingLock(id, null);
        assertThat(redis.opsForValue().get(lockKey(id))).isEqualTo(token);
        assertThat(independentReader.tryAcquireProcessingLock(id, Duration.ofSeconds(30))).isEmpty();
        writer.releaseProcessingLock(id, token);
        assertThat(redis.hasKey(lockKey(id))).isFalse();
        assertThat(independentReader.tryAcquireProcessingLock(id, Duration.ofSeconds(30)))
                .hasValueSatisfying(next -> assertThat(next).isNotEqualTo(token));
    }

    @Test
    void expiredLeaseCanBeReacquiredAndItsStaleTokenCannotReleaseTheNewOwnersLease() {
        String id = nextId();
        String staleToken = writer.tryAcquireProcessingLock(id, Duration.ofMillis(150)).orElseThrow();

        await().pollDelay(Duration.ZERO).pollInterval(Duration.ofMillis(25)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(redis.hasKey(lockKey(id))).isFalse());
        String currentToken = independentReader.tryAcquireProcessingLock(id, Duration.ofSeconds(30)).orElseThrow();
        writer.releaseProcessingLock(id, staleToken);

        assertThat(currentToken).isNotEqualTo(staleToken);
        assertThat(redis.opsForValue().get(lockKey(id))).isEqualTo(currentToken);
        assertThat(writer.tryAcquireProcessingLock(id, Duration.ofSeconds(30))).isEmpty();
        independentReader.releaseProcessingLock(id, currentToken);
        assertThat(redis.hasKey(lockKey(id))).isFalse();
    }

    private String nextId() {
        String id = UUID.randomUUID().toString();
        ownedKeys.add(key(id));
        ownedKeys.add(lockKey(id));
        return id;
    }

    private String key(String id) {
        return "ai:confirmation:" + id;
    }

    private String lockKey(String id) {
        return key(id) + ":processing";
    }

    private void assertTtl(String id, Duration expected) {
        assertThat(redis.getExpire(key(id), TimeUnit.MILLISECONDS))
                .isBetween(expected.minusSeconds(30).toMillis(), expected.toMillis());
    }

    private ConfirmationCard card(String id) {
        return ConfirmationCard.builder().id(id).conversationId(UUID.randomUUID()).ownerId(UUID.randomUUID())
                .serviceId(UUID.randomUUID()).slotId(UUID.randomUUID()).optionId(UUID.randomUUID())
                .participantsCount(3).quantity(2).participantsPerPackage(List.of(2, 1))
                .price(new BigDecimal("1250000.50")).date("2026-11-12").status(ConfirmationCard.STATUS_PENDING)
                .createdAt(LocalDateTime.of(2026, 11, 2, 9, 30)).reason("Confirm the selected package")
                .locale("vi").retryCount(2).build();
    }
}
