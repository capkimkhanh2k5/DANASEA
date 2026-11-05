package com.danasea.backend.modules.service.presentation.dtos;

import java.util.UUID;

import lombok.Builder;

@Builder
public record ServiceSlotUnitResponse(
        UUID id,
        Integer unitNumber,
        Integer capacity,
        Integer bookedCount,
        Integer availableCapacity,
        UUID equipmentId,
        boolean equipmentBlocked
) {}
