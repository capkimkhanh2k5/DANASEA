package com.danasea.backend.modules.order.domain.ports;

import com.danasea.backend.modules.order.domain.models.PaymentCancelStatus;

public record PaymentCancelResult(
        boolean success,
        PaymentCancelStatus status,
        boolean alreadyCaptured,
        String message,
        String errorCode
) {
    public static PaymentCancelResult success(String message) {
        return new PaymentCancelResult(true, PaymentCancelStatus.CANCELED, false, message, null);
    }

    public static PaymentCancelResult alreadyCaptured(String message) {
        return new PaymentCancelResult(false, PaymentCancelStatus.ALREADY_CAPTURED, true, message, "ORDER_ALREADY_CAPTURED");
    }

    public static PaymentCancelResult unknown(String message, String errorCode) {
        return new PaymentCancelResult(false, PaymentCancelStatus.CANCEL_UNKNOWN, false, message, errorCode);
    }

    public static PaymentCancelResult failed(String message, String errorCode) {
        return new PaymentCancelResult(false, PaymentCancelStatus.CANCEL_UNKNOWN, false, message, errorCode);
    }
}
