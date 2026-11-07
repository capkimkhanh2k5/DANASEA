package com.danasea.backend.modules.order.domain.exceptions;

public class PaymentAttemptConflictException extends InvalidOrderStateException {
    private final String code;

    private PaymentAttemptConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static PaymentAttemptConflictException closedAttempt() {
        return new PaymentAttemptConflictException("PAYMENT_ATTEMPT_CLOSED",
                "This payment key belongs to another provider or a closed attempt.");
    }

    public static PaymentAttemptConflictException expiredWindow() {
        return new PaymentAttemptConflictException("PAYMENT_WINDOW_EXPIRED", "The payment deadline has expired.");
    }

    public String getCode() {
        return code;
    }
}
