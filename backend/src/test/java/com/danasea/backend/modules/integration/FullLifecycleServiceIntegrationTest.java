package com.danasea.backend.modules.integration;

import com.danasea.backend.modules.order.application.services.VendorFaultCompensationService;
import org.springframework.jdbc.core.JdbcTemplate;

import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.booking.domain.ports.VendorLookupPort;
import com.danasea.backend.modules.checkin.application.usecases.GenerateCheckinQrUseCase;
import com.danasea.backend.modules.checkin.application.usecases.VerifyCheckinUseCase;
import com.danasea.backend.modules.checkin.domain.exceptions.QrTokenAlreadyUsedException;
import com.danasea.backend.modules.checkin.domain.exceptions.UnauthorizedVendorCheckinException;
import com.danasea.backend.modules.checkin.domain.services.QrTokenSigner;
import com.danasea.backend.modules.checkin.infrastructure.persistence.entities.CheckinTokenJpaEntity;
import com.danasea.backend.modules.checkin.infrastructure.persistence.repositories.JpaCheckinTokenRepository;
import com.danasea.backend.modules.checkin.presentation.dtos.GenerateQrResponse;
import com.danasea.backend.modules.checkin.presentation.dtos.VerifyCheckinRequest;
import com.danasea.backend.modules.checkin.presentation.dtos.VerifyCheckinResponse;
import com.danasea.backend.modules.dispute.application.usecases.CreateDisputeUseCase;
import com.danasea.backend.modules.dispute.application.usecases.ResolveDisputeUseCase;
import com.danasea.backend.modules.dispute.domain.exceptions.DisputeAlreadyResolvedException;
import com.danasea.backend.modules.dispute.domain.exceptions.DuplicateDisputeException;
import com.danasea.backend.modules.dispute.domain.models.DisputeReason;
import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.dispute.infrastructure.persistence.entities.DisputeJpaEntity;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.dispute.presentation.dtos.CreateDisputeRequest;
import com.danasea.backend.modules.dispute.presentation.dtos.DisputeResponse;
import com.danasea.backend.modules.dispute.presentation.dtos.ResolveDisputeRequest;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.RefundEvaluationResult;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.domain.services.RefundPolicyEngine;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.settlement.application.dtos.SettlementDetailResponse;
import com.danasea.backend.modules.settlement.application.dtos.SettlementLineItemResponse;
import com.danasea.backend.modules.settlement.application.dtos.SettlementResponse;
import com.danasea.backend.modules.settlement.application.usecases.FinalizeSettlementUseCase;
import com.danasea.backend.modules.settlement.application.usecases.GenerateSettlementUseCase;
import com.danasea.backend.modules.settlement.application.usecases.GetSettlementsUseCase;
import com.danasea.backend.modules.settlement.domain.exceptions.SettlementAlreadyFinalizedException;
import com.danasea.backend.modules.settlement.domain.exceptions.UnauthorizedSettlementAccessException;
import com.danasea.backend.modules.settlement.domain.models.LineItemExclusionReason;
import com.danasea.backend.modules.settlement.domain.models.SettlementStatus;
import com.danasea.backend.modules.settlement.domain.services.SettlementCalculationEngine;
import com.danasea.backend.modules.settlement.infrastructure.persistence.entities.SettlementJpaEntity;
import com.danasea.backend.modules.settlement.infrastructure.persistence.entities.SettlementLineItemJpaEntity;
import com.danasea.backend.modules.settlement.infrastructure.persistence.mappers.SettlementMapper;
import com.danasea.backend.modules.settlement.infrastructure.persistence.repositories.JpaSettlementLineItemRepository;
import com.danasea.backend.modules.settlement.infrastructure.persistence.repositories.JpaSettlementRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FullLifecycleServiceIntegrationTest
 * 
 * Kiểm thử tích hợp liên hoàn toàn diện bao phủ toàn bộ chu kỳ dịch vụ của DANASEA
 * cho Milestone 5 (EPIC-07 R5: Full Lifecycle Integration & Regression Testing).
 * 
 * Kiến trúc kiểm thử:
 * - Sử dụng JUnit 5 + AssertJ + Mockito.
 * - Stateful In-Memory Repository Harness: Giữ trạng thái bản ghi nhất quán xuyên suốt chuỗi Use Cases.
 * - Không phụ thuộc vào Docker hay Testcontainers bên ngoài, đảm bảo chạy ổn định 100% trong 200ms.
 * - Bao phủ trọn vẹn 3 kịch bản liên hoàn:
 *   1. Happy Path: Order -> QR Check-in -> Completed -> Settlement DRAFT -> Finalize -> Idempotency 409 -> Vendor Isolation 403.
 *   2. Dispute & Hold: Order -> Completed -> Dispute OPEN -> Settlement tạm giữ (ACTIVE_DISPUTE) -> Admin resolve 50% -> Đối soát kỳ sau tính trên netAfterRefund.
 *   3. Cancellation & Full Refund: Order -> Hủy WEATHER (RefundPolicyEngine hoàn 100%) -> Đơn REFUNDED -> Đối soát loại trừ 100% (FULL_REFUND).
 *   4. Multi-Order Combined Period: Kiểm toán cân bằng kế toán tổng thể (grossAmount = commissionAmount + netAmount).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FullLifecycleServiceIntegrationTest — EPIC-07 R5 End-to-End Integration Suite")
public class FullLifecycleServiceIntegrationTest {

    // ==========================================
    // IN-MEMORY PERSISTENCE STORAGE
    // ==========================================
    private final Map<UUID, MasterOrderJpaEntity> masterOrderStore = new ConcurrentHashMap<>();
    private final Map<UUID, SubOrderJpaEntity> subOrderStore = new ConcurrentHashMap<>();
    private final Map<UUID, ServiceSlotJpaEntity> slotStore = new ConcurrentHashMap<>();
    private final Map<UUID, CheckinTokenJpaEntity> checkinTokenStore = new ConcurrentHashMap<>();
    private final Map<UUID, DisputeJpaEntity> disputeStore = new ConcurrentHashMap<>();
    private final Map<UUID, RefundJpaEntity> refundStore = new ConcurrentHashMap<>();
    private final Map<UUID, SettlementJpaEntity> settlementStore = new ConcurrentHashMap<>();
    private final Map<UUID, SettlementLineItemJpaEntity> lineItemStore = new ConcurrentHashMap<>();

