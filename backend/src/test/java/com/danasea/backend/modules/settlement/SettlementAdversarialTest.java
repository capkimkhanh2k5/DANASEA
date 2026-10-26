package com.danasea.backend.modules.settlement;

import com.danasea.backend.modules.booking.domain.ports.VendorLookupPort;
import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.settlement.application.dtos.GenerateSettlementRequest;
import com.danasea.backend.modules.settlement.application.dtos.SettlementDetailResponse;
import com.danasea.backend.modules.settlement.application.dtos.SettlementResponse;
import com.danasea.backend.modules.settlement.application.usecases.FinalizeSettlementUseCase;
import com.danasea.backend.modules.settlement.application.usecases.GenerateSettlementUseCase;
import com.danasea.backend.modules.settlement.application.usecases.GetSettlementsUseCase;
import com.danasea.backend.modules.settlement.domain.exceptions.InvalidCommissionRateException;
import com.danasea.backend.modules.settlement.domain.exceptions.InvalidOrderAmountException;
import com.danasea.backend.modules.settlement.domain.exceptions.InvalidSettlementPeriodException;
import com.danasea.backend.modules.settlement.domain.exceptions.SettlementAlreadyFinalizedException;
import com.danasea.backend.modules.settlement.domain.exceptions.SettlementNotFoundException;
import com.danasea.backend.modules.settlement.domain.exceptions.UnauthorizedSettlementAccessException;
import com.danasea.backend.modules.settlement.domain.models.LineItemExclusionReason;
import com.danasea.backend.modules.settlement.domain.models.Settlement;
import com.danasea.backend.modules.settlement.domain.models.SettlementLineItem;
import com.danasea.backend.modules.settlement.domain.models.SettlementStatus;
import com.danasea.backend.modules.settlement.domain.models.SubOrderCalculationContext;
import com.danasea.backend.modules.settlement.domain.services.SettlementCalculationEngine;
import com.danasea.backend.modules.settlement.infrastructure.persistence.entities.SettlementJpaEntity;
import com.danasea.backend.modules.settlement.infrastructure.persistence.mappers.SettlementMapper;
import com.danasea.backend.modules.settlement.infrastructure.persistence.repositories.JpaSettlementLineItemRepository;
import com.danasea.backend.modules.settlement.infrastructure.persistence.repositories.JpaSettlementRepository;
import com.danasea.backend.modules.settlement.presentation.controllers.VendorSettlementController;
import com.danasea.backend.modules.settlement.presentation.handlers.SettlementExceptionHandler;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.shared.presentation.ErrorResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ADVERSARIAL STRESS TEST SUITE FOR MILESTONE 4 (EPIC-07 R4: VENDOR SETTLEMENT)
 * Thực nghiệm kiểm thử đối kháng:
 * 1. Idempotency Guard (Bảo vệ tính bất biến của kỳ đã chốt 409 Conflict)
 * 2. In-place Recalculation khi DRAFT (Cập nhật không sinh bản ghi thừa, thay thế line items)
 * 3. Multi-tenant Vendor Isolation (403 Forbidden cho cross-vendor IDOR)
 * 4. Xử lý Biên Ngày kỳ đối soát (Bao hàm [start, end], loại trừ ngoài biên, đơn ngày start == end)
 * 5. Bất biến Kế toán & Làm tròn HALF_UP
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Settlement Adversarial Challenge Tests - Milestone 4")
public class SettlementAdversarialTest {

    @Mock
    private JpaSettlementRepository settlementRepository;

    @Mock
    private JpaSettlementLineItemRepository settlementLineItemRepository;

    @Mock
    private JpaSubOrderRepository subOrderRepository;

    @Mock
    private JpaRefundRepository refundRepository;

    @Mock
    private JpaDisputeRepository disputeRepository;

    @Mock
    private JpaServiceSlotRepository serviceSlotRepository;

    @Mock
    private VendorInternalApi vendorInternalApi;

    @Mock
    private VendorLookupPort vendorLookupPort;

    @Spy
    private SettlementCalculationEngine calculationEngine = new SettlementCalculationEngine();

