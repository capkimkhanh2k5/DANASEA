package com.danasea.backend.modules.booking.infrastructure.redis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.danasea.backend.modules.booking.domain.exceptions.InsufficientInventoryException;
import com.danasea.backend.modules.booking.domain.models.BookingItemAllocation;
import com.danasea.backend.modules.booking.domain.models.InventoryLockItem;
import com.danasea.backend.modules.booking.domain.ports.InventoryLockPort;
import com.danasea.backend.modules.service.domain.models.InventoryType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RedisInventoryLockAdapter implements InventoryLockPort {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> acquireScript;
    private final RedisScript<String> releaseScript;
    private final RedisScript<List> extendScript;
    private final RedisScript<List> revertScript;
    private final RedisScript<List> markClosingScript;

    private static final String KEY_PREFIX = "inventory:slot:";

    public RedisInventoryLockAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

        DefaultRedisScript<List> acquire = new DefaultRedisScript<>();
        acquire.setLocation(new ClassPathResource("lua/acquire_hold.lua"));
        acquire.setResultType(List.class);
        this.acquireScript = acquire;

        DefaultRedisScript<String> release = new DefaultRedisScript<>();
        release.setLocation(new ClassPathResource("lua/release_hold.lua"));
        release.setResultType(String.class);
        this.releaseScript = release;

        DefaultRedisScript<List> extend = new DefaultRedisScript<>();
        extend.setLocation(new ClassPathResource("lua/extend_hold.lua"));
        extend.setResultType(List.class);
        this.extendScript = extend;

        DefaultRedisScript<List> revert = new DefaultRedisScript<>();
        revert.setLocation(new ClassPathResource("lua/revert_hold_expiry.lua"));
        revert.setResultType(List.class);
        this.revertScript = revert;

        DefaultRedisScript<List> markClosing = new DefaultRedisScript<>();
        markClosing.setLocation(new ClassPathResource("lua/mark_hold_closing.lua"));
        markClosing.setResultType(List.class);
        this.markClosingScript = markClosing;
    }

    private String buildSlotKey(UUID slotId) {
        return KEY_PREFIX + slotId + ":holds";
    }

    @Override
    public void acquireHolds(UUID holdId, List<InventoryLockItem> items, Duration ttl) {
        if (items == null || items.isEmpty()) {
            return;
        }

        List<String> keys = new ArrayList<>(items.size());
        List<String> args = new ArrayList<>();

        long nowMillis = System.currentTimeMillis();
        long expireAtMillis = nowMillis + ttl.toMillis();

        args.add(String.valueOf(nowMillis));
        args.add(holdId.toString());
        args.add(String.valueOf(expireAtMillis));

        for (InventoryLockItem item : items) {
            keys.add(buildSlotKey(item.getSlotId()));
            if (InventoryType.SHARED_CAPACITY_UNITS.equals(item.getInventoryType())) {
                StringBuilder unitsSb = new StringBuilder();
                if (item.getUnits() != null) {
                    for (int u = 0; u < item.getUnits().size(); u++) {
                        var unit = item.getUnits().get(u);
                        if (u > 0) unitsSb.append(";");
                        unitsSb.append(unit.getUnitNumber()).append(":")
                                .append(unit.getCapacity()).append(":")
                                .append(unit.getBookedCount());
                    }
                }
                String optType = item.getOptionType() != null ? item.getOptionType().name() : "SHARED";
                int pax = item.getPaxPerPackage() != null ? item.getPaxPerPackage() : 1;
                args.add("SHARED_CAPACITY_UNITS|" + optType + "|" + item.getQuantity() + "|" + pax + "|" + unitsSb + "|" + (item.isAllowSplit() ? "1" : "0"));
            } else {
                args.add("PERSON_LIMIT|" + item.getQuantity() + "|" + item.getMaxCapacity());
            }
        }

        @SuppressWarnings("unchecked")
        List<Object> result = redisTemplate.execute(acquireScript, keys, args.toArray());

        if (result == null || result.isEmpty()) {
            throw new RuntimeException("Redis inventory lock script returned null result");
        }

        String status = String.valueOf(result.get(0));
        if (!"OK".equalsIgnoreCase(status)) {
            String key = result.size() > 1 ? String.valueOf(result.get(1)) : "";
            int req = result.size() > 2 ? Integer.parseInt(String.valueOf(result.get(2))) : 0;
            int avail = result.size() > 3 ? Integer.parseInt(String.valueOf(result.get(3))) : 0;

            UUID failedSlotId = extractSlotIdFromKey(key);
            log.warn("Inventory hold failed for slot {}: requested {}, available {}", failedSlotId, req, avail);
            throw new InsufficientInventoryException(failedSlotId, req, avail);
        }

        // Parse allocated units for SHARED_CAPACITY_UNITS items if returned
        if (result.size() > 1 && result.get(1) != null) {
            String combinedAlloc = String.valueOf(result.get(1));
            String[] slotAllocs = combinedAlloc.split("@", -1);
            for (int i = 0; i < items.size(); i++) {
                if (i < slotAllocs.length && !slotAllocs[i].isBlank()) {
                    List<BookingItemAllocation> allocs = new ArrayList<>();
                    String[] entries = slotAllocs[i].split(";");
                    for (String eStr : entries) {
                        if (eStr.isBlank()) continue;
                        String[] p = eStr.split(":");
                        if (p.length >= 3) {
                            int uNum = Integer.parseInt(p[0]);
                            int seats = Integer.parseInt(p[1]);
                            boolean isPriv = "1".equals(p[2]);
                            allocs.add(BookingItemAllocation.builder()
                                    .slotId(items.get(i).getSlotId())
                                    .unitNumber(uNum)
                                    .allocatedSeats(seats)
                                    .isPrivateLock(isPriv)
                                    .build());
                        }
                    }
                    items.get(i).setAllocations(allocs);
                }
            }
        }
    }

    @Override
    public void releaseHolds(UUID holdId, List<InventoryLockItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        List<String> keys = items.stream()
                .map(item -> buildSlotKey(item.getSlotId()))
                .toList();

        try {
            redisTemplate.execute(releaseScript, keys, holdId.toString());
        } catch (Exception e) {
            log.error("Failed to release Redis holds for holdId {}", holdId, e);
        }
    }

    @Override
    public boolean extendHolds(UUID holdId, List<InventoryLockItem> items, Duration extendedDuration) {
        if (items == null || items.isEmpty()) {
            return true;
        }

        List<String> keys = items.stream()
                .map(item -> buildSlotKey(item.getSlotId()))
                .toList();

        long nowMillis = System.currentTimeMillis();
        long newExpireAtMillis = nowMillis + extendedDuration.toMillis();

        try {
            @SuppressWarnings("unchecked")
            List<Object> result = redisTemplate.execute(extendScript, keys,
                    String.valueOf(nowMillis), holdId.toString(), String.valueOf(newExpireAtMillis));

            if (result != null && !result.isEmpty() && "OK".equalsIgnoreCase(String.valueOf(result.get(0)))) {
                return true;
            }
            log.warn("Failed to extend Redis holds for holdId {}: result={}", holdId, result);
            return false;
        } catch (Exception e) {
            log.error("Exception extending Redis holds for holdId {}", holdId, e);
            return false;
        }
    }

    @Override
    public boolean revertHoldExpiry(UUID holdId, List<InventoryLockItem> items, long expectedExtendedExpiryMillis, long originalHoldExpiryMillis) {
        if (items == null || items.isEmpty()) {
            return true;
        }

        List<String> keys = items.stream()
                .map(item -> buildSlotKey(item.getSlotId()))
                .toList();

        try {
            @SuppressWarnings("unchecked")
            List<Object> result = redisTemplate.execute(revertScript, keys,
                    holdId.toString(), String.valueOf(expectedExtendedExpiryMillis), String.valueOf(originalHoldExpiryMillis));

            if (result != null && !result.isEmpty() && "OK".equalsIgnoreCase(String.valueOf(result.get(0)))) {
                return true;
            }
            log.warn("Failed to revert Redis hold expiry for holdId {}: result={}", holdId, result);
            return false;
        } catch (Exception e) {
            log.error("Exception reverting Redis hold expiry for holdId {}", holdId, e);
            return false;
        }
    }

    @Override
    public boolean markHoldsClosing(UUID holdId, List<InventoryLockItem> items, Duration closingGraceDuration) {
        if (items == null || items.isEmpty()) {
            return true;
        }

        List<String> keys = items.stream()
                .map(item -> buildSlotKey(item.getSlotId()))
                .toList();

        long closingGraceExpiry = System.currentTimeMillis() + closingGraceDuration.toMillis();

        try {
            @SuppressWarnings("unchecked")
            List<Object> result = redisTemplate.execute(markClosingScript, keys,
                    holdId.toString(), String.valueOf(closingGraceExpiry));

            return result != null && !result.isEmpty() && "OK".equalsIgnoreCase(String.valueOf(result.get(0)));
        } catch (Exception e) {
            log.error("Exception marking Redis holds closing for holdId {}", holdId, e);
            return false;
        }
    }

    private UUID extractSlotIdFromKey(String key) {
        try {
            if (key != null && key.startsWith(KEY_PREFIX)) {
                String sub = key.substring(KEY_PREFIX.length());
                int colon = sub.indexOf(":holds");
                if (colon > 0) {
                    return UUID.fromString(sub.substring(0, colon));
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
