package com.danasea.backend.modules.service.application.usecases;

import static com.danasea.backend.modules.service.domain.exceptions.ServiceSlotCreationException.invalid;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.LongStream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.presentation.dtos.BatchCreateServiceSlotsRequest;
import com.danasea.backend.modules.service.presentation.dtos.BatchCreateServiceSlotsResponse;
import com.danasea.backend.modules.service.presentation.dtos.CreateServiceSlotRequest;
import com.danasea.backend.modules.service.presentation.dtos.ServiceSlotResponse;
import com.danasea.backend.modules.service.presentation.dtos.SlotTemplateRequest;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BatchCreateServiceSlotsUseCase {

    private static final int MAX_DATES = 31;
    // Allow four departures per day with four units each across a full month.
    private static final int MAX_SLOTS = MAX_DATES * 4;
    private static final int MAX_ROWS = MAX_SLOTS * 5;

    private final CreateServiceSlotUseCase createServiceSlotUseCase;

    @Transactional
    public BatchCreateServiceSlotsResponse execute(UUID userId, UUID serviceId, BatchCreateServiceSlotsRequest request) {
        if (request == null) {
            throw invalid("error.bad_request");
        }

        List<LocalDate> dates = resolveDates(request, LocalDate.now(Booking.VIETNAM_ZONE));
        List<SlotTemplateRequest> templates = request.slotTemplates();
        int slotCount = validateWorkload(dates.size(), templates);
        List<CreateServiceSlotRequest> candidates = new ArrayList<>(slotCount);
        for (LocalDate date : dates) {
            for (SlotTemplateRequest template : templates) {
                candidates.add(new CreateServiceSlotRequest(
                        date, template.startTime(), template.endTime(), template.inventoryType(),
                        template.capacity(), template.units(), template.optionId(), template.optionIds()));
            }
        }

        List<ServiceSlotResponse> slots = createServiceSlotUseCase.createBatch(userId, serviceId, candidates);
        return new BatchCreateServiceSlotsResponse(slots.size(), slots);
    }

    private List<LocalDate> resolveDates(BatchCreateServiceSlotsRequest request, LocalDate today) {
        if (request.dates() != null) {
            if (request.startDate() != null || request.endDate() != null || request.daysOfWeek() != null) {
                throw invalid("error.slot_batch_date_mode");
            }
            if (request.dates().isEmpty() || request.dates().size() > MAX_DATES) {
                throw invalid("error.slot_batch_dates_count", MAX_DATES);
            }

            Set<LocalDate> dates = new HashSet<>();
            for (LocalDate date : request.dates()) {
                if (date == null || date.isBefore(today)) {
                    throw invalid("error.slot_batch_date_past");
                }
                if (!dates.add(date)) {
                    throw invalid("error.slot_batch_duplicate_date", date.toString());
                }
            }
            return dates.stream().sorted().toList();
        }

        LocalDate start = request.startDate();
        LocalDate end = request.endDate();
        if (start == null || end == null) {
            throw invalid("error.slot_batch_range_required");
        }
        if (start.isBefore(today)) {
            throw invalid("error.slot_batch_date_past");
        }
        if (start.isAfter(end)) {
            throw invalid("error.slot_batch_range_order");
        }
        long difference = ChronoUnit.DAYS.between(start, end);
        if (difference >= MAX_DATES) {
            throw invalid("error.slot_batch_range_limit", MAX_DATES);
        }
        if (request.daysOfWeek() == null || request.daysOfWeek().isEmpty()
                || request.daysOfWeek().stream().anyMatch(day -> day == null)) {
            throw invalid("validation.service.slot.batch.days.required");
        }

        List<LocalDate> dates = LongStream.rangeClosed(0, difference)
                .mapToObj(start::plusDays)
                .filter(date -> request.daysOfWeek().contains(date.getDayOfWeek()))
                .toList();
        if (dates.isEmpty()) {
            throw invalid("error.slot_batch_no_matching_days");
        }
        return dates;
    }

    private int validateWorkload(int dateCount, List<SlotTemplateRequest> templates) {
        if (templates == null || templates.isEmpty()) {
            throw invalid("validation.service.slot.batch.templates.required");
        }

        long slotCount = (long) dateCount * templates.size();
        if (slotCount > MAX_SLOTS) {
            throw invalid("error.slot_batch_slot_limit", MAX_SLOTS);
        }

        long unitRowsPerDate = 0;
        Set<LocalTime> startTimes = new HashSet<>();
        for (SlotTemplateRequest template : templates) {
            if (template == null || template.startTime() == null || template.endTime() == null) {
                throw invalid("error.slot_time_required");
            }
            if (template.resolveOptionIds().isEmpty()) {
                throw invalid("error.slot_option_required");
            }
            if (!startTimes.add(template.startTime())) {
                throw invalid("error.slot_batch_duplicate_time", template.startTime().toString());
            }
            if (InventoryType.SHARED_CAPACITY_UNITS.equals(template.inventoryType()) && template.units() != null) {
                unitRowsPerDate += template.units().size();
            }
        }
        if (slotCount + dateCount * unitRowsPerDate > MAX_ROWS) {
            throw invalid("error.slot_batch_row_limit", MAX_ROWS);
        }
        return (int) slotCount;
    }
}
