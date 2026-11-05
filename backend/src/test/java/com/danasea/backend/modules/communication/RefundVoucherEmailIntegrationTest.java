package com.danasea.backend.modules.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.account.infrastructure.persistence.entities.UserJpaEntity;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
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
import com.danasea.backend.modules.order.domain.models.DiscountScope;
import com.danasea.backend.modules.order.domain.models.DiscountSponsorType;
import com.danasea.backend.modules.order.domain.models.DiscountType;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.DiscountCodeJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaDiscountCodeRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository;
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
import com.danasea.backend.security.authentication.domain.models.Authentication;
import com.danasea.backend.security.authentication.infrastructure.security.JwtTokenProvider;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class RefundVoucherEmailIntegrationTest extends BaseSecurityIntegrationTest {

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
    @Autowired private JpaPaymentRepository paymentRepository;
    @Autowired private JpaDiscountCodeRepository discountCodeRepository;
    @Autowired private JpaSubOrderDisruptionRepository disruptionRepository;
    @Autowired private JpaDisputeRepository disputeRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private MockMvc mockMvc;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean(name = "captureTransactionalEmailSender")
    private TransactionalEmailSender captureSender;

    @MockitoBean private AccountInternalApi accountInternalApi;
    @MockitoBean private PaymentGatewayPort paymentGatewayPort;

    private UUID customerId;
    private UUID otherCustomerId;
    private UUID vendorId;
    private UUID vendorOwnerUserId;
    private UUID serviceId;
    private UUID categoryId;
    private UserJpaEntity customerUser;
    private UserJpaEntity otherCustomerUser;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.transactional-email.mode", () -> "capture");
        registry.add("app.transactional-email.worker.enabled", () -> "false");
        registry.add("app.transactional-email.enabled-families", () -> "REFUND,VOUCHER,DISRUPTION,OPS,BOOKING");
        registry.add("app.transactional-email.ops-emails.finance", () -> "finance-ops@danasea.vn");
        registry.add("app.transactional-email.ops-emails.operations", () -> "ops-team@danasea.vn");
    }

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        otherCustomerId = UUID.randomUUID();
        vendorOwnerUserId = UUID.randomUUID();
        vendorId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            emailRepository.deleteAll();
            disputeRepository.deleteAll();
            disruptionRepository.deleteAll();
            discountCodeRepository.deleteAll();
            refundRepository.deleteAll();
            paymentRepository.deleteAll();
            subOrderRepository.deleteAll();
            masterOrderRepository.deleteAll();
            slotRepository.deleteAll();
            serviceRepository.deleteAll();
            categoryRepository.deleteAll();
            vendorRepository.deleteAll();
            userRepository.deleteAll();

            customerUser = new UserJpaEntity();
            customerUser.setId(customerId);
            customerUser.setEmail("customer.refund@danasea.vn");
            customerUser.setPasswordHash("hashed");
            customerUser.setFullName("Lê Khách Hoàn");
            customerUser.setRole(Role.CUSTOMER);
            customerUser.setIsEmailVerified(true);
            customerUser.setIsLocked(false);
            customerUser.setLocale("vi");
            customerUser.markNew();
            userRepository.saveAndFlush(customerUser);

            otherCustomerUser = new UserJpaEntity();
            otherCustomerUser.setId(otherCustomerId);
            otherCustomerUser.setEmail("other.customer@danasea.vn");
            otherCustomerUser.setPasswordHash("hashed");
            otherCustomerUser.setFullName("Trần Khách Khác");
            otherCustomerUser.setRole(Role.CUSTOMER);
            otherCustomerUser.setIsEmailVerified(true);
            otherCustomerUser.setIsLocked(false);
            otherCustomerUser.setLocale("vi");
            otherCustomerUser.markNew();
            userRepository.saveAndFlush(otherCustomerUser);

            UserJpaEntity vendorUser = new UserJpaEntity();
            vendorUser.setId(vendorOwnerUserId);
            vendorUser.setEmail("vendor.refund@danasea.vn");
            vendorUser.setPasswordHash("hashed");
            vendorUser.setFullName("Công ty Du thuyền Sông Hàn");
            vendorUser.setRole(Role.VENDOR);
            vendorUser.setIsEmailVerified(true);
            vendorUser.setIsLocked(false);
            vendorUser.setLocale("vi");
            vendorUser.markNew();
            userRepository.saveAndFlush(vendorUser);

            VendorJpaEntity vendor = new VendorJpaEntity();
            vendor.setId(vendorId);
            vendor.setUserId(vendorOwnerUserId);
            vendor.setBusinessName("Du thuyền Sông Hàn Danasea");
            vendor.markNew();
            vendorRepository.saveAndFlush(vendor);

            CategoryJpaEntity cat = new CategoryJpaEntity();
            cat.setId(categoryId);
            cat.setName("Du thuyền ngắm cảnh");
            cat.setSlug("du-thuyen-" + UUID.randomUUID());
            cat.setIsActive(true);
            cat.setRequiresSafetyCert(false);
            cat.markNew();
            categoryRepository.saveAndFlush(cat);

            ServiceJpaEntity svc = new ServiceJpaEntity();
            svc.setId(serviceId);
            svc.setVendorId(vendorId);
            svc.setCategoryId(categoryId);
            svc.setName("Tour ngắm hoàng hôn Sông Hàn");
            svc.setNameEn("Sunset Cruise");
            svc.setPrice(BigDecimal.valueOf(500000));
            svc.setAddress("Bến tàu Sông Hàn, Đà Nẵng");
            svc.setLocationName("Sông Hàn");
            svc.setLatitude(new BigDecimal("16.0678"));
            svc.setLongitude(new BigDecimal("108.2208"));
            svc.markNew();
            serviceRepository.saveAndFlush(svc);
        });

        User domainCustomer = new User();
        domainCustomer.setId(customerId);
        domainCustomer.setEmail("customer.refund@danasea.vn");
        domainCustomer.setFullName("Lê Khách Hoàn");
        domainCustomer.setRole(Role.CUSTOMER);
        domainCustomer.setIsEmailVerified(true);
        domainCustomer.setIsLocked(false);
        domainCustomer.setLocale("vi");

        domainCustomer.setSessionVersion(0L);

        User domainOtherCustomer = new User();
        domainOtherCustomer.setId(otherCustomerId);
        domainOtherCustomer.setEmail("other.customer@danasea.vn");
        domainOtherCustomer.setFullName("Trần Khách Khác");
        domainOtherCustomer.setRole(Role.CUSTOMER);
        domainOtherCustomer.setIsEmailVerified(true);
        domainOtherCustomer.setIsLocked(false);
        domainOtherCustomer.setLocale("vi");
        domainOtherCustomer.setSessionVersion(0L);

        when(accountInternalApi.findUserById(customerId)).thenReturn(Optional.of(domainCustomer));
        when(accountInternalApi.findUserByEmail("customer.refund@danasea.vn")).thenReturn(Optional.of(domainCustomer));
        when(accountInternalApi.findUserByEmailUncached("customer.refund@danasea.vn")).thenReturn(Optional.of(domainCustomer));

        when(accountInternalApi.findUserById(otherCustomerId)).thenReturn(Optional.of(domainOtherCustomer));
        when(accountInternalApi.findUserByEmail("other.customer@danasea.vn")).thenReturn(Optional.of(domainOtherCustomer));
        when(accountInternalApi.findUserByEmailUncached("other.customer@danasea.vn")).thenReturn(Optional.of(domainOtherCustomer));

        properties.getOpsEmails().put("finance", "finance-ops@danasea.vn");
        properties.getOpsEmails().put("operations", "ops-team@danasea.vn");
        properties.setEnabledFamilies("REFUND,VOUCHER,DISRUPTION,OPS,BOOKING");

        reset(captureSender);
        when(captureSender.send(any(RenderedEmail.class)))
                .thenReturn(EmailSendResult.accepted());
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

    private String generateToken(UserJpaEntity user) {
        return jwtTokenProvider.generateAccessToken(new Authentication(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getRole().name(),
                true,
                true,
                "vi",
                0L
        ));
    }

    private ServiceSlotJpaEntity createSlot(LocalDate date, LocalTime start, LocalTime end) {
        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(UUID.randomUUID());
        slot.setServiceId(serviceId);
        slot.setDate(date);
        slot.setStartTime(start);
        slot.setEndTime(end);
        slot.setCapacity(20);
        slot.setBookedCount(0);
        slot.setStatus(SlotStatus.OPEN);
        slot.markNew();
        return slotRepository.saveAndFlush(slot);
    }

    private MasterOrderJpaEntity createMasterOrder(UUID custId) {
        MasterOrderJpaEntity mo = new MasterOrderJpaEntity();
        mo.setId(UUID.randomUUID());
        mo.setCustomerId(custId);
        mo.setStatus(MasterOrderStatus.PAID);
        mo.setPaymentStatus(PaymentOrderStatus.PAID);
        mo.setTotalAmount(BigDecimal.valueOf(500000));
        mo.setDiscountAmount(BigDecimal.ZERO);
        mo.markNew();
        return masterOrderRepository.saveAndFlush(mo);
    }

    private SubOrderJpaEntity createSubOrder(MasterOrderJpaEntity mo, ServiceSlotJpaEntity slot) {
        SubOrderJpaEntity so = new SubOrderJpaEntity();
        so.setId(UUID.randomUUID());
        so.setMasterOrderId(mo.getId());
        so.setVendorId(vendorId);
        so.setServiceId(serviceId);
        so.setSlotId(slot.getId());
        so.setStatus(SubOrderStatus.CONFIRMED);
        so.setQuantity(1);
        so.setUnitPrice(BigDecimal.valueOf(500000));
        so.setSubtotalAmount(BigDecimal.valueOf(500000));
        so.setDiscountAmount(BigDecimal.ZERO);
        so.setFinalAmount(BigDecimal.valueOf(500000));
        so.markNew();
        return subOrderRepository.saveAndFlush(so);
    }

    private PaymentJpaEntity createPayment(MasterOrderJpaEntity mo, BigDecimal amount) {
        PaymentJpaEntity p = new PaymentJpaEntity();
        p.setId(UUID.randomUUID());
        p.setMasterOrderId(mo.getId());
        p.setAmount(amount);
        p.setProvider(PaymentProvider.PAYPAL);
        p.setProviderTransactionId("CAPTURE-" + UUID.randomUUID());
        p.setProviderAmount(BigDecimal.valueOf(20.00));
        p.setProviderCurrency("USD");
        p.setStatus(PaymentStatus.SUCCESS);
        p.markNew();
        return paymentRepository.saveAndFlush(p);
    }

    private RefundJpaEntity createRefund(SubOrderJpaEntity so, PaymentJpaEntity p, BigDecimal amount, RefundStatus status, OffsetDateTime createdAt) {
        RefundJpaEntity r = new RefundJpaEntity();
        r.setId(UUID.randomUUID());
        r.setSubOrderId(so.getId());
        r.setPaymentId(p.getId());
        r.setProvider(p.getProvider());
        r.setAmount(amount);
        r.setProviderAmount(BigDecimal.valueOf(20.00));
        r.setProviderCurrency("USD");
        r.setProviderTransactionId(p.getProviderTransactionId());
        r.setStatus(status);
        r.setReason(RefundReason.CUSTOMER_REQUEST);
        r.setRequestedBy(customerId);
        r.setIdempotencyKey("refund-idem-" + UUID.randomUUID());
        r.setCreatedAt(createdAt);
        r.markNew();
        return refundRepository.saveAndFlush(r);
    }

    @Test
    @DisplayName("Ca 1: Khi TRIP_CANCELLED đã gộp đủ thông tin hoàn tiền -> Bỏ qua REFUND_REQUESTED riêng")
    void whenRefundRequested_andTripCancelledCoversIt_skipsRefundRequestedEmail() {
        ServiceSlotJpaEntity slot = createSlot(LocalDate.now().plusDays(2), LocalTime.of(15, 0), LocalTime.of(17, 0));
        MasterOrderJpaEntity mo = createMasterOrder(customerId);
        SubOrderJpaEntity so = createSubOrder(mo, slot);
        PaymentJpaEntity p = createPayment(mo, BigDecimal.valueOf(500000));
        RefundJpaEntity refund = createRefund(so, p, BigDecimal.valueOf(500000), RefundStatus.PENDING, OffsetDateTime.now());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            OffsetDateTime now = OffsetDateTime.now();
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.TRIP_CANCELLED,
                    "slot:" + slot.getId() + ":cancellation",
                    "USER:" + customerId,
                    now, now, now.plusDays(7), 1,
                    Map.of("slotId", slot.getId().toString(),
                            "serviceName", "Tour ngắm hoàng hôn",
                            "reason", "Thời tiết xấu",
                            "subOrderIds", List.of(so.getId().toString()),
                            "refundId", refund.getId().toString(),
                            "refundAmount", "500000")
            ));

            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.REFUND_REQUESTED,
                    "refund:" + refund.getId() + ":requested",
                    "USER:" + customerId,
                    now, now, now.plusDays(7), 1,
                    Map.of("refundId", refund.getId().toString(),
                            "subOrderId", so.getId().toString(),
                            "amount", "500000")
            ));
        });

        processPendingEmails();

        var deliveries = emailRepository.findAll();
        var cancelledDelivery = deliveries.stream()
                .filter(d -> d.getType() == TransactionalEmailType.TRIP_CANCELLED)
                .findFirst().orElseThrow();
        var refundRequestedDelivery = deliveries.stream()
                .filter(d -> d.getType() == TransactionalEmailType.REFUND_REQUESTED)
                .findFirst().orElseThrow();

        assertEquals(EmailDeliveryStatus.ACCEPTED, cancelledDelivery.getStatus());
        assertEquals(EmailDeliveryStatus.SKIPPED, refundRequestedDelivery.getStatus());
        assertEquals("SUPERSEDED_BY_TRIP_CANCELLED", refundRequestedDelivery.getLastErrorCode());
    }

    @Test
    @DisplayName("Ca 2: Khi nhóm disruption tắt -> REFUND_REQUESTED vẫn được gửi riêng")
    void whenRefundRequested_andDisruptionFamilyOff_sendsRefundRequestedEmail() {
        ServiceSlotJpaEntity slot = createSlot(LocalDate.now().plusDays(2), LocalTime.of(15, 0), LocalTime.of(17, 0));
        MasterOrderJpaEntity mo = createMasterOrder(customerId);
        SubOrderJpaEntity so = createSubOrder(mo, slot);
        PaymentJpaEntity p = createPayment(mo, BigDecimal.valueOf(500000));
        RefundJpaEntity refund = createRefund(so, p, BigDecimal.valueOf(500000), RefundStatus.PENDING, OffsetDateTime.now());

        properties.setEnabledFamilies("REFUND,VOUCHER,BOOKING");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            OffsetDateTime now = OffsetDateTime.now();
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.REFUND_REQUESTED,
                    "refund:" + refund.getId() + ":requested",
                    "USER:" + customerId,
                    now, now, now.plusDays(7), 1,
                    Map.of("refundId", refund.getId().toString(),
                            "subOrderId", so.getId().toString(),
                            "amount", "500000")
            ));
        });

        processPendingEmails();

        var deliveries = emailRepository.findAll();
        var refundRequestedDelivery = deliveries.stream()
                .filter(d -> d.getType() == TransactionalEmailType.REFUND_REQUESTED)
                .findFirst().orElseThrow();

        assertEquals(EmailDeliveryStatus.ACCEPTED, refundRequestedDelivery.getStatus());

        ArgumentCaptor<RenderedEmail> captor = ArgumentCaptor.forClass(RenderedEmail.class);
        verify(captureSender, atLeastOnce()).send(captor.capture());
        assertTrue(captor.getValue().textBody().contains("tiếp nhận yêu cầu hoàn tiền"));
    }

    @Test
    @DisplayName("Ca 3: Hoàn tiền đã PROCESSED trước khi gửi thư tiếp nhận -> Bỏ qua REFUND_REQUESTED, gửi REFUND_PROCESSED")
    void whenRefundProcessedBeforeRequestedEmailSent_supersedesRequestedAndSendsProcessed() {
        ServiceSlotJpaEntity slot = createSlot(LocalDate.now().plusDays(2), LocalTime.of(15, 0), LocalTime.of(17, 0));
        MasterOrderJpaEntity mo = createMasterOrder(customerId);
        SubOrderJpaEntity so = createSubOrder(mo, slot);
        PaymentJpaEntity p = createPayment(mo, BigDecimal.valueOf(500000));
        RefundJpaEntity refund = createRefund(so, p, BigDecimal.valueOf(500000), RefundStatus.PENDING, OffsetDateTime.now());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            OffsetDateTime now = OffsetDateTime.now();
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.REFUND_REQUESTED,
                    "refund:" + refund.getId() + ":requested",
                    "USER:" + customerId,
                    now, now, now.plusDays(7), 1,
                    Map.of("refundId", refund.getId().toString(),
                            "subOrderId", so.getId().toString(),
                            "amount", "500000")
            ));
        });

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            RefundJpaEntity ref = refundRepository.findById(refund.getId()).orElseThrow();
            ref.setStatus(RefundStatus.PROCESSED);
            ref.setProcessedAt(OffsetDateTime.now());
            refundRepository.saveAndFlush(ref);

            OffsetDateTime now = OffsetDateTime.now();
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.REFUND_PROCESSED,
                    "refund:" + refund.getId() + ":processed",
                    "USER:" + customerId,
                    now, now, now.plusDays(7), 1,
                    Map.of("refundId", refund.getId().toString(),
                            "subOrderId", so.getId().toString(),
                            "amount", "500000")
            ));
        });

        processPendingEmails();

        var deliveries = emailRepository.findAll();
        var requestedDelivery = deliveries.stream()
                .filter(d -> d.getType() == TransactionalEmailType.REFUND_REQUESTED)
                .findFirst().orElseThrow();
        var processedDelivery = deliveries.stream()
                .filter(d -> d.getType() == TransactionalEmailType.REFUND_PROCESSED)
                .findFirst().orElseThrow();

        assertEquals(EmailDeliveryStatus.SKIPPED, requestedDelivery.getStatus());
        assertEquals("SUPERSEDED_BY_REFUND_PROCESSED", requestedDelivery.getLastErrorCode());
        assertEquals(EmailDeliveryStatus.ACCEPTED, processedDelivery.getStatus());

        ArgumentCaptor<RenderedEmail> captor = ArgumentCaptor.forClass(RenderedEmail.class);
        verify(captureSender, atLeastOnce()).send(captor.capture());
        assertTrue(captor.getValue().textBody().contains("Cổng thanh toán đã xử lý khoản hoàn"));
    }

    @Test
    @DisplayName("Ca 4: Webhook và Job hoàn tiền lặp lại -> Khử trùng lặp, chỉ tạo 1 bản ghi email ở mỗi mốc")
    void whenRefundWebhookOrJobRepeated_createsOnlyOneEmailDeliveryPerMilestone() {
        ServiceSlotJpaEntity slot = createSlot(LocalDate.now().plusDays(2), LocalTime.of(15, 0), LocalTime.of(17, 0));
        MasterOrderJpaEntity mo = createMasterOrder(customerId);
        SubOrderJpaEntity so = createSubOrder(mo, slot);
        PaymentJpaEntity p = createPayment(mo, BigDecimal.valueOf(500000));
        RefundJpaEntity refund = createRefund(so, p, BigDecimal.valueOf(500000), RefundStatus.PROCESSED, OffsetDateTime.now());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            OffsetDateTime now = OffsetDateTime.now();
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.REFUND_PROCESSED,
                    "refund:" + refund.getId() + ":processed",
                    "USER:" + customerId,
                    now, now, now.plusDays(7), 1,
                    Map.of("refundId", refund.getId().toString(), "amount", "500000")
            ));
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.REFUND_PROCESSED,
                    "refund:" + refund.getId() + ":processed",
                    "USER:" + customerId,
                    now, now, now.plusDays(7), 1,
                    Map.of("refundId", refund.getId().toString(), "amount", "500000")
            ));
        });

        var deliveries = emailRepository.findAll();
        assertEquals(1, deliveries.size());
        assertEquals("refund:" + refund.getId() + ":processed", deliveries.get(0).getBusinessKey());
    }

    @Test
    @DisplayName("Ca 5: Quá hạn 24 giờ rồi thất bại -> Gửi cảnh báo quá hạn rồi tiếp tục gửi cảnh báo lỗi")
    void whenOverdueAlertSent_andThenRefundFails_sendsFailedAlert() {
        ServiceSlotJpaEntity slot = createSlot(LocalDate.now().plusDays(2), LocalTime.of(15, 0), LocalTime.of(17, 0));
        MasterOrderJpaEntity mo = createMasterOrder(customerId);
        SubOrderJpaEntity so = createSubOrder(mo, slot);
        PaymentJpaEntity p = createPayment(mo, BigDecimal.valueOf(500000));
        RefundJpaEntity refund = createRefund(so, p, BigDecimal.valueOf(500000), RefundStatus.PENDING, OffsetDateTime.now().minusHours(25));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            OffsetDateTime now = OffsetDateTime.now();
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.REFUND_NEEDS_ATTENTION,
                    "refund:" + refund.getId() + ":overdue",
                    "OPS:FINANCE",
                    now, now, now.plusDays(7), 1,
                    Map.of("refundId", refund.getId().toString(), "reason", "OVERDUE_24H", "amount", "500000")
            ));
        });

        processPendingEmails();

        var overdueDelivery = emailRepository.findAll().stream()
                .filter(d -> ("refund:" + refund.getId() + ":overdue").equals(d.getBusinessKey()))
                .findFirst().orElseThrow();
        assertEquals(EmailDeliveryStatus.ACCEPTED, overdueDelivery.getStatus());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            RefundJpaEntity ref = refundRepository.findById(refund.getId()).orElseThrow();
            ref.setStatus(RefundStatus.FAILED);
            ref.setLastError("GATEWAY_TIMEOUT");
            refundRepository.saveAndFlush(ref);

            OffsetDateTime now = OffsetDateTime.now();
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.REFUND_NEEDS_ATTENTION,
                    "refund:" + refund.getId() + ":failed",
                    "OPS:FINANCE",
                    now, now, now.plusDays(7), 1,
                    Map.of("refundId", refund.getId().toString(), "reason", "FAILED", "lastError", "GATEWAY_TIMEOUT")
            ));
        });

        processPendingEmails();

        var failedDelivery = emailRepository.findAll().stream()
                .filter(d -> ("refund:" + refund.getId() + ":failed").equals(d.getBusinessKey()))
                .findFirst().orElseThrow();
        assertEquals(EmailDeliveryStatus.ACCEPTED, failedDelivery.getStatus());
    }

    @Test
    @DisplayName("Ca 6: Cảnh báo quá hạn còn trong hàng đợi nhưng refund đã rời PENDING -> Bỏ qua cảnh báo đó")
    void whenOverdueRefundLeavesPending_skipsOverdueAlert() {
        ServiceSlotJpaEntity slot = createSlot(LocalDate.now().plusDays(2), LocalTime.of(15, 0), LocalTime.of(17, 0));
        MasterOrderJpaEntity mo = createMasterOrder(customerId);
        SubOrderJpaEntity so = createSubOrder(mo, slot);
        PaymentJpaEntity p = createPayment(mo, BigDecimal.valueOf(500000));
        RefundJpaEntity refund = createRefund(so, p, BigDecimal.valueOf(500000), RefundStatus.PENDING, OffsetDateTime.now().minusHours(25));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            OffsetDateTime now = OffsetDateTime.now();
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.REFUND_NEEDS_ATTENTION,
                    "refund:" + refund.getId() + ":overdue",
                    "OPS:FINANCE",
                    now, now, now.plusDays(7), 1,
                    Map.of("refundId", refund.getId().toString(), "reason", "OVERDUE_24H")
            ));

            RefundJpaEntity ref = refundRepository.findById(refund.getId()).orElseThrow();
            ref.setStatus(RefundStatus.PROCESSED);
            refundRepository.saveAndFlush(ref);
        });

        processPendingEmails();

        var overdueDelivery = emailRepository.findAll().stream()
                .filter(d -> ("refund:" + refund.getId() + ":overdue").equals(d.getBusinessKey()))
                .findFirst().orElseThrow();
        assertEquals(EmailDeliveryStatus.SKIPPED, overdueDelivery.getStatus());
        assertEquals("REFUND_NO_LONGER_PENDING", overdueDelivery.getLastErrorCode());
    }

    @Test
    @DisplayName("Ca 7: Thiếu cấu hình email vận hành tài chính (OPS:FINANCE) -> Ghi nhận SKIPPED an toàn")
    void whenOpsFinanceEmailNotConfigured_skipsWithoutError() {
        properties.getOpsEmails().remove("finance");
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                OffsetDateTime now = OffsetDateTime.now();
                transactionalEmailPort.enqueue(new EmailRequest(
                        TransactionalEmailType.REFUND_NEEDS_ATTENTION,
                        "refund:" + UUID.randomUUID() + ":failed",
                        "OPS:FINANCE",
                        now, now, now.plusDays(7), 1,
                        Map.of("reason", "FAILED")
                ));
            });

            processPendingEmails();

            var deliveries = emailRepository.findAll();
            assertEquals(1, deliveries.size());
            assertEquals(EmailDeliveryStatus.SKIPPED, deliveries.get(0).getStatus());
            assertEquals("UNCONFIGURED_OPS_EMAIL", deliveries.get(0).getLastErrorCode());
        } finally {
            properties.getOpsEmails().put("finance", "finance-ops@danasea.vn");
        }
    }

    @Test
    @DisplayName("Ca 8: Cấp voucher cá nhân từ lỗi đối tác và lỗi hệ thống -> Gửi đúng mã và link tới /profile/vouchers")
    void whenVendorFaultOrSystemFaultVoucherIssued_enqueuesPersonalVoucherEmail() {
        OffsetDateTime now = OffsetDateTime.now();

        DiscountCodeJpaEntity v1 = new DiscountCodeJpaEntity();
        v1.setId(UUID.randomUUID());
        v1.setCode("VC-PARTNER-COMP-01");
        v1.setScope(DiscountScope.PLATFORM);
        v1.setSponsorType(DiscountSponsorType.PLATFORM);
        v1.setRecipientCustomerId(customerId);
        v1.setDiscountType(DiscountType.FIXED);
        v1.setDiscountValue(BigDecimal.valueOf(100000));
        v1.setMinOrderAmount(BigDecimal.valueOf(200000));
        v1.setMaxUses(1);
        v1.setMaxUsesPerUser(1);
        v1.setValidFrom(now);
        v1.setValidTo(now.plusDays(30));
        v1.setIsActive(true);
        v1.markNew();
        discountCodeRepository.saveAndFlush(v1);

        DiscountCodeJpaEntity v2 = new DiscountCodeJpaEntity();
        v2.setId(UUID.randomUUID());
        v2.setCode("SYS-REPLACE-02");
        v2.setScope(DiscountScope.PLATFORM);
        v2.setSponsorType(DiscountSponsorType.PLATFORM);
        v2.setRecipientCustomerId(customerId);
        v2.setDiscountType(DiscountType.FIXED);
        v2.setDiscountValue(BigDecimal.valueOf(200000));
        v2.setMinOrderAmount(BigDecimal.ZERO);
        v2.setMaxUses(1);
        v2.setMaxUsesPerUser(1);
        v2.setValidFrom(now);
        v2.setValidTo(now.plusDays(30));
        v2.setIsActive(true);
        v2.markNew();
        discountCodeRepository.saveAndFlush(v2);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.PERSONAL_VOUCHER_ISSUED,
                    "voucher:" + v1.getId(),
                    "USER:" + customerId,
                    now, now, now.plusDays(7), 1,
                    Map.of("voucherId", v1.getId().toString(), "code", v1.getCode(), "discountValue", "100000")
            ));

            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.PERSONAL_VOUCHER_ISSUED,
                    "voucher:" + v2.getId(),
                    "USER:" + customerId,
                    now, now, now.plusDays(7), 1,
                    Map.of("voucherId", v2.getId().toString(), "code", v2.getCode(), "discountValue", "200000")
            ));

            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.PERSONAL_VOUCHER_ISSUED,
                    "voucher:" + v1.getId(),
                    "USER:" + customerId,
                    now, now, now.plusDays(7), 1,
                    Map.of("voucherId", v1.getId().toString(), "code", v1.getCode())
            ));
        });

        var deliveries = emailRepository.findAll();
        assertEquals(2, deliveries.size());

        processPendingEmails();

        var deliveriesAfter = emailRepository.findAll();
        assertTrue(deliveriesAfter.stream().allMatch(d -> d.getStatus() == EmailDeliveryStatus.ACCEPTED));

        ArgumentCaptor<RenderedEmail> captor = ArgumentCaptor.forClass(RenderedEmail.class);
        verify(captureSender, atLeastOnce()).send(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(e -> e.textBody().contains("/profile/vouchers")));
    }

    @Test
    @DisplayName("Ca 9: Kiểm tra API Kho voucher cá nhân (/api/customers/me/discount-codes) yêu cầu đăng nhập và chỉ trả về đúng voucher của người dùng")
    void whenAccessingVouchersApi_unauthenticatedOrOtherUser_enforcesAccessControl() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();

        DiscountCodeJpaEntity vCustomer = new DiscountCodeJpaEntity();
        vCustomer.setId(UUID.randomUUID());
        vCustomer.setCode("VC-MY-VOUCHER");
        vCustomer.setScope(DiscountScope.PLATFORM);
        vCustomer.setSponsorType(DiscountSponsorType.PLATFORM);
        vCustomer.setRecipientCustomerId(customerId);
        vCustomer.setDiscountType(DiscountType.FIXED);
        vCustomer.setDiscountValue(BigDecimal.valueOf(100000));
        vCustomer.setMinOrderAmount(BigDecimal.ZERO);
        vCustomer.setMaxUses(1);
        vCustomer.setMaxUsesPerUser(1);
        vCustomer.setValidFrom(now);
        vCustomer.setValidTo(now.plusDays(30));
        vCustomer.setIsActive(true);
        vCustomer.markNew();
        discountCodeRepository.saveAndFlush(vCustomer);

        DiscountCodeJpaEntity vOther = new DiscountCodeJpaEntity();
        vOther.setId(UUID.randomUUID());
        vOther.setCode("VC-OTHER-VOUCHER");
        vOther.setScope(DiscountScope.PLATFORM);
        vOther.setSponsorType(DiscountSponsorType.PLATFORM);
        vOther.setRecipientCustomerId(otherCustomerId);
        vOther.setDiscountType(DiscountType.FIXED);
        vOther.setDiscountValue(BigDecimal.valueOf(50000));
        vOther.setMinOrderAmount(BigDecimal.ZERO);
        vOther.setMaxUses(1);
        vOther.setMaxUsesPerUser(1);
        vOther.setValidFrom(now);
        vOther.setValidTo(now.plusDays(30));
        vOther.setIsActive(true);
        vOther.markNew();
        discountCodeRepository.saveAndFlush(vOther);

        mockMvc.perform(get("/api/customers/me/discount-codes"))
                .andExpect(status().isUnauthorized());

        String token = generateToken(customerUser);
        mockMvc.perform(get("/api/customers/me/discount-codes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].code").value("VC-MY-VOUCHER"));

        String otherToken = generateToken(otherCustomerUser);
        mockMvc.perform(get("/api/customers/me/discount-codes")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].code").value("VC-OTHER-VOUCHER"));
    }
}
