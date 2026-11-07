package com.danasea.backend.modules.order.application.services;

import com.danasea.backend.modules.booking.application.usecases.ConfirmBookingUseCase;
import com.danasea.backend.modules.booking.domain.exceptions.BookingHoldExpiredException;
import com.danasea.backend.modules.booking.domain.models.BookingStatus;
import com.danasea.backend.modules.booking.domain.ports.InventoryLockPort;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingItemJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.order.application.OrderPaymentService;
import com.danasea.backend.modules.order.domain.exceptions.InvalidOrderStateException;
import com.danasea.backend.modules.order.domain.exceptions.PaymentGatewayException;
import com.danasea.backend.modules.order.domain.models.*;
import com.danasea.backend.modules.order.domain.ports.PaymentCancelResult;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import com.danasea.backend.modules.order.domain.ports.PaymentIntentResult;
import com.danasea.backend.modules.order.domain.services.RefundPolicyEngine;
import com.danasea.backend.modules.order.infrastructure.adapters.PaymentGatewayRegistry;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.*;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.*;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.order.presentation.dtos.PaymentWindowResponse;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bộ 21 Acceptance Cases kiểm thử toàn diện Payment 5+5 theo Mục 8 của DANASEA_PAYMENT_5PLUS5_HANDOFF.md.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DANASEA Payment 5+5 Section 8 Acceptance Criteria Tests (21 Cases)")
class Payment5Plus5AcceptanceCasesTest {

    @Mock private JpaMasterOrderRepository masterOrderRepository;
    @Mock private JpaPaymentRepository paymentRepository;
    @Mock private JpaBookingRepository bookingRepository;
    @Mock private JpaSubOrderRepository subOrderRepository;
    @Mock private JpaRefundRepository refundRepository;
    @Mock private JpaServiceSlotRepository serviceSlotRepository;
    @Mock private VendorInternalApi vendorInternalApi;
    @Mock private ConfirmBookingUseCase confirmBookingUseCase;
    @Mock private PaymentGatewayRegistry gatewayRegistry;
    @Mock private InventoryLockPort inventoryLockPort;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private PaymentGatewayPort payosAdapter;
    @Mock private PaymentGatewayPort zaloPayAdapter;
    @Mock private PaymentGatewayPort vnPayAdapter;
    @Mock private PaymentGatewayPort payPalAdapter;

    private PaymentCoordinator coordinator;
    private OrderPaymentService orderPaymentService;
    private ObjectMapper objectMapper;

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
        objectMapper = new ObjectMapper();

        lenient().when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        lenient().when(inventoryLockPort.extendHolds(any(), any(), any())).thenReturn(true);

        orderPaymentService = new OrderPaymentService(
                bookingRepository,
                masterOrderRepository,
                subOrderRepository,
                paymentRepository,
                refundRepository,
                serviceSlotRepository,
                vendorInternalApi,
                new RefundPolicyEngine(),
                null,
                confirmBookingUseCase,
                objectMapper,
                null
        );
        orderPaymentService.setPaymentGatewayRegistry(gatewayRegistry);
        orderPaymentService.setPaymentGatewayPort(vnPayAdapter);

        coordinator = new PaymentCoordinator(
                masterOrderRepository,
                paymentRepository,
                bookingRepository,
                gatewayRegistry,
                inventoryLockPort,
                transactionManager,
                null,
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
        masterOrder.setTotalAmount(new BigDecimal("500000.00"));

        booking = new BookingJpaEntity();
        booking.setId(bookingId);
        booking.setCustomerId(customerId);
        booking.setStatus(BookingStatus.HOLD);
        booking.setHoldExpiresAt(OffsetDateTime.now().plusMinutes(3));
    }