    @Spy
    private SettlementMapper settlementMapper = new SettlementMapper();

    @InjectMocks
    private GenerateSettlementUseCase generateSettlementUseCase;

    @InjectMocks
    private GetSettlementsUseCase getSettlementsUseCase;

    @InjectMocks
    private FinalizeSettlementUseCase finalizeSettlementUseCase;

    private final SettlementExceptionHandler exceptionHandler = new SettlementExceptionHandler();

    private final UUID vendorAId = UUID.randomUUID();
    private final UUID vendorBId = UUID.randomUUID();
    private final LocalDate periodStart = LocalDate.of(2026, 9, 1);
    private final LocalDate periodEnd = LocalDate.of(2026, 9, 30);

    @BeforeEach
    void setUp() {
        lenient().when(vendorInternalApi.findById(any())).thenReturn(Optional.of(mock(Vendor.class)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================================
    // 1. ADVERSARIAL IDEMPOTENCY GUARD
    // =========================================================================
    @Nested
    @DisplayName("Adversarial Dimension 1: Idempotency & Re-generation Guard")
    class IdempotencyGuardAdversarialTests {

        @Test
        @DisplayName("ADVERSARIAL: Tái tạo kỳ settlement đã FINALIZED ném SettlementAlreadyFinalizedException -> HTTP 409")
        void adversarial_recreateFinalizedSettlement_ShouldThrow409Conflict() {
            SettlementJpaEntity finalizedSettlement = SettlementJpaEntity.builder()
                    .vendorId(vendorAId)
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .status(SettlementStatus.FINALIZED)
                    .grossAmount(new BigDecimal("5000000.00"))
                    .commissionAmount(new BigDecimal("500000.00"))
                    .netPayableAmount(new BigDecimal("4500000.00"))
                    .build();
            finalizedSettlement.setId(UUID.randomUUID());

            when(settlementRepository.findByVendorIdAndPeriodStartAndPeriodEnd(vendorAId, periodStart, periodEnd))
                    .thenReturn(Optional.of(finalizedSettlement));

            assertThatThrownBy(() -> generateSettlementUseCase.execute(vendorAId, periodStart, periodEnd))
                    .isInstanceOf(SettlementAlreadyFinalizedException.class)
                    .hasMessageContaining("already been finalized or paid");

            // Exception handler maps to HTTP 409 CONFLICT
            SettlementAlreadyFinalizedException ex = new SettlementAlreadyFinalizedException("Already finalized");
            ResponseEntity<ErrorResponse> errorResp = exceptionHandler.handleSettlementAlreadyFinalized(ex);
            assertThat(errorResp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(errorResp.getBody().code()).isEqualTo("SETTLEMENT_ALREADY_FINALIZED");

            verify(settlementRepository, never()).save(any());
            verify(settlementLineItemRepository, never()).deleteBySettlementId(any());
        }

        @Test
        @DisplayName("ADVERSARIAL: Tái tạo kỳ settlement đã PAID ném SettlementAlreadyFinalizedException -> HTTP 409")
        void adversarial_recreatePaidSettlement_ShouldThrow409Conflict() {
            SettlementJpaEntity paidSettlement = SettlementJpaEntity.builder()
                    .vendorId(vendorAId)
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .status(SettlementStatus.PAID)
                    .grossAmount(new BigDecimal("5000000.00"))
                    .commissionAmount(new BigDecimal("500000.00"))
                    .netPayableAmount(new BigDecimal("4500000.00"))
                    .build();
            paidSettlement.setId(UUID.randomUUID());

            when(settlementRepository.findByVendorIdAndPeriodStartAndPeriodEnd(vendorAId, periodStart, periodEnd))
                    .thenReturn(Optional.of(paidSettlement));

            assertThatThrownBy(() -> generateSettlementUseCase.execute(vendorAId, periodStart, periodEnd))
                    .isInstanceOf(SettlementAlreadyFinalizedException.class)
                    .hasMessageContaining("already been finalized or paid");

            verify(settlementRepository, never()).save(any());
            verify(settlementLineItemRepository, never()).deleteBySettlementId(any());
        }

        @Test
        @DisplayName("ADVERSARIAL: Tái tính toán in-place khi DRAFT thành công: xóa line items cũ, tái sử dụng ID, không bị 409")
        void adversarial_recalculateDraftInPlace_ShouldSucceedWithoutDuplicate() {
            UUID existingDraftId = UUID.randomUUID();
            SettlementJpaEntity existingDraft = SettlementJpaEntity.builder()
                    .vendorId(vendorAId)
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .status(SettlementStatus.DRAFT)
                    .grossAmount(new BigDecimal("1000000.00"))
                    .commissionAmount(new BigDecimal("100000.00"))
                    .netPayableAmount(new BigDecimal("900000.00"))
                    .build();
            existingDraft.setId(existingDraftId);

            when(settlementRepository.findByVendorIdAndPeriodStartAndPeriodEnd(vendorAId, periodStart, periodEnd))
                    .thenReturn(Optional.of(existingDraft));

            // New data: 2 orders totaling 3,000,000
            SubOrderJpaEntity o1 = new SubOrderJpaEntity();
            o1.setId(UUID.randomUUID());
            o1.setVendorId(vendorAId);
            o1.setStatus(SubOrderStatus.COMPLETED);
            o1.setSubtotalAmount(new BigDecimal("2000000.00"));
            o1.setCommissionRate(new BigDecimal("0.100000"));
            o1.setCreatedAt(periodStart.atTime(12, 0).atOffset(OffsetDateTime.now().getOffset()));

            SubOrderJpaEntity o2 = new SubOrderJpaEntity();
            o2.setId(UUID.randomUUID());
            o2.setVendorId(vendorAId);
            o2.setStatus(SubOrderStatus.COMPLETED);
            o2.setSubtotalAmount(new BigDecimal("1000000.00"));
            o2.setCommissionRate(new BigDecimal("0.100000"));
            o2.setCreatedAt(periodStart.atTime(12, 0).atOffset(OffsetDateTime.now().getOffset()));

            when(subOrderRepository.findByVendorIdAndStatusIn(eq(vendorAId), anyCollection()))
                    .thenReturn(List.of(o1, o2));
            when(disputeRepository.findBySubOrderIdInAndStatusIn(anyCollection(), anyCollection()))
                    .thenReturn(Collections.emptyList());
            when(refundRepository.findBySubOrderIdIn(anyCollection()))
                    .thenReturn(Collections.emptyList());
            when(settlementRepository.save(any(SettlementJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            SettlementResponse resp = generateSettlementUseCase.execute(vendorAId, periodStart, periodEnd);

            // Verify in-place recalculation semantics:
            assertThat(resp.id()).isEqualTo(existingDraftId);
            assertThat(resp.status()).isEqualTo(SettlementStatus.DRAFT);
            assertThat(resp.grossAmount()).isEqualByComparingTo(new BigDecimal("3000000.00"));
            assertThat(resp.commissionAmount()).isEqualByComparingTo(new BigDecimal("300000.00"));
            assertThat(resp.netPayableAmount()).isEqualByComparingTo(new BigDecimal("2700000.00"));

            // Verify old line items were wiped for this exact settlementId
            verify(settlementLineItemRepository).deleteBySettlementId(existingDraftId);
            // Verify exactly existing entity was saved (no duplicate entity)
            verify(settlementRepository).save(existingDraft);
            // Verify new line items were saved
            verify(settlementLineItemRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("ADVERSARIAL: Chặn chốt sổ lần 2 khi kỳ đối soát đã FINALIZED (409 Conflict)")
        void adversarial_finalizeTwice_ShouldThrow409() {
            UUID settlementId = UUID.randomUUID();
            SettlementJpaEntity finalized = SettlementJpaEntity.builder()
                    .vendorId(vendorAId)
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .status(SettlementStatus.FINALIZED)
                    .build();
            finalized.setId(settlementId);

            when(settlementRepository.findById(settlementId)).thenReturn(Optional.of(finalized));

            assertThatThrownBy(() -> finalizeSettlementUseCase.execute(settlementId))
                    .isInstanceOf(SettlementAlreadyFinalizedException.class)
                    .hasMessageContaining("cannot be finalized again");

            verify(settlementRepository, never()).save(any());
        }

        @Test
        @DisplayName("ADVERSARIAL: Chặn đánh dấu PAID khi Settlement chưa FINALIZED (IllegalStateException)")
        void adversarial_markAsPaidWhenDraft_ShouldThrowIllegalStateException() {
            Settlement settlement = Settlement.builder()
                    .id(UUID.randomUUID())
                    .vendorId(vendorAId)
                    .status(SettlementStatus.DRAFT)
                    .build();

            assertThatThrownBy(settlement::markAsPaid)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only FINALIZED settlements can be marked as PAID");
        }
    }

    // =========================================================================
    // 2. ADVERSARIAL MULTI-TENANT VENDOR ISOLATION (IDOR DEFENSE)
    // =========================================================================
    @Nested
    @DisplayName("Adversarial Dimension 2: Multi-tenant Vendor Isolation")
    class MultiTenantIsolationAdversarialTests {

        @Test
        @DisplayName("ADVERSARIAL IDOR: Vendor A gửi ID của Vendor B -> ném UnauthorizedSettlementAccessException -> HTTP 403 Forbidden")
        void adversarial_idor_VendorAAccessingVendorBSettlement_ShouldReturn403Forbidden() {
            UUID settlementBId = UUID.randomUUID();
            SettlementJpaEntity settlementOfVendorB = SettlementJpaEntity.builder()
                    .vendorId(vendorBId) // Thuộc Vendor B
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .status(SettlementStatus.FINALIZED)
                    .grossAmount(new BigDecimal("10000000.00"))
                    .commissionAmount(new BigDecimal("1000000.00"))
                    .netPayableAmount(new BigDecimal("9000000.00"))
                    .build();
            settlementOfVendorB.setId(settlementBId);

            when(settlementRepository.findById(settlementBId)).thenReturn(Optional.of(settlementOfVendorB));

            // Vendor A cố tình truy cập
            assertThatThrownBy(() -> getSettlementsUseCase.getVendorSettlementById(vendorAId, settlementBId))
                    .isInstanceOf(UnauthorizedSettlementAccessException.class)
                    .hasMessageContaining("permission to access settlement data of another vendor");

            // Exception handler maps to HTTP 403 FORBIDDEN
            UnauthorizedSettlementAccessException ex = new UnauthorizedSettlementAccessException("Forbidden cross-vendor access");
            ResponseEntity<ErrorResponse> errorResp = exceptionHandler.handleUnauthorizedSettlementAccess(ex);
            assertThat(errorResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(errorResp.getBody().code()).isEqualTo("FORBIDDEN_SETTLEMENT_ACCESS");
        }

        @Test
        @DisplayName("ADVERSARIAL: Vendor Settlement Controller cô lập truy vấn danh sách, chỉ lấy vendorId từ Security Context")
        void adversarial_vendorListQuery_StrictlyEnforcesCurrentVendorId() {
            UUID currentUserId = UUID.randomUUID();
            UUID actualVendorId = UUID.randomUUID();

            Authentication auth = mock(Authentication.class);
            when(auth.isAuthenticated()).thenReturn(true);
            when(auth.getName()).thenReturn(currentUserId.toString());
            SecurityContext secCtx = mock(SecurityContext.class);
            when(secCtx.getAuthentication()).thenReturn(auth);
            SecurityContextHolder.setContext(secCtx);

            when(vendorLookupPort.findVendorIdByUserId(currentUserId)).thenReturn(Optional.of(actualVendorId));

            Pageable pageable = PageRequest.of(0, 20);
            when(settlementRepository.findFiltered(eq(actualVendorId), any(), any(), any(), eq(pageable)))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));

            VendorSettlementController controller = new VendorSettlementController(getSettlementsUseCase, vendorLookupPort);
            ResponseEntity<Page<SettlementResponse>> response = controller.getVendorSettlements(null, null, null, pageable);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Kiểm chứng nghiêm ngặt: repository được gọi với đúng actualVendorId từ security context
            verify(settlementRepository).findFiltered(eq(actualVendorId), isNull(), isNull(), isNull(), eq(pageable));
            // Đảm bảo không bao giờ query với vendor khác hoặc vendorId null
            verify(settlementRepository, never()).findFiltered(isNull(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("ADVERSARIAL: Vendor Settlement Controller từ chối khi chưa xác thực (403 Forbidden)")
        void adversarial_vendorController_Unauthenticated_ShouldThrow403() {
            SecurityContextHolder.clearContext();
            VendorSettlementController controller = new VendorSettlementController(getSettlementsUseCase, vendorLookupPort);

            assertThatThrownBy(() -> controller.getVendorSettlements(null, null, null, PageRequest.of(0, 20)))
                    .isInstanceOf(UnauthorizedSettlementAccessException.class)
                    .hasMessageContaining("User is not authenticated");
        }
    }

    // =========================================================================
    // 3. ADVERSARIAL DATE BOUNDARY & FILTERING TESTS
    // =========================================================================
    @Nested
    @DisplayName("Adversarial Dimension 3: Period Date Boundary Handling")
    class DateBoundaryAdversarialTests {

        @Test
        @DisplayName("ADVERSARIAL: Đơn hàng ở đúng ngày periodStart và periodEnd được bao hàm; ngày kề ngoài bị loại trừ")
        void adversarial_dateBoundaries_ExactBorderInclusive_OuterBorderExclusive() {
            LocalDate start = LocalDate.of(2026, 9, 10);
            LocalDate end = LocalDate.of(2026, 9, 20);

            // SubOrder on periodStart (included)
            SubOrderJpaEntity onStart = new SubOrderJpaEntity();
            onStart.setId(UUID.randomUUID());
            onStart.setVendorId(vendorAId);
            onStart.setStatus(SubOrderStatus.COMPLETED);
            onStart.setSubtotalAmount(new BigDecimal("100000.00"));
            onStart.setCommissionRate(new BigDecimal("0.100000"));
            onStart.setCreatedAt(start.atStartOfDay().atOffset(OffsetDateTime.now().getOffset()));

            // SubOrder on periodEnd (included)
            SubOrderJpaEntity onEnd = new SubOrderJpaEntity();
            onEnd.setId(UUID.randomUUID());
            onEnd.setVendorId(vendorAId);
            onEnd.setStatus(SubOrderStatus.COMPLETED);
            onEnd.setSubtotalAmount(new BigDecimal("200000.00"));
            onEnd.setCommissionRate(new BigDecimal("0.100000"));
            onEnd.setCreatedAt(end.atStartOfDay().atOffset(OffsetDateTime.now().getOffset()));

            // SubOrder 1 day before start (excluded)
            SubOrderJpaEntity beforeStart = new SubOrderJpaEntity();
            beforeStart.setId(UUID.randomUUID());
            beforeStart.setVendorId(vendorAId);
            beforeStart.setStatus(SubOrderStatus.COMPLETED);
            beforeStart.setSubtotalAmount(new BigDecimal("300000.00"));
            beforeStart.setCommissionRate(new BigDecimal("0.100000"));
            beforeStart.setCreatedAt(start.minusDays(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset()));

            // SubOrder 1 day after end (excluded)
            SubOrderJpaEntity afterEnd = new SubOrderJpaEntity();
            afterEnd.setId(UUID.randomUUID());
            afterEnd.setVendorId(vendorAId);
            afterEnd.setStatus(SubOrderStatus.COMPLETED);
            afterEnd.setSubtotalAmount(new BigDecimal("400000.00"));
            afterEnd.setCommissionRate(new BigDecimal("0.100000"));
            afterEnd.setCreatedAt(end.plusDays(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset()));

            when(settlementRepository.findByVendorIdAndPeriodStartAndPeriodEnd(vendorAId, start, end))
                    .thenReturn(Optional.empty());
            when(subOrderRepository.findByVendorIdAndStatusIn(eq(vendorAId), anyCollection()))
                    .thenReturn(List.of(onStart, onEnd, beforeStart, afterEnd));
            when(disputeRepository.findBySubOrderIdInAndStatusIn(anyCollection(), anyCollection()))
                    .thenReturn(Collections.emptyList());
            when(refundRepository.findBySubOrderIdIn(anyCollection()))
                    .thenReturn(Collections.emptyList());
            when(settlementRepository.save(any(SettlementJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            SettlementResponse response = generateSettlementUseCase.execute(vendorAId, start, end);

            // Chỉ 2 đơn trong biên [start, end] được tính: 100k + 200k = 300k
            assertThat(response.grossAmount()).isEqualByComparingTo(new BigDecimal("300000.00"));
            assertThat(response.commissionAmount()).isEqualByComparingTo(new BigDecimal("30000.00"));
            assertThat(response.netPayableAmount()).isEqualByComparingTo(new BigDecimal("270000.00"));
        }

        @Test
        @DisplayName("ADVERSARIAL: Kỳ đối soát đơn ngày (periodStart == periodEnd) chỉ tính đơn trong ngày đó")
        void adversarial_singleDayPeriod_ShouldIncludeOnlyThatDay() {
            LocalDate singleDay = LocalDate.of(2026, 9, 15);

            SubOrderJpaEntity matchOrder = new SubOrderJpaEntity();
            matchOrder.setId(UUID.randomUUID());
            matchOrder.setVendorId(vendorAId);
            matchOrder.setStatus(SubOrderStatus.COMPLETED);
            matchOrder.setSubtotalAmount(new BigDecimal("500000.00"));
            matchOrder.setCommissionRate(new BigDecimal("0.100000"));
            matchOrder.setCreatedAt(singleDay.atStartOfDay().atOffset(OffsetDateTime.now().getOffset()));

            SubOrderJpaEntity otherDayOrder = new SubOrderJpaEntity();
            otherDayOrder.setId(UUID.randomUUID());
            otherDayOrder.setVendorId(vendorAId);
            otherDayOrder.setStatus(SubOrderStatus.COMPLETED);
            otherDayOrder.setSubtotalAmount(new BigDecimal("500000.00"));
            otherDayOrder.setCommissionRate(new BigDecimal("0.100000"));
            otherDayOrder.setCreatedAt(singleDay.plusDays(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset()));

            when(settlementRepository.findByVendorIdAndPeriodStartAndPeriodEnd(vendorAId, singleDay, singleDay))
                    .thenReturn(Optional.empty());
            when(subOrderRepository.findByVendorIdAndStatusIn(eq(vendorAId), anyCollection()))
                    .thenReturn(List.of(matchOrder, otherDayOrder));
            when(settlementRepository.save(any(SettlementJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            SettlementResponse response = generateSettlementUseCase.execute(vendorAId, singleDay, singleDay);

            assertThat(response.grossAmount()).isEqualByComparingTo(new BigDecimal("500000.00"));
            assertThat(response.commissionAmount()).isEqualByComparingTo(new BigDecimal("50000.00"));
            assertThat(response.netPayableAmount()).isEqualByComparingTo(new BigDecimal("450000.00"));
        }
    }

    // =========================================================================
    // 4. ADVERSARIAL FINANCIAL CALCULATIONS & INVARIANTS
    // =========================================================================
    @Nested
    @DisplayName("Adversarial Dimension 4: Financial Balance & Invariant Verification")
    class FinancialInvariantsAdversarialTests {

        @Test
        @DisplayName("ADVERSARIAL: Bất biến tài chính gross == commission + net luôn đúng tuyệt đối với số lẻ chia 3")
        void adversarial_accountingInvariant_StrictBalance_WithOddAmounts() {
            // Giá trị 333,333.33 VND với hoa hồng 15% (0.1500)
            BigDecimal subtotal = new BigDecimal("333333.33");
            BigDecimal rate = new BigDecimal("0.1500");

            SubOrderCalculationContext ctx = SubOrderCalculationContext.builder()
                    .subOrderId(UUID.randomUUID())
                    .status(SubOrderStatus.COMPLETED)
                    .subtotalAmount(subtotal)
                    .refundAmount(BigDecimal.ZERO)
                    .hasActiveDispute(false)
                    .commissionRate(rate)
                    .build();

            SettlementLineItem item = calculationEngine.calculateLineItem(ctx);

            // gross = 333,333.33
            // commission = 333,333.33 * 0.15 = 49,999.9995 -> 50,000.00
            // net = 333,333.33 - 50,000.00 = 283,333.33
            assertThat(item.getGrossAmount()).isEqualByComparingTo(new BigDecimal("333333.33"));
            assertThat(item.getCommissionAmount()).isEqualByComparingTo(new BigDecimal("50000.00"));
            assertThat(item.getNetAmount()).isEqualByComparingTo(new BigDecimal("283333.33"));

            // BẤT BIẾN KẾ TOÁN: Gross = Commission + Net
            assertThat(item.getGrossAmount()).isEqualByComparingTo(item.getCommissionAmount().add(item.getNetAmount()));
        }

        @Test
        @DisplayName("ADVERSARIAL: Hoàn tiền một phần cực lớn (99.99%) không gây âm tiền hay lệch số")
        void adversarial_hugePartialRefund_MaintainsFinancialSanity() {
            BigDecimal subtotal = new BigDecimal("1000000.00");
            BigDecimal refund = new BigDecimal("999900.00"); // Còn lại 100 VND
            BigDecimal rate = new BigDecimal("0.1000");      // 10%

            SubOrderCalculationContext ctx = SubOrderCalculationContext.builder()
                    .subOrderId(UUID.randomUUID())
                    .status(SubOrderStatus.PARTIALLY_REFUNDED)
                    .subtotalAmount(subtotal)
                    .refundAmount(refund)
                    .hasActiveDispute(false)
                    .commissionRate(rate)
                    .build();

            SettlementLineItem item = calculationEngine.calculateLineItem(ctx);

            assertThat(item.isExcluded()).isFalse();
            assertThat(item.getGrossAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(item.getCommissionAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
            assertThat(item.getNetAmount()).isEqualByComparingTo(new BigDecimal("90.00"));
            assertThat(item.getGrossAmount()).isEqualByComparingTo(item.getCommissionAmount().add(item.getNetAmount()));
        }

        @Test
        @DisplayName("ADVERSARIAL: Tỷ lệ hoa hồng vượt ngưỡng [0, 1] hoặc số tiền âm ném ngoại lệ 400 Bad Request")
        void adversarial_invalidParameters_ShouldThrow400BadRequest() {
            // Tỷ lệ hoa hồng âm
            SubOrderCalculationContext negativeRateCtx = SubOrderCalculationContext.builder()
                    .subOrderId(UUID.randomUUID())
                    .status(SubOrderStatus.COMPLETED)
                    .subtotalAmount(new BigDecimal("100000.00"))
                    .commissionRate(new BigDecimal("-0.05"))
                    .build();

            assertThatThrownBy(() -> calculationEngine.calculateLineItem(negativeRateCtx))
                    .isInstanceOf(InvalidCommissionRateException.class);

            // Tỷ lệ hoa hồng > 100%
            SubOrderCalculationContext overRateCtx = SubOrderCalculationContext.builder()
                    .subOrderId(UUID.randomUUID())
                    .status(SubOrderStatus.COMPLETED)
                    .subtotalAmount(new BigDecimal("100000.00"))
                    .commissionRate(new BigDecimal("1.0500"))
                    .build();

            assertThatThrownBy(() -> calculationEngine.calculateLineItem(overRateCtx))
                    .isInstanceOf(InvalidCommissionRateException.class);

            // Số tiền đơn hàng âm
            SubOrderCalculationContext negativeAmountCtx = SubOrderCalculationContext.builder()
                    .subOrderId(UUID.randomUUID())
                    .status(SubOrderStatus.COMPLETED)
                    .subtotalAmount(new BigDecimal("-50000.00"))
                    .commissionRate(new BigDecimal("0.1000"))
                    .build();

            assertThatThrownBy(() -> calculationEngine.calculateLineItem(negativeAmountCtx))
                    .isInstanceOf(InvalidOrderAmountException.class);
        }
    }
}
