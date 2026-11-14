package com.danasea.backend.modules.order.application.services;

import com.danasea.backend.modules.booking.domain.models.BookingStatus;
import com.danasea.backend.modules.booking.domain.ports.InventoryLockPort;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.order.application.OrderPaymentService;
import com.danasea.backend.modules.order.domain.exceptions.InvalidOrderStateException;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.exceptions.UnauthorizedOrderAccessException;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentAttemptState;
import com.danasea.backend.modules.order.domain.models.PaymentCancelStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.ports.PaymentCancelResult;
import com.danasea.backend.modules.order.domain.ports.PaymentCaptureResult;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import com.danasea.backend.modules.order.domain.ports.PaymentIntentResult;
import com.danasea.backend.modules.order.domain.ports.SubOrderRepositoryPort;
import com.danasea.backend.modules.order.infrastructure.adapters.PaymentGatewayRegistry;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository;
import com.danasea.backend.modules.order.presentation.dtos.PaymentStatusResponse;
import com.danasea.backend.modules.order.presentation.dtos.PaymentWindowResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentCoordinator 5+5 Unified Orchestration Tests")
class PaymentCoordinatorTest {

    @Mock private JpaMasterOrderRepository masterOrderRepository;
    @Mock private JpaPaymentRepository paymentRepository;
    @Mock private JpaBookingRepository bookingRepository;
    @Mock private PaymentGatewayRegistry gatewayRegistry;
    @Mock private InventoryLockPort inventoryLockPort;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private SubOrderRepositoryPort subOrderRepository;
    @Mock private OrderPaymentService orderPaymentService;
    @Mock private PaymentGatewayPort payosAdapter;
    @Mock private PaymentGatewayPort zaloPayAdapter;

    private PaymentCoordinator coordinator;

    private UUID customerId;
    private UUID orderId;
    private UUID bookingId;
    private MasterOrderJpaEntity masterOrder;
    private BookingJpaEntity booking;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        bookingId = UUID.randomUUID();

        lenient().when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        lenient().when(inventoryLockPort.extendHolds(any(), any(), any())).thenReturn(true);

        coordinator = new PaymentCoordinator(
                masterOrderRepository,
                paymentRepository,
                bookingRepository,
                gatewayRegistry,
                inventoryLockPort,
                transactionManager,
                subOrderRepository,
                null,
                null,
                orderPaymentService
        );

        masterOrder = new MasterOrderJpaEntity();
        masterOrder.setId(orderId);
        masterOrder.setCustomerId(customerId);
        masterOrder.setBookingId(bookingId);
        masterOrder.setStatus(MasterOrderStatus.PENDING_PAYMENT);
        masterOrder.setPaymentStatus(PaymentOrderStatus.UNPAID);
        masterOrder.setTotalAmount(new BigDecimal("1000000.00"));

