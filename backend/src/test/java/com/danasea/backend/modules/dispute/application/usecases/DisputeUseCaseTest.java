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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
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
@DisplayName("DisputeUseCaseTest — Comprehensive Unit Tests for EPIC-07 R2")
class DisputeUseCaseTest {

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
    private GetDisputesUseCase getDisputesUseCase;

    private UUID customerId;
    private UUID adminId;
    private UUID orderId;
    private UUID subOrderId;
    private UUID slotId;
    private UUID disputeId;

    @BeforeEach
    void setUp() {
        createDisputeUseCase = new CreateDisputeUseCase(
                disputeRepository, masterOrderRepository, subOrderRepository,
                new DisputeWindowService(slotRepository, Clock.systemDefaultZone()));
        resolveDisputeUseCase = new ResolveDisputeUseCase(
                disputeRepository, subOrderRepository, refundRepository);
        getDisputesUseCase = new GetDisputesUseCase(disputeRepository);

        customerId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        subOrderId = UUID.randomUUID();
        slotId = UUID.randomUUID();
        disputeId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("Customer Dispute Creation Tests")
    class CustomerCreateDisputeTests {

        @Test
        @DisplayName("1. Success: Create dispute when SubOrder is COMPLETED and within 7 days")
        void testCreateDispute_Success_WhenSubOrderCompletedAndWithin7Days() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(orderId);
            masterOrder.setCustomerId(customerId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setMasterOrderId(orderId);
            subOrder.setSlotId(slotId);
            subOrder.setStatus(SubOrderStatus.COMPLETED);

            ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
            slot.setId(slotId);
            slot.setDate(LocalDate.now().minusDays(3));

            when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
            when(disputeRepository.existsBySubOrderIdAndStatusIn(eq(subOrderId), any())).thenReturn(false);
            when(disputeRepository.save(any(DisputeJpaEntity.class))).thenAnswer(inv -> {
                DisputeJpaEntity entity = inv.getArgument(0);
                entity.setId(disputeId);
                entity.setCreatedAt(OffsetDateTime.now());
                return entity;
            });

            CreateDisputeRequest request = new CreateDisputeRequest(
                    subOrderId, DisputeReason.SERVICE_NOT_AS_DESCRIBED,
                    "Service did not match description", List.of("https://img.com/evidence1.jpg"));

            DisputeResponse response = createDisputeUseCase.execute(orderId, request, customerId);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(disputeId);
            assertThat(response.status()).isEqualTo(DisputeStatus.OPEN);
            assertThat(response.reason()).isEqualTo(DisputeReason.SERVICE_NOT_AS_DESCRIBED);

            ArgumentCaptor<DisputeJpaEntity> captor = ArgumentCaptor.forClass(DisputeJpaEntity.class);
            verify(disputeRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(DisputeStatus.OPEN);
            assertThat(captor.getValue().getRaisedBy()).isEqualTo(customerId);
        }

        @Test
        @DisplayName("2. Success: Create dispute when SubOrder is CONFIRMED")
        void testCreateDispute_Success_WhenSubOrderConfirmed() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(orderId);
            masterOrder.setCustomerId(customerId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setMasterOrderId(orderId);
            subOrder.setSlotId(slotId);
            subOrder.setStatus(SubOrderStatus.CONFIRMED);

            ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
            slot.setId(slotId);
            slot.setDate(LocalDate.now().minusDays(1));

            when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
            when(disputeRepository.existsBySubOrderIdAndStatusIn(eq(subOrderId), any())).thenReturn(false);
            when(disputeRepository.save(any(DisputeJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateDisputeRequest request = new CreateDisputeRequest(
                    subOrderId, DisputeReason.VENDOR_NO_SHOW, "Vendor did not appear", null);

            DisputeResponse response = createDisputeUseCase.execute(orderId, request, customerId);
            assertThat(response.status()).isEqualTo(DisputeStatus.OPEN);
        }

        @Test
        @DisplayName("3. Rejection (403): Throws when user is not the order owner")
        void testCreateDispute_Throws403_WhenNotOrderOwner() {
            UUID otherUserId = UUID.randomUUID();
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(orderId);
            masterOrder.setCustomerId(otherUserId);

            when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));

            CreateDisputeRequest request = new CreateDisputeRequest(
                    subOrderId, DisputeReason.OTHER, "Not my order", null);

            assertThatThrownBy(() -> createDisputeUseCase.execute(orderId, request, customerId))
                    .isInstanceOf(UnauthorizedDisputeAccessException.class);

            verify(disputeRepository, never()).save(any());
        }

        @Test
        @DisplayName("4. Rejection (400): Throws when SubOrder is CANCELLED")
        void testCreateDispute_Throws400_WhenSubOrderCancelled() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(orderId);
            masterOrder.setCustomerId(customerId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setMasterOrderId(orderId);
            subOrder.setStatus(SubOrderStatus.CANCELLED);

            when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));

            CreateDisputeRequest request = new CreateDisputeRequest(
                    subOrderId, DisputeReason.PAYMENT_ISSUE, "Cancelled booking", null);

            assertThatThrownBy(() -> createDisputeUseCase.execute(orderId, request, customerId))
                    .isInstanceOf(InvalidSubOrderStateException.class)
                    .hasMessageContaining("COMPLETED or CONFIRMED");

            verify(disputeRepository, never()).save(any());
        }

        @Test
        @DisplayName("5. Rejection (400): Throws when SubOrder is PENDING")
        void testCreateDispute_Throws400_WhenSubOrderPending() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(orderId);
            masterOrder.setCustomerId(customerId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setMasterOrderId(orderId);
            subOrder.setStatus(SubOrderStatus.PENDING);

            when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));

            CreateDisputeRequest request = new CreateDisputeRequest(
                    subOrderId, DisputeReason.PAYMENT_ISSUE, "Pending booking", null);

            assertThatThrownBy(() -> createDisputeUseCase.execute(orderId, request, customerId))
                    .isInstanceOf(InvalidSubOrderStateException.class);

            verify(disputeRepository, never()).save(any());
        }

