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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
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
import com.danasea.backend.modules.booking.application.usecases.CancelBookingUseCase;
import com.danasea.backend.modules.booking.domain.ports.BookingRepositoryPort;
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
import com.danasea.backend.modules.communication.infrastructure.email.repositories.JpaTransactionalEmailDeliveryRepository;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.order.application.services.DisruptionDecisionService;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderDisruptionJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderDisruptionRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.CategoryJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaCategoryRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import com.danasea.backend.modules.weather.infrastructure.jobs.SlotWeatherMonitoringJob;
import com.danasea.backend.modules.weather.infrastructure.persistence.repositories.JpaSafetyRuleEvaluationRepository;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none"})
@ActiveProfiles("test")
public class TripChangeEmailIntegrationTest extends BaseSecurityIntegrationTest {

    @Autowired private TransactionalEmailPort transactionalEmailPort;
    @Autowired private TransactionalEmailWorker worker;
    @Autowired private TransactionalEmailProperties properties;
    @Autowired private JpaTransactionalEmailDeliveryRepository emailRepository;
    @Autowired private JpaUserRepository userRepository;
    @Autowired private JpaVendorRepository vendorRepository;
    @Autowired private JpaServiceRepository serviceRepository;
    @Autowired private JpaServiceSlotRepository slotRepository;
    @Autowired private JpaCategoryRepository categoryRepository;
    @Autowired private JpaMasterOrderRepository masterOrderRepository;
    @Autowired private JpaSubOrderRepository subOrderRepository;
    @Autowired private JpaRefundRepository refundRepository;
    @Autowired private JpaSubOrderDisruptionRepository disruptionRepository;
    @Autowired private JpaSafetyRuleEvaluationRepository evaluationRepository;
    @Autowired private JpaDisputeRepository disputeRepository;
    @Autowired private DisruptionDecisionService disruptionDecisionService;
    @Autowired private SlotWeatherMonitoringJob slotWeatherMonitoringJob;
    @Autowired private CancelBookingUseCase cancelBookingUseCase;
    @Autowired private BookingRepositoryPort bookingRepositoryPort;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean(name = "captureTransactionalEmailSender")
    private TransactionalEmailSender captureSender;

    @MockitoBean private AccountInternalApi accountInternalApi;

