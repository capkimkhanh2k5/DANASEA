package com.danasea.backend.modules.service.presentation.dtos;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record BatchCreateServiceSlotsRequest(
        @NotNull(message = "{validation.service.slot.date.required}")
        @FutureOrPresent(message = "{validation.service.slot.date.future}")
        LocalDate startDate,

        @NotNull(message = "{validation.service.slot.date.required}")
        @FutureOrPresent(message = "{validation.service.slot.date.future}")
        LocalDate endDate,

        @NotEmpty(message = "At least one day of week must be selected")
        Set<DayOfWeek> daysOfWeek,

        @NotEmpty(message = "At least one slot template is required")
        @Valid
        List<SlotTemplateRequest> slotTemplates
) {}
