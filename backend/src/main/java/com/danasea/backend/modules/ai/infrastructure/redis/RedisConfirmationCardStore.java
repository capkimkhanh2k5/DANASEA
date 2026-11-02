package com.danasea.backend.modules.ai.infrastructure.redis;

import com.danasea.backend.modules.ai.application.ports.ConfirmationCardStorePort;
import com.danasea.backend.modules.ai.domain.models.ConfirmationCard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.List;

@Slf4j
@Component
public class RedisConfirmationCardStore implements ConfirmationCardStorePort {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private record CachedValue(String value, Instant expiresAt) { }
    private final Map<String, CachedValue> inMemoryFallback = new ConcurrentHashMap<>();
    private static final String KEY_PREFIX = "ai:confirmation:";
    private static final Duration TTL = Duration.ofMinutes(15);
    private static final Duration OUTCOME_TTL = Duration.ofDays(1);

    @Autowired
    public RedisConfirmationCardStore(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this(redisTemplate, objectMapper, Clock.systemUTC());
    }

    RedisConfirmationCardStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Override
    public void save(ConfirmationCard card) {
        String key = KEY_PREFIX + card.getId();
        try {
            String json = objectMapper.writeValueAsString(card);
            Duration ttl = ConfirmationCard.STATUS_CONFIRMED.equals(card.getStatus()) ? OUTCOME_TTL : TTL;
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(key, json, ttl);
            } else {
                removeExpired();
                if (inMemoryFallback.size() >= 10000 && !inMemoryFallback.containsKey(key))
                    throw new IllegalStateException("Confirmation cache is full");
                inMemoryFallback.put(key, new CachedValue(json, Instant.now(clock).plus(ttl)));
            }
        } catch (Exception e) {
            log.error("Failed to save confirmation card", e);
            throw new IllegalStateException("Unable to persist confirmation card", e);
        }
    }

    @Override
    public Optional<ConfirmationCard> findById(String id) {
        String key = KEY_PREFIX + id;
        try {
            String json;
            if (redisTemplate != null) {
                json = redisTemplate.opsForValue().get(key);
            } else {
                CachedValue cached = inMemoryFallback.get(key);
                if (cached != null && cached.expiresAt().isAfter(Instant.now(clock))) json = cached.value();
                else { inMemoryFallback.remove(key); json = null; }
            }
            if (json != null) {
                return Optional.of(objectMapper.readValue(json, ConfirmationCard.class));
            }
        } catch (Exception e) {
            log.error("Failed to get confirmation card", e);
        }
        return Optional.empty();
    }

    @Override
    public void delete(String id) {
        String key = KEY_PREFIX + id;
        if (redisTemplate != null) {
            redisTemplate.delete(key);
        } else {
            inMemoryFallback.remove(key);
        }
    }

    @Override
    public Optional<String> tryAcquireProcessingLock(String id, Duration ttl) {
        String key = KEY_PREFIX + id + ":processing";
        String token = UUID.randomUUID().toString();
        if (redisTemplate == null) {
            removeExpired();
            return inMemoryFallback.putIfAbsent(key, new CachedValue(token, Instant.now(clock).plus(ttl))) == null
                    ? Optional.of(token) : Optional.empty();
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, token, ttl))
                    ? Optional.of(token)
                    : Optional.empty();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to acquire confirmation processing lock", exception);
        }
    }

    @Override
    public void releaseProcessingLock(String id, String token) {
        if (token == null) {
            return;
        }
        String key = KEY_PREFIX + id + ":processing";
        if (redisTemplate == null) {
            inMemoryFallback.computeIfPresent(key, (ignored, cached) -> cached.value().equals(token) ? null : cached);
            return;
        }
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
                Long.class);
        try {
            redisTemplate.execute(script, List.of(key), token);
        } catch (Exception exception) {
            log.warn("Failed to release confirmation processing lock for card {}", id, exception);
        }
    }
    private void removeExpired() {
        Instant now = Instant.now(clock);
        inMemoryFallback.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

}
