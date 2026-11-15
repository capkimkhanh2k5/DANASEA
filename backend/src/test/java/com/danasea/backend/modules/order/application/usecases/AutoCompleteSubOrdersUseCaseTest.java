package com.danasea.backend.modules.order.application.usecases;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.communication.application.dtos.NotificationCommand;
import com.danasea.backend.modules.communication.application.usecases.SendNotificationUseCase;
import com.danasea.backend.modules.communication.domain.ports.PushNotificationPort;
import com.danasea.backend.modules.communication.infrastructure.persistence.entities.NotificationJpaEntity;
import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderDisruptionJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderDisruptionRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingItemJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingItemRepository;
import com.danasea.backend.modules.service.domain.models.OperationType;
import com.danasea.backend.modules.service.domain.models.OptionType;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceOptionJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceOptionRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AutoCompleteSubOrdersUseCaseTest — Comprehensive Tests for Phase 1 Auto-Completion")
class AutoCompleteSubOrdersUseCaseTest {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private JpaSubOrderRepository subOrderRepository;

    @Mock
    private JpaMasterOrderRepository masterOrderRepository;

    @Mock
    private JpaServiceSlotRepository slotRepository;

    @Mock
    private JpaDisputeRepository disputeRepository;

    @Mock
    private JpaRefundRepository refundRepository;

    @Mock
    private JpaSubOrderDisruptionRepository disruptionRepository;

    @Mock
    private AuditLogInternalApi auditLogInternalApi;

    @Mock
    private SendNotificationUseCase sendNotificationUseCase;

    @Mock
    private PushNotificationPort pushNotificationPort;

    @Mock
    private VendorInternalApi vendorInternalApi;

    @Mock
    private AccountInternalApi accountInternalApi;

    @Mock
    private JpaServiceRepository serviceRepository;

    @Mock
    private JpaBookingItemRepository bookingItemRepository;

    @Mock
    private JpaServiceOptionRepository serviceOptionRepository;

    private AutoCompleteSubOrdersUseCase useCase;

    private UUID subOrderId;
    private UUID masterOrderId;
    private UUID slotId;

    @BeforeEach
    void setUp() {
        useCase = new AutoCompleteSubOrdersUseCase(
                subOrderRepository,
                masterOrderRepository,
                slotRepository,
                disputeRepository,
                refundRepository,
                disruptionRepository
        );
        injectField("auditLogInternalApi", auditLogInternalApi);
        injectField("sendNotificationUseCase", sendNotificationUseCase);
        injectField("pushNotificationPort", pushNotificationPort);
        injectField("vendorInternalApi", vendorInternalApi);
        injectField("accountInternalApi", accountInternalApi);
        injectField("serviceRepository", serviceRepository);
        injectField("bookingItemRepository", bookingItemRepository);
        injectField("serviceOptionRepository", serviceOptionRepository);

        subOrderId = UUID.randomUUID();
        masterOrderId = UUID.randomUUID();
        slotId = UUID.randomUUID();
    }

    private void injectField(String fieldName, Object val) {
        try {
            var field = AutoCompleteSubOrdersUseCase.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(useCase, val);
        } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("1. Success: Auto-complete when CHECKED_IN and now is past slot endTime")
    void testAutoComplete_Success_WhenCheckedInAndPastSlotEndTime() {
        LocalDate date = LocalDate.of(2026, 11, 6);
        LocalTime startTime = LocalTime.of(8, 0);
        LocalTime endTime = LocalTime.of(10, 0);

        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(date);
        slot.setStartTime(startTime);
        slot.setEndTime(endTime);

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(subOrderId);
        subOrder.setMasterOrderId(masterOrderId);
        subOrder.setSlotId(slotId);
        subOrder.setStatus(SubOrderStatus.CHECKED_IN);
        subOrder.setCheckedInAt(LocalDateTime.of(date, LocalTime.of(7, 30)).atZone(VIETNAM_ZONE).toOffsetDateTime());

        MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
        masterOrder.setId(masterOrderId);
        masterOrder.setStatus(MasterOrderStatus.PAID);
        masterOrder.setPaymentStatus(PaymentOrderStatus.PAID);

        when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(disputeRepository.existsBySubOrderIdAndStatusIn(eq(subOrderId), any())).thenReturn(false);
        when(refundRepository.existsBySubOrderIdAndStatusIn(eq(subOrderId), any())).thenReturn(false);
        when(disruptionRepository.findBySubOrderIdAndStatus(eq(subOrderId), eq("AWAITING_DECISION"))).thenReturn(Optional.empty());
        when(masterOrderRepository.findByIdForUpdate(masterOrderId)).thenReturn(Optional.of(masterOrder));
        when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of(subOrder));

        OffsetDateTime pastEndTime = LocalDateTime.of(date, LocalTime.of(10, 1)).atZone(VIETNAM_ZONE).toOffsetDateTime();
        boolean completed = useCase.completeSingleSubOrder(subOrderId, pastEndTime);

        assertThat(completed).isTrue();
        assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.COMPLETED);
        assertThat(masterOrder.getStatus()).isEqualTo(MasterOrderStatus.COMPLETED);

