package com.danasea.backend.modules.service.domain.exceptions;

import java.time.LocalDate;
import java.time.LocalTime;

import com.danasea.backend.shared.i18n.LocalizedException;

public final class ServiceSlotCreationException extends LocalizedException {
    private final boolean conflict;

    private ServiceSlotCreationException(String code, String messageKey, boolean conflict, Object... args) {
        super(code, messageKey, args);
        this.conflict = conflict;
    }

    public static ServiceSlotCreationException invalid(String messageKey, Object... args) {
        return new ServiceSlotCreationException("BAD_REQUEST", messageKey, false, args);
    }

    public static ServiceSlotCreationException conflict(String code, String messageKey, LocalDate date, LocalTime startTime) {
        return new ServiceSlotCreationException(code, messageKey, true, date.toString(), startTime.toString());
    }

    public boolean isConflict() {
        return conflict;
    }
}
