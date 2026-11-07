package com.danasea.backend.modules.order.domain.models;

public enum PaymentCancelStatus {
    NONE,
    CANCEL_REQUESTED,
    CANCELED,
    CANCEL_UNKNOWN,
    ALREADY_CAPTURED
}
