package com.danasea.backend.modules.settlement.application.usecases;

import com.danasea.backend.modules.order.application.services.VendorFaultCompensationService;
import org.springframework.jdbc.core.JdbcTemplate;

import com.danasea.backend.modules.booking.domain.ports.VendorLookupPort;
import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
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
            assertThat(response.netPayableAmount()).isEqualByComparingTo("2250000.00");
            verify(lineItemRepository, org.mockito.Mockito.times(2)).save(any());
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
