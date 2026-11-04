package com.danasea.backend.modules.service.application.services;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.domain.models.ServiceOption;
import com.danasea.backend.modules.service.domain.models.ServiceSlot;
import com.danasea.backend.modules.service.domain.models.ServiceSlotUnit;
import com.danasea.backend.modules.service.domain.ports.ServiceSlotRepositoryPort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SlotAvailabilityCalculator {
    private final ServiceSlotRepositoryPort serviceSlotRepository;
    private final StringRedisTemplate redisTemplate;

    public static boolean isUpcoming(ServiceSlot slot) {
        return slot.isOpen() && slot.getDate() != null && slot.getStartTime() != null
                && slot.getDate().atTime(slot.getStartTime()).isAfter(LocalDateTime.now(Booking.VIETNAM_ZONE));
    }

    public record Availability(int available, int largestSharedUnit) {
        public boolean canBook(int quantity, boolean shared, boolean allowSplit, InventoryType inventoryType) {
            return available >= quantity && (!shared || allowSplit || inventoryType != InventoryType.SHARED_CAPACITY_UNITS
                    || largestSharedUnit >= quantity);
        }
    }

    public Availability evaluate(ServiceSlot slot, ServiceOption option) {
        Map<Object, Object> rawHolds = redisTemplate.opsForHash().entries("inventory:slot:" + slot.getId() + ":holds");
        long now = System.currentTimeMillis();
        if (slot.getInventoryType() != InventoryType.SHARED_CAPACITY_UNITS) {
            int held = rawHolds == null ? 0 : rawHolds.values().stream()
                    .mapToInt(value -> parsePersonLimitHoldSeats(String.valueOf(value), now)).sum();
            int available = option.isPrivate() ? 0 : Math.max(0, valueOrZero(slot.getCapacity())
                    - valueOrZero(slot.getBookedCount()) - held);
            return new Availability(available, available);
        }
        List<ServiceSlotUnit> units = slot.getUnits();
        if (units == null || units.isEmpty()) {
            units = serviceSlotRepository.findUnitsBySlotId(slot.getId());
        }
        Map<Integer, Integer> heldSeats = new HashMap<>();
        Map<Integer, Boolean> privateHolds = new HashMap<>();
        if (rawHolds != null) {
            rawHolds.values().forEach(value -> parseUnitHoldEntry(String.valueOf(value), now, heldSeats, privateHolds));
        }
        int available = 0;
        int largest = 0;
        int requiredCapacity = Math.max(1, valueOrZero(option.getMaxPaxPerPackage()));
        for (ServiceSlotUnit unit : units) {
            if (unit.isEquipmentBlocked()) {
                continue;
            }
            int booked = valueOrZero(unit.getBookedCount());
            int held = heldSeats.getOrDefault(unit.getUnitNumber(), 0);
            if (privateHolds.getOrDefault(unit.getUnitNumber(), false)) {
                continue;
            }
            int remaining = Math.max(0, valueOrZero(unit.getCapacity()) - booked - held);
            largest = Math.max(largest, remaining);
            if (option.isPrivate()) {
                if (booked == 0 && held == 0 && valueOrZero(unit.getCapacity()) >= requiredCapacity) {
                    available++;
                }
            } else {
                available += remaining;
            }
        }
        return new Availability(available, largest);
    }

    public boolean canBook(ServiceSlot slot, ServiceOption option, int quantity, boolean allowSplit) {
        return evaluate(slot, option).canBook(quantity, option.isShared(), allowSplit, slot.getInventoryType());
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private void parseUnitHoldEntry(
            String val,
            long now,
            Map<Integer, Integer> unitHeldSeats,
            Map<Integer, Boolean> unitPrivateHeld
    ) {
        try {
            int barPos = val.indexOf('|');
            if (barPos < 0) return;
            long exp = Long.parseLong(val.substring(0, barPos));
            if (exp <= now) return;

            String details = val.substring(barPos + 1);
            if (!details.startsWith("UNITS:")) return;

            String unitsStr = details.substring("UNITS:".length());
            String[] allocations = unitsStr.split(";");
            for (String alloc : allocations) {
                if (alloc.isBlank()) continue;
                String[] parts = alloc.split(":");
                if (parts.length >= 3) {
                    int unitNum = Integer.parseInt(parts[0]);
                    int seats = Integer.parseInt(parts[1]);
                    int priv = Integer.parseInt(parts[2]);

                    unitHeldSeats.merge(unitNum, seats, Integer::sum);
                    if (priv == 1) {
                        unitPrivateHeld.put(unitNum, true);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private int parsePersonLimitHoldSeats(String val, long now) {
        try {
            int barPos = val.indexOf('|');
            if (barPos > 0) {
                long exp = Long.parseLong(val.substring(0, barPos));
                if (exp <= now) return 0;
                String details = val.substring(barPos + 1);
                if (details.startsWith("PERSON_LIMIT:")) {
                    return Integer.parseInt(details.substring("PERSON_LIMIT:".length()));
                }
            } else {
                int colonPos = val.indexOf(':');
                if (colonPos > 0) {
                    int qty = Integer.parseInt(val.substring(0, colonPos));
                    long exp = Long.parseLong(val.substring(colonPos + 1));
                    if (exp > now) return qty;
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }
}
