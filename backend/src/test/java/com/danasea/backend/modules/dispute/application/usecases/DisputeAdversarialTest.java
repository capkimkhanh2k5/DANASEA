package com.danasea.backend.modules.dispute.application.usecases;

import com.danasea.backend.modules.dispute.application.services.DisputeWindowService;
import com.danasea.backend.modules.dispute.domain.exceptions.*;
import com.danasea.backend.modules.dispute.domain.models.DisputeReason;
import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.dispute.infrastructure.persistence.entities.DisputeJpaEntity;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.dispute.presentation.dtos.CreateDisputeRequest;
import com.danasea.backend.modules.dispute.presentation.dtos.DisputeResponse;
import com.danasea.backend.modules.dispute.presentation.dtos.ResolveDisputeRequest;
import com.danasea.backend.modules.dispute.presentation.handlers.DisputeExceptionHandler;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.shared.presentation.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DisputeAdversarialTest — Empirical Adversarial Challenge Suite for Milestone 2")
class DisputeAdversarialTest {

    @Mock
    private JpaDisputeRepository disputeRepository;

    @Mock
    private JpaSubOrderRepository subOrderRepository;

    @Mock
    private JpaMasterOrderRepository masterOrderRepository;

    @Mock
    private JpaServiceSlotRepository slotRepository;

    @Mock
    private JpaRefundRepository refundRepository;

    private CreateDisputeUseCase createDisputeUseCase;
    private ResolveDisputeUseCase resolveDisputeUseCase;
    private DisputeExceptionHandler exceptionHandler;

    private UUID customerAId;
    private UUID customerBId;
    private UUID adminId;
    private UUID masterOrderIdA;
    private UUID masterOrderIdB;
    private UUID subOrderIdA;
    private UUID subOrderIdB;
    private UUID slotId;
    private UUID disputeId;

    @BeforeEach
    void setUp() {
        createDisputeUseCase = new CreateDisputeUseCase(
                disputeRepository, masterOrderRepository, subOrderRepository,
                new DisputeWindowService(slotRepository, Clock.systemDefaultZone()));
        resolveDisputeUseCase = new ResolveDisputeUseCase(
                disputeRepository, subOrderRepository, refundRepository);
        exceptionHandler = new DisputeExceptionHandler();

        customerAId = UUID.randomUUID();
        customerBId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        masterOrderIdA = UUID.randomUUID();
        masterOrderIdB = UUID.randomUUID();
        subOrderIdA = UUID.randomUUID();
        subOrderIdB = UUID.randomUUID();
        slotId = UUID.randomUUID();
        disputeId = UUID.randomUUID();
    }

    // =========================================================================
    // 1. 7-DAY BOUNDARY PRECISION TESTS
    // =========================================================================
    @Nested
    @DisplayName("Adversarial 7-Day Boundary Precision Tests")
    class SevenDayBoundaryAdversarialTests {

        @ParameterizedTest(name = "Days in past: {0} -> Should ALLOW dispute (within 7 days)")
        @ValueSource(ints = {0, 1, 3, 5, 6, 7})
        @DisplayName("Boundary PASS: Experience date within 7 days (today through day 7)")
        void testExperienceDate_Within7Days_Allowed(int daysAgo) {
            LocalDate experienceDate = LocalDate.now().minusDays(daysAgo);

            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(masterOrderIdA);
            masterOrder.setCustomerId(customerAId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderIdA);
            subOrder.setMasterOrderId(masterOrderIdA);
            subOrder.setStatus(SubOrderStatus.COMPLETED);
            subOrder.setSlotId(slotId);

            ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
            slot.setId(slotId);
            slot.setDate(experienceDate);
            slot.setStartTime(LocalTime.MIN);

            when(masterOrderRepository.findById(masterOrderIdA)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findById(subOrderIdA)).thenReturn(Optional.of(subOrder));
            when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
            when(disputeRepository.existsBySubOrderIdAndStatusIn(eq(subOrderIdA), any())).thenReturn(false);
            when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateDisputeRequest request = new CreateDisputeRequest(
                    subOrderIdA, DisputeReason.SERVICE_NOT_AS_DESCRIBED, "Test within boundary", null);

            DisputeResponse response = createDisputeUseCase.execute(masterOrderIdA, request, customerAId);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(DisputeStatus.OPEN);
        }

