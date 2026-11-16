package com.danasea.backend.modules.service.presentation.dtos;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record BatchCreateServiceSlotsRequest(
        LocalDate startDate,

        LocalDate endDate,

        Set<DayOfWeek> daysOfWeek,

        @NotEmpty(message = "{validation.service.slot.batch.templates.required}")
        @Valid
        List<SlotTemplateRequest> slotTemplates,

        List<LocalDate> dates
) {
    public BatchCreateServiceSlotsRequest(LocalDate startDate, LocalDate endDate, Set<DayOfWeek> daysOfWeek,
                                          List<SlotTemplateRequest> slotTemplates) {
        this(startDate, endDate, daysOfWeek, slotTemplates, null);
    }
}
