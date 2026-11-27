package com.danasea.backend.modules.booking.infrastructure.redis;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import com.danasea.backend.modules.booking.domain.exceptions.InsufficientInventoryException;
import com.danasea.backend.modules.booking.domain.models.InventoryLockItem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisInventoryLockAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisInventoryLockAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisInventoryLockAdapter(redisTemplate);
    }

    @Test
    @DisplayName("acquireHolds succeeds when Redis script returns OK")
    void testAcquireHoldsSuccess() {
        UUID holdId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of("OK"));

        List<InventoryLockItem> items = List.of(InventoryLockItem.of(slotId, 2, 5));

        adapter.acquireHolds(holdId, items, Duration.ofMinutes(15));

        verify(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    @DisplayName("acquireHolds throws InsufficientInventoryException when Redis script returns INSUFFICIENT")
    void testAcquireHoldsInsufficient() {
        UUID holdId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        String key = "inventory:slot:" + slotId + ":holds";

        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of("INSUFFICIENT", key, "3", "1"));

        List<InventoryLockItem> items = List.of(InventoryLockItem.of(slotId, 3, 2));

        assertThatThrownBy(() -> adapter.acquireHolds(holdId, items, Duration.ofMinutes(15)))
                .isInstanceOf(InsufficientInventoryException.class)
                .satisfies(ex -> {
                    InsufficientInventoryException e = (InsufficientInventoryException) ex;
                    assertThat(e.getSlotId()).isEqualTo(slotId);
                    assertThat(e.getRequestedQuantity()).isEqualTo(3);
                    assertThat(e.getAvailableQuantity()).isEqualTo(1);
                });
    }

    @Test
    @DisplayName("releaseHolds executes release script with holdId")
    void testReleaseHolds() {
        UUID holdId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        List<InventoryLockItem> items = List.of(InventoryLockItem.of(slotId, 2, 5));

        adapter.releaseHolds(holdId, items);

        verify(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }
}
