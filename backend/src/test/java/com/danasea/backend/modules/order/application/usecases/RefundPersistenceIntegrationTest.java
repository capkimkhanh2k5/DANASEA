package com.danasea.backend.modules.order.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.danasea.backend.e2e.BaseE2ETest;
import com.danasea.backend.modules.booking.domain.models.BookingStatus;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.order.application.OrderPaymentService;
import com.danasea.backend.modules.order.application.RefundProcessingService;
import com.danasea.backend.modules.order.application.dtos.OrderRefundResult;
import com.danasea.backend.modules.order.application.dtos.RequestRefundCommand;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.domain.ports.GatewayRefundRequest;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.order.presentation.dtos.RefundDetailResponse;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@ActiveProfiles("test")
@DisplayName("Refund Persistence and End-to-End Processing Integration Test")
class RefundPersistenceIntegrationTest extends BaseE2ETest {

    @MockitoBean
    private io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager<byte[]> proxyManager;

    @MockitoBean
    private PaymentGatewayPort paymentGatewayPort;

    @Autowired
    private RefundProcessingService refundProcessingService;

    @Autowired
    private RequestRefundUseCase requestRefundUseCase;

    @Autowired
    private OrderPaymentService orderPaymentService;

    @Autowired
    private JpaMasterOrderRepository masterOrderRepository;

    @Autowired
    private JpaSubOrderRepository subOrderRepository;

    @Autowired
    private JpaPaymentRepository paymentRepository;

    @Autowired
    private JpaRefundRepository refundRepository;

    @Autowired
    private JpaBookingRepository bookingRepository;

    @Autowired
    private JpaServiceSlotRepository serviceSlotRepository;

    private UUID customerId;
    private UUID bookingId;
    private UUID masterOrderId;
    private UUID subOrderId;
    private UUID slotId;

    @BeforeEach
    void setUp() {
        customerId = customerUser.getId();
        ServiceSlotJpaEntity slot = slotA1TomorrowMorning;
        slot.setDate(LocalDate.now().plusDays(5));
        slot.setBookedCount(5);
        serviceSlotRepository.saveAndFlush(slot);
        slotId = slot.getId();

        BookingJpaEntity booking = new BookingJpaEntity();
        booking.setCustomerId(customerId);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setTotalAmount(BigDecimal.valueOf(500000));
        bookingId = bookingRepository.saveAndFlush(booking).getId();

        paidOrder.setBookingId(bookingId);
        paidOrder.setPaymentStatus(PaymentOrderStatus.PAID);
        paidOrder.setTotalAmount(BigDecimal.valueOf(500000));
        paidOrder.setDiscountAmount(BigDecimal.ZERO);
        masterOrderId = masterOrderRepository.saveAndFlush(paidOrder).getId();
        paidSubOrder.setUnitPrice(BigDecimal.valueOf(250000));
        paidSubOrder.setSubtotalAmount(BigDecimal.valueOf(500000));
        paidSubOrder.setDiscountAmount(BigDecimal.ZERO);
        paidSubOrder.setFinalAmount(BigDecimal.valueOf(500000));
        subOrderId = subOrderRepository.saveAndFlush(paidSubOrder).getId();

        paidPayment.setProvider(PaymentProvider.PAYPAL);
        paidPayment.setProviderTransactionId("PAYPAL-CAPTURE-XYZ999");
        paidPayment.setProviderAmount(new BigDecimal("19.31"));
        paidPayment.setProviderCurrency("USD");
        paidPayment.setAmount(BigDecimal.valueOf(500000));
        paymentRepository.saveAndFlush(paidPayment);
    }

    @AfterEach
    void removeRefundFixture() {
        refundRepository.deleteAll();
        if (masterOrderId != null) {
            masterOrderRepository.findById(masterOrderId).ifPresent(order -> {
                order.setBookingId(null);
                masterOrderRepository.saveAndFlush(order);
            });
        }
        if (bookingId != null) {
            bookingRepository.deleteById(bookingId);
        }
    }

