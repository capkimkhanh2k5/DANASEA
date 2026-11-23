package com.danasea.backend.modules.service.presentation.dtos;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.danasea.backend.modules.service.domain.models.InventoryType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record SlotTemplateRequest(
        @NotNull(message = "{validation.service.slot.start.required}")
        LocalTime startTime,

        @NotNull(message = "{validation.service.slot.end.required}")
        LocalTime endTime,

        @NotNull(message = "{validation.service.slot.inventory.required}")
        InventoryType inventoryType,

        Integer capacity,

        @Valid
        List<CreateSlotUnitRequest> units,

        UUID optionId,

        List<UUID> optionIds
) {
    public SlotTemplateRequest(LocalTime startTime, LocalTime endTime, InventoryType inventoryType,
                               Integer capacity, List<CreateSlotUnitRequest> units) {
        this(startTime, endTime, inventoryType, capacity, units, null, null);
    }

    public SlotTemplateRequest(LocalTime startTime, LocalTime endTime, InventoryType inventoryType,
                               Integer capacity, List<CreateSlotUnitRequest> units, UUID optionId) {
        this(startTime, endTime, inventoryType, capacity, units, optionId, null);
    }

    public SlotTemplateRequest(LocalTime startTime, LocalTime endTime, InventoryType inventoryType,
                               Integer capacity, List<CreateSlotUnitRequest> units, List<UUID> optionIds) {
        this(startTime, endTime, inventoryType, capacity, units, null, optionIds);
    }

    public List<UUID> resolveOptionIds() {
        if (optionIds != null && !optionIds.isEmpty()) {
            return optionIds;
        }
        if (optionId != null) {
            return List.of(optionId);
        }
        return List.of();
    }
}
