package com.danasea.backend.modules.service.presentation.dtos;

import java.time.LocalTime;
import java.util.List;

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
        List<CreateSlotUnitRequest> units
) {}
