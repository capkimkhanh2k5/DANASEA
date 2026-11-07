package com.danasea.backend.modules.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.domain.ports.GatewayRefundRequest;
import com.danasea.backend.modules.order.domain.ports.GatewayRefundStatus;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import com.danasea.backend.modules.order.domain.ports.RefundResult;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;

class RefundProcessingSafetyBranchesTest {

    private JpaRefundRepository refunds;
    private JpaPaymentRepository payments;
    private JpaSubOrderRepository subOrders;
    private JpaServiceSlotRepository slots;
    private PaymentGatewayPort gateway;
    private ApplicationEventPublisher events;
    private RefundProcessingService service;
    private MasterOrderJpaEntity order;
    private SubOrderJpaEntity subOrder;
    private PaymentJpaEntity payment;
    private RefundJpaEntity refund;

    @BeforeEach
    void setUp() {
        refunds = mock(JpaRefundRepository.class);
        payments = mock(JpaPaymentRepository.class);
        subOrders = mock(JpaSubOrderRepository.class);
        slots = mock(JpaServiceSlotRepository.class);
        gateway = mock(PaymentGatewayPort.class);
        events = mock(ApplicationEventPublisher.class);
        var orders = mock(JpaMasterOrderRepository.class);
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        service = new RefundProcessingService(refunds, subOrders, orders, payments,
                mock(JpaBookingRepository.class), slots, gateway, transactions);
        service.setEventPublisher(events);

        order = new MasterOrderJpaEntity();
        order.setId(UUID.randomUUID());
        order.setStatus(MasterOrderStatus.PAID);
        subOrder = subOrder(SubOrderStatus.CONFIRMED);
        subOrder.setSlotId(UUID.randomUUID());
        subOrder.setQuantity(1);
        payment = new PaymentJpaEntity();
        payment.setId(UUID.randomUUID());
        payment.setMasterOrderId(order.getId());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setProvider(PaymentProvider.PAYPAL);
        payment.setProviderTransactionId("CAPTURE-ORIGINAL");
        payment.setAmount(new BigDecimal("3"));
        payment.setProviderAmount(new BigDecimal("0.05"));
        payment.setProviderCurrency("USD");
        refund = refund(subOrder, "1", null, RefundStatus.PENDING);
        refund.setRefundPercentage(new BigDecimal("100"));

        when(refunds.findByIdForUpdate(refund.getId())).thenReturn(Optional.of(refund));
        when(subOrders.findById(subOrder.getId())).thenReturn(Optional.of(subOrder));
        when(subOrders.findByMasterOrderId(order.getId())).thenReturn(List.of(subOrder));
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(payments.findByMasterOrderIdOrderByCreatedAtDesc(order.getId())).thenReturn(List.of(payment));
        when(payments.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRefundResults")
    void independentlyRejectsEachUnboundRefundResult(String scenario, RefundResult result) {
        refund.setGatewayRequestedAt(OffsetDateTime.now());
        when(gateway.queryRefund(any(GatewayRefundRequest.class), any())).thenReturn(result);

        assertThat(service.processRefund(refund.getId())).isFalse();

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(refund.getProcessedAt()).isNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.CONFIRMED);
        verifyNoInteractions(slots, events);
    }

    private static Stream<Arguments> invalidRefundResults() {
        return Stream.of(
                Arguments.of("wrong amount only", completed("REFUND-1", "0.01", "USD")),
                Arguments.of("wrong currency only", completed("REFUND-1", "0.02", "EUR")),
                Arguments.of("missing refund ID", completed(null, "0.02", "USD")),
                Arguments.of("blank refund ID", completed(" ", "0.02", "USD")),
                Arguments.of("missing amount", new RefundResult(true, "REFUND-1", null,
                        "completed", GatewayRefundStatus.COMPLETED, "USD")),
                Arguments.of("unsuccessful completed result", new RefundResult(false, "REFUND-1",
                        new BigDecimal("0.02"), "not successful", GatewayRefundStatus.COMPLETED, "USD")),
                Arguments.of("pending provider status despite success flag", new RefundResult(true, "REFUND-1",
                        new BigDecimal("0.02"), "pending", GatewayRefundStatus.PENDING, "USD")),
                Arguments.of("missing provider result", null));
    }

    @Test
    void aDifferentProviderRefundIdCannotReplaceThePersistedOperation() {
        refund.setPaymentId(payment.getId());
        refund.setProvider(PaymentProvider.PAYPAL);
        refund.setProviderAmount(new BigDecimal("0.02"));
        refund.setProviderCurrency("USD");
        refund.setGatewayRequestId("persisted-refund-operation");
        refund.setProviderRefundId("REFUND-ORIGINAL");
        refund.setGatewayRequestedAt(OffsetDateTime.now().minusMinutes(1));
        when(gateway.queryRefund(any(GatewayRefundRequest.class), any()))
                .thenReturn(completed("REFUND-DIFFERENT", "0.02", "USD"));

        assertThat(service.processRefund(refund.getId())).isFalse();

        assertThat(refund.getProviderRefundId()).isEqualTo("REFUND-ORIGINAL");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(refund.getLastError()).contains("does not match");
        verify(gateway).queryRefund(any(GatewayRefundRequest.class),
                eq("REFUND-ORIGINAL"));
        verify(gateway, never()).requestRefund(any(GatewayRefundRequest.class));
        verifyNoInteractions(slots, events);
    }

    @ParameterizedTest
    @CsvSource({"0.05, 0.02, 0.01", "0.04, 0.01, 0.02"})
    void finalRefundUsesTheExactRemainingGatewayCents(
            String originalProviderAmount, String eachEarlierProviderAmount, String expectedFinalAmount) {
        refund.setGatewayRequestedAt(OffsetDateTime.now());
        payment.setProviderAmount(new BigDecimal(originalProviderAmount));
        var firstSubOrder = subOrder(SubOrderStatus.REFUNDED);
        var secondSubOrder = subOrder(SubOrderStatus.REFUNDED);
        var firstRefund = refund(firstSubOrder, "1", eachEarlierProviderAmount, RefundStatus.PROCESSED);
        var secondRefund = refund(secondSubOrder, "1", eachEarlierProviderAmount, RefundStatus.PROCESSED);
        var allSubOrders = List.of(firstSubOrder, secondSubOrder, subOrder);
        var allRefunds = List.of(firstRefund, secondRefund, refund);
        var subOrderIds = allSubOrders.stream().map(SubOrderJpaEntity::getId).toList();
        when(subOrders.findByMasterOrderId(order.getId())).thenReturn(allSubOrders);
        when(refunds.findBySubOrderIdIn(subOrderIds)).thenReturn(allRefunds);
        when(refunds.findBySubOrderIdInAndStatus(subOrderIds, RefundStatus.PROCESSED)).thenReturn(allRefunds);
        when(gateway.queryRefund(any(GatewayRefundRequest.class), any())).thenAnswer(invocation -> {
            GatewayRefundRequest request = invocation.getArgument(0);
            assertThat(request.amount()).isEqualByComparingTo(expectedFinalAmount);
            assertThat(request.currency()).isEqualTo("USD");
            assertThat(request.transactionId()).isEqualTo("CAPTURE-ORIGINAL");
            return completed("REFUND-FINAL", expectedFinalAmount, "USD");
        });

        assertThat(service.processRefund(refund.getId())).isTrue();

        assertThat(refund.getProviderAmount()).isEqualByComparingTo(expectedFinalAmount);
        assertThat(firstRefund.getProviderAmount().add(secondRefund.getProviderAmount()).add(refund.getProviderAmount()))
                .isEqualByComparingTo(originalProviderAmount);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(slots).decrementBookedCount(subOrder.getSlotId(), 1);
    }

    @Test
    void failedRefundReservationsDoNotConsumeOriginalMoneyOrGatewayCents() {
        refund.setGatewayRequestedAt(OffsetDateTime.now());
        refund.setAmount(new BigDecimal("3"));
        var failed = refund(subOrder, "3", "0.05", RefundStatus.FAILED);
        when(refunds.findBySubOrderIdIn(List.of(subOrder.getId()))).thenReturn(List.of(failed, refund));
        when(gateway.queryRefund(any(GatewayRefundRequest.class), any())).thenAnswer(invocation -> {
            GatewayRefundRequest request = invocation.getArgument(0);
            assertThat(request.amount()).isEqualByComparingTo("0.05");
            assertThat(request.fullRefund()).isTrue();
            return new RefundResult(false, "REFUND-PENDING", new BigDecimal("0.05"),
                    "pending", GatewayRefundStatus.PENDING, "USD");
        });

        assertThat(service.processRefund(refund.getId())).isFalse();

        assertThat(refund.getProviderAmount()).isEqualByComparingTo("0.05");
        assertThat(refund.getProviderRefundId()).isEqualTo("REFUND-PENDING");
        assertThat(refund.getLastError()).contains("Gateway refund remains pending");
        verify(gateway).queryRefund(any(GatewayRefundRequest.class), any());
        verifyNoInteractions(slots, events);
    }

    @Test
    void aRefundThatRoundsToZeroGatewayCentsRequiresReviewWithoutSendingMoney() {
        payment.setAmount(new BigDecimal("1000"));
        payment.setProviderAmount(new BigDecimal("0.01"));

        assertThat(service.processRefund(refund.getId())).isFalse();

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(refund.getLastError()).contains("cannot be represented");
        assertThat(refund.getGatewayRequestId()).isNull();
        verifyNoInteractions(gateway, slots, events);
    }

    @Test
    void gatewayAllocationsCannotExceedOriginalProviderMoneyEvenWithinLocalReservationLimit() {
        var other = refund(subOrder, "1", "0.05", RefundStatus.PENDING);
        when(refunds.findBySubOrderIdIn(List.of(subOrder.getId()))).thenReturn(List.of(other, refund));

        assertThat(service.processRefund(refund.getId())).isFalse();

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(refund.getLastError()).contains("cannot be represented");
        verifyNoInteractions(gateway, slots, events);
    }

    @Test
    void missingOriginalSuccessfulPaymentStaysPendingForReviewWithoutThrowingOrSendingMoney() {
        refund.setPaymentId(null);
        when(payments.findByMasterOrderIdOrderByCreatedAtDesc(order.getId())).thenReturn(List.of());
        var processed = new AtomicBoolean(true);

        assertThatCode(() -> processed.set(service.processRefund(refund.getId()))).doesNotThrowAnyException();

        assertThat(processed.get()).isFalse();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(refund.getLastError()).startsWith("REFUND_CONTEXT_REQUIRES_REVIEW")
                .contains("No successful original payment");
        assertThat(refund.getNextAttemptAt()).isNotNull();
        verifyNoInteractions(gateway, slots, events);
    }

    @Test
    void invalidOriginalPaymentWithAnExistingOperationIsReviewedExactlyOnceWithoutSendingMoney() {
        refund.setGatewayRequestId("persisted-refund-operation");
        refund.setProviderAmount(new BigDecimal("0.02"));
        refund.setProviderCurrency("USD");
        refund.setProvider(PaymentProvider.PAYPAL);
        when(payments.findByIdForUpdate(payment.getId())).thenReturn(Optional.empty());
        var processed = new AtomicBoolean(true);

        assertThatCode(() -> processed.set(service.processRefund(refund.getId()))).doesNotThrowAnyException();

        assertThat(processed.get()).isFalse();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(refund.getLastError()).startsWith("REFUND_CONTEXT_REQUIRES_REVIEW")
                .contains("Original payment not found");
        assertThat(refund.getRetryCount()).isEqualTo(1);
        assertThat(refund.getGatewayRequestId()).isEqualTo("persisted-refund-operation");
        assertThat(refund.getProviderAmount()).isEqualByComparingTo("0.02");
        assertThat(refund.getGatewayRequestedAt()).isNull();
        assertThat(refund.getProcessedAt()).isNull();
        assertThat(refund.getNextAttemptAt()).isNotNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.CONFIRMED);
        verifyNoInteractions(gateway, slots, events);
    }

