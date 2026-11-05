package com.danasea.backend.modules.ai.infrastructure.redis;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.danasea.backend.modules.ai.domain.models.ConfirmationCard;
import com.danasea.backend.modules.ai.support.MutableClock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisConfirmationCardStoreTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "CONFIRMED"})
    void nullTemplateFallbackRoundTripsIndependentPendingAndConfirmedCards(String status) {
        RedisConfirmationCardStore store = new RedisConfirmationCardStore(null, mapper);
        ConfirmationCard card = card();
        card.setStatus(status);
        if (ConfirmationCard.STATUS_CONFIRMED.equals(status)) {
            card.setConfirmedBy(card.getOwnerId());
            card.setConfirmationOutcome(Map.of("bookingId", UUID.randomUUID().toString(), "success", true));
        }
        store.save(card);
        ConfirmationCard restored = store.findById(card.getId()).orElseThrow();
        assertThat(restored).isEqualTo(card).isNotSameAs(card);
        restored.setReason("Mutated caller copy");
        assertThat(store.findById(card.getId()).orElseThrow().getReason()).isEqualTo("Confirm package grouping");
        card.setReason("Original caller mutated its own object");
        assertThat(store.findById(card.getId()).orElseThrow().getReason()).isEqualTo("Confirm package grouping");
    }

    @Test
    void fallbackDeletionKeepsOtherCardsAndDoesNotReleaseTheProcessingLease() {
        RedisConfirmationCardStore store = new RedisConfirmationCardStore(null, mapper);
        ConfirmationCard deleted = card();
        ConfirmationCard retained = card();
        store.save(deleted);
        store.save(retained);
        String token = store.tryAcquireProcessingLock(deleted.getId(), Duration.ofSeconds(30)).orElseThrow();

        store.delete(deleted.getId());
        store.delete(deleted.getId());

        assertThat(store.findById(deleted.getId())).isEmpty();
        assertThat(store.findById(retained.getId())).contains(retained);
        assertThat(store.findById("missing-card")).isEmpty();
        assertThat(store.tryAcquireProcessingLock(deleted.getId(), Duration.ofSeconds(30))).isEmpty();
        store.releaseProcessingLock(deleted.getId(), token);
        assertThat(store.tryAcquireProcessingLock(deleted.getId(), Duration.ofSeconds(30))).isPresent();
    }

    @Test
    void fallbackLeaseRequiresItsOwnerTokenAndAnExpiredOwnerCannotReleaseItsReplacement() {
        RedisConfirmationCardStore store = new RedisConfirmationCardStore(null, mapper);
        String id = UUID.randomUUID().toString();
        String oldOwner = store.tryAcquireProcessingLock(id, Duration.ofSeconds(1)).orElseThrow();
        store.releaseProcessingLock(id, "wrong-token");
        store.releaseProcessingLock(id, null);
        assertThat(store.tryAcquireProcessingLock(id, Duration.ofSeconds(30))).isEmpty();
        String newOwner = await().pollDelay(Duration.ZERO).pollInterval(Duration.ofMillis(25))
                .atMost(Duration.ofSeconds(5))
                .until(() -> store.tryAcquireProcessingLock(id, Duration.ofSeconds(30)), Optional::isPresent)
                .orElseThrow();
        assertThat(newOwner).isNotEqualTo(oldOwner);
        store.releaseProcessingLock(id, oldOwner);
        assertThat(store.tryAcquireProcessingLock(id, Duration.ofSeconds(30))).isEmpty();
        store.releaseProcessingLock(id, newOwner);
        assertThat(store.tryAcquireProcessingLock(id, Duration.ofSeconds(30)))
                .hasValueSatisfying(token -> assertThat(token).isNotEqualTo(newOwner));
    }

    @Test
    void fallbackExpiryIsDeterministicForPendingAndConfirmedCards() {
        MutableClock clock = MutableClock.of(Instant.parse("2026-11-02T10:00:00Z"));
        RedisConfirmationCardStore store = new RedisConfirmationCardStore(null, mapper, clock);

        ConfirmationCard pendingCard = card();
        pendingCard.setStatus(ConfirmationCard.STATUS_PENDING);

        ConfirmationCard confirmedCard = card();
        confirmedCard.setStatus(ConfirmationCard.STATUS_CONFIRMED);
        confirmedCard.setConfirmedBy(confirmedCard.getOwnerId());
        confirmedCard.setConfirmationOutcome(Map.of("bookingId", UUID.randomUUID().toString(), "success", true));

        store.save(pendingCard);
        store.save(confirmedCard);

        assertThat(store.findById(pendingCard.getId())).isPresent();
        assertThat(store.findById(confirmedCard.getId())).isPresent();

        // Advance 14m 59s: both still present
        clock.advance(Duration.ofMinutes(14).plusSeconds(59));
        assertThat(store.findById(pendingCard.getId())).isPresent();
        assertThat(store.findById(confirmedCard.getId())).isPresent();

        // Advance 1s (total 15m): pending expired and evicted, confirmed still present
        clock.advance(Duration.ofSeconds(1));
        assertThat(store.findById(pendingCard.getId())).isEmpty();
        assertThat(store.findById(confirmedCard.getId())).isPresent();

        // Advance 23h 44m 59s (total 23h 59m 59s): confirmed still present
        clock.advance(Duration.ofHours(23).plusMinutes(44).plusSeconds(59));
        assertThat(store.findById(confirmedCard.getId())).isPresent();

        // Advance 1s (total 24h / 1 day): confirmed card expired
        clock.advance(Duration.ofSeconds(1));
        assertThat(store.findById(confirmedCard.getId())).isEmpty();
    }

    @Test
    void fallbackCapacityCapRejectsNewKeysAt10000AndAcceptsNewKeysAfterExpiry() {
        MutableClock clock = MutableClock.of(Instant.parse("2026-11-02T10:00:00Z"));
        RedisConfirmationCardStore store = new RedisConfirmationCardStore(null, mapper, clock);

        List<ConfirmationCard> cards = new ArrayList<>(10_000);
        for (int i = 0; i < 10_000; i++) {
            ConfirmationCard card = card();
            cards.add(card);
            store.save(card);
        }

        // Overwrite an existing card within the 10,000 set
        ConfirmationCard existingCard = cards.get(0);
        existingCard.setReason("Updated reason for card 0");
        store.save(existingCard);
        assertThat(store.findById(existingCard.getId()).orElseThrow().getReason())
                .isEqualTo("Updated reason for card 0");

        // 10,001st distinct card must be rejected
        ConfirmationCard card10001 = card();
        assertThatThrownBy(() -> store.save(card10001))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to persist confirmation card")
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Confirmation cache is full");

        assertThat(store.findById(card10001.getId())).isEmpty();

        // Advance time past TTL (15 minutes)
        clock.advance(Duration.ofMinutes(16));

        // New card is accepted after expired cards are cleaned up
        ConfirmationCard newCardAfterExpiry = card();
        store.save(newCardAfterExpiry);
        assertThat(store.findById(newCardAfterExpiry.getId())).isPresent();
    }

    @Test
    void fallbackLeaseExpiryIsDeterministicWithClock() {
        MutableClock clock = MutableClock.of(Instant.parse("2026-11-02T10:00:00Z"));
        RedisConfirmationCardStore store = new RedisConfirmationCardStore(null, mapper, clock);
        String id = UUID.randomUUID().toString();
        String firstOwner = store.tryAcquireProcessingLock(id, Duration.ofSeconds(30)).orElseThrow();

        // While lock is active, cannot reacquire
        clock.advance(Duration.ofSeconds(29));
        assertThat(store.tryAcquireProcessingLock(id, Duration.ofSeconds(30))).isEmpty();

        // Advance past lease TTL
        clock.advance(Duration.ofSeconds(2));
        String secondOwner = store.tryAcquireProcessingLock(id, Duration.ofSeconds(30)).orElseThrow();
        assertThat(secondOwner).isNotEqualTo(firstOwner);

        // First owner tries to release expired lock, must not release second owner's lock
        store.releaseProcessingLock(id, firstOwner);
        assertThat(store.tryAcquireProcessingLock(id, Duration.ofSeconds(30))).isEmpty();

        // Second owner releases properly
        store.releaseProcessingLock(id, secondOwner);
        assertThat(store.tryAcquireProcessingLock(id, Duration.ofSeconds(30))).isPresent();
    }

    @Test
    void redisWriteFailureIsSurfacedInsteadOfPretendingTheCardWasPersisted() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = valueOperations(redis);
        ConfirmationCard card = card();
        var outage = new DataAccessResourceFailureException("Redis unavailable");
        doThrow(outage).when(values).set(eq(key(card.getId())), anyString(), any(Duration.class));
        RedisConfirmationCardStore store = new RedisConfirmationCardStore(redis, mapper);

        assertThatThrownBy(() -> store.save(card)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to persist confirmation card").hasCause(outage);

        verify(values).set(eq(key(card.getId())), anyString(), eq(Duration.ofMinutes(15)));
    }

    @Test
    void unserializableOutcomeFailsBeforeAnyRedisWrite() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ConfirmationCard card = card();
        card.setConfirmationOutcome(Map.of("invalid", new UnserializableOutcome()));
        RedisConfirmationCardStore store = new RedisConfirmationCardStore(redis, mapper);

        assertThatThrownBy(() -> store.save(card)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to persist confirmation card").hasCauseInstanceOf(JsonProcessingException.class);

        verifyNoInteractions(redis);
    }

    @Test
    void redisReadFailureReturnsNoCardInsteadOfAnUnverifiedConfirmation() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = valueOperations(redis);
        String id = UUID.randomUUID().toString();
        when(values.get(key(id))).thenThrow(new DataAccessResourceFailureException("Redis unavailable"));
        RedisConfirmationCardStore store = new RedisConfirmationCardStore(redis, mapper);

        assertThat(store.findById(id)).isEmpty();

        verify(values).get(key(id));
    }

    @Test
    void redisLockFailureThrowsAndCannotGrantAnInMemoryProcessingLease() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = valueOperations(redis);
        String id = UUID.randomUUID().toString();
        var outage = new DataAccessResourceFailureException("Redis unavailable");
        when(values.setIfAbsent(eq(key(id) + ":processing"), anyString(), eq(Duration.ofSeconds(30))))
                .thenThrow(outage);
        RedisConfirmationCardStore store = new RedisConfirmationCardStore(redis, mapper);

        assertThatThrownBy(() -> store.tryAcquireProcessingLock(id, Duration.ofSeconds(30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to acquire confirmation processing lock").hasCause(outage);

        verify(values).setIfAbsent(eq(key(id) + ":processing"), anyString(), eq(Duration.ofSeconds(30)));
    }

    @Test
    void busyOrIndeterminateRedisResponseCannotGrantALease() {
        for (Boolean response : new Boolean[]{false, null}) {
            StringRedisTemplate redis = mock(StringRedisTemplate.class);
            ValueOperations<String, String> values = valueOperations(redis);
            String id = UUID.randomUUID().toString();
            when(values.setIfAbsent(eq(key(id) + ":processing"), anyString(), eq(Duration.ofSeconds(30))))
                    .thenReturn(response);
            RedisConfirmationCardStore store = new RedisConfirmationCardStore(redis, mapper);

            assertThat(store.tryAcquireProcessingLock(id, Duration.ofSeconds(30))).isEmpty();

            verify(values).setIfAbsent(eq(key(id) + ":processing"), anyString(), eq(Duration.ofSeconds(30)));
        }
    }

    @Test
    void failedRedisReleaseRemainsBestEffortAndUsesOnlyTheRequestedKeyAndOwnerToken() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        String id = UUID.randomUUID().toString();
        String token = UUID.randomUUID().toString();
        doThrow(new DataAccessResourceFailureException("Redis unavailable")).when(redis)
                .execute(any(RedisScript.class), eq(List.of(key(id) + ":processing")), eq(token));
        RedisConfirmationCardStore store = new RedisConfirmationCardStore(redis, mapper);

        store.releaseProcessingLock(id, token);

        verify(redis).execute(any(RedisScript.class), eq(List.of(key(id) + ":processing")), eq(token));
    }

    @Test
    void redisDeleteFailureIsVisibleToTheCaller() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        String id = UUID.randomUUID().toString();
        var outage = new DataAccessResourceFailureException("Redis unavailable");
        when(redis.delete(key(id))).thenThrow(outage);
        RedisConfirmationCardStore store = new RedisConfirmationCardStore(redis, mapper);

        assertThatThrownBy(() -> store.delete(id)).isSameAs(outage);

        verify(redis).delete(key(id));
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> valueOperations(StringRedisTemplate redis) {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        return values;
    }

    private String key(String id) {
        return "ai:confirmation:" + id;
    }

    private ConfirmationCard card() {
        return ConfirmationCard.builder().id(UUID.randomUUID().toString()).ownerId(UUID.randomUUID())
                .conversationId(UUID.randomUUID()).serviceId(UUID.randomUUID()).slotId(UUID.randomUUID())
                .optionId(UUID.randomUUID()).participantsCount(3).quantity(2).participantsPerPackage(List.of(2, 1))
                .price(new BigDecimal("1250000.50")).date("2026-11-12").status(ConfirmationCard.STATUS_PENDING)
                .createdAt(LocalDateTime.of(2026, 11, 2, 9, 30)).reason("Confirm package grouping")
                .locale("en").retryCount(1).build();
    }

    public static final class UnserializableOutcome {
        public String getValue() {
            throw new IllegalStateException("Outcome serialization failed");
        }
    }
}
