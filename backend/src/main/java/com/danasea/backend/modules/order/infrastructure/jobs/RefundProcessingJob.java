package com.danasea.backend.modules.order.infrastructure.jobs;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.communication.application.email.EmailRequest;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailPort;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;
import com.danasea.backend.modules.order.application.RefundProcessingService;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefundProcessingJob {

    private final JpaRefundRepository refundRepository;
    private final RefundProcessingService refundProcessingService;

    @Autowired(required = false)
    private TransactionalEmailPort transactionalEmailPort;

    @Scheduled(fixedDelayString = "${app.scheduler.refund-processing-delay:30s}")
    public void processPendingRefunds() {
        List<RefundJpaEntity> pendingRefunds = refundRepository
                .findByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
                        RefundStatus.PENDING, RefundProcessingService.MAX_RETRIES);

        if (pendingRefunds.isEmpty()) {
            return;
        }

        log.info("Found {} pending refund(s) to process", pendingRefunds.size());
        for (RefundJpaEntity refund : pendingRefunds) {
            if (refund.getNextAttemptAt() != null && refund.getNextAttemptAt().isAfter(OffsetDateTime.now())) {
                continue;
            }
            try {
                refundProcessingService.processRefund(refund.getId());
            } catch (Exception ex) {
                log.error("Unhandled error in RefundProcessingJob for refund {}: {}",
                        refund.getId(), ex.getMessage(), ex);
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.scheduler.refund-overdue-check-delay:5m}")
    @Transactional
    public void checkOverdueRefunds() {
        if (transactionalEmailPort == null) {
            return;
        }
        OffsetDateTime threshold = OffsetDateTime.now().minusHours(24);
        List<RefundJpaEntity> overdueRefunds = refundRepository
                .findByStatusAndCreatedAtLessThanOrderByCreatedAtAsc(RefundStatus.PENDING, threshold);

        if (overdueRefunds.isEmpty()) {
            return;
        }

        log.info("Found {} overdue pending refund(s) to notify finance", overdueRefunds.size());
        for (RefundJpaEntity refund : overdueRefunds) {
            try {
                OffsetDateTime now = OffsetDateTime.now();
                transactionalEmailPort.enqueue(new EmailRequest(
                        TransactionalEmailType.REFUND_NEEDS_ATTENTION,
                        "refund:" + refund.getId() + ":overdue",
                        "OPS:FINANCE",
                        now,
                        now,
                        null,
                        1,
                        Map.of(
                                "refundId", refund.getId().toString(),
                                "reason", "OVERDUE",
                                "lastError", refund.getLastError() != null ? refund.getLastError() : "PENDING_MORE_THAN_24H",
                                "amount", refund.getAmount() != null ? refund.getAmount().doubleValue() : 0.0
                        )
                ));
            } catch (Exception ex) {
                log.error("Failed to enqueue overdue alert for refund {}: {}", refund.getId(), ex.getMessage(), ex);
            }
        }
    }
}