        booking = new BookingJpaEntity();
        booking.setId(bookingId);
        booking.setCustomerId(customerId);
        booking.setHoldExpiresAt(OffsetDateTime.now().plusMinutes(3));
    }

    @Test
    @DisplayName("startPayment fixes payment deadline to now+300s, extends Redis hold to 420s, and creates attempt #1")
    void testStartPaymentFirstAttempt() {
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByMasterOrderIdAndIsActiveTrue(orderId)).thenReturn(Optional.empty());
        when(paymentRepository.findInFlightAttempts(eq(orderId), any())).thenReturn(Collections.emptyList());
        when(paymentRepository.findMaxAttemptNumberByMasterOrderId(orderId)).thenReturn(0);
        when(paymentRepository.getNextPayOSOrderCode()).thenReturn(987654L);

        when(paymentRepository.save(any(PaymentJpaEntity.class))).thenAnswer(invocation -> {
            PaymentJpaEntity p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });

        when(gatewayRegistry.getAdapter(PaymentProvider.PAYOS)).thenReturn(payosAdapter);
        when(payosAdapter.createPaymentIntent(any(), eq(orderId), eq(new BigDecimal("1000000.00")), eq(PaymentProvider.PAYOS), any(), any(), any()))
                .thenReturn(new PaymentIntentResult(
                        UUID.randomUUID(), orderId, PaymentProvider.PAYOS, new BigDecimal("1000000.00"),
                        "https://pay.payos.vn/123", "000201...", OffsetDateTime.now().plusSeconds(300),
                        "link-123", new BigDecimal("1000000.00"), "VND", null
                ));

        PaymentJpaEntity savedPayment = new PaymentJpaEntity();
        savedPayment.setStatus(PaymentStatus.PENDING);
        savedPayment.setId(UUID.randomUUID());
        savedPayment.setAttemptState(PaymentAttemptState.CREATING);
        savedPayment.setMasterOrderId(orderId);
        savedPayment.setProvider(PaymentProvider.PAYOS);
        savedPayment.setAmount(new BigDecimal("1000000.00"));
        when(paymentRepository.findByIdForUpdate(any())).thenReturn(Optional.of(savedPayment));

        PaymentWindowResponse response = coordinator.startPayment(customerId, orderId, PaymentProvider.PAYOS, "idempotency-key-01");

        assertThat(response).isNotNull();
        assertThat(masterOrder.getPaymentDeadline()).isNotNull();
        assertThat(response.attemptNumber()).isEqualTo(1);
        assertThat(response.provider()).isEqualTo(PaymentProvider.PAYOS);

        // Verify Redis Hold was extended for 420s (300s window + 120s grace)
        verify(inventoryLockPort).extendHolds(eq(bookingId), any(), eq(Duration.ofSeconds(420)));
    }

    @Test
    @DisplayName("startPayment reuses active attempt if called repeatedly for the same provider")
    void testStartPaymentReusesActiveAttempt() {
        OffsetDateTime fixedDeadline = OffsetDateTime.now().plusSeconds(250);
        masterOrder.setPaymentDeadline(fixedDeadline);

        PaymentJpaEntity activePayment = new PaymentJpaEntity();
        activePayment.setStatus(PaymentStatus.PENDING);
        activePayment.setId(UUID.randomUUID());
        activePayment.setMasterOrderId(orderId);
        activePayment.setProvider(PaymentProvider.ZALOPAY);
        activePayment.setAmount(new BigDecimal("1000000.00"));
        activePayment.setStatus(PaymentStatus.PENDING);
        activePayment.setAttemptState(PaymentAttemptState.PENDING);
        activePayment.setAttemptNumber(1);
        activePayment.setIsActive(true);
        activePayment.setPaymentUrl("https://qcgateway.zalopay.vn/pay");

        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));
        when(paymentRepository.findByMasterOrderIdAndIsActiveTrue(orderId)).thenReturn(Optional.of(activePayment));

        PaymentWindowResponse response = coordinator.startPayment(customerId, orderId, PaymentProvider.ZALOPAY, "idempotency-key-repeat");

        assertThat(response).isNotNull();
        assertThat(response.paymentId()).isEqualTo(activePayment.getId());
        assertThat(response.paymentUrl()).isEqualTo("https://qcgateway.zalopay.vn/pay");
        assertThat(response.attemptNumber()).isEqualTo(1);

        // No external gateway call should be made when reused
        verifyNoInteractions(gatewayRegistry);
    }

    @Test
    @DisplayName("switchProvider cancels old attempt server-side, terminates it, and creates attempt #2 with identical deadline")
    void testSwitchProviderSafeSwitch() {
        OffsetDateTime fixedDeadline = OffsetDateTime.now().plusSeconds(200);
        masterOrder.setPaymentDeadline(fixedDeadline);

        PaymentJpaEntity oldZaloPayAttempt = new PaymentJpaEntity();
        oldZaloPayAttempt.setStatus(PaymentStatus.PENDING);
        oldZaloPayAttempt.setId(UUID.randomUUID());
        oldZaloPayAttempt.setMasterOrderId(orderId);
        oldZaloPayAttempt.setProvider(PaymentProvider.ZALOPAY);
        oldZaloPayAttempt.setProviderOrderId("261105_123456");
        oldZaloPayAttempt.setAmount(new BigDecimal("1000000.00"));
        oldZaloPayAttempt.setStatus(PaymentStatus.PENDING);
        oldZaloPayAttempt.setAttemptState(PaymentAttemptState.PENDING);
        oldZaloPayAttempt.setAttemptNumber(1);
        oldZaloPayAttempt.setIsActive(true);

        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));
        when(paymentRepository.findByMasterOrderIdAndIsActiveTrue(orderId))
                .thenReturn(Optional.of(oldZaloPayAttempt))
                .thenReturn(Optional.empty()); // after cancel
        when(paymentRepository.findByIdForUpdate(oldZaloPayAttempt.getId())).thenReturn(Optional.of(oldZaloPayAttempt));

        when(gatewayRegistry.getAdapter(PaymentProvider.ZALOPAY)).thenReturn(zaloPayAdapter);
        when(zaloPayAdapter.canCancelServerSide(PaymentProvider.ZALOPAY)).thenReturn(true);
        when(zaloPayAdapter.cancelPayment(eq(PaymentProvider.ZALOPAY), eq("261105_123456"), any()))
                .thenReturn(PaymentCancelResult.success("Canceled on ZaloPay"));

        // Setup Phase 1-2-3 for new payOS attempt #2
        when(paymentRepository.findInFlightAttempts(eq(orderId), any())).thenReturn(Collections.emptyList());
        when(paymentRepository.findMaxAttemptNumberByMasterOrderId(orderId)).thenReturn(1);
        when(paymentRepository.getNextPayOSOrderCode()).thenReturn(555666L);

        when(paymentRepository.save(any(PaymentJpaEntity.class))).thenAnswer(invocation -> {
            PaymentJpaEntity p = invocation.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });

        when(gatewayRegistry.getAdapter(PaymentProvider.PAYOS)).thenReturn(payosAdapter);
        when(payosAdapter.createPaymentIntent(any(), eq(orderId), eq(new BigDecimal("1000000.00")), eq(PaymentProvider.PAYOS), any(), any(), any()))
                .thenReturn(new PaymentIntentResult(
                        UUID.randomUUID(), orderId, PaymentProvider.PAYOS, new BigDecimal("1000000.00"),
                        "https://pay.payos.vn/new-link", "000201...", fixedDeadline,
                        "link-payos-2", new BigDecimal("1000000.00"), "VND", null
                ));

        PaymentJpaEntity newSavedAttempt = new PaymentJpaEntity();
        newSavedAttempt.setStatus(PaymentStatus.PENDING);
        newSavedAttempt.setId(UUID.randomUUID());
        newSavedAttempt.setAttemptState(PaymentAttemptState.CREATING);
        newSavedAttempt.setMasterOrderId(orderId);
        newSavedAttempt.setProvider(PaymentProvider.PAYOS);
        newSavedAttempt.setAmount(new BigDecimal("1000000.00"));
        when(paymentRepository.findByIdForUpdate(argThat(id -> !id.equals(oldZaloPayAttempt.getId()))))
                .thenReturn(Optional.of(newSavedAttempt));

        PaymentWindowResponse response = coordinator.switchProvider(customerId, orderId, PaymentProvider.PAYOS, "switch-key-01");

        assertThat(response).isNotNull();
        assertThat(oldZaloPayAttempt.getIsActive()).isFalse();
        assertThat(oldZaloPayAttempt.getAttemptState()).isEqualTo(PaymentAttemptState.TERMINATED);
        assertThat(oldZaloPayAttempt.getCancelStatus()).isEqualTo(PaymentCancelStatus.CANCELED);

        // Crucial 5+5 Invariant: deadline must NOT be reset upon switch
        assertThat(masterOrder.getPaymentDeadline()).isEqualTo(fixedDeadline);
    }

    @Test
    @DisplayName("switchProvider is blocked if previous provider order was already captured")
    void testSwitchProviderBlockedWhenAlreadyCaptured() {
        OffsetDateTime fixedDeadline = OffsetDateTime.now().plusSeconds(180);
        masterOrder.setPaymentDeadline(fixedDeadline);

        PaymentJpaEntity oldAttempt = new PaymentJpaEntity();
        oldAttempt.setStatus(PaymentStatus.PENDING);
        oldAttempt.setId(UUID.randomUUID());
        oldAttempt.setMasterOrderId(orderId);
        oldAttempt.setProvider(PaymentProvider.ZALOPAY);
        oldAttempt.setProviderOrderId("261105_captured");
        oldAttempt.setIsActive(true);

        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));
        when(paymentRepository.findByMasterOrderIdAndIsActiveTrue(orderId)).thenReturn(Optional.of(oldAttempt));

        when(gatewayRegistry.getAdapter(PaymentProvider.ZALOPAY)).thenReturn(zaloPayAdapter);
        when(zaloPayAdapter.canCancelServerSide(PaymentProvider.ZALOPAY)).thenReturn(true);
        when(zaloPayAdapter.cancelPayment(any(), any(), any()))
                .thenReturn(PaymentCancelResult.alreadyCaptured("ZaloPay was already paid"));

        assertThatThrownBy(() -> coordinator.switchProvider(customerId, orderId, PaymentProvider.PAYOS, "switch-key-captured"))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("not been verified as canceled");
    }

    @Test
    @DisplayName("switchProvider is rejected if remaining time is less than 5 seconds")
    void testSwitchProviderRejectedWhenTimeInsufficient() {
        OffsetDateTime now = OffsetDateTime.parse("2026-11-14T23:59:58+07:00");
        masterOrder.setPaymentDeadline(now.plusSeconds(3));
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));

        try (var time = mockStatic(OffsetDateTime.class,
                withSettings().mockMaker(MockMakers.INLINE).defaultAnswer(CALLS_REAL_METHODS))) {
            time.when(OffsetDateTime::now).thenReturn(now);
            assertThatThrownBy(() -> coordinator.switchProvider(customerId, orderId, PaymentProvider.PAYOS, "switch-key-late"))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("Insufficient time remaining (3s)");
        }
    }

    @Test
    @DisplayName("startPayment is blocked if another attempt is currently CREATING")
    void testStartPaymentBlockedWhenInFlight() {
        masterOrder.setPaymentDeadline(OffsetDateTime.now().plusSeconds(200));

        PaymentJpaEntity creatingAttempt = new PaymentJpaEntity();
        creatingAttempt.setStatus(PaymentStatus.PENDING);
        creatingAttempt.setAttemptState(PaymentAttemptState.CREATING);

        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));
        when(paymentRepository.findByMasterOrderIdAndIsActiveTrue(orderId)).thenReturn(Optional.empty());
        when(paymentRepository.findInFlightAttempts(eq(orderId), any()))
                .thenReturn(List.of(creatingAttempt));

        assertThatThrownBy(() -> coordinator.startPayment(customerId, orderId, PaymentProvider.PAYOS, "key-inflight"))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("being generated");
    }

    @Test
    @DisplayName("getPaymentStatus returns remaining seconds and countdown without resetting")
    void testGetPaymentStatus() {
        OffsetDateTime deadline = OffsetDateTime.now().plusSeconds(145);
        masterOrder.setPaymentDeadline(deadline);

        when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));
        when(paymentRepository.findByMasterOrderIdAndIsActiveTrue(orderId)).thenReturn(Optional.empty());

        PaymentStatusResponse status = coordinator.getPaymentStatus(customerId, orderId);

        assertThat(status).isNotNull();
        assertThat(status.remainingSeconds()).isBetween(140L, 146L);
        assertThat(status.isExpired()).isFalse();
    }

    @Test
    void startPaymentBlocksWhenAPreviousPayosCreationIsUnresolved() {
        masterOrder.setPaymentDeadline(OffsetDateTime.now().plusSeconds(200));
        PaymentJpaEntity attempt = new PaymentJpaEntity();
        attempt.setId(UUID.randomUUID());
        attempt.setProvider(PaymentProvider.PAYOS);
        attempt.setAttemptState(PaymentAttemptState.CREATING);
        attempt.setCancelStatus(PaymentCancelStatus.CANCEL_UNKNOWN);

        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));
        when(paymentRepository.findInFlightAttempts(eq(orderId), any())).thenReturn(List.of(attempt));

        assertThatThrownBy(() -> coordinator.startPayment(customerId, orderId, PaymentProvider.PAYOS, "idemp-unresolved"))
                .isInstanceOf(InvalidOrderStateException.class);
        verifyNoInteractions(payosAdapter);
        assertThat(attempt.getPaymentUrl()).isNull();
    }

    @Test
    void startPaymentBlocksWhenASecondLegacyAttemptRemainsPending() {
        masterOrder.setPaymentDeadline(OffsetDateTime.now().plusSeconds(200));
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));
        when(paymentRepository.existsByMasterOrderIdAndStatus(orderId, PaymentStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> coordinator.startPayment(customerId, orderId, PaymentProvider.PAYOS, "idemp-unresolved"))
                .isInstanceOf(InvalidOrderStateException.class);
        verifyNoInteractions(payosAdapter);
    }

    @Test
    @DisplayName("startPayment and switchProvider reject null inputs or malformed idempotency keys")
    void testValidateInputsRejectsInvalidArguments() {
        assertThatThrownBy(() -> coordinator.startPayment(null, orderId, PaymentProvider.PAYOS, "key-12345678"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> coordinator.startPayment(customerId, null, PaymentProvider.PAYOS, "key-12345678"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> coordinator.startPayment(customerId, orderId, null, "key-12345678"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> coordinator.startPayment(customerId, orderId, PaymentProvider.PAYOS, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> coordinator.startPayment(customerId, orderId, PaymentProvider.PAYOS, "short"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> coordinator.switchProvider(null, orderId, PaymentProvider.PAYOS, "key-12345678"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getPaymentStatus throws OrderNotFoundException when order does not exist")
    void testGetPaymentStatusOrderNotFound() {
        when(masterOrderRepository.findById(orderId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> coordinator.getPaymentStatus(customerId, orderId))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("getPaymentStatus throws UnauthorizedOrderAccessException when customer does not match")
    void testGetPaymentStatusUnauthorizedCustomer() {
        UUID otherCustomer = UUID.randomUUID();
        when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));
        assertThatThrownBy(() -> coordinator.getPaymentStatus(otherCustomer, orderId))
                .isInstanceOf(UnauthorizedOrderAccessException.class);
    }

    @Test
    @DisplayName("getPaymentStatus marks window as expired when deadline is in the past")
    void testGetPaymentStatusExpiredDeadline() {
        masterOrder.setPaymentDeadline(OffsetDateTime.now().minusSeconds(10));
        when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));

        PaymentStatusResponse status = coordinator.getPaymentStatus(customerId, orderId);

        assertThat(status).isNotNull();
        assertThat(status.isExpired()).isTrue();
        assertThat(status.remainingSeconds()).isZero();
    }

    @Test
    @DisplayName("getPaymentStatus reconciles pending payment with gateway when orderPaymentService is configured")
    void testGetPaymentStatusReconcilesWithGateway() {
        masterOrder.setStatus(MasterOrderStatus.PENDING_PAYMENT);
        when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));
        when(orderPaymentService.reconcilePaymentWithGateway(orderId)).thenReturn(true);

        MasterOrderJpaEntity paidOrder = new MasterOrderJpaEntity();
        paidOrder.setId(orderId);
        paidOrder.setCustomerId(customerId);
        paidOrder.setStatus(MasterOrderStatus.PAID);
        paidOrder.setPaymentStatus(PaymentOrderStatus.PAID);
        when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder), Optional.of(paidOrder));

        PaymentStatusResponse status = coordinator.getPaymentStatus(customerId, orderId);

        assertThat(status).isNotNull();
        assertThat(status.orderStatus()).isEqualTo(MasterOrderStatus.PAID);
        assertThat(status.paymentStatus()).isEqualTo(PaymentOrderStatus.PAID);
    }

    @Test
    void expirySweepLeavesInventoryToBookingCleanup() {
        when(paymentRepository.findExpiredActiveAttempts(eq(PaymentStatus.PENDING), eq(MasterOrderStatus.PENDING_PAYMENT), any()))
                .thenReturn(List.of());

        coordinator.sweepExpiredClosingOrders();

        verifyNoInteractions(inventoryLockPort, bookingRepository, gatewayRegistry);
    }
}