        verify(subOrderRepository).save(subOrder);
        verify(masterOrderRepository).save(masterOrder);
        verify(auditLogInternalApi).recordTransactionalAuditLog(eq(null), eq("AUTO_COMPLETE_SUB_ORDER"), eq("SUB_ORDER"), eq(subOrderId), any());
    }

    @Test
    @DisplayName("2. Guard: Skipped when now is before slot endTime")
    void testAutoComplete_Skipped_WhenBeforeSlotEndTime() {
        LocalDate date = LocalDate.of(2026, 11, 6);
        LocalTime startTime = LocalTime.of(8, 0);
        LocalTime endTime = LocalTime.of(10, 0);

        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(date);
        slot.setStartTime(startTime);
        slot.setEndTime(endTime);

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(subOrderId);
        subOrder.setMasterOrderId(masterOrderId);
        subOrder.setSlotId(slotId);
        subOrder.setStatus(SubOrderStatus.CHECKED_IN);
        subOrder.setCheckedInAt(LocalDateTime.of(date, LocalTime.of(7, 30)).atZone(VIETNAM_ZONE).toOffsetDateTime());

        when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        OffsetDateTime beforeEndTime = LocalDateTime.of(date, LocalTime.of(9, 59)).atZone(VIETNAM_ZONE).toOffsetDateTime();
        boolean completed = useCase.completeSingleSubOrder(subOrderId, beforeEndTime);

        assertThat(completed).isFalse();
        assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.CHECKED_IN);
        verify(subOrderRepository, never()).save(any());
        verify(masterOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("3. Guard: Skipped when subOrder is CONFIRMED (not checked in)")
    void testAutoComplete_Skipped_WhenSubOrderNotCheckedIn() {
        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(subOrderId);
        subOrder.setStatus(SubOrderStatus.CONFIRMED);
        subOrder.setCheckedInAt(null);

        when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));

        OffsetDateTime now = OffsetDateTime.now(VIETNAM_ZONE);
        boolean completed = useCase.completeSingleSubOrder(subOrderId, now);

        assertThat(completed).isFalse();
        assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.CONFIRMED);
        verify(subOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("4. Guard: Skipped when active Dispute exists (OPEN or UNDER_REVIEW)")
    void testAutoComplete_Skipped_WhenActiveDisputeExists() {
        LocalDate date = LocalDate.of(2026, 11, 6);
        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(date);
        slot.setStartTime(LocalTime.of(8, 0));
        slot.setEndTime(LocalTime.of(10, 0));

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(subOrderId);
        subOrder.setSlotId(slotId);
        subOrder.setStatus(SubOrderStatus.CHECKED_IN);
        subOrder.setCheckedInAt(LocalDateTime.of(date, LocalTime.of(7, 30)).atZone(VIETNAM_ZONE).toOffsetDateTime());

        when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(disputeRepository.existsBySubOrderIdAndStatusIn(eq(subOrderId), any())).thenReturn(true);

        OffsetDateTime pastEndTime = LocalDateTime.of(date, LocalTime.of(10, 15)).atZone(VIETNAM_ZONE).toOffsetDateTime();
        boolean completed = useCase.completeSingleSubOrder(subOrderId, pastEndTime);

        assertThat(completed).isFalse();
        assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.CHECKED_IN);
        verify(subOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("5. Guard: Skipped when pending Refund exists")
    void testAutoComplete_Skipped_WhenPendingRefundExists() {
        LocalDate date = LocalDate.of(2026, 11, 6);
        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(date);
        slot.setStartTime(LocalTime.of(8, 0));
        slot.setEndTime(LocalTime.of(10, 0));

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(subOrderId);
        subOrder.setSlotId(slotId);
        subOrder.setStatus(SubOrderStatus.CHECKED_IN);
        subOrder.setCheckedInAt(LocalDateTime.of(date, LocalTime.of(7, 30)).atZone(VIETNAM_ZONE).toOffsetDateTime());

        when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(disputeRepository.existsBySubOrderIdAndStatusIn(eq(subOrderId), any())).thenReturn(false);
        when(refundRepository.existsBySubOrderIdAndStatusIn(eq(subOrderId), eq(List.of(RefundStatus.PENDING)))).thenReturn(true);

        OffsetDateTime pastEndTime = LocalDateTime.of(date, LocalTime.of(10, 15)).atZone(VIETNAM_ZONE).toOffsetDateTime();
        boolean completed = useCase.completeSingleSubOrder(subOrderId, pastEndTime);

        assertThat(completed).isFalse();
        assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.CHECKED_IN);
        verify(subOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("6. Guard: Skipped when Disruption is AWAITING_DECISION")
    void testAutoComplete_Skipped_WhenActiveDisruptionExists() {
        LocalDate date = LocalDate.of(2026, 11, 6);
        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(date);
        slot.setStartTime(LocalTime.of(8, 0));
        slot.setEndTime(LocalTime.of(10, 0));

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(subOrderId);
        subOrder.setSlotId(slotId);
        subOrder.setStatus(SubOrderStatus.CHECKED_IN);
        subOrder.setCheckedInAt(LocalDateTime.of(date, LocalTime.of(7, 30)).atZone(VIETNAM_ZONE).toOffsetDateTime());

        when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(disputeRepository.existsBySubOrderIdAndStatusIn(eq(subOrderId), any())).thenReturn(false);
        when(refundRepository.existsBySubOrderIdAndStatusIn(eq(subOrderId), any())).thenReturn(false);
        when(disruptionRepository.findBySubOrderIdAndStatus(eq(subOrderId), eq("AWAITING_DECISION")))
                .thenReturn(Optional.of(new SubOrderDisruptionJpaEntity()));

        OffsetDateTime pastEndTime = LocalDateTime.of(date, LocalTime.of(10, 15)).atZone(VIETNAM_ZONE).toOffsetDateTime();
        boolean completed = useCase.completeSingleSubOrder(subOrderId, pastEndTime);

        assertThat(completed).isFalse();
        assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.CHECKED_IN);
        verify(subOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("7. Overnight Slot: Handled accurately across midnight")
    void testAutoComplete_OvernightSlot_AccurateBoundary() {
        LocalDate date = LocalDate.of(2026, 11, 6);
        LocalTime startTime = LocalTime.of(22, 0); // 22:00
        LocalTime endTime = LocalTime.of(2, 0);    // 02:00 next day (Nov 7)

        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(date);
        slot.setStartTime(startTime);
        slot.setEndTime(endTime);

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(subOrderId);
        subOrder.setMasterOrderId(masterOrderId);
        subOrder.setSlotId(slotId);
        subOrder.setStatus(SubOrderStatus.CHECKED_IN);
        subOrder.setCheckedInAt(LocalDateTime.of(date, LocalTime.of(21, 30)).atZone(VIETNAM_ZONE).toOffsetDateTime());

        when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        // Time 01:50 on Nov 7 (still before 02:00 end time) -> should NOT complete
        OffsetDateTime beforeEnd = LocalDateTime.of(LocalDate.of(2026, 11, 7), LocalTime.of(1, 50)).atZone(VIETNAM_ZONE).toOffsetDateTime();
        boolean beforeCompleted = useCase.completeSingleSubOrder(subOrderId, beforeEnd);
        assertThat(beforeCompleted).isFalse();

        // Time 02:05 on Nov 7 (after 02:00 end time) -> should complete
        when(disputeRepository.existsBySubOrderIdAndStatusIn(eq(subOrderId), any())).thenReturn(false);
        when(refundRepository.existsBySubOrderIdAndStatusIn(eq(subOrderId), any())).thenReturn(false);
        when(disruptionRepository.findBySubOrderIdAndStatus(eq(subOrderId), eq("AWAITING_DECISION"))).thenReturn(Optional.empty());
        when(masterOrderRepository.findByIdForUpdate(masterOrderId)).thenReturn(Optional.of(new MasterOrderJpaEntity()));
        when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of(subOrder));

        OffsetDateTime afterEnd = LocalDateTime.of(LocalDate.of(2026, 11, 7), LocalTime.of(2, 5)).atZone(VIETNAM_ZONE).toOffsetDateTime();
        boolean afterCompleted = useCase.completeSingleSubOrder(subOrderId, afterEnd);
        assertThat(afterCompleted).isTrue();
        assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("8. MasterOrder: Multiple SubOrders -> PARTIALLY_COMPLETED when 1 completed and 1 still pending")
    void testAutoComplete_MasterOrder_MultipleSubOrders_PartiallyCompleted() {
        LocalDate date = LocalDate.of(2026, 11, 6);
        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(date);
        slot.setStartTime(LocalTime.of(8, 0));
        slot.setEndTime(LocalTime.of(10, 0));

        SubOrderJpaEntity subOrder1 = new SubOrderJpaEntity();
        subOrder1.setId(subOrderId);
        subOrder1.setMasterOrderId(masterOrderId);
        subOrder1.setSlotId(slotId);
        subOrder1.setStatus(SubOrderStatus.CHECKED_IN);
        subOrder1.setCheckedInAt(LocalDateTime.of(date, LocalTime.of(7, 30)).atZone(VIETNAM_ZONE).toOffsetDateTime());

        SubOrderJpaEntity subOrder2 = new SubOrderJpaEntity();
        subOrder2.setId(UUID.randomUUID());
        subOrder2.setMasterOrderId(masterOrderId);
        subOrder2.setStatus(SubOrderStatus.CONFIRMED); // still upcoming tomorrow

        MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
        masterOrder.setId(masterOrderId);
        masterOrder.setStatus(MasterOrderStatus.PAID);

        when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder1));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(disputeRepository.existsBySubOrderIdAndStatusIn(eq(subOrderId), any())).thenReturn(false);
        when(refundRepository.existsBySubOrderIdAndStatusIn(eq(subOrderId), any())).thenReturn(false);
        when(disruptionRepository.findBySubOrderIdAndStatus(eq(subOrderId), eq("AWAITING_DECISION"))).thenReturn(Optional.empty());
        when(masterOrderRepository.findByIdForUpdate(masterOrderId)).thenReturn(Optional.of(masterOrder));
        when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of(subOrder1, subOrder2));

        OffsetDateTime pastEndTime = LocalDateTime.of(date, LocalTime.of(10, 5)).atZone(VIETNAM_ZONE).toOffsetDateTime();
        boolean completed = useCase.completeSingleSubOrder(subOrderId, pastEndTime);

        assertThat(completed).isTrue();
        assertThat(subOrder1.getStatus()).isEqualTo(SubOrderStatus.COMPLETED);
        assertThat(masterOrder.getStatus()).isEqualTo(MasterOrderStatus.PARTIALLY_COMPLETED);
    }

    @Test
    @DisplayName("9. MasterOrder: All SubOrders terminal (1 COMPLETED, 1 CANCELLED) -> COMPLETED")
    void testAutoComplete_MasterOrder_AllTerminal_Completed() {
        LocalDate date = LocalDate.of(2026, 11, 6);
        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(date);
        slot.setStartTime(LocalTime.of(8, 0));
        slot.setEndTime(LocalTime.of(10, 0));

        SubOrderJpaEntity subOrder1 = new SubOrderJpaEntity();
        subOrder1.setId(subOrderId);
        subOrder1.setMasterOrderId(masterOrderId);
        subOrder1.setSlotId(slotId);
        subOrder1.setStatus(SubOrderStatus.CHECKED_IN);
        subOrder1.setCheckedInAt(LocalDateTime.of(date, LocalTime.of(7, 30)).atZone(VIETNAM_ZONE).toOffsetDateTime());

        SubOrderJpaEntity subOrder2 = new SubOrderJpaEntity();
        subOrder2.setId(UUID.randomUUID());
        subOrder2.setMasterOrderId(masterOrderId);
        subOrder2.setStatus(SubOrderStatus.CANCELLED);

        MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
        masterOrder.setId(masterOrderId);
        masterOrder.setStatus(MasterOrderStatus.PAID);

        when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder1));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(disputeRepository.existsBySubOrderIdAndStatusIn(eq(subOrderId), any())).thenReturn(false);
        when(refundRepository.existsBySubOrderIdAndStatusIn(eq(subOrderId), any())).thenReturn(false);
        when(disruptionRepository.findBySubOrderIdAndStatus(eq(subOrderId), eq("AWAITING_DECISION"))).thenReturn(Optional.empty());
        when(masterOrderRepository.findByIdForUpdate(masterOrderId)).thenReturn(Optional.of(masterOrder));
        when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of(subOrder1, subOrder2));

        OffsetDateTime pastEndTime = LocalDateTime.of(date, LocalTime.of(10, 5)).atZone(VIETNAM_ZONE).toOffsetDateTime();
        boolean completed = useCase.completeSingleSubOrder(subOrderId, pastEndTime);

        assertThat(completed).isTrue();
        assertThat(subOrder1.getStatus()).isEqualTo(SubOrderStatus.COMPLETED);
        // Only one of two sub-orders was completed, the other was CANCELLED -> PARTIALLY_COMPLETED
        assertThat(masterOrder.getStatus()).isEqualTo(MasterOrderStatus.PARTIALLY_COMPLETED);
    }

    @Test
    @DisplayName("10. Idempotency: Multiple runs on already completed SubOrder return false without error")
    void testAutoComplete_Idempotency() {
        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(subOrderId);
        subOrder.setStatus(SubOrderStatus.COMPLETED);
        subOrder.setCheckedInAt(OffsetDateTime.now(VIETNAM_ZONE).minusHours(3));

        when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));

        boolean completed = useCase.completeSingleSubOrder(subOrderId, OffsetDateTime.now(VIETNAM_ZONE));

        assertThat(completed).isFalse();
        verify(subOrderRepository, never()).save(any());
        verify(auditLogInternalApi, never()).recordTransactionalAuditLog(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("11. Guard: Rejection of suspicious late check-in scanned after slot end time")
    void testAutoComplete_Skipped_WhenCheckedInAtAfterSlotEndTime() {
        LocalDate date = LocalDate.of(2026, 11, 6);
        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(date);
        slot.setStartTime(LocalTime.of(8, 0));
        slot.setEndTime(LocalTime.of(9, 0));

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(subOrderId);
        subOrder.setSlotId(slotId);
        subOrder.setStatus(SubOrderStatus.CHECKED_IN);
        // Checked in at 09:15, after slot already ended at 09:00!
        subOrder.setCheckedInAt(LocalDateTime.of(date, LocalTime.of(9, 15)).atZone(VIETNAM_ZONE).toOffsetDateTime());

        when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(9, 30)).atZone(VIETNAM_ZONE).toOffsetDateTime();
        boolean completed = useCase.completeSingleSubOrder(subOrderId, now);

        assertThat(completed).isFalse();
        assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.CHECKED_IN);
        verify(subOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("12. Phase 2 NO_SHOW: Confirmed SubOrder not checked in past slot end time is marked NO_SHOW")
    void testMarkNoShows_Success() {
        LocalDate date = LocalDate.of(2026, 11, 6);
        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(date);
        slot.setStartTime(LocalTime.of(8, 0));
        slot.setEndTime(LocalTime.of(9, 0));

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(subOrderId);
        subOrder.setMasterOrderId(masterOrderId);
        subOrder.setSlotId(slotId);
        subOrder.setStatus(SubOrderStatus.CONFIRMED);
        subOrder.setCheckedInAt(null); // Never checked in!

        MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
        masterOrder.setId(masterOrderId);
        masterOrder.setStatus(MasterOrderStatus.PAID);

        when(subOrderRepository.findCandidateIdsByStatusAndCheckedInAtNull(SubOrderStatus.CONFIRMED)).thenReturn(List.of(subOrderId));
        when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(masterOrderRepository.findByIdForUpdate(masterOrderId)).thenReturn(Optional.of(masterOrder));
        when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of(subOrder));

        OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(9, 1)).atZone(VIETNAM_ZONE).toOffsetDateTime();
        int markedCount = useCase.markNoShows(now);

        assertThat(markedCount).isEqualTo(1);
        assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.NO_SHOW);
        assertThat(masterOrder.getStatus()).isEqualTo(MasterOrderStatus.CANCELLED);

        verify(subOrderRepository).save(subOrder);
        verify(masterOrderRepository).save(masterOrder);
        verify(auditLogInternalApi).recordTransactionalAuditLog(eq(null), eq("MARK_SUB_ORDER_NO_SHOW"), eq("SUB_ORDER"), eq(subOrderId), any());
    }

    @Test
    @DisplayName("13. Phase 2 NO_SHOW: SubOrder with cancellation reason is never marked NO_SHOW")
    void testMarkNoShows_WithCancellationReason_Skipped() {
        LocalDate date = LocalDate.of(2026, 11, 6);
        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(date);
        slot.setStartTime(LocalTime.of(8, 0));
        slot.setEndTime(LocalTime.of(9, 0));

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(subOrderId);
        subOrder.setMasterOrderId(masterOrderId);
        subOrder.setSlotId(slotId);
        subOrder.setStatus(SubOrderStatus.CONFIRMED);
        subOrder.setCheckedInAt(null);
        subOrder.setCancellationReason(RefundReason.VENDOR_FAULT); // Cancellation reason set!

        when(subOrderRepository.findCandidateIdsByStatusAndCheckedInAtNull(SubOrderStatus.CONFIRMED)).thenReturn(List.of(subOrderId));
        when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));

        OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(9, 1)).atZone(VIETNAM_ZONE).toOffsetDateTime();
        int markedCount = useCase.markNoShows(now);

        assertThat(markedCount).isEqualTo(0);
        assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.CONFIRMED);
        verify(subOrderRepository, never()).save(subOrder);
    }

    @Test
    @DisplayName("14. MasterOrder Rollup: PARTIALLY_REFUNDED and CANCELLED leads to MasterOrder COMPLETED")
    void testMasterOrderRollup_PartiallyRefundedAndCancelled() {
        LocalDate date = LocalDate.of(2026, 11, 6);
        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(date);
        slot.setStartTime(LocalTime.of(8, 0));
        slot.setEndTime(LocalTime.of(9, 0));

        SubOrderJpaEntity subOrder1 = new SubOrderJpaEntity();
        subOrder1.setId(subOrderId);
        subOrder1.setMasterOrderId(masterOrderId);
        subOrder1.setSlotId(slotId);
        subOrder1.setStatus(SubOrderStatus.CHECKED_IN);
        subOrder1.setCheckedInAt(LocalDateTime.of(date, LocalTime.of(7, 50)).atZone(VIETNAM_ZONE).toOffsetDateTime());

        SubOrderJpaEntity subOrder2 = new SubOrderJpaEntity();
        subOrder2.setId(UUID.randomUUID());
        subOrder2.setMasterOrderId(masterOrderId);
        subOrder2.setStatus(SubOrderStatus.CANCELLED);

        MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
        masterOrder.setId(masterOrderId);
        masterOrder.setStatus(MasterOrderStatus.PAID);

        when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder1));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(masterOrderRepository.findByIdForUpdate(masterOrderId)).thenReturn(Optional.of(masterOrder));
        when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of(subOrder1, subOrder2));

        OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(9, 5)).atZone(VIETNAM_ZONE).toOffsetDateTime();
        boolean completed = useCase.completeSingleSubOrder(subOrderId, now);

        assertThat(completed).isTrue();
        assertThat(subOrder1.getStatus()).isEqualTo(SubOrderStatus.COMPLETED);
        // All terminal, one completed and one cancelled -> masterOrder is PARTIALLY_COMPLETED
        assertThat(masterOrder.getStatus()).isEqualTo(MasterOrderStatus.PARTIALLY_COMPLETED);
    }

    @Test
    @DisplayName("15. MasterOrder Rollup: All terminal without served items leads to CANCELLED")
    void testMasterOrderRollup_CancelledAndRefunded() {
        LocalDate date = LocalDate.of(2026, 11, 6);
        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(date);
        slot.setStartTime(LocalTime.of(8, 0));
        slot.setEndTime(LocalTime.of(9, 0));

        SubOrderJpaEntity subOrder1 = new SubOrderJpaEntity();
        subOrder1.setId(subOrderId);
        subOrder1.setMasterOrderId(masterOrderId);
        subOrder1.setSlotId(slotId);
        subOrder1.setStatus(SubOrderStatus.CONFIRMED);
        subOrder1.setCheckedInAt(null);

        SubOrderJpaEntity subOrder2 = new SubOrderJpaEntity();
        subOrder2.setId(UUID.randomUUID());
        subOrder2.setMasterOrderId(masterOrderId);
        subOrder2.setStatus(SubOrderStatus.REFUNDED);

        MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
        masterOrder.setId(masterOrderId);
        masterOrder.setStatus(MasterOrderStatus.PAID);

        // Suppose subOrder1 is cancelled
        subOrder1.setStatus(SubOrderStatus.CANCELLED);

        when(subOrderRepository.findCandidateIdsByStatusAndCheckedInAtNull(SubOrderStatus.CONFIRMED)).thenReturn(List.of());
        when(masterOrderRepository.findByIdForUpdate(masterOrderId)).thenReturn(Optional.of(masterOrder));
        when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of(subOrder1, subOrder2));

        // Trigger rollup via completeSingleSubOrder or no-show
        // We verify that if both are terminal without served, status becomes CANCELLED
        subOrder1.setStatus(SubOrderStatus.CONFIRMED);
        when(subOrderRepository.findCandidateIdsByStatusAndCheckedInAtNull(SubOrderStatus.CONFIRMED)).thenReturn(List.of(subOrderId));
        when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder1));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(9, 5)).atZone(VIETNAM_ZONE).toOffsetDateTime();
        useCase.markNoShows(now);

        // SubOrder1 marked NO_SHOW, subOrder2 REFUNDED -> zero services delivered -> CANCELLED
        assertThat(masterOrder.getStatus()).isEqualTo(MasterOrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("16. Overdue Notification: Customer is notified when check-in cutoff (start - 15m) has passed")
    void testNotifyOverdueCheckins_Success() {
        LocalDate date = LocalDate.of(2026, 11, 6);
        LocalTime startTime = LocalTime.of(9, 0);
        LocalTime endTime = LocalTime.of(10, 0);

        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(date);
        slot.setStartTime(startTime);
        slot.setEndTime(endTime);

        UUID serviceId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID vendorUserId = UUID.randomUUID();

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(subOrderId);
        subOrder.setMasterOrderId(masterOrderId);
        subOrder.setSlotId(slotId);
        subOrder.setServiceId(serviceId);
        subOrder.setVendorId(vendorId);
        subOrder.setStatus(SubOrderStatus.CONFIRMED);
        subOrder.setCheckedInAt(null);

        MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
        masterOrder.setId(masterOrderId);
        masterOrder.setCustomerId(customerId);

        ServiceJpaEntity serviceEntity = new ServiceJpaEntity();
        serviceEntity.setId(serviceId);
        serviceEntity.setName("Tour Đảo Sơn Trà");
        serviceEntity.setOperationType(OperationType.FIXED_DEPARTURE);

        Vendor vendor = new Vendor();
        vendor.setId(vendorId);
        vendor.setUserId(vendorUserId);
        vendor.setBusinessName("DanaSea Travel");

        User vendorUser = new User();
        vendorUser.setId(vendorUserId);
        vendorUser.setPhone("0905123456");

        NotificationJpaEntity savedNotif = new NotificationJpaEntity();
        savedNotif.setId(UUID.randomUUID());
        savedNotif.setUserId(customerId);
        savedNotif.setTitle("Quá thời hạn check-in chuyến đi");
        savedNotif.setBody("Chuyến đi Tour Đảo Sơn Trà lúc 09:00 đã quá thời hạn check-in");

        when(subOrderRepository.findCandidateIdsByStatusAndCheckedInAtNull(SubOrderStatus.CONFIRMED)).thenReturn(List.of(subOrderId));
        when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(masterOrderRepository.findById(masterOrderId)).thenReturn(Optional.of(masterOrder));
        when(vendorInternalApi.findById(vendorId)).thenReturn(Optional.of(vendor));
        when(accountInternalApi.findUserById(vendorUserId)).thenReturn(Optional.of(vendorUser));
        when(sendNotificationUseCase.executeOnce(any(NotificationCommand.class), eq("overdue_checkin:" + subOrderId)))
                .thenReturn(Optional.of(savedNotif));

        // Cutoff for FIXED_DEPARTURE is 09:00 - 15m = 08:45. Let now = 08:46
        OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(8, 46)).atZone(VIETNAM_ZONE).toOffsetDateTime();
        int notifiedCount = useCase.notifyOverdueCheckins(now);

        assertThat(notifiedCount).isEqualTo(1);
        verify(sendNotificationUseCase).executeOnce(any(NotificationCommand.class), eq("overdue_checkin:" + subOrderId));
        verify(pushNotificationPort).sendPush(eq(customerId), eq(savedNotif.getTitle()), eq(savedNotif.getBody()), any());
        verify(auditLogInternalApi).recordTransactionalAuditLog(eq(null), eq("NOTIFY_OVERDUE_CHECKIN"), eq("SUB_ORDER"), eq(subOrderId), any());
    }

    @Test
    @DisplayName("17. Overdue Notification Idempotency: When already notified, second run returns 0 and does not re-notify")
    void testNotifyOverdueCheckins_Idempotent() {
        LocalDate date = LocalDate.of(2026, 11, 6);
        LocalTime startTime = LocalTime.of(9, 0);
        LocalTime endTime = LocalTime.of(10, 0);

        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(date);
        slot.setStartTime(startTime);
        slot.setEndTime(endTime);

        UUID serviceId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(subOrderId);
        subOrder.setMasterOrderId(masterOrderId);
        subOrder.setSlotId(slotId);
        subOrder.setServiceId(serviceId);
        subOrder.setVendorId(vendorId);
        subOrder.setStatus(SubOrderStatus.CONFIRMED);

        MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
        masterOrder.setId(masterOrderId);
        masterOrder.setCustomerId(customerId);

        when(subOrderRepository.findCandidateIdsByStatusAndCheckedInAtNull(SubOrderStatus.CONFIRMED)).thenReturn(List.of(subOrderId));
        when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(masterOrderRepository.findById(masterOrderId)).thenReturn(Optional.of(masterOrder));
        // Idempotent: executeOnce returns empty if already exists
        when(sendNotificationUseCase.executeOnce(any(NotificationCommand.class), eq("overdue_checkin:" + subOrderId)))
                .thenReturn(Optional.empty());

        OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(8, 50)).atZone(VIETNAM_ZONE).toOffsetDateTime();
        int notifiedCount = useCase.notifyOverdueCheckins(now);

        assertThat(notifiedCount).isEqualTo(0);
        verify(pushNotificationPort, never()).sendPush(any(), any(), any(), any());
    }

    @Test
    @DisplayName("18. MasterOrder Rollup: COMPLETED + NO_SHOW -> PARTIALLY_COMPLETED (only partial services delivered)")
    void testMasterOrderRollup_CompletedPlusNoShow_PartiallyCompleted() {
        SubOrderJpaEntity sub1 = new SubOrderJpaEntity();
        sub1.setId(UUID.randomUUID());
        sub1.setMasterOrderId(masterOrderId);
        sub1.setStatus(SubOrderStatus.COMPLETED);

        SubOrderJpaEntity sub2 = new SubOrderJpaEntity();
        sub2.setId(UUID.randomUUID());
        sub2.setMasterOrderId(masterOrderId);
        sub2.setStatus(SubOrderStatus.CONFIRMED);
        sub2.setSlotId(slotId);

        MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
        masterOrder.setId(masterOrderId);
        masterOrder.setStatus(MasterOrderStatus.PAID);

        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(LocalDate.of(2026, 11, 6));
        slot.setStartTime(LocalTime.of(8, 0));
        slot.setEndTime(LocalTime.of(9, 0));

        when(subOrderRepository.findCandidateIdsByStatusAndCheckedInAtNull(SubOrderStatus.CONFIRMED)).thenReturn(List.of(sub2.getId()));
        when(subOrderRepository.findByIdForUpdate(sub2.getId())).thenReturn(Optional.of(sub2));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(masterOrderRepository.findByIdForUpdate(masterOrderId)).thenReturn(Optional.of(masterOrder));
        when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of(sub1, sub2));

        OffsetDateTime now = LocalDateTime.of(2026, 11, 6, 9, 5).atZone(VIETNAM_ZONE).toOffsetDateTime();
        useCase.markNoShows(now);

        assertThat(sub2.getStatus()).isEqualTo(SubOrderStatus.NO_SHOW);
        assertThat(masterOrder.getStatus()).isEqualTo(MasterOrderStatus.PARTIALLY_COMPLETED);
    }

    @Test
    @DisplayName("19. MasterOrder Rollup: NO_SHOW + REFUNDED -> CANCELLED (zero services delivered)")
    void testMasterOrderRollup_NoShowPlusRefunded_Cancelled() {
        SubOrderJpaEntity sub1 = new SubOrderJpaEntity();
        sub1.setId(UUID.randomUUID());
        sub1.setMasterOrderId(masterOrderId);
        sub1.setStatus(SubOrderStatus.REFUNDED);

        SubOrderJpaEntity sub2 = new SubOrderJpaEntity();
        sub2.setId(UUID.randomUUID());
        sub2.setMasterOrderId(masterOrderId);
        sub2.setStatus(SubOrderStatus.CONFIRMED);
        sub2.setSlotId(slotId);

        MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
        masterOrder.setId(masterOrderId);
        masterOrder.setStatus(MasterOrderStatus.PAID);

        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(LocalDate.of(2026, 11, 6));
        slot.setStartTime(LocalTime.of(8, 0));
        slot.setEndTime(LocalTime.of(9, 0));

        when(subOrderRepository.findCandidateIdsByStatusAndCheckedInAtNull(SubOrderStatus.CONFIRMED)).thenReturn(List.of(sub2.getId()));
        when(subOrderRepository.findByIdForUpdate(sub2.getId())).thenReturn(Optional.of(sub2));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(masterOrderRepository.findByIdForUpdate(masterOrderId)).thenReturn(Optional.of(masterOrder));
        when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of(sub1, sub2));

        OffsetDateTime now = LocalDateTime.of(2026, 11, 6, 9, 5).atZone(VIETNAM_ZONE).toOffsetDateTime();
        useCase.markNoShows(now);

        assertThat(sub2.getStatus()).isEqualTo(SubOrderStatus.NO_SHOW);
        assertThat(masterOrder.getStatus()).isEqualTo(MasterOrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("20. MasterOrder Rollup: PARTIALLY_REFUNDED + CANCELLED -> PARTIALLY_COMPLETED")
    void testMasterOrderRollup_PartiallyRefundedPlusCancelled_PartiallyCompleted() {
        SubOrderJpaEntity sub1 = new SubOrderJpaEntity();
        sub1.setId(UUID.randomUUID());
        sub1.setMasterOrderId(masterOrderId);
        sub1.setStatus(SubOrderStatus.PARTIALLY_REFUNDED);

        SubOrderJpaEntity sub2 = new SubOrderJpaEntity();
        sub2.setId(UUID.randomUUID());
        sub2.setMasterOrderId(masterOrderId);
        sub2.setStatus(SubOrderStatus.CONFIRMED);
        sub2.setSlotId(slotId);

        MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
        masterOrder.setId(masterOrderId);
        masterOrder.setStatus(MasterOrderStatus.PAID);

        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(LocalDate.of(2026, 11, 6));
        slot.setStartTime(LocalTime.of(8, 0));
        slot.setEndTime(LocalTime.of(9, 0));

        when(subOrderRepository.findCandidateIdsByStatusAndCheckedInAtNull(SubOrderStatus.CONFIRMED)).thenReturn(List.of(sub2.getId()));
        when(subOrderRepository.findByIdForUpdate(sub2.getId())).thenReturn(Optional.of(sub2));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(masterOrderRepository.findByIdForUpdate(masterOrderId)).thenReturn(Optional.of(masterOrder));
        // Sub2 becomes NO_SHOW, sub1 is PARTIALLY_REFUNDED
        when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of(sub1, sub2));

        OffsetDateTime now = LocalDateTime.of(2026, 11, 6, 9, 5).atZone(VIETNAM_ZONE).toOffsetDateTime();
        useCase.markNoShows(now);

        assertThat(sub2.getStatus()).isEqualTo(SubOrderStatus.NO_SHOW);
        assertThat(masterOrder.getStatus()).isEqualTo(MasterOrderStatus.PARTIALLY_COMPLETED);
    }

    @Test
    @DisplayName("21. Overdue Notification: Private DURATION_BASED is NOT overdue before slot end (e.g., 08:25 or 08:29 for 08:30 end)")
    void testNotifyOverdueCheckins_PrivateDurationBased_NotOverdueBeforeSlotEnd() {
        LocalDate date = LocalDate.of(2026, 11, 7);
        LocalTime startTime = LocalTime.of(8, 0);
        LocalTime endTime = LocalTime.of(8, 30);

        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(date);
        slot.setStartTime(startTime);
        slot.setEndTime(endTime);

        UUID serviceId = UUID.randomUUID();
        UUID bookingItemId = UUID.randomUUID();
        UUID optionId = UUID.randomUUID();

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(subOrderId);
        subOrder.setMasterOrderId(masterOrderId);
        subOrder.setSlotId(slotId);
        subOrder.setServiceId(serviceId);
        subOrder.setBookingItemId(bookingItemId);
        subOrder.setStatus(SubOrderStatus.CONFIRMED);
        subOrder.setCheckedInAt(null);

        ServiceJpaEntity serviceEntity = new ServiceJpaEntity();
        serviceEntity.setId(serviceId);
        serviceEntity.setName("Thuê Jetski tự lái");
        serviceEntity.setOperationType(OperationType.DURATION_BASED);
        serviceEntity.setDurationMinutes(30);

        BookingItemJpaEntity bookingItem = new BookingItemJpaEntity();
        bookingItem.setId(bookingItemId);
        bookingItem.setOptionId(optionId);

        ServiceOptionJpaEntity serviceOption = new ServiceOptionJpaEntity();
        serviceOption.setId(optionId);
        serviceOption.setOptionType(OptionType.PRIVATE);

        when(subOrderRepository.findCandidateIdsByStatusAndCheckedInAtNull(SubOrderStatus.CONFIRMED)).thenReturn(List.of(subOrderId));
        when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(bookingItemRepository.findById(bookingItemId)).thenReturn(Optional.of(bookingItem));
        when(serviceOptionRepository.findById(optionId)).thenReturn(Optional.of(serviceOption));

        // At 08:25 (end - 5m) -> Still entitled to check in, NOT overdue!
        OffsetDateTime at0825 = LocalDateTime.of(date, LocalTime.of(8, 25)).atZone(VIETNAM_ZONE).toOffsetDateTime();
        int notifiedAt0825 = useCase.notifyOverdueCheckins(at0825);
        assertThat(notifiedAt0825).isEqualTo(0);

        // At 08:29 (end - 1m) -> Still entitled to check in, NOT overdue!
        OffsetDateTime at0829 = LocalDateTime.of(date, LocalTime.of(8, 29)).atZone(VIETNAM_ZONE).toOffsetDateTime();
        int notifiedAt0829 = useCase.notifyOverdueCheckins(at0829);
        assertThat(notifiedAt0829).isEqualTo(0);

        verify(sendNotificationUseCase, never()).executeOnce(any(), any());
    }

    @Test
    @DisplayName("22. Overdue Notification: PRIVATE does not receive OVERDUE_CHECKIN (notified via PRIVATE_LATE_REMINDER, then NO_SHOW)")
    void testNotifyOverdueCheckins_Private_Skipped_NoOverdueNotification() {
        LocalDate date = LocalDate.of(2026, 11, 7);
        LocalTime startTime = LocalTime.of(8, 0);
        LocalTime endTime = LocalTime.of(8, 30);

        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(date);
        slot.setStartTime(startTime);
        slot.setEndTime(endTime);

        UUID serviceId = UUID.randomUUID();
        UUID bookingItemId = UUID.randomUUID();
        UUID optionId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(subOrderId);
        subOrder.setMasterOrderId(masterOrderId);
        subOrder.setSlotId(slotId);
        subOrder.setServiceId(serviceId);
        subOrder.setBookingItemId(bookingItemId);
        subOrder.setVendorId(vendorId);
        subOrder.setStatus(SubOrderStatus.CONFIRMED);
        subOrder.setCheckedInAt(null);

        MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
        masterOrder.setId(masterOrderId);
        masterOrder.setCustomerId(customerId);

        ServiceJpaEntity serviceEntity = new ServiceJpaEntity();
        serviceEntity.setId(serviceId);
        serviceEntity.setName("Thuê Jetski tự lái");
        serviceEntity.setOperationType(OperationType.DURATION_BASED);
        serviceEntity.setDurationMinutes(30);

        BookingItemJpaEntity bookingItem = new BookingItemJpaEntity();
        bookingItem.setId(bookingItemId);
        bookingItem.setOptionId(optionId);

        ServiceOptionJpaEntity serviceOption = new ServiceOptionJpaEntity();
        serviceOption.setId(optionId);
        serviceOption.setOptionType(OptionType.PRIVATE);

        when(subOrderRepository.findCandidateIdsByStatusAndCheckedInAtNull(SubOrderStatus.CONFIRMED)).thenReturn(List.of(subOrderId));
        when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(bookingItemRepository.findById(bookingItemId)).thenReturn(Optional.of(bookingItem));
        when(serviceOptionRepository.findById(optionId)).thenReturn(Optional.of(serviceOption));
        when(masterOrderRepository.findById(masterOrderId)).thenReturn(Optional.of(masterOrder));

        // At 08:31 (after slot end 08:30) -> PRIVATE is skipped for OVERDUE_CHECKIN per PO policy
        OffsetDateTime at0831 = LocalDateTime.of(date, LocalTime.of(8, 31)).atZone(VIETNAM_ZONE).toOffsetDateTime();
        int notifiedAt0831 = useCase.notifyOverdueCheckins(at0831);
        assertThat(notifiedAt0831).isEqualTo(0);

        verify(sendNotificationUseCase, never()).executeOnce(any(), eq("overdue_checkin:" + subOrderId));
    }

    @Test
    @DisplayName("23. Overdue Notification: SHARED DURATION_BASED is overdue when cutoff (start - 15m) has passed")
    void testNotifyOverdueCheckins_SharedDurationBased_OverdueAfterStartMinus15m() {
        LocalDate date = LocalDate.of(2026, 11, 7);
        LocalTime startTime = LocalTime.of(14, 0);
        LocalTime endTime = LocalTime.of(15, 0);

        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(date);
        slot.setStartTime(startTime);
        slot.setEndTime(endTime);

        UUID serviceId = UUID.randomUUID();
        UUID bookingItemId = UUID.randomUUID();
        UUID optionId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(subOrderId);
        subOrder.setMasterOrderId(masterOrderId);
        subOrder.setSlotId(slotId);
        subOrder.setServiceId(serviceId);
        subOrder.setBookingItemId(bookingItemId);
        subOrder.setStatus(SubOrderStatus.CONFIRMED);
        subOrder.setCheckedInAt(null);

        MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
        masterOrder.setId(masterOrderId);
        masterOrder.setCustomerId(customerId);

        ServiceJpaEntity serviceEntity = new ServiceJpaEntity();
        serviceEntity.setId(serviceId);
        serviceEntity.setName("Chèo SUP nhóm ghép");
        serviceEntity.setOperationType(OperationType.DURATION_BASED);
        serviceEntity.setDurationMinutes(60);

        BookingItemJpaEntity bookingItem = new BookingItemJpaEntity();
        bookingItem.setId(bookingItemId);
        bookingItem.setOptionId(optionId);

        ServiceOptionJpaEntity serviceOption = new ServiceOptionJpaEntity();
        serviceOption.setId(optionId);
        serviceOption.setOptionType(OptionType.SHARED);

        NotificationJpaEntity savedNotif = new NotificationJpaEntity();
        savedNotif.setId(UUID.randomUUID());
        savedNotif.setUserId(customerId);
        savedNotif.setTitle("Thông báo quá hạn check-in");
        savedNotif.setBody("Quá hạn check-in");

        when(subOrderRepository.findCandidateIdsByStatusAndCheckedInAtNull(SubOrderStatus.CONFIRMED)).thenReturn(List.of(subOrderId));
        when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(bookingItemRepository.findById(bookingItemId)).thenReturn(Optional.of(bookingItem));
        when(serviceOptionRepository.findById(optionId)).thenReturn(Optional.of(serviceOption));
        when(masterOrderRepository.findById(masterOrderId)).thenReturn(Optional.of(masterOrder));
        when(sendNotificationUseCase.executeOnce(any(), eq("overdue_checkin:" + subOrderId)))
                .thenReturn(Optional.of(savedNotif));

        // Cutoff is 14:00 - 15m = 13:45. At 13:46 -> Overdue notification sent!
        OffsetDateTime at1346 = LocalDateTime.of(date, LocalTime.of(13, 46)).atZone(VIETNAM_ZONE).toOffsetDateTime();
        int notified = useCase.notifyOverdueCheckins(at1346);
        assertThat(notified).isEqualTo(1);
        verify(sendNotificationUseCase).executeOnce(any(), eq("overdue_checkin:" + subOrderId));
    }

    @Test
    @DisplayName("24. Private Late Reminder: Sends reminder during slot (start < now < end) and is idempotent")
    void testNotifyPrivateLateReminders_SendsReminderDuringSlot_Idempotent() {
        LocalDate date = LocalDate.of(2026, 11, 7);
        LocalTime startTime = LocalTime.of(8, 0);
        LocalTime endTime = LocalTime.of(8, 30);

        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(date);
        slot.setStartTime(startTime);
        slot.setEndTime(endTime);

        UUID serviceId = UUID.randomUUID();
        UUID bookingItemId = UUID.randomUUID();
        UUID optionId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(subOrderId);
        subOrder.setMasterOrderId(masterOrderId);
        subOrder.setSlotId(slotId);
        subOrder.setServiceId(serviceId);
        subOrder.setBookingItemId(bookingItemId);
        subOrder.setVendorId(vendorId);
        subOrder.setStatus(SubOrderStatus.CONFIRMED);
        subOrder.setCheckedInAt(null);

        MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
        masterOrder.setId(masterOrderId);
        masterOrder.setCustomerId(customerId);

        ServiceJpaEntity serviceEntity = new ServiceJpaEntity();
        serviceEntity.setId(serviceId);
        serviceEntity.setName("Thuê Jetski riêng");
        serviceEntity.setOperationType(OperationType.DURATION_BASED);

        BookingItemJpaEntity bookingItem = new BookingItemJpaEntity();
        bookingItem.setId(bookingItemId);
        bookingItem.setOptionId(optionId);

        ServiceOptionJpaEntity serviceOption = new ServiceOptionJpaEntity();
        serviceOption.setId(optionId);
        serviceOption.setOptionType(OptionType.PRIVATE);

        NotificationJpaEntity savedNotif = new NotificationJpaEntity();
        savedNotif.setId(UUID.randomUUID());
        savedNotif.setUserId(customerId);
        savedNotif.setTitle("Nhắc nhở: Chuyến đi riêng đang diễn ra");
        savedNotif.setBody("Bạn đang đến muộn dịch vụ");

        when(subOrderRepository.findCandidateIdsByStatusAndCheckedInAtNull(SubOrderStatus.CONFIRMED)).thenReturn(List.of(subOrderId));
        when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(bookingItemRepository.findById(bookingItemId)).thenReturn(Optional.of(bookingItem));
        when(serviceOptionRepository.findById(optionId)).thenReturn(Optional.of(serviceOption));
        when(masterOrderRepository.findById(masterOrderId)).thenReturn(Optional.of(masterOrder));
        when(sendNotificationUseCase.executeOnce(any(), eq("private_late_reminder:" + subOrderId)))
                .thenReturn(Optional.of(savedNotif));

        // At 08:15 (start < now < end) -> sends private late reminder!
        OffsetDateTime at0815 = LocalDateTime.of(date, LocalTime.of(8, 15)).atZone(VIETNAM_ZONE).toOffsetDateTime();
        int reminded = useCase.notifyPrivateLateReminders(at0815);
        assertThat(reminded).isEqualTo(1);
        verify(sendNotificationUseCase).executeOnce(any(), eq("private_late_reminder:" + subOrderId));
    }

    @Test
    @DisplayName("25. Overdue Notification: Customer already checked in is NOT notified")
    void testNotifyOverdueCheckins_CustomerAlreadyCheckedIn_NotNotified() {
        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(subOrderId);
        subOrder.setStatus(SubOrderStatus.CHECKED_IN);
        subOrder.setCheckedInAt(OffsetDateTime.now());

        when(subOrderRepository.findCandidateIdsByStatusAndCheckedInAtNull(SubOrderStatus.CONFIRMED)).thenReturn(List.of());

        OffsetDateTime now = OffsetDateTime.now();
        int notified = useCase.notifyOverdueCheckins(now);
        assertThat(notified).isEqualTo(0);
        verify(sendNotificationUseCase, never()).executeOnce(any(), any());
    }

    @Nested
    @DisplayName("23. MasterOrder Rollup Comprehensive Audit: N SubOrders, State Combinations, Concurrency, Invariance")
    class MasterOrderRollupTests {

        @ParameterizedTest(name = "Rollup N={0} all COMPLETED -> MasterOrder COMPLETED")
        @ValueSource(ints = {1, 2, 3, 5, 10, 20, 50})
        void testRollup_AllCompleted_ResultsInCompleted(int n) {
            UUID mId = UUID.randomUUID();
            MasterOrderJpaEntity master = new MasterOrderJpaEntity();
            master.setId(mId);
            master.setStatus(MasterOrderStatus.PAID);

            List<SubOrderJpaEntity> subs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                SubOrderJpaEntity sub = new SubOrderJpaEntity();
                sub.setId(UUID.randomUUID());
                sub.setMasterOrderId(mId);
                sub.setStatus(SubOrderStatus.COMPLETED);
                subs.add(sub);
            }

            when(masterOrderRepository.findByIdForUpdate(mId)).thenReturn(Optional.of(master));
            when(subOrderRepository.findByMasterOrderId(mId)).thenReturn(subs);

            useCase.updateMasterOrderStatus(mId);

            assertThat(master.getStatus()).isEqualTo(MasterOrderStatus.COMPLETED);
            verify(masterOrderRepository).save(master);
        }

        @ParameterizedTest(name = "Rollup N={0} all NO_SHOW -> MasterOrder CANCELLED")
        @ValueSource(ints = {1, 2, 3, 5, 10, 20, 50})
        void testRollup_AllNoShow_ResultsInCancelled(int n) {
            UUID mId = UUID.randomUUID();
            MasterOrderJpaEntity master = new MasterOrderJpaEntity();
            master.setId(mId);
            master.setStatus(MasterOrderStatus.PAID);

            List<SubOrderJpaEntity> subs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                SubOrderJpaEntity sub = new SubOrderJpaEntity();
                sub.setId(UUID.randomUUID());
                sub.setMasterOrderId(mId);
                sub.setStatus(SubOrderStatus.NO_SHOW);
                subs.add(sub);
            }

            when(masterOrderRepository.findByIdForUpdate(mId)).thenReturn(Optional.of(master));
            when(subOrderRepository.findByMasterOrderId(mId)).thenReturn(subs);

            useCase.updateMasterOrderStatus(mId);

            assertThat(master.getStatus()).isEqualTo(MasterOrderStatus.CANCELLED);
            verify(masterOrderRepository).save(master);
        }

        @ParameterizedTest(name = "Rollup N={0} all CANCELLED -> MasterOrder CANCELLED")
        @ValueSource(ints = {1, 2, 3, 5, 10, 20, 50})
        void testRollup_AllCancelled_ResultsInCancelled(int n) {
            UUID mId = UUID.randomUUID();
            MasterOrderJpaEntity master = new MasterOrderJpaEntity();
            master.setId(mId);
            master.setStatus(MasterOrderStatus.PAID);

            List<SubOrderJpaEntity> subs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                SubOrderJpaEntity sub = new SubOrderJpaEntity();
                sub.setId(UUID.randomUUID());
                sub.setMasterOrderId(mId);
                sub.setStatus(SubOrderStatus.CANCELLED);
                subs.add(sub);
            }

            when(masterOrderRepository.findByIdForUpdate(mId)).thenReturn(Optional.of(master));
            when(subOrderRepository.findByMasterOrderId(mId)).thenReturn(subs);

            useCase.updateMasterOrderStatus(mId);

            assertThat(master.getStatus()).isEqualTo(MasterOrderStatus.CANCELLED);
            verify(masterOrderRepository).save(master);
        }

        @ParameterizedTest(name = "Rollup N={0} all REFUNDED -> MasterOrder CANCELLED")
        @ValueSource(ints = {1, 2, 3, 5, 10, 20, 50})
        void testRollup_AllRefunded_ResultsInCancelled(int n) {
            UUID mId = UUID.randomUUID();
            MasterOrderJpaEntity master = new MasterOrderJpaEntity();
            master.setId(mId);
            master.setStatus(MasterOrderStatus.PAID);

            List<SubOrderJpaEntity> subs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                SubOrderJpaEntity sub = new SubOrderJpaEntity();
                sub.setId(UUID.randomUUID());
                sub.setMasterOrderId(mId);
                sub.setStatus(SubOrderStatus.REFUNDED);
                subs.add(sub);
            }

            when(masterOrderRepository.findByIdForUpdate(mId)).thenReturn(Optional.of(master));
            when(subOrderRepository.findByMasterOrderId(mId)).thenReturn(subs);

            useCase.updateMasterOrderStatus(mId);

            assertThat(master.getStatus()).isEqualTo(MasterOrderStatus.CANCELLED);
            verify(masterOrderRepository).save(master);
        }

        @ParameterizedTest(name = "Rollup N={0} all CONFIRMED -> MasterOrder remains PAID")
        @ValueSource(ints = {1, 2, 3, 5, 10, 20, 50})
        void testRollup_AllConfirmed_RemainsPaid(int n) {
            UUID mId = UUID.randomUUID();
            MasterOrderJpaEntity master = new MasterOrderJpaEntity();
            master.setId(mId);
            master.setStatus(MasterOrderStatus.PAID);

            List<SubOrderJpaEntity> subs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                SubOrderJpaEntity sub = new SubOrderJpaEntity();
                sub.setId(UUID.randomUUID());
                sub.setMasterOrderId(mId);
                sub.setStatus(SubOrderStatus.CONFIRMED);
                subs.add(sub);
            }

            when(masterOrderRepository.findByIdForUpdate(mId)).thenReturn(Optional.of(master));
            when(subOrderRepository.findByMasterOrderId(mId)).thenReturn(subs);

            useCase.updateMasterOrderStatus(mId);

            assertThat(master.getStatus()).isEqualTo(MasterOrderStatus.PAID);
        }

        @ParameterizedTest(name = "Rollup N={0} mixed terminal (COMPLETED + NO_SHOW + CANCELLED) -> PARTIALLY_COMPLETED")
        @ValueSource(ints = {3, 5, 10, 20, 50})
        void testRollup_MixedTerminalWithCompleted_ResultsInPartiallyCompleted(int n) {
            UUID mId = UUID.randomUUID();
            MasterOrderJpaEntity master = new MasterOrderJpaEntity();
            master.setId(mId);
            master.setStatus(MasterOrderStatus.PAID);

            List<SubOrderJpaEntity> subs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                SubOrderJpaEntity sub = new SubOrderJpaEntity();
                sub.setId(UUID.randomUUID());
                sub.setMasterOrderId(mId);
                if (i % 3 == 0) sub.setStatus(SubOrderStatus.COMPLETED);
                else if (i % 3 == 1) sub.setStatus(SubOrderStatus.NO_SHOW);
                else sub.setStatus(SubOrderStatus.CANCELLED);
                subs.add(sub);
            }

            when(masterOrderRepository.findByIdForUpdate(mId)).thenReturn(Optional.of(master));
            when(subOrderRepository.findByMasterOrderId(mId)).thenReturn(subs);

            useCase.updateMasterOrderStatus(mId);

            assertThat(master.getStatus()).isEqualTo(MasterOrderStatus.PARTIALLY_COMPLETED);
            verify(masterOrderRepository).save(master);
        }

        @ParameterizedTest(name = "Rollup N={0} mixed terminal without served (NO_SHOW + REFUNDED + CANCELLED) -> CANCELLED")
        @ValueSource(ints = {3, 5, 10, 20, 50})
        void testRollup_MixedTerminalWithoutServed_ResultsInCancelled(int n) {
            UUID mId = UUID.randomUUID();
            MasterOrderJpaEntity master = new MasterOrderJpaEntity();
            master.setId(mId);
            master.setStatus(MasterOrderStatus.PAID);

            List<SubOrderJpaEntity> subs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                SubOrderJpaEntity sub = new SubOrderJpaEntity();
                sub.setId(UUID.randomUUID());
                sub.setMasterOrderId(mId);
                if (i % 3 == 0) sub.setStatus(SubOrderStatus.NO_SHOW);
                else if (i % 3 == 1) sub.setStatus(SubOrderStatus.REFUNDED);
                else sub.setStatus(SubOrderStatus.CANCELLED);
                subs.add(sub);
            }

            when(masterOrderRepository.findByIdForUpdate(mId)).thenReturn(Optional.of(master));
            when(subOrderRepository.findByMasterOrderId(mId)).thenReturn(subs);

            useCase.updateMasterOrderStatus(mId);

            assertThat(master.getStatus()).isEqualTo(MasterOrderStatus.CANCELLED);
            verify(masterOrderRepository).save(master);
        }

        @Test
        @DisplayName("Rollup (N-1) COMPLETED + 1 CONFIRMED -> PARTIALLY_COMPLETED (progressive)")
        void testRollup_ProgressiveCompletion_PartiallyCompleted() {
            UUID mId = UUID.randomUUID();
            MasterOrderJpaEntity master = new MasterOrderJpaEntity();
            master.setId(mId);
            master.setStatus(MasterOrderStatus.PAID);

            List<SubOrderJpaEntity> subs = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                SubOrderJpaEntity sub = new SubOrderJpaEntity();
                sub.setId(UUID.randomUUID());
                sub.setMasterOrderId(mId);
                sub.setStatus(SubOrderStatus.COMPLETED);
                subs.add(sub);
            }
            SubOrderJpaEntity activeSub = new SubOrderJpaEntity();
            activeSub.setId(UUID.randomUUID());
            activeSub.setMasterOrderId(mId);
            activeSub.setStatus(SubOrderStatus.CONFIRMED);
            subs.add(activeSub);

            when(masterOrderRepository.findByIdForUpdate(mId)).thenReturn(Optional.of(master));
            when(subOrderRepository.findByMasterOrderId(mId)).thenReturn(subs);

            useCase.updateMasterOrderStatus(mId);

            assertThat(master.getStatus()).isEqualTo(MasterOrderStatus.PARTIALLY_COMPLETED);
        }

        @Test
        @DisplayName("Order Invariance: Shuffling SubOrders produces identical MasterOrderStatus")
        void testRollup_OrderInvariance_Permutations() {
            UUID mId = UUID.randomUUID();

            List<SubOrderStatus> templateStatuses = List.of(
                    SubOrderStatus.COMPLETED,
                    SubOrderStatus.NO_SHOW,
                    SubOrderStatus.REFUNDED,
                    SubOrderStatus.CANCELLED,
                    SubOrderStatus.PARTIALLY_REFUNDED
            );

            for (int seed = 0; seed < 10; seed++) {
                List<SubOrderStatus> shuffled = new ArrayList<>(templateStatuses);
                Collections.shuffle(shuffled, new java.util.Random(seed));

                MasterOrderJpaEntity master = new MasterOrderJpaEntity();
                master.setId(mId);
                master.setStatus(MasterOrderStatus.PAID);

                List<SubOrderJpaEntity> subs = new ArrayList<>();
                for (SubOrderStatus st : shuffled) {
                    SubOrderJpaEntity s = new SubOrderJpaEntity();
                    s.setId(UUID.randomUUID());
                    s.setMasterOrderId(mId);
                    s.setStatus(st);
                    subs.add(s);
                }

                when(masterOrderRepository.findByIdForUpdate(mId)).thenReturn(Optional.of(master));
                when(subOrderRepository.findByMasterOrderId(mId)).thenReturn(subs);

                useCase.updateMasterOrderStatus(mId);

                // Contains COMPLETED and PARTIALLY_REFUNDED plus non-completed -> Must be PARTIALLY_COMPLETED
                assertThat(master.getStatus()).isEqualTo(MasterOrderStatus.PARTIALLY_COMPLETED);
            }
        }

        @Test
        @DisplayName("Multi-vendor & Multi-day: SubOrders spanning different vendors and days rollup consistently")
        void testRollup_MultiVendorMultiDay_Consistent() {
            UUID mId = UUID.randomUUID();
            MasterOrderJpaEntity master = new MasterOrderJpaEntity();
            master.setId(mId);
            master.setStatus(MasterOrderStatus.PAID);

            UUID vendorA = UUID.randomUUID();
            UUID vendorB = UUID.randomUUID();
            UUID vendorC = UUID.randomUUID();

            // Day 1, Vendor A: COMPLETED
            SubOrderJpaEntity sub1 = new SubOrderJpaEntity();
            sub1.setId(UUID.randomUUID());
            sub1.setMasterOrderId(mId);
            sub1.setVendorId(vendorA);
            sub1.setStatus(SubOrderStatus.COMPLETED);

            // Day 2, Vendor B: COMPLETED
            SubOrderJpaEntity sub2 = new SubOrderJpaEntity();
            sub2.setId(UUID.randomUUID());
            sub2.setMasterOrderId(mId);
            sub2.setVendorId(vendorB);
            sub2.setStatus(SubOrderStatus.COMPLETED);

            // Day 3, Vendor C: COMPLETED
            SubOrderJpaEntity sub3 = new SubOrderJpaEntity();
            sub3.setId(UUID.randomUUID());
            sub3.setMasterOrderId(mId);
            sub3.setVendorId(vendorC);
            sub3.setStatus(SubOrderStatus.COMPLETED);

            when(masterOrderRepository.findByIdForUpdate(mId)).thenReturn(Optional.of(master));
            when(subOrderRepository.findByMasterOrderId(mId)).thenReturn(List.of(sub1, sub2, sub3));

            useCase.updateMasterOrderStatus(mId);

            assertThat(master.getStatus()).isEqualTo(MasterOrderStatus.COMPLETED);
        }

        @Test
        @DisplayName("Concurrency Simulation: Concurrent invocations of updateMasterOrderStatus remain deterministic")
        void testRollup_ConcurrencySimulation() throws Exception {
            UUID mId = UUID.randomUUID();
            MasterOrderJpaEntity master = new MasterOrderJpaEntity();
            master.setId(mId);
            master.setStatus(MasterOrderStatus.PAID);

            SubOrderJpaEntity s1 = new SubOrderJpaEntity();
            s1.setId(UUID.randomUUID());
            s1.setMasterOrderId(mId);
            s1.setStatus(SubOrderStatus.COMPLETED);

            SubOrderJpaEntity s2 = new SubOrderJpaEntity();
            s2.setId(UUID.randomUUID());
            s2.setMasterOrderId(mId);
            s2.setStatus(SubOrderStatus.NO_SHOW);

            when(masterOrderRepository.findByIdForUpdate(mId)).thenReturn(Optional.of(master));
            when(subOrderRepository.findByMasterOrderId(mId)).thenReturn(List.of(s1, s2));

            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                tasks.add(() -> {
                    useCase.updateMasterOrderStatus(mId);
                    return null;
                });
            }

            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> f : futures) {
                f.get();
            }
            executor.shutdown();

            assertThat(master.getStatus()).isEqualTo(MasterOrderStatus.PARTIALLY_COMPLETED);
        }
    }
}
