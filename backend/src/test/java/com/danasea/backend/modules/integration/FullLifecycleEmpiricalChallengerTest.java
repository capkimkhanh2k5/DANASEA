package com.danasea.backend.modules.integration;

import com.danasea.backend.modules.booking.domain.ports.VendorLookupPort;
import com.danasea.backend.modules.checkin.domain.services.QrTokenSigner;
import com.danasea.backend.modules.checkin.infrastructure.persistence.entities.CheckinTokenJpaEntity;
import com.danasea.backend.modules.checkin.infrastructure.persistence.repositories.JpaCheckinTokenRepository;
import com.danasea.backend.modules.dispute.application.services.DisputeWindowService;
import com.danasea.backend.modules.dispute.application.usecases.CreateDisputeUseCase;
import com.danasea.backend.modules.dispute.application.usecases.ResolveDisputeUseCase;
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
import com.danasea.backend.modules.settlement.application.usecases.GenerateSettlementUseCase;
import com.danasea.backend.modules.settlement.application.usecases.GetSettlementsUseCase;
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
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FullLifecycleEmpiricalChallengerTest
 * 
 * Kiểm thử thực nghiệm đối kháng độc lập (Empirical Challenger Suite) cho Milestone 5.
 * Chuyên sâu vào các tương tác phức tạp, các bất biến kế toán và các điều kiện biên:
 * 1. Dispute OPEN / UNDER_REVIEW Hold: Không chi trả, gross = 0, commission = 0, net = 0.
 * 2. Dispute RESOLVED_REJECTED: Mở khóa đơn hàng, đối soát đủ 100% doanh thu và hoa hồng.
 * 3. Dispute RESOLVED_PARTIAL: Tính đúng hoa hồng trên netAfterRefund với các mức tỷ lệ khác nhau.
 * 4. Hủy do thời tiết (WEATHER): Hoàn 100%, loại trừ hoàn toàn (FULL_REFUND).
 * 5. Bất biến kế toán: grossAmount == commissionAmount + netPayableAmount trong mọi kịch bản.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FullLifecycleEmpiricalChallengerTest — Challenger 2 Stress & Invariant Verification")
public class FullLifecycleEmpiricalChallengerTest {

    private final Map<UUID, MasterOrderJpaEntity> masterOrderStore = new ConcurrentHashMap<>();
    private final Map<UUID, SubOrderJpaEntity> subOrderStore = new ConcurrentHashMap<>();
    private final Map<UUID, ServiceSlotJpaEntity> slotStore = new ConcurrentHashMap<>();
    private final Map<UUID, CheckinTokenJpaEntity> checkinTokenStore = new ConcurrentHashMap<>();
    private final Map<UUID, DisputeJpaEntity> disputeStore = new ConcurrentHashMap<>();
    private final Map<UUID, RefundJpaEntity> refundStore = new ConcurrentHashMap<>();
    private final Map<UUID, SettlementJpaEntity> settlementStore = new ConcurrentHashMap<>();
    private final Map<UUID, SettlementLineItemJpaEntity> lineItemStore = new ConcurrentHashMap<>();

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

    private RefundPolicyEngine refundPolicyEngine;
    private SettlementCalculationEngine calculationEngine;
    private SettlementMapper settlementMapper;

    private CreateDisputeUseCase createDisputeUseCase;
    private ResolveDisputeUseCase resolveDisputeUseCase;
    private GenerateSettlementUseCase generateSettlementUseCase;
    private GetSettlementsUseCase getSettlementsUseCase;

    private final UUID vendorId = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private final UUID customerId = UUID.fromString("66666666-7777-8888-9999-000000000000");
    private final UUID adminUserId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private final UUID serviceId = UUID.fromString("77777777-8888-9999-aaaa-bbbbbbbbbbbb");

    private final LocalDate periodStart = LocalDate.now().minusDays(15);
    private final LocalDate periodEnd = LocalDate.now().plusDays(15);

