package com.danasea.backend.modules.communication.domain.models;

public enum EmailDeliveryStatus {
    PENDING,
    ACCEPTED,
    RETRYABLE_REJECTION,
    PERMANENT_REJECTION,
    UNKNOWN,
    SKIPPED;

    public boolean isTerminal() {
        return this == ACCEPTED || this == PERMANENT_REJECTION || this == UNKNOWN || this == SKIPPED;
    }
}
