package com.danasea.backend.modules.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.danasea.backend.configs.properties.PaymentProperties;
import com.danasea.backend.modules.booking.application.dtos.ConfirmBookingCommand;
import com.danasea.backend.modules.booking.application.usecases.ConfirmBookingUseCase;
import com.danasea.backend.modules.booking.domain.models.BookingStatus;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingItemJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.order.application.usecases.CreatePaymentIntentUseCase;
import com.danasea.backend.modules.order.domain.exceptions.InvalidWebhookException;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentAttemptState;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.domain.ports.CommissionPolicyPort;
import com.danasea.backend.modules.order.domain.ports.PaymentCaptureResult;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import com.danasea.backend.modules.order.domain.services.RefundPolicyEngine;
import com.danasea.backend.modules.order.infrastructure.adapters.PayOSPaymentAdapter;
import com.danasea.backend.modules.order.infrastructure.adapters.PaymentGatewayRegistry;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.order.presentation.dtos.OrderResponse;
import com.danasea.backend.modules.order.presentation.dtos.PaymentWebhookResponse;
import com.danasea.backend.modules.order.presentation.dtos.RefundWebhookResponse;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class OrderPaymentServiceTest {

    @Mock private JpaBookingRepository bookingRepository;
    @Mock private JpaMasterOrderRepository masterOrderRepository;
    @Mock private JpaSubOrderRepository subOrderRepository;
    @Mock private JpaPaymentRepository paymentRepository;
    @Mock private JpaRefundRepository refundRepository;
    @Mock private JpaServiceSlotRepository serviceSlotRepository;
    @Mock private VendorInternalApi vendorInternalApi;
    @Mock private ConfirmBookingUseCase confirmBookingUseCase;
    @Mock private CreatePaymentIntentUseCase createPaymentIntentUseCase;
    @Mock private CommissionPolicyPort commissionPolicy;

    private PaymentWebhookSigner signer;
    private OrderPaymentService service;

    @BeforeEach
    void setUp() {
        signer = new PaymentWebhookSigner(new PaymentProperties(
                "test-payment-webhook-secret-minimum-256-bits-long"));
        service = new OrderPaymentService(
                bookingRepository,
                masterOrderRepository,
                subOrderRepository,
                paymentRepository,
                refundRepository,
                serviceSlotRepository,
                vendorInternalApi,
                new RefundPolicyEngine(),
                signer,
                confirmBookingUseCase,
                new ObjectMapper(),
                createPaymentIntentUseCase);
        service.setConfiguredCommission(commissionPolicy);
        lenient().when(commissionPolicy.getCommissionRate(any())).thenReturn(new BigDecimal("0.10"));
    }

    @Test
    void createOrder_isIdempotentPerBookingAndMovesHoldToPendingPayment() {
        UUID customerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        BookingJpaEntity booking = booking(customerId, bookingId);

        when(bookingRepository.findByIdWithItemsForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(masterOrderRepository.findByBookingId(bookingId)).thenReturn(Optional.empty());
        when(masterOrderRepository.findByCustomerIdAndIdempotencyKey(customerId, "order-key-123"))
                .thenReturn(Optional.empty());
        when(masterOrderRepository.save(any())).thenAnswer(invocation -> {
            MasterOrderJpaEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });
        when(subOrderRepository.saveAll(any())).thenAnswer(invocation -> {
            List<SubOrderJpaEntity> entities = invocation.getArgument(0);
            entities.forEach(entity -> entity.setId(UUID.randomUUID()));
            return entities;
        });

        OrderResponse response = service.createOrder(customerId, bookingId, "order-key-123");

        assertThat(response.status()).isEqualTo(MasterOrderStatus.PENDING_PAYMENT);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().status()).isEqualTo(SubOrderStatus.PENDING);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        verify(bookingRepository).save(booking);
    }

    @Test
    void successfulSignedWebhook_confirmsOrderSubOrdersAndBookingOnce() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentJpaEntity payment = payment(paymentId, orderId);
        MasterOrderJpaEntity order = order(orderId, bookingId, customerId);
        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(UUID.randomUUID());
        subOrder.setStatus(SubOrderStatus.PENDING);

        String payload = "{\"eventId\":\"event-123\",\"paymentId\":\"" + paymentId
                + "\",\"providerTransactionId\":\"provider-tx-123\",\"status\":\"SUCCESS\","
                + "\"amount\":250000}";
        when(paymentRepository.findByProviderAndWebhookEventId(PaymentProvider.VNPAY, "event-123"))
                .thenReturn(Optional.empty());
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(subOrderRepository.findByMasterOrderId(orderId)).thenReturn(List.of(subOrder));

        PaymentWebhookResponse response = service.processWebhook(
                PaymentProvider.VNPAY, payload, signer.sign(payload));

        assertThat(response.alreadyProcessed()).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(order.getStatus()).isEqualTo(MasterOrderStatus.PAID);
        assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.CONFIRMED);
        verify(confirmBookingUseCase).execute(new ConfirmBookingCommand(bookingId, customerId));
    }

    @Test
    void repeatedWebhookEvent_returnsStoredResultWithoutRepeatingSideEffects() {
        UUID paymentId = UUID.randomUUID();
        PaymentJpaEntity payment = payment(paymentId, UUID.randomUUID());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setWebhookEventId("event-123");
        String payload = "{\"eventId\":\"event-123\",\"paymentId\":\"" + paymentId
                + "\",\"providerTransactionId\":\"provider-tx-123\",\"status\":\"SUCCESS\","
                + "\"amount\":250000}";
        when(paymentRepository.findByProviderAndWebhookEventId(PaymentProvider.VNPAY, "event-123"))
                .thenReturn(Optional.of(payment));

        PaymentWebhookResponse response = service.processWebhook(
                PaymentProvider.VNPAY, payload, signer.sign(payload));

        assertThat(response.alreadyProcessed()).isTrue();
        verify(paymentRepository, never()).findByIdForUpdate(any());
        verify(confirmBookingUseCase, never()).execute(any());
    }

    @Test
    void invalidSignature_isRejectedBeforeDatabaseLookup() {
        String payload = "{}";

        assertThatThrownBy(() -> service.processWebhook(PaymentProvider.MOMO, payload, "invalid"))
                .isInstanceOf(InvalidWebhookException.class);

        verify(paymentRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void successfulSignedRefundWebhook_isTheOnlyPathThatCompletesARefund() {
        UUID refundId = UUID.randomUUID();
        UUID subOrderId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        RefundJpaEntity refund = new RefundJpaEntity();
        refund.setId(refundId);
        refund.setSubOrderId(subOrderId);
        refund.setAmount(new BigDecimal("250000"));
        refund.setRefundPercentage(new BigDecimal("100"));
        refund.setStatus(RefundStatus.PENDING);

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(subOrderId);
        subOrder.setMasterOrderId(orderId);
        subOrder.setStatus(SubOrderStatus.CANCELLED);

        PaymentJpaEntity payment = payment(UUID.randomUUID(), orderId);
        payment.setStatus(PaymentStatus.SUCCESS);

        MasterOrderJpaEntity order = order(orderId, UUID.randomUUID(), UUID.randomUUID());
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        String payload = "{\"eventId\":\"refund-event-1\",\"refundId\":\"" + refundId
                + "\",\"providerRefundId\":\"provider-refund-1\",\"status\":\"PROCESSED\","
                + "\"amount\":250000}";
        when(refundRepository.findByProviderAndWebhookEventId(PaymentProvider.VNPAY, "refund-event-1"))
                .thenReturn(Optional.empty());
        when(refundRepository.findByIdForUpdate(refundId)).thenReturn(Optional.of(refund));
        when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
        when(paymentRepository.findByMasterOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(List.of(payment));
        when(subOrderRepository.findByMasterOrderId(orderId)).thenReturn(List.of(subOrder));
        when(refundRepository.findBySubOrderIdInAndStatus(List.of(subOrderId), RefundStatus.PROCESSED))
                .thenReturn(List.of(refund));

        RefundWebhookResponse response = service.processRefundWebhook(
                PaymentProvider.VNPAY, payload, signer.sign(payload));

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentOrderStatus.REFUNDED);
        assertThat(response.status()).isEqualTo(RefundStatus.PROCESSED);
        assertThat(response.alreadyProcessed()).isFalse();
        assertThat(refund.getProviderRefundId()).isEqualTo("provider-refund-1");
        assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.REFUNDED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(refundRepository).save(refund);
        verify(subOrderRepository).save(subOrder);
        verify(paymentRepository).save(payment);
    }

    // ==========================================
    // payOS Webhook Tests
    // ==========================================

    @Test
    void processPayOSWebhook_emptyOrBlank_returnsCode01() {
        Map<String, Object> res1 = service.processPayOSWebhook(null, null);
        assertThat(res1).containsEntry("code", "01").containsEntry("desc", "Empty payload");

        Map<String, Object> res2 = service.processPayOSWebhook("   ", "sig");
        assertThat(res2).containsEntry("code", "01").containsEntry("desc", "Empty payload");
    }

    @Test
    void processPayOSWebhook_invalidSignature_returnsCode02() {
        PaymentGatewayRegistry registry = mock(PaymentGatewayRegistry.class);
        PayOSPaymentAdapter payosAdapter = mock(PayOSPaymentAdapter.class);
        when(registry.getAdapter(PaymentProvider.PAYOS)).thenReturn(payosAdapter);
        when(payosAdapter.verifyWebhookData(any(), any())).thenReturn(false);
        service.setPaymentGatewayRegistry(registry);

        String payload = "{\"data\":{\"orderCode\":123456,\"code\":\"00\",\"currency\":\"VND\",\"reference\":\"REF-123456\",\"amount\":250000},\"signature\":\"bad-sig\"}";
        Map<String, Object> res = service.processPayOSWebhook(payload, "bad-sig");

        assertThat(res).containsEntry("code", "02").containsEntry("desc", "Invalid signature");
    }

    @Test
    void processPayOSWebhook_orderNotFound_returnsCode01() {
        PaymentGatewayRegistry registry = mock(PaymentGatewayRegistry.class);
        PayOSPaymentAdapter payosAdapter = mock(PayOSPaymentAdapter.class);
        when(registry.getAdapter(PaymentProvider.PAYOS)).thenReturn(payosAdapter);
        when(payosAdapter.verifyWebhookData(any(), any())).thenReturn(true);
        service.setPaymentGatewayRegistry(registry);

        when(paymentRepository.findByPayosOrderCodeForUpdate(123456L)).thenReturn(Optional.empty());

        String payload = "{\"data\":{\"orderCode\":123456,\"code\":\"00\",\"currency\":\"VND\",\"reference\":\"REF-123456\",\"amount\":250000},\"signature\":\"valid-sig\"}";
        Map<String, Object> res = service.processPayOSWebhook(payload, "valid-sig");

        assertThat(res).containsEntry("code", "01").containsEntry("desc", "Order not found");
    }

    @Test
    void processPayOSWebhook_masterOrderNotFound_returnsCode01() {
        PaymentGatewayRegistry registry = mock(PaymentGatewayRegistry.class);
        PayOSPaymentAdapter payosAdapter = mock(PayOSPaymentAdapter.class);
        when(registry.getAdapter(PaymentProvider.PAYOS)).thenReturn(payosAdapter);
        when(payosAdapter.verifyWebhookData(any(), any())).thenReturn(true);
        service.setPaymentGatewayRegistry(registry);

        UUID orderId = UUID.randomUUID();
        PaymentJpaEntity payment = payment(UUID.randomUUID(), orderId);
        payment.setPayosOrderCode(123456L);
        when(paymentRepository.findByPayosOrderCodeForUpdate(123456L)).thenReturn(Optional.of(payment));
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        String payload = "{\"data\":{\"orderCode\":123456,\"code\":\"00\",\"currency\":\"VND\",\"reference\":\"REF-123456\",\"amount\":250000},\"signature\":\"valid-sig\"}";
        Map<String, Object> res = service.processPayOSWebhook(payload, "valid-sig");

        assertThat(res).containsEntry("code", "01").containsEntry("desc", "MasterOrder not found");
    }

    @Test
    void processPayOSWebhook_alreadySuccess_returnsCode00() {
        PaymentGatewayRegistry registry = mock(PaymentGatewayRegistry.class);
        PayOSPaymentAdapter payosAdapter = mock(PayOSPaymentAdapter.class);
        when(registry.getAdapter(PaymentProvider.PAYOS)).thenReturn(payosAdapter);
        when(payosAdapter.verifyWebhookData(any(), any())).thenReturn(true);
        service.setPaymentGatewayRegistry(registry);

        UUID orderId = UUID.randomUUID();
        PaymentJpaEntity payment = payment(UUID.randomUUID(), orderId);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPayosOrderCode(123456L);
        MasterOrderJpaEntity order = order(orderId, UUID.randomUUID(), UUID.randomUUID());

        when(paymentRepository.findByPayosOrderCodeForUpdate(123456L)).thenReturn(Optional.of(payment));
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        String payload = "{\"data\":{\"orderCode\":123456,\"code\":\"00\",\"currency\":\"VND\",\"reference\":\"REF-123456\",\"amount\":250000},\"signature\":\"valid-sig\"}";
        Map<String, Object> res = service.processPayOSWebhook(payload, "valid-sig");

        assertThat(res).containsEntry("code", "00").containsEntry("desc", "Success");
    }

    @Test
    void processPayOSWebhook_amountMismatch_returnsCode04() {
        PaymentGatewayRegistry registry = mock(PaymentGatewayRegistry.class);
        PayOSPaymentAdapter payosAdapter = mock(PayOSPaymentAdapter.class);
        when(registry.getAdapter(PaymentProvider.PAYOS)).thenReturn(payosAdapter);
        when(payosAdapter.verifyWebhookData(any(), any())).thenReturn(true);
        service.setPaymentGatewayRegistry(registry);

        UUID orderId = UUID.randomUUID();
        PaymentJpaEntity payment = payment(UUID.randomUUID(), orderId);
        payment.setAmount(new BigDecimal("250000"));
        payment.setPayosOrderCode(123456L);
        MasterOrderJpaEntity order = order(orderId, UUID.randomUUID(), UUID.randomUUID());

        when(paymentRepository.findByPayosOrderCodeForUpdate(123456L)).thenReturn(Optional.of(payment));
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        String payload = "{\"data\":{\"orderCode\":123456,\"code\":\"00\",\"currency\":\"VND\",\"reference\":\"REF-123456\",\"amount\":100000},\"signature\":\"valid-sig\"}";
        Map<String, Object> res = service.processPayOSWebhook(payload, "valid-sig");

        assertThat(res).containsEntry("code", "04").containsEntry("desc", "Invalid amount");
    }

    @Test
    void processPayOSWebhook_validSuccess_updatesPaymentAndOrder() {
        PaymentGatewayRegistry registry = mock(PaymentGatewayRegistry.class);
        PayOSPaymentAdapter payosAdapter = mock(PayOSPaymentAdapter.class);
        when(registry.getAdapter(PaymentProvider.PAYOS)).thenReturn(payosAdapter);
        when(payosAdapter.verifyWebhookData(any(), any())).thenReturn(true);
        service.setPaymentGatewayRegistry(registry);

        UUID customerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        PaymentJpaEntity payment = payment(paymentId, orderId);
        payment.setProvider(PaymentProvider.PAYOS);
        payment.setPayosOrderCode(123456L);
        MasterOrderJpaEntity order = order(orderId, bookingId, customerId);
        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(UUID.randomUUID());
        subOrder.setStatus(SubOrderStatus.PENDING);
        BookingJpaEntity booking = booking(customerId, bookingId);

        when(paymentRepository.findByPayosOrderCodeForUpdate(123456L)).thenReturn(Optional.of(payment));
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(subOrderRepository.findByMasterOrderId(orderId)).thenReturn(List.of(subOrder));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        String payload = "{\"data\":{\"orderCode\":123456,\"code\":\"00\",\"currency\":\"VND\",\"reference\":\"REF-123456\",\"amount\":250000,\"transactionDateTime\":\"2026-11-06 10:15:00\"},\"signature\":\"valid-sig\"}";
        Map<String, Object> res = service.processPayOSWebhook(payload, "valid-sig");

        assertThat(res).containsEntry("code", "00").containsEntry("desc", "Success");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(payment.getProviderTransactionId()).isEqualTo("REF-123456");
        assertThat(order.getStatus()).isEqualTo(MasterOrderStatus.PAID);
        assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.CONFIRMED);
        verify(confirmBookingUseCase).execute(new ConfirmBookingCommand(bookingId, customerId));
    }

    @Test
    void processPayOSWebhook_usesReceiptTimeWhenGatewayTimestampIsAbsent() {
        PaymentGatewayRegistry registry = mock(PaymentGatewayRegistry.class);
        PayOSPaymentAdapter payosAdapter = mock(PayOSPaymentAdapter.class);
        when(registry.getAdapter(PaymentProvider.PAYOS)).thenReturn(payosAdapter);
        when(payosAdapter.verifyWebhookData(any(), any())).thenReturn(true);
        service.setPaymentGatewayRegistry(registry);

        UUID customerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        PaymentJpaEntity payment = payment(paymentId, orderId);
        payment.setProvider(PaymentProvider.PAYOS);
        payment.setPayosOrderCode(123456L);
        MasterOrderJpaEntity order = order(orderId, bookingId, customerId);
        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(UUID.randomUUID());
        subOrder.setStatus(SubOrderStatus.PENDING);
        BookingJpaEntity booking = booking(customerId, bookingId);

        when(paymentRepository.findByPayosOrderCodeForUpdate(123456L)).thenReturn(Optional.of(payment));
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(subOrderRepository.findByMasterOrderId(orderId)).thenReturn(List.of(subOrder));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        String payload = "{\"data\":{\"orderCode\":123456,\"code\":\"00\",\"currency\":\"VND\",\"reference\":\"REF-123456\",\"amount\":250000},\"signature\":\"valid-sig\"}";
        Map<String, Object> res = service.processPayOSWebhook(payload, "valid-sig");

        assertThat(res).containsEntry("code", "00").containsEntry("desc", "Success");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getPaidAt()).isNotNull();
    }

    @Test
    void processPayOSWebhook_malformedJson_returnsCode99() {
        Map<String, Object> res = service.processPayOSWebhook("not-json", "sig");
        assertThat(res).containsEntry("code", "99");
    }

    // ==========================================
    // ZaloPay Callback Tests
    // ==========================================

    @Test
    void processZaloPayCallback_emptyOrBlank_returnsCode2() {
        Map<String, Object> res1 = service.processZaloPayCallback(null);
        assertThat(res1).containsEntry("return_code", 2).containsEntry("return_message", "Empty payload");

        Map<String, Object> res2 = service.processZaloPayCallback("   ");
        assertThat(res2).containsEntry("return_code", 2).containsEntry("return_message", "Empty payload");
    }

    @Test
    void processZaloPayCallback_invalidMac_returnsCodeMinus1() {
        PaymentGatewayRegistry registry = mock(PaymentGatewayRegistry.class);
        PaymentGatewayPort zaloAdapter = mock(PaymentGatewayPort.class);
        when(registry.getAdapter(PaymentProvider.ZALOPAY)).thenReturn(zaloAdapter);
        when(zaloAdapter.verifyWebhookSignature(any(), any())).thenReturn(false);
        service.setPaymentGatewayRegistry(registry);

        String payload = "{\"data\":\"{\\\"app_trans_id\\\":\\\"261106_123\\\"}\",\"mac\":\"bad-mac\"}";
        Map<String, Object> res = service.processZaloPayCallback(payload);

        assertThat(res).containsEntry("return_code", -1).containsEntry("return_message", "Invalid MAC");
    }

    @Test
    void processZaloPayCallback_orderNotFound_returnsCode2() {
        PaymentGatewayRegistry registry = mock(PaymentGatewayRegistry.class);
        PaymentGatewayPort zaloAdapter = mock(PaymentGatewayPort.class);
        when(registry.getAdapter(PaymentProvider.ZALOPAY)).thenReturn(zaloAdapter);
        when(zaloAdapter.verifyWebhookSignature(any(), any())).thenReturn(true);
        service.setPaymentGatewayRegistry(registry);

        when(paymentRepository.findByProviderAndProviderOrderIdForUpdate(PaymentProvider.ZALOPAY, "261106_123"))
                .thenReturn(Optional.empty());

        String data = "{\"app_trans_id\":\"261106_123\",\"amount\":250000}";
        String payload = "{\"data\":\"" + data.replace("\"", "\\\"") + "\",\"mac\":\"valid-mac\"}";
        Map<String, Object> res = service.processZaloPayCallback(payload);

        assertThat(res).containsEntry("return_code", 2).containsEntry("return_message", "Order not found");
    }

    @Test
    void processZaloPayCallback_masterOrderNotFound_returnsCode2() {
        PaymentGatewayRegistry registry = mock(PaymentGatewayRegistry.class);
        PaymentGatewayPort zaloAdapter = mock(PaymentGatewayPort.class);
        when(registry.getAdapter(PaymentProvider.ZALOPAY)).thenReturn(zaloAdapter);
        when(zaloAdapter.verifyWebhookSignature(any(), any())).thenReturn(true);
        service.setPaymentGatewayRegistry(registry);

        UUID orderId = UUID.randomUUID();
        PaymentJpaEntity payment = payment(UUID.randomUUID(), orderId);
        payment.setProvider(PaymentProvider.ZALOPAY);
        payment.setProviderOrderId("261106_123");

        when(paymentRepository.findByProviderAndProviderOrderIdForUpdate(PaymentProvider.ZALOPAY, "261106_123"))
                .thenReturn(Optional.of(payment));
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        String data = "{\"app_trans_id\":\"261106_123\",\"amount\":250000}";
        String payload = "{\"data\":\"" + data.replace("\"", "\\\"") + "\",\"mac\":\"valid-mac\"}";
        Map<String, Object> res = service.processZaloPayCallback(payload);

        assertThat(res).containsEntry("return_code", 2).containsEntry("return_message", "MasterOrder not found");
    }

    @Test
    void processZaloPayCallback_alreadySuccess_returnsCode1() {
        PaymentGatewayRegistry registry = mock(PaymentGatewayRegistry.class);
        PaymentGatewayPort zaloAdapter = mock(PaymentGatewayPort.class);
        when(registry.getAdapter(PaymentProvider.ZALOPAY)).thenReturn(zaloAdapter);
        when(zaloAdapter.verifyWebhookSignature(any(), any())).thenReturn(true);
        service.setPaymentGatewayRegistry(registry);

        UUID orderId = UUID.randomUUID();
        PaymentJpaEntity payment = payment(UUID.randomUUID(), orderId);
        payment.setProvider(PaymentProvider.ZALOPAY);
        payment.setProviderOrderId("261106_123");
        payment.setStatus(PaymentStatus.SUCCESS);
        MasterOrderJpaEntity order = order(orderId, UUID.randomUUID(), UUID.randomUUID());

        when(paymentRepository.findByProviderAndProviderOrderIdForUpdate(PaymentProvider.ZALOPAY, "261106_123"))
                .thenReturn(Optional.of(payment));
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        String data = "{\"app_trans_id\":\"261106_123\",\"amount\":250000}";
        String payload = "{\"data\":\"" + data.replace("\"", "\\\"") + "\",\"mac\":\"valid-mac\"}";
        Map<String, Object> res = service.processZaloPayCallback(payload);

        assertThat(res).containsEntry("return_code", 1).containsEntry("return_message", "success");
    }

    @Test
    void processZaloPayCallback_amountMismatch_returnsCode2() {
        PaymentGatewayRegistry registry = mock(PaymentGatewayRegistry.class);
        PaymentGatewayPort zaloAdapter = mock(PaymentGatewayPort.class);
        when(registry.getAdapter(PaymentProvider.ZALOPAY)).thenReturn(zaloAdapter);
        when(zaloAdapter.verifyWebhookSignature(any(), any())).thenReturn(true);
        service.setPaymentGatewayRegistry(registry);

        UUID orderId = UUID.randomUUID();
        PaymentJpaEntity payment = payment(UUID.randomUUID(), orderId);
        payment.setProvider(PaymentProvider.ZALOPAY);
        payment.setProviderOrderId("261106_123");
        payment.setAmount(new BigDecimal("250000"));
        MasterOrderJpaEntity order = order(orderId, UUID.randomUUID(), UUID.randomUUID());

        when(paymentRepository.findByProviderAndProviderOrderIdForUpdate(PaymentProvider.ZALOPAY, "261106_123"))
                .thenReturn(Optional.of(payment));
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        String data = "{\"app_trans_id\":\"261106_123\",\"amount\":100000}";
        String payload = "{\"data\":\"" + data.replace("\"", "\\\"") + "\",\"mac\":\"valid-mac\"}";
        Map<String, Object> res = service.processZaloPayCallback(payload);

        assertThat(res).containsEntry("return_code", 2).containsEntry("return_message", "Invalid amount");
    }

    @Test
    void processZaloPayCallback_validSuccess_updatesPaymentAndOrder() {
        PaymentGatewayRegistry registry = mock(PaymentGatewayRegistry.class);
        PaymentGatewayPort zaloAdapter = mock(PaymentGatewayPort.class);
        when(registry.getAdapter(PaymentProvider.ZALOPAY)).thenReturn(zaloAdapter);
        when(zaloAdapter.verifyWebhookSignature(any(), any())).thenReturn(true);
        service.setPaymentGatewayRegistry(registry);

        UUID customerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        PaymentJpaEntity payment = payment(paymentId, orderId);
        payment.setProvider(PaymentProvider.ZALOPAY);
        payment.setProviderOrderId("261106_123");
        MasterOrderJpaEntity order = order(orderId, bookingId, customerId);
        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(UUID.randomUUID());
        subOrder.setStatus(SubOrderStatus.PENDING);
        BookingJpaEntity booking = booking(customerId, bookingId);

        when(paymentRepository.findByProviderAndProviderOrderIdForUpdate(PaymentProvider.ZALOPAY, "261106_123"))
                .thenReturn(Optional.of(payment));
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(subOrderRepository.findByMasterOrderId(orderId)).thenReturn(List.of(subOrder));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        String data = "{\"app_trans_id\":\"261106_123\",\"amount\":250000,\"server_time\":1730880000000,\"zp_trans_id\":\"ZP-999\"}";
        String payload = "{\"data\":\"" + data.replace("\"", "\\\"") + "\",\"mac\":\"valid-mac\"}";
        Map<String, Object> res = service.processZaloPayCallback(payload);

        assertThat(res).containsEntry("return_code", 1).containsEntry("return_message", "success");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(payment.getProviderTransactionId()).isEqualTo("ZP-999");
        assertThat(order.getStatus()).isEqualTo(MasterOrderStatus.PAID);
        assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.CONFIRMED);
        verify(confirmBookingUseCase).execute(new ConfirmBookingCommand(bookingId, customerId));
    }

    @Test
    void processZaloPayCallback_malformedDataJson_returnsCode2() {
        PaymentGatewayRegistry registry = mock(PaymentGatewayRegistry.class);
        PaymentGatewayPort zaloAdapter = mock(PaymentGatewayPort.class);
        when(registry.getAdapter(PaymentProvider.ZALOPAY)).thenReturn(zaloAdapter);
        when(zaloAdapter.verifyWebhookSignature(any(), any())).thenReturn(true);
        service.setPaymentGatewayRegistry(registry);

        String payload = "{\"data\":\"not-json\",\"mac\":\"valid-mac\"}";
        Map<String, Object> res = service.processZaloPayCallback(payload);
        assertThat(res).containsEntry("return_code", 2);
    }

    // ==========================================
    // reconcilePaymentWithGateway Tests
    // ==========================================

    @Test
    void statusReconciliationIgnoresAnOrderWithoutAnActivePendingPayment() {
        UUID orderId = UUID.randomUUID();
        when(paymentRepository.findByMasterOrderIdAndIsActiveTrue(orderId)).thenReturn(Optional.empty());

        assertThat(service.reconcilePaymentWithGateway(orderId)).isFalse();

        PaymentJpaEntity payment = payment(UUID.randomUUID(), orderId);
        payment.setStatus(PaymentStatus.FAILED);
        when(paymentRepository.findByMasterOrderIdAndIsActiveTrue(orderId)).thenReturn(Optional.of(payment));
        assertThat(service.reconcilePaymentWithGateway(orderId)).isFalse();
    }

    @Test
    void reconcilePaymentWithGatewayRejectsMismatchedGatewayMoney() {
        UUID orderId = UUID.randomUUID();
        MasterOrderJpaEntity order = order(orderId, UUID.randomUUID(), UUID.randomUUID());
        PaymentJpaEntity payment = payment(UUID.randomUUID(), orderId);
        PaymentGatewayPort adapter = prepareStatusReconciliation(order, payment);
        when(adapter.queryPayment(PaymentProvider.ZALOPAY, "merchant-ref", null))
                .thenReturn(new PaymentCaptureResult(true, "capture-ref", BigDecimal.ONE, "USD", "COMPLETED", null));

        assertThat(service.reconcilePaymentWithGateway(orderId)).isFalse();
        assertThat(order.getStatus()).isEqualTo(MasterOrderStatus.PENDING_PAYMENT);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void reconcilePaymentWithGatewayUsesVerifiedCaptureReference() {
        UUID customerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        MasterOrderJpaEntity order = order(orderId, bookingId, customerId);
        PaymentJpaEntity payment = payment(UUID.randomUUID(), orderId);
        PaymentGatewayPort adapter = prepareStatusReconciliation(order, payment);
        when(adapter.queryPayment(PaymentProvider.ZALOPAY, "merchant-ref", null))
                .thenReturn(new PaymentCaptureResult(true, "capture-ref", order.getTotalAmount(), "VND", "COMPLETED", null));

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(UUID.randomUUID());
        subOrder.setStatus(SubOrderStatus.PENDING);
        when(subOrderRepository.findByMasterOrderId(orderId)).thenReturn(List.of(subOrder));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking(customerId, bookingId)));

        assertThat(service.reconcilePaymentWithGateway(orderId)).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getProviderTransactionId()).isEqualTo("capture-ref");
        assertThat(order.getStatus()).isEqualTo(MasterOrderStatus.PAID);
        assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.CONFIRMED);
    }

    private PaymentGatewayPort prepareStatusReconciliation(MasterOrderJpaEntity order, PaymentJpaEntity payment) {
        var transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any()))
                .thenAnswer(invocation -> new SimpleTransactionStatus());
        service = new OrderPaymentService(bookingRepository, masterOrderRepository, subOrderRepository,
                paymentRepository, refundRepository, serviceSlotRepository, vendorInternalApi,
                new RefundPolicyEngine(), signer, confirmBookingUseCase, new ObjectMapper(),
                createPaymentIntentUseCase, null, null, transactionManager);

        payment.setProvider(PaymentProvider.ZALOPAY);
        payment.setProviderOrderId("merchant-ref");
        payment.setProviderAmount(order.getTotalAmount());
        payment.setProviderCurrency("VND");
        PaymentGatewayRegistry registry = mock(PaymentGatewayRegistry.class);
        PaymentGatewayPort adapter = mock(PaymentGatewayPort.class);
        when(registry.getAdapter(PaymentProvider.ZALOPAY)).thenReturn(adapter);
        service.setPaymentGatewayRegistry(registry);
        when(masterOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.findByMasterOrderIdAndIsActiveTrue(order.getId())).thenReturn(Optional.of(payment));
        return adapter;
    }

    private BookingJpaEntity booking(UUID customerId, UUID bookingId) {
        BookingJpaEntity booking = BookingJpaEntity.builder()
                .customerId(customerId)
                .status(BookingStatus.HOLD)
                .totalAmount(new BigDecimal("250000"))
                .holdExpiresAt(OffsetDateTime.now().plusMinutes(10))
                .build();
        booking.setId(bookingId);
        BookingItemJpaEntity item = BookingItemJpaEntity.builder()
                .serviceId(UUID.randomUUID())
                .vendorId(UUID.randomUUID())
                .slotId(UUID.randomUUID())
                .quantity(1)
                .bookingDate(LocalDate.now().plusDays(2))
                .bookingTime(LocalTime.NOON)
                .price(new BigDecimal("250000"))
                .build();
        item.setId(UUID.randomUUID());
        booking.addItem(item);
        return booking;
    }

    private PaymentJpaEntity payment(UUID paymentId, UUID orderId) {
        PaymentJpaEntity payment = new PaymentJpaEntity();
        payment.setId(paymentId);
        payment.setMasterOrderId(orderId);
        payment.setProvider(PaymentProvider.VNPAY);
        payment.setAmount(new BigDecimal("250000"));
        payment.setStatus(PaymentStatus.PENDING);
        return payment;
    }

    private MasterOrderJpaEntity order(UUID orderId, UUID bookingId, UUID customerId) {
        MasterOrderJpaEntity order = new MasterOrderJpaEntity();
        order.setId(orderId);
        order.setBookingId(bookingId);
        order.setCustomerId(customerId);
        order.setTotalAmount(new BigDecimal("250000"));
        order.setStatus(MasterOrderStatus.PENDING_PAYMENT);
        return order;
    }
}