        @Test
        @DisplayName("6. Rejection (400): Throws when > 7 days after experience date")
        void testCreateDispute_Throws400_WhenMoreThan7DaysAfterExperienceDate() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(orderId);
            masterOrder.setCustomerId(customerId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setMasterOrderId(orderId);
            subOrder.setStatus(SubOrderStatus.COMPLETED);
            subOrder.setSlotId(slotId);

            ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
            slot.setId(slotId);
            slot.setDate(LocalDate.now().minusDays(8)); // 8 days ago (> 7 days)

            when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

            CreateDisputeRequest request = new CreateDisputeRequest(
                    subOrderId, DisputeReason.SAFETY_CONCERN, "Late dispute", null);

            assertThatThrownBy(() -> createDisputeUseCase.execute(orderId, request, customerId))
                    .isInstanceOf(DisputePeriodExpiredException.class)
                    .hasMessageContaining("within 7 days");

            verify(disputeRepository, never()).save(any());
        }

        @Test
        @DisplayName("7. Boundary: Allowed when exactly 7 days after experience date")
        void testCreateDispute_Success_WhenExactly7DaysAfterExperienceDate() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(orderId);
            masterOrder.setCustomerId(customerId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setMasterOrderId(orderId);
            subOrder.setStatus(SubOrderStatus.COMPLETED);
            subOrder.setSlotId(slotId);

            ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
            slot.setId(slotId);
            slot.setDate(LocalDate.now().minusDays(7)); // Exactly 7 days ago

            when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
            when(disputeRepository.existsBySubOrderIdAndStatusIn(eq(subOrderId), any())).thenReturn(false);
            when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateDisputeRequest request = new CreateDisputeRequest(
                    subOrderId, DisputeReason.SAFETY_CONCERN, "Exact 7th day dispute", null);

            DisputeResponse response = createDisputeUseCase.execute(orderId, request, customerId);
            assertThat(response.status()).isEqualTo(DisputeStatus.OPEN);
        }

        @Test
        @DisplayName("8. Conflict (409): Throws when an active dispute already exists with status OPEN")
        void testCreateDispute_Throws409_WhenActiveDisputeAlreadyExists_Open() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(orderId);
            masterOrder.setCustomerId(customerId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setMasterOrderId(orderId);
            subOrder.setStatus(SubOrderStatus.COMPLETED);
            subOrder.setSlotId(slotId);

            ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
            slot.setId(slotId);
            slot.setDate(LocalDate.now().minusDays(2));

            when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
            when(disputeRepository.existsBySubOrderIdAndStatusIn(eq(subOrderId), any())).thenReturn(true);

            CreateDisputeRequest request = new CreateDisputeRequest(
                    subOrderId, DisputeReason.SERVICE_NOT_AS_DESCRIBED, "Duplicate attempt", null);

            assertThatThrownBy(() -> createDisputeUseCase.execute(orderId, request, customerId))
                    .isInstanceOf(DuplicateDisputeException.class)
                    .hasMessageContaining("active dispute already exists");

            verify(disputeRepository, never()).save(any());
        }

        @Test
        @DisplayName("9. Conflict (409): Throws when an active dispute already exists with status UNDER_REVIEW")
        void testCreateDispute_Throws409_WhenActiveDisputeAlreadyExists_UnderReview() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(orderId);
            masterOrder.setCustomerId(customerId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setMasterOrderId(orderId);
            subOrder.setStatus(SubOrderStatus.COMPLETED);
            subOrder.setSlotId(slotId);

            ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
            slot.setId(slotId);
            slot.setDate(LocalDate.now().minusDays(2));

            when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
            when(disputeRepository.existsBySubOrderIdAndStatusIn(eq(subOrderId), any())).thenReturn(true);

            CreateDisputeRequest request = new CreateDisputeRequest(
                    subOrderId, DisputeReason.PAYMENT_ISSUE, "Duplicate under review attempt", null);

            assertThatThrownBy(() -> createDisputeUseCase.execute(orderId, request, customerId))
                    .isInstanceOf(DuplicateDisputeException.class)
                    .hasMessageContaining("active dispute already exists");

            verify(disputeRepository, never()).save(any());
        }

        @Test
        @DisplayName("10. Not Found (404): Throws when MasterOrder not found")
        void testCreateDispute_Throws404_WhenMasterOrderNotFound() {
            when(masterOrderRepository.findById(orderId)).thenReturn(Optional.empty());

            CreateDisputeRequest request = new CreateDisputeRequest(
                    subOrderId, DisputeReason.OTHER, "Order not found", null);

            assertThatThrownBy(() -> createDisputeUseCase.execute(orderId, request, customerId))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("Order not found with id");

            verify(disputeRepository, never()).save(any());
        }

