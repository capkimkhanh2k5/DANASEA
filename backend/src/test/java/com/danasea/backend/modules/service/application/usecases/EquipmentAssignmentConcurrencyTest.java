package com.danasea.backend.modules.service.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.EquipmentStore;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.EquipmentStore.Equipment;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.EquipmentStore.Slot;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.EquipmentStore.Unit;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class EquipmentAssignmentConcurrencyTest {

    @Mock private VendorPort vendors;
    @Mock private EquipmentStore store;

    private EquipmentAssignmentService service;

    private UUID userId;
    private UUID vendorId;
    private UUID equipment1Id;
    private UUID equipment2Id;
    private UUID slot1Id;
    private UUID slot2Id;
    private LocalDate tomorrow;

    @BeforeEach
    void setUp() {
        service = new EquipmentAssignmentService(vendors, store);
        userId = UUID.randomUUID();
        vendorId = UUID.randomUUID();
        equipment1Id = UUID.randomUUID();
        equipment2Id = UUID.randomUUID();
        slot1Id = UUID.randomUUID();
        slot2Id = UUID.randomUUID();
        tomorrow = LocalDate.now().plusDays(2);

        Vendor vendor = new Vendor();
        vendor.setId(vendorId);
        vendor.setUserId(userId);
        when(vendors.findByUserId(userId)).thenReturn(Optional.of(vendor));
    }

    @Test
    @DisplayName("Hai ca chồng giờ (08:00–10:00 và 08:30–10:30) cố gán CÙNG thiết bị -> bị chặn bởi alreadyAssignedDuring với EQUIPMENT_CONFLICT")
    void overlappingSlots_sameEquipment_throwsEquipmentConflict() {
        // Slot 2: 08:30–10:30
        LocalDateTime start = tomorrow.atTime(8, 30);
        LocalDateTime end = tomorrow.atTime(10, 30);
        Slot slot2 = new Slot(slot2Id, start, end, "OPEN");

        Equipment eq1 = new Equipment(equipment1Id, "Cano A", "cano-6", 6);
        // Equipment row is locked FOR UPDATE
        when(store.equipment(vendorId, equipment1Id, true)).thenReturn(Optional.of(eq1));
        when(store.lockSlot(vendorId, slot2Id)).thenReturn(Optional.of(slot2));

        UUID unitId = UUID.randomUUID();
        when(store.units(slot2Id)).thenReturn(List.of(new Unit(unitId, 1, null, "cano-6", 6, 0, false)));
        when(store.commitments(slot2Id)).thenReturn(List.of());

        // store detects equipment1 is already assigned to Slot 1 (08:00–10:00) which overlaps!
        when(store.alreadyAssignedDuring(equipment1Id, slot2)).thenReturn(true);

        assertThatThrownBy(() -> service.attach(userId, slot2Id, 1, equipment1Id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Physical equipment already assigned to an overlapping slot");

        // Verify row-level lock FOR UPDATE was acquired on equipment
        verify(store).equipment(vendorId, equipment1Id, true);
    }

    @Test
    @DisplayName("Hai ca chồng giờ (08:00–10:00 và 08:30–10:30) gán KHÁC thiết bị (Cano 1 và Cano 2) -> thành công, không xung đột giả")
    void overlappingSlots_differentEquipment_succeeds() {
        LocalDateTime start = tomorrow.atTime(8, 30);
        LocalDateTime end = tomorrow.atTime(10, 30);
        Slot slot2 = new Slot(slot2Id, start, end, "OPEN");

        Equipment eq2 = new Equipment(equipment2Id, "Cano B", "cano-6", 6);
        when(store.equipment(vendorId, equipment2Id, true)).thenReturn(Optional.of(eq2));
        when(store.lockSlot(vendorId, slot2Id)).thenReturn(Optional.of(slot2));

        UUID unitId = UUID.randomUUID();
        when(store.units(slot2Id)).thenReturn(List.of(new Unit(unitId, 1, null, "cano-6", 6, 0, false)));
        when(store.commitments(slot2Id)).thenReturn(List.of());

        // Cano 2 is not assigned to any overlapping slot
        when(store.alreadyAssignedDuring(equipment2Id, slot2)).thenReturn(false);
        when(store.maintenanceOverlaps(equipment2Id, start, end)).thenReturn(false);

        service.attach(userId, slot2Id, 1, equipment2Id);

        verify(store).bind(unitId, equipment2Id, false);
    }

    @Test
    @DisplayName("Hai ca không chồng giờ (08:00–10:00 và 10:00–12:00) tái sử dụng CÙNG thiết bị (Cano 1) -> thành công")
    void nonOverlappingSlots_sameEquipment_succeeds() {
        LocalDateTime start = tomorrow.atTime(10, 0);
        LocalDateTime end = tomorrow.atTime(12, 0);
        Slot slot2 = new Slot(slot2Id, start, end, "OPEN");

        Equipment eq1 = new Equipment(equipment1Id, "Cano A", "cano-6", 6);
        when(store.equipment(vendorId, equipment1Id, true)).thenReturn(Optional.of(eq1));
        when(store.lockSlot(vendorId, slot2Id)).thenReturn(Optional.of(slot2));

        UUID unitId = UUID.randomUUID();
        when(store.units(slot2Id)).thenReturn(List.of(new Unit(unitId, 1, null, "cano-6", 6, 0, false)));
        when(store.commitments(slot2Id)).thenReturn(List.of());

        // Slot 1 ended at 10:00, Slot 2 starts at 10:00: s.date+s.start_time < slot2.end AND s.date+s.end_time > slot2.start (10:00 > 10:00 is FALSE) -> no overlap!
        when(store.alreadyAssignedDuring(equipment1Id, slot2)).thenReturn(false);
        when(store.maintenanceOverlaps(equipment1Id, start, end)).thenReturn(false);

        service.attach(userId, slot2Id, 1, equipment1Id);

        verify(store).bind(unitId, equipment1Id, false);
    }

    @Test
    @DisplayName("C.5 Detach: Gỡ thiết bị khỏi đơn vị chưa có khách/HOLD -> gọi store.unbind")
    void detach_emptyUncommittedUnit_unbindsEquipment() {
        Slot slot2 = new Slot(slot2Id, tomorrow.atTime(9, 0), tomorrow.atTime(11, 0), "OPEN");
        Equipment eq1 = new Equipment(equipment1Id, "Cano A", "cano-6", 6);
        UUID unitId = UUID.randomUUID();

        when(store.equipment(vendorId, equipment1Id, true)).thenReturn(Optional.of(eq1));
        when(store.lockSlot(vendorId, slot2Id)).thenReturn(Optional.of(slot2));
        when(store.units(slot2Id)).thenReturn(List.of(new Unit(unitId, 1, equipment1Id, "cano-6", 6, 0, false)));
        when(store.commitments(slot2Id)).thenReturn(List.of());

        service.detach(userId, slot2Id, 1, equipment1Id);

        verify(store).unbind(unitId);
    }

    @Test
    @DisplayName("C.5 Detach: Đơn vị đang có active Redis HOLD -> chặn với 409 CONFLICT")
    @SuppressWarnings("unchecked")
    void detach_withActiveRedisHold_throwsConflict() {
        StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashOps = org.mockito.Mockito.mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.entries("inventory:slot:" + slot2Id + ":holds"))
                .thenReturn(Map.of("hold-1", (System.currentTimeMillis() + 60_000L) + "|UNITS:1:2:0"));
        ReflectionTestUtils.setField(service, "redisTemplate", redisTemplate);

        Slot slot2 = new Slot(slot2Id, tomorrow.atTime(9, 0), tomorrow.atTime(11, 0), "OPEN");
        Equipment eq1 = new Equipment(equipment1Id, "Cano A", "cano-6", 6);
        UUID unitId = UUID.randomUUID();

        when(store.equipment(vendorId, equipment1Id, true)).thenReturn(Optional.of(eq1));
        when(store.lockSlot(vendorId, slot2Id)).thenReturn(Optional.of(slot2));
        when(store.units(slot2Id)).thenReturn(List.of(new Unit(unitId, 1, equipment1Id, "cano-6", 6, 0, false)));
        when(store.commitments(slot2Id)).thenReturn(List.of());

        assertThatThrownBy(() -> service.detach(userId, slot2Id, 1, equipment1Id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("active hold");

        org.mockito.Mockito.verify(store, org.mockito.Mockito.never()).unbind(any());
    }

    @Test
    @DisplayName("C.5 Detach & Replace: Đơn vị đã có khách đặt (booked > 0 hoặc CONFIRMED commitment) -> chặn 409")
    void detachAndReplace_withConfirmedBooking_throwsConflict() {
        Slot slot2 = new Slot(slot2Id, tomorrow.atTime(9, 0), tomorrow.atTime(11, 0), "OPEN");
        Equipment eq1 = new Equipment(equipment1Id, "Cano A", "cano-6", 6);
        Equipment eq2 = new Equipment(equipment2Id, "Cano B", "cano-8", 8);
        UUID unitId = UUID.randomUUID();
        var confirmedCommitment = new EquipmentStore.Commitment(
                UUID.randomUUID(), UUID.randomUUID(), 1, 2, 2, false, false, false);

        when(store.equipment(vendorId, equipment1Id, true)).thenReturn(Optional.of(eq1));
        when(store.equipment(vendorId, equipment2Id, true)).thenReturn(Optional.of(eq2));
        when(store.lockSlot(vendorId, slot2Id)).thenReturn(Optional.of(slot2));
        when(store.units(slot2Id)).thenReturn(List.of(new Unit(unitId, 1, equipment1Id, "cano-6", 6, 2, false)));
        when(store.commitments(slot2Id)).thenReturn(List.of(confirmedCommitment));

        assertThatThrownBy(() -> service.detach(userId, slot2Id, 1, equipment1Id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("committed booking");
        assertThatThrownBy(() -> service.attach(userId, slot2Id, 1, equipment2Id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("committed booking");

        org.mockito.Mockito.verify(store, org.mockito.Mockito.never()).unbind(any());
        org.mockito.Mockito.verify(store, org.mockito.Mockito.never())
                .replaceUnitEquipment(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @DisplayName("C.5 Replace: Đổi thiết bị khác sức chứa (6 -> 8) trên đơn vị trống -> replaceUnitEquipment atomic")
    void attach_replaceUncommittedUnit_differentCapacity_replacesAtomically() {
        LocalDateTime start = tomorrow.atTime(9, 0);
        LocalDateTime end = tomorrow.atTime(11, 0);
        Slot slot2 = new Slot(slot2Id, start, end, "OPEN");
        Equipment eq2 = new Equipment(equipment2Id, "Cano 8", "cano-8", 8);
        UUID unitId = UUID.randomUUID();

        when(store.equipment(vendorId, equipment2Id, true)).thenReturn(Optional.of(eq2));
        when(store.lockSlot(vendorId, slot2Id)).thenReturn(Optional.of(slot2));
        when(store.units(slot2Id)).thenReturn(List.of(new Unit(unitId, 1, equipment1Id, "cano-6", 6, 0, false)));
        when(store.commitments(slot2Id)).thenReturn(List.of());
        when(store.alreadyAssignedDuring(equipment2Id, slot2)).thenReturn(false);
        when(store.maintenanceOverlaps(equipment2Id, start, end)).thenReturn(false);
        when(store.maxPrivateOptionPax(slot2Id)).thenReturn(0);

        service.attach(userId, slot2Id, 1, equipment2Id);

        verify(store).replaceUnitEquipment(slot2Id, unitId, equipment2Id, 8, false);
    }

    @Test
    @DisplayName("C.5 Replace: Đổi sang thiết bị nhỏ hơn mức tối đa của gói PRIVATE -> chặn, giữ nguyên thiết bị cũ")
    void attach_replaceSmallerThanPrivateOption_throwsAndPreservesOriginal() {
        LocalDateTime start = tomorrow.atTime(9, 0);
        LocalDateTime end = tomorrow.atTime(11, 0);
        Slot slot2 = new Slot(slot2Id, start, end, "OPEN");
        Equipment smallEq = new Equipment(equipment2Id, "Kayak 2", "kayak-2", 2);
        UUID unitId = UUID.randomUUID();

        when(store.equipment(vendorId, equipment2Id, true)).thenReturn(Optional.of(smallEq));
        when(store.lockSlot(vendorId, slot2Id)).thenReturn(Optional.of(slot2));
        when(store.units(slot2Id)).thenReturn(List.of(new Unit(unitId, 1, equipment1Id, "cano-6", 6, 0, false)));
        when(store.commitments(slot2Id)).thenReturn(List.of());
        when(store.alreadyAssignedDuring(equipment2Id, slot2)).thenReturn(false);
        when(store.maintenanceOverlaps(equipment2Id, start, end)).thenReturn(false);
        when(store.maxPrivateOptionPax(slot2Id)).thenReturn(6);

        assertThatThrownBy(() -> service.attach(userId, slot2Id, 1, equipment2Id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private option maximum capacity");

        org.mockito.Mockito.verify(store, org.mockito.Mockito.never())
                .replaceUnitEquipment(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @DisplayName("C.5 Replace: Đổi sang thiết bị đang trùng ca khác -> chặn với EQUIPMENT_CONFLICT, không mất thiết bị cũ")
    void attach_replaceOverlappingTargetEquipment_throwsAndPreservesOriginal() {
        LocalDateTime start = tomorrow.atTime(9, 0);
        LocalDateTime end = tomorrow.atTime(11, 0);
        Slot slot2 = new Slot(slot2Id, start, end, "OPEN");
        Equipment eq2 = new Equipment(equipment2Id, "Cano B", "cano-8", 8);
        UUID unitId = UUID.randomUUID();

        when(store.equipment(vendorId, equipment2Id, true)).thenReturn(Optional.of(eq2));
        when(store.lockSlot(vendorId, slot2Id)).thenReturn(Optional.of(slot2));
        when(store.units(slot2Id)).thenReturn(List.of(new Unit(unitId, 1, equipment1Id, "cano-6", 6, 0, false)));
        when(store.commitments(slot2Id)).thenReturn(List.of());
        when(store.alreadyAssignedDuring(equipment2Id, slot2)).thenReturn(true);

        assertThatThrownBy(() -> service.attach(userId, slot2Id, 1, equipment2Id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("overlapping slot");

        org.mockito.Mockito.verify(store, org.mockito.Mockito.never())
                .replaceUnitEquipment(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyBoolean());
    }
}
