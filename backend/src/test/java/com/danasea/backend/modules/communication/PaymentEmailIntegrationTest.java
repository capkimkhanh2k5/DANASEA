package com.danasea.backend.modules.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.communication.application.email.EmailSendResult;
import com.danasea.backend.modules.communication.application.email.RenderedEmail;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailSender;
import com.danasea.backend.modules.communication.domain.models.EmailDeliveryMode;
import com.danasea.backend.modules.communication.domain.models.EmailDeliveryStatus;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;
import com.danasea.backend.modules.communication.infrastructure.email.TransactionalEmailProperties;
import com.danasea.backend.modules.communication.infrastructure.email.TransactionalEmailWorker;
import com.danasea.backend.modules.communication.infrastructure.email.entities.TransactionalEmailDeliveryJpaEntity;
import com.danasea.backend.modules.communication.infrastructure.email.repositories.JpaTransactionalEmailDeliveryRepository;
import com.danasea.backend.modules.communication.infrastructure.listeners.PaymentNotificationEventListener;
import com.danasea.backend.modules.order.domain.events.PaymentSuccessEvent;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;

@ActiveProfiles("test")
class PaymentEmailIntegrationTest extends BaseEmailIntegrationTest {

    @Autowired private PaymentNotificationEventListener listener;
    @Autowired private TransactionalEmailWorker worker;
    @Autowired private JpaTransactionalEmailDeliveryRepository emailRepository;
    @Autowired private JpaMasterOrderRepository masterOrderRepository;
    @Autowired private JpaSubOrderRepository subOrderRepository;
    @Autowired private JpaRefundRepository refundRepository;
    @Autowired private JpaVendorRepository vendorRepository;
    @Autowired private JpaServiceRepository serviceRepository;
    @Autowired private JpaServiceSlotRepository slotRepository;
    @Autowired private TransactionalEmailProperties properties;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean(name = "captureTransactionalEmailSender")
    private TransactionalEmailSender captureSender;

    @MockitoBean
    private AccountInternalApi accountInternalApi;

