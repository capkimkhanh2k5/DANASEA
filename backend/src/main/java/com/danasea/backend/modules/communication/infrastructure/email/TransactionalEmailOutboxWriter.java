package com.danasea.backend.modules.communication.infrastructure.email;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.communication.application.email.EmailRequest;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailPort;
import com.danasea.backend.modules.communication.domain.models.EmailDeliveryMode;
import com.danasea.backend.modules.communication.infrastructure.email.repositories.JpaTransactionalEmailDeliveryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionalEmailOutboxWriter implements TransactionalEmailPort {

    private final JpaTransactionalEmailDeliveryRepository repository;
    private final TransactionalEmailProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(EmailRequest request) {
        if (request == null) {
            return;
        }

        EmailDeliveryMode mode = properties.getDeliveryMode();
        if (mode == EmailDeliveryMode.OFF) {
            log.debug("Transactional email is OFF; skipping enqueue for type={}, businessKey={}",
                    request.type(), request.businessKey());
            return;
        }

        // Live mode filters applied at producer
        if (mode == EmailDeliveryMode.LIVE) {
            if (!properties.isFamilyEnabled(request.family())) {
                log.debug("Family {} is not enabled in LIVE mode; skipping enqueue for {}",
                        request.family(), request.businessKey());
                return;
            }

            OffsetDateTime liveFrom = properties.getLiveFrom(request.family());
            if (liveFrom != null && request.occurredAt().isBefore(liveFrom)) {
                log.debug("occurredAt {} is before liveFrom {} for family {}; skipping enqueue",
                        request.occurredAt(), liveFrom, request.family());
                return;
            }

            var pilots = properties.getPilotRecipients();
            if (!pilots.isEmpty() && !pilots.contains(request.recipientKey())) {
                log.debug("recipientKey {} is not in pilot recipients; skipping enqueue", request.recipientKey());
                return;
            }
        }

        String payloadJson = "{}";
        try {
            if (request.payload() != null && !request.payload().isEmpty()) {
                payloadJson = objectMapper.writeValueAsString(request.payload());
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize transactional email payload for businessKey={}", request.businessKey(), e);
            throw new IllegalArgumentException("Invalid payload for email request", e);
        }

        OffsetDateTime now = OffsetDateTime.now();
        UUID id = UUID.randomUUID();
        repository.insertOnConflictDoNothing(
                id,
                request.type().name(),
                request.businessKey(),
                request.recipientKey(),
                request.family().name(),
                mode.name(),
                request.payloadVersion(),
                payloadJson,
                request.occurredAt(),
                request.dueAt(),
                request.expiresAt(),
                now
        );
    }
}