    @Test
    @DisplayName("Should persist refund PENDING, execute through gateway, become PROCESSED and sync states")
    void shouldPersistAndProcessRefundEndToEnd() {
        String idempotencyKey = "refund-idemp-test-01";
        RequestRefundCommand command = new RequestRefundCommand(
                customerId,
                masterOrderId,
                RefundReason.CUSTOMER_REQUEST,
                idempotencyKey,
                LocalDateTime.now()
        );

        // Execute refund
        List<OrderRefundResult> results = requestRefundUseCase.execute(command);

        assertThat(results).hasSize(1);
        OrderRefundResult result = results.get(0);
        assertThat(result.subOrderId()).isEqualTo(subOrderId);
        assertThat(result.amount()).isEqualByComparingTo(BigDecimal.valueOf(500000));
        assertThat(result.status()).isEqualTo(RefundStatus.PENDING);
        assertThat(refundProcessingService.processRefund(result.refundId())).isFalse();
        assertThat(refundRepository.findById(result.refundId()).orElseThrow().getStatus()).isEqualTo(RefundStatus.PENDING);
        refundProcessingService.completeManualRefund(result.refundId(), adminUser.getId(), "PAYPAL-REF-REAL-01",
                new BigDecimal("19.31"), "USD");
        org.mockito.Mockito.verify(paymentGatewayPort, org.mockito.Mockito.never()).requestRefund(any(GatewayRefundRequest.class));

        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(customerId).stream()
                .filter(notification -> "REFUND_COMPLETED".equals(notification.getType())).count()).isEqualTo(1);

        // 1. Verify Refund DB Persistence
        RefundJpaEntity persistedRefund = refundRepository.findById(result.refundId()).orElseThrow();
        assertThat(persistedRefund.getStatus()).isEqualTo(RefundStatus.PROCESSED);
        assertThat(persistedRefund.getProvider()).isEqualTo(PaymentProvider.PAYPAL);
        assertThat(persistedRefund.getProviderTransactionId()).isEqualTo("PAYPAL-CAPTURE-XYZ999");
        assertThat(persistedRefund.getProviderRefundId()).isNotNull();
        assertThat(persistedRefund.getProcessedAt()).isNotNull();
        assertThat(persistedRefund.getIdempotencyKey()).isEqualTo(idempotencyKey);

        // 2. Test Idempotency: Gửi lại cùng idempotency key
        List<OrderRefundResult> repeatedResults = requestRefundUseCase.execute(command);
        assertThat(repeatedResults).hasSize(1);
        assertThat(repeatedResults.get(0).refundId()).isEqualTo(persistedRefund.getId());
        assertThat(refundRepository.findBySubOrderId(subOrderId)).hasSize(1); // Không tạo thêm bản ghi

        // 3. Verify Order & SubOrder states
        SubOrderJpaEntity updatedSubOrder = subOrderRepository.findById(subOrderId).orElseThrow();
        assertThat(updatedSubOrder.getStatus()).isEqualTo(SubOrderStatus.REFUNDED);

        MasterOrderJpaEntity updatedMasterOrder = masterOrderRepository.findById(masterOrderId).orElseThrow();
        assertThat(updatedMasterOrder.getStatus()).isEqualTo(MasterOrderStatus.CANCELLED);

        BookingJpaEntity updatedBooking = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(updatedBooking.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        // 4. Verify Inventory slot count released
        ServiceSlotJpaEntity updatedSlot = serviceSlotRepository.findById(slotId).orElseThrow();
        assertThat(updatedSlot.getBookedCount()).isEqualTo(3); // 5 - 2 = 3

        // 5. Test Query Refund details for Customer
        List<RefundDetailResponse> customerRefunds = orderPaymentService.getOrderRefunds(customerId, masterOrderId, false);
        assertThat(customerRefunds).hasSize(1);
        assertThat(customerRefunds.get(0).id()).isEqualTo(persistedRefund.getId());
        assertThat(customerRefunds.get(0).providerTransactionId()).isEqualTo("PAYPAL-CAPTURE-XYZ999");

        // 6. Test Query Refund details for Admin
        RefundDetailResponse adminDetail = orderPaymentService.getAdminRefundDetail(persistedRefund.getId());
        assertThat(adminDetail.id()).isEqualTo(persistedRefund.getId());
        assertThat(adminDetail.status()).isEqualTo(RefundStatus.PROCESSED);
    }
}
