package com.danasea.backend.modules.order.infrastructure.jobs;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.danasea.backend.modules.order.application.OrderPaymentService;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliationJob {
    private final JpaPaymentRepository paymentRepository;
    private final OrderPaymentService orderPaymentService;

    @Scheduled(fixedDelayString = "${app.scheduler.payment-reconciliation-delay:60s}")
    public void reconcilePendingCaptures() {
        OffsetDateTime now = OffsetDateTime.now();
        List<PaymentJpaEntity> pendingPayments = paymentRepository.findReconciliationCandidates(
                PaymentStatus.PENDING, List.of(PaymentProvider.PAYPAL, PaymentProvider.VNPAY, PaymentProvider.PAYOS, PaymentProvider.ZALOPAY),
                now.minusMinutes(2), now, PageRequest.of(0, 50));
        for (var payment : pendingPayments) {
            try {
                orderPaymentService.reconcilePayment(payment.getId());
            } catch (Exception ex) {
                log.warn("Pending payment reconciliation unresolved for payment {}: {}", payment.getId(), ex.getMessage());
            }
        }
    }
}
