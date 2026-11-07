package com.danasea.backend.modules.order.domain.ports;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentCaptureResult(
        boolean success,
        String captureId,
        BigDecimal amount,
        String currency,
        String status,
        String message,
        OffsetDateTime paidAt
) {
    public PaymentCaptureResult(boolean success, String captureId, BigDecimal amount, String currency,
            String status, String message) {
        this(success, captureId, amount, currency, status, message, null);
    }
}
