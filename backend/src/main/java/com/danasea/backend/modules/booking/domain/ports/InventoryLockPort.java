package com.danasea.backend.modules.booking.domain.ports;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.danasea.backend.modules.booking.domain.models.InventoryLockItem;

public interface InventoryLockPort {

    /**
     * Attempts to atomically lock inventory across all requested slot items using a single batch Lua script.
     * If any slot has insufficient capacity, no locks are acquired and InsufficientInventoryException is thrown.
     *
     * @param holdId identifier of the hold/booking
     * @param items list of slot items and quantities to hold
     * @param ttl time-to-live for the temporary hold
     */
    void acquireHolds(UUID holdId, List<InventoryLockItem> items, Duration ttl);

    /**
     * Releases held slots for a given holdId, freeing inventory back into the pool.
     *
     * @param holdId identifier of the hold/booking
     * @param items list of slot items to release
     */
    void releaseHolds(UUID holdId, List<InventoryLockItem> items);

    /**
     * Atomically extends inventory holds in Redis for payment window (5+5).
     *
     * @param holdId identifier of the hold/booking
     * @param items list of slot items
     * @param extendedDuration new duration from now
     * @return true if all holds successfully extended, false if expired or missing
     */
    default boolean extendHolds(UUID holdId, List<InventoryLockItem> items, Duration extendedDuration) {
        return false;
    }

    /**
     * Reverts extended hold expiry back to original hold expiry if database transaction fails.
     * Guarded with expectedExtendedExpiryMillis to prevent reverting newer sessions.
     *
     * @param holdId identifier of the hold/booking
     * @param items list of slot items
     * @param expectedExtendedExpiryMillis the extended expiry timestamp that was set
     * @param originalHoldExpiryMillis the original hold expiry to restore
     * @return true if successfully reverted across all items
     */
    default boolean revertHoldExpiry(UUID holdId, List<InventoryLockItem> items, long expectedExtendedExpiryMillis, long originalHoldExpiryMillis) {
        return false;
    }

    /**
     * Marks holds as closing in Redis by granting grace extension buffer, preventing premature deletion.
     *
     * @param holdId identifier of the hold/booking
     * @param items list of slot items
     * @param closingGraceDuration buffer duration
     * @return true if marked closing
     */
    default boolean markHoldsClosing(UUID holdId, List<InventoryLockItem> items, Duration closingGraceDuration) {
        return false;
    }
}