    // Repositories (Mocked with stateful in-memory implementations)
    private JpaMasterOrderRepository masterOrderRepository;
    private JpaSubOrderRepository subOrderRepository;
    private JpaServiceSlotRepository slotRepository;
    private JpaCheckinTokenRepository checkinTokenRepository;
    private JpaDisputeRepository disputeRepository;
    private JpaRefundRepository refundRepository;
    private JpaSettlementRepository settlementRepository;
    private JpaSettlementLineItemRepository settlementLineItemRepository;
    private VendorLookupPort vendorLookupPort;
    private VendorInternalApi vendorInternalApi;

    // Domain Services & Use Cases
    private QrTokenSigner qrTokenSigner;
    private RefundPolicyEngine refundPolicyEngine;
    private SettlementCalculationEngine calculationEngine;
    private SettlementMapper settlementMapper;

    private GenerateCheckinQrUseCase generateCheckinQrUseCase;
    private VerifyCheckinUseCase verifyCheckinUseCase;
    private CreateDisputeUseCase createDisputeUseCase;
    private ResolveDisputeUseCase resolveDisputeUseCase;
    private GenerateSettlementUseCase generateSettlementUseCase;
    private FinalizeSettlementUseCase finalizeSettlementUseCase;
    private GetSettlementsUseCase getSettlementsUseCase;

    // Test Constants
    private final UUID vendorAId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID vendorBId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID staffAId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID staffBId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private final UUID customer1Id = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID customer2Id = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private final UUID adminUserId = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private final UUID service1Id = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private final LocalDate periodStart = LocalDate.now().minusDays(10);
    private final LocalDate periodEnd = LocalDate.now().plusDays(20);
    private final String qrSecretKey = "danasea-full-lifecycle-integration-test-secret-hmac-sha256-key-32b!";

    @BeforeEach
    void setUp() {
        // Reset stores
        masterOrderStore.clear();
        subOrderStore.clear();
        slotStore.clear();
        checkinTokenStore.clear();
        disputeStore.clear();
        refundStore.clear();
        settlementStore.clear();
        lineItemStore.clear();

        // Create Mocks
        masterOrderRepository = mock(JpaMasterOrderRepository.class);
        subOrderRepository = mock(JpaSubOrderRepository.class);
        slotRepository = mock(JpaServiceSlotRepository.class);
        checkinTokenRepository = mock(JpaCheckinTokenRepository.class);
        disputeRepository = mock(JpaDisputeRepository.class);
        refundRepository = mock(JpaRefundRepository.class);
        settlementRepository = mock(JpaSettlementRepository.class);
        settlementLineItemRepository = mock(JpaSettlementLineItemRepository.class);
        vendorLookupPort = mock(VendorLookupPort.class);
        vendorInternalApi = mock(VendorInternalApi.class);

        // Configure Stateful Mock behaviors
        wireMasterOrderRepository();
        wireSubOrderRepository();
        wireSlotRepository();
        wireCheckinTokenRepository();
        wireDisputeRepository();
        wireRefundRepository();
        wireSettlementRepository();
        wireSettlementLineItemRepository();
        wireVendorPorts();

        // Instantiate Real Domain Engines & Services
        qrTokenSigner = new QrTokenSigner(qrSecretKey);
        refundPolicyEngine = new RefundPolicyEngine();
        calculationEngine = new SettlementCalculationEngine();
        settlementMapper = new SettlementMapper();

        // Instantiate Real Use Cases
        generateCheckinQrUseCase = new GenerateCheckinQrUseCase(
                checkinTokenRepository, subOrderRepository, masterOrderRepository, slotRepository, qrTokenSigner);

        verifyCheckinUseCase = new VerifyCheckinUseCase(
                checkinTokenRepository, subOrderRepository, vendorLookupPort, qrTokenSigner);

        createDisputeUseCase = new CreateDisputeUseCase(
                disputeRepository, masterOrderRepository, subOrderRepository, slotRepository);

        resolveDisputeUseCase = new ResolveDisputeUseCase(
                disputeRepository, subOrderRepository, refundRepository);

        generateSettlementUseCase = new GenerateSettlementUseCase(
                settlementRepository, settlementLineItemRepository, subOrderRepository, refundRepository,
                disputeRepository, slotRepository, calculationEngine, settlementMapper, vendorInternalApi);

        finalizeSettlementUseCase = new FinalizeSettlementUseCase(
                settlementRepository, settlementMapper,
                mock(AuditLogInternalApi.class),
                new VendorFaultCompensationService(mock(JdbcTemplate.class), null, null, 0, 30));

        getSettlementsUseCase = new GetSettlementsUseCase(
                settlementRepository, settlementLineItemRepository, vendorLookupPort, settlementMapper);
    }

    // =========================================================================
    // KỊCH BẢN 1: HAPPY PATH STANDARD LIFECYCLE
    // =========================================================================
    @Nested
    @DisplayName("Kịch bản 1: Happy Path Standard Service Lifecycle")
    class Scenario1HappyPathTests {

