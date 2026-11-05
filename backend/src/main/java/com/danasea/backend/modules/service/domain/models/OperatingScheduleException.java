package com.danasea.backend.modules.service.domain.models;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record OperatingScheduleException(UUID id, LocalDate dateFrom, LocalDate dateTo,
                                         LocalTime startTime, String reason) {
    public OperatingScheduleException {
        if (dateFrom == null || dateTo == null || dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("Schedule exception must have a valid date range");
        }
        if (startTime != null && startTime.getNano() != 0) {
            throw new IllegalArgumentException("Schedule exception start time must have whole-second precision");
        }
        if (reason == null || reason.isBlank() || reason.strip().length() > 500) {
            throw new IllegalArgumentException("Schedule exception reason must contain 1 to 500 characters");
        }
        reason = reason.strip();
    }

    public boolean matches(LocalDate date, LocalTime time) {
        return !date.isBefore(dateFrom) && !date.isAfter(dateTo) && (startTime == null || startTime.equals(time));
    }
}