    private TransactionTemplate transactionTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registerEmailDatabase(registry, "payment");
        registry.add("app.transactional-email.mode", () -> "capture");
        registry.add("app.transactional-email.worker.enabled", () -> "true");
        registry.add("app.transactional-email.enabled-families", () -> "BOOKING,TRIP,DISRUPTION,REFUND");
    }

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        reset(captureSender);
        when(captureSender.send(any())).thenReturn(EmailSendResult.accepted());
    }

    private User createMockUser(UUID id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setIsEmailVerified(true);
        user.setIsLocked(false);
        user.setLocale("vi");
        return user;
    }

    private VendorJpaEntity createVendor(UUID vendorUserId) {
        VendorJpaEntity vendor = new VendorJpaEntity();
        vendor.setId(UUID.randomUUID());
        vendor.setUserId(vendorUserId);
        vendor.setBusinessName("Vendor Tour " + vendor.getId().toString().substring(0, 6));
        return vendorRepository.saveAndFlush(vendor);
    }

    private ServiceJpaEntity createService(UUID vendorId, String name) {
        ServiceJpaEntity s = new ServiceJpaEntity();
        s.setId(UUID.randomUUID());
        s.setVendorId(vendorId);
        s.setName(name);
        s.setNameEn(name + " EN");
        s.setAddress("Bãi biển Mỹ Khê, Đà Nẵng");
        s.setLocationName("Đà Nẵng");
        return serviceRepository.saveAndFlush(s);
    }

    private ServiceSlotJpaEntity createSlot(UUID serviceId, LocalDate date, LocalTime time) {
        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(UUID.randomUUID());
        slot.setServiceId(serviceId);
        slot.setDate(date);
        slot.setStartTime(time);
        slot.setEndTime(time.plusHours(2));
        slot.setCapacity(20);
        slot.setBookedCount(2);
        slot.setStatus(SlotStatus.OPEN);
        return slotRepository.saveAndFlush(slot);
    }

    @Test
    @DisplayName("PaymentSuccessEvent creates exactly one customer email and one vendor email per vendor")
    void validPayment_createsOneCustomerAndOneVendorEmailPerVendor() {
        UUID customerId = UUID.randomUUID();
        UUID vendorUserId1 = UUID.randomUUID();
        UUID vendorUserId2 = UUID.randomUUID();

        when(accountInternalApi.findUserById(customerId)).thenReturn(Optional.of(createMockUser(customerId, "customer@example.com")));
        when(accountInternalApi.findUserById(vendorUserId1)).thenReturn(Optional.of(createMockUser(vendorUserId1, "vendor1@example.com")));
        when(accountInternalApi.findUserById(vendorUserId2)).thenReturn(Optional.of(createMockUser(vendorUserId2, "vendor2@example.com")));

        VendorJpaEntity v1 = createVendor(vendorUserId1);
        VendorJpaEntity v2 = createVendor(vendorUserId2);
        ServiceJpaEntity s1 = createService(v1.getId(), "Lặn ngắm san hô Cù Lao Chàm");
        ServiceJpaEntity s2 = createService(v2.getId(), "Chèo thuyền Kayak sông Hàn");
        ServiceSlotJpaEntity slot1 = createSlot(s1.getId(), LocalDate.now().plusDays(2), LocalTime.of(8, 30));
        ServiceSlotJpaEntity slot2 = createSlot(s2.getId(), LocalDate.now().plusDays(2), LocalTime.of(15, 0));

        MasterOrderJpaEntity order = new MasterOrderJpaEntity();
        order.setId(UUID.randomUUID());
        order.setCustomerId(customerId);
        order.setPaymentStatus(PaymentOrderStatus.PAID);
        order.setStatus(MasterOrderStatus.PAID);
        order.setTotalAmount(new BigDecimal("1200000"));
        order.setDiscountAmount(BigDecimal.ZERO);
        masterOrderRepository.saveAndFlush(order);

        SubOrderJpaEntity so1 = new SubOrderJpaEntity();
        so1.setId(UUID.randomUUID());
        so1.setMasterOrderId(order.getId());
        so1.setVendorId(v1.getId());
        so1.setServiceId(s1.getId());
        so1.setSlotId(slot1.getId());
        so1.setQuantity(2);
        so1.setUnitPrice(new BigDecimal("350000"));
        so1.setSubtotalAmount(new BigDecimal("700000"));
        so1.setStatus(SubOrderStatus.CONFIRMED);
        subOrderRepository.saveAndFlush(so1);

        SubOrderJpaEntity so2 = new SubOrderJpaEntity();
        so2.setId(UUID.randomUUID());
        so2.setMasterOrderId(order.getId());
        so2.setVendorId(v2.getId());
        so2.setServiceId(s2.getId());
        so2.setSlotId(slot2.getId());
        so2.setQuantity(2);
        so2.setUnitPrice(new BigDecimal("250000"));
        so2.setSubtotalAmount(new BigDecimal("500000"));
        so2.setStatus(SubOrderStatus.CONFIRMED);
        subOrderRepository.saveAndFlush(so2);

        // Act: trigger payment listener
        transactionTemplate.execute(status -> {
            listener.handlePaymentSuccess(new PaymentSuccessEvent(order.getId(), customerId, null));
            return null;
        });

        // Duplicate call should NOT add records
        transactionTemplate.execute(status -> {
            listener.handlePaymentSuccess(new PaymentSuccessEvent(order.getId(), customerId, null));
            return null;
        });

        // Verify outbox
        var customerRecord = emailRepository.findByTypeAndBusinessKeyAndRecipientKeyAndMode(
                TransactionalEmailType.BOOKING_CONFIRMED,
                "order:" + order.getId(),
                "USER:" + customerId,
                EmailDeliveryMode.CAPTURE
        );
        assertTrue(customerRecord.isPresent(), "Customer confirmation email should be enqueued");

        var vendor1Record = emailRepository.findByTypeAndBusinessKeyAndRecipientKeyAndMode(
                TransactionalEmailType.VENDOR_BOOKING_CONFIRMED,
                "order:" + order.getId() + ":vendor:" + v1.getId(),
                "USER:" + vendorUserId1,
                EmailDeliveryMode.CAPTURE
        );
        assertTrue(vendor1Record.isPresent(), "Vendor 1 confirmation email should be enqueued");

        var vendor2Record = emailRepository.findByTypeAndBusinessKeyAndRecipientKeyAndMode(
                TransactionalEmailType.VENDOR_BOOKING_CONFIRMED,
                "order:" + order.getId() + ":vendor:" + v2.getId(),
                "USER:" + vendorUserId2,
                EmailDeliveryMode.CAPTURE
        );
        assertTrue(vendor2Record.isPresent(), "Vendor 2 confirmation email should be enqueued");

        // Execute delivery via worker
        worker.processIndividualEmail(customerRecord.get().getId(), EmailDeliveryMode.CAPTURE);

        ArgumentCaptor<RenderedEmail> emailCaptor = ArgumentCaptor.forClass(RenderedEmail.class);
        verify(captureSender).send(emailCaptor.capture());
        RenderedEmail captured = emailCaptor.getValue();
        assertEquals("customer@example.com", captured.to());
        assertTrue(captured.subject().contains("Xác nhận đặt vé thành công"));
        assertTrue(captured.textBody().contains("Lặn ngắm san hô Cù Lao Chàm"));
        assertTrue(captured.textBody().contains("Chèo thuyền Kayak sông Hàn"));
        assertTrue(captured.textBody().contains("08:30"));
        assertTrue(captured.textBody().contains("15:00"));

        var updatedCustomerRecord = emailRepository.findById(customerRecord.get().getId()).orElseThrow();
        assertEquals(EmailDeliveryStatus.ACCEPTED, updatedCustomerRecord.getStatus());
    }

    @Test
    @DisplayName("Partially confirmed order at payment lists all items with accurate status and financial breakdown")
    void partiallyConfirmedOrderAtPayment_listsAllItemsWithAccurateStatus() {
        UUID customerId = UUID.randomUUID();
        when(accountInternalApi.findUserById(customerId)).thenReturn(Optional.of(createMockUser(customerId, "customer@example.com")));

        VendorJpaEntity v = createVendor(UUID.randomUUID());
        ServiceJpaEntity s1 = createService(v.getId(), "Tour Bà Nà Hills");
        ServiceJpaEntity s2 = createService(v.getId(), "Tour Cù Lao Chàm");
        ServiceSlotJpaEntity slot1 = createSlot(s1.getId(), LocalDate.now().plusDays(3), LocalTime.of(8, 0));
        ServiceSlotJpaEntity slot2 = createSlot(s2.getId(), LocalDate.now().plusDays(3), LocalTime.of(9, 0));

        MasterOrderJpaEntity order = new MasterOrderJpaEntity();
        order.setId(UUID.randomUUID());
        order.setCustomerId(customerId);
        order.setPaymentStatus(PaymentOrderStatus.PAID);
        order.setStatus(MasterOrderStatus.PAID);
        order.setTotalAmount(new BigDecimal("1500000"));
        order.setDiscountAmount(new BigDecimal("100000"));
        masterOrderRepository.saveAndFlush(order);

        SubOrderJpaEntity confirmedItem = new SubOrderJpaEntity();
        confirmedItem.setId(UUID.randomUUID());
        confirmedItem.setMasterOrderId(order.getId());
        confirmedItem.setVendorId(v.getId());
        confirmedItem.setServiceId(s1.getId());
        confirmedItem.setSlotId(slot1.getId());
        confirmedItem.setQuantity(1);
        confirmedItem.setUnitPrice(new BigDecimal("900000"));
        confirmedItem.setSubtotalAmount(new BigDecimal("900000"));
        confirmedItem.setStatus(SubOrderStatus.CONFIRMED);
        subOrderRepository.saveAndFlush(confirmedItem);

        SubOrderJpaEntity cancelledItem = new SubOrderJpaEntity();
        cancelledItem.setId(UUID.randomUUID());
        cancelledItem.setMasterOrderId(order.getId());
        cancelledItem.setVendorId(v.getId());
        cancelledItem.setServiceId(s2.getId());
        cancelledItem.setSlotId(slot2.getId());
        cancelledItem.setQuantity(1);
        cancelledItem.setUnitPrice(new BigDecimal("700000"));
        cancelledItem.setSubtotalAmount(new BigDecimal("700000"));
        cancelledItem.setStatus(SubOrderStatus.CANCELLED);
        subOrderRepository.saveAndFlush(cancelledItem);

        // Pending refund for cancelledItem
        RefundJpaEntity refund = new RefundJpaEntity();
        refund.setId(UUID.randomUUID());
        refund.setSubOrderId(cancelledItem.getId());
        refund.setAmount(new BigDecimal("600000"));
        refund.setStatus(RefundStatus.PENDING);
        refundRepository.saveAndFlush(refund);

        transactionTemplate.execute(status -> {
            listener.handlePaymentSuccess(new PaymentSuccessEvent(order.getId(), customerId, null));
            return null;
        });

        var customerRecord = emailRepository.findByTypeAndBusinessKeyAndRecipientKeyAndMode(
                TransactionalEmailType.BOOKING_CONFIRMED,
                "order:" + order.getId(),
                "USER:" + customerId,
                EmailDeliveryMode.CAPTURE
        ).orElseThrow();

        worker.processIndividualEmail(customerRecord.getId(), EmailDeliveryMode.CAPTURE);

        ArgumentCaptor<RenderedEmail> emailCaptor = ArgumentCaptor.forClass(RenderedEmail.class);
        verify(captureSender).send(emailCaptor.capture());
        RenderedEmail captured = emailCaptor.getValue();

        assertTrue(captured.subject().contains("Đơn hàng được xác nhận một phần"), "Subject should indicate partial confirmation");
        assertTrue(captured.textBody().contains("Tour Bà Nà Hills - [Đã xác nhận]"));
        assertTrue(captured.textBody().contains("Tour Cù Lao Chàm - [Đã hủy · Đang xử lý hoàn tiền]"));
        assertTrue(captured.textBody().contains("Tổng số tiền thực tế đã thu: 1.500.000 VNĐ"));
        assertTrue(captured.textBody().contains("Số tiền đang chờ hoàn: 600.000 VNĐ"));

        var updated = emailRepository.findById(customerRecord.getId()).orElseThrow();
        assertEquals(EmailDeliveryStatus.ACCEPTED, updated.getStatus());
    }

    @Test
    @DisplayName("Full cancellation before worker sends transitions email to SKIPPED / SUPERSEDED_BY_CANCELLATION and sender is never called")
    void fullCancellationBeforeWorkerSends_marksSkippedAndSenderNotCalled() {
        UUID customerId = UUID.randomUUID();
        when(accountInternalApi.findUserById(customerId)).thenReturn(Optional.of(createMockUser(customerId, "customer@example.com")));

        VendorJpaEntity v = createVendor(UUID.randomUUID());
        ServiceJpaEntity s = createService(v.getId(), "Tour Lặn Biển");
        ServiceSlotJpaEntity slot = createSlot(s.getId(), LocalDate.now().plusDays(1), LocalTime.of(7, 30));

        MasterOrderJpaEntity order = new MasterOrderJpaEntity();
        order.setId(UUID.randomUUID());
        order.setCustomerId(customerId);
        order.setPaymentStatus(PaymentOrderStatus.PAID);
        order.setStatus(MasterOrderStatus.PAID);
        order.setTotalAmount(new BigDecimal("500000"));
        masterOrderRepository.saveAndFlush(order);

        SubOrderJpaEntity so = new SubOrderJpaEntity();
        so.setId(UUID.randomUUID());
        so.setMasterOrderId(order.getId());
        so.setVendorId(v.getId());
        so.setServiceId(s.getId());
        so.setSlotId(slot.getId());
        so.setQuantity(1);
        so.setUnitPrice(new BigDecimal("500000"));
        so.setSubtotalAmount(new BigDecimal("500000"));
        so.setStatus(SubOrderStatus.CONFIRMED);
        subOrderRepository.saveAndFlush(so);

        // Enqueue initially
        transactionTemplate.execute(status -> {
            listener.handlePaymentSuccess(new PaymentSuccessEvent(order.getId(), customerId, null));
            return null;
        });

        var customerRecord = emailRepository.findByTypeAndBusinessKeyAndRecipientKeyAndMode(
                TransactionalEmailType.BOOKING_CONFIRMED,
                "order:" + order.getId(),
                "USER:" + customerId,
                EmailDeliveryMode.CAPTURE
        ).orElseThrow();

        // Now cancel all sub-orders before worker runs
        so.setStatus(SubOrderStatus.CANCELLED);
        subOrderRepository.saveAndFlush(so);

        // Worker processes
        worker.processIndividualEmail(customerRecord.getId(), EmailDeliveryMode.CAPTURE);

        // Sender must NOT be called
        verify(captureSender, never()).send(any());

        var updated = emailRepository.findById(customerRecord.getId()).orElseThrow();
        assertEquals(EmailDeliveryStatus.SKIPPED, updated.getStatus());
        assertEquals("SUPERSEDED_BY_CANCELLATION", updated.getLastErrorCode());
    }

    @Test
    @DisplayName("Rescheduled slot before worker sends renders updated departure time in confirmation email")
    void rescheduledSlotBeforeWorkerSends_rendersUpdatedDepartureTime() {
        UUID customerId = UUID.randomUUID();
        when(accountInternalApi.findUserById(customerId)).thenReturn(Optional.of(createMockUser(customerId, "customer@example.com")));

        VendorJpaEntity v = createVendor(UUID.randomUUID());
        ServiceJpaEntity s = createService(v.getId(), "Tour Ca Nô Cao Tốc");
        ServiceSlotJpaEntity oldSlot = createSlot(s.getId(), LocalDate.now().plusDays(2), LocalTime.of(8, 0));
        ServiceSlotJpaEntity newSlot = createSlot(s.getId(), LocalDate.now().plusDays(4), LocalTime.of(14, 0));

        MasterOrderJpaEntity order = new MasterOrderJpaEntity();
        order.setId(UUID.randomUUID());
        order.setCustomerId(customerId);
        order.setPaymentStatus(PaymentOrderStatus.PAID);
        order.setStatus(MasterOrderStatus.PAID);
        order.setTotalAmount(new BigDecimal("800000"));
        masterOrderRepository.saveAndFlush(order);

        SubOrderJpaEntity so = new SubOrderJpaEntity();
        so.setId(UUID.randomUUID());
        so.setMasterOrderId(order.getId());
        so.setVendorId(v.getId());
        so.setServiceId(s.getId());
        so.setSlotId(oldSlot.getId());
        so.setQuantity(2);
        so.setUnitPrice(new BigDecimal("400000"));
        so.setSubtotalAmount(new BigDecimal("800000"));
        so.setStatus(SubOrderStatus.CONFIRMED);
        subOrderRepository.saveAndFlush(so);

        // Enqueue initially
        transactionTemplate.execute(status -> {
            listener.handlePaymentSuccess(new PaymentSuccessEvent(order.getId(), customerId, null));
            return null;
        });

        var customerRecord = emailRepository.findByTypeAndBusinessKeyAndRecipientKeyAndMode(
                TransactionalEmailType.BOOKING_CONFIRMED,
                "order:" + order.getId(),
                "USER:" + customerId,
                EmailDeliveryMode.CAPTURE
        ).orElseThrow();

        // Reschedule to newSlot before worker sends
        so.setSlotId(newSlot.getId());
        subOrderRepository.saveAndFlush(so);

        // Worker processes
        worker.processIndividualEmail(customerRecord.getId(), EmailDeliveryMode.CAPTURE);

        ArgumentCaptor<RenderedEmail> emailCaptor = ArgumentCaptor.forClass(RenderedEmail.class);
        verify(captureSender).send(emailCaptor.capture());
        RenderedEmail captured = emailCaptor.getValue();

        assertTrue(captured.textBody().contains("14:00"), "Email should render new slot departure time 14:00");
        assertFalse(captured.textBody().contains("08:00"), "Old slot time 08:00 should not be rendered");
    }
}