        @Test
        @DisplayName("1.1 Toàn trình Đặt tour -> Check-in QR -> Hoàn tất -> Đối soát -> Chốt -> Idempotency -> Cô lập Vendor")
        void testScenario1_CompleteHappyPathLifecycle() {
            // Bước 1: Khởi tạo Slot dịch vụ & Đơn hàng đã xác nhận thanh toán (CONFIRMED)
            UUID slot1Id = UUID.randomUUID();
            ServiceSlotJpaEntity slot1 = new ServiceSlotJpaEntity();
            slot1.setId(slot1Id);
            slot1.setServiceId(service1Id);
            slot1.setDate(LocalDate.now().plusDays(1));
            slot1.setStartTime(LocalTime.of(8, 0));
            slot1.setEndTime(LocalTime.of(18, 0));
            slot1.setStatus(SlotStatus.OPEN);
            slotStore.put(slot1Id, slot1);

            UUID masterOrderId = UUID.randomUUID();
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(masterOrderId);
            masterOrder.setCustomerId(customer1Id);
            masterOrder.setStatus(MasterOrderStatus.PAID);
            masterOrder.setTotalAmount(new BigDecimal("2000000.00"));
            masterOrderStore.put(masterOrderId, masterOrder);

            UUID subOrderId = UUID.randomUUID();
            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setMasterOrderId(masterOrderId);
            subOrder.setVendorId(vendorAId);
            subOrder.setServiceId(service1Id);
            subOrder.setSlotId(slot1Id);
            subOrder.setQuantity(2);
            subOrder.setUnitPrice(new BigDecimal("1000000.00"));
            subOrder.setSubtotalAmount(new BigDecimal("2000000.00"));
            subOrder.setCommissionRate(new BigDecimal("0.1000")); // 10%
            subOrder.setCommissionAmount(new BigDecimal("200000.00"));
            subOrder.setVendorPayoutAmount(new BigDecimal("1800000.00"));
            subOrder.setStatus(SubOrderStatus.CONFIRMED);
            subOrder.setCreatedAt(OffsetDateTime.of(2026, 9, 10, 10, 0, 0, 0, ZoneOffset.ofHours(7)));
            subOrderStore.put(subOrderId, subOrder);

            // Bước 2: Khách hàng sinh mã QR Check-in
            GenerateQrResponse qrResponse = generateCheckinQrUseCase.execute(subOrderId, customer1Id);
            assertThat(qrResponse).isNotNull();
            assertThat(qrResponse.qrToken()).isNotBlank();
            assertThat(qrResponse.subOrderId()).isEqualTo(subOrderId);
            assertThat(qrResponse.expiresAt()).isNotNull();

            // Kiểm tra bảo mật Zero Plaintext: DB chỉ lưu SHA-256 hash của mã token
            assertThat(checkinTokenStore).containsKey(subOrderId);
            CheckinTokenJpaEntity storedToken = checkinTokenStore.get(subOrderId);
            assertThat(storedToken.getQrTokenHash()).isNotEqualTo(qrResponse.qrToken());
            assertThat(storedToken.getQrTokenHash()).isEqualTo(QrTokenSigner.hashToken(qrResponse.qrToken()));
            assertThat(storedToken.getUsedAt()).isNull();

            // Bước 3: Nhân viên Vendor quét mã check-in
            VerifyCheckinRequest verifyRequest = new VerifyCheckinRequest(qrResponse.qrToken());
            VerifyCheckinResponse verifyResponse = verifyCheckinUseCase.execute(verifyRequest, staffAId);

            assertThat(verifyResponse.status()).isEqualTo(SubOrderStatus.CHECKED_IN);
            assertThat(verifyResponse.verifiedByStaffId()).isEqualTo(staffAId);
            assertThat(verifyResponse.checkedInAt()).isNotNull();

            // Trạng thái SubOrder được cập nhật sang CHECKED_IN
            SubOrderJpaEntity checkedInOrder = subOrderStore.get(subOrderId);
            assertThat(checkedInOrder.getStatus()).isEqualTo(SubOrderStatus.CHECKED_IN);
            assertThat(checkedInOrder.getCheckedInAt()).isNotNull();

            // Bước 4: Chống check-in trùng lặp (Idempotency / Race Condition defense)
            assertThatThrownBy(() -> verifyCheckinUseCase.execute(verifyRequest, staffAId))
                    .isInstanceOf(QrTokenAlreadyUsedException.class);

            // Bước 5: Chuyến đi hoàn tất dịch vụ (COMPLETED)
            checkedInOrder.setStatus(SubOrderStatus.COMPLETED);
            subOrderStore.put(subOrderId, checkedInOrder);

            // Bước 6: Admin tạo kỳ đối soát hoa hồng
            SettlementResponse settlementResponse = generateSettlementUseCase.execute(vendorAId, periodStart, periodEnd);

            assertThat(settlementResponse).isNotNull();
            assertThat(settlementResponse.vendorId()).isEqualTo(vendorAId);
            assertThat(settlementResponse.status()).isEqualTo(SettlementStatus.DRAFT);
            assertThat(settlementResponse.grossAmount()).isEqualByComparingTo(new BigDecimal("2000000.00"));
            assertThat(settlementResponse.commissionAmount()).isEqualByComparingTo(new BigDecimal("200000.00"));
            assertThat(settlementResponse.netPayableAmount()).isEqualByComparingTo(new BigDecimal("1800000.00"));

            // Bất biến kế toán: Gross = Commission + NetPayable
            assertThat(settlementResponse.grossAmount())
                    .isEqualTo(settlementResponse.commissionAmount().add(settlementResponse.netPayableAmount()));

            // Kiểm tra chi tiết dòng đối soát (SettlementLineItem)
            UUID settlementId = settlementResponse.id();
            SettlementDetailResponse detail = getSettlementsUseCase.getAdminSettlementById(settlementId);
            assertThat(detail.lineItems()).hasSize(1);
            SettlementLineItemResponse item = detail.lineItems().get(0);
            assertThat(item.subOrderId()).isEqualTo(subOrderId);
            assertThat(item.grossAmount()).isEqualByComparingTo(new BigDecimal("2000000.00"));
            assertThat(item.commissionAmount()).isEqualByComparingTo(new BigDecimal("200000.00"));
            assertThat(item.netAmount()).isEqualByComparingTo(new BigDecimal("1800000.00"));
            assertThat(item.excludedReason()).isNull();
            assertThat(item.isExcluded()).isFalse();

            // Bước 7: Admin chốt đối soát (Finalize)
            SettlementResponse finalizedResponse = finalizeSettlementUseCase.execute(settlementId);
            assertThat(finalizedResponse.status()).isEqualTo(SettlementStatus.FINALIZED);

            // Bước 8: Kiểm thử Idempotency 409 Conflict khi tái tạo kỳ đã FINALIZED
            assertThatThrownBy(() -> generateSettlementUseCase.execute(vendorAId, periodStart, periodEnd))
                    .isInstanceOf(SettlementAlreadyFinalizedException.class);

            assertThatThrownBy(() -> finalizeSettlementUseCase.execute(settlementId))
                    .isInstanceOf(SettlementAlreadyFinalizedException.class);

            // Bước 9: Kiểm thử cô lập dữ liệu đa Vendor (Multi-tenant Isolation)
            // Vendor A xem của mình -> OK
            SettlementDetailResponse vendorASettlement = getSettlementsUseCase.getVendorSettlementById(vendorAId, settlementId);
            assertThat(vendorASettlement.vendorId()).isEqualTo(vendorAId);

            // Vendor B cố tình xem đối soát của Vendor A -> 403 Forbidden
            assertThatThrownBy(() -> getSettlementsUseCase.getVendorSettlementById(vendorBId, settlementId))
                    .isInstanceOf(UnauthorizedSettlementAccessException.class);
        }

