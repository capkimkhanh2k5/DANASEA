package com.danasea.backend.modules.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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
import com.danasea.backend.modules.account.application.usecases.ChangePasswordUseCase;
import com.danasea.backend.modules.account.domain.models.PasswordResetToken;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.account.infrastructure.persistence.entities.UserJpaEntity;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.admin.application.usecases.LockUserUseCase;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.CategoryJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaCategoryRepository;
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
import com.danasea.backend.modules.dispute.application.usecases.ResolveDisputeUseCase;
import com.danasea.backend.modules.dispute.domain.models.DisputeReason;
import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.dispute.infrastructure.persistence.entities.DisputeJpaEntity;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.dispute.presentation.dtos.ResolveDisputeRequest;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.settlement.application.usecases.FinalizeSettlementUseCase;
import com.danasea.backend.modules.settlement.domain.models.SettlementStatus;
import com.danasea.backend.modules.settlement.infrastructure.persistence.entities.SettlementJpaEntity;
import com.danasea.backend.modules.settlement.infrastructure.persistence.repositories.JpaSettlementRepository;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import com.danasea.backend.security.authentication.application.ports.PasswordHasher;
import com.danasea.backend.security.authentication.application.usecases.ResetPasswordUseCase;
import com.danasea.backend.security.authentication.domain.models.Authentication;
import com.danasea.backend.security.authentication.infrastructure.security.HashUtils;
import com.danasea.backend.security.authentication.infrastructure.security.JwtTokenProvider;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class CaseAndAccountEmailIntegrationTest extends BaseSecurityIntegrationTest {

    @DynamicPropertySource
    static void configureCaseEmailProperties(DynamicPropertyRegistry registry) {
        registry.add("app.transactional-email.mode", () -> "capture");
        registry.add("app.transactional-email.worker.enabled", () -> "false");
        registry.add("app.transactional-email.worker.batch-size", () -> "20");
        registry.add("app.transactional-email.enabled-families", () -> "CASES,ACCOUNT,SETTLEMENT,REFUND,DISRUPTION");
        registry.add("app.transactional-email.ops-emails.finance", () -> "finance-test@danasea.vn");
        registry.add("app.transactional-email.ops-emails.operations", () -> "operations-test@danasea.vn");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private TransactionalEmailPort transactionalEmailPort;
    @Autowired private TransactionalEmailWorker worker;
    @Autowired private TransactionalEmailProperties properties;
    @Autowired private JpaTransactionalEmailDeliveryRepository deliveryRepository;

    @Autowired private ResolveDisputeUseCase resolveDisputeUseCase;
    @Autowired private ChangePasswordUseCase changePasswordUseCase;
    @Autowired private ResetPasswordUseCase resetPasswordUseCase;
    @Autowired private LockUserUseCase lockUserUseCase;
    @Autowired private FinalizeSettlementUseCase finalizeSettlementUseCase;

    @Autowired private JpaDisputeRepository disputeRepository;
    @Autowired private JpaMasterOrderRepository masterOrderRepository;
    @Autowired private JpaSubOrderRepository subOrderRepository;
    @Autowired private JpaRefundRepository refundRepository;
    @Autowired private JpaVendorRepository vendorRepository;
    @Autowired private JpaCategoryRepository categoryRepository;
    @Autowired private JpaServiceRepository serviceRepository;
    @Autowired private JpaServiceSlotRepository serviceSlotRepository;
    @Autowired private JpaSettlementRepository settlementRepository;
    @Autowired private JpaUserRepository userRepository;

    @Autowired private PasswordHasher passwordHasher;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean(name = "captureTransactionalEmailSender")
    private TransactionalEmailSender sender;
    @MockitoBean private AccountInternalApi accountInternalApi;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        cleanDatabase();
        reset(sender);

        when(sender.send(any(RenderedEmail.class)))
                .thenReturn(EmailSendResult.accepted());

        properties.setMode("capture");
        properties.setEnabledFamilies("CASES,ACCOUNT,SETTLEMENT,REFUND,OPS");
        properties.getOpsEmails().put("finance", "finance-test@danasea.vn");
        properties.getOpsEmails().put("operations", "operations-test@danasea.vn");
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        tx.executeWithoutResult(status -> {
            deliveryRepository.deleteAll();
            disputeRepository.deleteAll();
            settlementRepository.deleteAll();
            refundRepository.deleteAll();
            subOrderRepository.deleteAll();
            masterOrderRepository.deleteAll();
            serviceSlotRepository.deleteAll();
            serviceRepository.deleteAll();
            categoryRepository.deleteAll();
            vendorRepository.deleteAll();
            userRepository.deleteAll();
        });
    }

    private void processPendingEmails() {
        List<TransactionalEmailDeliveryJpaEntity> pendings =
                deliveryRepository.findAll().stream()
                        .filter(e -> e.getStatus() == EmailDeliveryStatus.PENDING)
                        .toList();
        for (TransactionalEmailDeliveryJpaEntity p : pendings) {
            worker.processIndividualEmail(p.getId(), EmailDeliveryMode.CAPTURE);
        }
    }

    // =========================================================================
    // 1. DISPUTE_RESOLVED: 2 templates khác nhau cho Khách & Đối tác
    // =========================================================================
    @Test
    @DisplayName("PR 6 - Khi giải quyết tranh chấp, gửi 2 template riêng biệt cho khách và đối tác, chống trùng lặp")
    void whenDisputeResolved_sendsDifferentTemplatesToCustomerAndVendor() {
        UUID customerId = UUID.randomUUID();
        UUID vendorOwnerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        mockUser(customerId, "customer@danasea.vn", "Nguyễn Khách", Role.CUSTOMER, true, false);
        mockUser(vendorOwnerId, "vendor@danasea.vn", "Trần Đối Tác", Role.VENDOR, true, false);
        mockUser(adminId, "admin@danasea.vn", "Quản Trị Viên", Role.ADMIN, true, false);

        var fixture = createDisputeFixture(customerId, vendorOwnerId, BigDecimal.valueOf(500000));
        UUID disputeId = fixture.disputeId();

        // Admin resolve: Chấp thuận hoàn 50%
        ResolveDisputeRequest request = new ResolveDisputeRequest(
                DisputeStatus.RESOLVED_PARTIAL,
                BigDecimal.valueOf(50.0),
                "Đồng ý hoàn 50% theo bằng chứng hai bên cung cấp"
        );

        tx.executeWithoutResult(status -> resolveDisputeUseCase.execute(disputeId, request, adminId));

        // Worker xử lý hàng đợi
        processPendingEmails();

        // Kiểm tra 2 email được gửi: 1 cho khách, 1 cho đối tác
        var deliveries = deliveryRepository.findAll();
        var customerEmailOpt = deliveries.stream()
                .filter(d -> d.getType() == TransactionalEmailType.DISPUTE_RESOLVED)
                .filter(d -> d.getRecipientKey().equals("USER:" + customerId))
                .findFirst();
        var vendorEmailOpt = deliveries.stream()
                .filter(d -> d.getType() == TransactionalEmailType.DISPUTE_RESOLVED)
                .filter(d -> d.getRecipientKey().equals("USER:" + vendorOwnerId))
                .findFirst();

        assertTrue(customerEmailOpt.isPresent(), "Phải có email DISPUTE_RESOLVED gửi cho khách hàng");
        assertTrue(vendorEmailOpt.isPresent(), "Phải có email DISPUTE_RESOLVED gửi cho đối tác");

        assertEquals(EmailDeliveryStatus.ACCEPTED, customerEmailOpt.get().getStatus());
        assertEquals(EmailDeliveryStatus.ACCEPTED, vendorEmailOpt.get().getStatus());

        ArgumentCaptor<RenderedEmail> captor = ArgumentCaptor.forClass(RenderedEmail.class);
        verify(sender, atLeastOnce()).send(captor.capture());
        List<RenderedEmail> sentEmails = captor.getAllValues();

        RenderedEmail customerEmail = sentEmails.stream()
                .filter(e -> "customer@danasea.vn".equals(e.to()))
                .findFirst().orElseThrow();
        RenderedEmail vendorEmail = sentEmails.stream()
                .filter(e -> "vendor@danasea.vn".equals(e.to()))
                .findFirst().orElseThrow();

        // Khách: Thấy kết quả, lý do công bố, số tiền hoàn (250.000 đ) và link xem vụ việc
        assertTrue(customerEmail.textBody().contains("250.000"), "Email khách phải hiển thị số tiền hoàn 50% (250.000 đ)");
        assertTrue(customerEmail.textBody().contains("Đồng ý hoàn 50%"), "Email khách phải có lý do công bố");
        assertTrue(customerEmail.textBody().contains("/profile/orders"), "Email khách phải dẫn về chi tiết đơn");

        // Đối tác: Thấy kết quả, việc cần làm, khoản khấu trừ đối soát
        assertTrue(vendorEmail.textBody().contains("khấu trừ") || vendorEmail.textBody().contains("đối soát"),
                "Email đối tác phải thông báo ảnh hưởng tài chính/khấu trừ đối soát");
        assertTrue(vendorEmail.textBody().contains("/vendor/disputes"), "Email đối tác phải dẫn về cổng đối tác");

        // Chống trùng lặp: Gọi lại với cùng disputeId không tạo thêm email
        long countBefore = deliveryRepository.count();
        tx.executeWithoutResult(status -> {
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.DISPUTE_RESOLVED,
                    "dispute:" + disputeId + ":customer:resolved",
                    "USER:" + customerId,
                    OffsetDateTime.now(),
                    OffsetDateTime.now(),
                    null,
                    1,
                    Map.of("disputeId", disputeId.toString())
            ));
        });
        assertEquals(countBefore, deliveryRepository.count(), "Replay cùng businessKey không tạo bản ghi mới");
    }

    // =========================================================================
    // 2. GỘP THƯ: REFUND_REQUESTED bị SUPERSEDED bởi DISPUTE_RESOLVED
    // =========================================================================
    @Test
    @DisplayName("PR 6 - Khi DISPUTE_RESOLVED đủ điều kiện gửi và chứa refundId, REFUND_REQUESTED bị SKIPPED (SUPERSEDED)")
    void whenDisputeResolvedWithRefund_supersedesRefundRequestedEmail() {
        UUID customerId = UUID.randomUUID();
        UUID vendorOwnerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        mockUser(customerId, "customer@danasea.vn", "Nguyễn Khách", Role.CUSTOMER, true, false);
        mockUser(vendorOwnerId, "vendor@danasea.vn", "Trần Đối Tác", Role.VENDOR, true, false);
        mockUser(adminId, "admin@danasea.vn", "Quản Trị Viên", Role.ADMIN, true, false);

        var fixture = createDisputeFixture(customerId, vendorOwnerId, BigDecimal.valueOf(600000));
        UUID disputeId = fixture.disputeId();

        // Resolve hoàn 100%
        ResolveDisputeRequest request = new ResolveDisputeRequest(
                DisputeStatus.RESOLVED_REFUND,
                BigDecimal.valueOf(100.0),
                "Chấp thuận hoàn toàn bộ tiền do tour bị huỷ đột xuất"
        );
        tx.executeWithoutResult(status -> resolveDisputeUseCase.execute(disputeId, request, adminId));

        var dispute = disputeRepository.findById(disputeId).orElseThrow();
        var refund = refundRepository.findBySubOrderId(dispute.getSubOrderId()).stream().findFirst().orElseThrow();

        // Enqueue thêm REFUND_REQUESTED độc lập mô phỏng luồng tiếp nhận hoàn tiền
        tx.executeWithoutResult(status -> {
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.REFUND_REQUESTED,
                    "refund:" + refund.getId() + ":requested",
                    "USER:" + customerId,
                    OffsetDateTime.now(),
                    OffsetDateTime.now(),
                    null,
                    1,
                    Map.of("refundId", refund.getId().toString(), "amount", 600000.0, "subOrderId", dispute.getSubOrderId().toString())
            ));
        });

        // Worker quét hàng đợi
        processPendingEmails();

        var refundDelivery = deliveryRepository.findAll().stream()
                .filter(d -> d.getType() == TransactionalEmailType.REFUND_REQUESTED)
                .findFirst().orElseThrow();

        // Assert bị SKIPPED với mã SUPERSEDED_BY_DISPUTE_RESOLUTION
        assertEquals(EmailDeliveryStatus.SKIPPED, refundDelivery.getStatus());
        assertEquals("SUPERSEDED_BY_DISPUTE_RESOLUTION", refundDelivery.getLastErrorCode());
    }

    // =========================================================================
    // 3. PASSWORD_CHANGED: Cả đổi trực tiếp & Reset qua OTP
    // =========================================================================
    @Test
    @DisplayName("PR 6 - PASSWORD_CHANGED gửi sau khi đổi trực tiếp hoặc reset OTP, ghi giờ VN và hướng dẫn")
    void whenPasswordChanged_sendsNotificationToVerifiedUser_bothDirectAndOtp() {
        UUID userId = UUID.randomUUID();
        String email = "security-user@danasea.vn";
        mockUser(userId, email, "Lê Bảo Mật", Role.CUSTOMER, true, false);

        User domainUser = new User();
        domainUser.setId(userId);
        domainUser.setEmail(email);
        domainUser.setFullName("Lê Bảo Mật");
        domainUser.setPasswordHash(passwordHasher.hash("OldPass123!"));
        domainUser.setRole(Role.CUSTOMER);
        domainUser.setIsEmailVerified(true);
        domainUser.setIsLocked(false);
        domainUser.setSessionVersion(0L);

        // 1. Đổi mật khẩu trực tiếp
        when(accountInternalApi.findUserByEmail(email)).thenReturn(Optional.of(domainUser));

        tx.executeWithoutResult(status -> changePasswordUseCase.execute(email, "OldPass123!", "NewPass456!"));

        // 2. Reset mật khẩu qua OTP
        String otp = "123456";
        String otpHashed = HashUtils.sha256(otp);
        domainUser.setPasswordHash(passwordHasher.hash("NewPass456!"));

        when(accountInternalApi.findUserByEmailForUpdate(email)).thenReturn(Optional.of(domainUser));

        PasswordResetToken token = new PasswordResetToken();
        token.setId(UUID.randomUUID());
        token.setUserId(userId);
        token.setTokenHash(otpHashed);
        token.setFailedAttempts(0);
        token.setExpiresAt(OffsetDateTime.now().plusMinutes(10));

        when(accountInternalApi.findLatestActivePasswordResetToken(userId)).thenReturn(Optional.of(token));

        tx.executeWithoutResult(status -> resetPasswordUseCase.execute(email, otp, "FinalReset789!"));

        // Worker gửi
        processPendingEmails();

        var deliveries = deliveryRepository.findAll().stream()
                .filter(d -> d.getType() == TransactionalEmailType.PASSWORD_CHANGED)
                .toList();

        assertEquals(2, deliveries.size(), "Phải có 2 email PASSWORD_CHANGED (1 direct, 1 otp)");
        assertTrue(deliveries.stream().allMatch(d -> d.getStatus() == EmailDeliveryStatus.ACCEPTED));

        ArgumentCaptor<RenderedEmail> captor = ArgumentCaptor.forClass(RenderedEmail.class);
        verify(sender, atLeastOnce()).send(captor.capture());

        RenderedEmail lastEmail = captor.getAllValues().stream()
                .filter(e -> email.equals(e.to()))
                .reduce((first, second) -> second).orElseThrow();

        // Kiểm tra nội dung: thời điểm, cảnh báo an toàn
        assertTrue(lastEmail.textBody().contains("mật khẩu"), "Nội dung phải nói về thay đổi mật khẩu");
        assertTrue(lastEmail.textBody().contains("không phải bạn") || lastEmail.textBody().contains("liên hệ"),
                "Phải có khuyến cáo bảo mật nếu người nhận không tự thực hiện");
    }

    // =========================================================================
    // 4. ACCOUNT_LOCKED: Gửi tới email đã xác minh, bỏ qua nếu chưa xác minh
    // =========================================================================
    @Test
    @DisplayName("PR 6 - ACCOUNT_LOCKED gửi tới tài khoản bị khóa nếu email đã xác minh, bỏ qua nếu unverified")
    void whenAccountLocked_sendsAlertOnlyIfEmailVerified() {
        UUID verifiedUserId = UUID.randomUUID();
        UUID unverifiedUserId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        // User 1: Email đã xác minh
        mockUser(verifiedUserId, "verified-locked@danasea.vn", "Trần Khóa 1", Role.CUSTOMER, true, false);
        User u1 = new User();
        u1.setId(verifiedUserId);
        u1.setEmail("verified-locked@danasea.vn");
        u1.setFullName("Trần Khóa 1");
        u1.setIsEmailVerified(true);
        u1.setIsLocked(false);
        u1.setRole(Role.CUSTOMER);
        u1.setSessionVersion(0L);
        when(accountInternalApi.findUserById(verifiedUserId)).thenReturn(Optional.of(u1));

        // User 2: Email chưa xác minh
        mockUser(unverifiedUserId, "unverified-locked@danasea.vn", "Trần Khóa 2", Role.CUSTOMER, false, false);
        User u2 = new User();
        u2.setId(unverifiedUserId);
        u2.setEmail("unverified-locked@danasea.vn");
        u2.setFullName("Trần Khóa 2");
        u2.setIsEmailVerified(false);
        u2.setIsLocked(false);
        u2.setRole(Role.CUSTOMER);
        u2.setSessionVersion(0L);
        when(accountInternalApi.findUserById(unverifiedUserId)).thenReturn(Optional.of(u2));

        // Khóa cả hai
        tx.executeWithoutResult(status -> lockUserUseCase.execute(adminId, verifiedUserId, "Vi phạm điều khoản dịch vụ"));
        tx.executeWithoutResult(status -> lockUserUseCase.execute(adminId, unverifiedUserId, "Spam tài khoản"));

        // User sau khi khóa có isLocked = true
        u1.setIsLocked(true);
        u2.setIsLocked(true);
        when(accountInternalApi.findUserById(verifiedUserId)).thenReturn(Optional.of(u1));
        when(accountInternalApi.findUserById(unverifiedUserId)).thenReturn(Optional.of(u2));

        // Worker quét
        processPendingEmails();

        var deliveries = deliveryRepository.findAll().stream()
                .filter(d -> d.getType() == TransactionalEmailType.ACCOUNT_LOCKED)
                .toList();

        assertEquals(2, deliveries.size());

        var verifiedDelivery = deliveries.stream()
                .filter(d -> d.getRecipientKey().equals("USER:" + verifiedUserId))
                .findFirst().orElseThrow();
        var unverifiedDelivery = deliveries.stream()
                .filter(d -> d.getRecipientKey().equals("USER:" + unverifiedUserId))
                .findFirst().orElseThrow();

        // User có verified email được ACCEPTED dù account bị locked (isPermittedWhenAccountLocked == true)
        assertEquals(EmailDeliveryStatus.ACCEPTED, verifiedDelivery.getStatus());

        // User chưa verified email bị SKIPPED (UNVERIFIED_EMAIL)
        assertEquals(EmailDeliveryStatus.SKIPPED, unverifiedDelivery.getStatus());
        assertEquals("UNVERIFIED_EMAIL", unverifiedDelivery.getLastErrorCode());
    }

    // =========================================================================
    // 5. SETTLEMENT_FINALIZED: Gửi bản tóm tắt đối soát cho Vendor Owner
    // =========================================================================
    @Test
    @DisplayName("PR 6 - SETTLEMENT_FINALIZED gửi tóm tắt đối soát, doanh thu, hoa hồng, thực nhận cho đối tác")
    void whenSettlementFinalized_sendsSummaryToVendorOwner() {
        UUID vendorOwnerId = UUID.randomUUID();
        mockUser(vendorOwnerId, "vendor-owner@danasea.vn", "Chủ Đối Tác", Role.VENDOR, true, false);

        UUID vendorId = createVendorFixture(vendorOwnerId);
        UUID settlementId = createSettlementFixture(vendorId, BigDecimal.valueOf(10000000), BigDecimal.valueOf(1000000), BigDecimal.valueOf(9000000));

        // Finalize settlement
        tx.executeWithoutResult(status -> finalizeSettlementUseCase.execute(settlementId));

        // Worker gửi
        processPendingEmails();

        var deliveryOpt = deliveryRepository.findAll().stream()
                .filter(d -> d.getType() == TransactionalEmailType.SETTLEMENT_FINALIZED)
                .findFirst();

        assertTrue(deliveryOpt.isPresent(), "Phải có email SETTLEMENT_FINALIZED");
        assertEquals(EmailDeliveryStatus.ACCEPTED, deliveryOpt.get().getStatus());
        assertEquals("USER:" + vendorOwnerId, deliveryOpt.get().getRecipientKey());

        ArgumentCaptor<RenderedEmail> captor = ArgumentCaptor.forClass(RenderedEmail.class);
        verify(sender, atLeastOnce()).send(captor.capture());

        RenderedEmail email = captor.getAllValues().stream()
                .filter(e -> "vendor-owner@danasea.vn".equals(e.to()))
                .findFirst().orElseThrow();

        // Kiểm tra nội dung: Doanh thu, hoa hồng, thực nhận, link /vendor/revenue?settlementId=
        assertTrue(email.textBody().contains("10.000.000"), "Phải có doanh thu gross 10.000.000 đ");
        assertTrue(email.textBody().contains("1.000.000"), "Phải có phí hoa hồng 1.000.000 đ");
        assertTrue(email.textBody().contains("9.000.000"), "Phải có thực nhận 9.000.000 đ");
        assertTrue(email.textBody().contains("/vendor/revenue?settlementId=" + settlementId), "Phải có link xem chi tiết đối soát");
    }

    // =========================================================================
    // 6. PHÂN QUYỀN API: Đối tác khác không được mở kỳ đối soát qua link
    // =========================================================================
    @Test
    @DisplayName("PR 6 - Đối tác khác không được mở kỳ đối soát của người khác qua link (403/Forbidden)")
    void whenAccessingSettlementDetail_otherVendorIsForbidden() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID otherVendorOwnerId = UUID.randomUUID();

        mockUser(ownerId, "owner@danasea.vn", "Chủ A", Role.VENDOR, true, false);
        mockUser(otherVendorOwnerId, "other@danasea.vn", "Chủ B", Role.VENDOR, true, false);

        UUID vendorA = createVendorFixture(ownerId);
        UUID vendorB = createVendorFixture(otherVendorOwnerId);

        UUID settlementIdA = createSettlementFixture(vendorA, BigDecimal.valueOf(5000000), BigDecimal.valueOf(500000), BigDecimal.valueOf(4500000));

        // Token của Chủ B (thuộc Vendor B)
        String tokenB = createJwt(otherVendorOwnerId, "other@danasea.vn", "VENDOR");

        // Gọi GET /api/vendor/settlements/{id} của Vendor A bằng token B -> Phải bị từ chối
        mockMvc.perform(get("/api/vendor/settlements/" + settlementIdA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // HELPER METHODS & FIXTURES
    // =========================================================================

    private void mockUser(UUID id, String email, String name, Role role, boolean emailVerified, boolean locked) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(name);
        user.setRole(role);
        user.setIsEmailVerified(emailVerified);
        user.setIsLocked(locked);
        user.setPasswordHash("hashed");
        user.setSessionVersion(0L);

        when(accountInternalApi.findUserById(id)).thenReturn(Optional.of(user));
        when(accountInternalApi.findUserByEmail(email)).thenReturn(Optional.of(user));
        when(accountInternalApi.findUserByEmailUncached(email)).thenReturn(Optional.of(user));

        tx.executeWithoutResult(status -> {
            UserJpaEntity entity = userRepository.findById(id).orElseGet(() -> {
                UserJpaEntity u = new UserJpaEntity();
                u.setId(id);
                u.markNew();
                return u;
            });
            entity.setEmail(email);
            entity.setFullName(name);
            entity.setRole(role);
            entity.setIsEmailVerified(emailVerified);
            entity.setIsLocked(locked);
            entity.setPasswordHash("hashed");
            userRepository.save(entity);
        });
    }

    private String createJwt(UUID userId, String email, String role) {
        Authentication auth = new Authentication(
                userId,
                email,
                "hashed",
                role,
                true,
                true,
                "vi",
                0L
        );
        return jwtTokenProvider.generateAccessToken(auth);
    }

    private record DisputeFixture(UUID disputeId, UUID subOrderId, UUID masterOrderId) {}

    private DisputeFixture createDisputeFixture(UUID customerId, UUID vendorOwnerId, BigDecimal amount) {
        return tx.execute(status -> {
            UUID vendorId = createVendorFixture(vendorOwnerId);

            CategoryJpaEntity cat = new CategoryJpaEntity();
            cat.setId(UUID.randomUUID());
            cat.setName("Du thuyền");
            cat.setSlug("du-thuyen-" + UUID.randomUUID());
            cat.setIsActive(true);
            cat.markNew();
            categoryRepository.saveAndFlush(cat);

            ServiceJpaEntity service = new ServiceJpaEntity();
            service.setId(UUID.randomUUID());
            service.markNew();
            service.setVendorId(vendorId);
            service.setCategoryId(cat.getId());
            service.setName("Tour Chèo SUP Mỹ Khê");
            service.setNameEn("My Khe SUP Tour");
            service.setStatus(ServiceStatus.PUBLISHED);
            service.setPrice(amount);
            service.setLocationName("Đà Nẵng");
            service.setAddress("Bãi biển Mỹ Khê");
            service.setLatitude(new BigDecimal("16.0544"));
            service.setLongitude(new BigDecimal("108.2428"));
            service = serviceRepository.save(service);

            ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
            slot.setId(UUID.randomUUID());
            slot.markNew();
            slot.setServiceId(service.getId());
            slot.setDate(LocalDate.now().plusDays(2));
            slot.setStartTime(LocalTime.of(8, 0));
            slot.setEndTime(LocalTime.of(11, 0));
            slot.setCapacity(20);
            slot.setBookedCount(0);
            slot.setStatus(SlotStatus.OPEN);
            slot = serviceSlotRepository.save(slot);

            MasterOrderJpaEntity master = new MasterOrderJpaEntity();
            master.setId(UUID.randomUUID());
            master.markNew();
            master.setCustomerId(customerId);
            master.setTotalAmount(amount);
            master.setDiscountAmount(BigDecimal.ZERO);
            master.setStatus(MasterOrderStatus.PAID);
            master.setPaymentStatus(PaymentOrderStatus.PAID);
            master = masterOrderRepository.save(master);

            SubOrderJpaEntity sub = new SubOrderJpaEntity();
            sub.setId(UUID.randomUUID());
            sub.markNew();
            sub.setMasterOrderId(master.getId());
            sub.setVendorId(vendorId);
            sub.setServiceId(service.getId());
            sub.setSlotId(slot.getId());
            sub.setStatus(SubOrderStatus.CONFIRMED);
            sub.setUnitPrice(amount);
            sub.setQuantity(1);
            sub.setDiscountAmount(BigDecimal.ZERO);
            sub.setFinalAmount(amount);
            sub = subOrderRepository.save(sub);

            DisputeJpaEntity dispute = DisputeJpaEntity.builder()
                    .orderId(master.getId())
                    .subOrderId(sub.getId())
                    .customerId(customerId)
                    .reason(DisputeReason.SERVICE_NOT_AS_DESCRIBED)
                    .description("Khách phàn nàn chất lượng không đúng cam kết")
                    .status(DisputeStatus.OPEN)
                    .build();
            dispute.setId(UUID.randomUUID());
            dispute.markNew();
            dispute = disputeRepository.save(dispute);

            return new DisputeFixture(dispute.getId(), sub.getId(), master.getId());
        });
    }

    private UUID createVendorFixture(UUID ownerUserId) {
        VendorJpaEntity vendor = new VendorJpaEntity();
        vendor.setId(UUID.randomUUID());
        vendor.markNew();
        vendor.setUserId(ownerUserId);
        vendor.setBusinessName("Công Ty Du Lịch Biển Đà Nẵng " + UUID.randomUUID().toString().substring(0, 6));
        vendor.setAddress("123 Võ Nguyên Giáp, Đà Nẵng");
        vendor.setVerificationStatus(VerificationStatus.APPROVED);
        vendor = vendorRepository.save(vendor);
        return vendor.getId();
    }

    private UUID createSettlementFixture(UUID vendorId, BigDecimal gross, BigDecimal commission, BigDecimal net) {
        return tx.execute(status -> {
            SettlementJpaEntity settlement = SettlementJpaEntity.builder()
                    .vendorId(vendorId)
                    .periodStart(LocalDate.now().minusDays(15))
                    .periodEnd(LocalDate.now().minusDays(1))
                    .grossAmount(gross)
                    .commissionAmount(commission)
                    .netPayableAmount(net)
                    .vendorFaultCompensationDeductedAmount(BigDecimal.ZERO)
                    .status(SettlementStatus.PENDING)
                    .generatedAt(OffsetDateTime.now())
                    .build();
            settlement.setId(UUID.randomUUID());
            settlement.markNew();
            settlement = settlementRepository.save(settlement);
            return settlement.getId();
        });
    }
}
