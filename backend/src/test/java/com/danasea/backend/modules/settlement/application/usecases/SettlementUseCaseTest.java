package com.danasea.backend.modules.settlement.application.usecases;

import com.danasea.backend.modules.order.application.services.VendorFaultCompensationService;
import org.springframework.jdbc.core.JdbcTemplate;

import com.danasea.backend.modules.booking.domain.ports.VendorLookupPort;
import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.dispute.infrastructure.persistence.entities.DisputeJpaEntity;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderDisruptionRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderDisruptionJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.settlement.application.dtos.GenerateSettlementRequest;
import com.danasea.backend.modules.settlement.application.dtos.SettlementDetailResponse;
import com.danasea.backend.modules.settlement.application.dtos.SettlementResponse;
import com.danasea.backend.modules.settlement.domain.exceptions.InvalidSettlementPeriodException;
import com.danasea.backend.modules.settlement.domain.exceptions.SettlementAlreadyFinalizedException;
import com.danasea.backend.modules.settlement.domain.exceptions.SettlementNotFoundException;
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
import com.danasea.backend.security.authorization.domain.models.AuthorizationSubject;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementUseCaseTest — Application Layer Unit Tests for EPIC-07 R4")
class SettlementUseCaseTest {

    @Mock
    private JpaSettlementRepository settlementRepository;

    @Mock
    private JpaSettlementLineItemRepository lineItemRepository;

    @Mock
    private JpaSubOrderRepository subOrderRepository;

    @Mock
    private JpaRefundRepository refundRepository;

    @Mock
    private JpaDisputeRepository disputeRepository;

    @Mock
    private VendorLookupPort vendorLookupPort;

    @Mock
    private JpaServiceSlotRepository serviceSlotRepository;

    @Mock
    private JpaSubOrderDisruptionRepository disruptionRepository;

    @Mock
    private VendorInternalApi vendorInternalApi;

    @Mock
    private AuditLogInternalApi auditLogInternalApi;

    private SettlementCalculationEngine calculationEngine;
    private SettlementMapper mapper;

    private GenerateSettlementUseCase generateSettlementUseCase;
    private FinalizeSettlementUseCase finalizeSettlementUseCase;
    private GetSettlementsUseCase getSettlementsUseCase;
    private GetSettlementDetailUseCase getSettlementDetailUseCase;

    private UUID vendorId;
    private LocalDate periodStart;
    private LocalDate periodEnd;

    @BeforeEach
    void setUp() {
        calculationEngine = new SettlementCalculationEngine();
        mapper = new SettlementMapper();

        generateSettlementUseCase = new GenerateSettlementUseCase(
                settlementRepository,
                lineItemRepository,
                subOrderRepository,
                refundRepository,
                disputeRepository,
                serviceSlotRepository,
                disruptionRepository,
                calculationEngine,
                mapper,
                vendorInternalApi
        );

        finalizeSettlementUseCase = new FinalizeSettlementUseCase(
                settlementRepository,
                mapper,
                auditLogInternalApi,
                new VendorFaultCompensationService(mock(JdbcTemplate.class), null, null, 0, 30)
        );
        finalizeSettlementUseCase.setLineItemRepository(lineItemRepository);

        getSettlementsUseCase = new GetSettlementsUseCase(
                settlementRepository,
                lineItemRepository,
                vendorLookupPort,
                mapper
        );

        getSettlementDetailUseCase = new GetSettlementDetailUseCase(
                settlementRepository,
                lineItemRepository,
                vendorLookupPort,
                mapper
        );

        vendorId = UUID.randomUUID();
        periodStart = LocalDate.of(2026, 9, 1);
        periodEnd = LocalDate.of(2026, 9, 30);
    }

    @AfterEach
    void tearDown() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("1. GenerateSettlementUseCase Tests")
    class GenerateSettlementTests {