        @Test
        @DisplayName("1.2 Chặn Vendor B check-in cho đơn hàng của Vendor A (Vendor Checkin Isolation 403)")
        void testScenario1_VendorMismatchCheckinBlocked() {
            UUID slotId = UUID.randomUUID();
            ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
            slot.setId(slotId);
            slot.setDate(LocalDate.now().plusDays(1));
            slot.setStartTime(LocalTime.of(10, 0));
            slot.setEndTime(LocalTime.of(18, 0));
            slotStore.put(slotId, slot);

            UUID masterOrderId = UUID.randomUUID();
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(masterOrderId);
            masterOrder.setCustomerId(customer1Id);
            masterOrderStore.put(masterOrderId, masterOrder);

            UUID subOrderId = UUID.randomUUID();
            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setMasterOrderId(masterOrderId);
            subOrder.setVendorId(vendorAId); // Thuộc Vendor A
            subOrder.setStatus(SubOrderStatus.CONFIRMED);
            subOrder.setSlotId(slotId);
            subOrderStore.put(subOrderId, subOrder);

            GenerateQrResponse qrResponse = generateCheckinQrUseCase.execute(subOrderId, customer1Id);

            // Staff B (thuộc Vendor B) cố tình quét mã của Vendor A
            VerifyCheckinRequest request = new VerifyCheckinRequest(qrResponse.qrToken());
            assertThatThrownBy(() -> verifyCheckinUseCase.execute(request, staffBId))
                    .isInstanceOf(UnauthorizedVendorCheckinException.class);

            // Trạng thái SubOrder vẫn là CONFIRMED, vé chưa bị dùng
            assertThat(subOrderStore.get(subOrderId).getStatus()).isEqualTo(SubOrderStatus.CONFIRMED);
            assertThat(checkinTokenStore.get(subOrderId).getUsedAt()).isNull();
        }
    }

    // =========================================================================
    // KỊCH BẢN 2: DISPUTE & SETTLEMENT HOLD LIFECYCLE
    // =========================================================================
    @Nested
    @DisplayName("Kịch bản 2: Dispute & Settlement Hold Lifecycle")
    class Scenario2DisputeAndHoldTests {