    private UUID customerId;
    private UUID vendorId;
    private UUID vendorOwnerUserId;
    private UUID serviceId;
    private UUID categoryId;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.transactional-email.mode", () -> "capture");
        registry.add("app.transactional-email.worker.enabled", () -> "false");
        registry.add("app.transactional-email.enabled-families", () -> "DISRUPTION,OPS,BOOKING");
    }

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            emailRepository.deleteAll();
            disputeRepository.deleteAll();
            disruptionRepository.deleteAll();
            refundRepository.deleteAll();
            subOrderRepository.deleteAll();
            masterOrderRepository.deleteAll();
            evaluationRepository.deleteAll();
            slotRepository.deleteAll();
            serviceRepository.deleteAll();
            categoryRepository.deleteAll();
            vendorRepository.deleteAll();
            userRepository.deleteAll();
        });

        reset(captureSender);
        when(captureSender.send(any())).thenReturn(EmailSendResult.accepted());

        customerId = UUID.randomUUID();
        vendorOwnerUserId = UUID.randomUUID();
        vendorId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        // 1. Setup Customer User
        UserJpaEntity customerUser = new UserJpaEntity();
        customerUser.setId(customerId);
        customerUser.setEmail("customer@example.com");
        customerUser.setFullName("Nguyen Van Khach");
        customerUser.setRole(Role.CUSTOMER);
        customerUser.setIsEmailVerified(true);
        customerUser.setIsLocked(false);
        customerUser.setLocale("vi");
        customerUser.markNew();
        userRepository.saveAndFlush(customerUser);

        // 2. Setup Vendor Owner User
        UserJpaEntity vendorUser = new UserJpaEntity();
        vendorUser.setId(vendorOwnerUserId);
        vendorUser.setEmail("vendor.owner@example.com");
        vendorUser.setFullName("Tran Van Doi Tac");
        vendorUser.setRole(Role.VENDOR);
        vendorUser.setIsEmailVerified(true);
        vendorUser.setIsLocked(false);
        vendorUser.setLocale("vi");
        vendorUser.markNew();
        userRepository.saveAndFlush(vendorUser);

        // 3. Setup Vendor Entity
        VendorJpaEntity vendor = new VendorJpaEntity();
        vendor.setId(vendorId);
        vendor.setUserId(vendorOwnerUserId);
        vendor.setBusinessName("DanaSea Travel");
        vendor.markNew();
        vendorRepository.saveAndFlush(vendor);

        // 4. Setup Category
        CategoryJpaEntity category = new CategoryJpaEntity();
        category.setId(categoryId);
        category.setName("Tour Biển");
        category.setSlug("tour-bien");
        category.setIsActive(true);
        category.setRequiresSafetyCert(false);
        category.markNew();
        categoryRepository.saveAndFlush(category);

        // 5. Setup Service
        ServiceJpaEntity service = new ServiceJpaEntity();
        service.setId(serviceId);
        service.setVendorId(vendorId);
        service.setCategoryId(categoryId);
        service.setName("Lặn biển Sơn Trà");
        service.setNameEn("Son Tra Snorkeling");
        service.setAddress("Bến thuyền bán đảo Sơn Trà, Đà Nẵng");
        service.setLocationName("Sơn Trà");
        service.setLatitude(new BigDecimal("16.1001"));
        service.setLongitude(new BigDecimal("108.2501"));
        service.markNew();
        serviceRepository.saveAndFlush(service);

        // Mock AccountInternalApi
        User domainCustomer = new User();
        domainCustomer.setId(customerId);
        domainCustomer.setEmail("customer@example.com");
        domainCustomer.setLocale("vi");
        domainCustomer.setIsEmailVerified(true);
        domainCustomer.setIsLocked(false);

        User domainVendor = new User();
        domainVendor.setId(vendorOwnerUserId);
        domainVendor.setEmail("vendor.owner@example.com");
        domainVendor.setLocale("vi");
        domainVendor.setIsEmailVerified(true);
        domainVendor.setIsLocked(false);

        when(accountInternalApi.findUserById(customerId)).thenReturn(Optional.of(domainCustomer));
        when(accountInternalApi.findUserById(vendorOwnerUserId)).thenReturn(Optional.of(domainVendor));
    }

    private ServiceSlotJpaEntity createSlot(LocalDate date, LocalTime startTime, LocalTime endTime) {
        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(UUID.randomUUID());
        slot.setServiceId(serviceId);
        slot.setDate(date);
        slot.setStartTime(startTime);
        slot.setEndTime(endTime);
        slot.setCapacity(20);
        slot.setBookedCount(2);
        slot.setStatus(SlotStatus.OPEN);
        slot.markNew();
        return slotRepository.saveAndFlush(slot);
    }

    private SubOrderJpaEntity createOrderAndSubOrder(ServiceSlotJpaEntity slot, SubOrderStatus status, BigDecimal amount) {
        UUID orderId = UUID.randomUUID();
        MasterOrderJpaEntity master = new MasterOrderJpaEntity();
        master.setId(orderId);
        master.setCustomerId(customerId);
        master.setTotalAmount(amount);
        master.setDiscountAmount(BigDecimal.ZERO);
        master.setStatus(MasterOrderStatus.PAID);
        master.setPaymentStatus(PaymentOrderStatus.PAID);
        master.markNew();
        masterOrderRepository.saveAndFlush(master);

        SubOrderJpaEntity sub = new SubOrderJpaEntity();
        sub.setId(UUID.randomUUID());
        sub.setMasterOrderId(orderId);
        sub.setVendorId(vendorId);
        sub.setServiceId(serviceId);
        sub.setSlotId(slot.getId());
        sub.setStatus(status);
        sub.setQuantity(2);
        sub.setUnitPrice(amount.divide(BigDecimal.valueOf(2)));
        sub.setSubtotalAmount(amount);
        sub.setDiscountAmount(BigDecimal.ZERO);
        sub.setFinalAmount(amount);
        sub.markNew();
        return subOrderRepository.saveAndFlush(sub);
    }

    private void processPendingEmails() {
        List<TransactionalEmailDeliveryJpaEntity> pendings =
                emailRepository.findAll().stream()
                        .filter(e -> e.getStatus() == EmailDeliveryStatus.PENDING)
                        .toList();
        for (TransactionalEmailDeliveryJpaEntity p : pendings) {
            worker.processIndividualEmail(p.getId(), EmailDeliveryMode.CAPTURE);
        }
    }

    @Test
    @DisplayName("1. Khi Cảnh báo Đỏ đang PENDING và ca bị hủy -> Cảnh báo Đỏ chuyển SKIPPED (SUPERSEDED_BY_CANCELLATION), gửi TRIP_CANCELLED")
    void whenRedAlertPending_andTripCancelled_redAlertIsSkippedAndTripCancelledIsSent() {
        ServiceSlotJpaEntity slot = createSlot(LocalDate.now().plusDays(1), LocalTime.of(8, 0), LocalTime.of(10, 0));
        SubOrderJpaEntity subOrder = createOrderAndSubOrder(slot, SubOrderStatus.CONFIRMED, new BigDecimal("1000000"));

        OffsetDateTime now = OffsetDateTime.now();

        // 1. Enqueue TRIP_RED_ALERT
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.TRIP_RED_ALERT,
                    "slot:" + slot.getId() + ":weather:red",
                    "USER:" + customerId,
                    now,
                    now,
                    now.plusHours(12),
                    1,
                    Map.of(
                            "slotId", slot.getId().toString(),
                            "serviceName", "Lặn biển Sơn Trà",
                            "date", slot.getDate().toString(),
                            "startTime", slot.getStartTime().toString(),
                            "warning", "Gió giật cấp 7, sóng cao trên 2.5m"
                    )
            ));
        });

        // 2. Ca bị hủy và đơn con chuyển CANCELLED
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            slot.setStatus(SlotStatus.CANCELLED);
            slotRepository.saveAndFlush(slot);
            subOrder.setStatus(SubOrderStatus.CANCELLED);
            subOrder.setCancellationReason(RefundReason.WEATHER);
            subOrderRepository.saveAndFlush(subOrder);

            // Enqueue TRIP_CANCELLED
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.TRIP_CANCELLED,
                    "slot:" + slot.getId() + ":cancellation",
                    "USER:" + customerId,
                    now,
                    now,
                    now.plusDays(7),
                    1,
                    Map.of(
                            "slotId", slot.getId().toString(),
                            "serviceName", "Lặn biển Sơn Trà",
                            "reason", "Thời tiết nguy hiểm (Gió giật cấp 7)",
                            "subOrderIds", List.of(subOrder.getId().toString())
                    )
            ));
        });

        // 3. Worker xử lý batch
        processPendingEmails();

        // 4. Assertions:
        // Cảnh báo Đỏ bị SKIPPED với SUPERSEDED_BY_CANCELLATION
        TransactionalEmailDeliveryJpaEntity redAlert = emailRepository.findAll().stream()
                .filter(e -> e.getType() == TransactionalEmailType.TRIP_RED_ALERT)
                .findFirst()
                .orElseThrow();
        assertEquals(EmailDeliveryStatus.SKIPPED, redAlert.getStatus());
        assertEquals("SUPERSEDED_BY_CANCELLATION", redAlert.getLastErrorCode());

        // TRIP_CANCELLED được ACCEPTED
        TransactionalEmailDeliveryJpaEntity cancelEmail = emailRepository.findAll().stream()
                .filter(e -> e.getType() == TransactionalEmailType.TRIP_CANCELLED)
                .findFirst()
                .orElseThrow();
        assertEquals(EmailDeliveryStatus.ACCEPTED, cancelEmail.getStatus());

        ArgumentCaptor<RenderedEmail> captor = ArgumentCaptor.forClass(RenderedEmail.class);
        verify(captureSender).send(captor.capture());
        RenderedEmail sent = captor.getValue();
        assertEquals("customer@example.com", sent.to());
        assertTrue(sent.textBody().contains("Không đến điểm hẹn") || sent.textBody().contains("KHÔNG ĐẾN ĐIỂM HẸN"));
    }

    @Test
    @DisplayName("2. Khi Cảnh báo Đỏ đã gửi (ACCEPTED) rồi mới có quyết định hủy -> Vẫn gửi TRIP_CANCELLED cho khách")
    void whenRedAlertAlreadyAccepted_andTripCancelled_tripCancelledIsStillSent() {
        ServiceSlotJpaEntity slot = createSlot(LocalDate.now().plusDays(1), LocalTime.of(8, 0), LocalTime.of(10, 0));
        SubOrderJpaEntity subOrder = createOrderAndSubOrder(slot, SubOrderStatus.CONFIRMED, new BigDecimal("1000000"));

        OffsetDateTime now = OffsetDateTime.now();

        // 1. Enqueue & gửi trước TRIP_RED_ALERT
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.TRIP_RED_ALERT,
                    "slot:" + slot.getId() + ":weather:red",
                    "USER:" + customerId,
                    now,
                    now,
                    now.plusHours(12),
                    1,
                    Map.of(
                            "slotId", slot.getId().toString(),
                            "serviceName", "Lặn biển Sơn Trà",
                            "date", slot.getDate().toString(),
                            "startTime", slot.getStartTime().toString(),
                            "warning", "Cảnh báo bão gần bờ"
                    )
            ));
        });
        processPendingEmails();

        TransactionalEmailDeliveryJpaEntity redAlert = emailRepository.findAll().stream()
                .filter(e -> e.getType() == TransactionalEmailType.TRIP_RED_ALERT)
                .findFirst()
                .orElseThrow();
        assertEquals(EmailDeliveryStatus.ACCEPTED, redAlert.getStatus());

        // 2. Sau đó mới hủy ca
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            slot.setStatus(SlotStatus.CANCELLED);
            slotRepository.saveAndFlush(slot);
            subOrder.setStatus(SubOrderStatus.CANCELLED);
            subOrder.setCancellationReason(RefundReason.WEATHER);
            subOrderRepository.saveAndFlush(subOrder);

            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.TRIP_CANCELLED,
                    "slot:" + slot.getId() + ":cancellation",
                    "USER:" + customerId,
                    now,
                    now,
                    now.plusDays(7),
                    1,
                    Map.of(
                            "slotId", slot.getId().toString(),
                            "serviceName", "Lặn biển Sơn Trà",
                            "reason", "Hủy vì bão đổ bộ",
                            "subOrderIds", List.of(subOrder.getId().toString())
                    )
            ));
        });
        processPendingEmails();

        TransactionalEmailDeliveryJpaEntity cancelEmail = emailRepository.findAll().stream()
                .filter(e -> e.getType() == TransactionalEmailType.TRIP_CANCELLED)
                .findFirst()
                .orElseThrow();
        assertEquals(EmailDeliveryStatus.ACCEPTED, cancelEmail.getStatus());
        // Cả 2 email đều được gửi thành công
        assertEquals(2L, emailRepository.count());
    }

    @Test
    @DisplayName("3. Hai đường xử lý (Job thời tiết & Luồng hoàn tiền) cùng một lần hủy -> Khử trùng lặp, chỉ tạo 1 thư khách và 1 thư đối tác")
    void whenDualCancellationPaths_createsOnlyOneCancellationEmailPerRecipient() {
        ServiceSlotJpaEntity slot = createSlot(LocalDate.now().plusDays(1), LocalTime.of(8, 0), LocalTime.of(10, 0));
        SubOrderJpaEntity subOrder = createOrderAndSubOrder(slot, SubOrderStatus.CONFIRMED, new BigDecimal("1000000"));

        OffsetDateTime now = OffsetDateTime.now();
        String cancelKey = "slot:" + slot.getId() + ":cancellation";

        // Đường 1: Job thời tiết kích hoạt hủy
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.TRIP_CANCELLED,
                    cancelKey,
                    "USER:" + customerId,
                    now,
                    now,
                    now.plusDays(7),
                    1,
                    Map.of("slotId", slot.getId().toString(), "serviceName", "Lặn biển Sơn Trà")
            ));
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.TRIP_CANCELLED,
                    cancelKey,
                    "USER:" + vendorOwnerUserId,
                    now,
                    now,
                    now.plusDays(7),
                    1,
                    Map.of("slotId", slot.getId().toString(), "serviceName", "Lặn biển Sơn Trà", "isVendor", true)
            ));
        });

        // Đường 2: Luồng hoàn tiền / Disruption xử lý tiếp
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.TRIP_CANCELLED,
                    cancelKey,
                    "USER:" + customerId,
                    now,
                    now,
                    now.plusDays(7),
                    1,
                    Map.of("slotId", slot.getId().toString(), "serviceName", "Lặn biển Sơn Trà")
            ));
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.TRIP_CANCELLED,
                    cancelKey,
                    "USER:" + vendorOwnerUserId,
                    now,
                    now,
                    now.plusDays(7),
                    1,
                    Map.of("slotId", slot.getId().toString(), "serviceName", "Lặn biển Sơn Trà", "isVendor", true)
            ));
        });

        // Assertions: Mỗi recipient chỉ có đúng 1 bản ghi trong DB outbox
        List<TransactionalEmailDeliveryJpaEntity> deliveries = emailRepository.findAll();
        assertEquals(2, deliveries.size());
        assertTrue(deliveries.stream().anyMatch(d -> d.getRecipientKey().equals("USER:" + customerId)));
        assertTrue(deliveries.stream().anyMatch(d -> d.getRecipientKey().equals("USER:" + vendorOwnerUserId)));
    }

    @Test
    @DisplayName("4. Gửi theo vai trò: Khách (USER:<customerId>), Đối tác (USER:<vendorOwnerUserId>), Vận hành (OPS:OPERATIONS)")
    void whenDisruptionOccurs_routesToCorrectRoles() {
        ServiceSlotJpaEntity slot = createSlot(LocalDate.now().plusDays(1), LocalTime.of(8, 0), LocalTime.of(10, 0));
        SubOrderJpaEntity subOrder = createOrderAndSubOrder(slot, SubOrderStatus.CONFIRMED, new BigDecimal("1000000"));

        OffsetDateTime now = OffsetDateTime.now();
        properties.getOpsEmails().put("operations", "ops-team@danasea.vn");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            // Khách
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.TRIP_CANCELLED,
                    "slot:" + slot.getId() + ":cancellation",
                    "USER:" + customerId,
                    now,
                    now,
                    now.plusDays(7),
                    1,
                    Map.of("slotId", slot.getId().toString(), "serviceName", "Lặn biển Sơn Trà", "subOrderIds", List.of(subOrder.getId().toString()))
            ));
            // Đối tác
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.TRIP_CANCELLED,
                    "slot:" + slot.getId() + ":cancellation",
                    "USER:" + vendorOwnerUserId,
                    now,
                    now,
                    now.plusDays(7),
                    1,
                    Map.of("slotId", slot.getId().toString(), "serviceName", "Lặn biển Sơn Trà", "isVendor", true)
            ));
            // Vận hành
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.OPS_DISRUPTION_ALERT,
                    "slot:" + slot.getId() + ":ops:escalation:12h",
                    "OPS:OPERATIONS",
                    now,
                    now,
                    now.plusDays(7),
                    1,
                    Map.of("slotId", slot.getId().toString(), "serviceName", "Lặn biển Sơn Trà", "stage", "12h_FALLBACK")
            ));
        });

        processPendingEmails();

        ArgumentCaptor<RenderedEmail> captor = ArgumentCaptor.forClass(RenderedEmail.class);
        verify(captureSender, times(3)).send(captor.capture());

        List<RenderedEmail> sentEmails = captor.getAllValues();
        assertTrue(sentEmails.stream().anyMatch(e -> "customer@example.com".equals(e.to())));
        assertTrue(sentEmails.stream().anyMatch(e -> "vendor.owner@example.com".equals(e.to())));
        assertTrue(sentEmails.stream().anyMatch(e -> "ops-team@danasea.vn".equals(e.to())));
    }

    @Test
    @DisplayName("5. Thiếu cấu hình hộp thư vận hành (OPS:OPERATIONS) -> Ghi nhận SKIPPED (UNCONFIGURED_OPS_EMAIL) mà không ném lỗi crash")
    void whenOpsEmailNotConfigured_skipsWithoutError() {
        ServiceSlotJpaEntity slot = createSlot(LocalDate.now().plusDays(1), LocalTime.of(8, 0), LocalTime.of(10, 0));
        OffsetDateTime now = OffsetDateTime.now();

        // Đảm bảo không có cấu hình ops email
        properties.getOpsEmails().remove("operations");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.OPS_DISRUPTION_ALERT,
                    "slot:" + slot.getId() + ":ops:escalation:12h",
                    "OPS:OPERATIONS",
                    now,
                    now,
                    now.plusDays(7),
                    1,
                    Map.of("slotId", slot.getId().toString(), "serviceName", "Lặn biển Sơn Trà")
            ));
        });

        processPendingEmails();

        TransactionalEmailDeliveryJpaEntity delivery = emailRepository.findAll().stream()
                .filter(e -> "OPS:OPERATIONS".equals(e.getRecipientKey()))
                .findFirst()
                .orElseThrow();

        assertEquals(EmailDeliveryStatus.SKIPPED, delivery.getStatus());
        assertEquals("UNCONFIGURED_OPS_EMAIL", delivery.getLastErrorCode());
        verify(captureSender, never()).send(any());
    }

    @Test
    @DisplayName("6. Sửa địa chỉ danh mục dịch vụ (ServiceJpaEntity) -> Không phát email ra outbox")
    void whenServiceCatalogAddressUpdated_doesNotEnqueueEmails() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ServiceJpaEntity service = serviceRepository.findById(serviceId).orElseThrow();
            service.setAddress("Điểm hẹn mới tại Cảng Tiên Sa");
            service.setLatitude(new BigDecimal("16.1200"));
            service.setLongitude(new BigDecimal("108.2300"));
            serviceRepository.saveAndFlush(service);
        });

        assertEquals(0L, emailRepository.count());
    }

    @Test
    @DisplayName("7. Khách hủy chuyến có yêu cầu hoàn tiền PENDING -> Email nêu 'Đang xử lý hoàn tiền'")
    void whenTripCancelledWithPendingRefund_indicatesRefundInProgress() {
        ServiceSlotJpaEntity slot = createSlot(LocalDate.now().plusDays(1), LocalTime.of(8, 0), LocalTime.of(10, 0));
        SubOrderJpaEntity subOrder = createOrderAndSubOrder(slot, SubOrderStatus.CANCELLED, new BigDecimal("1000000"));

        RefundJpaEntity refund = new RefundJpaEntity();
        refund.setSubOrderId(subOrder.getId());
        refund.setAmount(new BigDecimal("1000000"));
        refund.setRefundPercentage(BigDecimal.valueOf(100.0));
        refund.setReason(RefundReason.WEATHER);
        refund.setStatus(RefundStatus.PENDING);
        refund.setIdempotencyKey("refund-" + subOrder.getId());
        refundRepository.saveAndFlush(refund);

        OffsetDateTime now = OffsetDateTime.now();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.TRIP_CANCELLED,
                    "slot:" + slot.getId() + ":cancellation",
                    "USER:" + customerId,
                    now,
                    now,
                    now.plusDays(7),
                    1,
                    Map.of(
                            "slotId", slot.getId().toString(),
                            "serviceName", "Lặn biển Sơn Trà",
                            "reason", "Thời tiết không an toàn",
                            "subOrderIds", List.of(subOrder.getId().toString())
                    )
            ));
        });

        processPendingEmails();

        ArgumentCaptor<RenderedEmail> captor = ArgumentCaptor.forClass(RenderedEmail.class);
        verify(captureSender).send(captor.capture());
        RenderedEmail sent = captor.getValue();
        assertTrue(sent.textBody().contains("hoàn tiền") || sent.textBody().contains("Hoàn tiền"));
        assertTrue(sent.textBody().contains("đang xử lý") || sent.textBody().contains("Đang xử lý"));
    }

    @Test
    @DisplayName("8. Khách hủy chuyến không được hoàn tiền (0%) -> Email nêu rõ chính sách không áp dụng hoàn tiền")
    void whenTripCancelledWithNoRefund_indicatesNonRefundable() {
        ServiceSlotJpaEntity slot = createSlot(LocalDate.now().plusDays(1), LocalTime.of(8, 0), LocalTime.of(10, 0));
        SubOrderJpaEntity subOrder = createOrderAndSubOrder(slot, SubOrderStatus.CANCELLED, new BigDecimal("1000000"));
        // Không tạo bản ghi refund nào

        OffsetDateTime now = OffsetDateTime.now();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.TRIP_CANCELLED,
                    "slot:" + slot.getId() + ":cancellation:norefund",
                    "USER:" + customerId,
                    now,
                    now,
                    now.plusDays(7),
                    1,
                    Map.of(
                            "slotId", slot.getId().toString(),
                            "serviceName", "Lặn biển Sơn Trà",
                            "reason", "Khách tự hủy sát giờ",
                            "subOrderIds", List.of(subOrder.getId().toString())
                    )
            ));
        });

        processPendingEmails();

        ArgumentCaptor<RenderedEmail> captor = ArgumentCaptor.forClass(RenderedEmail.class);
        verify(captureSender).send(captor.capture());
        RenderedEmail sent = captor.getValue();
        assertFalse(sent.textBody().contains("Đang xử lý hoàn tiền"));
        assertTrue(sent.textBody().contains("không áp dụng hoàn tiền") || sent.textBody().contains("Không áp dụng hoàn tiền"));
    }
}
