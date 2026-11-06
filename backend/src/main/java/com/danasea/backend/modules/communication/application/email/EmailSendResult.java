package com.danasea.backend.modules.communication.application.email;

import com.danasea.backend.modules.communication.domain.models.EmailDeliveryStatus;

public record EmailSendResult(
        EmailDeliveryStatus status,
        String errorCode,
        String errorMessage
) {
    public static EmailSendResult accepted() {
        return new EmailSendResult(EmailDeliveryStatus.ACCEPTED, null, null);
    }

    public static EmailSendResult retryableRejection(String errorCode, String errorMessage) {
        return new EmailSendResult(EmailDeliveryStatus.RETRYABLE_REJECTION, errorCode, errorMessage);
    }

    public static EmailSendResult permanentRejection(String errorCode, String errorMessage) {
        return new EmailSendResult(EmailDeliveryStatus.PERMANENT_REJECTION, errorCode, errorMessage);
    }

    public static EmailSendResult unknown(String errorCode, String errorMessage) {
        return new EmailSendResult(EmailDeliveryStatus.UNKNOWN, errorCode, errorMessage);
    }

    public static EmailSendResult skipped(String reasonCode, String message) {
        return new EmailSendResult(EmailDeliveryStatus.SKIPPED, reasonCode, message);
    }
}