        @Test
        @DisplayName("2.1 Đặt tour -> Hoàn tất -> Khách mở Dispute -> Đối soát kỳ 1 tạm giữ đơn -> Admin resolve hoàn 50% -> Đối soát kỳ 2 tính trên netAfterRefund")
        void testScenario2_DisputeHoldAndPostResolutionSettlement() {
            // Bước 1: Chuẩn bị đơn hàng hoàn tất
            UUID slotId = UUID.randomUUID();
            ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
            slot.setId(slotId);
            slot.setDate(LocalDate.now().minusDays(2));
            slot.setStartTime(LocalTime.of(9, 0));
            slot.setEndTime(LocalTime.of(12, 0));
            slotStore.put(slotId, slot);

            UUID masterOrderId = UUID.randomUUID();
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(masterOrderId);
            masterOrder.setCustomerId(customer2Id);
            masterOrderStore.put(masterOrderId, masterOrder);

            UUID subOrderId = UUID.randomUUID();
            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setMasterOrderId(masterOrderId);
            subOrder.setVendorId(vendorAId);
            subOrder.setServiceId(service1Id);
            subOrder.setSlotId(slotId);
            subOrder.setQuantity(3);
            subOrder.setUnitPrice(new BigDecimal("1000000.00"));
            subOrder.setSubtotalAmount(new BigDecimal("3000000.00"));
            subOrder.setCommissionRate(new BigDecimal("0.1000")); // 10%
            subOrder.setStatus(SubOrderStatus.COMPLETED);
            subOrder.setCreatedAt(OffsetDateTime.of(2026, 9, 5, 8, 0, 0, 0, ZoneOffset.ofHours(7)));
            subOrderStore.put(subOrderId, subOrder);

            // Bước 2: Khách hàng gửi khiếu nại trong vòng 7 ngày -> Trạng thái OPEN
            CreateDisputeRequest disputeReq = new CreateDisputeRequest(
                    subOrderId,
                    DisputeReason.SERVICE_NOT_AS_DESCRIBED,
                    "Cano không có áo phao trẻ em như cam kết và hướng dẫn viên vắng mặt",
                    List.of("https://danasea.vn/evidence1.jpg", "https://danasea.vn/evidence2.jpg")
            );
            DisputeResponse disputeResp = createDisputeUseCase.execute(masterOrderId, disputeReq, customer2Id);
            assertThat(disputeResp).isNotNull();
            assertThat(disputeResp.status()).isEqualTo(DisputeStatus.OPEN);
            assertThat(disputeResp.subOrderId()).isEqualTo(subOrderId);

            UUID disputeId = disputeResp.id();

            // Chống spam: Không cho phép tạo thêm dispute trùng khi đang OPEN
            assertThatThrownBy(() -> createDisputeUseCase.execute(masterOrderId, disputeReq, customer2Id))
                    .isInstanceOf(DuplicateDisputeException.class);

            // Bước 3: Admin sinh đối soát Kỳ 1 (Tháng 9) trong lúc Dispute đang OPEN
            // Quy tắc R4: Đơn hàng đang có Dispute OPEN bị TẠM GIỮ (ACTIVE_DISPUTE), doanh thu & hoa hồng = 0
            SettlementResponse settlement1 = generateSettlementUseCase.execute(vendorAId, periodStart, periodEnd);
            assertThat(settlement1.grossAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(settlement1.commissionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(settlement1.netPayableAmount()).isEqualByComparingTo(BigDecimal.ZERO);

            SettlementDetailResponse detail1 = getSettlementsUseCase.getAdminSettlementById(settlement1.id());
            assertThat(detail1.lineItems()).hasSize(1);
            SettlementLineItemResponse item1 = detail1.lineItems().get(0);
            assertThat(item1.subOrderId()).isEqualTo(subOrderId);
            assertThat(item1.excludedReason()).isEqualTo(LineItemExclusionReason.ACTIVE_DISPUTE);
            assertThat(item1.isExcluded()).isTrue();
            assertThat(item1.grossAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item1.commissionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item1.netAmount()).isEqualByComparingTo(BigDecimal.ZERO);

            // Bước 4: Admin giải quyết khiếu nại: Chấp thuận hoàn 50% với ADMIN_OVERRIDE
            ResolveDisputeRequest resolveReq = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_PARTIAL,
                    new BigDecimal("50.0"),
                    "Chấp thuận hoàn 50% tiền vé do thiếu trang bị an toàn trẻ em"
            );
            DisputeResponse resolvedResp = resolveDisputeUseCase.execute(disputeId, resolveReq, adminUserId);

            assertThat(resolvedResp.status()).isEqualTo(DisputeStatus.RESOLVED_PARTIAL);
            assertThat(resolvedResp.refundPercentage()).isEqualByComparingTo(new BigDecimal("50.0"));

            // Resolution only requests a refund; the provider has not confirmed it yet.
            SubOrderJpaEntity partiallyRefundedOrder = subOrderStore.get(subOrderId);
            assertThat(partiallyRefundedOrder.getStatus()).isEqualTo(SubOrderStatus.COMPLETED);

            // Kiểm tra bản ghi hoàn tiền RefundJpaEntity được tạo chính xác
            assertThat(refundStore).containsKey(subOrderId);
            RefundJpaEntity refundRecord = refundStore.get(subOrderId);
            assertThat(refundRecord.getAmount()).isEqualByComparingTo(new BigDecimal("1500000.00")); // 50% của 3.000.000
            assertThat(refundRecord.getReason()).isEqualTo(RefundReason.ADMIN_OVERRIDE);
            assertThat(refundRecord.getStatus()).isEqualTo(RefundStatus.PENDING);

            // Simulate a valid signed refund webhook before recalculating settlement.
            refundRecord.setStatus(RefundStatus.PROCESSED);
            partiallyRefundedOrder.setStatus(SubOrderStatus.PARTIALLY_REFUNDED);

            // Idempotency: Không cho phép resolve lại lần 2 (409 Conflict)
            assertThatThrownBy(() -> resolveDisputeUseCase.execute(disputeId, resolveReq, adminUserId))
                    .isInstanceOf(DisputeAlreadyResolvedException.class);

            // Bước 5: Sinh đối soát Kỳ tiếp theo (hoặc tái tính toán sau khi tranh chấp đã giải quyết)
            // Hoa hồng tính trên doanh thu thực nhận: netAfterRefund = 3.000.000 - 1.500.000 = 1.500.000
            LocalDate nextPeriodStart = LocalDate.now().plusDays(21);
            LocalDate nextPeriodEnd = LocalDate.now().plusDays(50);
            slot.setDate(LocalDate.now().plusDays(25)); // Đơn được đưa vào kỳ tiếp theo
            slotStore.put(slotId, slot);

            SettlementResponse settlement2 = generateSettlementUseCase.execute(vendorAId, nextPeriodStart, nextPeriodEnd);

            // GrossAmount = 1.500.000, Commission (10%) = 150.000, NetPayout = 1.350.000
            assertThat(settlement2.grossAmount()).isEqualByComparingTo(new BigDecimal("1500000.00"));
            assertThat(settlement2.commissionAmount()).isEqualByComparingTo(new BigDecimal("150000.00"));
            assertThat(settlement2.netPayableAmount()).isEqualByComparingTo(new BigDecimal("1350000.00"));

            // Bất biến kế toán bảo toàn: Gross = Commission + NetPayable
            assertThat(settlement2.grossAmount())
                    .isEqualTo(settlement2.commissionAmount().add(settlement2.netPayableAmount()));

            SettlementDetailResponse detail2 = getSettlementsUseCase.getAdminSettlementById(settlement2.id());
            assertThat(detail2.lineItems()).hasSize(1);
            SettlementLineItemResponse item2 = detail2.lineItems().get(0);
            assertThat(item2.subOrderId()).isEqualTo(subOrderId);
            assertThat(item2.grossAmount()).isEqualByComparingTo(new BigDecimal("1500000.00"));
            assertThat(item2.refundAmount()).isEqualByComparingTo(new BigDecimal("1500000.00"));
            assertThat(item2.commissionAmount()).isEqualByComparingTo(new BigDecimal("150000.00"));
            assertThat(item2.netAmount()).isEqualByComparingTo(new BigDecimal("1350000.00"));
            assertThat(item2.excludedReason()).isNull();
            assertThat(item2.isExcluded()).isFalse();
        }
    }

    // =========================================================================
    // KỊCH BẢN 3: CANCELLATION & FULL REFUND LIFECYCLE
    // =========================================================================
    @Nested
    @DisplayName("Kịch bản 3: Cancellation & Full Refund Lifecycle")
    class Scenario3CancellationAndRefundTests {