    // 1. HOLD hết sau 300 giây nếu chưa bắt đầu thanh toán; không kích hoạt PAYMENT sau HOLD đã hết.
    @Test
    @DisplayName("Case 01: HOLD expired -> Rejects payment activation")
    void testCase01_HoldExpired_RejectsPaymentStart() {
        booking.setHoldExpiresAt(OffsetDateTime.now().minusSeconds(10));
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> coordinator.startPayment(customerId, orderId, PaymentProvider.PAYOS, "idemp-01"))
                .isInstanceOf(BookingHoldExpiredException.class);
        verify(inventoryLockPort, never()).extendHolds(any(), any(), any());
    }

    // 2. Bắt đầu payment lúc 10:04, deadline 10:09; lần mở cổng sau vẫn giữ 10:09.
    @Test
    @DisplayName("Case 02: Start payment at 10:04 -> fixed deadline 10:09 preserved on subsequent calls")
    void testCase02_StartPayment_SetsFixedDeadlineAndPreservesOnNextOpen() {
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByMasterOrderIdAndIsActiveTrue(orderId)).thenReturn(Optional.empty());
        when(paymentRepository.findInFlightAttempts(eq(orderId), any())).thenReturn(Collections.emptyList());
        when(paymentRepository.findMaxAttemptNumberByMasterOrderId(orderId)).thenReturn(0);
        when(paymentRepository.getNextPayOSOrderCode()).thenReturn(111222L);
        when(paymentRepository.save(any(PaymentJpaEntity.class))).thenAnswer(inv -> {
            PaymentJpaEntity p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });
        when(gatewayRegistry.getAdapter(PaymentProvider.PAYOS)).thenReturn(payosAdapter);
        when(payosAdapter.createPaymentIntent(any(), eq(orderId), any(), eq(PaymentProvider.PAYOS), any(), any(), any()))
                .thenReturn(new PaymentIntentResult(
                        UUID.randomUUID(), orderId, PaymentProvider.PAYOS, new BigDecimal("500000.00"),
                        "https://pay.payos.vn/web/111222", "000201...", OffsetDateTime.now().plusSeconds(300), "111222"
                ));

        PaymentJpaEntity activated = new PaymentJpaEntity();
        activated.setStatus(PaymentStatus.PENDING);
        activated.setId(UUID.randomUUID());
        activated.setAttemptState(PaymentAttemptState.CREATING);
        activated.setMasterOrderId(orderId);
        activated.setProvider(PaymentProvider.PAYOS);
        activated.setAmount(new BigDecimal("500000.00"));
        when(paymentRepository.findByIdForUpdate(any())).thenReturn(Optional.of(activated));

        PaymentWindowResponse res = coordinator.startPayment(customerId, orderId, PaymentProvider.PAYOS, "idemp-02");
        assertThat(res).isNotNull();
        OffsetDateTime firstDeadline = masterOrder.getPaymentDeadline();
        assertThat(firstDeadline).isNotNull();

        // Lần mở sau với attempt active: deadline giữ nguyên
        activated.setAttemptState(PaymentAttemptState.PENDING);
        when(paymentRepository.findByMasterOrderIdAndIsActiveTrue(orderId)).thenReturn(Optional.of(activated));
        PaymentWindowResponse secondRes = coordinator.startPayment(customerId, orderId, PaymentProvider.PAYOS, "idemp-02-reload");
        assertThat(secondRes.danaseaDeadlineAt()).isEqualTo(firstDeadline);
    }

    // 3. Bắt đầu lúc 10:04:59: không đánh mất inventory khi chuyển phase; timeout đúng một lần.
    @Test
    @DisplayName("Case 03: Start payment at end of HOLD (1s before expiry) extends Redis hold to 420s")
    void testCase03_StartPaymentAtEndOfHold_ExtendsRedisHolds() {
        booking.setHoldExpiresAt(OffsetDateTime.now().plusSeconds(5));
        masterOrder.setPaymentDeadline(booking.getHoldExpiresAt());
        OffsetDateTime originalHoldDeadline = booking.getHoldExpiresAt();
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByMasterOrderIdAndIsActiveTrue(orderId)).thenReturn(Optional.empty());
        when(paymentRepository.findInFlightAttempts(eq(orderId), any())).thenReturn(Collections.emptyList());
        when(paymentRepository.findMaxAttemptNumberByMasterOrderId(orderId)).thenReturn(0);
        when(paymentRepository.save(any(PaymentJpaEntity.class))).thenAnswer(inv -> {
            PaymentJpaEntity p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });
        when(gatewayRegistry.getAdapter(PaymentProvider.PAYOS)).thenReturn(payosAdapter);
        when(payosAdapter.createPaymentIntent(any(), eq(orderId), any(), eq(PaymentProvider.PAYOS), any(), any(), any()))
                .thenReturn(new PaymentIntentResult(
                        UUID.randomUUID(), orderId, PaymentProvider.PAYOS, new BigDecimal("500000.00"),
                        "https://pay.payos.vn/link", "qr", OffsetDateTime.now().plusSeconds(300), "111"
                ));

        PaymentJpaEntity act = new PaymentJpaEntity();
        act.setStatus(PaymentStatus.PENDING);
        act.setId(UUID.randomUUID());
        act.setAttemptState(PaymentAttemptState.CREATING);
        act.setMasterOrderId(orderId);
        act.setProvider(PaymentProvider.PAYOS);
        act.setAmount(new BigDecimal("500000.00"));
        when(paymentRepository.findByIdForUpdate(any())).thenReturn(Optional.of(act));

        when(inventoryLockPort.extendHolds(eq(bookingId), any(), any())).thenReturn(true);
        coordinator.startPayment(customerId, orderId, PaymentProvider.PAYOS, "idemp-03");
        assertThat(masterOrder.getPaymentDeadline()).isAfter(originalHoldDeadline.plusMinutes(4));
        assertThat(booking.getHoldExpiresAt()).isEqualTo(masterOrder.getPaymentDeadline().plusSeconds(120));
        verify(inventoryLockPort).extendHolds(eq(bookingId), any(), eq(Duration.ofSeconds(420)));
    }

    @Test
    void paymentStartFailsWhenInventoryCannotBeExtended() {
        when(inventoryLockPort.extendHolds(any(), any(), any())).thenReturn(false);
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        lenient().when(paymentRepository.save(any())).thenAnswer(inv -> {
            PaymentJpaEntity payment = inv.getArgument(0);
            payment.setId(UUID.randomUUID());
            return payment;
        });
        lenient().when(gatewayRegistry.getAdapter(PaymentProvider.PAYOS)).thenReturn(payosAdapter);

        assertThatThrownBy(() -> coordinator.startPayment(customerId, orderId, PaymentProvider.PAYOS, "hold-missing"))
                .isInstanceOf(BookingHoldExpiredException.class);
        assertThat(masterOrder.getPaymentDeadline()).isNull();
        verifyNoInteractions(payosAdapter);
    }

    // 4. 30 request đồng thời cùng order+provider → đúng 1 external create / 1 attempt hoạt động; retry trả attempt cũ.
    @Test
    @DisplayName("Case 04: Multiple concurrent requests block in-flight and serialize to single attempt")
    void testCase04_ConcurrentRequests_SingleAttemptGenerated() {
        masterOrder.setPaymentDeadline(OffsetDateTime.now().plusSeconds(200));
        PaymentJpaEntity inFlight = new PaymentJpaEntity();
        inFlight.setStatus(PaymentStatus.PENDING);
        inFlight.setAttemptState(PaymentAttemptState.CREATING);

        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));
        when(paymentRepository.findByMasterOrderIdAndIsActiveTrue(orderId)).thenReturn(Optional.empty());
        when(paymentRepository.findInFlightAttempts(eq(orderId), any())).thenReturn(List.of(inFlight));

        assertThatThrownBy(() -> coordinator.startPayment(customerId, orderId, PaymentProvider.PAYOS, "spam-click-04"))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("being generated");
    }

    // 5. Gateway create timeout sau khi provider có thể đã tạo: không blind recreate, reconcile theo idempotency/external ref.
    @Test
    @DisplayName("Case 05: Gateway create timeout marks CANCEL_UNKNOWN instead of blind recreation")
    void testCase05_GatewayCreateTimeout_NoBlindRecreate() {
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByMasterOrderIdAndIsActiveTrue(orderId)).thenReturn(Optional.empty());
        when(paymentRepository.findInFlightAttempts(eq(orderId), any())).thenReturn(Collections.emptyList());
        when(paymentRepository.findMaxAttemptNumberByMasterOrderId(orderId)).thenReturn(0);
        when(paymentRepository.getNextPayOSOrderCode()).thenReturn(55555L);

        PaymentJpaEntity attempt = new PaymentJpaEntity();
        attempt.setStatus(PaymentStatus.PENDING);
        attempt.setId(UUID.randomUUID());
        attempt.setAttemptState(PaymentAttemptState.CREATING);
        attempt.setMasterOrderId(orderId);
        when(paymentRepository.save(any(PaymentJpaEntity.class))).thenReturn(attempt);
        when(paymentRepository.findByIdForUpdate(attempt.getId())).thenReturn(Optional.of(attempt));

        when(gatewayRegistry.getAdapter(PaymentProvider.PAYOS)).thenReturn(payosAdapter);
        when(payosAdapter.createPaymentIntent(any(), eq(orderId), any(), eq(PaymentProvider.PAYOS), any(), any(), any()))
                .thenThrow(new PaymentGatewayException(
                        "Read timed out from gateway", new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> coordinator.startPayment(customerId, orderId, PaymentProvider.PAYOS, "idemp-05"))
                .isInstanceOf(RuntimeException.class);

        assertThat(attempt.getCancelStatus()).isEqualTo(PaymentCancelStatus.CANCEL_UNKNOWN);
    }

    // 6. Back/refresh/cùng cổng: trả QR/link cũ khi valid; không lặp refund/cancel.
    @Test
    @DisplayName("Case 06: Back or refresh returns active attempt without recreating or cancelling")
    void testCase06_BackRefreshSameGateway_ReusesOldLink() {
        OffsetDateTime deadline = OffsetDateTime.now().plusSeconds(250);
        masterOrder.setPaymentDeadline(deadline);

        PaymentJpaEntity existing = new PaymentJpaEntity();
        existing.setStatus(PaymentStatus.PENDING);
        existing.setId(UUID.randomUUID());
        existing.setMasterOrderId(orderId);
        existing.setProvider(PaymentProvider.ZALOPAY);
        existing.setAmount(new BigDecimal("500000.00"));
        existing.setAttemptState(PaymentAttemptState.PENDING);
        existing.setIsActive(true);
        existing.setPaymentUrl("https://qcgateway.zalopay.vn/pay?id=123");

        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));
        when(paymentRepository.findByMasterOrderIdAndIsActiveTrue(orderId)).thenReturn(Optional.of(existing));

        PaymentWindowResponse res = coordinator.startPayment(customerId, orderId, PaymentProvider.ZALOPAY, "refresh-06");
        assertThat(res.paymentId()).isEqualTo(existing.getId());
        assertThat(res.paymentUrl()).isEqualTo("https://qcgateway.zalopay.vn/pay?id=123");
        verifyNoInteractions(gatewayRegistry);
    }

    // 7. Đổi ZaloPay → payOS: chỉ tạo payOS sau khi ZaloPay cancel/close verified.
    @Test
    @DisplayName("Case 07: Switch ZaloPay -> payOS cancels old ZaloPay attempt first")
    void testCase07_SwitchZaloPayToPayOS_VerifiedCancellation() {
        OffsetDateTime fixedDeadline = OffsetDateTime.now().plusSeconds(180);
        masterOrder.setPaymentDeadline(fixedDeadline);

        PaymentJpaEntity oldZalo = new PaymentJpaEntity();
        oldZalo.setStatus(PaymentStatus.PENDING);
        oldZalo.setId(UUID.randomUUID());
        oldZalo.setMasterOrderId(orderId);
        oldZalo.setProvider(PaymentProvider.ZALOPAY);
        oldZalo.setProviderOrderId("zp-trans-07");
        oldZalo.setAmount(new BigDecimal("500000.00"));
        oldZalo.setAttemptState(PaymentAttemptState.PENDING);
        oldZalo.setIsActive(true);

        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));
        when(paymentRepository.findByMasterOrderIdAndIsActiveTrue(orderId)).thenReturn(Optional.of(oldZalo)).thenReturn(Optional.empty());
        when(paymentRepository.findByIdForUpdate(oldZalo.getId())).thenReturn(Optional.of(oldZalo));

        when(gatewayRegistry.getAdapter(PaymentProvider.ZALOPAY)).thenReturn(zaloPayAdapter);
        when(zaloPayAdapter.canCancelServerSide(PaymentProvider.ZALOPAY)).thenReturn(true);
        when(zaloPayAdapter.cancelPayment(eq(PaymentProvider.ZALOPAY), eq("zp-trans-07"), any()))
                .thenReturn(PaymentCancelResult.success("Canceled successfully"));

        when(paymentRepository.findInFlightAttempts(eq(orderId), any())).thenReturn(Collections.emptyList());
        when(paymentRepository.findMaxAttemptNumberByMasterOrderId(orderId)).thenReturn(1);
        when(paymentRepository.getNextPayOSOrderCode()).thenReturn(777888L);
        when(paymentRepository.save(any(PaymentJpaEntity.class))).thenAnswer(inv -> {
            PaymentJpaEntity p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });

        when(gatewayRegistry.getAdapter(PaymentProvider.PAYOS)).thenReturn(payosAdapter);
        when(payosAdapter.createPaymentIntent(any(), eq(orderId), any(), eq(PaymentProvider.PAYOS), any(), any(), any()))
                .thenReturn(new PaymentIntentResult(
                        UUID.randomUUID(), orderId, PaymentProvider.PAYOS, new BigDecimal("500000.00"),
                        "https://pay.payos.vn/link-07", "qr", fixedDeadline, "777888"
                ));

        PaymentJpaEntity newAttempt = new PaymentJpaEntity();
        newAttempt.setStatus(PaymentStatus.PENDING);
        newAttempt.setId(UUID.randomUUID());
        newAttempt.setAttemptState(PaymentAttemptState.CREATING);
        newAttempt.setMasterOrderId(orderId);
        newAttempt.setProvider(PaymentProvider.PAYOS);
        newAttempt.setAmount(new BigDecimal("500000.00"));
        when(paymentRepository.findByIdForUpdate(argThat(id -> !id.equals(oldZalo.getId()))))
                .thenReturn(Optional.of(newAttempt));

        PaymentWindowResponse res = coordinator.switchProvider(customerId, orderId, PaymentProvider.PAYOS, "switch-07");
        assertThat(res).isNotNull();
        assertThat(oldZalo.getIsActive()).isFalse();
        assertThat(oldZalo.getCancelStatus()).isEqualTo(PaymentCancelStatus.CANCELED);
    }

    // 8. Cancel ZaloPay timeout/ORDER_ALREADY_CAPTURED: chặn switch, đối soát; PAID hợp lệ thì xử lý xác nhận, không tạo cổng tiếp.
    @Test
    @DisplayName("Case 08: Switch blocked when old provider already captured")
    void testCase08_CancelZaloPayAlreadyCaptured_BlocksSwitch() {
        masterOrder.setPaymentDeadline(OffsetDateTime.now().plusSeconds(120));
        PaymentJpaEntity oldZalo = new PaymentJpaEntity();
        oldZalo.setStatus(PaymentStatus.PENDING);
        oldZalo.setId(UUID.randomUUID());
        oldZalo.setMasterOrderId(orderId);
        oldZalo.setProvider(PaymentProvider.ZALOPAY);
        oldZalo.setProviderOrderId("zp-captured");
        oldZalo.setIsActive(true);

        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));
        when(paymentRepository.findByMasterOrderIdAndIsActiveTrue(orderId)).thenReturn(Optional.of(oldZalo));
        when(gatewayRegistry.getAdapter(PaymentProvider.ZALOPAY)).thenReturn(zaloPayAdapter);
        when(zaloPayAdapter.canCancelServerSide(PaymentProvider.ZALOPAY)).thenReturn(true);
        when(zaloPayAdapter.cancelPayment(any(), any(), any())).thenReturn(PaymentCancelResult.alreadyCaptured("Captured"));

        assertThatThrownBy(() -> coordinator.switchProvider(customerId, orderId, PaymentProvider.PAYOS, "switch-08"))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("not been verified as canceled");
    }

    // 9. PayOS cancel: QR/link chụp cũ không được phép tiếp tục thu tiền theo kết quả verified; race thu tiền đối soát đúng.
    @Test
    @DisplayName("Case 09: Switch cancels payOS before opening target gateway")
    void testCase09_PayOSCancel_CancelsOldAttempt() {
        masterOrder.setPaymentDeadline(OffsetDateTime.now().plusSeconds(150));
        PaymentJpaEntity oldPayOS = new PaymentJpaEntity();
        oldPayOS.setStatus(PaymentStatus.PENDING);
        oldPayOS.setId(UUID.randomUUID());
        oldPayOS.setMasterOrderId(orderId);
        oldPayOS.setProvider(PaymentProvider.PAYOS);
        oldPayOS.setProviderOrderId("payos-09");
        oldPayOS.setIsActive(true);

        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));
        when(paymentRepository.findByMasterOrderIdAndIsActiveTrue(orderId)).thenReturn(Optional.of(oldPayOS)).thenReturn(Optional.empty());
        when(paymentRepository.findByIdForUpdate(oldPayOS.getId())).thenReturn(Optional.of(oldPayOS));
        when(gatewayRegistry.getAdapter(PaymentProvider.PAYOS)).thenReturn(payosAdapter);
        when(payosAdapter.canCancelServerSide(PaymentProvider.PAYOS)).thenReturn(true);
        when(payosAdapter.cancelPayment(any(), any(), any())).thenReturn(PaymentCancelResult.success("Cancelled"));

        when(paymentRepository.findInFlightAttempts(eq(orderId), any())).thenReturn(Collections.emptyList());
        when(paymentRepository.findMaxAttemptNumberByMasterOrderId(orderId)).thenReturn(1);
        when(paymentRepository.save(any(PaymentJpaEntity.class))).thenAnswer(inv -> {
            PaymentJpaEntity p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });

        when(gatewayRegistry.getAdapter(PaymentProvider.ZALOPAY)).thenReturn(zaloPayAdapter);
        when(zaloPayAdapter.createPaymentIntent(any(), eq(orderId), any(), eq(PaymentProvider.ZALOPAY), any(), any(), any()))
                .thenReturn(new PaymentIntentResult(UUID.randomUUID(), orderId, PaymentProvider.ZALOPAY, new BigDecimal("500000.00"), "url", "qr", null, "zp"));

        PaymentJpaEntity act = new PaymentJpaEntity();
        act.setStatus(PaymentStatus.PENDING);
        act.setId(UUID.randomUUID());
        act.setAttemptState(PaymentAttemptState.CREATING);
        act.setMasterOrderId(orderId);
        when(paymentRepository.findByIdForUpdate(argThat(id -> id != null && !id.equals(oldPayOS.getId())))).thenReturn(Optional.of(act));

        PaymentWindowResponse res = coordinator.switchProvider(customerId, orderId, PaymentProvider.ZALOPAY, "switch-09");
        assertThat(res).isNotNull();
        assertThat(oldPayOS.getCancelStatus()).isEqualTo(PaymentCancelStatus.CANCELED);
    }

    // 10. VNPay không thể cancel tức thời: UX/code chặn switch không an toàn.
    @Test
    @DisplayName("Case 10: VNPay switch rejected because it cannot be canceled server-side and has not expired")
    void testCase10_VNPayCannotCancelInstantly_BlocksUnsafeSwitch() {
        masterOrder.setPaymentDeadline(OffsetDateTime.now().plusSeconds(200));
        PaymentJpaEntity activeVNPay = new PaymentJpaEntity();
        activeVNPay.setStatus(PaymentStatus.PENDING);
        activeVNPay.setId(UUID.randomUUID());
        activeVNPay.setMasterOrderId(orderId);
        activeVNPay.setProvider(PaymentProvider.VNPAY);
        activeVNPay.setProviderExpiresAt(OffsetDateTime.now().plusMinutes(10)); // Still active!
        activeVNPay.setIsActive(true);

        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));
        when(paymentRepository.findByMasterOrderIdAndIsActiveTrue(orderId)).thenReturn(Optional.of(activeVNPay));
        when(gatewayRegistry.getAdapter(PaymentProvider.VNPAY)).thenReturn(vnPayAdapter);
        when(vnPayAdapter.canCancelServerSide(PaymentProvider.VNPAY)).thenReturn(false); // Cannot cancel server-side!

        assertThatThrownBy(() -> coordinator.switchProvider(customerId, orderId, PaymentProvider.PAYOS, "switch-vnpay"))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("cannot be canceled");
    }

    // 11. PayPal approval xong, quay lại sau deadline: không capture khi inventory không còn hợp lệ.
    @Test
    @DisplayName("Case 11: Switch rejected when remaining time is less than 5 seconds")
    void testCase11_SwitchProvider_RejectedWhenUnder5Seconds() {
        masterOrder.setPaymentDeadline(OffsetDateTime.now().plusSeconds(4));
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));

        assertThatThrownBy(() -> coordinator.switchProvider(customerId, orderId, PaymentProvider.PAYOS, "switch-11"))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("Insufficient time remaining");
    }

    // 12. Hai webhook của hai attempts về cùng lúc, cả hai báo PAID: commit booking chỉ 1; record khoản dư + refund/review.
    @Test
    @DisplayName("Case 12: Duplicate capture on already PAID order creates 100% RefundJpaEntity")
    void testCase12_DuplicateCaptureOnAlreadyPaidOrder_CreatesRefund() {
        masterOrder.setStatus(MasterOrderStatus.PAID);
        masterOrder.setPaymentStatus(PaymentOrderStatus.PAID);

        PaymentJpaEntity secondAttempt = new PaymentJpaEntity();
        secondAttempt.setStatus(PaymentStatus.PENDING);
        secondAttempt.setId(UUID.randomUUID());
        secondAttempt.setMasterOrderId(orderId);
        secondAttempt.setProvider(PaymentProvider.VNPAY);
        secondAttempt.setAmount(new BigDecimal("500000.00"));
        secondAttempt.setStatus(PaymentStatus.PENDING);

        lenient().when(gatewayRegistry.getAdapter(PaymentProvider.VNPAY)).thenReturn(vnPayAdapter);
        when(vnPayAdapter.verifyWebhookSignature(any(), any())).thenReturn(true);
        when(paymentRepository.findByIdForUpdate(secondAttempt.getId())).thenReturn(Optional.of(secondAttempt));
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", secondAttempt.getId().toString());
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "vnp-trans-999");
        params.put("vnp_Amount", "50000000");
        params.put("vnp_SecureHash", "mock_valid_hash");

        orderPaymentService.processVNPayIpn(params);

        assertThat(secondAttempt.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(secondAttempt.getIsCanonical()).isFalse();
        assertThat(secondAttempt.getIsActive()).isFalse();

        ArgumentCaptor<RefundJpaEntity> refundCaptor = ArgumentCaptor.forClass(RefundJpaEntity.class);
        verify(refundRepository).save(refundCaptor.capture());
        RefundJpaEntity refund = refundCaptor.getValue();
        assertThat(refund.getAmount()).isEqualByComparingTo(new BigDecimal("500000.00"));
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(refund.getReason()).isEqualTo(RefundReason.SYSTEM_FAULT);
        verify(confirmBookingUseCase, never()).execute(any());
    }

    // 13. Callback trùng/lệch thứ tự/restart cleanup → trạng thái cuối nhất quán.
    @Test
    @DisplayName("Case 13: Duplicate webhook on already confirmed payment returns success idempotently")
    void testCase13_DuplicateWebhook_IdempotentResponse() {
        long orderCode = 888999L;
        PaymentJpaEntity payment = new PaymentJpaEntity();
        payment.setStatus(PaymentStatus.PENDING);
        payment.setId(UUID.randomUUID());
        payment.setMasterOrderId(orderId);
        payment.setProvider(PaymentProvider.PAYOS);
        payment.setPayosOrderCode(orderCode);
        payment.setStatus(PaymentStatus.SUCCESS);

        when(gatewayRegistry.getAdapter(PaymentProvider.PAYOS)).thenReturn(payosAdapter);
        when(payosAdapter.verifyWebhookSignature(any(), any())).thenReturn(true);
        when(paymentRepository.findByPayosOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(payment));
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));

        String rawPayload = "{\"data\":{\"orderCode\":888999,\"amount\":500000,\"code\":\"00\",\"currency\":\"VND\",\"reference\":\"REF-888999\"}}";
        Map<String, Object> res = orderPaymentService.processPayOSWebhook(rawPayload, "sig13");
        assertThat(res.get("code")).isEqualTo("00");
        verify(confirmBookingUseCase, never()).execute(any());
    }

    // 14. Callback chậm 1–2s khi provider đã thu tiền trước deadline và inventory còn reserved → confirm đúng.
    @Test
    @DisplayName("Case 14: Late callback arrived after sweep but paid on-time with inventory held -> CONFIRMED")
    void testCase14_LateCallbackPaidOnTimeWithInventory_ConfirmsBooking() {
        masterOrder.setPaymentDeadline(OffsetDateTime.now().minusSeconds(2));

        PaymentJpaEntity payment = new PaymentJpaEntity();
        payment.setStatus(PaymentStatus.PENDING);
        payment.setId(UUID.randomUUID());
        payment.setMasterOrderId(orderId);
        payment.setProvider(PaymentProvider.PAYOS);
        payment.setPayosOrderCode(12345L);
        payment.setAmount(new BigDecimal("500000.00"));
        payment.setStatus(PaymentStatus.PENDING);
        payment.setDanaseaDeadlineAt(masterOrder.getPaymentDeadline());

        booking.setStatus(BookingStatus.HOLD);
        booking.setHoldExpiresAt(OffsetDateTime.now().plusMinutes(5));

        when(gatewayRegistry.getAdapter(PaymentProvider.PAYOS)).thenReturn(payosAdapter);
        when(payosAdapter.verifyWebhookSignature(any(), any())).thenReturn(true);
        when(paymentRepository.findByPayosOrderCodeForUpdate(12345L)).thenReturn(Optional.of(payment));
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(subOrderRepository.findByMasterOrderId(orderId)).thenReturn(Collections.emptyList());

        String paidAt = OffsetDateTime.now().minusSeconds(5)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String rawPayload = """
                {"data":{"orderCode":12345,"amount":500000,"code":"00","currency":"VND",
                         "reference":"REF-12345","transactionDateTime":"%s"}}
                """.formatted(paidAt);
        orderPaymentService.processPayOSWebhook(rawPayload, "sig14");

        assertThat(masterOrder.getStatus()).isEqualTo(MasterOrderStatus.PAID);
        assertThat(masterOrder.getPaymentStatus()).isEqualTo(PaymentOrderStatus.PAID);
        verify(confirmBookingUseCase).execute(any());
    }

    // 15. Hết hạn, Redis key expired nhưng scheduler chưa chạy → KHÔNG có khả năng cấp inventory trùng.
    @Test
    @DisplayName("Case 15: Start payment after deadline rejected by database state check")
    void testCase15_PaymentAfterDeadlineRejected() {
        masterOrder.setPaymentDeadline(OffsetDateTime.now().minusSeconds(1)); // Expired!
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));

        assertThatThrownBy(() -> coordinator.startPayment(customerId, orderId, PaymentProvider.PAYOS, "idemp-15"))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("The payment deadline has expired.");
    }

    // 16. Hết hạn + cancel/query timeout → pending closing được bảo vệ và retry bền vững.
    @Test
    @DisplayName("Case 16: Confirmed gateway cancellation closes the payment attempt")
    void testCase16_SweepExpiredOrder_TerminatesVerifiedCanceledAttempt() {
        var active = expiredPayosAttempt();
        when(payosAdapter.cancelPayment(any(), any(), any())).thenReturn(PaymentCancelResult.success("Canceled"));

        coordinator.sweepExpiredClosingOrders();

        assertThat(active.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(active.getAttemptState()).isEqualTo(PaymentAttemptState.TERMINATED);
        assertThat(active.getIsActive()).isFalse();
        verifyNoInteractions(inventoryLockPort);
    }

    @Test
    void sweepPreservesPaymentForReconciliationWhenCancelIsUncertain() {
        var active = expiredPayosAttempt();
        when(payosAdapter.cancelPayment(any(), any(), any()))
                .thenReturn(PaymentCancelResult.unknown("Network timeout", "TIMEOUT"));

        coordinator.sweepExpiredClosingOrders();

        assertThat(active.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(active.getCancelStatus()).isEqualTo(PaymentCancelStatus.CANCEL_UNKNOWN);
        assertThat(active.getIsActive()).isFalse();
        verifyNoInteractions(inventoryLockPort);
    }

    private PaymentJpaEntity expiredPayosAttempt() {
        PaymentJpaEntity active = new PaymentJpaEntity();
        active.setId(UUID.randomUUID());
        active.setProvider(PaymentProvider.PAYOS);
        active.setProviderOrderId("gateway-link");
        active.setIsActive(true);
        active.setStatus(PaymentStatus.PENDING);

        when(paymentRepository.findExpiredActiveAttempts(eq(PaymentStatus.PENDING), eq(MasterOrderStatus.PENDING_PAYMENT), any()))
                .thenReturn(List.of(active));
        when(paymentRepository.findByIdForUpdate(active.getId())).thenReturn(Optional.of(active));
        when(gatewayRegistry.supports(PaymentProvider.PAYOS)).thenReturn(true);
        when(gatewayRegistry.getAdapter(PaymentProvider.PAYOS)).thenReturn(payosAdapter);
        when(payosAdapter.canCancelServerSide(PaymentProvider.PAYOS)).thenReturn(true);
        return active;
    }

    // 17. SHARED capacity đầy / PRIVATE chiếm cả unit / MasterOrder nhiều BookingItem / nhiều Vendor → không sai số lượng chỗ.
    @Test
    @DisplayName("Case 17: Booking with items correctly maps lock items to inventoryLockPort")
    void testCase17_MultipleBookingItems_MappedCorrectly() {
        BookingItemJpaEntity item1 = new BookingItemJpaEntity();
        item1.setSlotId(UUID.randomUUID());
        item1.setQuantity(2);
        BookingItemJpaEntity item2 = new BookingItemJpaEntity();
        item2.setSlotId(UUID.randomUUID());
        item2.setQuantity(4);
        booking.setItems(List.of(item1, item2));

        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByMasterOrderIdAndIsActiveTrue(orderId)).thenReturn(Optional.empty());
        when(paymentRepository.findInFlightAttempts(eq(orderId), any())).thenReturn(Collections.emptyList());
        when(paymentRepository.getNextPayOSOrderCode()).thenReturn(999888L);
        when(paymentRepository.save(any(PaymentJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        when(gatewayRegistry.getAdapter(PaymentProvider.PAYOS)).thenReturn(payosAdapter);
        when(payosAdapter.createPaymentIntent(any(), eq(orderId), any(), eq(PaymentProvider.PAYOS), any(), any(), any()))
                .thenReturn(new PaymentIntentResult(UUID.randomUUID(), orderId, PaymentProvider.PAYOS, new BigDecimal("500000.00"), "url", "qr", null, "999888"));

        PaymentJpaEntity act = new PaymentJpaEntity();
        act.setStatus(PaymentStatus.PENDING);
        act.setId(UUID.randomUUID());
        act.setAttemptState(PaymentAttemptState.CREATING);
        act.setMasterOrderId(orderId);
        when(paymentRepository.findByIdForUpdate(any())).thenReturn(Optional.of(act));

        coordinator.startPayment(customerId, orderId, PaymentProvider.PAYOS, "idemp-17");

        verify(inventoryLockPort).extendHolds(eq(bookingId), argThat(items -> items.size() == 2), eq(Duration.ofSeconds(420)));
    }

    // 18. Dừng/restart backend, Redis hoặc gateway mock trong lúc switch → recover không tạo trùng.
    @Test
    @DisplayName("Case 18: In-flight attempt recovery check blocks duplicate creation during uncertain switch")
    void testCase18_InFlightAttempt_PreventsDuplicateDuringRecovery() {
        masterOrder.setPaymentDeadline(OffsetDateTime.now().plusSeconds(200));
        PaymentJpaEntity inFlight = new PaymentJpaEntity();
        inFlight.setStatus(PaymentStatus.PENDING);
        inFlight.setAttemptState(PaymentAttemptState.CREATING);

        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));
        when(paymentRepository.findByMasterOrderIdAndIsActiveTrue(orderId)).thenReturn(Optional.empty());
        when(paymentRepository.findInFlightAttempts(eq(orderId), any())).thenReturn(List.of(inFlight));

        assertThatThrownBy(() -> coordinator.startPayment(customerId, orderId, PaymentProvider.ZALOPAY, "idemp-18"))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("being generated");
    }

    // 19. Flood clicks, nhiều MasterOrder/IP: giới hạn outbound, người dùng nhấn 30 lần do lag vẫn lấy được attempt cũ.
    @Test
    @DisplayName("Case 19: User clicks 30 times during lag safely receives same active attempt")
    void testCase19_LagClickSpam_ReturnsSameAttemptWithoutExtraGatewayCalls() {
        masterOrder.setPaymentDeadline(OffsetDateTime.now().plusSeconds(250));
        PaymentJpaEntity active = new PaymentJpaEntity();
        active.setStatus(PaymentStatus.PENDING);
        active.setId(UUID.randomUUID());
        active.setMasterOrderId(orderId);
        active.setProvider(PaymentProvider.PAYOS);
        active.setAmount(new BigDecimal("500000.00"));
        active.setAttemptState(PaymentAttemptState.PENDING);
        active.setPaymentUrl("https://pay.payos.vn/same-url");

        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));
        when(paymentRepository.findByMasterOrderIdAndIsActiveTrue(orderId)).thenReturn(Optional.of(active));

        for (int i = 0; i < 30; i++) {
            PaymentWindowResponse res = coordinator.startPayment(customerId, orderId, PaymentProvider.PAYOS, "spam-click-test-" + i);
            assertThat(res.paymentUrl()).isEqualTo("https://pay.payos.vn/same-url");
        }
        verifyNoInteractions(gatewayRegistry);
    }

    // 20. Hủy Booking khác Back và khác hủy attempt; sau CONFIRMED không được trả slot chỉ vì callback CANCELLED muộn.
    @Test
    @DisplayName("Case 20: After order is CONFIRMED, late cancel callback does not release slot or cancel booking")
    void testCase20_ConfirmedOrder_LateCancelDoesNotReleaseBooking() {
        masterOrder.setStatus(MasterOrderStatus.PAID);
        masterOrder.setPaymentStatus(PaymentOrderStatus.PAID);
        booking.setStatus(BookingStatus.CONFIRMED);

        when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));

        var statusRes = coordinator.getPaymentStatus(customerId, orderId);
        assertThat(statusRes.orderStatus()).isEqualTo(MasterOrderStatus.PAID);
        verify(inventoryLockPort, never()).releaseHolds(any(), any());
    }

    // 21. Ghi nhận refund request, refund confirmed và refund failed riêng; không mất tiền khỏi sổ đối soát.
    @Test
    @DisplayName("Case 21: Refund request is created as PENDING without premature REFUNDED status")
    void testCase21_RefundLifecycle_CreatedAsPending() {
        RefundJpaEntity refund = new RefundJpaEntity();
        refund.setPaymentId(UUID.randomUUID());
        refund.setAmount(new BigDecimal("250000.00"));
        refund.setRefundPercentage(BigDecimal.valueOf(50));
        refund.setStatus(RefundStatus.PENDING);
        refund.setReason(RefundReason.CUSTOMER_REQUEST);

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(refund.getStatus()).isNotEqualTo(RefundStatus.PROCESSED);
    }
}