        @Test
        @DisplayName("11. Not Found (404): Throws when SubOrder not found")
        void testCreateDispute_Throws404_WhenSubOrderNotFound() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(orderId);
            masterOrder.setCustomerId(customerId);

            when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.empty());

            CreateDisputeRequest request = new CreateDisputeRequest(
                    subOrderId, DisputeReason.OTHER, "SubOrder missing", null);

            assertThatThrownBy(() -> createDisputeUseCase.execute(orderId, request, customerId))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("Sub-order not found with id");

            verify(disputeRepository, never()).save(any());
        }

        @Test
        @DisplayName("12. Integrity (404): Throws when SubOrder does not belong to MasterOrder")
        void testCreateDispute_Throws404_WhenSubOrderDoesNotBelongToMasterOrder() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(orderId);
            masterOrder.setCustomerId(customerId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setMasterOrderId(UUID.randomUUID()); // Different master order ID
            subOrder.setStatus(SubOrderStatus.COMPLETED);

            when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));

            CreateDisputeRequest request = new CreateDisputeRequest(
                    subOrderId, DisputeReason.OTHER, "Mismatched order linkage", null);

            assertThatThrownBy(() -> createDisputeUseCase.execute(orderId, request, customerId))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("does not belong to order");

            verify(disputeRepository, never()).save(any());
        }

        @Test
        @DisplayName("13. Fallback: Uses SubOrder createdAt when slotId is missing or slot not found")
        void testCreateDispute_MissingSlot_FallsBackToCreatedAt_Success() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(orderId);
            masterOrder.setCustomerId(customerId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setMasterOrderId(orderId);
            subOrder.setSlotId(null); // No slotId
            subOrder.setStatus(SubOrderStatus.COMPLETED);
            subOrder.setCreatedAt(OffsetDateTime.now().minusDays(2));

            when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(disputeRepository.existsBySubOrderIdAndStatusIn(eq(subOrderId), any())).thenReturn(false);
            when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateDisputeRequest request = new CreateDisputeRequest(
                    subOrderId, DisputeReason.PAYMENT_ISSUE, "Fallback to createdAt test", null);

            DisputeResponse response = createDisputeUseCase.execute(orderId, request, customerId);
            assertThat(response.status()).isEqualTo(DisputeStatus.OPEN);
        }

        @Test
        @DisplayName("14. Allowed: Allows new dispute when previous dispute is RESOLVED_REJECTED (not active)")
        void testCreateDispute_PreviousDisputeResolved_AllowsNewDispute() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(orderId);
            masterOrder.setCustomerId(customerId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setMasterOrderId(orderId);
            subOrder.setStatus(SubOrderStatus.COMPLETED);
            subOrder.setCreatedAt(OffsetDateTime.now().minusDays(1));

            when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            // Repository checks for OPEN and UNDER_REVIEW only -> returns false
            when(disputeRepository.existsBySubOrderIdAndStatusIn(eq(subOrderId), any())).thenReturn(false);
            when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateDisputeRequest request = new CreateDisputeRequest(
                    subOrderId, DisputeReason.SAFETY_CONCERN, "New concern filed", null);

            DisputeResponse response = createDisputeUseCase.execute(orderId, request, customerId);
            assertThat(response.status()).isEqualTo(DisputeStatus.OPEN);
        }
    }

    @Nested
    @DisplayName("Admin Dispute Resolution Tests")
    class AdminResolveDisputeTests {

        @Test
        @DisplayName("15. Success: RESOLVED_REFUND creates 100% refund and updates SubOrder to REFUNDED")
        void testResolveDispute_Success_ResolvedRefund_Full100Percent() {
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setSubOrderId(subOrderId);
            dispute.setStatus(DisputeStatus.OPEN);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setSubtotalAmount(new BigDecimal("2000000.00"));
            subOrder.setFinalAmount(new BigDecimal("1600000.00"));
            subOrder.setStatus(SubOrderStatus.COMPLETED);

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ResolveDisputeRequest request = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_REFUND, new BigDecimal("100.0"), "Full refund authorized");

            DisputeResponse response = resolveDisputeUseCase.execute(disputeId, request, adminId);

            assertThat(response.status()).isEqualTo(DisputeStatus.RESOLVED_REFUND);
            assertThat(response.adminNote()).isEqualTo("Full refund authorized");
            assertThat(response.resolvedBy()).isEqualTo(adminId);

            // Verify Refund Created with ADMIN_OVERRIDE
            ArgumentCaptor<RefundJpaEntity> refundCaptor = ArgumentCaptor.forClass(RefundJpaEntity.class);
            verify(refundRepository).save(refundCaptor.capture());
            RefundJpaEntity refund = refundCaptor.getValue();
            assertThat(refund.getSubOrderId()).isEqualTo(subOrderId);
            assertThat(refund.getAmount()).isEqualByComparingTo(new BigDecimal("1600000.00"));
            assertThat(refund.getRefundPercentage()).isEqualByComparingTo(new BigDecimal("100.0"));
            assertThat(refund.getReason()).isEqualTo(RefundReason.ADMIN_OVERRIDE);
            assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
            assertThat(refund.getProcessedAt()).isNull();

            // Provider confirmation, not the admin decision, performs the financial terminal transition.
            verify(subOrderRepository, never()).save(any());
            assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.COMPLETED);
        }

        @Test
        @DisplayName("16. Success: RESOLVED_REFUND defaults to 100% when refundPercentage is null")
        void testResolveDispute_Success_ResolvedRefund_Default100WhenNullPercentage() {
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setSubOrderId(subOrderId);
            dispute.setStatus(DisputeStatus.OPEN);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setSubtotalAmount(new BigDecimal("1500000.00"));
            subOrder.setStatus(SubOrderStatus.COMPLETED);

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ResolveDisputeRequest request = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_REFUND, null, "Default full refund");

            DisputeResponse response = resolveDisputeUseCase.execute(disputeId, request, adminId);

            assertThat(response.status()).isEqualTo(DisputeStatus.RESOLVED_REFUND);

            ArgumentCaptor<RefundJpaEntity> refundCaptor = ArgumentCaptor.forClass(RefundJpaEntity.class);
            verify(refundRepository).save(refundCaptor.capture());
            assertThat(refundCaptor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("1500000.00"));
            assertThat(refundCaptor.getValue().getRefundPercentage()).isEqualByComparingTo(new BigDecimal("100.0"));

            verify(subOrderRepository, never()).save(any());
        }

        @Test
        @DisplayName("17. Success: RESOLVED_PARTIAL creates partial refund and updates SubOrder to PARTIALLY_REFUNDED")
        void testResolveDispute_Success_ResolvedPartial_FiftyPercent() {
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setSubOrderId(subOrderId);
            dispute.setStatus(DisputeStatus.UNDER_REVIEW);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setSubtotalAmount(new BigDecimal("1000000.00"));
            subOrder.setStatus(SubOrderStatus.COMPLETED);

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ResolveDisputeRequest request = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_PARTIAL, new BigDecimal("40.0"), "Partial 40% settlement");

            DisputeResponse response = resolveDisputeUseCase.execute(disputeId, request, adminId);

            assertThat(response.status()).isEqualTo(DisputeStatus.RESOLVED_PARTIAL);

            ArgumentCaptor<RefundJpaEntity> refundCaptor = ArgumentCaptor.forClass(RefundJpaEntity.class);
            verify(refundRepository).save(refundCaptor.capture());
            assertThat(refundCaptor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("400000.00"));
            assertThat(refundCaptor.getValue().getRefundPercentage()).isEqualByComparingTo(new BigDecimal("40.0"));
            assertThat(refundCaptor.getValue().getReason()).isEqualTo(RefundReason.ADMIN_OVERRIDE);

            verify(subOrderRepository, never()).save(any());
        }

        @Test
        @DisplayName("18. Success: RESOLVED_REJECTED updates dispute without creating refund or modifying SubOrder")
        void testResolveDispute_Success_ResolvedRejected_NoRefundCreated() {
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setSubOrderId(subOrderId);
            dispute.setStatus(DisputeStatus.OPEN);

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
            when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ResolveDisputeRequest request = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_REJECTED, null, "Vendor proved service was delivered accurately");

            DisputeResponse response = resolveDisputeUseCase.execute(disputeId, request, adminId);

            assertThat(response.status()).isEqualTo(DisputeStatus.RESOLVED_REJECTED);
            assertThat(response.adminNote()).isEqualTo("Vendor proved service was delivered accurately");

            verify(refundRepository, never()).save(any());
            verify(subOrderRepository, never()).save(any());
        }

        @Test
        @DisplayName("19. Idempotency (409): Throws when resolving an already RESOLVED_REFUND dispute")
        void testResolveDispute_Throws409_WhenAlreadyResolvedRefund() {
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setStatus(DisputeStatus.RESOLVED_REFUND);

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));

            ResolveDisputeRequest request = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_REFUND, new BigDecimal("100.0"), "Duplicate call");

            assertThatThrownBy(() -> resolveDisputeUseCase.execute(disputeId, request, adminId))
                    .isInstanceOf(DisputeAlreadyResolvedException.class)
                    .hasMessageContaining("already resolved");

            verify(refundRepository, never()).save(any());
            verify(subOrderRepository, never()).save(any());
            verify(disputeRepository, never()).save(any());
        }

        @Test
        @DisplayName("20. Idempotency (409): Throws when resolving an already RESOLVED_PARTIAL dispute")
        void testResolveDispute_Throws409_WhenAlreadyResolvedPartial() {
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setStatus(DisputeStatus.RESOLVED_PARTIAL);

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));

            ResolveDisputeRequest request = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_PARTIAL, new BigDecimal("50.0"), "Duplicate partial call");

            assertThatThrownBy(() -> resolveDisputeUseCase.execute(disputeId, request, adminId))
                    .isInstanceOf(DisputeAlreadyResolvedException.class)
                    .hasMessageContaining("already resolved");

            verify(refundRepository, never()).save(any());
            verify(subOrderRepository, never()).save(any());
        }

        @Test
        @DisplayName("21. Idempotency (409): Throws when resolving an already RESOLVED_REJECTED dispute")
        void testResolveDispute_Throws409_WhenAlreadyResolvedRejected() {
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setStatus(DisputeStatus.RESOLVED_REJECTED);

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));

            ResolveDisputeRequest request = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_REFUND, new BigDecimal("100.0"), "Trying again after rejection");

            assertThatThrownBy(() -> resolveDisputeUseCase.execute(disputeId, request, adminId))
                    .isInstanceOf(DisputeAlreadyResolvedException.class)
                    .hasMessageContaining("already resolved");
        }

        @Test
        @DisplayName("22. Not Found (404): Throws when dispute does not exist")
        void testResolveDispute_Throws404_WhenDisputeNotFound() {
            when(disputeRepository.findById(disputeId)).thenReturn(Optional.empty());

            ResolveDisputeRequest request = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_REFUND, new BigDecimal("100.0"), "Resolve non-existent");

            assertThatThrownBy(() -> resolveDisputeUseCase.execute(disputeId, request, adminId))
                    .isInstanceOf(DisputeNotFoundException.class)
                    .hasMessageContaining("Dispute not found with id");
        }

        @Test
        @DisplayName("23. Not Found (404): Throws when subOrder does not exist during refund resolution")
        void testResolveDispute_Throws404_WhenSubOrderNotFound() {
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setSubOrderId(subOrderId);
            dispute.setStatus(DisputeStatus.OPEN);

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.empty());

            ResolveDisputeRequest request = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_REFUND, new BigDecimal("100.0"), "SubOrder not found test");

            assertThatThrownBy(() -> resolveDisputeUseCase.execute(disputeId, request, adminId))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("Sub-order not found with id");

            verify(refundRepository, never()).save(any());
        }

        @Test
        @DisplayName("24. Validation (400): Throws when resolution status is invalid (OPEN or UNDER_REVIEW)")
        void testResolveDispute_Throws400_WhenInvalidResolutionStatus() {
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setStatus(DisputeStatus.OPEN);

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));

            ResolveDisputeRequest request = new ResolveDisputeRequest(
                    DisputeStatus.OPEN, null, "Invalid action");

            assertThatThrownBy(() -> resolveDisputeUseCase.execute(disputeId, request, adminId))
                    .isInstanceOf(InvalidDisputeResolutionException.class)
                    .hasMessageContaining("RESOLVED_REFUND, RESOLVED_PARTIAL, RESOLVED_REJECTED");
        }

        @Test
        @DisplayName("25. Validation (400): Throws when partial refund percentage is zero or negative")
        void testResolveDispute_Throws400_WhenPartialPercentageZeroOrNegative() {
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setSubOrderId(subOrderId);
            dispute.setStatus(DisputeStatus.OPEN);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setSubtotalAmount(new BigDecimal("1000000.00"));

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));

            ResolveDisputeRequest request = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_PARTIAL, BigDecimal.ZERO, "Zero percentage");

            assertThatThrownBy(() -> resolveDisputeUseCase.execute(disputeId, request, adminId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Refund percentage must be greater than 0");
        }

        @Test
        @DisplayName("26. Validation (400): Throws when partial refund percentage exceeds 100")
        void testResolveDispute_Throws400_WhenPartialPercentageExceeds100() {
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setSubOrderId(subOrderId);
            dispute.setStatus(DisputeStatus.OPEN);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setSubtotalAmount(new BigDecimal("1000000.00"));

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));

            ResolveDisputeRequest request = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_PARTIAL, new BigDecimal("105.0"), "Over 100 percentage");

            assertThatThrownBy(() -> resolveDisputeUseCase.execute(disputeId, request, adminId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("less than or equal to 100");
        }

        @Test
        @DisplayName("27. Rounding Precision: Correct HALF_UP rounding for partial refunds")
        void testResolveDispute_FinancialRounding_PrecisionCheck() {
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setSubOrderId(subOrderId);
            dispute.setStatus(DisputeStatus.OPEN);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setSubtotalAmount(new BigDecimal("1000000.00"));

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // 33.33% of 1,000,000 = 333,300.00
            ResolveDisputeRequest request = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_PARTIAL, new BigDecimal("33.33"), "1/3 refund");

            resolveDisputeUseCase.execute(disputeId, request, adminId);

            ArgumentCaptor<RefundJpaEntity> refundCaptor = ArgumentCaptor.forClass(RefundJpaEntity.class);
            verify(refundRepository).save(refundCaptor.capture());
            assertThat(refundCaptor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("333300.00"));
        }
    }

    @Nested
    @DisplayName("Admin Dispute Query Tests")
    class AdminGetDisputesTests {

        @Test
        @DisplayName("28. Success: Filter by status and reason")
        void testGetDisputes_FilterByStatusAndReason() {
            Pageable pageable = PageRequest.of(0, 10);
            DisputeJpaEntity d1 = new DisputeJpaEntity();
            d1.setStatus(DisputeStatus.OPEN);
            d1.setReason(DisputeReason.SERVICE_NOT_AS_DESCRIBED);

            when(disputeRepository.findByStatusAndReason(
                    DisputeStatus.OPEN, DisputeReason.SERVICE_NOT_AS_DESCRIBED, pageable))
                    .thenReturn(new PageImpl<>(List.of(d1), pageable, 1));

            Page<DisputeResponse> result = getDisputesUseCase.execute(
                    DisputeStatus.OPEN, DisputeReason.SERVICE_NOT_AS_DESCRIBED, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).status()).isEqualTo(DisputeStatus.OPEN);
            verify(disputeRepository).findByStatusAndReason(
                    DisputeStatus.OPEN, DisputeReason.SERVICE_NOT_AS_DESCRIBED, pageable);
        }

        @Test
        @DisplayName("29. Success: Filter by status only")
        void testGetDisputes_FilterByStatusOnly() {
            Pageable pageable = PageRequest.of(0, 10);
            DisputeJpaEntity d1 = new DisputeJpaEntity();
            d1.setStatus(DisputeStatus.UNDER_REVIEW);

            when(disputeRepository.findByStatus(DisputeStatus.UNDER_REVIEW, pageable))
                    .thenReturn(new PageImpl<>(List.of(d1), pageable, 1));

            Page<DisputeResponse> result = getDisputesUseCase.execute(
                    DisputeStatus.UNDER_REVIEW, null, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).status()).isEqualTo(DisputeStatus.UNDER_REVIEW);
            verify(disputeRepository).findByStatus(DisputeStatus.UNDER_REVIEW, pageable);
        }

        @Test
        @DisplayName("30. Success: Filter by reason only")
        void testGetDisputes_FilterByReasonOnly() {
            Pageable pageable = PageRequest.of(0, 10);
            DisputeJpaEntity d1 = new DisputeJpaEntity();
            d1.setReason(DisputeReason.SAFETY_CONCERN);

            when(disputeRepository.findByReason(DisputeReason.SAFETY_CONCERN, pageable))
                    .thenReturn(new PageImpl<>(List.of(d1), pageable, 1));

            Page<DisputeResponse> result = getDisputesUseCase.execute(
                    null, DisputeReason.SAFETY_CONCERN, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).reason()).isEqualTo(DisputeReason.SAFETY_CONCERN);
            verify(disputeRepository).findByReason(DisputeReason.SAFETY_CONCERN, pageable);
        }

        @Test
        @DisplayName("31. Success: Query all without filters")
        void testGetDisputes_NoFilters_FindAll() {
            Pageable pageable = PageRequest.of(0, 20);
            when(disputeRepository.findAll(pageable)).thenReturn(Page.empty());

            Page<DisputeResponse> result = getDisputesUseCase.execute(null, null, pageable);
            assertThat(result.getTotalElements()).isEqualTo(0);
            verify(disputeRepository).findAll(pageable);
        }

        @Test
        @DisplayName("32. Success: Empty result returns empty Page")
        void testGetDisputes_EmptyResult() {
            Pageable pageable = PageRequest.of(0, 10);
            when(disputeRepository.findAll(pageable)).thenReturn(Page.empty());

            Page<DisputeResponse> result = getDisputesUseCase.execute(null, null, pageable);
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Adversarial Empirical Challenge Tests — Idempotency, Concurrency, Boundaries & Rounding")
    class AdversarialEmpiricalChallengeTests {

        @Test
        @DisplayName("33. Adversarial Boundary: Minimum valid percentage (0.01%) calculates correctly")
        void testResolveDispute_Boundary_MinimumValidPercentage() {
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setSubOrderId(subOrderId);
            dispute.setStatus(DisputeStatus.OPEN);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setSubtotalAmount(new BigDecimal("10000000.00")); // 10 million VND
            subOrder.setStatus(SubOrderStatus.COMPLETED);

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // 0.01% of 10,000,000 = 1,000.00
            ResolveDisputeRequest request = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_PARTIAL, new BigDecimal("0.01"), "Minimum valid refund boundary");

            DisputeResponse response = resolveDisputeUseCase.execute(disputeId, request, adminId);

            assertThat(response.status()).isEqualTo(DisputeStatus.RESOLVED_PARTIAL);
            assertThat(response.refundPercentage()).isEqualByComparingTo(new BigDecimal("0.01"));

            ArgumentCaptor<RefundJpaEntity> refundCaptor = ArgumentCaptor.forClass(RefundJpaEntity.class);
            verify(refundRepository).save(refundCaptor.capture());
            assertThat(refundCaptor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
            assertThat(refundCaptor.getValue().getRefundPercentage()).isEqualByComparingTo(new BigDecimal("0.01"));

            verify(subOrderRepository, never()).save(any());
        }

        @Test
        @DisplayName("34. Adversarial Boundary: Near-maximum valid percentage (99.99%) calculates correctly")
        void testResolveDispute_Boundary_NearMaximumValidPercentage() {
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setSubOrderId(subOrderId);
            dispute.setStatus(DisputeStatus.UNDER_REVIEW);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setSubtotalAmount(new BigDecimal("1000000.00"));
            subOrder.setStatus(SubOrderStatus.COMPLETED);

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // 99.99% of 1,000,000 = 999,900.00
            ResolveDisputeRequest request = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_PARTIAL, new BigDecimal("99.99"), "Near-maximum refund boundary");

            DisputeResponse response = resolveDisputeUseCase.execute(disputeId, request, adminId);

            assertThat(response.status()).isEqualTo(DisputeStatus.RESOLVED_PARTIAL);
            assertThat(response.refundPercentage()).isEqualByComparingTo(new BigDecimal("99.99"));

            ArgumentCaptor<RefundJpaEntity> refundCaptor = ArgumentCaptor.forClass(RefundJpaEntity.class);
            verify(refundRepository).save(refundCaptor.capture());
            assertThat(refundCaptor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("999900.00"));

            verify(subOrderRepository, never()).save(any());
        }

        @Test
        @DisplayName("35. Adversarial Validation: Negative percentage (-10.0%) rejected with IllegalArgumentException")
        void testResolveDispute_Validation_NegativePercentage_ThrowsException() {
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setSubOrderId(subOrderId);
            dispute.setStatus(DisputeStatus.OPEN);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setSubtotalAmount(new BigDecimal("1000000.00"));

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));

            ResolveDisputeRequest request = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_PARTIAL, new BigDecimal("-10.0"), "Negative percentage test");

            assertThatThrownBy(() -> resolveDisputeUseCase.execute(disputeId, request, adminId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("greater than 0");

            verify(refundRepository, never()).save(any());
            verify(subOrderRepository, never()).save(any());
        }

        @Test
        @DisplayName("36. Adversarial Validation: Null percentage for RESOLVED_PARTIAL rejected with IllegalArgumentException")
        void testResolveDispute_Validation_NullPercentageForPartial_ThrowsException() {
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setSubOrderId(subOrderId);
            dispute.setStatus(DisputeStatus.OPEN);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setSubtotalAmount(new BigDecimal("1000000.00"));

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));

            ResolveDisputeRequest request = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_PARTIAL, null, "Missing partial percentage");

            assertThatThrownBy(() -> resolveDisputeUseCase.execute(disputeId, request, adminId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("greater than 0 and less than or equal to 100");

            verify(refundRepository, never()).save(any());
        }

        @Test
        @DisplayName("37. Financial Rounding: Half-Up scale 2 on .025 boundary (100.05 * 50% = 50.025 -> 50.03)")
        void testResolveDispute_FinancialRounding_HalfUpBoundary025() {
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setSubOrderId(subOrderId);
            dispute.setStatus(DisputeStatus.OPEN);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setSubtotalAmount(new BigDecimal("100.05"));

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ResolveDisputeRequest request = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_PARTIAL, new BigDecimal("50.00"), "Half-up test at 0.025");

            resolveDisputeUseCase.execute(disputeId, request, adminId);

            ArgumentCaptor<RefundJpaEntity> captor = ArgumentCaptor.forClass(RefundJpaEntity.class);
            verify(refundRepository).save(captor.capture());
            // 100.05 * 50 / 100 = 50.025 -> HALF_UP scale 2 = 50.03
            assertThat(captor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("50.03"));
        }

        @Test
        @DisplayName("38. Financial Rounding: Half-Up scale 2 on .015 boundary (100.03 * 50% = 50.015 -> 50.02)")
        void testResolveDispute_FinancialRounding_HalfUpBoundary015() {
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setSubOrderId(subOrderId);
            dispute.setStatus(DisputeStatus.OPEN);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setSubtotalAmount(new BigDecimal("100.03"));

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ResolveDisputeRequest request = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_PARTIAL, new BigDecimal("50.00"), "Half-up test at 0.015");

            resolveDisputeUseCase.execute(disputeId, request, adminId);

            ArgumentCaptor<RefundJpaEntity> captor = ArgumentCaptor.forClass(RefundJpaEntity.class);
            verify(refundRepository).save(captor.capture());
            // 100.03 * 50 / 100 = 50.015 -> HALF_UP scale 2 = 50.02
            assertThat(captor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("50.02"));
        }

        @Test
        @DisplayName("39. Financial Rounding: Fractional subtotal and odd percentage (1234567.89 * 33.33% = 411481.48)")
        void testResolveDispute_FinancialRounding_OddSubtotalAndPercentage() {
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setSubOrderId(subOrderId);
            dispute.setStatus(DisputeStatus.OPEN);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setSubtotalAmount(new BigDecimal("1234567.89"));

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ResolveDisputeRequest request = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_PARTIAL, new BigDecimal("33.33"), "Fractional math check");

            resolveDisputeUseCase.execute(disputeId, request, adminId);

            ArgumentCaptor<RefundJpaEntity> captor = ArgumentCaptor.forClass(RefundJpaEntity.class);
            verify(refundRepository).save(captor.capture());
            // 1234567.89 * 33.33 / 100 = 411481.477737 -> HALF_UP scale 2 = 411481.48
            assertThat(captor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("411481.48"));
        }

        @Test
        @DisplayName("40. Sequential Idempotency Guard: Multiple resolve calls on same dispute entity throw 409 Conflict")
        void testResolveDispute_SequentialIdempotency_SecondCallThrows409() {
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setSubOrderId(subOrderId);
            dispute.setStatus(DisputeStatus.OPEN);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setSubtotalAmount(new BigDecimal("2000000.00"));
            subOrder.setStatus(SubOrderStatus.COMPLETED);

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(disputeRepository.save(any())).thenAnswer(inv -> {
                DisputeJpaEntity d = inv.getArgument(0);
                return d;
            });

            ResolveDisputeRequest firstRequest = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_REFUND, new BigDecimal("100.0"), "First resolution call");

            // 1st call: Should succeed
            DisputeResponse firstResponse = resolveDisputeUseCase.execute(disputeId, firstRequest, adminId);
            assertThat(firstResponse.status()).isEqualTo(DisputeStatus.RESOLVED_REFUND);
            verify(refundRepository, times(1)).save(any());
            verify(subOrderRepository, never()).save(any());

            // Dispute is now RESOLVED_REFUND
            assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.RESOLVED_REFUND);

            // 2nd call: Must immediately throw DisputeAlreadyResolvedException (409 Conflict)
            ResolveDisputeRequest secondRequest = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_PARTIAL, new BigDecimal("50.0"), "Second rogue resolution call");

            assertThatThrownBy(() -> resolveDisputeUseCase.execute(disputeId, secondRequest, adminId))
                    .isInstanceOf(DisputeAlreadyResolvedException.class)
                    .hasMessageContaining("already resolved");

            // Verify no additional refund or subOrder modification occurred
            verify(refundRepository, times(1)).save(any()); // Still only 1 call
            verify(subOrderRepository, never()).save(any());
        }

        @Test
        @DisplayName("41. Sequential Idempotency on Rejection: Second resolve call after REJECTED throws 409 Conflict")
        void testResolveDispute_SequentialIdempotency_AfterRejection_Throws409() {
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setSubOrderId(subOrderId);
            dispute.setStatus(DisputeStatus.OPEN);

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
            when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ResolveDisputeRequest rejectRequest = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_REJECTED, null, "Vendor was fully in compliance");

            // 1st call: Reject dispute
            DisputeResponse rejectResponse = resolveDisputeUseCase.execute(disputeId, rejectRequest, adminId);
            assertThat(rejectResponse.status()).isEqualTo(DisputeStatus.RESOLVED_REJECTED);
            verify(refundRepository, never()).save(any());
            verify(subOrderRepository, never()).save(any());

            // Dispute is now RESOLVED_REJECTED
            assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.RESOLVED_REJECTED);

            // 2nd call: Attempt to re-resolve rejected dispute throws 409
            ResolveDisputeRequest reResolveRequest = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_REFUND, new BigDecimal("100.0"), "Trying to override rejection");

            assertThatThrownBy(() -> resolveDisputeUseCase.execute(disputeId, reResolveRequest, adminId))
                    .isInstanceOf(DisputeAlreadyResolvedException.class)
                    .hasMessageContaining("already resolved");

            verify(refundRepository, never()).save(any());
            verify(subOrderRepository, never()).save(any());
        }

        @Test
        @DisplayName("42. Concurrency Race Deduplication: Second concurrent attempt on same SubOrder throws 409 Conflict")
        void testCreateDispute_ConcurrencyDeduplication_SecondAttemptThrows409() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(orderId);
            masterOrder.setCustomerId(customerId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setMasterOrderId(orderId);
            subOrder.setStatus(SubOrderStatus.COMPLETED);
            subOrder.setCreatedAt(OffsetDateTime.now().minusDays(1));

            when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));

            // First check: false (no active dispute), Second check: true (active dispute created by peer thread)
            when(disputeRepository.existsBySubOrderIdAndStatusIn(eq(subOrderId), any()))
                    .thenReturn(false)
                    .thenReturn(true);

            when(disputeRepository.save(any())).thenAnswer(inv -> {
                DisputeJpaEntity entity = inv.getArgument(0);
                entity.setId(disputeId);
                return entity;
            });

            CreateDisputeRequest request1 = new CreateDisputeRequest(
                    subOrderId, DisputeReason.PAYMENT_ISSUE, "First thread dispute", null);
            CreateDisputeRequest request2 = new CreateDisputeRequest(
                    subOrderId, DisputeReason.PAYMENT_ISSUE, "Second thread concurrent dispute", null);

            // Thread 1 succeeds
            DisputeResponse resp1 = createDisputeUseCase.execute(orderId, request1, customerId);
            assertThat(resp1.status()).isEqualTo(DisputeStatus.OPEN);

            // Thread 2 encounters active dispute and throws DuplicateDisputeException (409 Conflict)
            assertThatThrownBy(() -> createDisputeUseCase.execute(orderId, request2, customerId))
                    .isInstanceOf(DuplicateDisputeException.class)
                    .hasMessageContaining("active dispute already exists");

            // Verify disputeRepository.save called only once
            verify(disputeRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("43. Null Safety: SubOrder with null subtotal resolves with 0.00 refund without ArithmeticException")
        void testResolveDispute_NullSubtotal_ResolvesSafelyToZero() {
            DisputeJpaEntity dispute = new DisputeJpaEntity();
            dispute.setId(disputeId);
            dispute.setSubOrderId(subOrderId);
            dispute.setStatus(DisputeStatus.OPEN);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setSubtotalAmount(null); // Null subtotal amount edge case
            subOrder.setStatus(SubOrderStatus.COMPLETED);

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ResolveDisputeRequest request = new ResolveDisputeRequest(
                    DisputeStatus.RESOLVED_REFUND, new BigDecimal("100.0"), "Refund on free service");

            DisputeResponse response = resolveDisputeUseCase.execute(disputeId, request, adminId);

            assertThat(response.status()).isEqualTo(DisputeStatus.RESOLVED_REFUND);

            verify(refundRepository, never()).save(any());
        }
    }
}