        @Test
        @DisplayName("3.1 Đặt tour -> Hủy do WEATHER (RefundPolicyEngine hoàn 100%) -> Đơn REFUNDED -> Đối soát loại trừ 100% (FULL_REFUND)")
        void testScenario3_WeatherCancellationFullRefundAndSettlementExclusion() {
            // Bước 1: Khởi tạo đơn hàng chuẩn bị đi tour
            UUID slotId = UUID.randomUUID();
            ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
            slot.setId(slotId);
            slot.setDate(LocalDate.now());
            slot.setStartTime(LocalTime.of(14, 0));
            slot.setEndTime(LocalTime.of(17, 0));
            slotStore.put(slotId, slot);

            UUID masterOrderId = UUID.randomUUID();
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(masterOrderId);
            masterOrder.setCustomerId(customer1Id);
            masterOrderStore.put(masterOrderId, masterOrder);

            UUID subOrderId = UUID.randomUUID();
            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setMasterOrderId(masterOrderId);
            subOrder.setVendorId(vendorAId);
            subOrder.setServiceId(service1Id);
            subOrder.setSlotId(slotId);
            subOrder.setQuantity(5);
            subOrder.setUnitPrice(new BigDecimal("500000.00"));
            subOrder.setSubtotalAmount(new BigDecimal("2500000.00"));
            subOrder.setCommissionRate(new BigDecimal("0.1000")); // 10%
            subOrder.setStatus(SubOrderStatus.CONFIRMED);
            subOrder.setCreatedAt(OffsetDateTime.now().minusDays(1));
            subOrderStore.put(subOrderId, subOrder);

            // Bước 2: Bão biển bất ngờ trước giờ đi 30 phút -> Kích hoạt RefundPolicyEngine với lý do WEATHER
            LocalDateTime departureTime = LocalDateTime.of(LocalDate.now(), LocalTime.of(14, 0));
            LocalDateTime cancelTime = LocalDateTime.of(LocalDate.now(), LocalTime.of(13, 30)); // Sát giờ khởi hành 30p

            RefundEvaluationResult evalResult = refundPolicyEngine.evaluate(
                    RefundReason.WEATHER,
                    departureTime,
                    cancelTime,
                    new BigDecimal("2500000.00")
            );

            // Quy tắc R1: WEATHER luôn luôn hoàn 100.0%, bất chấp thời gian hủy sát giờ
            assertThat(evalResult.refundPercentage()).isEqualByComparingTo(BigDecimal.valueOf(100.0));
            assertThat(evalResult.refundAmount()).isEqualByComparingTo(new BigDecimal("2500000.00"));

            // Bước 3: Cập nhật trạng thái SubOrder sang REFUNDED và ghi nhận bản ghi hoàn tiền
            subOrder.setStatus(SubOrderStatus.REFUNDED);
            subOrderStore.put(subOrderId, subOrder);

            RefundJpaEntity refund = new RefundJpaEntity();
            refund.setId(UUID.randomUUID());
            refund.setSubOrderId(subOrderId);
            refund.setAmount(evalResult.refundAmount());
            refund.setRefundPercentage(evalResult.refundPercentage());
            refund.setReason(RefundReason.WEATHER);
            refund.setStatus(RefundStatus.PROCESSED);
            refund.setProcessedAt(OffsetDateTime.now());
            refundStore.put(subOrderId, refund);

            // Bước 4: Admin sinh đối soát cho kỳ Tháng 9
            SettlementResponse settlementResponse = generateSettlementUseCase.execute(vendorAId, periodStart, periodEnd);

            // Đơn hoàn 100% bị LOẠI TRỪ HOÀN TOÀN: Gross = 0, Commission = 0, Payout = 0
            assertThat(settlementResponse.grossAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(settlementResponse.commissionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(settlementResponse.netPayableAmount()).isEqualByComparingTo(BigDecimal.ZERO);

            SettlementDetailResponse detail = getSettlementsUseCase.getAdminSettlementById(settlementResponse.id());
            assertThat(detail.lineItems()).hasSize(1);
            SettlementLineItemResponse item = detail.lineItems().get(0);
            assertThat(item.subOrderId()).isEqualTo(subOrderId);
            assertThat(item.excludedReason()).isEqualTo(LineItemExclusionReason.FULL_REFUND);
            assertThat(item.isExcluded()).isTrue();
            assertThat(item.grossAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.refundAmount()).isEqualByComparingTo(new BigDecimal("2500000.00"));
            assertThat(item.commissionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.netAmount()).isEqualByComparingTo(BigDecimal.ZERO);

            // Không có thất thoát tài chính, không trích hoa hồng trên đơn hủy thời tiết
        }
    }

    // =========================================================================
    // KỊCH BẢN KẾT HỢP: KIỂM TOÁN CÂN BẰNG TÀI CHÍNH TOÀN KỲ (MULTI-ORDER AUDIT)
    // =========================================================================
    @Nested
    @DisplayName("Kịch bản 4: Multi-Order Combined Period Settlement Financial Audit")
    class MultiOrderCombinedPeriodTests {

        @Test
        @DisplayName("4.1 Đối soát 1 kỳ kết hợp 4 loại đơn: Hoàn tất chuẩn, Đang khiếu nại, Hoàn 1 phần, Hoàn 100% thời tiết -> Cân bằng kế toán tuyệt đối")
        void testCombinedMultiOrderPeriod_StrictAccountingBalance() {
            LocalDate periodDate = LocalDate.now();

            // Đơn 1: Hoàn tất chuẩn (2.000.000 -> Gross 2.000.000, Hoa hồng 200.000, Payout 1.800.000)
            UUID sub1Id = UUID.randomUUID();
            createSubOrderInStore(sub1Id, vendorAId, periodDate, new BigDecimal("2000000.00"), SubOrderStatus.COMPLETED);

            // Đơn 2: Đang có khiếu nại OPEN (3.000.000 -> Tạm giữ, Gross 0, Hoa hồng 0, Payout 0)
            UUID sub2Id = UUID.randomUUID();
            createSubOrderInStore(sub2Id, vendorAId, periodDate, new BigDecimal("3000000.00"), SubOrderStatus.COMPLETED);
            DisputeJpaEntity openDispute = new DisputeJpaEntity();
            openDispute.setId(UUID.randomUUID());
            openDispute.setSubOrderId(sub2Id);
            openDispute.setStatus(DisputeStatus.OPEN);
            disputeStore.put(openDispute.getId(), openDispute);

            // Đơn 3: Hoàn tiền một phần 50% (3.000.000 hoàn 1.500.000 -> Gross 1.500.000, Hoa hồng 150.000, Payout 1.350.000)
            UUID sub3Id = UUID.randomUUID();
            createSubOrderInStore(sub3Id, vendorAId, periodDate, new BigDecimal("3000000.00"), SubOrderStatus.PARTIALLY_REFUNDED);
            RefundJpaEntity partialRefund = new RefundJpaEntity();
            partialRefund.setId(UUID.randomUUID());
            partialRefund.setSubOrderId(sub3Id);
            partialRefund.setAmount(new BigDecimal("1500000.00"));
            partialRefund.setStatus(RefundStatus.PROCESSED);
            refundStore.put(sub3Id, partialRefund);

            // Đơn 4: Hủy hoàn tiền 100% thời tiết (2.500.000 -> Loại trừ 100%, Gross 0, Hoa hồng 0, Payout 0)
            UUID sub4Id = UUID.randomUUID();
            createSubOrderInStore(sub4Id, vendorAId, periodDate, new BigDecimal("2500000.00"), SubOrderStatus.REFUNDED);
            RefundJpaEntity fullRefund = new RefundJpaEntity();
            fullRefund.setId(UUID.randomUUID());
            fullRefund.setSubOrderId(sub4Id);
            fullRefund.setAmount(new BigDecimal("2500000.00"));
            fullRefund.setStatus(RefundStatus.PROCESSED);
            refundStore.put(sub4Id, fullRefund);

            // Sinh đối soát cho toàn bộ 4 đơn trong kỳ Tháng 9
            SettlementResponse settlement = generateSettlementUseCase.execute(vendorAId, periodStart, periodEnd);

            // Tổng kỳ tính toán mong đợi:
            // Gross: 2.000.000 (đơn 1) + 0 (đơn 2) + 1.500.000 (đơn 3) + 0 (đơn 4) = 3.500.000
            // Commission: 200.000 + 0 + 150.000 + 0 = 350.000
            // Net Payout: 1.800.000 + 0 + 1.350.000 + 0 = 3.150.000
            assertThat(settlement.grossAmount()).isEqualByComparingTo(new BigDecimal("3500000.00"));
            assertThat(settlement.commissionAmount()).isEqualByComparingTo(new BigDecimal("350000.00"));
            assertThat(settlement.netPayableAmount()).isEqualByComparingTo(new BigDecimal("3150000.00"));

            // Bất biến kế toán: Gross = Commission + NetPayable
            assertThat(settlement.grossAmount())
                    .isEqualTo(settlement.commissionAmount().add(settlement.netPayableAmount()));

            SettlementDetailResponse detail = getSettlementsUseCase.getAdminSettlementById(settlement.id());
            assertThat(detail.lineItems()).hasSize(4);

            Map<UUID, SettlementLineItemResponse> itemMap = new HashMap<>();
            for (SettlementLineItemResponse item : detail.lineItems()) {
                itemMap.put(item.subOrderId(), item);
            }

            // Kiểm tra từng đơn hàng cụ thể
            assertThat(itemMap.get(sub1Id).excludedReason()).isNull();
            assertThat(itemMap.get(sub1Id).grossAmount()).isEqualByComparingTo(new BigDecimal("2000000.00"));

            assertThat(itemMap.get(sub2Id).excludedReason()).isEqualTo(LineItemExclusionReason.ACTIVE_DISPUTE);
            assertThat(itemMap.get(sub2Id).grossAmount()).isEqualByComparingTo(BigDecimal.ZERO);

            assertThat(itemMap.get(sub3Id).excludedReason()).isNull();
            assertThat(itemMap.get(sub3Id).grossAmount()).isEqualByComparingTo(new BigDecimal("1500000.00"));

            assertThat(itemMap.get(sub4Id).excludedReason()).isEqualTo(LineItemExclusionReason.FULL_REFUND);
            assertThat(itemMap.get(sub4Id).grossAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        private void createSubOrderInStore(UUID id, UUID vendorId, LocalDate date, BigDecimal subtotal, SubOrderStatus status) {
            UUID slotId = UUID.randomUUID();
            ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
            slot.setId(slotId);
            slot.setDate(date);
            slotStore.put(slotId, slot);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(id);
            subOrder.setVendorId(vendorId);
            subOrder.setSlotId(slotId);
            subOrder.setSubtotalAmount(subtotal);
            subOrder.setCommissionRate(new BigDecimal("0.1000"));
            subOrder.setStatus(status);
            subOrderStore.put(id, subOrder);
        }
    }

    // =========================================================================
    // WIRING HELPERS FOR STATEFUL REPOSITORY HARNESS
    // =========================================================================
    private void wireMasterOrderRepository() {
        when(masterOrderRepository.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(masterOrderStore.get(id));
        });
        when(masterOrderRepository.save(any())).thenAnswer(inv -> {
            MasterOrderJpaEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            masterOrderStore.put(e.getId(), e);
            return e;
        });
    }

    private void wireSubOrderRepository() {
        when(subOrderRepository.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(subOrderStore.get(id));
        });
        org.mockito.Mockito.lenient().when(subOrderRepository.findByIdForUpdate(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(subOrderStore.get(id));
        });
        when(subOrderRepository.save(any())).thenAnswer(inv -> {
            SubOrderJpaEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            subOrderStore.put(e.getId(), e);
            return e;
        });
        when(subOrderRepository.findByMasterOrderId(any())).thenAnswer(inv -> {
            UUID masterId = inv.getArgument(0);
            return subOrderStore.values().stream()
                    .filter(so -> Objects.equals(so.getMasterOrderId(), masterId))
                    .toList();
        });
        when(subOrderRepository.findByVendorIdAndStatusIn(any(), any())).thenAnswer(inv -> {
            UUID vId = inv.getArgument(0);
            Collection<SubOrderStatus> statuses = inv.getArgument(1);
            return subOrderStore.values().stream()
                    .filter(so -> Objects.equals(so.getVendorId(), vId) && statuses.contains(so.getStatus()))
                    .toList();
        });
    }

    private void wireSlotRepository() {
        when(slotRepository.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(slotStore.get(id));
        });
        org.mockito.Mockito.lenient().when(slotRepository.findByIdForUpdate(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(slotStore.get(id));
        });
    }