        @ParameterizedTest(name = "Days in past: {0} -> Should BLOCK dispute with 400 (exceeded 7 days)")
        @ValueSource(ints = {8, 9, 10, 14, 30, 90, 365})
        @DisplayName("Boundary FAIL: Experience date strictly after 7 days (day 8 and beyond)")
        void testExperienceDate_Exceeded7Days_BlockedWith400(int daysAgo) {
            LocalDate experienceDate = LocalDate.now().minusDays(daysAgo);

            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(masterOrderIdA);
            masterOrder.setCustomerId(customerAId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderIdA);
            subOrder.setMasterOrderId(masterOrderIdA);
            subOrder.setStatus(SubOrderStatus.COMPLETED);
            subOrder.setSlotId(slotId);

            ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
            slot.setId(slotId);
            slot.setDate(experienceDate);

            when(masterOrderRepository.findById(masterOrderIdA)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findById(subOrderIdA)).thenReturn(Optional.of(subOrder));
            when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

            CreateDisputeRequest request = new CreateDisputeRequest(
                    subOrderIdA, DisputeReason.SERVICE_NOT_AS_DESCRIBED, "Late dispute", null);

            assertThatThrownBy(() -> createDisputeUseCase.execute(masterOrderIdA, request, customerAId))
                    .isInstanceOf(DisputePeriodExpiredException.class)
                    .hasMessageContaining("within 7 days");

            verify(disputeRepository, never()).save(any());
        }

        @Test
        @DisplayName("Fallback Boundary: Slot ID present but missing in DB, SubOrder createdAt 7 days ago -> ALLOWED")
        void testFallbackCreatedAt_7DaysAgo_Allowed() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(masterOrderIdA);
            masterOrder.setCustomerId(customerAId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderIdA);
            subOrder.setMasterOrderId(masterOrderIdA);
            subOrder.setStatus(SubOrderStatus.COMPLETED);
            subOrder.setSlotId(slotId);
            subOrder.setCreatedAt(OffsetDateTime.now().minusDays(7));

            when(masterOrderRepository.findById(masterOrderIdA)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findById(subOrderIdA)).thenReturn(Optional.of(subOrder));
            when(slotRepository.findById(slotId)).thenReturn(Optional.empty()); // Slot not found
            when(disputeRepository.existsBySubOrderIdAndStatusIn(eq(subOrderIdA), any())).thenReturn(false);
            when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateDisputeRequest request = new CreateDisputeRequest(
                    subOrderIdA, DisputeReason.OTHER, "Fallback exactly day 7", null);

            DisputeResponse response = createDisputeUseCase.execute(masterOrderIdA, request, customerAId);
            assertThat(response.status()).isEqualTo(DisputeStatus.OPEN);
        }

        @Test
        @DisplayName("Fallback Boundary: Slot ID present but missing in DB, SubOrder createdAt 8 days ago -> BLOCKED (400)")
        void testFallbackCreatedAt_8DaysAgo_Blocked() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(masterOrderIdA);
            masterOrder.setCustomerId(customerAId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderIdA);
            subOrder.setMasterOrderId(masterOrderIdA);
            subOrder.setStatus(SubOrderStatus.COMPLETED);
            subOrder.setSlotId(slotId);
            subOrder.setCreatedAt(OffsetDateTime.now().minusDays(8));

            when(masterOrderRepository.findById(masterOrderIdA)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findById(subOrderIdA)).thenReturn(Optional.of(subOrder));
            when(slotRepository.findById(slotId)).thenReturn(Optional.empty()); // Slot not found

            CreateDisputeRequest request = new CreateDisputeRequest(
                    subOrderIdA, DisputeReason.OTHER, "Fallback day 8", null);

            assertThatThrownBy(() -> createDisputeUseCase.execute(masterOrderIdA, request, customerAId))
                    .isInstanceOf(DisputePeriodExpiredException.class)
                    .hasMessageContaining("within 7 days");

