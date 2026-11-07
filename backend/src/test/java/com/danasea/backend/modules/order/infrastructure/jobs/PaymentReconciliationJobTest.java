package com.danasea.backend.modules.order.infrastructure.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.danasea.backend.modules.order.application.OrderPaymentService;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationJobTest {
    @Mock private JpaPaymentRepository repository;
    @Mock private OrderPaymentService service;
    private PaymentReconciliationJob job;

    @BeforeEach
    void setup() {
        job = new PaymentReconciliationJob(repository, service);
    }

    @Test
    void scansOneBoundedBatchAndDoesNotQueryCaptureTwice() {
        var first = payment();
        var second = payment();
        when(repository.findReconciliationCandidates(any(), any(), any(), any(), any())).thenReturn(List.of(first, second));
        job.reconcilePendingCaptures();
        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findReconciliationCandidates(eq(PaymentStatus.PENDING),
                eq(List.of(PaymentProvider.PAYPAL, PaymentProvider.VNPAY, PaymentProvider.PAYOS, PaymentProvider.ZALOPAY)),
                any(OffsetDateTime.class), any(OffsetDateTime.class), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(50);
        verify(service).reconcilePayment(first.getId());
        verify(service).reconcilePayment(second.getId());
        verify(service, never()).reconcilePayPalCapture(any());
    }

    @Test
    void continuesAfterIndividualFailure() {
        var first = payment();
        var second = payment();
        when(repository.findReconciliationCandidates(any(), any(), any(), any(), any())).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("Lookup unavailable")).when(service).reconcilePayment(first.getId());
        job.reconcilePendingCaptures();
        verify(service).reconcilePayment(second.getId());
    }

    @Test
    void emptyBatchDoesNotCallGatewayService() {
        when(repository.findReconciliationCandidates(any(), any(), any(), any(), any())).thenReturn(List.of());
        job.reconcilePendingCaptures();
        verifyNoInteractions(service);
    }

    private PaymentJpaEntity payment() {
        var payment = new PaymentJpaEntity();
        payment.setId(UUID.randomUUID());
        return payment;
    }
}