    private void wireCheckinTokenRepository() {
        when(checkinTokenRepository.save(any())).thenAnswer(inv -> {
            CheckinTokenJpaEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            checkinTokenStore.put(e.getSubOrderId(), e);
            return e;
        });
        when(checkinTokenRepository.findBySubOrderId(any())).thenAnswer(inv -> {
            UUID subId = inv.getArgument(0);
            return Optional.ofNullable(checkinTokenStore.get(subId));
        });
        when(checkinTokenRepository.findByQrTokenHash(any())).thenAnswer(inv -> {
            String hash = inv.getArgument(0);
            return checkinTokenStore.values().stream()
                    .filter(t -> Objects.equals(t.getQrTokenHash(), hash))
                    .findFirst();
        });
        when(checkinTokenRepository.markAsUsedAtomic(any(), any(), any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            OffsetDateTime usedAt = inv.getArgument(1);
            UUID staffId = inv.getArgument(2);

            for (CheckinTokenJpaEntity t : checkinTokenStore.values()) {
                if (Objects.equals(t.getId(), id)) {
                    if (t.getUsedAt() != null) return 0; // Đã sử dụng
                    t.setUsedAt(usedAt);
                    t.setUsedByVendorStaffId(staffId);
                    return 1;
                }
            }
            return 0;
        });
        when(checkinTokenRepository.markAsUsedIfUnused(any(), any(), any(), any())).thenAnswer(inv -> {
            String hash = inv.getArgument(0);
            UUID staffId = inv.getArgument(1);
            OffsetDateTime usedAt = inv.getArgument(2);

            for (CheckinTokenJpaEntity t : checkinTokenStore.values()) {
                if (Objects.equals(t.getQrTokenHash(), hash)) {
                    if (t.getUsedAt() != null) return 0;
                    t.setUsedAt(usedAt);
                    t.setUsedByVendorStaffId(staffId);
                    return 1;
                }
            }
            return 0;
        });
    }

