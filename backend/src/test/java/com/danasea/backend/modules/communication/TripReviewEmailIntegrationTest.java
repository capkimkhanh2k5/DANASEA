package com.danasea.backend.modules.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.account.infrastructure.persistence.entities.UserJpaEntity;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.booking.domain.ports.VendorLookupPort;
import com.danasea.backend.modules.checkin.application.usecases.VerifyCheckinUseCase;
import com.danasea.backend.modules.checkin.domain.services.QrTokenSigner;
import com.danasea.backend.modules.checkin.infrastructure.persistence.entities.CheckinTokenJpaEntity;
import com.danasea.backend.modules.checkin.infrastructure.persistence.repositories.JpaCheckinTokenRepository;
import com.danasea.backend.modules.checkin.presentation.dtos.VerifyCheckinRequest;
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
import com.danasea.backend.modules.dispute.domain.models.DisputeReason;
import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.dispute.infrastructure.persistence.entities.DisputeJpaEntity;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.operation.domain.exceptions.InvalidReviewSubOrderStateException;
import com.danasea.backend.modules.operation.infrastructure.persistence.entities.ReviewJpaEntity;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaReviewRepository;
import com.danasea.backend.modules.operation.application.usecases.CreateReviewUseCase;
import com.danasea.backend.modules.operation.presentation.dtos.CreateReviewRequest;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.CategoryJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaCategoryRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class TripReviewEmailIntegrationTest extends BaseSecurityIntegrationTest {

    @DynamicPropertySource
    static void configureReviewEmailProperties(DynamicPropertyRegistry registry) {
        registry.add("app.transactional-email.mode", () -> "capture");
        registry.add("app.transactional-email.worker.enabled", () -> "false");
        registry.add("app.transactional-email.worker.batch-size", () -> "20");
        registry.add("app.transactional-email.enabled-families", () -> "TRIP,BOOKING");
    }

    @Autowired private TransactionalEmailPort transactionalEmailPort;
    @Autowired private TransactionalEmailWorker worker;
    @Autowired private TransactionalEmailProperties properties;
    @Autowired private JpaTransactionalEmailDeliveryRepository deliveryRepository;

    @Autowired private VerifyCheckinUseCase verifyCheckinUseCase;
    @Autowired private CreateReviewUseCase createReviewUseCase;
    @Autowired private JpaCheckinTokenRepository checkinTokenRepository;
    @Autowired private JpaReviewRepository reviewRepository;
    @Autowired private JpaDisputeRepository disputeRepository;
    @Autowired private JpaMasterOrderRepository masterOrderRepository;
    @Autowired private JpaSubOrderRepository subOrderRepository;
    @Autowired private JpaVendorRepository vendorRepository;
    @Autowired private JpaCategoryRepository categoryRepository;
    @Autowired private JpaServiceRepository serviceRepository;
    @Autowired private JpaServiceSlotRepository serviceSlotRepository;
    @Autowired private JpaUserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean(name = "captureTransactionalEmailSender")
    private TransactionalEmailSender sender;

    @MockitoBean private AccountInternalApi accountInternalApi;
    @MockitoBean private VendorLookupPort vendorLookupPort;

    private TransactionTemplate tx;
    private static final String QR_SECRET = "test-checkin-qr-secret-that-is-at-least-256-bits-long";
    private QrTokenSigner qrSigner;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        qrSigner = new QrTokenSigner(QR_SECRET);
        cleanDatabase();
        reset(sender);

        when(sender.send(any(RenderedEmail.class)))
                .thenReturn(EmailSendResult.accepted());

        properties.setMode("capture");
        properties.setEnabledFamilies("TRIP,BOOKING");
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        tx.executeWithoutResult(status -> {
            deliveryRepository.deleteAll();
            reviewRepository.deleteAll();
            disputeRepository.deleteAll();
            checkinTokenRepository.deleteAll();
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
    // 1. CHECK-IN ENQUEUE: Check-in thành công enqueue TRIP_REVIEW_INVITATION dueAt +24h
    // =========================================================================
    @Test
    @DisplayName("PR 7 - Khi khách check-in thành công (CHECKED_IN), enqueue email mời đánh giá sau đúng 24 giờ")
    void whenCheckedIn_enqueuesTripReviewInvitation_dueAfter24Hours() {
        UUID customerId = UUID.randomUUID();
        UUID vendorOwnerId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        mockUser(customerId, "customer-review@danasea.vn", "Nguyễn Khách", Role.CUSTOMER);
        mockUser(vendorOwnerId, "vendor-owner@danasea.vn", "Chủ Đối Tác", Role.VENDOR);
        mockUser(staffId, "staff@danasea.vn", "Nhân Viên Soát Vé", Role.VENDOR);

        var fixture = createCheckinFixture(customerId, vendorOwnerId);
        when(vendorLookupPort.findVendorIdByUserId(staffId)).thenReturn(Optional.of(fixture.vendorId()));

        // Thực hiện check-in
        VerifyCheckinRequest req = new VerifyCheckinRequest(fixture.signedQrToken());
        tx.executeWithoutResult(status -> verifyCheckinUseCase.execute(req, staffId));

        // Kiểm tra email được enqueue
        var deliveries = deliveryRepository.findAll().stream()
                .filter(d -> d.getType() == TransactionalEmailType.TRIP_REVIEW_INVITATION)
                .toList();

        assertEquals(1, deliveries.size(), "Phải có đúng 1 email TRIP_REVIEW_INVITATION được enqueue");
        var delivery = deliveries.get(0);
        assertEquals("USER:" + customerId, delivery.getRecipientKey());
        assertEquals("review:suborder:" + fixture.subOrderId() + ":invite", delivery.getBusinessKey());
        assertEquals(EmailDeliveryStatus.PENDING, delivery.getStatus());

        // Kiểm tra dueAt = checkedInAt + 24h (chênh lệch tối đa vài giây)
        assertTrue(delivery.getDueAt().isAfter(delivery.getOccurredAt().plusHours(23).plusMinutes(59)),
                "dueAt phải được hẹn sau ít nhất 24 giờ kể từ khi check-in");
    }

    // =========================================================================
    // 2. WORKER SEND: Gửi email mời đánh giá sau 24h, kèm link và tên tour
    // =========================================================================
    @Test
    @DisplayName("PR 7 - Worker gửi thư mời đánh giá thành công sau 24h, hiển thị tên dịch vụ và liên kết đánh giá")
    void whenDueAtArrives_workerSendsReviewInvitationSuccessfully() {
        UUID customerId = UUID.randomUUID();
        UUID vendorOwnerId = UUID.randomUUID();
        mockUser(customerId, "customer-review@danasea.vn", "Nguyễn Khách", Role.CUSTOMER);
        mockUser(vendorOwnerId, "vendor-owner@danasea.vn", "Chủ Đối Tác", Role.VENDOR);

        var fixture = createCheckinFixture(customerId, vendorOwnerId);

        // Đánh dấu subOrder là CHECKED_IN
        tx.executeWithoutResult(status -> {
            var subOrder = subOrderRepository.findById(fixture.subOrderId()).orElseThrow();
            subOrder.setStatus(SubOrderStatus.CHECKED_IN);
            subOrder.setCheckedInAt(OffsetDateTime.now().minusHours(25)); // Đã check-in cách đây 25 tiếng
            subOrderRepository.save(subOrder);

            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.TRIP_REVIEW_INVITATION,
                    "review:suborder:" + fixture.subOrderId() + ":invite",
                    "USER:" + customerId,
                    OffsetDateTime.now().minusHours(25),
                    OffsetDateTime.now().minusHours(1), // dueAt đã qua 1 tiếng
                    OffsetDateTime.now().plusDays(13),
                    1,
                    Map.of(
                            "subOrderId", fixture.subOrderId().toString(),
                            "masterOrderId", fixture.masterOrderId().toString(),
                            "serviceId", fixture.serviceId().toString()
                    )
            ));
        });

        // Worker quét hàng đợi
        processPendingEmails();

        var delivery = deliveryRepository.findAll().stream()
                .filter(d -> d.getType() == TransactionalEmailType.TRIP_REVIEW_INVITATION)
                .findFirst().orElseThrow();

        assertEquals(EmailDeliveryStatus.ACCEPTED, delivery.getStatus());

        ArgumentCaptor<RenderedEmail> captor = ArgumentCaptor.forClass(RenderedEmail.class);
        verify(sender, atLeastOnce()).send(captor.capture());

        RenderedEmail sent = captor.getValue();
        assertEquals("customer-review@danasea.vn", sent.to());
        assertTrue(sent.subject().contains("đánh giá") || sent.subject().contains("review"),
                "Tiêu đề phải mời chia sẻ đánh giá");
        assertTrue(sent.textBody().contains("Tour Lặn Ngắm San Hô"),
                "Nội dung phải có tên dịch vụ đã trải nghiệm");
        assertTrue(sent.textBody().contains("/profile/orders"),
                "Nội dung phải chứa link mở đánh giá trên website");
    }

    // =========================================================================
    // 3. SKIPPED KHI ĐÃ ĐÁNH GIÁ: Nếu khách đã đánh giá trước, bỏ qua ALREADY_REVIEWED
    // =========================================================================
    @Test
    @DisplayName("PR 7 - Nếu khách hàng đã đánh giá dịch vụ trước khi worker gửi, đánh dấu SKIPPED / ALREADY_REVIEWED")
    void whenCustomerAlreadyReviewed_workerSkipsWithAlreadyReviewed() {
        UUID customerId = UUID.randomUUID();
        UUID vendorOwnerId = UUID.randomUUID();
        mockUser(customerId, "customer-review@danasea.vn", "Nguyễn Khách", Role.CUSTOMER);
        mockUser(vendorOwnerId, "vendor-owner@danasea.vn", "Chủ Đối Tác", Role.VENDOR);

        var fixture = createCheckinFixture(customerId, vendorOwnerId);

        // Khách hàng đã đánh giá trước
        tx.executeWithoutResult(status -> {
            var subOrder = subOrderRepository.findById(fixture.subOrderId()).orElseThrow();
            subOrder.setStatus(SubOrderStatus.CHECKED_IN);
            subOrder.setCheckedInAt(OffsetDateTime.now().minusHours(25));
            subOrderRepository.save(subOrder);

            ReviewJpaEntity review = ReviewJpaEntity.builder()
                    .subOrderId(fixture.subOrderId())
                    .customerId(customerId)
                    .vendorId(fixture.vendorId())
                    .serviceId(fixture.serviceId())
                    .rating((short) 5)
                    .comment("Dịch vụ tuyệt vời!")
                    .isVisible(true)
                    .isFlagged(false)
                    .build();
            review.setId(UUID.randomUUID());
            review.setCreatedAt(OffsetDateTime.now());
            reviewRepository.saveAndFlush(review);

            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.TRIP_REVIEW_INVITATION,
                    "review:suborder:" + fixture.subOrderId() + ":invite",
                    "USER:" + customerId,
                    OffsetDateTime.now().minusHours(25),
                    OffsetDateTime.now().minusHours(1),
                    OffsetDateTime.now().plusDays(13),
                    1,
                    Map.of("subOrderId", fixture.subOrderId().toString(), "masterOrderId", fixture.masterOrderId().toString(), "serviceId", fixture.serviceId().toString())
            ));
        });

        // Worker quét
        processPendingEmails();

        var delivery = deliveryRepository.findAll().stream()
                .filter(d -> d.getType() == TransactionalEmailType.TRIP_REVIEW_INVITATION)
                .findFirst().orElseThrow();

        assertEquals(EmailDeliveryStatus.SKIPPED, delivery.getStatus());
        assertEquals("ALREADY_REVIEWED", delivery.getLastErrorCode());
    }

    // =========================================================================
    // 4. SKIPPED KHI CÓ TRANH CHẤP ĐANG MỞ: OPEN_DISPUTE
    // =========================================================================
    @Test
    @DisplayName("PR 7 - Nếu đơn con có tranh chấp đang mở chưa giải quyết, worker đánh dấu SKIPPED / OPEN_DISPUTE")
    void whenSubOrderHasOpenDispute_workerSkipsWithOpenDispute() {
        UUID customerId = UUID.randomUUID();
        UUID vendorOwnerId = UUID.randomUUID();
        mockUser(customerId, "customer-review@danasea.vn", "Nguyễn Khách", Role.CUSTOMER);
        mockUser(vendorOwnerId, "vendor-owner@danasea.vn", "Chủ Đối Tác", Role.VENDOR);

        var fixture = createCheckinFixture(customerId, vendorOwnerId);

        // Tạo tranh chấp PENDING
        tx.executeWithoutResult(status -> {
            var subOrder = subOrderRepository.findById(fixture.subOrderId()).orElseThrow();
            subOrder.setStatus(SubOrderStatus.CHECKED_IN);
            subOrder.setCheckedInAt(OffsetDateTime.now().minusHours(25));
            subOrderRepository.save(subOrder);

            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(UUID.randomUUID());
            dispute.setSubOrderId(fixture.subOrderId());
            dispute.setCustomerId(customerId);
            dispute.setReason(DisputeReason.SERVICE_NOT_AS_DESCRIBED);
            dispute.setStatus(DisputeStatus.OPEN);
            dispute.setDescription("Chất lượng dịch vụ không đúng cam kết");
            dispute.setCreatedAt(OffsetDateTime.now());
            dispute.markNew();
            disputeRepository.saveAndFlush(dispute);

            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.TRIP_REVIEW_INVITATION,
                    "review:suborder:" + fixture.subOrderId() + ":invite",
                    "USER:" + customerId,
                    OffsetDateTime.now().minusHours(25),
                    OffsetDateTime.now().minusHours(1),
                    OffsetDateTime.now().plusDays(13),
                    1,
                    Map.of("subOrderId", fixture.subOrderId().toString(), "masterOrderId", fixture.masterOrderId().toString(), "serviceId", fixture.serviceId().toString())
            ));
        });

        // Worker quét
        processPendingEmails();

        var delivery = deliveryRepository.findAll().stream()
                .filter(d -> d.getType() == TransactionalEmailType.TRIP_REVIEW_INVITATION)
                .findFirst().orElseThrow();

        assertEquals(EmailDeliveryStatus.SKIPPED, delivery.getStatus());
        assertEquals("OPEN_DISPUTE", delivery.getLastErrorCode());
    }

    // =========================================================================
    // 5. CHO PHÉP ĐÁNH GIÁ KHI ĐÃ CHECKED_IN
    // =========================================================================
    @Test
    @DisplayName("PR 7 - Khách hàng đã check-in (CHECKED_IN) được phép gửi đánh giá thành công")
    void whenCheckedIn_customerCanCreateReviewSuccessfully() {
        UUID customerId = UUID.randomUUID();
        UUID vendorOwnerId = UUID.randomUUID();
        mockUser(customerId, "customer-review@danasea.vn", "Nguyễn Khách", Role.CUSTOMER);
        mockUser(vendorOwnerId, "vendor-owner@danasea.vn", "Chủ Đối Tác", Role.VENDOR);

        var fixture = createCheckinFixture(customerId, vendorOwnerId);

        tx.executeWithoutResult(status -> {
            var subOrder = subOrderRepository.findById(fixture.subOrderId()).orElseThrow();
            subOrder.setStatus(SubOrderStatus.CHECKED_IN);
            subOrder.setCheckedInAt(OffsetDateTime.now().minusHours(24));
            subOrderRepository.save(subOrder);
        });

        // Gửi đánh giá
        CreateReviewRequest req = new CreateReviewRequest((short) 5, "Tour rất đẹp và an toàn!", List.of());
        var res = tx.execute(status -> createReviewUseCase.execute(fixture.subOrderId(), req, customerId));

        assertTrue(res != null && res.id() != null, "Đánh giá phải được tạo thành công khi subOrder là CHECKED_IN");
        assertTrue(reviewRepository.existsBySubOrderId(fixture.subOrderId()));
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private void mockUser(UUID id, String email, String name, Role role) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(name);
        user.setRole(role);
        user.setIsEmailVerified(true);
        user.setIsLocked(false);
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
            entity.setIsEmailVerified(true);
            entity.setIsLocked(false);
            entity.setPasswordHash("hashed");
            userRepository.save(entity);
        });
    }

    private record CheckinFixture(UUID vendorId, UUID serviceId, UUID masterOrderId, UUID subOrderId, String signedQrToken) {}

    private CheckinFixture createCheckinFixture(UUID customerId, UUID vendorOwnerId) {
        return tx.execute(status -> {
            VendorJpaEntity vendor = new VendorJpaEntity();
            vendor.setId(UUID.randomUUID());
            vendor.setUserId(vendorOwnerId);
            vendor.setBusinessName("Công Ty Biển Xanh");
            vendor.setVerificationStatus(VerificationStatus.APPROVED);
            vendor.markNew();
            vendorRepository.saveAndFlush(vendor);

            CategoryJpaEntity cat = new CategoryJpaEntity();
            cat.setId(UUID.randomUUID());
            cat.setName("Lặn Biển");
            cat.setSlug("lan-bien-" + UUID.randomUUID());
            cat.setIsActive(true);
            cat.markNew();
            categoryRepository.saveAndFlush(cat);

            ServiceJpaEntity svc = new ServiceJpaEntity();
            svc.setId(UUID.randomUUID());
            svc.setVendorId(vendor.getId());
            svc.setCategoryId(cat.getId());
            svc.setName("Tour Lặn Ngắm San Hô");
            svc.setNameEn("Coral Diving Tour");
            svc.setPrice(BigDecimal.valueOf(800000));
            svc.setAddress("Bán đảo Sơn Trà, Đà Nẵng");
            svc.setLocationName("Sơn Trà");
            svc.markNew();
            serviceRepository.saveAndFlush(svc);

            ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
            slot.setId(UUID.randomUUID());
            slot.setServiceId(svc.getId());
            slot.setDate(LocalDate.now());
            LocalTime nowTime = LocalTime.now();
            slot.setStartTime(nowTime.minusHours(1));
            slot.setEndTime(nowTime.plusHours(2));
            slot.setCapacity(20);
            slot.setStatus(SlotStatus.OPEN);
            slot.markNew();
            serviceSlotRepository.saveAndFlush(slot);

            MasterOrderJpaEntity master = new MasterOrderJpaEntity();
            master.setId(UUID.randomUUID());
            master.setCustomerId(customerId);
            master.setStatus(MasterOrderStatus.PAID);
            master.setPaymentStatus(PaymentOrderStatus.PAID);
            master.setTotalAmount(BigDecimal.valueOf(800000));
            master.setCreatedAt(OffsetDateTime.now());
            master.setUpdatedAt(OffsetDateTime.now());
            master.markNew();
            masterOrderRepository.saveAndFlush(master);

            SubOrderJpaEntity sub = new SubOrderJpaEntity();
            sub.setId(UUID.randomUUID());
            sub.setMasterOrderId(master.getId());
            sub.setVendorId(vendor.getId());
            sub.setServiceId(svc.getId());
            sub.setSlotId(slot.getId());
            sub.setStatus(SubOrderStatus.CONFIRMED);
            sub.setQuantity(1);
            sub.setUnitPrice(BigDecimal.valueOf(800000));
            sub.setCreatedAt(OffsetDateTime.now());
            sub.setUpdatedAt(OffsetDateTime.now());
            sub.markNew();
            subOrderRepository.saveAndFlush(sub);

            // Sinh mã QR token
            UUID tokenId = UUID.randomUUID();
            OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(4);
            String token = qrSigner.generateToken(sub.getId(), expiresAt);
            String tokenHash = QrTokenSigner.hashToken(token);

            CheckinTokenJpaEntity checkinToken = CheckinTokenJpaEntity.builder()
                    .subOrderId(sub.getId())
                    .qrTokenHash(tokenHash)
                    .expiresAt(expiresAt)
                    .build();
            checkinToken.setId(tokenId);
            checkinToken.markNew();
            checkinTokenRepository.saveAndFlush(checkinToken);

            return new CheckinFixture(vendor.getId(), svc.getId(), master.getId(), sub.getId(), token);
        });
    }
}