        @Test
        @DisplayName("Khởi tạo đối soát thành công với các đơn hàng đa dạng trạng thái")
        void testGenerateSettlementSuccess() {
            UUID subOrder1Id = UUID.randomUUID();
            SubOrderJpaEntity subOrder1 = new SubOrderJpaEntity();
            subOrder1.setId(subOrder1Id);
            subOrder1.setVendorId(vendorId);
            subOrder1.setSubtotalAmount(new BigDecimal("1000000.00"));
            subOrder1.setCommissionRate(new BigDecimal("0.1000"));
            subOrder1.setStatus(SubOrderStatus.COMPLETED);
            subOrder1.setCreatedAt(periodStart.atTime(12, 0).atOffset(OffsetDateTime.now().getOffset()));

            UUID subOrder2Id = UUID.randomUUID();
            SubOrderJpaEntity subOrder2 = new SubOrderJpaEntity();
            subOrder2.setId(subOrder2Id);
            subOrder2.setVendorId(vendorId);
            subOrder2.setSubtotalAmount(new BigDecimal("2000000.00"));
            subOrder2.setCommissionRate(new BigDecimal("0.1000"));
            subOrder2.setStatus(SubOrderStatus.PARTIALLY_REFUNDED);
            subOrder2.setCreatedAt(periodStart.atTime(12, 0).atOffset(OffsetDateTime.now().getOffset()));

            RefundJpaEntity refund = new RefundJpaEntity();
            refund.setSubOrderId(subOrder2Id);
            refund.setAmount(new BigDecimal("500000.00"));
            refund.setStatus(RefundStatus.PROCESSED);

            when(settlementRepository.findByVendorIdAndPeriodStartAndPeriodEnd(vendorId, periodStart, periodEnd))
                    .thenReturn(Optional.empty());
            when(subOrderRepository.findByVendorIdAndStatusIn(eq(vendorId), any()))
                    .thenReturn(List.of(subOrder1, subOrder2));
            when(refundRepository.findBySubOrderIdIn(any()))
                    .thenReturn(List.of(refund));
            when(disputeRepository.findBySubOrderIdInAndStatusIn(any(), any()))
                    .thenReturn(Collections.emptyList());

            when(settlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(lineItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            GenerateSettlementRequest request = new GenerateSettlementRequest(vendorId, periodStart, periodEnd);
            SettlementResponse response = generateSettlementUseCase.execute(request);

            assertThat(response).isNotNull();
            assertThat(response.vendorId()).isEqualTo(vendorId);
            assertThat(response.status()).isEqualTo(SettlementStatus.DRAFT);
            // Gross: 1,000,000 + (2,000,000 - 500,000) = 2,500,000.00
            assertThat(response.grossAmount()).isEqualByComparingTo("2500000.00");
            assertThat(response.commissionAmount()).isEqualByComparingTo("250000.00");
            assertThat(response.safetyRebateQualified()).isFalse();
            assertThat(response.safetyRebateAmount()).isEqualByComparingTo("0.00");
            assertThat(response.netPayableAmount()).isEqualByComparingTo("2250000.00");
            verify(lineItemRepository, org.mockito.Mockito.times(2)).save(any());
        }

        @Test
        @DisplayName("Safety & SLA Rebate: Cộng trực tiếp 2% tiền mặt vào Net Payable khi Vendor đạt chuẩn an toàn và rating >= 4.8")
        void testGenerateSettlementWithSafetyRebateQualified() {
            UUID subOrderId = UUID.randomUUID();
            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setVendorId(vendorId);
            subOrder.setSubtotalAmount(new BigDecimal("2500000.00"));
            subOrder.setCommissionRate(new BigDecimal("0.1000"));
            subOrder.setStatus(SubOrderStatus.COMPLETED);
            subOrder.setCreatedAt(periodStart.atTime(12, 0).atOffset(OffsetDateTime.now().getOffset()));

            Vendor qualifiedVendor = new Vendor();
            qualifiedVendor.setId(vendorId);
            qualifiedVendor.setRatingAvg(new BigDecimal("4.90"));
            qualifiedVendor.setRatingCount(10);

            when(settlementRepository.findByVendorIdAndPeriodStartAndPeriodEnd(vendorId, periodStart, periodEnd))
                    .thenReturn(Optional.empty());
            when(subOrderRepository.findByVendorIdAndStatusIn(eq(vendorId), any()))
                    .thenReturn(List.of(subOrder));
            when(refundRepository.findBySubOrderIdIn(any()))
                    .thenReturn(Collections.emptyList());
            when(disputeRepository.findBySubOrderIdInAndStatusIn(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(vendorInternalApi.findById(vendorId))
                    .thenReturn(Optional.of(qualifiedVendor));

            when(settlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(lineItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            GenerateSettlementRequest request = new GenerateSettlementRequest(vendorId, periodStart, periodEnd);
            SettlementResponse response = generateSettlementUseCase.execute(request);

            assertThat(response.grossAmount()).isEqualByComparingTo("2500000.00");
            assertThat(response.commissionAmount()).isEqualByComparingTo("250000.00");
            assertThat(response.safetyRebateQualified()).isTrue();
            assertThat(response.safetyRebateAmount()).isEqualByComparingTo("50000.00");
            assertThat(response.netPayableAmount()).isEqualByComparingTo("2300000.00");
        }

        @Test
        @DisplayName("Idempotency: Chặn tạo lại khi kỳ đối soát đã FINALIZED (ném SettlementAlreadyFinalizedException 409)")
        void testGenerateSettlementAlreadyFinalizedThrowsConflict() {
            SettlementJpaEntity existing = new SettlementJpaEntity();
            existing.setId(UUID.randomUUID());
            existing.setVendorId(vendorId);
            existing.setPeriodStart(periodStart);
            existing.setPeriodEnd(periodEnd);
            existing.setStatus(SettlementStatus.FINALIZED);

            when(settlementRepository.findByVendorIdAndPeriodStartAndPeriodEnd(vendorId, periodStart, periodEnd))
                    .thenReturn(Optional.of(existing));

            GenerateSettlementRequest request = new GenerateSettlementRequest(vendorId, periodStart, periodEnd);

            assertThatThrownBy(() -> generateSettlementUseCase.execute(request))
                    .isInstanceOf(SettlementAlreadyFinalizedException.class)
                    .hasMessageContaining("FINALIZED");

            verify(subOrderRepository, never()).findByVendorId(any());
        }

        @Test
        @DisplayName("Idempotency: Chặn tạo lại khi kỳ đối soát đã PAID (ném SettlementAlreadyFinalizedException 409)")
        void testGenerateSettlementAlreadyPaidThrowsConflict() {
            SettlementJpaEntity existing = new SettlementJpaEntity();
            existing.setId(UUID.randomUUID());
            existing.setStatus(SettlementStatus.PAID);

            when(settlementRepository.findByVendorIdAndPeriodStartAndPeriodEnd(vendorId, periodStart, periodEnd))
                    .thenReturn(Optional.of(existing));

            GenerateSettlementRequest request = new GenerateSettlementRequest(vendorId, periodStart, periodEnd);

            assertThatThrownBy(() -> generateSettlementUseCase.execute(request))
                    .isInstanceOf(SettlementAlreadyFinalizedException.class);
        }

        @Test
        @DisplayName("Recalculate: Cho phép tính toán lại in-place nếu kỳ đang là DRAFT")
        void testGenerateSettlementRecalculatesDraftInPlace() {
            UUID existingId = UUID.randomUUID();
            SettlementJpaEntity existing = new SettlementJpaEntity();
            existing.setId(existingId);
            existing.setVendorId(vendorId);
            existing.setStatus(SettlementStatus.DRAFT);
            existing.setGrossAmount(BigDecimal.ZERO);

            when(settlementRepository.findByVendorIdAndPeriodStartAndPeriodEnd(vendorId, periodStart, periodEnd))
                    .thenReturn(Optional.of(existing));
            when(subOrderRepository.findByVendorIdAndStatusIn(eq(vendorId), any()))
                    .thenReturn(Collections.emptyList());
            when(settlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            GenerateSettlementRequest request = new GenerateSettlementRequest(vendorId, periodStart, periodEnd);
            SettlementResponse response = generateSettlementUseCase.execute(request);

            verify(lineItemRepository).deleteBySettlementId(existingId);
            assertThat(response.id()).isEqualTo(existingId);
            assertThat(response.status()).isEqualTo(SettlementStatus.DRAFT);
        }

        @Test
        @DisplayName("Validation: Ngày bắt đầu sau ngày kết thúc ném InvalidSettlementPeriodException 400")
        void testInvalidPeriodThrowsBadRequest() {
            GenerateSettlementRequest request = new GenerateSettlementRequest(
                    vendorId,
                    LocalDate.of(2026, 9, 30),
                    LocalDate.of(2026, 9, 1)
            );

            assertThatThrownBy(() -> generateSettlementUseCase.execute(request))
                    .isInstanceOf(InvalidSettlementPeriodException.class)
                    .hasMessageContaining("must not be after");
        }

        @Test
        @DisplayName("D+7 Boundary: Đơn Day 6 và Day 7 bị tạm giữ tiền (DISPUTE_WINDOW_OPEN), Day 8 được giải ngân")
        void testDPlus7Boundary() {
            LocalDate experienceDate = LocalDate.of(2026, 10, 1);
            ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
            slot.setId(UUID.randomUUID());
            slot.setDate(experienceDate);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(UUID.randomUUID());
            subOrder.setVendorId(vendorId);
            subOrder.setSlotId(slot.getId());
            subOrder.setSubtotalAmount(new BigDecimal("1000000.00"));
            subOrder.setCommissionRate(new BigDecimal("0.1000"));
            subOrder.setStatus(SubOrderStatus.COMPLETED);

            when(settlementRepository.findByVendorIdAndPeriodStartAndPeriodEnd(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(subOrderRepository.findByVendorIdAndStatusIn(eq(vendorId), any()))
                    .thenReturn(List.of(subOrder));
            when(serviceSlotRepository.findById(slot.getId())).thenReturn(Optional.of(slot));
            when(refundRepository.findBySubOrderIdIn(any())).thenReturn(Collections.emptyList());
            when(disputeRepository.findBySubOrderIdInAndStatusIn(any(), any())).thenReturn(Collections.emptyList());
            when(lineItemRepository.findSettledSubOrderIdsByVendorId(vendorId)).thenReturn(Collections.emptySet());
            when(settlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Ngày đánh giá: Day 7 (experienceDate + 7 days = 2026-10-08) -> Cửa sổ khiếu nại vẫn mở!
            LocalDate day7 = experienceDate.plusDays(7);
            SettlementResponse respDay7 = generateSettlementUseCase.execute(vendorId, experienceDate, day7, day7);
            assertThat(respDay7.netPayableAmount()).isEqualByComparingTo("0.00"); // Tiền bị giữ!

            // Ngày đánh giá: Day 8 (experienceDate + 8 days = 2026-10-09) -> Cửa sổ khiếu nại đã đóng!
            LocalDate day8 = experienceDate.plusDays(8);
            SettlementResponse respDay8 = generateSettlementUseCase.execute(vendorId, experienceDate, day7, day8);
            assertThat(respDay8.netPayableAmount()).isEqualByComparingTo("900000.00"); // Được giải ngân!
        }

        @Test
        @DisplayName("Đơn gối kỳ: Đơn chưa đủ điều kiện ở kỳ 1 (do D+7) tự động được gối và thanh toán ở kỳ 2")
        void testRolloverUnsettledOrderToNextPeriod() {
            LocalDate exp1 = LocalDate.of(2026, 10, 6);
            ServiceSlotJpaEntity slot1 = new ServiceSlotJpaEntity();
            slot1.setId(UUID.randomUUID());
            slot1.setDate(exp1);

            SubOrderJpaEntity subOrder1 = new SubOrderJpaEntity();
            subOrder1.setId(UUID.randomUUID());
            subOrder1.setVendorId(vendorId);
            subOrder1.setSlotId(slot1.getId());
            subOrder1.setSubtotalAmount(new BigDecimal("1000000.00"));
            subOrder1.setCommissionRate(new BigDecimal("0.1000"));
            subOrder1.setStatus(SubOrderStatus.COMPLETED);

            when(serviceSlotRepository.findById(slot1.getId())).thenReturn(Optional.of(slot1));
            when(subOrderRepository.findByVendorIdAndStatusIn(eq(vendorId), any())).thenReturn(List.of(subOrder1));
            when(refundRepository.findBySubOrderIdIn(any())).thenReturn(Collections.emptyList());
            when(disputeRepository.findBySubOrderIdInAndStatusIn(any(), any())).thenReturn(Collections.emptyList());
            when(lineItemRepository.findSettledSubOrderIdsByVendorId(vendorId)).thenReturn(Collections.emptySet());
            when(settlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Kỳ 1: 01/10 - 07/10, ngày chạy đối soát 08/10 (exp1 = 06/10 mới qua 2 ngày) -> Bị giữ tiền
            SettlementResponse r1 = generateSettlementUseCase.execute(vendorId, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 8));
            assertThat(r1.netPayableAmount()).isEqualByComparingTo("0.00");

            // Kỳ 2: 08/10 - 14/10, ngày chạy đối soát 15/10 (exp1 = 06/10 đã qua 9 ngày, hết D+7)
            // Đơn exp1 trước periodStart (08/10) nhưng chưa từng được quyết toán -> Tự động được gối vào kỳ 2 và chi trả!
            SettlementResponse r2 = generateSettlementUseCase.execute(vendorId, LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 15));
            assertThat(r2.netPayableAmount()).isEqualByComparingTo("900000.00");
        }

        @Test
        @DisplayName("Chống quyết toán trùng: Đơn đã thanh toán ở kỳ FINALIZED không bị tính lại")
        void testAntiDuplicateSettlement() {
            LocalDate exp1 = LocalDate.of(2026, 10, 1);
            ServiceSlotJpaEntity slot1 = new ServiceSlotJpaEntity();
            slot1.setId(UUID.randomUUID());
            slot1.setDate(exp1);

            SubOrderJpaEntity subOrder1 = new SubOrderJpaEntity();
            UUID subId = UUID.randomUUID();
            subOrder1.setId(subId);
            subOrder1.setVendorId(vendorId);
            subOrder1.setSlotId(slot1.getId());
            subOrder1.setSubtotalAmount(new BigDecimal("1000000.00"));
            subOrder1.setCommissionRate(new BigDecimal("0.1000"));
            subOrder1.setStatus(SubOrderStatus.COMPLETED);

            when(subOrderRepository.findByVendorIdAndStatusIn(eq(vendorId), any())).thenReturn(List.of(subOrder1));
            // Đơn này đã nằm trong danh sách các đơn đã quyết toán của kỳ trước
            when(lineItemRepository.findSettledSubOrderIdsByVendorId(vendorId)).thenReturn(Set.of(subId));
            when(settlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SettlementResponse r = generateSettlementUseCase.execute(vendorId, LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 15));
            assertThat(r.grossAmount()).isEqualByComparingTo("0.00");
            assertThat(r.netPayableAmount()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Pending Refund: Đơn có yêu cầu hoàn tiền PENDING bị tạm giữ tiền")
        void testPendingRefund_HoldsPayout() {
            LocalDate exp1 = LocalDate.of(2026, 10, 1);
            SubOrderJpaEntity subOrder1 = new SubOrderJpaEntity();
            UUID subId = UUID.randomUUID();
            subOrder1.setId(subId);
            subOrder1.setVendorId(vendorId);
            subOrder1.setCreatedAt(exp1.atStartOfDay().atOffset(OffsetDateTime.now().getOffset()));
            subOrder1.setSubtotalAmount(new BigDecimal("1000000.00"));
            subOrder1.setCommissionRate(new BigDecimal("0.1000"));
            subOrder1.setStatus(SubOrderStatus.COMPLETED);

            RefundJpaEntity pendingRefund = new RefundJpaEntity();
            pendingRefund.setSubOrderId(subId);
            pendingRefund.setStatus(RefundStatus.PENDING);

            when(subOrderRepository.findByVendorIdAndStatusIn(eq(vendorId), any())).thenReturn(List.of(subOrder1));
            when(refundRepository.findBySubOrderIdIn(any())).thenReturn(List.of(pendingRefund));
            when(disputeRepository.findBySubOrderIdInAndStatusIn(any(), any())).thenReturn(Collections.emptyList());
            when(lineItemRepository.findSettledSubOrderIdsByVendorId(vendorId)).thenReturn(Collections.emptySet());
            when(settlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Ngày đánh giá: Day 10 (đã qua D+7), nhưng có pending refund -> Tiền vẫn bị giữ!
            SettlementResponse r = generateSettlementUseCase.execute(vendorId, exp1, exp1.plusDays(10), exp1.plusDays(11));
            assertThat(r.netPayableAmount()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Active Disruption: Đơn có sự cố AWAITING_DECISION bị tạm giữ tiền")
        void testActiveDisruption_HoldsPayout() {
            LocalDate exp1 = LocalDate.of(2026, 10, 1);
            SubOrderJpaEntity subOrder1 = new SubOrderJpaEntity();
            UUID subId = UUID.randomUUID();
            subOrder1.setId(subId);
            subOrder1.setVendorId(vendorId);
            subOrder1.setCreatedAt(exp1.atStartOfDay().atOffset(OffsetDateTime.now().getOffset()));
            subOrder1.setSubtotalAmount(new BigDecimal("1000000.00"));
            subOrder1.setCommissionRate(new BigDecimal("0.1000"));
            subOrder1.setStatus(SubOrderStatus.COMPLETED);

            SubOrderDisruptionJpaEntity disruption = new SubOrderDisruptionJpaEntity();
            disruption.setSubOrderId(subId);
            disruption.setStatus("AWAITING_DECISION");

            when(subOrderRepository.findByVendorIdAndStatusIn(eq(vendorId), any())).thenReturn(List.of(subOrder1));
            when(refundRepository.findBySubOrderIdIn(any())).thenReturn(Collections.emptyList());
            when(disputeRepository.findBySubOrderIdInAndStatusIn(any(), any())).thenReturn(Collections.emptyList());
            when(disruptionRepository.findBySubOrderIdInAndStatus(any(), eq("AWAITING_DECISION"))).thenReturn(List.of(disruption));
            when(lineItemRepository.findSettledSubOrderIdsByVendorId(vendorId)).thenReturn(Collections.emptySet());
            when(settlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SettlementResponse r = generateSettlementUseCase.execute(vendorId, exp1, exp1.plusDays(10), exp1.plusDays(11));
            assertThat(r.netPayableAmount()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Case A: Đặt sớm, đi tour sau nhiều ngày — Chỉ vào kỳ và giải ngân theo ngày trải nghiệm + D+7")
        void testCaseA_BookEarly_TourLate_FollowsExperienceDate() {
            LocalDate bookDate = LocalDate.of(2026, 9, 1);
            LocalDate expDate = LocalDate.of(2026, 9, 25);
            ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
            slot.setId(UUID.randomUUID());
            slot.setDate(expDate);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(UUID.randomUUID());
            subOrder.setVendorId(vendorId);
            subOrder.setSlotId(slot.getId());
            subOrder.setCreatedAt(bookDate.atTime(10, 0).atOffset(OffsetDateTime.now().getOffset()));
            subOrder.setSubtotalAmount(new BigDecimal("1000000.00"));
            subOrder.setCommissionRate(new BigDecimal("0.1000"));
            subOrder.setStatus(SubOrderStatus.COMPLETED);

            when(subOrderRepository.findByVendorIdAndStatusIn(eq(vendorId), any())).thenReturn(List.of(subOrder));
            when(serviceSlotRepository.findById(slot.getId())).thenReturn(Optional.of(slot));
            when(refundRepository.findBySubOrderIdIn(any())).thenReturn(Collections.emptyList());
            when(disputeRepository.findBySubOrderIdInAndStatusIn(any(), any())).thenReturn(Collections.emptyList());
            when(lineItemRepository.findSettledSubOrderIdsByVendorId(vendorId)).thenReturn(Collections.emptySet());
            when(settlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Kỳ 1: 01/09 - 15/09 (chạy ngày 16/09): ngày trải nghiệm 25/09 sau kỳ này -> không thuộc kỳ
            SettlementResponse r1 = generateSettlementUseCase.execute(vendorId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 16));
            assertThat(r1.grossAmount()).isEqualByComparingTo("0.00");
            assertThat(r1.netPayableAmount()).isEqualByComparingTo("0.00");

            // Kỳ 2: 16/09 - 30/09 (chạy ngày 05/10): ngày trải nghiệm 25/09 nằm trong kỳ, D+7 đã qua (02/10 <= 05/10) -> được giải ngân
            SettlementResponse r2 = generateSettlementUseCase.execute(vendorId, LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 5));
            assertThat(r2.grossAmount()).isEqualByComparingTo("1000000.00");
            assertThat(r2.netPayableAmount()).isEqualByComparingTo("900000.00");
        }

        @Test
        @DisplayName("Case B: Kỳ cũ chưa từng được Admin tạo — Đơn chưa từng có dòng WITHHELD vẫn được thu hồi và chi trả ở kỳ sau")
        void testCaseB_PastPeriodNeverCreated_UnsettledOrdersPickedUpOnceDPlus7Elapsed() {
            LocalDate expDate = LocalDate.of(2026, 9, 5);
            ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
            slot.setId(UUID.randomUUID());
            slot.setDate(expDate);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(UUID.randomUUID());
            subOrder.setVendorId(vendorId);
            subOrder.setSlotId(slot.getId());
            subOrder.setSubtotalAmount(new BigDecimal("2000000.00"));
            subOrder.setCommissionRate(new BigDecimal("0.1000"));
            subOrder.setStatus(SubOrderStatus.COMPLETED);

            when(subOrderRepository.findByVendorIdAndStatusIn(eq(vendorId), any())).thenReturn(List.of(subOrder));
            when(serviceSlotRepository.findById(slot.getId())).thenReturn(Optional.of(slot));
            when(refundRepository.findBySubOrderIdIn(any())).thenReturn(Collections.emptyList());
            when(disputeRepository.findBySubOrderIdInAndStatusIn(any(), any())).thenReturn(Collections.emptyList());
            when(lineItemRepository.findSettledSubOrderIdsByVendorId(vendorId)).thenReturn(Collections.emptySet());
            when(settlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Admin tạo kỳ 16/09 - 30/09 (chạy ngày 01/10): Đơn ngày 05/09 chưa từng có WITHHELD vẫn được tự động thu hồi và thanh toán
            SettlementResponse r = generateSettlementUseCase.execute(vendorId, LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 1));
            assertThat(r.grossAmount()).isEqualByComparingTo("2000000.00");
            assertThat(r.netPayableAmount()).isEqualByComparingTo("1800000.00");
        }

        @Test
        @DisplayName("Case C: Đơn từng bị giữ tiền ở kỳ trước — Kỳ 1 giữ tiền (DISPUTE_WINDOW_OPEN), kỳ 2 nhả tiền")
        void testCaseC_OrderWithheldInPreviousPeriod_ReleasedInNextPeriod() {
            LocalDate expDate = LocalDate.of(2026, 9, 14);
            ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
            slot.setId(UUID.randomUUID());
            slot.setDate(expDate);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(UUID.randomUUID());
            subOrder.setVendorId(vendorId);
            subOrder.setSlotId(slot.getId());
            subOrder.setSubtotalAmount(new BigDecimal("1000000.00"));
            subOrder.setCommissionRate(new BigDecimal("0.1000"));
            subOrder.setStatus(SubOrderStatus.COMPLETED);

            when(subOrderRepository.findByVendorIdAndStatusIn(eq(vendorId), any())).thenReturn(List.of(subOrder));
            when(serviceSlotRepository.findById(slot.getId())).thenReturn(Optional.of(slot));
            when(refundRepository.findBySubOrderIdIn(any())).thenReturn(Collections.emptyList());
            when(disputeRepository.findBySubOrderIdInAndStatusIn(any(), any())).thenReturn(Collections.emptyList());
            when(lineItemRepository.findSettledSubOrderIdsByVendorId(vendorId)).thenReturn(Collections.emptySet());
            when(settlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Kỳ 1: 01/09 - 15/09, chạy ngày 16/09 (14/09 + 7 = 21/09 > 16/09) -> Bị giữ tiền (bị loại trừ ở kỳ 1)
            SettlementResponse r1 = generateSettlementUseCase.execute(vendorId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 16));
            assertThat(r1.grossAmount()).isEqualByComparingTo("0.00");
            assertThat(r1.netPayableAmount()).isEqualByComparingTo("0.00");

            // Kỳ 2: 16/09 - 30/09, chạy ngày 25/09 (21/09 <= 25/09) -> Giải ngân
            SettlementResponse r2 = generateSettlementUseCase.execute(vendorId, LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 25));
            assertThat(r2.grossAmount()).isEqualByComparingTo("1000000.00");
            assertThat(r2.netPayableAmount()).isEqualByComparingTo("900000.00");
        }

        @Test
        @DisplayName("Case D: Dispute được giải quyết sau — Đang tranh chấp bị giữ, giải quyết xong được nhả tiền")
        void testCaseD_DisputeResolvedLater_MoneyReleased() {
            LocalDate expDate = LocalDate.of(2026, 9, 5);
            ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
            slot.setId(UUID.randomUUID());
            slot.setDate(expDate);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            UUID subId = UUID.randomUUID();
            subOrder.setId(subId);
            subOrder.setVendorId(vendorId);
            subOrder.setSlotId(slot.getId());
            subOrder.setSubtotalAmount(new BigDecimal("1000000.00"));
            subOrder.setCommissionRate(new BigDecimal("0.1000"));
            subOrder.setStatus(SubOrderStatus.COMPLETED);

            DisputeJpaEntity openDispute = new DisputeJpaEntity();
            openDispute.setSubOrderId(subId);
            openDispute.setStatus(DisputeStatus.OPEN);

            when(subOrderRepository.findByVendorIdAndStatusIn(eq(vendorId), any())).thenReturn(List.of(subOrder));
            when(serviceSlotRepository.findById(slot.getId())).thenReturn(Optional.of(slot));
            when(refundRepository.findBySubOrderIdIn(any())).thenReturn(Collections.emptyList());
            when(lineItemRepository.findSettledSubOrderIdsByVendorId(vendorId)).thenReturn(Collections.emptySet());
            when(settlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Kỳ 1: có dispute đang mở -> giữ tiền
            when(disputeRepository.findBySubOrderIdInAndStatusIn(any(), any())).thenReturn(List.of(openDispute));
            SettlementResponse r1 = generateSettlementUseCase.execute(vendorId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 20));
            assertThat(r1.netPayableAmount()).isEqualByComparingTo("0.00");

            // Kỳ 2: dispute đã giải quyết (không còn OPEN/UNDER_REVIEW) -> nhả tiền
            when(disputeRepository.findBySubOrderIdInAndStatusIn(any(), any())).thenReturn(Collections.emptyList());
            SettlementResponse r2 = generateSettlementUseCase.execute(vendorId, LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 1));
            assertThat(r2.netPayableAmount()).isEqualByComparingTo("900000.00");
        }

        @Test
        @DisplayName("Case E: Refund dang dở giữ tiền, giải quyết xong thì nhả")
        void testCaseE_PendingRefundHoldsMoney_OnceResolvedReleased() {
            LocalDate expDate = LocalDate.of(2026, 9, 5);
            ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
            slot.setId(UUID.randomUUID());
            slot.setDate(expDate);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            UUID subId = UUID.randomUUID();
            subOrder.setId(subId);
            subOrder.setVendorId(vendorId);
            subOrder.setSlotId(slot.getId());
            subOrder.setSubtotalAmount(new BigDecimal("1000000.00"));
            subOrder.setCommissionRate(new BigDecimal("0.1000"));
            subOrder.setStatus(SubOrderStatus.COMPLETED);

            RefundJpaEntity pendingRefund = new RefundJpaEntity();
            pendingRefund.setSubOrderId(subId);
            pendingRefund.setStatus(RefundStatus.PENDING);

            when(subOrderRepository.findByVendorIdAndStatusIn(eq(vendorId), any())).thenReturn(List.of(subOrder));
            when(serviceSlotRepository.findById(slot.getId())).thenReturn(Optional.of(slot));
            when(disputeRepository.findBySubOrderIdInAndStatusIn(any(), any())).thenReturn(Collections.emptyList());
            when(lineItemRepository.findSettledSubOrderIdsByVendorId(vendorId)).thenReturn(Collections.emptySet());
            when(settlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Kỳ 1: refund PENDING -> giữ tiền
            when(refundRepository.findBySubOrderIdIn(any())).thenReturn(List.of(pendingRefund));
            SettlementResponse r1 = generateSettlementUseCase.execute(vendorId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 20));
            assertThat(r1.netPayableAmount()).isEqualByComparingTo("0.00");

            // Kỳ 2: refund đã từ chối (không còn record PENDING) -> nhả tiền
            when(refundRepository.findBySubOrderIdIn(any())).thenReturn(Collections.emptyList());
            SettlementResponse r2 = generateSettlementUseCase.execute(vendorId, LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 1));
            assertThat(r2.netPayableAmount()).isEqualByComparingTo("900000.00");
        }

        @Test
        @DisplayName("Case F: Vendor không có đơn hợp lệ: Tổng tiền = 0")
        void testCaseF_VendorWithNoEligibleOrders_ZeroGrossAndNet() {
            when(subOrderRepository.findByVendorIdAndStatusIn(eq(vendorId), any())).thenReturn(Collections.emptyList());
            when(lineItemRepository.findSettledSubOrderIdsByVendorId(vendorId)).thenReturn(Collections.emptySet());
            when(settlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SettlementResponse r = generateSettlementUseCase.execute(vendorId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 20));
            assertThat(r.grossAmount()).isEqualByComparingTo("0.00");
            assertThat(r.commissionAmount()).isEqualByComparingTo("0.00");
            assertThat(r.netPayableAmount()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Case G: Bỏ qua một kỳ rồi chạy kỳ sau — Không mất đơn, tất cả đơn chưa quyết toán được thu hồi")
        void testCaseG_SkippedMultiplePeriods_AllUnsettledOrdersRecoveredWithoutLoss() {
            // Đơn 1: 05/09
            ServiceSlotJpaEntity slot1 = new ServiceSlotJpaEntity();
            slot1.setId(UUID.randomUUID());
            slot1.setDate(LocalDate.of(2026, 9, 5));
            SubOrderJpaEntity sub1 = new SubOrderJpaEntity();
            sub1.setId(UUID.randomUUID());
            sub1.setVendorId(vendorId);
            sub1.setSlotId(slot1.getId());
            sub1.setSubtotalAmount(new BigDecimal("1000000.00"));
            sub1.setCommissionRate(new BigDecimal("0.1000"));
            sub1.setStatus(SubOrderStatus.COMPLETED);

            // Đơn 2: 20/09
            ServiceSlotJpaEntity slot2 = new ServiceSlotJpaEntity();
            slot2.setId(UUID.randomUUID());
            slot2.setDate(LocalDate.of(2026, 9, 20));
            SubOrderJpaEntity sub2 = new SubOrderJpaEntity();
            sub2.setId(UUID.randomUUID());
            sub2.setVendorId(vendorId);
            sub2.setSlotId(slot2.getId());
            sub2.setSubtotalAmount(new BigDecimal("2000000.00"));
            sub2.setCommissionRate(new BigDecimal("0.1000"));
            sub2.setStatus(SubOrderStatus.COMPLETED);

            // Đơn 3: 05/10
            ServiceSlotJpaEntity slot3 = new ServiceSlotJpaEntity();
            slot3.setId(UUID.randomUUID());
            slot3.setDate(LocalDate.of(2026, 10, 5));
            SubOrderJpaEntity sub3 = new SubOrderJpaEntity();
            sub3.setId(UUID.randomUUID());
            sub3.setVendorId(vendorId);
            sub3.setSlotId(slot3.getId());
            sub3.setSubtotalAmount(new BigDecimal("3000000.00"));
            sub3.setCommissionRate(new BigDecimal("0.1000"));
            sub3.setStatus(SubOrderStatus.COMPLETED);

            when(subOrderRepository.findByVendorIdAndStatusIn(eq(vendorId), any())).thenReturn(List.of(sub1, sub2, sub3));
            when(serviceSlotRepository.findById(slot1.getId())).thenReturn(Optional.of(slot1));
            when(serviceSlotRepository.findById(slot2.getId())).thenReturn(Optional.of(slot2));
            when(serviceSlotRepository.findById(slot3.getId())).thenReturn(Optional.of(slot3));
            when(refundRepository.findBySubOrderIdIn(any())).thenReturn(Collections.emptyList());
            when(disputeRepository.findBySubOrderIdInAndStatusIn(any(), any())).thenReturn(Collections.emptyList());
            when(lineItemRepository.findSettledSubOrderIdsByVendorId(vendorId)).thenReturn(Collections.emptySet());
            when(settlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Kỳ 1 (01/09-15/09) và Kỳ 2 (16/09-30/09) bị admin bỏ qua.
            // Admin chạy Kỳ 3 (01/10-15/10) vào ngày 16/10:
            // Cả 3 đơn đều có ngày <= 15/10 và D+7 đã qua hết (05/09+7, 20/09+7, 05/10+7 <= 16/10)
            // Tổng Gross = 1M + 2M + 3M = 6M; Commission = 600K; Net = 5.4M
            SettlementResponse r = generateSettlementUseCase.execute(vendorId, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 16));
            assertThat(r.grossAmount()).isEqualByComparingTo("6000000.00");
            assertThat(r.commissionAmount()).isEqualByComparingTo("600000.00");
            assertThat(r.netPayableAmount()).isEqualByComparingTo("5400000.00");
        }
    }

    @Nested
    @DisplayName("2. FinalizeSettlementUseCase Tests")
    class FinalizeSettlementTests {

        @Test
        @DisplayName("Chốt sổ thành công kỳ đối soát DRAFT sang FINALIZED")
        void testFinalizeDraftSettlementSuccess() {
            UUID id = UUID.randomUUID();
            SettlementJpaEntity settlement = new SettlementJpaEntity();
            settlement.setId(id);
            settlement.setStatus(SettlementStatus.DRAFT);

            when(settlementRepository.findById(id)).thenReturn(Optional.of(settlement));
            when(settlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SettlementResponse response = finalizeSettlementUseCase.execute(id);

            assertThat(response.status()).isEqualTo(SettlementStatus.FINALIZED);
        }

        @Test
        @DisplayName("Chốt sổ kỳ đối soát đã FINALIZED ném SettlementAlreadyFinalizedException (409)")
        void testFinalizeAlreadyFinalizedSettlementThrowsConflict() {
            UUID id = UUID.randomUUID();
            SettlementJpaEntity settlement = new SettlementJpaEntity();
            settlement.setId(id);
            settlement.setStatus(SettlementStatus.FINALIZED);

            when(settlementRepository.findById(id)).thenReturn(Optional.of(settlement));

            assertThatThrownBy(() -> finalizeSettlementUseCase.execute(id))
                    .isInstanceOf(SettlementAlreadyFinalizedException.class);
        }

        @Test
        @DisplayName("Chốt sổ kỳ không tồn tại ném SettlementNotFoundException (404)")
        void testFinalizeNonExistingSettlementThrowsNotFound() {
            UUID id = UUID.randomUUID();
            when(settlementRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> finalizeSettlementUseCase.execute(id))
                    .isInstanceOf(SettlementNotFoundException.class);
        }

        @Test
        @DisplayName("Cross-period double payout guard: Ném SettlementAlreadyFinalizedException nếu đơn trong kỳ đã được chốt ở kỳ khác")
        void testFinalizeCrossPeriodDoublePayoutGuard_ThrowsConflict() {
            UUID id = UUID.randomUUID();
            SettlementJpaEntity settlement = new SettlementJpaEntity();
            settlement.setId(id);
            settlement.setVendorId(vendorId);
            settlement.setStatus(SettlementStatus.DRAFT);

            UUID subOrderId = UUID.randomUUID();
            SettlementLineItemJpaEntity item = new SettlementLineItemJpaEntity();
            item.setSubOrderId(subOrderId);
            item.setExcludedReason(LineItemExclusionReason.NONE);

            when(settlementRepository.findById(id)).thenReturn(Optional.of(settlement));
            when(lineItemRepository.findBySettlementId(id)).thenReturn(List.of(item));
            // Đơn này đã được chốt và thanh toán ở một kỳ đối soát khác trước đó!
            when(lineItemRepository.findSettledSubOrderIdsByVendorId(vendorId)).thenReturn(Set.of(subOrderId));

            assertThatThrownBy(() -> finalizeSettlementUseCase.execute(id))
                    .isInstanceOf(SettlementAlreadyFinalizedException.class)
                    .hasMessageContaining("already been settled and finalized in another settlement period");

            verify(settlementRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("3. Vendor Isolation & Access Control Tests")
    class VendorIsolationTests {

        @Test
        @DisplayName("Vendor xem chi tiết kỳ đối soát của chính mình -> thành công")
        void testVendorViewOwnSettlementSuccess() {
            UUID userId = UUID.randomUUID();
            AuthorizationSubject subject =
                    new AuthorizationSubject(
                            userId, "vendor@example.com", java.util.Set.of("VENDOR"), java.util.Set.of("READ")
                    );
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(subject.email(), null, Collections.emptyList());
            auth.setDetails(subject);
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

            when(vendorLookupPort.findVendorIdByUserId(userId)).thenReturn(Optional.of(vendorId));

            UUID settlementId = UUID.randomUUID();
            SettlementJpaEntity settlement = new SettlementJpaEntity();
            settlement.setId(settlementId);
            settlement.setVendorId(vendorId);
            settlement.setStatus(SettlementStatus.FINALIZED);

            when(settlementRepository.findById(settlementId)).thenReturn(Optional.of(settlement));
            when(lineItemRepository.findBySettlementId(settlementId)).thenReturn(Collections.emptyList());

            SettlementDetailResponse response = getSettlementDetailUseCase.getVendorSettlementDetail(settlementId);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(settlementId);
            assertThat(response.vendorId()).isEqualTo(vendorId);
        }

        @Test
        @DisplayName("Vendor A xem chi tiết kỳ đối soát của Vendor B -> ném UnauthorizedSettlementAccessException (403)")
        void testVendorACannotViewVendorBSettlement() {
            UUID userId = UUID.randomUUID();
            AuthorizationSubject subject =
                    new AuthorizationSubject(
                            userId, "vendorA@example.com", java.util.Set.of("VENDOR"), java.util.Set.of("READ")
                    );
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(subject.email(), null, Collections.emptyList());
            auth.setDetails(subject);
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

            UUID vendorAId = UUID.randomUUID();
            UUID vendorBId = UUID.randomUUID();
            when(vendorLookupPort.findVendorIdByUserId(userId)).thenReturn(Optional.of(vendorAId));

            UUID settlementId = UUID.randomUUID();
            SettlementJpaEntity settlement = new SettlementJpaEntity();
            settlement.setId(settlementId);
            settlement.setVendorId(vendorBId); // Thuộc Vendor B!

            when(settlementRepository.findById(settlementId)).thenReturn(Optional.of(settlement));

            assertThatThrownBy(() -> getSettlementDetailUseCase.getVendorSettlementDetail(settlementId))
                    .isInstanceOf(UnauthorizedSettlementAccessException.class)
                    .hasMessageContaining("another vendor's settlement");
        }

    }
}