    @BeforeEach
    void setUp() {
        masterOrderStore.clear();
        subOrderStore.clear();
        slotStore.clear();
        checkinTokenStore.clear();
        disputeStore.clear();
        refundStore.clear();
        settlementStore.clear();
        lineItemStore.clear();

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

        wireRepositories();

        refundPolicyEngine = new RefundPolicyEngine();
        calculationEngine = new SettlementCalculationEngine();
        settlementMapper = new SettlementMapper();

        createDisputeUseCase = new CreateDisputeUseCase(
                disputeRepository, masterOrderRepository, subOrderRepository,
                new DisputeWindowService(slotRepository, Clock.systemDefaultZone()));

        resolveDisputeUseCase = new ResolveDisputeUseCase(
                disputeRepository, subOrderRepository, refundRepository);

        generateSettlementUseCase = new GenerateSettlementUseCase(
                settlementRepository, settlementLineItemRepository, subOrderRepository, refundRepository,
                disputeRepository, slotRepository, calculationEngine, settlementMapper, vendorInternalApi);

        getSettlementsUseCase = new GetSettlementsUseCase(
                settlementRepository, settlementLineItemRepository, vendorLookupPort, settlementMapper);
    }

    @Nested
    @DisplayName("1. Đối kháng tương tác Dispute Hold (OPEN / UNDER_REVIEW) vs Settlement Run")
    class DisputeHoldTests {

