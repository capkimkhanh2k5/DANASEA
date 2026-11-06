package com.danasea.backend.modules.communication.infrastructure.email;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.communication.application.email.EmailSendResult;
import com.danasea.backend.modules.communication.domain.models.EmailDeliveryStatus;
import com.danasea.backend.modules.communication.infrastructure.email.entities.TransactionalEmailDeliveryJpaEntity;
import com.danasea.backend.modules.communication.infrastructure.email.repositories.JpaTransactionalEmailDeliveryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionalEmailClaimService {

    private final JpaTransactionalEmailDeliveryRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<TransactionalEmailDeliveryJpaEntity> claimForDelivery(
            UUID id,
            String leaseToken,
            OffsetDateTime leasedUntil,
            OffsetDateTime now
    ) {
        Optional<TransactionalEmailDeliveryJpaEntity> entityOpt = repository.findByIdAndLockForClaim(id, now);
        if (entityOpt.isEmpty()) {
            return Optional.empty();
        }

        TransactionalEmailDeliveryJpaEntity entity = entityOpt.get();
        entity.setLeaseToken(leaseToken);
        entity.setLeasedUntil(leasedUntil);
        entity.setAttemptCount(entity.getAttemptCount() + 1);
        entity.setUpdatedAt(now);
        repository.saveAndFlush(entity);

        return Optional.of(entity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFinalOutcome(
            UUID id,
            String leaseToken,
            EmailSendResult result,
            OffsetDateTime now
    ) {
        repository.findById(id).ifPresent(entity -> {
            if (leaseToken != null && !leaseToken.equals(entity.getLeaseToken())) {
                log.warn("Cannot finalize email delivery id={}: lease token mismatch (expected={}, current={})",
                        id, leaseToken, entity.getLeaseToken());
                return;
            }

            entity.setStatus(result.status());
            entity.setLeaseToken(null);
            entity.setLeasedUntil(null);
            entity.setLastErrorCode(result.errorCode());
            entity.setLastErrorMessage(result.errorMessage());
            entity.setUpdatedAt(now);

            if (result.status() == EmailDeliveryStatus.ACCEPTED) {
                entity.setAcceptedAt(now);
            }

            repository.saveAndFlush(entity);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRetryableFailure(
            UUID id,
            String leaseToken,
            String errorCode,
            String errorMessage,
            OffsetDateTime nextAttemptAt,
            boolean isPermanent,
            OffsetDateTime now
    ) {
        repository.findById(id).ifPresent(entity -> {
            if (leaseToken != null && !leaseToken.equals(entity.getLeaseToken())) {
                log.warn("Cannot record retry for email delivery id={}: lease token mismatch", id);
                return;
            }

            if (isPermanent) {
                entity.setStatus(EmailDeliveryStatus.PERMANENT_REJECTION);
                entity.setLastErrorCode(errorCode != null ? errorCode : "MAX_ATTEMPTS_EXCEEDED");
            } else {
                entity.setStatus(EmailDeliveryStatus.RETRYABLE_REJECTION);
                entity.setLastErrorCode(errorCode);
                entity.setNextAttemptAt(nextAttemptAt);
            }

            entity.setLeaseToken(null);
            entity.setLeasedUntil(null);
            entity.setLastErrorMessage(errorMessage);
            entity.setUpdatedAt(now);

            repository.saveAndFlush(entity);
        });
    }
}
