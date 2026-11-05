package com.danasea.backend.modules.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.account.infrastructure.persistence.entities.UserJpaEntity;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.communication.application.email.EmailRequest;
import com.danasea.backend.modules.communication.application.email.EmailSendResult;
import com.danasea.backend.modules.communication.application.email.RenderedEmail;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailPort;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailSender;
import com.danasea.backend.modules.communication.domain.models.EmailDeliveryMode;
import com.danasea.backend.modules.communication.domain.models.EmailDeliveryStatus;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;
import com.danasea.backend.modules.communication.infrastructure.email.TransactionalEmailProperties;
import com.danasea.backend.modules.communication.infrastructure.email.TransactionalEmailWorker;
import com.danasea.backend.modules.communication.infrastructure.email.entities.TransactionalEmailDeliveryJpaEntity;
import com.danasea.backend.modules.communication.infrastructure.email.renderers.TripReminderEmailRenderer;
import com.danasea.backend.modules.communication.infrastructure.email.repositories.JpaTransactionalEmailDeliveryRepository;
import com.danasea.backend.modules.communication.infrastructure.jobs.TripReminderJob;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none"})
@ActiveProfiles("test")
class TripReminderEmailIntegrationTest extends BaseSecurityIntegrationTest {

    @Autowired private TripReminderJob tripReminderJob;
    @Autowired private TripReminderEmailRenderer tripReminderEmailRenderer;
    @Autowired private TransactionalEmailWorker worker;
    @Autowired private TransactionalEmailPort emailPort;
    @Autowired private JpaTransactionalEmailDeliveryRepository emailRepository;
    @Autowired private JpaMasterOrderRepository masterOrderRepository;
    @Autowired private JpaSubOrderRepository subOrderRepository;
    @Autowired private JpaDisputeRepository disputeRepository;
    @Autowired private JpaVendorRepository vendorRepository;
    @Autowired private JpaUserRepository userRepository;
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
        registry.add("app.transactional-email.mode", () -> "capture");
        registry.add("app.transactional-email.worker.enabled", () -> "true");
        registry.add("app.transactional-email.enabled-families", () -> "BOOKING,TRIP,DISRUPTION,REFUND");
        registry.add("app.transactional-email.support-phone", () -> "1900-6868");
    }

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        reset(captureSender);
        when(captureSender.send(any())).thenReturn(EmailSendResult.accepted());

        transactionTemplate.executeWithoutResult(status -> {
            emailRepository.deleteAll();
            disputeRepository.deleteAll();
            subOrderRepository.deleteAll();
            masterOrderRepository.deleteAll();
            slotRepository.deleteAll();
            serviceRepository.deleteAll();
            vendorRepository.deleteAll();
            userRepository.deleteAll();
        });
    }

    @Test
    @DisplayName("Quét trễ khi còn 10 giờ: gửi bù 1 lần với giờ khởi hành thực tế và đầy đủ thông tin điểm hẹn, hotline")
    void lateBatchScanRemaining10Hours_sendsReminderWithActualDeparture() {
        // Given: Giờ cố định hiện tại: 2026-11-06T00:00:00+07:00
        Instant now = Instant.parse("2026-11-05T17:00:00Z"); // 00:00 +07:00
        Clock fixedClock = Clock.fixed(now, Booking.VIETNAM_ZONE);
        tripReminderJob.setClock(fixedClock);
        tripReminderEmailRenderer.setClock(fixedClock);

        // Ca khởi hành vào lúc 10:00 sáng cùng ngày (sau đúng 10 tiếng)
        LocalDate tripDate = LocalDate.of(2026, 11, 6);
        LocalTime tripTime = LocalTime.of(10, 0);

        UUID customerId = UUID.randomUUID();
        setupAccount(customerId, "customer@test.com", "Nguyễn Văn Khách", "vi", true, false);

        UUID vendorOwnerId = UUID.randomUUID();
        setupUser(vendorOwnerId, "vendor@danasea.vn", "0905123456");
        UUID vendorId = createVendor(vendorOwnerId, "Dana Marine Tour");

        UUID serviceId = createService(vendorId, "Lặn ngắm san hô Bán Đảo Sơn Trà", "Bến tàu du lịch Sơn Trà", "Đường Hoàng Sa, Sơn Trà, Đà Nẵng",
                new BigDecimal("16.1001"), new BigDecimal("108.2501"));
        UUID slotId = createSlot(serviceId, tripDate, tripTime, SlotStatus.OPEN);

        UUID orderId = createMasterOrder(customerId, MasterOrderStatus.PAID, PaymentOrderStatus.PAID, new BigDecimal("1000000"));
        createSubOrder(orderId, slotId, vendorId, SubOrderStatus.CONFIRMED, new BigDecimal("1000000"));

        // When: Chạy batch scan
        transactionTemplate.executeWithoutResult(s -> tripReminderJob.scanAndSendReminders());

        // Then: Phải enqueue 1 bản ghi outbox
        List<TransactionalEmailDeliveryJpaEntity> deliveries = emailRepository.findAll();
        assertEquals(1, deliveries.size());
        TransactionalEmailDeliveryJpaEntity delivery = deliveries.getFirst();
        assertEquals(TransactionalEmailType.TRIP_REMINDER_24H, delivery.getType());
        assertEquals("USER:" + customerId, delivery.getRecipientKey());
        assertTrue(delivery.getBusinessKey().startsWith("slot:" + slotId + ":start:"));
        assertEquals(EmailDeliveryStatus.PENDING, delivery.getStatus());

        // When: Worker xử lý gửi email
        processPendingEmails();

        // Then: captureSender phải được gọi 1 lần
        ArgumentCaptor<RenderedEmail> captor = ArgumentCaptor.forClass(RenderedEmail.class);
        verify(captureSender, times(1)).send(captor.capture());

        RenderedEmail sent = captor.getValue();
        assertEquals("customer@test.com", sent.to());
        assertTrue(sent.subject().contains("Lặn ngắm san hô Bán Đảo Sơn Trà"));
        assertTrue(sent.subject().contains("10:00 06/11/2026"));

        String body = sent.textBody();
        assertTrue(body.contains("10:00 06/11/2026"));
        assertTrue(body.contains("Bến tàu du lịch Sơn Trà"));
        assertTrue(body.contains("16.1001, 108.2501"));
        assertTrue(body.contains("0905123456")); // Ưu tiên số hotline đối tác
        assertTrue(body.contains("HƯỚNG DẪN CHUẨN BỊ & LƯU Ý AN TOÀN"));
        assertTrue(body.contains("/profile/orders?orderId=" + orderId));

        // Bản ghi outbox chuyển sang ACCEPTED
        TransactionalEmailDeliveryJpaEntity updated = emailRepository.findById(delivery.getId()).orElseThrow();
        assertEquals(EmailDeliveryStatus.ACCEPTED, updated.getStatus());
    }

    @Test
    @DisplayName("Đúng biên 2 giờ hoặc ít hơn: bỏ qua cả lúc enqueue và ngay trước lúc gửi")
    void exactBoundary2HoursOrLess_skipsReminderBothAtEnqueueAndBeforeSend() {
        // Ca 1: Lúc enqueue, thời gian còn lại đúng 2 tiếng hoặc ít hơn -> không enqueue
        Instant now = Instant.parse("2026-11-05T17:00:00Z"); // 00:00 +07:00
        Clock fixedClock = Clock.fixed(now, Booking.VIETNAM_ZONE);
        tripReminderJob.setClock(fixedClock);
        tripReminderEmailRenderer.setClock(fixedClock);

        LocalDate tripDate = LocalDate.of(2026, 11, 6);
        LocalTime tripTime = LocalTime.of(2, 0); // Khởi hành lúc 02:00 -> cách đúng 2 tiếng

        UUID customerId = UUID.randomUUID();
        setupAccount(customerId, "customer@test.com", "Nguyễn Văn Khách", "vi", true, false);

        UUID vendorOwnerId = UUID.randomUUID();
        setupUser(vendorOwnerId, "vendor@danasea.vn", "0905123456");
        UUID vendorId = createVendor(vendorOwnerId, "Dana Marine Tour");

        UUID serviceId = createService(vendorId, "Ngắm bình minh biển", "Bãi biển Mỹ Khê", "Võ Nguyên Giáp", null, null);
        UUID slotId = createSlot(serviceId, tripDate, tripTime, SlotStatus.OPEN);

        UUID orderId = createMasterOrder(customerId, MasterOrderStatus.PAID, PaymentOrderStatus.PAID, new BigDecimal("500000"));
        createSubOrder(orderId, slotId, vendorId, SubOrderStatus.CONFIRMED, new BigDecimal("500000"));

        // When: scan ca khởi hành chỉ còn đúng 2 tiếng
        transactionTemplate.executeWithoutResult(s -> tripReminderJob.scanAndSendReminders());

        // Then: Không có email nào được enqueue!
        assertEquals(0, emailRepository.count());

        // Ca 2: Đã enqueue trước đó khi còn nhiều giờ, nhưng khi worker xử lý thì thời gian chỉ còn <= 2 giờ
        Instant scheduledInstant = now.plus(Duration.ofHours(5)); // khởi hành lúc 05:00 sáng
        UUID slotId2 = createSlot(serviceId, tripDate, LocalTime.of(5, 0), SlotStatus.OPEN);
        createSubOrder(orderId, slotId2, vendorId, SubOrderStatus.CONFIRMED, new BigDecimal("500000"));

        // Enqueue email hợp lệ cho slotId2
        String businessKey = "slot:" + slotId2 + ":start:" + scheduledInstant + ":24h";
        transactionTemplate.executeWithoutResult(s -> {
            emailPort.enqueue(EmailRequest.builder()
                    .type(TransactionalEmailType.TRIP_REMINDER_24H)
                    .businessKey(businessKey)
                    .recipientKey("USER:" + customerId)
                    .occurredAt(OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                    .dueAt(OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                    .expiresAt(OffsetDateTime.ofInstant(scheduledInstant.minus(Duration.ofHours(2)), ZoneOffset.UTC))
                    .payloadVersion(1)
                    .payload(Map.of("slotId", slotId2.toString()))
                    .build());
        });

        assertEquals(1, emailRepository.count());

        // Tua clock tới lúc 03:30 (chỉ còn 1 tiếng 30 phút trước giờ đi lúc 05:00)
        Instant lateNow = scheduledInstant.minus(Duration.ofMinutes(90));
        tripReminderEmailRenderer.setClock(Clock.fixed(lateNow, Booking.VIETNAM_ZONE));

        // When: worker chạy
        processPendingEmails();

        // Then: sender KHÔNG được gọi, bản ghi outbox chuyển sang SKIPPED với TRIP_STARTING_TOO_SOON
        verify(captureSender, never()).send(any());
        TransactionalEmailDeliveryJpaEntity delivery = emailRepository.findAll().getFirst();
        assertEquals(EmailDeliveryStatus.SKIPPED, delivery.getStatus());
        assertEquals("TRIP_STARTING_TOO_SOON", delivery.getLastErrorCode());
    }

    @Test
    @DisplayName("Nhiều đơn con trong cùng 1 slot của 1 khách: chỉ tạo duy nhất 1 bản ghi outbox (idempotency/gom theo slot + start)")
    void multipleSubOrdersSameSlotSameCustomer_createsOnlyOneOutboxEmail() {
        Instant now = Instant.parse("2026-11-05T17:00:00Z");
        Clock fixedClock = Clock.fixed(now, Booking.VIETNAM_ZONE);
        tripReminderJob.setClock(fixedClock);
        tripReminderEmailRenderer.setClock(fixedClock);

        LocalDate tripDate = LocalDate.of(2026, 11, 6);
        LocalTime tripTime = LocalTime.of(15, 0); // Sau 15 tiếng

        UUID customerId = UUID.randomUUID();
        setupAccount(customerId, "customer@test.com", "Lê Văn Khách", "vi", true, false);

        UUID vendorOwnerId = UUID.randomUUID();
        setupUser(vendorOwnerId, "vendor@danasea.vn", "0905123456");
        UUID vendorId = createVendor(vendorOwnerId, "Dana Marine Tour");

        UUID serviceId = createService(vendorId, "Tour Cano Cù Lao Chàm", "Cảng Cửa Đại", "Hội An", null, null);
        UUID slotId = createSlot(serviceId, tripDate, tripTime, SlotStatus.OPEN);

        // Khách có 2 đơn hàng khác nhau đặt cùng slot này
        UUID order1 = createMasterOrder(customerId, MasterOrderStatus.PAID, PaymentOrderStatus.PAID, new BigDecimal("1000000"));
        createSubOrder(order1, slotId, vendorId, SubOrderStatus.CONFIRMED, new BigDecimal("1000000"));

        UUID order2 = createMasterOrder(customerId, MasterOrderStatus.PAID, PaymentOrderStatus.PAID, new BigDecimal("2000000"));
        createSubOrder(order2, slotId, vendorId, SubOrderStatus.CONFIRMED, new BigDecimal("2000000"));

        // When: chạy quét nhắc nhở 2 lần liên tiếp
        transactionTemplate.executeWithoutResult(s -> tripReminderJob.scanAndSendReminders());
        transactionTemplate.executeWithoutResult(s -> tripReminderJob.scanAndSendReminders());

        // Then: Chỉ có duy nhất 1 email outbox được tạo
        List<TransactionalEmailDeliveryJpaEntity> deliveries = emailRepository.findAll();
        assertEquals(1, deliveries.size());

        // Worker chỉ gửi đúng 1 lần
        processPendingEmails();
        verify(captureSender, times(1)).send(any());
    }

    @Test
    @DisplayName("Hai tour khác nhau trùng giờ khởi hành: gửi 2 email riêng biệt với đúng điểm hẹn của từng tour")
    void twoSlotsSameTimeSameCustomer_createsTwoSeparateEmails() {
        Instant now = Instant.parse("2026-11-05T17:00:00Z");
        Clock fixedClock = Clock.fixed(now, Booking.VIETNAM_ZONE);
        tripReminderJob.setClock(fixedClock);
        tripReminderEmailRenderer.setClock(fixedClock);

        LocalDate tripDate = LocalDate.of(2026, 11, 6);
        LocalTime tripTime = LocalTime.of(8, 0); // Khởi hành cùng 08:00

        UUID customerId = UUID.randomUUID();
        setupAccount(customerId, "customer@test.com", "Trần Văn A", "vi", true, false);

        UUID vendorOwnerId = UUID.randomUUID();
        setupUser(vendorOwnerId, "vendor@danasea.vn", "0905123456");
        UUID vendorId = createVendor(vendorOwnerId, "Dana Marine Tour");

        // Tour 1: Lặn biển
        UUID service1 = createService(vendorId, "Tour Lặn Biển", "Bến Sơn Trà", "Sơn Trà", null, null);
        UUID slot1 = createSlot(service1, tripDate, tripTime, SlotStatus.OPEN);

        // Tour 2: Thuyền buồm
        UUID service2 = createService(vendorId, "Tour Thuyền Buồm", "Bến Sông Hàn", "Sông Hàn", null, null);
        UUID slot2 = createSlot(service2, tripDate, tripTime, SlotStatus.OPEN);

        UUID order1 = createMasterOrder(customerId, MasterOrderStatus.PAID, PaymentOrderStatus.PAID, new BigDecimal("1000000"));
        createSubOrder(order1, slot1, vendorId, SubOrderStatus.CONFIRMED, new BigDecimal("1000000"));

        UUID order2 = createMasterOrder(customerId, MasterOrderStatus.PAID, PaymentOrderStatus.PAID, new BigDecimal("1500000"));
        createSubOrder(order2, slot2, vendorId, SubOrderStatus.CONFIRMED, new BigDecimal("1500000"));

        // When: chạy quét
        transactionTemplate.executeWithoutResult(s -> tripReminderJob.scanAndSendReminders());

        // Then: Phải tạo 2 bản ghi outbox riêng biệt
        List<TransactionalEmailDeliveryJpaEntity> deliveries = emailRepository.findAll();
        assertEquals(2, deliveries.size());

        // Worker gửi 2 thư
        processPendingEmails();
        ArgumentCaptor<RenderedEmail> captor = ArgumentCaptor.forClass(RenderedEmail.class);
        verify(captureSender, times(2)).send(captor.capture());

        List<RenderedEmail> sentEmails = captor.getAllValues();
        boolean hasTour1 = sentEmails.stream().anyMatch(e -> e.textBody().contains("Tour Lặn Biển") && e.textBody().contains("Bến Sơn Trà"));
        boolean hasTour2 = sentEmails.stream().anyMatch(e -> e.textBody().contains("Tour Thuyền Buồm") && e.textBody().contains("Bến Sông Hàn"));
        assertTrue(hasTour1, "Phải có email cho Tour 1 với điểm hẹn Bến Sơn Trà");
        assertTrue(hasTour2, "Phải có email cho Tour 2 với điểm hẹn Bến Sông Hàn");
    }

    @Test
    @DisplayName("Ca đã đổi, hủy hoặc đã check-in trước khi gửi: worker kiểm tra và đánh dấu SKIPPED, không gọi sender")
    void rescheduledOrCancelledOrCheckedInBeforeWorkerSends_marksSkipped() {
        Instant now = Instant.parse("2026-11-05T17:00:00Z");
        Clock fixedClock = Clock.fixed(now, Booking.VIETNAM_ZONE);
        tripReminderJob.setClock(fixedClock);
        tripReminderEmailRenderer.setClock(fixedClock);

        LocalDate tripDate = LocalDate.of(2026, 11, 6);
        LocalTime tripTime = LocalTime.of(14, 0);

        UUID customerId = UUID.randomUUID();
        setupAccount(customerId, "customer@test.com", "Nguyễn Văn B", "vi", true, false);

        UUID vendorOwnerId = UUID.randomUUID();
        setupUser(vendorOwnerId, "vendor@danasea.vn", "0905123456");
        UUID vendorId = createVendor(vendorOwnerId, "Dana Marine Tour");

        UUID serviceId = createService(vendorId, "Tour Câu Mực Đêm", "Bến Sơn Trà", "Sơn Trà", null, null);
        UUID slotId = createSlot(serviceId, tripDate, tripTime, SlotStatus.OPEN);

        UUID orderId = createMasterOrder(customerId, MasterOrderStatus.PAID, PaymentOrderStatus.PAID, new BigDecimal("800000"));
        UUID subOrderId = createSubOrder(orderId, slotId, vendorId, SubOrderStatus.CONFIRMED, new BigDecimal("800000"));

        // Quét để enqueue email
        transactionTemplate.executeWithoutResult(s -> tripReminderJob.scanAndSendReminders());
        assertEquals(1, emailRepository.count());

        // Khách đã check-in trước khi worker chạy
        transactionTemplate.executeWithoutResult(s -> {
            SubOrderJpaEntity subOrder = subOrderRepository.findById(subOrderId).orElseThrow();
            subOrder.setStatus(SubOrderStatus.CHECKED_IN);
            subOrderRepository.saveAndFlush(subOrder);
        });

        // Worker chạy
        processPendingEmails();

        // Assert: Không gửi email, đánh dấu SKIPPED với ALREADY_CHECKED_IN
        verify(captureSender, never()).send(any());
        TransactionalEmailDeliveryJpaEntity delivery = emailRepository.findAll().getFirst();
        assertEquals(EmailDeliveryStatus.SKIPPED, delivery.getStatus());
        assertEquals("ALREADY_CHECKED_IN", delivery.getLastErrorCode());
    }

    @Test
    @DisplayName("Thư xác nhận cho đúng ca đã được SMTP chấp nhận trong 2 giờ vừa qua: bỏ qua lời nhắc trùng")
    void recentlyAcceptedBookingConfirmedWithin2Hours_skipsReminder() {
        Instant now = Instant.parse("2026-11-05T17:00:00Z");
        Clock fixedClock = Clock.fixed(now, Booking.VIETNAM_ZONE);
        tripReminderJob.setClock(fixedClock);
        tripReminderEmailRenderer.setClock(fixedClock);

        LocalDate tripDate = LocalDate.of(2026, 11, 6);
        LocalTime tripTime = LocalTime.of(12, 0); // Sau 12 tiếng

        UUID customerId = UUID.randomUUID();
        setupAccount(customerId, "customer@test.com", "Nguyễn Văn C", "vi", true, false);

        UUID vendorOwnerId = UUID.randomUUID();
        setupUser(vendorOwnerId, "vendor@danasea.vn", "0905123456");
        UUID vendorId = createVendor(vendorOwnerId, "Dana Marine Tour");

        UUID serviceId = createService(vendorId, "Tour Jet Ski", "Bãi Biển Mỹ Khê", "Võ Nguyên Giáp", null, null);
        UUID slotId = createSlot(serviceId, tripDate, tripTime, SlotStatus.OPEN);

        UUID orderId = createMasterOrder(customerId, MasterOrderStatus.PAID, PaymentOrderStatus.PAID, new BigDecimal("1200000"));
        createSubOrder(orderId, slotId, vendorId, SubOrderStatus.CONFIRMED, new BigDecimal("1200000"));

        // Tạo 1 email BOOKING_CONFIRMED cho khách này đã được ACCEPTED lúc 30 phút trước
        transactionTemplate.executeWithoutResult(s -> {
            var entity = TransactionalEmailDeliveryJpaEntity.builder()
                    .type(TransactionalEmailType.BOOKING_CONFIRMED)
                    .businessKey("order:" + orderId)
                    .recipientKey("USER:" + customerId)
                    .family(TransactionalEmailType.BOOKING_CONFIRMED.getFamily())
                    .mode(EmailDeliveryMode.CAPTURE)
                    .status(EmailDeliveryStatus.ACCEPTED)
                    .acceptedAt(OffsetDateTime.ofInstant(now.minus(Duration.ofMinutes(30)), ZoneOffset.UTC))
                    .occurredAt(OffsetDateTime.ofInstant(now.minus(Duration.ofMinutes(30)), ZoneOffset.UTC))
                    .dueAt(OffsetDateTime.ofInstant(now.minus(Duration.ofMinutes(30)), ZoneOffset.UTC))
                    .attemptCount(1)
                    .payload("{}")
                    .build();
            emailRepository.saveAndFlush(entity);
        });

        // When: chạy quét
        transactionTemplate.executeWithoutResult(s -> tripReminderJob.scanAndSendReminders());

        // Then: Không tạo thêm email TRIP_REMINDER_24H nào vì đã gửi thư xác nhận kèm đủ hướng dẫn trong 2h qua
        assertEquals(1, emailRepository.count());
        assertEquals(TransactionalEmailType.BOOKING_CONFIRMED, emailRepository.findAll().getFirst().getType());
    }

    @Test
    @DisplayName("Xác minh fallback số điện thoại liên hệ: ưu tiên đối tác -> hotline Danasea -> thiếu cả hai thì bỏ dòng số điện thoại")
    void phoneFallbackVerification() {
        Instant now = Instant.parse("2026-11-05T17:00:00Z");
        Clock fixedClock = Clock.fixed(now, Booking.VIETNAM_ZONE);
        tripReminderJob.setClock(fixedClock);
        tripReminderEmailRenderer.setClock(fixedClock);

        LocalDate tripDate = LocalDate.of(2026, 11, 6);
        LocalTime tripTime = LocalTime.of(16, 0);

        UUID customerId = UUID.randomUUID();
        setupAccount(customerId, "customer@test.com", "Nguyễn Văn D", "vi", true, false);

        // Trường hợp 1: Đối tác không có phone nhưng hệ thống có hotline Danasea (1900-6868)
        UUID vendorOwnerId = UUID.randomUUID();
        setupUser(vendorOwnerId, "vendor_no_phone@danasea.vn", null); // Không có SĐT
        UUID vendorId = createVendor(vendorOwnerId, "Vendor No Phone");

        UUID serviceId = createService(vendorId, "Tour Dù Bay", "Bãi Non Nước", "Ngũ Hành Sơn", null, null);
        UUID slotId = createSlot(serviceId, tripDate, tripTime, SlotStatus.OPEN);

        UUID orderId = createMasterOrder(customerId, MasterOrderStatus.PAID, PaymentOrderStatus.PAID, new BigDecimal("900000"));
        createSubOrder(orderId, slotId, vendorId, SubOrderStatus.CONFIRMED, new BigDecimal("900000"));

        transactionTemplate.executeWithoutResult(s -> tripReminderJob.scanAndSendReminders());
        processPendingEmails();

        ArgumentCaptor<RenderedEmail> captor = ArgumentCaptor.forClass(RenderedEmail.class);
        verify(captureSender).send(captor.capture());
        assertTrue(captor.getValue().textBody().contains("1900-6868"), "Phải fallback về hotline Danasea");

        // Trường hợp 2: Thiếu cả đối tác lẫn hotline Danasea (supportPhone = null)
        reset(captureSender);
        when(captureSender.send(any())).thenReturn(EmailSendResult.accepted());
        properties.setSupportPhone(null); // Xóa hotline Danasea
        transactionTemplate.executeWithoutResult(s -> {
            emailRepository.deleteAll();
            disputeRepository.deleteAll();
            subOrderRepository.deleteAll();
            slotRepository.deleteAll();
        });

        // Tạo slot mới 17:00 để quét lại
        UUID slotId2 = createSlot(serviceId, tripDate, LocalTime.of(17, 0), SlotStatus.OPEN);
        createSubOrder(orderId, slotId2, vendorId, SubOrderStatus.CONFIRMED, new BigDecimal("900000"));

        transactionTemplate.executeWithoutResult(s -> tripReminderJob.scanAndSendReminders());
        processPendingEmails();

        ArgumentCaptor<RenderedEmail> captor2 = ArgumentCaptor.forClass(RenderedEmail.class);
        verify(captureSender).send(captor2.capture());
        String body = captor2.getValue().textBody();

        assertFalse(body.contains("Hotline hỗ trợ"), "Không được hiển thị dòng Hotline khi thiếu số liên hệ");
        assertTrue(body.contains("chi tiết đơn hàng"), "Phải có dòng hướng dẫn khách xem chi tiết đơn hàng");
    }

    private void processPendingEmails() {
        List<TransactionalEmailDeliveryJpaEntity> list = emailRepository.findAll();
        for (var entity : list) {
            if (entity.getStatus() == EmailDeliveryStatus.PENDING) {
                worker.processIndividualEmail(entity.getId(), EmailDeliveryMode.CAPTURE);
            }
        }
    }

    // --- Helper methods ---

    private void setupAccount(UUID id, String email, String fullName, String locale, boolean isVerified, boolean isLocked) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(Role.CUSTOMER);
        user.setIsEmailVerified(isVerified);
        user.setIsLocked(isLocked);
        user.setLocale(locale);
        when(accountInternalApi.findUserById(id)).thenReturn(Optional.of(user));
    }

    private void setupUser(UUID id, String email, String phone) {
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(id);
        entity.markNew();
        entity.setEmail(email);
        entity.setPhone(phone);
        entity.setFullName("User " + email);
        entity.setPasswordHash("hashed");
        entity.setRole(Role.VENDOR);
        entity.setIsEmailVerified(true);
        entity.setIsLocked(false);
        entity.setLocale("vi");
        userRepository.saveAndFlush(entity);
    }

    private UUID createVendor(UUID userId, String businessName) {
        VendorJpaEntity vendor = new VendorJpaEntity();
        vendor.setId(UUID.randomUUID());
        vendor.markNew();
        vendor.setUserId(userId);
        vendor.setBusinessName(businessName);
        return vendorRepository.saveAndFlush(vendor).getId();
    }

    private UUID createService(UUID vendorId, String name, String locationName, String address, BigDecimal lat, BigDecimal lng) {
        ServiceJpaEntity service = new ServiceJpaEntity();
        service.setId(UUID.randomUUID());
        service.markNew();
        service.setVendorId(vendorId);
        service.setName(name);
        service.setNameEn(name + " (EN)");
        service.setLocationName(locationName);
        service.setAddress(address);
        service.setLatitude(lat);
        service.setLongitude(lng);
        service.setPrice(new BigDecimal("1000000"));
        return serviceRepository.saveAndFlush(service).getId();
    }

    private UUID createSlot(UUID serviceId, LocalDate date, LocalTime startTime, SlotStatus status) {
        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(UUID.randomUUID());
        slot.markNew();
        slot.setServiceId(serviceId);
        slot.setDate(date);
        slot.setStartTime(startTime);
        slot.setEndTime(startTime.plusHours(2));
        slot.setCapacity(20);
        slot.setBookedCount(1);
        slot.setStatus(status);
        return slotRepository.saveAndFlush(slot).getId();
    }

    private UUID createMasterOrder(UUID customerId, MasterOrderStatus status, PaymentOrderStatus paymentStatus, BigDecimal totalPaid) {
        MasterOrderJpaEntity order = new MasterOrderJpaEntity();
        order.setId(UUID.randomUUID());
        order.markNew();
        order.setCustomerId(customerId);
        order.setStatus(status);
        order.setPaymentStatus(paymentStatus);
        order.setTotalAmount(totalPaid);
        return masterOrderRepository.saveAndFlush(order).getId();
    }

    private UUID createSubOrder(UUID masterOrderId, UUID slotId, UUID vendorId, SubOrderStatus status, BigDecimal price) {
        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(UUID.randomUUID());
        subOrder.markNew();
        subOrder.setMasterOrderId(masterOrderId);
        subOrder.setSlotId(slotId);
        subOrder.setVendorId(vendorId);
        subOrder.setStatus(status);
        subOrder.setUnitPrice(price);
        subOrder.setSubtotalAmount(price);
        subOrder.setQuantity(1);
        return subOrderRepository.saveAndFlush(subOrder).getId();
    }
}
