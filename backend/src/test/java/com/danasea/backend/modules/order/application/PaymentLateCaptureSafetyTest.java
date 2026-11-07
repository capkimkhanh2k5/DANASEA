package com.danasea.backend.modules.order.application;

import com.danasea.backend.modules.booking.application.usecases.ConfirmBookingUseCase;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.order.application.usecases.CreatePaymentIntentUseCase;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentAttemptState;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import com.danasea.backend.modules.order.domain.services.RefundPolicyEngine;
import com.danasea.backend.modules.order.infrastructure.adapters.PaymentGatewayRegistry;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Payment Late Capture & Void Unfulfilled Refund Safety Tests")
class PaymentLateCaptureSafetyTest {

    @Mock private JpaBookingRepository bookingRepository;
    @Mock private JpaMasterOrderRepository masterOrderRepository;
    @Mock private JpaSubOrderRepository subOrderRepository;
    @Mock private JpaPaymentRepository paymentRepository;
    @Mock private JpaRefundRepository refundRepository;
    @Mock private JpaServiceSlotRepository serviceSlotRepository;
    @Mock private VendorInternalApi vendorInternalApi;
    @Mock private ConfirmBookingUseCase confirmBookingUseCase;
    @Mock private CreatePaymentIntentUseCase createPaymentIntentUseCase;
    @Mock private PaymentGatewayRegistry gatewayRegistry;
    @Mock private PaymentGatewayPort payosAdapter;
    @Mock private PaymentGatewayPort zaloPayAdapter;

