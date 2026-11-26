package com.danasea.backend.modules.service.presentation.dtos;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateSlotUnitRequest(
        @NotNull(message = "{validation.service.unit.number.required}")
        @Min(value = 1, message = "{validation.service.unit.number.min}")
        Integer unitNumber,

        @NotNull(message = "{validation.service.unit.capacity.required}")
        @Min(value = 1, message = "{validation.service.unit.capacity.min}")
        Integer capacity,

        UUID equipmentId
) {
    public CreateSlotUnitRequest(Integer unitNumber, Integer capacity) {
        this(unitNumber, capacity, null);
    }
}
