package com.danasea.backend.modules.checkin.application.usecases;

import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.booking.domain.ports.VendorLookupPort;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidCheckinStateException;
import com.danasea.backend.modules.checkin.domain.exceptions.UnauthorizedVendorCheckinException;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EarlyDispatchSlotUseCaseTest — FIFO Early Dispatch & Slot Reallocation Tests")
class EarlyDispatchSlotUseCaseTest {

    @Mock
    private JpaSubOrderRepository subOrderRepository;

    @Mock
    private JpaServiceSlotRepository slotRepository;

    @Mock
    private VendorLookupPort vendorLookupPort;

    @Mock
    private AuditLogInternalApi auditLogInternalApi;

    private EarlyDispatchSlotUseCase useCase;

    private final UUID staffId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final UUID subOrderId = UUID.randomUUID();
    private final UUID originalSlotId = UUID.randomUUID();
    private final UUID targetSlotId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new EarlyDispatchSlotUseCase(subOrderRepository, slotRepository, vendorLookupPort);
        ReflectionTestUtils.setField(useCase, "auditLogInternalApi", auditLogInternalApi);
        when(vendorLookupPort.findVendorIdByUserId(staffId)).thenReturn(Optional.of(vendorId));
    }

    private ServiceSlotJpaEntity buildSlot(UUID id, LocalDate date, LocalTime start, LocalTime end, int capacity,
            int booked, SlotStatus status) {
        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(id);
        slot.setServiceId(serviceId);
        slot.setDate(date);
        slot.setStartTime(start);
        slot.setEndTime(end);
        slot.setCapacity(capacity);
        slot.setBookedCount(booked);
        slot.setStatus(status);
        return slot;
    }

    private SubOrderJpaEntity buildSubOrder(SubOrderStatus status, int quantity) {
        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(subOrderId);
        subOrder.setVendorId(vendorId);
        subOrder.setServiceId(serviceId);
        subOrder.setSlotId(originalSlotId);
        subOrder.setStatus(status);
        subOrder.setQuantity(quantity);
        return subOrder;
    }

    @Test
    @DisplayName("1. FIFO Early Dispatch: Success swaps inventory and preserves original registered slotId")
    void testEarlyDispatch_Success() {
        LocalDate date = LocalDate.now().plusDays(1);
        SubOrderJpaEntity subOrder = buildSubOrder(SubOrderStatus.CHECKED_IN, 2);

        // Original slot: 10:00 -> 11:30 (booked=5, capacity=10)
        ServiceSlotJpaEntity origSlot = buildSlot(originalSlotId, date, LocalTime.of(10, 0), LocalTime.of(11, 30), 10,
                5, SlotStatus.OPEN);
        // Early target slot: 08:30 -> 10:00 (booked=3, capacity=10)
        ServiceSlotJpaEntity targetSlot = buildSlot(targetSlotId, date, LocalTime.of(8, 30), LocalTime.of(10, 0), 10, 3,
                SlotStatus.OPEN);

        when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));
        when(slotRepository.findByIdForUpdate(originalSlotId)).thenReturn(Optional.of(origSlot));
        when(slotRepository.findByIdForUpdate(targetSlotId)).thenReturn(Optional.of(targetSlot));

        var request = new EarlyDispatchSlotUseCase.EarlyDispatchRequest(subOrderId, targetSlotId,
                "Customer agreed to take early slot");
        var response = useCase.execute(request, staffId);

        assertThat(response.subOrderId()).isEqualTo(subOrderId);
        assertThat(response.originalSlotId()).isEqualTo(originalSlotId);
        assertThat(response.actualSlotId()).isEqualTo(targetSlotId);

        // Verification of inventory transfer
        assertThat(origSlot.getBookedCount()).isEqualTo(3); // 5 - 2
        assertThat(targetSlot.getBookedCount()).isEqualTo(5); // 3 + 2

        // Critical requirement: original slotId is KEPT for Auto-Completion, while
        // actualSlotId records FIFO slot
        assertThat(subOrder.getSlotId()).isEqualTo(originalSlotId);
        assertThat(subOrder.getActualSlotId()).isEqualTo(targetSlotId);

        verify(slotRepository).save(origSlot);
        verify(slotRepository).save(targetSlot);
        verify(subOrderRepository).save(subOrder);
        verify(auditLogInternalApi).recordTransactionalAuditLog(eq(staffId), eq("FIFO_EARLY_DISPATCH"), eq("SUB_ORDER"),
                eq(subOrderId), any());
    }

    @Test
    @DisplayName("2. Rejection: SubOrder not CHECKED_IN throws InvalidCheckinStateException")
    void testEarlyDispatch_ThrowsWhenNotCheckedIn() {
        SubOrderJpaEntity subOrder = buildSubOrder(SubOrderStatus.CONFIRMED, 2);
        when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));

        var request = new EarlyDispatchSlotUseCase.EarlyDispatchRequest(subOrderId, targetSlotId, "Early dispatch");

        assertThatThrownBy(() -> useCase.execute(request, staffId))
                .isInstanceOf(InvalidCheckinStateException.class)
                .hasMessageContaining("Only checked-in orders can be dispatched");
    }

    @Test
    @DisplayName("3. Rejection: Target slot capacity insufficient throws InvalidCheckinStateException")
    void testEarlyDispatch_ThrowsWhenCapacityInsufficient() {
        LocalDate date = LocalDate.now().plusDays(1);
        SubOrderJpaEntity subOrder = buildSubOrder(SubOrderStatus.CHECKED_IN, 4);

        ServiceSlotJpaEntity origSlot = buildSlot(originalSlotId, date, LocalTime.of(10, 0), LocalTime.of(11, 30), 10,
                5, SlotStatus.OPEN);
        // Target slot has capacity 10 and booked 8 -> only 2 available, but subOrder
        // requires 4
        ServiceSlotJpaEntity targetSlot = buildSlot(targetSlotId, date, LocalTime.of(8, 30), LocalTime.of(10, 0), 10, 8,
                SlotStatus.OPEN);

        when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));
        when(slotRepository.findByIdForUpdate(originalSlotId)).thenReturn(Optional.of(origSlot));
        when(slotRepository.findByIdForUpdate(targetSlotId)).thenReturn(Optional.of(targetSlot));

        var request = new EarlyDispatchSlotUseCase.EarlyDispatchRequest(subOrderId, targetSlotId, "Early dispatch");

        assertThatThrownBy(() -> useCase.execute(request, staffId))
                .isInstanceOf(InvalidCheckinStateException.class)
                .hasMessageContaining("does not have sufficient capacity");

        // Verify inventory untouched
        verify(slotRepository, never()).save(any());
    }

    @Test
    @DisplayName("4. Rejection: Target slot not earlier than original slot throws InvalidCheckinStateException")
    void testEarlyDispatch_ThrowsWhenTargetNotEarlier() {
        LocalDate date = LocalDate.now().plusDays(1);
        SubOrderJpaEntity subOrder = buildSubOrder(SubOrderStatus.CHECKED_IN, 2);

        // Original slot: 08:30
        ServiceSlotJpaEntity origSlot = buildSlot(originalSlotId, date, LocalTime.of(8, 30), LocalTime.of(10, 0), 10, 5,
                SlotStatus.OPEN);
        // Target slot: 10:00 (later than original!)
        ServiceSlotJpaEntity targetSlot = buildSlot(targetSlotId, date, LocalTime.of(10, 0), LocalTime.of(11, 30), 10,
                2, SlotStatus.OPEN);

        when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));
        when(slotRepository.findByIdForUpdate(originalSlotId)).thenReturn(Optional.of(origSlot));
        when(slotRepository.findByIdForUpdate(targetSlotId)).thenReturn(Optional.of(targetSlot));

        var request = new EarlyDispatchSlotUseCase.EarlyDispatchRequest(subOrderId, targetSlotId, "Early dispatch");

        assertThatThrownBy(() -> useCase.execute(request, staffId))
                .isInstanceOf(InvalidCheckinStateException.class)
                .hasMessageContaining("earlier than");
    }

    @Test
    @DisplayName("5. Vendor Isolation: Staff of another vendor cannot dispatch subOrder")
    void testEarlyDispatch_ThrowsWhenStaffNotOwningVendor() {
        UUID otherVendorId = UUID.randomUUID();
        when(vendorLookupPort.findVendorIdByUserId(staffId)).thenReturn(Optional.of(otherVendorId));

        SubOrderJpaEntity subOrder = buildSubOrder(SubOrderStatus.CHECKED_IN, 2);
        when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));

        var request = new EarlyDispatchSlotUseCase.EarlyDispatchRequest(subOrderId, targetSlotId, "Early dispatch");

        assertThatThrownBy(() -> useCase.execute(request, staffId))
                .isInstanceOf(UnauthorizedVendorCheckinException.class)
                .hasMessageContaining("Staff does not belong to vendor");
    }

    @Test
    @DisplayName("6. Chained FIFO Dispatch: Customer B (08:00 -> 07:30) frees 08:00, allowing Customer C (08:30 -> 08:00)")
    void testChainedFifoDispatch_Success() {
        LocalDate date = LocalDate.now().plusDays(1);
        UUID slotAId = UUID.randomUUID();
        UUID slotBId = UUID.randomUUID();
        UUID slotCId = UUID.randomUUID();

        // Slot A: 07:30 (cap: 1, booked: 0)
        ServiceSlotJpaEntity slotA = buildSlot(slotAId, date, LocalTime.of(7, 30), LocalTime.of(8, 0), 1, 0, SlotStatus.OPEN);
        // Slot B: 08:00 (cap: 1, booked: 1 - Customer B)
        ServiceSlotJpaEntity slotB = buildSlot(slotBId, date, LocalTime.of(8, 0), LocalTime.of(8, 30), 1, 1, SlotStatus.OPEN);
        // Slot C: 08:30 (cap: 1, booked: 1 - Customer C)
        ServiceSlotJpaEntity slotC = buildSlot(slotCId, date, LocalTime.of(8, 30), LocalTime.of(9, 0), 1, 1, SlotStatus.OPEN);

        UUID subOrderBId = UUID.randomUUID();
        SubOrderJpaEntity subOrderB = new SubOrderJpaEntity();
        subOrderB.setId(subOrderBId);
        subOrderB.setVendorId(vendorId);
        subOrderB.setServiceId(serviceId);
        subOrderB.setSlotId(slotBId);
        subOrderB.setStatus(SubOrderStatus.CHECKED_IN);
        subOrderB.setQuantity(1);

        UUID subOrderCId = UUID.randomUUID();
        SubOrderJpaEntity subOrderC = new SubOrderJpaEntity();
        subOrderC.setId(subOrderCId);
        subOrderC.setVendorId(vendorId);
        subOrderC.setServiceId(serviceId);
        subOrderC.setSlotId(slotCId);
        subOrderC.setStatus(SubOrderStatus.CHECKED_IN);
        subOrderC.setQuantity(1);

        // Step 1: Customer B arrives, dispatched to Slot A
        when(subOrderRepository.findByIdForUpdate(subOrderBId)).thenReturn(Optional.of(subOrderB));
        when(slotRepository.findByIdForUpdate(slotBId)).thenReturn(Optional.of(slotB));
        when(slotRepository.findByIdForUpdate(slotAId)).thenReturn(Optional.of(slotA));

        var reqB = new EarlyDispatchSlotUseCase.EarlyDispatchRequest(subOrderBId, slotAId, "B moves to earlier slot A");
        var resB = useCase.execute(reqB, staffId);

        assertThat(resB.actualSlotId()).isEqualTo(slotAId);
        assertThat(subOrderB.getActualSlotId()).isEqualTo(slotAId);
        assertThat(subOrderB.getSlotId()).isEqualTo(slotBId); // Registered slot B preserved
        assertThat(slotA.getBookedCount()).isEqualTo(1); // Slot A now full
        assertThat(slotB.getBookedCount()).isEqualTo(0); // Slot B now FREE!

        // Step 2: Customer C arrives, dispatched to Slot B (which was just freed by B)
        when(subOrderRepository.findByIdForUpdate(subOrderCId)).thenReturn(Optional.of(subOrderC));
        when(slotRepository.findByIdForUpdate(slotCId)).thenReturn(Optional.of(slotC));
        when(slotRepository.findByIdForUpdate(slotBId)).thenReturn(Optional.of(slotB));

        var reqC = new EarlyDispatchSlotUseCase.EarlyDispatchRequest(subOrderCId, slotBId, "C moves to earlier slot B");
        var resC = useCase.execute(reqC, staffId);

        assertThat(resC.actualSlotId()).isEqualTo(slotBId);
        assertThat(subOrderC.getActualSlotId()).isEqualTo(slotBId);
        assertThat(subOrderC.getSlotId()).isEqualTo(slotCId); // Registered slot C preserved
        assertThat(slotB.getBookedCount()).isEqualTo(1); // Slot B re-occupied by C
        assertThat(slotC.getBookedCount()).isEqualTo(0); // Slot C now FREE!
    }

    @Test
    @DisplayName("7. Re-dispatch of same SubOrder: Releases actualSlotId instead of original slotId")
    void testReDispatch_ReleasesCurrentlyOccupiedSlot() {
        LocalDate date = LocalDate.now().plusDays(1);
        UUID slotAId = UUID.randomUUID();
        UUID slotBId = UUID.randomUUID();
        UUID slotCId = UUID.randomUUID();

        // Customer was originally registered in C (08:30) but already dispatched to B (08:00)
        ServiceSlotJpaEntity slotA = buildSlot(slotAId, date, LocalTime.of(7, 30), LocalTime.of(8, 0), 1, 0, SlotStatus.OPEN);
        ServiceSlotJpaEntity slotB = buildSlot(slotBId, date, LocalTime.of(8, 0), LocalTime.of(8, 30), 1, 1, SlotStatus.OPEN);
        ServiceSlotJpaEntity slotC = buildSlot(slotCId, date, LocalTime.of(8, 30), LocalTime.of(9, 0), 1, 0, SlotStatus.OPEN);

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(subOrderId);
        subOrder.setVendorId(vendorId);
        subOrder.setServiceId(serviceId);
        subOrder.setSlotId(slotCId); // original registered slot
        subOrder.setActualSlotId(slotBId); // currently occupying slot B
        subOrder.setStatus(SubOrderStatus.CHECKED_IN);
        subOrder.setQuantity(1);

        when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));
        when(slotRepository.findByIdForUpdate(slotBId)).thenReturn(Optional.of(slotB));
        when(slotRepository.findByIdForUpdate(slotAId)).thenReturn(Optional.of(slotA));
        when(slotRepository.findById(slotCId)).thenReturn(Optional.of(slotC));

        var req = new EarlyDispatchSlotUseCase.EarlyDispatchRequest(subOrderId, slotAId, "Re-dispatch from B to even earlier A");
        var res = useCase.execute(req, staffId);

        assertThat(res.actualSlotId()).isEqualTo(slotAId);
        assertThat(subOrder.getActualSlotId()).isEqualTo(slotAId);
        assertThat(subOrder.getSlotId()).isEqualTo(slotCId); // Still original C!
        assertThat(slotB.getBookedCount()).isEqualTo(0); // Freed slot B!
        assertThat(slotA.getBookedCount()).isEqualTo(1); // Occupied slot A!
        assertThat(slotC.getBookedCount()).isEqualTo(0); // Untouched slot C!
    }

    @Test
    @DisplayName("8. Redis Holds: Active holds reduce available capacity and block dispatch")
    void testEarlyDispatch_ThrowsWhenRedisHoldReducesCapacity() {
        LocalDate date = LocalDate.now().plusDays(1);
        SubOrderJpaEntity subOrder = buildSubOrder(SubOrderStatus.CHECKED_IN, 2);

        // Orig: 10:00 (cap: 10, booked: 5)
        ServiceSlotJpaEntity origSlot = buildSlot(originalSlotId, date, LocalTime.of(10, 0), LocalTime.of(11, 30), 10, 5, SlotStatus.OPEN);
        // Target: 08:30 (cap: 10, booked: 8 -> 2 seats left in Postgres, but Redis holds 2 seats)
        ServiceSlotJpaEntity targetSlot = buildSlot(targetSlotId, date, LocalTime.of(8, 30), LocalTime.of(10, 0), 10, 8, SlotStatus.OPEN);

        org.springframework.data.redis.core.StringRedisTemplate redisTemplate = mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        org.springframework.data.redis.core.HashOperations hashOperations = mock(org.springframework.data.redis.core.HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        long futureExp = System.currentTimeMillis() + 600000;
        // Hold value formatted as "<exp>|PERSON_LIMIT:<qty>"
        when(hashOperations.entries("inventory:slot:" + targetSlotId + ":holds"))
                .thenReturn(java.util.Map.of("hold1", futureExp + "|PERSON_LIMIT:2"));

        ReflectionTestUtils.setField(useCase, "redisTemplate", redisTemplate);

        when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));
        when(slotRepository.findByIdForUpdate(originalSlotId)).thenReturn(Optional.of(origSlot));
        when(slotRepository.findByIdForUpdate(targetSlotId)).thenReturn(Optional.of(targetSlot));

        var req = new EarlyDispatchSlotUseCase.EarlyDispatchRequest(subOrderId, targetSlotId, "Early dispatch");

        assertThatThrownBy(() -> useCase.execute(req, staffId))
                .isInstanceOf(InvalidCheckinStateException.class)
                .hasMessageContaining("does not have sufficient capacity")
                .hasMessageContaining("Holds: 2");
    }
}
