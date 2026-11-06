package com.danasea.backend.modules.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.danasea.backend.modules.booking.domain.models.BookingStatus;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.order.domain.exceptions.PaymentGatewayException;
import com.danasea.backend.modules.order.domain.models.*;
import com.danasea.backend.modules.order.domain.ports.*;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.*;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.*;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;

class RefundProcessingServiceTest {
    private RefundProcessingService service;
    private JpaRefundRepository refunds;
    private JpaPaymentRepository payments;
    private JpaServiceSlotRepository slots;
    private JpaSubOrderRepository subs;
    private PaymentGatewayPort gateway;
    private RefundJpaEntity refund;
    private PaymentJpaEntity payment;
    private SubOrderJpaEntity sub;
    private MasterOrderJpaEntity order;
    private BookingJpaEntity booking;

    @BeforeEach
    void setup() {
        refunds = mock(JpaRefundRepository.class);
        payments = mock(JpaPaymentRepository.class);
        slots = mock(JpaServiceSlotRepository.class);
        subs = mock(JpaSubOrderRepository.class);
        var orders = mock(JpaMasterOrderRepository.class);
        var bookings = mock(JpaBookingRepository.class);
        gateway = mock(PaymentGatewayPort.class);
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(inv -> new SimpleTransactionStatus());
        service = new RefundProcessingService(refunds, subs, orders, payments, bookings, slots, gateway, transactions);
        booking = new BookingJpaEntity(); booking.setId(UUID.randomUUID()); booking.setStatus(BookingStatus.CONFIRMED);
        order = new MasterOrderJpaEntity(); order.setId(UUID.randomUUID()); order.setBookingId(booking.getId()); order.setStatus(MasterOrderStatus.PAID);
        sub = new SubOrderJpaEntity(); sub.setId(UUID.randomUUID()); sub.setMasterOrderId(order.getId());
        sub.setStatus(SubOrderStatus.CONFIRMED); sub.setSlotId(UUID.randomUUID()); sub.setQuantity(2);
        payment = new PaymentJpaEntity(); payment.setId(UUID.randomUUID()); payment.setMasterOrderId(order.getId());
        payment.setProvider(PaymentProvider.PAYPAL); payment.setStatus(PaymentStatus.SUCCESS);
        payment.setProviderTransactionId("CAPTURE-ORIGINAL"); payment.setProviderOrderId("ORDER-ORIGINAL");
        payment.setAmount(new BigDecimal("500000")); payment.setProviderAmount(new BigDecimal("19.31")); payment.setProviderCurrency("USD");
        refund = new RefundJpaEntity(); refund.setId(UUID.randomUUID()); refund.setSubOrderId(sub.getId());
        refund.setAmount(payment.getAmount()); refund.setRefundPercentage(new BigDecimal("100")); refund.setStatus(RefundStatus.PENDING);
        refund.setIdempotencyKey("client-key-shared-across-sub-orders");
        when(refunds.findByIdForUpdate(refund.getId())).thenReturn(Optional.of(refund));
        when(subs.findById(sub.getId())).thenReturn(Optional.of(sub));
        when(subs.findByMasterOrderId(order.getId())).thenReturn(List.of(sub));
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(payments.findByMasterOrderIdOrderByCreatedAtDesc(order.getId())).thenReturn(List.of(payment));
        when(payments.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(refunds.findBySubOrderIdInAndStatus(List.of(sub.getId()), RefundStatus.PROCESSED)).thenAnswer(inv ->
                refund.getStatus() == RefundStatus.PROCESSED ? List.of(refund) : List.of());
    }

    private RefundResult result(GatewayRefundStatus status) {
        return new RefundResult(status == GatewayRefundStatus.COMPLETED, "REFUND-ORIGINAL", refund.getProviderAmount(),
                status.name(), status, refund.getProviderCurrency());
    }

    @Test
    void completedRefundUpdatesAllStatesOnce() {
        when(gateway.requestRefund(any(GatewayRefundRequest.class))).thenAnswer(inv -> result(GatewayRefundStatus.COMPLETED));
        assertThat(service.processRefund(refund.getId())).isTrue();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PROCESSED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(sub.getStatus()).isEqualTo(SubOrderStatus.REFUNDED);
        assertThat(order.getStatus()).isEqualTo(MasterOrderStatus.CANCELLED);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(refund.getGatewayRequestId()).isNotEqualTo(refund.getIdempotencyKey());
        assertThat(service.processRefund(refund.getId())).isTrue();
        verify(gateway, times(1)).requestRefund(any(GatewayRefundRequest.class));
        verify(slots, times(1)).decrementBookedCount(sub.getSlotId(), 2);
    }

    @Test
    void completedDuplicateCaptureRefundUpdatesOnlyTheExtraPayment() {
        order.setPaymentStatus(PaymentOrderStatus.PAID);
        refund.setSubOrderId(null);
        refund.setPaymentId(payment.getId());
        refund.setReason(RefundReason.SYSTEM_FAULT);
        when(gateway.requestRefund(any(GatewayRefundRequest.class))).thenAnswer(inv -> {
            GatewayRefundRequest request = inv.getArgument(0);
            assertThat(request.fullRefund()).isTrue();
            assertThat(request.amount()).isEqualByComparingTo("19.31");
            assertThat(request.transactionId()).isEqualTo("CAPTURE-ORIGINAL");
            return result(GatewayRefundStatus.COMPLETED);
        });

        assertThat(service.processRefund(refund.getId())).isTrue();
        assertThat(service.processRefund(refund.getId())).isTrue();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PROCESSED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(order.getStatus()).isEqualTo(MasterOrderStatus.PAID);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentOrderStatus.PAID);
        assertThat(sub.getStatus()).isEqualTo(SubOrderStatus.CONFIRMED);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verifyNoInteractions(slots);
        verify(gateway).requestRefund(any(GatewayRefundRequest.class));
    }