            verify(disputeRepository, never()).save(any());
        }

        @Test
        @DisplayName("Fallback Boundary: Both slotId and createdAt null -> Defaults to now -> ALLOWED")
        void testFallbackAllNull_DefaultsToNow_Allowed() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(masterOrderIdA);
            masterOrder.setCustomerId(customerAId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderIdA);
            subOrder.setMasterOrderId(masterOrderIdA);
            subOrder.setStatus(SubOrderStatus.COMPLETED);
            subOrder.setSlotId(null);
            subOrder.setCreatedAt(null);

            when(masterOrderRepository.findById(masterOrderIdA)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findById(subOrderIdA)).thenReturn(Optional.of(subOrder));
            when(disputeRepository.existsBySubOrderIdAndStatusIn(eq(subOrderIdA), any())).thenReturn(false);
            when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateDisputeRequest request = new CreateDisputeRequest(
                    subOrderIdA, DisputeReason.OTHER, "No dates available", null);

            DisputeResponse response = createDisputeUseCase.execute(masterOrderIdA, request, customerAId);
            assertThat(response.status()).isEqualTo(DisputeStatus.OPEN);
        }
    }

    // =========================================================================
    // 2. CROSS-TENANT IDOR SECURITY ATTACKS
    // =========================================================================
    @Nested
    @DisplayName("Adversarial Cross-Tenant IDOR Attack Tests")
    class CrossTenantIdorAdversarialTests {

        @Test
        @DisplayName("IDOR Attack 1: Customer B tries to dispute Customer A's MasterOrder -> 403 Forbidden")
        void testIdor_CustomerBTriesToDisputeCustomerAOrder_Throws403() {
            MasterOrderJpaEntity masterOrderA = new MasterOrderJpaEntity();
            masterOrderA.setId(masterOrderIdA);
            masterOrderA.setCustomerId(customerAId); // Belongs to Customer A

            when(masterOrderRepository.findById(masterOrderIdA)).thenReturn(Optional.of(masterOrderA));

            CreateDisputeRequest request = new CreateDisputeRequest(
                    subOrderIdA, DisputeReason.PAYMENT_ISSUE, "Attacker trying to dispute A's order", null);

            // Attacker Customer B attempts execution
            assertThatThrownBy(() -> createDisputeUseCase.execute(masterOrderIdA, request, customerBId))
                    .isInstanceOf(UnauthorizedDisputeAccessException.class)
                    .hasMessageContaining("User does not have permission");

            verify(disputeRepository, never()).save(any());
        }

        @Test
        @DisplayName("IDOR Attack 2: Customer A uses own MasterOrder A, but injects SubOrder B belonging to MasterOrder B -> 404 Not Found")
        void testIdor_SubOrderCrossWiringAttack_Throws404() {
            // Customer A owns MasterOrder A
            MasterOrderJpaEntity masterOrderA = new MasterOrderJpaEntity();
            masterOrderA.setId(masterOrderIdA);
            masterOrderA.setCustomerId(customerAId);

            // SubOrder B belongs to MasterOrder B (Customer B's order)
            SubOrderJpaEntity subOrderB = new SubOrderJpaEntity();
            subOrderB.setId(subOrderIdB);
            subOrderB.setMasterOrderId(masterOrderIdB); // Cross-wired to different master order
            subOrderB.setStatus(SubOrderStatus.COMPLETED);

            when(masterOrderRepository.findById(masterOrderIdA)).thenReturn(Optional.of(masterOrderA));
            when(subOrderRepository.findById(subOrderIdB)).thenReturn(Optional.of(subOrderB));

            CreateDisputeRequest request = new CreateDisputeRequest(
                    subOrderIdB, DisputeReason.OTHER, "Cross-wired subOrder injection", null);

            assertThatThrownBy(() -> createDisputeUseCase.execute(masterOrderIdA, request, customerAId))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("does not belong to order");

            verify(disputeRepository, never()).save(any());
        }

        @Test
        @DisplayName("IDOR Attack 3: MasterOrder has null customerId -> 403 Forbidden")
        void testIdor_MasterOrderNullCustomerId_Throws403() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(masterOrderIdA);
            masterOrder.setCustomerId(null); // Orphaned / unassigned order

            when(masterOrderRepository.findById(masterOrderIdA)).thenReturn(Optional.of(masterOrder));

            CreateDisputeRequest request = new CreateDisputeRequest(
                    subOrderIdA, DisputeReason.OTHER, "Orphan order attack", null);

            assertThatThrownBy(() -> createDisputeUseCase.execute(masterOrderIdA, request, customerAId))
                    .isInstanceOf(UnauthorizedDisputeAccessException.class);

            verify(disputeRepository, never()).save(any());
        }

        @Test
        @DisplayName("IDOR Attack 4: SubOrder has null masterOrderId -> 404 Not Found")
        void testIdor_SubOrderNullMasterOrderId_Throws404() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(masterOrderIdA);
            masterOrder.setCustomerId(customerAId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderIdA);
            subOrder.setMasterOrderId(null); // Corrupted subOrder linkage
            subOrder.setStatus(SubOrderStatus.COMPLETED);

            when(masterOrderRepository.findById(masterOrderIdA)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findById(subOrderIdA)).thenReturn(Optional.of(subOrder));

            CreateDisputeRequest request = new CreateDisputeRequest(
                    subOrderIdA, DisputeReason.OTHER, "Unlinked subOrder attack", null);

            assertThatThrownBy(() -> createDisputeUseCase.execute(masterOrderIdA, request, customerAId))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("does not belong to order");

            verify(disputeRepository, never()).save(any());
        }
    }

    // =========================================================================
    // 3. SUBORDER STATUS EXHAUSTIVE ISOLATION TESTS
    // =========================================================================
    @Nested
    @DisplayName("Adversarial SubOrder Status Isolation Tests")
    class SubOrderStatusIsolationAdversarialTests {

        @ParameterizedTest(name = "SubOrder Status: {0} -> Must be REJECTED with 400 Bad Request")
        @EnumSource(
                value = SubOrderStatus.class,
                names = {"PENDING", "REJECTED", "CANCELLED", "REFUNDED", "PARTIALLY_REFUNDED", "IN_PROGRESS"}
        )
        @DisplayName("Exhaustive check: All 6 non-eligible statuses MUST be rejected with InvalidSubOrderStateException")
        void testSubOrderStatus_AllInvalidStatuses_Throws400(SubOrderStatus invalidStatus) {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(masterOrderIdA);
            masterOrder.setCustomerId(customerAId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderIdA);
            subOrder.setMasterOrderId(masterOrderIdA);
            subOrder.setStatus(invalidStatus);

            when(masterOrderRepository.findById(masterOrderIdA)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findById(subOrderIdA)).thenReturn(Optional.of(subOrder));

            CreateDisputeRequest request = new CreateDisputeRequest(
                    subOrderIdA, DisputeReason.SERVICE_NOT_AS_DESCRIBED, "Invalid status test", null);

            assertThatThrownBy(() -> createDisputeUseCase.execute(masterOrderIdA, request, customerAId))
                    .isInstanceOf(InvalidSubOrderStateException.class)
                    .hasMessageContaining("COMPLETED or CONFIRMED");

            verify(disputeRepository, never()).save(any());
        }

        @ParameterizedTest(name = "SubOrder Status: {0} -> Must be ACCEPTED")
        @EnumSource(
                value = SubOrderStatus.class,
                names = {"COMPLETED", "CONFIRMED", "CHECKED_IN", "NO_SHOW"}
        )
        @DisplayName("Eligible statuses: COMPLETED, CONFIRMED, and CHECKED_IN are accepted")
        void testSubOrderStatus_EligibleStatuses_Accepted(SubOrderStatus validStatus) {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(masterOrderIdA);
            masterOrder.setCustomerId(customerAId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderIdA);
            subOrder.setMasterOrderId(masterOrderIdA);
            subOrder.setStatus(validStatus);
            subOrder.setCreatedAt(OffsetDateTime.now().minusDays(1));

            when(masterOrderRepository.findById(masterOrderIdA)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findById(subOrderIdA)).thenReturn(Optional.of(subOrder));
            when(disputeRepository.existsBySubOrderIdAndStatusIn(eq(subOrderIdA), any())).thenReturn(false);
            when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateDisputeRequest request = new CreateDisputeRequest(
                    subOrderIdA, DisputeReason.OTHER, "Eligible status test", null);

            DisputeResponse response = createDisputeUseCase.execute(masterOrderIdA, request, customerAId);
            assertThat(response.status()).isEqualTo(DisputeStatus.OPEN);
        }
    }

    // =========================================================================
    // 4. ADMIN RESOLUTION ADVERSARIAL TESTS & IDEMPOTENCY
    // =========================================================================
    @Nested
    @DisplayName("Adversarial Admin Resolution Edge Cases & Financial Precision")
    class AdminResolutionAdversarialTests {

        @ParameterizedTest(name = "Invalid percentage: {0} -> Must throw IllegalArgumentException")
        @ValueSource(strings = {"0.00", "-0.01", "-50.0", "100.01", "200.0", "999.99"})
        @DisplayName("Financial Boundary: Percentage outside (0, 100] throws IllegalArgumentException")
        void testResolveDispute_InvalidPercentage_ThrowsIllegalArgument(String invalidPctStr) {
            BigDecimal invalidPct = new BigDecimal(invalidPctStr);

            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setSubOrderId(subOrderIdA);
            dispute.setStatus(DisputeStatus.OPEN);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderIdA);
            subOrder.setSubtotalAmount(new BigDecimal("1000000.00"));

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderIdA)).thenReturn(Optional.of(subOrder));

            ResolveDisputeRequest request = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_PARTIAL, invalidPct, "Bad percentage test");

            assertThatThrownBy(() -> resolveDisputeUseCase.execute(disputeId, request, adminId))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(refundRepository, never()).save(any());
        }

        @Test
        @DisplayName("Partial Resolution with null percentage throws IllegalArgumentException")
        void testResolveDispute_PartialNullPercentage_ThrowsIllegalArgument() {
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setSubOrderId(subOrderIdA);
            dispute.setStatus(DisputeStatus.OPEN);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderIdA);
            subOrder.setSubtotalAmount(new BigDecimal("1000000.00"));

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderIdA)).thenReturn(Optional.of(subOrder));

            ResolveDisputeRequest request = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_PARTIAL, null, "Null partial percentage");

            assertThatThrownBy(() -> resolveDisputeUseCase.execute(disputeId, request, adminId))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(refundRepository, never()).save(any());
        }

        @Test
        @DisplayName("RESOLVED_PARTIAL with 100% creates a pending refund without claiming provider success")
        void testResolveDispute_PartialWith100Percent_CreatesPendingRefund() {
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setSubOrderId(subOrderIdA);
            dispute.setStatus(DisputeStatus.UNDER_REVIEW);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderIdA);
            subOrder.setSubtotalAmount(new BigDecimal("500000.00"));
            subOrder.setStatus(SubOrderStatus.COMPLETED);

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderIdA)).thenReturn(Optional.of(subOrder));
            when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ResolveDisputeRequest request = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_PARTIAL, new BigDecimal("100.0"), "100% via partial");

            DisputeResponse response = resolveDisputeUseCase.execute(disputeId, request, adminId);
            assertThat(response.status()).isEqualTo(DisputeStatus.RESOLVED_PARTIAL);

            ArgumentCaptor<RefundJpaEntity> captor = ArgumentCaptor.forClass(RefundJpaEntity.class);
            verify(refundRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(RefundStatus.PENDING);
            assertThat(captor.getValue().getRefundPercentage()).isEqualByComparingTo("100.0");
            assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.COMPLETED);
            verify(subOrderRepository, never()).save(any());
        }

        @ParameterizedTest(name = "Dispute resolved status: {0} -> Subsequent resolve throws 409 Conflict")
        @EnumSource(
                value = DisputeStatus.class,
                names = {"RESOLVED_REFUND", "RESOLVED_PARTIAL", "RESOLVED_REJECTED"}
        )
        @DisplayName("Idempotency: Any dispute with resolved status blocks further resolution with 409")
        void testResolveDispute_ExhaustiveIdempotency(DisputeStatus resolvedStatus) {
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setStatus(resolvedStatus);

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));

            ResolveDisputeRequest request = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_REFUND, new BigDecimal("100.0"), "Duplicate attempt");

            assertThatThrownBy(() -> resolveDisputeUseCase.execute(disputeId, request, adminId))
                    .isInstanceOf(DisputeAlreadyResolvedException.class)
                    .hasMessageContaining("already resolved");

            verify(refundRepository, never()).save(any());
            verify(subOrderRepository, never()).save(any());
        }
    }

    // =========================================================================
    // 5. EXCEPTION HANDLER HTTP STATUS MAPPING ADVERSARIAL VERIFICATION
    // =========================================================================
    @Nested
    @DisplayName("Adversarial Exception Handler HTTP Status Code Verification")
    class ExceptionHandlerAdversarialTests {

        @Test
        @DisplayName("UnauthorizedDisputeAccessException maps to HTTP 403 FORBIDDEN")
        void testHandler_UnauthorizedDisputeAccessException() {
            ResponseEntity<ErrorResponse> response = exceptionHandler.handleUnauthorizedDisputeAccess(
                    new UnauthorizedDisputeAccessException("Forbidden"));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody().code()).isEqualTo("ACCESS_DENIED");
        }

        @Test
        @DisplayName("DisputePeriodExpiredException maps to HTTP 400 BAD_REQUEST")
        void testHandler_DisputePeriodExpiredException() {
            ResponseEntity<ErrorResponse> response = exceptionHandler.handleDisputePeriodExpired(
                    new DisputePeriodExpiredException("Expired"));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo("DISPUTE_PERIOD_EXPIRED");
        }

        @Test
        @DisplayName("InvalidSubOrderStateException maps to HTTP 400 BAD_REQUEST")
        void testHandler_InvalidSubOrderStateException() {
            ResponseEntity<ErrorResponse> response = exceptionHandler.handleInvalidSubOrderState(
                    new InvalidSubOrderStateException("Invalid state"));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo("INVALID_SUB_ORDER_STATE");
        }

        @Test
        @DisplayName("DuplicateDisputeException maps to HTTP 409 CONFLICT")
        void testHandler_DuplicateDisputeException() {
            ResponseEntity<ErrorResponse> response = exceptionHandler.handleDuplicateDispute(
                    new DuplicateDisputeException("Duplicate"));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo("ACTIVE_DISPUTE_ALREADY_EXISTS");
        }

        @Test
        @DisplayName("DisputeAlreadyResolvedException maps to HTTP 409 CONFLICT")
        void testHandler_DisputeAlreadyResolvedException() {
            ResponseEntity<ErrorResponse> response = exceptionHandler.handleDisputeAlreadyResolved(
                    new DisputeAlreadyResolvedException("Already resolved"));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo("DISPUTE_ALREADY_RESOLVED");
        }

        @Test
        @DisplayName("OrderNotFoundException maps to HTTP 404 NOT_FOUND")
        void testHandler_OrderNotFoundException() {
            ResponseEntity<ErrorResponse> response = exceptionHandler.handleOrderNotFound(
                    new OrderNotFoundException("Not found"));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo("ORDER_NOT_FOUND");
        }

        @Test
        @DisplayName("DisputeNotFoundException maps to HTTP 404 NOT_FOUND")
        void testHandler_DisputeNotFoundException() {
            ResponseEntity<ErrorResponse> response = exceptionHandler.handleDisputeNotFound(
                    new DisputeNotFoundException("Not found"));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo("DISPUTE_NOT_FOUND");
        }

        @Test
        @DisplayName("InvalidDisputeResolutionException maps to HTTP 400 BAD_REQUEST")
        void testHandler_InvalidDisputeResolutionException() {
            ResponseEntity<ErrorResponse> response = exceptionHandler.handleInvalidDisputeResolution(
                    new InvalidDisputeResolutionException("Bad resolution"));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo("INVALID_DISPUTE_RESOLUTION");
        }
    }
}
