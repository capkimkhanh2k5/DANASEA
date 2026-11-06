package com.danasea.backend.modules.communication.application.email;

import java.time.OffsetDateTime;
import java.util.Map;

import com.danasea.backend.modules.communication.domain.models.TransactionalEmailFamily;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;

import lombok.Builder;

@Builder(toBuilder = true)
public record EmailRequest(
        TransactionalEmailType type,
        String businessKey,
        String recipientKey,
        OffsetDateTime occurredAt,
        OffsetDateTime dueAt,
        OffsetDateTime expiresAt,
        int payloadVersion,
        Map<String, Object> payload
) {
    public EmailRequest {
        if (type == null) {
            throw new IllegalArgumentException("TransactionalEmailType must not be null");
        }
        if (businessKey == null || businessKey.isBlank()) {
            throw new IllegalArgumentException("businessKey must not be null or blank");
        }
        if (recipientKey == null || recipientKey.isBlank()) {
            throw new IllegalArgumentException("recipientKey must not be null or blank");
        }
        if (occurredAt == null) {
            occurredAt = OffsetDateTime.now();
        }
        if (dueAt == null) {
            dueAt = occurredAt;
        }
        if (payloadVersion <= 0) {
            payloadVersion = 1;
        }
        if (payload == null) {
            payload = Map.of();
        }
    }

    public TransactionalEmailFamily family() {
        return type.getFamily();
    }
}
