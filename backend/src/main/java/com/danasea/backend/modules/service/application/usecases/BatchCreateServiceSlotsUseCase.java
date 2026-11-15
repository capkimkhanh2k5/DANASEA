package com.danasea.backend.modules.service.application.usecases;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.service.presentation.dtos.BatchCreateServiceSlotsRequest;
import com.danasea.backend.modules.service.presentation.dtos.BatchCreateServiceSlotsResponse;
import com.danasea.backend.modules.service.presentation.dtos.CreateServiceSlotRequest;
import com.danasea.backend.modules.service.presentation.dtos.ServiceSlotResponse;
import com.danasea.backend.modules.service.presentation.dtos.SlotTemplateRequest;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BatchCreateServiceSlotsUseCase {

    private final CreateServiceSlotUseCase createServiceSlotUseCase;

    @Transactional
    public BatchCreateServiceSlotsResponse execute(UUID userId, UUID serviceId, BatchCreateServiceSlotsRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        if (request.startDate() == null || request.endDate() == null) {
            throw new IllegalArgumentException("Start date and end date are required");
        }
        if (request.startDate().isAfter(request.endDate())) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }
        if (request.startDate().plusDays(31).isBefore(request.endDate())) {
            throw new IllegalArgumentException("Batch date range cannot exceed 31 days");
        }
        if (request.daysOfWeek() == null || request.daysOfWeek().isEmpty()) {
            throw new IllegalArgumentException("At least one day of week must be selected");
        }
        if (request.slotTemplates() == null || request.slotTemplates().isEmpty()) {
            throw new IllegalArgumentException("At least one slot template is required");
        }

        List<SlotTemplateRequest> templates = request.slotTemplates();
        Set<java.time.LocalTime> templateStartTimes = new HashSet<>();
        for (SlotTemplateRequest template : templates) {
            if (template == null || template.startTime() == null || template.endTime() == null) {
                throw new IllegalArgumentException("Slot template start and end times are required");
            }
            if (!template.startTime().isBefore(template.endTime())) {
                throw new IllegalArgumentException("Template start time must be before end time: " + template.startTime() + " - " + template.endTime());
            }
            if (!templateStartTimes.add(template.startTime())) {
                throw new IllegalArgumentException("Duplicate start time in slot templates: " + template.startTime());
            }
        }

        List<CreateServiceSlotRequest> candidates = new ArrayList<>();
        for (LocalDate date = request.startDate(); !date.isAfter(request.endDate()); date = date.plusDays(1)) {
            if (request.daysOfWeek().contains(date.getDayOfWeek())) {
                for (SlotTemplateRequest template : templates) {
                    candidates.add(new CreateServiceSlotRequest(
                            date,
                            template.startTime(),
                            template.endTime(),
                            template.inventoryType(),
                            template.capacity(),
                            template.units()
                    ));
                }
            }
        }

        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No matching days of week found in the specified date range");
        }

        List<ServiceSlotResponse> createdResponses = new ArrayList<>(candidates.size());
        for (CreateServiceSlotRequest candidate : candidates) {
            ServiceSlotResponse response = createServiceSlotUseCase.execute(userId, serviceId, candidate);
            createdResponses.add(response);
        }

        return new BatchCreateServiceSlotsResponse(createdResponses.size(), createdResponses);
    }
}