    @Test
    void refundAllocationIgnoresOtherAttemptsAndCountsLegacySubOrderRefunds() {
        refund.setPaymentId(payment.getId());
        refund.setAmount(new BigDecimal("400000"));
        RefundJpaEntity otherAttempt = new RefundJpaEntity();
        otherAttempt.setId(UUID.randomUUID());
        otherAttempt.setPaymentId(UUID.randomUUID());
        otherAttempt.setStatus(RefundStatus.PROCESSED);
        otherAttempt.setAmount(new BigDecimal("500000"));
        RefundJpaEntity legacy = new RefundJpaEntity();
        legacy.setId(UUID.randomUUID());
        legacy.setStatus(RefundStatus.PROCESSED);
        legacy.setAmount(new BigDecimal("100000"));
        legacy.setProviderAmount(new BigDecimal("3.86"));
        when(refunds.findBySubOrderIdIn(List.of(sub.getId()))).thenReturn(List.of(otherAttempt, legacy));
        when(refunds.findBySubOrderIdInAndStatus(List.of(sub.getId()), RefundStatus.PROCESSED))
                .thenReturn(List.of(refund, otherAttempt, legacy));
        when(gateway.requestRefund(any(GatewayRefundRequest.class))).thenAnswer(inv -> {
            GatewayRefundRequest request = inv.getArgument(0);
            assertThat(request.amount()).isEqualByComparingTo("15.45");
            return result(GatewayRefundStatus.COMPLETED);
        });

        assertThat(service.processRefund(refund.getId())).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void completedAllocatedRefundReleasesTheBookingItemOnce() {
        sub.setBookingItemId(UUID.randomUUID());
        when(gateway.requestRefund(any(GatewayRefundRequest.class)))
                .thenAnswer(inv -> result(GatewayRefundStatus.COMPLETED));
        assertThat(service.processRefund(refund.getId())).isTrue();
        assertThat(service.processRefund(refund.getId())).isTrue();
        verify(slots, times(1)).releaseBookingItemCapacity(sub.getBookingItemId());
        verify(slots, never()).decrementBookedCount(any(), anyInt());
    }

    @Test
    void gatewayPendingRemainsPendingAndReconcilesUsingTheSameOperation() {
        when(gateway.requestRefund(any(GatewayRefundRequest.class))).thenAnswer(inv -> result(GatewayRefundStatus.PENDING));
        assertThat(service.processRefund(refund.getId())).isFalse();
        String operation = refund.getGatewayRequestId();
        assertThat(refund.getProviderRefundId()).isEqualTo("REFUND-ORIGINAL");
        assertThat(refund.getProcessedAt()).isNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verifyNoInteractions(slots);
        when(gateway.queryRefund(any(GatewayRefundRequest.class), any())).thenAnswer(inv -> {
            GatewayRefundRequest request = inv.getArgument(0);
            assertThat(request.requestId()).isEqualTo(operation);
            return result(GatewayRefundStatus.COMPLETED);
        });
        assertThat(service.processRefund(refund.getId())).isTrue();
        verify(gateway, times(1)).requestRefund(any(GatewayRefundRequest.class));
        verify(gateway).queryRefund(any(GatewayRefundRequest.class), eq("REFUND-ORIGINAL"));
    }

    @Test
    void queryFailureNeverResendsOrLosesTheTimeoutMarker() {
        when(gateway.requestRefund(any(GatewayRefundRequest.class))).thenThrow(new PaymentGatewayException("Read timeout"));
        assertThat(service.processRefund(refund.getId())).isFalse();
        when(gateway.queryRefund(any(GatewayRefundRequest.class), any())).thenThrow(new PaymentGatewayException("Lookup unavailable"));
        for (int i = 0; i < 6; i++) {
            assertThat(service.processRefund(refund.getId())).isFalse();
        }
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(refund.getLastError()).contains("GATEWAY_TIMEOUT_AWAITING_VERIFICATION");
        assertThat(refund.getRetryCount()).isZero();
        assertThat(refund.getNextAttemptAt()).isAfter(OffsetDateTime.now());
        verify(gateway, times(1)).requestRefund(any(GatewayRefundRequest.class));
        verify(gateway, times(6)).queryRefund(any(GatewayRefundRequest.class), any());
    }

    @Test
    void legacyTimeoutWithoutOperationIdStillOnlyQueries() {
        refund.setLastError("GATEWAY_TIMEOUT_AWAITING_VERIFICATION");
        when(gateway.queryRefund(any(GatewayRefundRequest.class), any())).thenReturn(
                new RefundResult(false, null, null, "Not identified", GatewayRefundStatus.UNKNOWN, null));
        service.processRefund(refund.getId());
        verify(gateway, never()).requestRefund(any(GatewayRefundRequest.class));
        verify(gateway).queryRefund(any(GatewayRefundRequest.class), any());
        // Legacy ambiguous requests cannot be safely resubmitted with a newly generated ID.
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
    }

    @Test
    void definitiveProviderFailureDoesNotBecomeProcessed() {
        when(gateway.requestRefund(any(GatewayRefundRequest.class))).thenAnswer(inv -> result(GatewayRefundStatus.FAILED));
        assertThat(service.processRefund(refund.getId())).isFalse();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(sub.getStatus()).isEqualTo(SubOrderStatus.CONFIRMED);
        verifyNoInteractions(slots);
    }

    @Test
    void mismatchedProviderMoneyRemainsPendingForReview() {
        when(gateway.requestRefund(any(GatewayRefundRequest.class))).thenReturn(
                new RefundResult(true, "REFUND-WRONG", new BigDecimal("0.01"), "Completed", GatewayRefundStatus.COMPLETED, "EUR"));
        assertThat(service.processRefund(refund.getId())).isFalse();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(refund.getLastError()).contains("does not match");
        verifyNoInteractions(slots);
    }

    @Test
    void partialRefundUsesStoredExchangeSnapshotWithoutOneDollarMinimum() {
        refund.setAmount(new BigDecimal("1000")); refund.setRefundPercentage(new BigDecimal("0.2"));
        when(gateway.requestRefund(any(GatewayRefundRequest.class))).thenAnswer(inv -> {
            GatewayRefundRequest request = inv.getArgument(0);
            assertThat(request.amount()).isEqualByComparingTo("0.04");
            assertThat(request.currency()).isEqualTo("USD");
            assertThat(request.fullRefund()).isFalse();
            assertThat(request.transactionId()).isEqualTo("CAPTURE-ORIGINAL");
            return result(GatewayRefundStatus.COMPLETED);
        });
        assertThat(service.processRefund(refund.getId())).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void vnPayUsesOriginalReferenceDateAndSeparatelyPersistedRequestId() {
        payment.setProvider(PaymentProvider.VNPAY); payment.setProviderAmount(payment.getAmount()); payment.setProviderCurrency("VND");
        payment.setProviderTransactionDate("20261005123045");
        when(gateway.requestRefund(any(GatewayRefundRequest.class))).thenAnswer(inv -> {
            GatewayRefundRequest request = inv.getArgument(0);
            assertThat(request.orderId()).isEqualTo("ORDER-ORIGINAL");
            assertThat(request.transactionDate()).isEqualTo("20261005123045");
            assertThat(request.fullRefund()).isTrue();
            assertThat(request.requestId()).isEqualTo(refund.getGatewayRequestId());
            return result(GatewayRefundStatus.PENDING);
        });
        service.processRefund(refund.getId());
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
    }

    @Test
    void missingOriginalCaptureCannotFallBackToLocalPaymentUuid() {
        payment.setProviderTransactionId(null);
        assertThat(service.processRefund(refund.getId())).isFalse();
        verifyNoInteractions(gateway);
        assertThat(refund.getLastError()).contains("Original gateway transaction");
    }

    @Test
    void pendingRefundReservationsCannotExceedOriginalPayment() {
        var other = new RefundJpaEntity(); other.setId(UUID.randomUUID()); other.setAmount(new BigDecimal("100000"));
        other.setStatus(RefundStatus.PENDING);
        when(refunds.findBySubOrderIdIn(List.of(sub.getId()))).thenReturn(List.of(refund, other));
        assertThat(service.processRefund(refund.getId())).isFalse();
        verifyNoInteractions(gateway);
        assertThat(refund.getLastError()).contains("exceed");
    }

    @Test
    void refundDoesNotReleaseInventoryTwiceAfterCancellation() {
        sub.setStatus(SubOrderStatus.CANCELLED);
        when(gateway.requestRefund(any(GatewayRefundRequest.class))).thenAnswer(inv -> result(GatewayRefundStatus.COMPLETED));
        assertThat(service.processRefund(refund.getId())).isTrue();
        verifyNoInteractions(slots);
    }

    @Test
    void verifiedRefundNotificationQueriesTheProviderAndIsIdempotent() {
        when(gateway.requestRefund(any(GatewayRefundRequest.class))).thenAnswer(inv -> result(GatewayRefundStatus.PENDING));
        service.processRefund(refund.getId());
        when(refunds.findByProviderAndProviderRefundId(PaymentProvider.PAYPAL, "REFUND-ORIGINAL")).thenReturn(Optional.of(refund));
        when(gateway.queryRefund(any(GatewayRefundRequest.class), eq("REFUND-ORIGINAL")))
                .thenAnswer(inv -> result(GatewayRefundStatus.COMPLETED));
        var response = service.processPayPalNotification("WH-REFUND", "REFUND-ORIGINAL", refund.getGatewayRequestId());
        assertThat(response.status()).isEqualTo(RefundStatus.PROCESSED);
        assertThat(service.processPayPalNotification("WH-REFUND", "REFUND-ORIGINAL", refund.getGatewayRequestId()).alreadyProcessed()).isTrue();
        verify(gateway, times(1)).queryRefund(any(GatewayRefundRequest.class), eq("REFUND-ORIGINAL"));
        verify(slots, times(1)).decrementBookedCount(sub.getSlotId(), 2);
    }
}
