package com.danasea.backend.modules.service.presentation.dtos;

import java.util.List;
import java.util.UUID;

import com.danasea.backend.modules.service.domain.models.SlotStatus;
import jakarta.validation.Valid;

public record UpdateServiceSlotRequest(
        SlotStatus status,

        Integer capacity,

        @Valid
        List<CreateSlotUnitRequest> units,

        List<UUID> optionIds
) {
    public UpdateServiceSlotRequest(SlotStatus status, Integer capacity, List<CreateSlotUnitRequest> units) {
        this(status, capacity, units, null);
    }
}
