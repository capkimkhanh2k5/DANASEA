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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class EquipmentAssignmentService {

    private final VendorPort vendors;
    private final EquipmentStore store;

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
    public void attachGenerated(UUID vendorId, UUID slotId, Map<Integer, UUID> equipmentByUnitNumber) {
        if (!equipmentByUnitNumber.isEmpty()) {
            assign(vendorId, slotId, equipmentByUnitNumber);
        }
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
        if (!slot.start().isAfter(LocalDateTime.now(Booking.VIETNAM_ZONE)) || "CANCELLED".equals(slot.status())) {
            throw conflict("Equipment can only be assigned to an upcoming slot that is not cancelled");
        }
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
            if (unit.equipmentId() != null || unit.booked() != 0
                    || commitments.stream().anyMatch(c -> c.unitNumber() == unit.number())) {
                throw conflict("Only an empty, unassigned slot unit can be linked");
            }
            if (unit.capacity() != locked.get(equipmentId).capacity()) {
                throw new IllegalArgumentException("Slot unit capacity must match physical equipment capacity");
            }
            if (units.values().stream().anyMatch(u -> equipmentId.equals(u.equipmentId()))
                    || store.alreadyAssignedDuring(equipmentId, slot)) {
                throw conflict("Physical equipment already assigned to an overlapping slot");
            }
            store.bind(unit.id(), equipmentId, store.maintenanceOverlaps(equipmentId, slot.start(), slot.end()));
        }
    }

    private Equipment equipment(UUID vendorId, UUID equipmentId) {
        return store.equipment(vendorId, equipmentId, true)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));
    }

    static ResponseStatusException conflict(String message) {
        return new ApiConflictException("EQUIPMENT_CONFLICT", message);
    }
}