    private OrderPaymentService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new OrderPaymentService(
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
                createPaymentIntentUseCase
        );
        service.setPaymentGatewayRegistry(gatewayRegistry);
    }

    @Test
    @DisplayName("payOS late webhook for locally expired order records payment success, voids booking, and creates SYSTEM_FAULT refund")
    void testPayOSLateCaptureRefundUnfulfilled() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        long orderCode = 123456L;

        PaymentJpaEntity payment = new PaymentJpaEntity();
        payment.setId(UUID.randomUUID());
        payment.setMasterOrderId(orderId);
        payment.setProvider(PaymentProvider.PAYOS);
        payment.setPayosOrderCode(orderCode);
        payment.setAmount(new BigDecimal("500000.00"));
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAttemptState(PaymentAttemptState.PENDING);

        MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
        masterOrder.setId(orderId);
        masterOrder.setCustomerId(customerId);
        masterOrder.setBookingId(bookingId);
        masterOrder.setTotalAmount(new BigDecimal("500000.00"));
        // Order had timed out locally before gateway callback arrived
        masterOrder.setStatus(MasterOrderStatus.CANCELLED);
        masterOrder.setPaymentStatus(PaymentOrderStatus.UNPAID);

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(UUID.randomUUID());
        subOrder.setMasterOrderId(orderId);
        subOrder.setBookingItemId(UUID.randomUUID());
        subOrder.setFinalAmount(new BigDecimal("500000.00"));
        subOrder.setStatus(SubOrderStatus.PENDING);

        when(gatewayRegistry.getAdapter(PaymentProvider.PAYOS)).thenReturn(payosAdapter);
        when(payosAdapter.verifyWebhookSignature(any(), any())).thenReturn(true);

        when(paymentRepository.findByPayosOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(payment));
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));
        when(subOrderRepository.findByMasterOrderId(orderId)).thenReturn(List.of(subOrder));

        String rawPayload = """
                {
                    "code": "00",
                    "desc": "success",
                    "data": {
                        "orderCode": 123456,
                        "code": "00",
                        "currency": "VND",
                        "amount": 500000.00,
                        "reference": "PAYOS_REF_999",
                        "paymentLinkId": "link-123"
                    },
                    "signature": "valid_sig"
                }
                """;

        Map<String, Object> response = service.processPayOSWebhook(rawPayload, "valid_sig");

        assertThat(response.get("code")).isEqualTo("00");

        // 1. Payment row must be marked SUCCESS and canonical
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getIsCanonical()).isTrue();
        assertThat(payment.getAttemptState()).isEqualTo(PaymentAttemptState.COMPLETED);
        verify(paymentRepository).save(payment);

        // 2. SubOrder must be CANCELLED with SYSTEM_FAULT
        assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.CANCELLED);
        assertThat(subOrder.getCancellationReason()).isEqualTo(RefundReason.SYSTEM_FAULT);

        // 3. A RefundJpaEntity must be created with PENDING and 100% amount
        ArgumentCaptor<RefundJpaEntity> refundCaptor = ArgumentCaptor.forClass(RefundJpaEntity.class);
        verify(refundRepository).save(refundCaptor.capture());
        RefundJpaEntity refund = refundCaptor.getValue();
        assertThat(refund.getAmount()).isEqualByComparingTo("500000.00");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(refund.getPaymentId()).isEqualTo(payment.getId());

        // 4. Booking must be voided via confirmBookingUseCase.voidUnfulfilledPayment
        verify(confirmBookingUseCase).voidUnfulfilledPayment(bookingId);
        verify(confirmBookingUseCase, never()).execute(any());

        // 5. MasterOrder remains CANCELLED with paymentStatus PAID
        assertThat(masterOrder.getStatus()).isEqualTo(MasterOrderStatus.CANCELLED);
        assertThat(masterOrder.getPaymentStatus()).isEqualTo(PaymentOrderStatus.PAID);
    }

    @Test
    @DisplayName("ZaloPay late callback for locally expired order records payment success and creates SYSTEM_FAULT refund")
    void testZaloPayLateCaptureRefundUnfulfilled() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        String appTransId = "261105_777888";

        PaymentJpaEntity payment = new PaymentJpaEntity();
        payment.setId(UUID.randomUUID());
        payment.setMasterOrderId(orderId);
        payment.setProvider(PaymentProvider.ZALOPAY);
        payment.setProviderOrderId(appTransId);
        payment.setAmount(new BigDecimal("750000.00"));
        payment.setStatus(PaymentStatus.PENDING);

        MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
        masterOrder.setId(orderId);
        masterOrder.setCustomerId(customerId);
        masterOrder.setBookingId(bookingId);
        masterOrder.setTotalAmount(new BigDecimal("750000.00"));
        masterOrder.setStatus(MasterOrderStatus.CANCELLED);
        masterOrder.setPaymentStatus(PaymentOrderStatus.UNPAID);

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(UUID.randomUUID());
        subOrder.setMasterOrderId(orderId);
        subOrder.setBookingItemId(UUID.randomUUID());
        subOrder.setFinalAmount(new BigDecimal("750000.00"));
        subOrder.setStatus(SubOrderStatus.PENDING);

        when(gatewayRegistry.getAdapter(PaymentProvider.ZALOPAY)).thenReturn(zaloPayAdapter);
        when(zaloPayAdapter.verifyWebhookSignature(any(), any())).thenReturn(true);

        when(paymentRepository.findByProviderAndProviderOrderIdForUpdate(PaymentProvider.ZALOPAY, appTransId))
                .thenReturn(Optional.of(payment));
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));
        when(subOrderRepository.findByMasterOrderId(orderId)).thenReturn(List.of(subOrder));

        String rawPayload = """
                {
                    "data": "{\\"app_id\\":2553,\\"app_trans_id\\":\\"261105_777888\\",\\"amount\\":750000.00,\\"zp_trans_id\\":\\"ZP123\\"}",
                    "mac": "valid_mac"
                }
                """;

        Map<String, Object> response = service.processZaloPayCallback(rawPayload);

        assertThat(response.get("return_code")).isEqualTo(1);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getIsCanonical()).isTrue();

        verify(refundRepository).save(argThat(r ->
                r.getAmount().compareTo(new BigDecimal("750000.00")) == 0
                && r.getStatus() == RefundStatus.PENDING
        ));
        verify(confirmBookingUseCase).voidUnfulfilledPayment(bookingId);
    }

    @Test
    @DisplayName("Duplicate capture on ALREADY PAID order records payment success, non-canonical, and creates 100% refund")
    void testDuplicatePaymentOnAlreadyPaidOrderCreatesRefund() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        long orderCode = 888999L;

        PaymentJpaEntity payment = new PaymentJpaEntity();
        payment.setId(UUID.randomUUID());
        payment.setMasterOrderId(orderId);
        payment.setProvider(PaymentProvider.PAYOS);
        payment.setPayosOrderCode(orderCode);
        payment.setAmount(new BigDecimal("500000.00"));
        payment.setStatus(PaymentStatus.PENDING);

        MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
        masterOrder.setId(orderId);
        masterOrder.setCustomerId(customerId);
        masterOrder.setBookingId(bookingId);
        masterOrder.setTotalAmount(new BigDecimal("500000.00"));
        // Order was ALREADY PAID by another attempt!
        masterOrder.setStatus(MasterOrderStatus.PAID);
        masterOrder.setPaymentStatus(PaymentOrderStatus.PAID);

        when(gatewayRegistry.getAdapter(PaymentProvider.PAYOS)).thenReturn(payosAdapter);
        when(payosAdapter.verifyWebhookSignature(any(), any())).thenReturn(true);
        when(paymentRepository.findByPayosOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(payment));
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(masterOrder));

        String rawPayload = """
                {
                    "code": "00",
                    "desc": "success",
                    "data": {
                        "orderCode": 888999,
                        "code": "00",
                        "currency": "VND",
                        "amount": 500000.00,
                        "reference": "REF_DUP_123"
                    },
                    "signature": "valid_sig"
                }
                """;

        Map<String, Object> response = service.processPayOSWebhook(rawPayload, "valid_sig");

        assertThat(response.get("code")).isEqualTo("00");

        // Payment recorded as SUCCESS, but non-canonical (order was already paid by another attempt)
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getIsCanonical()).isFalse();
        assertThat(payment.getIsActive()).isFalse();

        // 100% refund must be created for the overpaid amount
        ArgumentCaptor<RefundJpaEntity> refundCaptor = ArgumentCaptor.forClass(RefundJpaEntity.class);
        verify(refundRepository).save(refundCaptor.capture());
        RefundJpaEntity refund = refundCaptor.getValue();
        assertThat(refund.getAmount()).isEqualByComparingTo("500000.00");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(refund.getReason()).isEqualTo(RefundReason.SYSTEM_FAULT);

        // Booking confirmation is not touched
        verifyNoInteractions(confirmBookingUseCase);
    }
}
