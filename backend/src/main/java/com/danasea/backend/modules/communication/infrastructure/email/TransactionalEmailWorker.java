package com.danasea.backend.modules.communication.infrastructure.email;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.danasea.backend.modules.communication.application.email.EmailSendResult;
import com.danasea.backend.modules.communication.application.email.RenderedEmail;
import com.danasea.backend.modules.communication.application.email.ResolvedRecipient;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailRecipientResolver;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailRenderer;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailSender;
import com.danasea.backend.modules.communication.domain.exceptions.SupersededEmailException;
import com.danasea.backend.modules.communication.domain.models.EmailDeliveryMode;
import com.danasea.backend.modules.communication.domain.models.EmailDeliveryStatus;
import com.danasea.backend.modules.communication.infrastructure.email.entities.TransactionalEmailDeliveryJpaEntity;
import com.danasea.backend.modules.communication.infrastructure.email.repositories.JpaTransactionalEmailDeliveryRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TransactionalEmailWorker {

    private final JpaTransactionalEmailDeliveryRepository repository;
    private final TransactionalEmailClaimService claimService;
    private final TransactionalEmailRecipientResolver recipientResolver;
    private final TransactionalEmailRenderer renderer;
    private final TransactionalEmailSender smtpSender;
    private final TransactionalEmailSender captureSender;
    private final TransactionalEmailProperties properties;

    public TransactionalEmailWorker(
            JpaTransactionalEmailDeliveryRepository repository,
            TransactionalEmailClaimService claimService,
            TransactionalEmailRecipientResolver recipientResolver,
            TransactionalEmailRenderer renderer,
            @Qualifier("smtpTransactionalEmailSender") TransactionalEmailSender smtpSender,
            @Qualifier("captureTransactionalEmailSender") TransactionalEmailSender captureSender,
            TransactionalEmailProperties properties
    ) {
        this.repository = repository;
        this.claimService = claimService;
        this.recipientResolver = recipientResolver;
        this.renderer = renderer;
        this.smtpSender = smtpSender;
        this.captureSender = captureSender;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.transactional-email.worker.poll-interval-ms:15000}")
    public void runWorkerCycle() {
        if (!properties.isWorkerEnabled()) {
            return;
        }

        EmailDeliveryMode mode = properties.getDeliveryMode();
        if (mode == EmailDeliveryMode.OFF) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();

        // 1. Reclaim expired leases -> UNKNOWN for manual reconciliation
        try {
            int expiredCount = repository.markExpiredLeasesAsUnknown(now);
            if (expiredCount > 0) {
                log.warn("Reclaimed {} expired transactional email lease(s) and set status to UNKNOWN", expiredCount);
            }
        } catch (Exception e) {
            log.error("Failed to mark expired leases as UNKNOWN", e);
        }

        // 2. Fetch candidate IDs up to batch size (max 20)
        int batchSize = properties.getWorker().getBatchSize();
        List<UUID> dueIds;
        try {
            dueIds = repository.findDueEmailIds(mode.name(), now, batchSize);
        } catch (Exception e) {
            log.error("Failed to query due transactional emails for mode {}", mode, e);
            return;
        }

        if (dueIds.isEmpty()) {
            return;
        }

        // 3. Claim and process each email individually
        for (UUID id : dueIds) {
            processIndividualEmail(id, mode);
        }
    }

    public void processIndividualEmail(UUID id, EmailDeliveryMode mode) {
        OffsetDateTime now = OffsetDateTime.now();
        String leaseToken = UUID.randomUUID().toString();
        OffsetDateTime leasedUntil = now.plusSeconds(properties.getWorker().getLeaseDurationSeconds());

        Optional<TransactionalEmailDeliveryJpaEntity> claimedOpt =
                claimService.claimForDelivery(id, leaseToken, leasedUntil, now);

        if (claimedOpt.isEmpty()) {
            // Already claimed by another worker or condition changed
            return;
        }

        TransactionalEmailDeliveryJpaEntity entity = claimedOpt.get();

        try {
            deliverEmail(entity, leaseToken, mode);
        } catch (SupersededEmailException e) {
            log.info("Email delivery id={} is superseded (reason={}): {}. Marking SKIPPED.",
                    id, e.getReasonCode(), e.getMessage());
            claimService.recordFinalOutcome(
                    id,
                    leaseToken,
                    EmailSendResult.skipped(e.getReasonCode(), e.getMessage()),
                    OffsetDateTime.now()
            );
        } catch (Exception e) {
            log.error("Unexpected error during delivery execution of email id={}", id, e);
            // Outcome uncertain
            claimService.recordFinalOutcome(
                    id,
                    leaseToken,
                    EmailSendResult.unknown("DELIVERY_EXECUTION_ERROR", e.getMessage()),
                    OffsetDateTime.now()
            );
        }
    }

    private void deliverEmail(
            TransactionalEmailDeliveryJpaEntity entity,
            String leaseToken,
            EmailDeliveryMode mode
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        UUID id = entity.getId();

        // Expiry check
        if (entity.getExpiresAt() != null && now.isAfter(entity.getExpiresAt())) {
            log.info("Email delivery id={} has expired (expiresAt={}). Marking SKIPPED.", id, entity.getExpiresAt());
            claimService.recordFinalOutcome(
                    id,
                    leaseToken,
                    EmailSendResult.skipped("EXPIRED", "Email expired before delivery"),
                    now
            );
            return;
        }

        // Live mode eligibility checks
        if (mode == EmailDeliveryMode.LIVE) {
            if (!properties.isFamilyEnabled(entity.getFamily())) {
                log.info("Email delivery id={} family {} is disabled in LIVE mode. Marking SKIPPED.", id, entity.getFamily());
                claimService.recordFinalOutcome(
                        id,
                        leaseToken,
                        EmailSendResult.skipped("FAMILY_DISABLED", "Family not enabled in LIVE mode"),
                        now
                );
                return;
            }

            OffsetDateTime liveFrom = properties.getLiveFrom(entity.getFamily());
            if (liveFrom != null && entity.getOccurredAt().isBefore(liveFrom)) {
                log.info("Email delivery id={} occurredAt {} is before liveFrom {}. Marking SKIPPED.",
                        id, entity.getOccurredAt(), liveFrom);
                claimService.recordFinalOutcome(
                        id,
                        leaseToken,
                        EmailSendResult.skipped("BEFORE_LIVE_FROM", "Event occurred before liveFrom cutoff"),
                        now
                );
                return;
            }

            var pilots = properties.getPilotRecipients();
            if (!pilots.isEmpty() && !pilots.contains(entity.getRecipientKey())) {
                log.info("Email delivery id={} recipientKey {} not in pilot list. Marking SKIPPED.",
                        id, entity.getRecipientKey());
                claimService.recordFinalOutcome(
                        id,
                        leaseToken,
                        EmailSendResult.skipped("PILOT_FILTERED", "Recipient not in pilot allowlist"),
                        now
                );
                return;
            }
        }

        // Resolve recipient
        Optional<ResolvedRecipient> recipientOpt;
        try {
            recipientOpt = recipientResolver.resolve(entity.getRecipientKey());
        } catch (Exception e) {
            log.warn("Temporary recipient resolution error for email id={}: {}", id, e.getMessage());
            handleRetryOrPermanentRejection(entity, leaseToken, "RECIPIENT_LOOKUP_ERROR", e.getMessage(), now);
            return;
        }

        if (recipientOpt.isEmpty()) {
            String reason = (entity.getRecipientKey() != null && entity.getRecipientKey().startsWith("OPS:"))
                    ? "UNCONFIGURED_OPS_EMAIL"
                    : "RECIPIENT_NOT_FOUND";
            log.warn("Recipient could not be resolved for recipientKey={}. Marking SKIPPED ({}).", entity.getRecipientKey(), reason);
            claimService.recordFinalOutcome(
                    id,
                    leaseToken,
                    EmailSendResult.skipped(reason, "Recipient could not be resolved"),
                    now
            );
            return;
        }

        ResolvedRecipient recipient = recipientOpt.get();

        // Check if verified
        if (!recipient.isEmailVerified()) {
            log.info("Recipient {} has unverified email. Marking SKIPPED / UNVERIFIED_EMAIL.", entity.getRecipientKey());
            claimService.recordFinalOutcome(
                    id,
                    leaseToken,
                    EmailSendResult.skipped("UNVERIFIED_EMAIL", "Recipient email is unverified"),
                    now
            );
            return;
        }

        // Check if account locked
        if (recipient.isLocked()) {
            if (!entity.getType().isPermittedWhenAccountLocked()) {
                log.info("Recipient {} is locked and email type {} is not permitted. Marking SKIPPED / ACCOUNT_LOCKED.",
                        entity.getRecipientKey(), entity.getType());
                claimService.recordFinalOutcome(
                        id,
                        leaseToken,
                        EmailSendResult.skipped("ACCOUNT_LOCKED", "Non-critical email skipped for locked account"),
                        now
                );
                return;
            }
        }

        // Render email
        RenderedEmail rendered = renderer.render(entity, recipient);

        // Select sender based on mode
        TransactionalEmailSender sender = (mode == EmailDeliveryMode.CAPTURE) ? captureSender : smtpSender;
        EmailSendResult result = sender.send(rendered);

        // Handle result
        if (result.status() == EmailDeliveryStatus.ACCEPTED
                || result.status() == EmailDeliveryStatus.UNKNOWN
                || result.status() == EmailDeliveryStatus.PERMANENT_REJECTION
                || result.status() == EmailDeliveryStatus.SKIPPED) {
            claimService.recordFinalOutcome(id, leaseToken, result, OffsetDateTime.now());
        } else if (result.status() == EmailDeliveryStatus.RETRYABLE_REJECTION) {
            handleRetryOrPermanentRejection(entity, leaseToken, result.errorCode(), result.errorMessage(), OffsetDateTime.now());
        }
    }

    private void handleRetryOrPermanentRejection(
            TransactionalEmailDeliveryJpaEntity entity,
            String leaseToken,
            String errorCode,
            String errorMessage,
            OffsetDateTime now
    ) {
        int currentAttempt = entity.getAttemptCount(); // Already incremented at claim time
        int maxAttempts = properties.getWorker().getMaxAttempts();

        if (currentAttempt >= maxAttempts) {
            log.warn("Email delivery id={} exceeded max attempts ({}/{}). Transitioning to PERMANENT_REJECTION.",
                    entity.getId(), currentAttempt, maxAttempts);
            claimService.recordRetryableFailure(
                    entity.getId(),
                    leaseToken,
                    "MAX_ATTEMPTS_EXCEEDED",
                    errorMessage,
                    null,
                    true,
                    now
            );
        } else {
            Duration backoff = calculateBackoff(currentAttempt);
            OffsetDateTime nextAttemptAt = now.plus(backoff);
            log.info("Email delivery id={} failed attempt {}/{}. Scheduling retry at {} (in {})",
                    entity.getId(), currentAttempt, maxAttempts, nextAttemptAt, backoff);
            claimService.recordRetryableFailure(
                    entity.getId(),
                    leaseToken,
                    errorCode,
                    errorMessage,
                    nextAttemptAt,
                    false,
                    now
            );
        }
    }

    private Duration calculateBackoff(int attempt) {
        // Attempt 1 -> 1 min, Attempt 2 -> 5 min, Attempt 3 -> 15 min
        return switch (attempt) {
            case 1 -> Duration.ofMinutes(1);
            case 2 -> Duration.ofMinutes(5);
            default -> Duration.ofMinutes(15);
        };
    }
}