        @Test
        @DisplayName("1.1 Khiếu nại OPEN: SubOrder COMPLETED bị giữ lại (ACTIVE_DISPUTE, gross=0, commission=0, net=0)")
        void testDisputeOpen_IsHeldInSettlement() {
            UUID subId = UUID.randomUUID();
            createOrderWithSlot(subId, new BigDecimal("5000000.00"), new BigDecimal("0.1000"), SubOrderStatus.COMPLETED);

            // Mở dispute
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(UUID.randomUUID());
            dispute.setSubOrderId(subId);
            dispute.setStatus(DisputeStatus.OPEN);
            disputeStore.put(dispute.getId(), dispute);

            // Chạy settlement
            SettlementResponse response = generateSettlementUseCase.execute(vendorId, periodStart, periodEnd);

            assertThat(response.grossAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.commissionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.netPayableAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.grossAmount())
                    .isEqualTo(response.commissionAmount().add(response.netPayableAmount()));

            SettlementDetailResponse detail = getSettlementsUseCase.getAdminSettlementById(response.id());
            assertThat(detail.lineItems()).hasSize(1);
            SettlementLineItemResponse item = detail.lineItems().get(0);
            assertThat(item.subOrderId()).isEqualTo(subId);
            assertThat(item.excludedReason()).isEqualTo(LineItemExclusionReason.ACTIVE_DISPUTE);
            assertThat(item.isExcluded()).isTrue();
            assertThat(item.grossAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.commissionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.netAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("1.2 Khiếu nại UNDER_REVIEW: Cũng bị giữ lại (ACTIVE_DISPUTE, gross=0, commission=0, net=0)")
        void testDisputeUnderReview_IsHeldInSettlement() {
            UUID subId = UUID.randomUUID();
            createOrderWithSlot(subId, new BigDecimal("4000000.00"), new BigDecimal("0.1200"), SubOrderStatus.COMPLETED);

            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(UUID.randomUUID());
            dispute.setSubOrderId(subId);
            dispute.setStatus(DisputeStatus.UNDER_REVIEW);
            disputeStore.put(dispute.getId(), dispute);

            SettlementResponse response = generateSettlementUseCase.execute(vendorId, periodStart, periodEnd);

            assertThat(response.grossAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.commissionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.netPayableAmount()).isEqualByComparingTo(BigDecimal.ZERO);

            SettlementDetailResponse detail = getSettlementsUseCase.getAdminSettlementById(response.id());
            SettlementLineItemResponse item = detail.lineItems().get(0);
            assertThat(item.excludedReason()).isEqualTo(LineItemExclusionReason.ACTIVE_DISPUTE);
            assertThat(item.grossAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("1.3 Khiếu nại RESOLVED_REJECTED: Đơn hàng được mở khóa, đối soát 100% doanh thu")
        void testDisputeResolvedRejected_UnfreezesForSettlement() {
            UUID subId = UUID.randomUUID();
            createOrderWithSlot(subId, new BigDecimal("4000000.00"), new BigDecimal("0.1000"), SubOrderStatus.COMPLETED);

            UUID masterOrderId = subOrderStore.get(subId).getMasterOrderId();

            // Khách tạo dispute
            CreateDisputeRequest req = new CreateDisputeRequest(
                    subId, DisputeReason.OTHER, "Yêu cầu không chính đáng", Collections.emptyList());
            DisputeResponse disputeResp = createDisputeUseCase.execute(masterOrderId, req, customerId);

            // Admin bác bỏ khiếu nại (RESOLVED_REJECTED)
            ResolveDisputeRequest resolveReq = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_REJECTED, null, "Khiếu nại không có căn cứ");
            resolveDisputeUseCase.execute(disputeResp.id(), resolveReq, adminUserId);

            // Đơn hàng vẫn là COMPLETED, dispute không còn active
            assertThat(subOrderStore.get(subId).getStatus()).isEqualTo(SubOrderStatus.COMPLETED);
            assertThat(disputeStore.get(disputeResp.id()).getStatus()).isEqualTo(DisputeStatus.RESOLVED_REJECTED);

            // Chạy settlement: Đơn hàng được thanh toán bình thường
            SettlementResponse response = generateSettlementUseCase.execute(vendorId, periodStart, periodEnd);

            assertThat(response.grossAmount()).isEqualByComparingTo(new BigDecimal("4000000.00"));
            assertThat(response.commissionAmount()).isEqualByComparingTo(new BigDecimal("400000.00"));
            assertThat(response.netPayableAmount()).isEqualByComparingTo(new BigDecimal("3600000.00"));

            // Bất biến kế toán: Gross == Commission + NetPayable
            assertThat(response.grossAmount())
                    .isEqualTo(response.commissionAmount().add(response.netPayableAmount()));

            SettlementDetailResponse detail = getSettlementsUseCase.getAdminSettlementById(response.id());
            SettlementLineItemResponse item = detail.lineItems().get(0);
            assertThat(item.excludedReason()).isNull();
            assertThat(item.isExcluded()).isFalse();
        }
    }

    @Nested
    @DisplayName("2. Đối kháng Dispute RESOLVED_PARTIAL: Tính đúng hoa hồng trên netAfterRefund")
    class DisputeResolvedPartialTests {

        @Test
        @DisplayName("2.1 Admin giải quyết hoàn 35%: Hoa hồng 15% tính trên netAfterRefund (65%), không tính trên giá gốc")
        void testDisputePartialRefund_CommissionCalculatedOnNetAfterRefund() {
            UUID subId = UUID.randomUUID();
            BigDecimal subtotal = new BigDecimal("10000000.00"); // 10 triệu
            BigDecimal commRate = new BigDecimal("0.1500");     // 15% hoa hồng
            createOrderWithSlot(subId, subtotal, commRate, SubOrderStatus.COMPLETED);

            UUID masterOrderId = subOrderStore.get(subId).getMasterOrderId();

            // Mở dispute
            CreateDisputeRequest req = new CreateDisputeRequest(
                    subId, DisputeReason.SERVICE_NOT_AS_DESCRIBED, "Dịch vụ thiếu 1 bữa ăn", Collections.emptyList());
            DisputeResponse disputeResp = createDisputeUseCase.execute(masterOrderId, req, customerId);

            // Admin resolve hoàn 35% (3.500.000 VND)
            ResolveDisputeRequest resolveReq = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_PARTIAL, new BigDecimal("35.0"), "Đồng ý hoàn 35% tiền ăn");
            resolveDisputeUseCase.execute(disputeResp.id(), resolveReq, adminUserId);

            // Resolution creates a pending provider refund and does not claim success early.
            assertThat(subOrderStore.get(subId).getStatus()).isEqualTo(SubOrderStatus.COMPLETED);
            RefundJpaEntity pendingRefund = refundStore.get(subId);
            assertThat(pendingRefund.getAmount()).isEqualByComparingTo(new BigDecimal("3500000.00"));
            assertThat(pendingRefund.getStatus()).isEqualTo(RefundStatus.PENDING);

            // Simulate the signed payment-provider webhook before settlement.
            pendingRefund.setStatus(RefundStatus.PROCESSED);
            subOrderStore.get(subId).setStatus(SubOrderStatus.PARTIALLY_REFUNDED);

            // Chạy settlement
            SettlementResponse response = generateSettlementUseCase.execute(vendorId, periodStart, periodEnd);

            // Kỳ vọng:
            // netAfterRefund = 10.000.000 - 3.500.000 = 6.500.000
            // grossAmount = 6.500.000.00
            // commissionAmount = 6.500.000 * 15% = 975.000.00
            // netPayableAmount = 6.500.000 - 975.000 = 5.525.000.00
            assertThat(response.grossAmount()).isEqualByComparingTo(new BigDecimal("6500000.00"));
            assertThat(response.commissionAmount()).isEqualByComparingTo(new BigDecimal("975000.00"));
            assertThat(response.netPayableAmount()).isEqualByComparingTo(new BigDecimal("5525000.00"));

            // Bất biến kế toán: Gross == Commission + NetPayable
            assertThat(response.grossAmount())
                    .isEqualTo(response.commissionAmount().add(response.netPayableAmount()));

            SettlementDetailResponse detail = getSettlementsUseCase.getAdminSettlementById(response.id());
            SettlementLineItemResponse item = detail.lineItems().get(0);
            assertThat(item.grossAmount()).isEqualByComparingTo(new BigDecimal("6500000.00"));
            assertThat(item.refundAmount()).isEqualByComparingTo(new BigDecimal("3500000.00"));
            assertThat(item.commissionAmount()).isEqualByComparingTo(new BigDecimal("975000.00"));
            assertThat(item.netAmount()).isEqualByComparingTo(new BigDecimal("5525000.00"));
            assertThat(item.excludedReason()).isNull();
        }

        @Test
        @DisplayName("2.2 Phép tính lẻ 33.33% với số tiền lẻ 1.333.333 VND: Đảm bảo độ chính xác scale 2 và không lệch 1 xu")
        void testPartialRefundOddNumbersAndScaleRounding() {
            UUID subId = UUID.randomUUID();
            BigDecimal subtotal = new BigDecimal("1333333.00");
            BigDecimal commRate = new BigDecimal("0.1000");
            createOrderWithSlot(subId, subtotal, commRate, SubOrderStatus.PARTIALLY_REFUNDED);

            // Giả lập refund 33.33% = 444399.89
            BigDecimal refundAmount = new BigDecimal("444399.89");
            RefundJpaEntity refund = new RefundJpaEntity();
            refund.setId(UUID.randomUUID());
            refund.setSubOrderId(subId);
            refund.setAmount(refundAmount);
            refund.setStatus(RefundStatus.PROCESSED);
            refundStore.put(subId, refund);

            SettlementResponse response = generateSettlementUseCase.execute(vendorId, periodStart, periodEnd);

            // netAfterRefund = 1333333.00 - 444399.89 = 888933.11
            // commissionAmount = 888933.11 * 0.1000 = 88893.31 (HALF_UP)
            // netPayableAmount = 888933.11 - 88893.31 = 800039.80
            BigDecimal expectedGross = new BigDecimal("888933.11");
            BigDecimal expectedComm = new BigDecimal("88893.31");
            BigDecimal expectedNet = new BigDecimal("800039.80");

            assertThat(response.grossAmount()).isEqualByComparingTo(expectedGross);
            assertThat(response.commissionAmount()).isEqualByComparingTo(expectedComm);
            assertThat(response.netPayableAmount()).isEqualByComparingTo(expectedNet);

            // Bất biến kế toán: Gross == Commission + NetPayable
            assertThat(response.grossAmount())
                    .isEqualTo(response.commissionAmount().add(response.netPayableAmount()));
        }
    }

    @Nested
    @DisplayName("3. Đối kháng Hủy do Thời tiết (WEATHER): Hoàn 100%, loại trừ hoàn toàn (FULL_REFUND)")
    class WeatherCancellationTests {

        @Test
        @DisplayName("3.1 Hủy do WEATHER trước 10 phút: RefundPolicyEngine hoàn 100.0%, Settlement loại trừ với FULL_REFUND")
        void testWeatherCancellation100PercentExclusion() {
            UUID subId = UUID.randomUUID();
            BigDecimal subtotal = new BigDecimal("3500000.00");
            createOrderWithSlot(subId, subtotal, new BigDecimal("0.1000"), SubOrderStatus.CONFIRMED);

            // Đánh giá hủy do WEATHER
            LocalDateTime departureTime = LocalDateTime.of(LocalDate.now(), LocalTime.of(15, 0));
            LocalDateTime cancelTime = LocalDateTime.of(LocalDate.now(), LocalTime.of(14, 50)); // Trước 10 phút

            RefundEvaluationResult eval = refundPolicyEngine.evaluate(
                    RefundReason.WEATHER, departureTime, cancelTime, subtotal);

            assertThat(eval.refundPercentage()).isEqualByComparingTo(BigDecimal.valueOf(100.0));
            assertThat(eval.refundAmount()).isEqualByComparingTo(subtotal);

            // Cập nhật REFUNDED
            SubOrderJpaEntity subOrder = subOrderStore.get(subId);
            subOrder.setStatus(SubOrderStatus.REFUNDED);
            subOrderStore.put(subId, subOrder);

            RefundJpaEntity refund = new RefundJpaEntity();
            refund.setId(UUID.randomUUID());
            refund.setSubOrderId(subId);
            refund.setAmount(eval.refundAmount());
            refund.setRefundPercentage(eval.refundPercentage());
            refund.setReason(RefundReason.WEATHER);
            refund.setStatus(RefundStatus.PROCESSED);
            refundStore.put(subId, refund);

            // Chạy settlement
            SettlementResponse response = generateSettlementUseCase.execute(vendorId, periodStart, periodEnd);

            assertThat(response.grossAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.commissionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.netPayableAmount()).isEqualByComparingTo(BigDecimal.ZERO);

            SettlementDetailResponse detail = getSettlementsUseCase.getAdminSettlementById(response.id());
            assertThat(detail.lineItems()).hasSize(1);
            SettlementLineItemResponse item = detail.lineItems().get(0);
            assertThat(item.excludedReason()).isEqualTo(LineItemExclusionReason.FULL_REFUND);
            assertThat(item.isExcluded()).isTrue();
            assertThat(item.grossAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.refundAmount()).isEqualByComparingTo(subtotal);
            assertThat(item.commissionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.netAmount()).isEqualByComparingTo(BigDecimal.ZERO);

            // Bất biến kế toán: 0 == 0 + 0
            assertThat(response.grossAmount())
                    .isEqualTo(response.commissionAmount().add(response.netPayableAmount()));
        }
    }

    @Nested
    @DisplayName("4. Đối kháng Bất biến Kế toán Toàn diện: grossAmount == commissionAmount + netPayableAmount")
    class ComprehensiveAccountingInvariantTests {

        @Test
        @DisplayName("4.1 Kiểm toán ma trận 8 đơn hàng hỗn hợp đa trạng thái: Bất biến kế toán giữ vững trên từng đơn và toàn kỳ")
        void testMatrix8OrdersAccountingInvariant() {
            LocalDate date = LocalDate.now();

            // Đơn 1: COMPLETED chuẩn, hoa hồng 10%
            UUID o1 = UUID.randomUUID();
            createOrderWithSlot(o1, new BigDecimal("1000000.00"), new BigDecimal("0.1000"), SubOrderStatus.COMPLETED);

            // Đơn 2: COMPLETED chuẩn, hoa hồng 15%
            UUID o2 = UUID.randomUUID();
            createOrderWithSlot(o2, new BigDecimal("2500000.00"), new BigDecimal("0.1500"), SubOrderStatus.COMPLETED);

            // Đơn 3: CHECKED_IN, hoa hồng 10%
            UUID o3 = UUID.randomUUID();
            createOrderWithSlot(o3, new BigDecimal("800000.00"), new BigDecimal("0.1000"), SubOrderStatus.CHECKED_IN);

            // Đơn 4: OPEN Dispute -> Tạm giữ
            UUID o4 = UUID.randomUUID();
            createOrderWithSlot(o4, new BigDecimal("3000000.00"), new BigDecimal("0.1000"), SubOrderStatus.COMPLETED);
            DisputeJpaEntity d4 = new DisputeJpaEntity();
            d4.setId(UUID.randomUUID());
            d4.setSubOrderId(o4);
            d4.setStatus(DisputeStatus.OPEN);
            disputeStore.put(d4.getId(), d4);

            // Đơn 5: UNDER_REVIEW Dispute -> Tạm giữ
            UUID o5 = UUID.randomUUID();
            createOrderWithSlot(o5, new BigDecimal("4500000.00"), new BigDecimal("0.1200"), SubOrderStatus.COMPLETED);
            DisputeJpaEntity d5 = new DisputeJpaEntity();
            d5.setId(UUID.randomUUID());
            d5.setSubOrderId(o5);
            d5.setStatus(DisputeStatus.UNDER_REVIEW);
            disputeStore.put(d5.getId(), d5);

            // Đơn 6: Hoàn 50% (PARTIALLY_REFUNDED), hoa hồng 10%
            UUID o6 = UUID.randomUUID();
            createOrderWithSlot(o6, new BigDecimal("2000000.00"), new BigDecimal("0.1000"), SubOrderStatus.PARTIALLY_REFUNDED);
            RefundJpaEntity r6 = new RefundJpaEntity();
            r6.setId(UUID.randomUUID());
            r6.setSubOrderId(o6);
            r6.setAmount(new BigDecimal("1000000.00"));
            r6.setStatus(RefundStatus.PROCESSED);
            refundStore.put(o6, r6);

            // Đơn 7: Hủy WEATHER 100% (REFUNDED)
            UUID o7 = UUID.randomUUID();
            createOrderWithSlot(o7, new BigDecimal("1500000.00"), new BigDecimal("0.1000"), SubOrderStatus.REFUNDED);
            RefundJpaEntity r7 = new RefundJpaEntity();
            r7.setId(UUID.randomUUID());
            r7.setSubOrderId(o7);
            r7.setAmount(new BigDecimal("1500000.00"));
            r7.setStatus(RefundStatus.PROCESSED);
            refundStore.put(o7, r7);

            // Đơn 8: Hoàn 70% (PARTIALLY_REFUNDED), hoa hồng 20%
            UUID o8 = UUID.randomUUID();
            createOrderWithSlot(o8, new BigDecimal("5000000.00"), new BigDecimal("0.2000"), SubOrderStatus.PARTIALLY_REFUNDED);
            RefundJpaEntity r8 = new RefundJpaEntity();
            r8.setId(UUID.randomUUID());
            r8.setSubOrderId(o8);
            r8.setAmount(new BigDecimal("3500000.00"));
            r8.setStatus(RefundStatus.PROCESSED);
            refundStore.put(o8, r8);

            // Chạy settlement
            SettlementResponse response = generateSettlementUseCase.execute(vendorId, periodStart, periodEnd);
            SettlementDetailResponse detail = getSettlementsUseCase.getAdminSettlementById(response.id());

            assertThat(detail.lineItems()).hasSize(8);

            // Xác minh bất biến kế toán trên TỪNG DÒNG ĐƠN HÀNG
            for (SettlementLineItemResponse item : detail.lineItems()) {
                assertThat(item.grossAmount())
                        .as("Line item %s must satisfy gross == commission + net", item.subOrderId())
                        .isEqualTo(item.commissionAmount().add(item.netAmount()));
            }

            // Xác minh bất biến kế toán trên TỔNG THỂ KỲ ĐỐI SOÁT
            assertThat(response.grossAmount())
                    .as("Settlement total gross must equal commission + netPayable")
                    .isEqualTo(response.commissionAmount().add(response.netPayableAmount()));

            // Tính toán kỳ vọng chính xác:
            // o1: gross 1.000.000, comm 100.000, net 900.000
            // o2: gross 2.500.000, comm 375.000, net 2.125.000
            // o3: gross 800.000, comm 80.000, net 720.000
            // o4: gross 0, comm 0, net 0 (ACTIVE_DISPUTE)
            // o5: gross 0, comm 0, net 0 (ACTIVE_DISPUTE)
            // o6: gross 1.000.000, comm 100.000, net 900.000
            // o7: gross 0, comm 0, net 0 (FULL_REFUND)
            // o8: gross 1.500.000, comm 300.000, net 1.200.000
            //
            // Total Gross = 1.000.000 + 2.500.000 + 800.000 + 1.000.000 + 1.500.000 = 6.800.000
            // Total Comm  = 100.000 + 375.000 + 80.000 + 100.000 + 300.000 = 955.000
            // Total Net   = 900.000 + 2.125.000 + 720.000 + 900.000 + 1.200.000 = 5.845.000
            BigDecimal expectedTotalGross = new BigDecimal("6800000.00");
            BigDecimal expectedTotalComm = new BigDecimal("955000.00");
            BigDecimal expectedTotalNet = new BigDecimal("5845000.00");

            assertThat(response.grossAmount()).isEqualByComparingTo(expectedTotalGross);
            assertThat(response.commissionAmount()).isEqualByComparingTo(expectedTotalComm);
            assertThat(response.netPayableAmount()).isEqualByComparingTo(expectedTotalNet);
            assertThat(expectedTotalGross).isEqualTo(expectedTotalComm.add(expectedTotalNet));
        }
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================
    private void createOrderWithSlot(UUID subId, BigDecimal subtotal, BigDecimal commissionRate, SubOrderStatus status) {
        UUID slotId = UUID.randomUUID();
        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setServiceId(serviceId);
        slot.setDate(LocalDate.now().minusDays(1));
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setEndTime(LocalTime.of(12, 0));
        slot.setStatus(SlotStatus.OPEN);
        slotStore.put(slotId, slot);

        UUID masterId = UUID.randomUUID();
        MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
        masterOrder.setId(masterId);
        masterOrder.setCustomerId(customerId);
        masterOrder.setStatus(MasterOrderStatus.PAID);
        masterOrder.setTotalAmount(subtotal);
        masterOrderStore.put(masterId, masterOrder);

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(subId);
        subOrder.setMasterOrderId(masterId);
        subOrder.setVendorId(vendorId);
        subOrder.setServiceId(serviceId);
        subOrder.setSlotId(slotId);
        subOrder.setSubtotalAmount(subtotal);
        subOrder.setCommissionRate(commissionRate);
        subOrder.setStatus(status);
        subOrder.setCreatedAt(OffsetDateTime.of(2026, 9, 15, 10, 0, 0, 0, ZoneOffset.ofHours(7)));
        subOrderStore.put(subId, subOrder);
    }

    private void wireRepositories() {
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

        when(subOrderRepository.findById(any())).thenAnswer(inv -> {
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

        when(slotRepository.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(slotStore.get(id));
        });

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

        Vendor vendor = new Vendor();
        vendor.setId(vendorId);
        vendor.setVerificationStatus(VerificationStatus.APPROVED);
        when(vendorInternalApi.findById(vendorId)).thenReturn(Optional.of(vendor));
    }
}
