package com.danasea.backend.modules.order.domain.models;

public enum PaymentAttemptState {
    CREATING,
    PENDING,
    ACTIVE,
    COMPLETED,
    FAILED,
    TERMINATED
}
