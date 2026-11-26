package com.danasea.backend.modules.service.presentation.dtos;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.domain.models.OptionType;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import lombok.Builder;

@Builder
public record ServiceSlotResponse(
        UUID id,
        UUID serviceId,
        UUID optionId,
        OptionType optionType,
        String optionName,
        List<ServiceSlotOptionSummaryResponse> options,
        String optionBindingMode,
        List<ServiceSlotOptionSummaryResponse> legacyCompatibleOptions,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        SlotStatus status,
        InventoryType inventoryType,
        Integer capacity,
        Integer bookedCount,
        Integer availableCapacity,
        List<ServiceSlotUnitResponse> units
) {
    public ServiceSlotResponse(
            UUID id,
            UUID serviceId,
            UUID optionId,
            OptionType optionType,
            String optionName,
            List<ServiceSlotOptionSummaryResponse> options,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            SlotStatus status,
            InventoryType inventoryType,
            Integer capacity,
            Integer bookedCount,
            Integer availableCapacity,
            List<ServiceSlotUnitResponse> units) {
        this(id, serviceId, optionId, optionType, optionName, options, null, List.of(), date, startTime, endTime, status, inventoryType, capacity, bookedCount, availableCapacity, units);
    }

    public ServiceSlotResponse(
            UUID id,
            UUID serviceId,
            UUID optionId,
            OptionType optionType,
            String optionName,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            SlotStatus status,
            InventoryType inventoryType,
            Integer capacity,
            Integer bookedCount,
            Integer availableCapacity,
            List<ServiceSlotUnitResponse> units) {
        this(id, serviceId, optionId, optionType, optionName, List.of(), null, List.of(), date, startTime, endTime, status, inventoryType, capacity, bookedCount, availableCapacity, units);
    }

    public ServiceSlotResponse(
            UUID id,
            UUID serviceId,
            UUID optionId,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            SlotStatus status,
            InventoryType inventoryType,
            Integer capacity,
            Integer bookedCount,
            Integer availableCapacity,
            List<ServiceSlotUnitResponse> units) {
        this(id, serviceId, optionId, null, null, List.of(), null, List.of(), date, startTime, endTime, status, inventoryType, capacity, bookedCount, availableCapacity, units);
    }
}