    @Test
    void originalPaymentDisappearingBetweenPhasesLeavesTheRefundPendingForVerification() {
        refund.setGatewayRequestedAt(OffsetDateTime.now());
        when(payments.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment), Optional.empty());
        var processed = new AtomicBoolean(true);

        assertThatCode(() -> processed.set(service.processRefund(refund.getId()))).doesNotThrowAnyException();

        assertThat(processed.get()).isFalse();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(refund.getLastError()).startsWith("GATEWAY_TIMEOUT_AWAITING_VERIFICATION")
                .contains("Original payment not found");
        assertThat(refund.getGatewayRequestId()).isNotBlank();
        assertThat(refund.getGatewayRequestedAt()).isNotNull();
        assertThat(refund.getProviderAmount()).isEqualByComparingTo("0.02");
        assertThat(refund.getVerificationAttempts()).isEqualTo(1);
        assertThat(refund.getRetryCount()).isZero();
        assertThat(refund.getProcessedAt()).isNull();
        assertThat(refund.getNextAttemptAt()).isAfter(OffsetDateTime.now());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.CONFIRMED);
        verifyNoInteractions(gateway, slots, events);
    }

    private SubOrderJpaEntity subOrder(SubOrderStatus status) {
        var entity = new SubOrderJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setMasterOrderId(order.getId());
        entity.setStatus(status);
        return entity;
    }

    private RefundJpaEntity refund(SubOrderJpaEntity item, String amount, String providerAmount, RefundStatus status) {
        var entity = new RefundJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setSubOrderId(item.getId());
        entity.setPaymentId(payment.getId());
        entity.setAmount(new BigDecimal(amount));
        entity.setProviderAmount(providerAmount == null ? null : new BigDecimal(providerAmount));
        entity.setStatus(status);
        return entity;
    }

    private static RefundResult completed(String id, String amount, String currency) {
        return new RefundResult(true, id, new BigDecimal(amount), "completed", GatewayRefundStatus.COMPLETED, currency);
    }
}
