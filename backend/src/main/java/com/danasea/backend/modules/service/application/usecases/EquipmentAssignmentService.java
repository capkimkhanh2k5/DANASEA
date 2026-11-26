package com.danasea.backend.modules.service.application.usecases;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.service.domain.exceptions.VendorNotApprovedException;
import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.EquipmentStore;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.EquipmentStore.Equipment;
import com.danasea.backend.shared.presentation.ApiConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class EquipmentAssignmentService {

    private final VendorPort vendors;
    private final EquipmentStore store;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    public UUID vendorId(UUID userId) {
        return vendors.findByUserId(userId)
                .orElseThrow(() -> new VendorNotApprovedException("Vendor profile not found"))
                .getId();
    }

    @Transactional
    public Equipment create(UUID userId, String name, String compatibilityGroup, int capacity) {
        return store.create(vendorId(userId), name.strip(), compatibilityGroup.strip(), capacity);
    }

    @Transactional(readOnly = true)
    public List<Equipment> list(UUID userId) {
        return store.list(vendorId(userId));
    }

    @Transactional
    public void validateScheduleEquipment(UUID vendorId, Map<UUID, Integer> expectedCapacities) {
        for (UUID id : expectedCapacities.keySet().stream().sorted().toList()) {
            if (equipment(vendorId, id).capacity() != expectedCapacities.get(id)) {
                throw new IllegalArgumentException("Schedule unit capacity must match physical equipment capacity");
            }
        }
    }

    @Transactional
    public void attach(UUID userId, UUID slotId, int unitNumber, UUID equipmentId) {
        assign(vendorId(userId), slotId, Map.of(unitNumber, equipmentId));
    }

    @Transactional
    public void detach(UUID userId, UUID slotId, int unitNumber, UUID equipmentId) {
        UUID vendorId = vendorId(userId);
        equipment(vendorId, equipmentId);
        var slot = store.lockSlot(vendorId, slotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found"));
        ensureUpcomingOpenSlot(slot);
        var unit = store.units(slotId).stream()
                .filter(u -> u.number() == unitNumber)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot unit not found"));
        if (!equipmentId.equals(unit.equipmentId())) {
            throw conflict("Equipment is not currently linked to this slot unit");
        }
        ensureUnitUncommitted(slotId, unit, store.commitments(slotId),
                "Cannot detach equipment from a unit with a committed booking or active hold");
        store.unbind(unit.id());
    }

    @Transactional
    public void attachGenerated(UUID vendorId, UUID slotId, Map<Integer, UUID> equipmentByUnitNumber) {
        if (!equipmentByUnitNumber.isEmpty()) {
            assign(vendorId, slotId, equipmentByUnitNumber);
        }
    }

    @Transactional
    public void detachUnitsInTransaction(UUID vendorId, UUID slotId, List<Integer> unitNumbers) {
        if (unitNumbers == null || unitNumbers.isEmpty()) {
            return;
        }
        var slot = store.lockSlot(vendorId, slotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found"));
        ensureUpcomingOpenSlot(slot);
        var units = store.units(slotId).stream()
                .collect(Collectors.toMap(EquipmentStore.Unit::number, Function.identity()));
        var commitments = store.commitments(slotId);
        unitNumbers.stream()
                .map(units::get)
                .filter(u -> u != null && u.equipmentId() != null)
                .map(EquipmentStore.Unit::equipmentId)
                .distinct()
                .sorted()
                .forEach(eqId -> equipment(vendorId, eqId));
        for (int unitNumber : unitNumbers) {
            var unit = units.get(unitNumber);
            if (unit == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot unit not found");
            }
            if (unit.equipmentId() == null) {
                continue;
            }
            ensureUnitUncommitted(slotId, unit, commitments,
                    "Cannot detach equipment from a unit with a committed booking or active hold");
            store.unbind(unit.id());
        }
    }

    @Transactional
    public List<EquipmentStore.Unit> attachOrReplaceInTransaction(
            UUID vendorId, UUID slotId, Map<Integer, UUID> equipmentByUnitNumber) {
        if (equipmentByUnitNumber != null && !equipmentByUnitNumber.isEmpty()) {
            assign(vendorId, slotId, equipmentByUnitNumber);
        }
        return store.units(slotId);
    }

    @Transactional(readOnly = true)
    public List<EquipmentStore.Unit> slotUnits(UUID slotId) {
        return store.units(slotId);
    }

    private void assign(UUID vendorId, UUID slotId, Map<Integer, UUID> equipmentByUnitNumber) {
        if (new HashSet<>(equipmentByUnitNumber.values()).size() != equipmentByUnitNumber.size()) {
            throw new IllegalArgumentException("A piece of equipment can only appear once in a slot");
        }
        Map<UUID, Equipment> locked = new HashMap<>();
        equipmentByUnitNumber.values().stream().sorted()
                .forEach(id -> locked.put(id, equipment(vendorId, id)));
        var slot = store.lockSlot(vendorId, slotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found"));
        ensureUpcomingOpenSlot(slot);
        var units = store.units(slotId).stream()
                .collect(Collectors.toMap(EquipmentStore.Unit::number, Function.identity()));
        var commitments = store.commitments(slotId);
        for (var entry : equipmentByUnitNumber.entrySet()) {
            var unit = units.get(entry.getKey());
            if (unit == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot unit not found");
            }
            UUID equipmentId = entry.getValue();
            if (equipmentId.equals(unit.equipmentId())) {
                continue;
            }
            Equipment targetEquipment = locked.get(equipmentId);
            boolean replacing = unit.equipmentId() != null;
            ensureUnitUncommitted(slotId, unit, commitments,
                    replacing
                            ? "Cannot replace equipment on a unit with a committed booking or active hold"
                            : "Only an empty, unassigned slot unit can be linked");
            if (units.values().stream().anyMatch(u -> u.number() != unit.number() && equipmentId.equals(u.equipmentId()))
                    || store.alreadyAssignedDuring(equipmentId, slot)) {
                throw conflict("Physical equipment already assigned to an overlapping slot");
            }
            boolean blocked = store.maintenanceOverlaps(equipmentId, slot.start(), slot.end());
            if (!replacing) {
                if (unit.capacity() != targetEquipment.capacity()) {
                    throw new IllegalArgumentException("Slot unit capacity must match physical equipment capacity");
                }
                store.bind(unit.id(), equipmentId, blocked);
            } else {
                if (targetEquipment.capacity() != unit.capacity()) {
                    int maxPrivatePax = store.maxPrivateOptionPax(slotId);
                    if (maxPrivatePax > 0) {
                        int maxRemainingCapacity = units.values().stream()
                                .mapToInt(u -> u.number() == unit.number() ? targetEquipment.capacity() : u.capacity())
                                .max()
                                .orElse(0);
                        if (maxRemainingCapacity < maxPrivatePax) {
                            throw new IllegalArgumentException(
                                    "At least one slot unit must fit the private option maximum capacity (" + maxPrivatePax + ")");
                        }
                    }
                }
                store.replaceUnitEquipment(slotId, unit.id(), equipmentId, targetEquipment.capacity(), blocked);
            }
            units.put(unit.number(), new EquipmentStore.Unit(
                    unit.id(), unit.number(), equipmentId, unit.compatibilityGroup(),
                    targetEquipment.capacity(), unit.booked(), blocked));
        }
    }

    private void ensureUpcomingOpenSlot(EquipmentStore.Slot slot) {
        if (!slot.start().isAfter(LocalDateTime.now(Booking.VIETNAM_ZONE)) || "CANCELLED".equals(slot.status())) {
            throw conflict("Equipment can only be assigned to an upcoming slot that is not cancelled");
        }
    }

    private void ensureUnitUncommitted(UUID slotId, EquipmentStore.Unit unit,
            List<EquipmentStore.Commitment> commitments, String conflictMessage) {
        if (unit.booked() != 0
                || commitments.stream().anyMatch(c -> c.unitNumber() == unit.number())
                || hasActiveRedisHold(slotId, unit.number())) {
            throw conflict(conflictMessage);
        }
    }

    private boolean hasActiveRedisHold(UUID slotId, int unitNumber) {
        if (redisTemplate == null) {
            return false;
        }
        Map<Object, Object> entries = redisTemplate.opsForHash().entries("inventory:slot:" + slotId + ":holds");
        if (entries == null || entries.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        for (Object rawValue : entries.values()) {
            String val = String.valueOf(rawValue);
            int barPos = val.indexOf('|');
            if (barPos < 0) {
                continue;
            }
            long exp;
            try {
                exp = Long.parseLong(val.substring(0, barPos));
            } catch (NumberFormatException ex) {
                return true;
            }
            if (exp <= now) {
                continue;
            }
            String details = val.substring(barPos + 1);
            if (!details.startsWith("UNITS:")) {
                return true;
            }
            for (String alloc : details.substring("UNITS:".length()).split(";")) {
                if (alloc.isBlank()) {
                    continue;
                }
                String[] parts = alloc.split(":");
                if (parts.length < 3) {
                    return true;
                }
                try {
                    int heldUnit = Integer.parseInt(parts[0]);
                    int seats = Integer.parseInt(parts[1]);
                    int priv = Integer.parseInt(parts[2]);
                    if (heldUnit == unitNumber && (seats > 0 || priv == 1)) {
                        return true;
                    }
                } catch (NumberFormatException ex) {
                    return true;
                }
            }
        }
        return false;
    }

    private Equipment equipment(UUID vendorId, UUID equipmentId) {
        return store.equipment(vendorId, equipmentId, true)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));
    }

    static ResponseStatusException conflict(String message) {
        return new ApiConflictException("EQUIPMENT_CONFLICT", message);
    }
}