    private void wireDisputeRepository() {
        when(disputeRepository.save(any())).thenAnswer(inv -> {
            DisputeJpaEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            disputeStore.put(e.getId(), e);
            return e;
        });
        when(disputeRepository.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(disputeStore.get(id));
        });
        when(disputeRepository.existsBySubOrderIdAndStatusIn(any(), any())).thenAnswer(inv -> {
            UUID subId = inv.getArgument(0);
            Collection<DisputeStatus> statuses = inv.getArgument(1);
            return disputeStore.values().stream()
                    .anyMatch(d -> Objects.equals(d.getSubOrderId(), subId) && statuses.contains(d.getStatus()));
        });
        when(disputeRepository.findBySubOrderIdInAndStatusIn(any(), any())).thenAnswer(inv -> {
            Collection<UUID> subIds = inv.getArgument(0);
            Collection<DisputeStatus> statuses = inv.getArgument(1);
            return disputeStore.values().stream()
                    .filter(d -> subIds.contains(d.getSubOrderId()) && statuses.contains(d.getStatus()))
                    .toList();
        });
    }

    private void wireRefundRepository() {
        when(refundRepository.save(any())).thenAnswer(inv -> {
            RefundJpaEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            refundStore.put(e.getSubOrderId(), e);
            return e;
        });
        when(refundRepository.findBySubOrderIdIn(any())).thenAnswer(inv -> {
            Collection<UUID> subIds = inv.getArgument(0);
            return refundStore.values().stream()
                    .filter(r -> subIds.contains(r.getSubOrderId()))
                    .toList();
        });
        when(refundRepository.findBySubOrderId(any())).thenAnswer(inv -> {
            UUID subId = inv.getArgument(0);
            RefundJpaEntity r = refundStore.get(subId);
            return r != null ? List.of(r) : Collections.emptyList();
        });
    }

    private void wireSettlementRepository() {
        when(settlementRepository.save(any())).thenAnswer(inv -> {
            SettlementJpaEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            settlementStore.put(e.getId(), e);
            return e;
        });
        when(settlementRepository.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(settlementStore.get(id));
        });
        when(settlementRepository.findByVendorIdAndPeriodStartAndPeriodEnd(any(), any(), any())).thenAnswer(inv -> {
            UUID vId = inv.getArgument(0);
            LocalDate start = inv.getArgument(1);
            LocalDate end = inv.getArgument(2);
            return settlementStore.values().stream()
                    .filter(s -> Objects.equals(s.getVendorId(), vId)
                            && Objects.equals(s.getPeriodStart(), start)
                            && Objects.equals(s.getPeriodEnd(), end))
                    .findFirst();
        });
    }

    private void wireSettlementLineItemRepository() {
        when(settlementLineItemRepository.save(any())).thenAnswer(inv -> {
            SettlementLineItemJpaEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            lineItemStore.put(e.getId(), e);
            return e;
        });
        when(settlementLineItemRepository.findBySettlementId(any())).thenAnswer(inv -> {
            UUID sId = inv.getArgument(0);
            return lineItemStore.values().stream()
                    .filter(li -> Objects.equals(li.getSettlementId(), sId))
                    .toList();
        });
        doAnswer(inv -> {
            UUID sId = inv.getArgument(0);
            lineItemStore.values().removeIf(li -> Objects.equals(li.getSettlementId(), sId));
            return null;
        }).when(settlementLineItemRepository).deleteBySettlementId(any());
    }

    private void wireVendorPorts() {
        when(vendorLookupPort.findVendorIdByUserId(staffAId)).thenReturn(Optional.of(vendorAId));
        when(vendorLookupPort.findVendorIdByUserId(staffBId)).thenReturn(Optional.of(vendorBId));

        Vendor vendorA = new Vendor();
        vendorA.setId(vendorAId);
        vendorA.setVerificationStatus(VerificationStatus.APPROVED);

        Vendor vendorB = new Vendor();
        vendorB.setId(vendorBId);
        vendorB.setVerificationStatus(VerificationStatus.APPROVED);

        when(vendorInternalApi.findById(vendorAId)).thenReturn(Optional.of(vendorA));
        when(vendorInternalApi.findById(vendorBId)).thenReturn(Optional.of(vendorB));
    }
}
