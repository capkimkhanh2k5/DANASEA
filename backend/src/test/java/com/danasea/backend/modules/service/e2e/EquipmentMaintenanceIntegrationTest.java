package com.danasea.backend.modules.service.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.danasea.backend.e2e.BaseE2ETest;
import com.danasea.backend.modules.booking.domain.models.BookingStatus;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingItemAllocationJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingItemJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingItemRepository;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.service.application.usecases.EquipmentAssignmentService;
import com.danasea.backend.modules.service.application.usecases.EquipmentMaintenanceService;
import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotUnitRepository;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

class EquipmentMaintenanceIntegrationTest extends BaseE2ETest {
    @MockitoBean LettuceBasedProxyManager<byte[]> proxyManager;
    @Autowired EquipmentAssignmentService assignments;
    @Autowired EquipmentMaintenanceService maintenance;
    @Autowired JpaBookingRepository bookings;
    @Autowired JpaBookingItemRepository items;
    @Autowired JpaServiceSlotUnitRepository units;
    @Autowired JdbcTemplate jdbc;

    @Test
    void reallocatesExistingBookingAndRejectsPlannedRemovalOfTheOnlyRemainingBoat() {
        var slot = slotA1TomorrowMorning;
        slot.setInventoryType(InventoryType.SHARED_CAPACITY_UNITS);
        slot.setBookedCount(0);
        slot.setCapacity(16);
        serviceSlotRepository.saveAndFlush(slot);

        for (int number = 1; number <= 2; number++) {
            jdbc.update("""
                    INSERT INTO service_slot_units (id, slot_id, unit_number, capacity, booked_count, created_at, updated_at)
                    VALUES (?,?,?,8,0,now(),now())
                    """, UUID.randomUUID(), slot.getId(), number);
        }
        UUID vendorUser = vendorUserA.getId();
        UUID first = assignments.create(vendorUser, "Cano B", "canos-equivalent", 8).id();
        UUID second = assignments.create(vendorUser, "Cano A", "canos-equivalent", 8).id();
        assignments.attach(vendorUser, slot.getId(), 1, first);
        assignments.attach(vendorUser, slot.getId(), 2, second);

        UUID itemId = addConfirmedGroup(slot.getId());
        jdbc.update("UPDATE service_slot_units SET booked_count=3 WHERE slot_id=? AND unit_number=1", slot.getId());
        jdbc.update("UPDATE service_slots SET booked_count=3 WHERE id=?", slot.getId());

        var start = slot.getDate().atTime(slot.getStartTime());
        var end = slot.getDate().atTime(slot.getEndTime());
        UUID firstMaintenance = UUID.randomUUID();
        var result = maintenance.schedule(vendorUser, first, firstMaintenance, start, end, false, "Planned service");

        assertThat(result.maintenance().movedAllocations()).isEqualTo(1);
        assertThat(result.affectedBookings()).isEmpty();
        assertThat(units.findBySlotIdAndUnitNumber(slot.getId(), 1).orElseThrow().isEquipmentBlocked()).isTrue();
        assertThat(units.findBySlotIdAndUnitNumber(slot.getId(), 2).orElseThrow().getBookedCount()).isEqualTo(3);
        assertThat(items.findById(itemId).orElseThrow().getAllocations()).singleElement()
                .satisfies(allocation -> assertThat(allocation.getUnitNumber()).isEqualTo(2));
        assertThat(maintenance.schedule(vendorUser, first, firstMaintenance, start, end, false, "Planned service"))
                .isEqualTo(result);

        UUID secondMaintenance = UUID.randomUUID();
        assertThatThrownBy(() -> maintenance.schedule(vendorUser, second, secondMaintenance, start, end,
                false, "Would leave no capacity"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(units.findBySlotIdAndUnitNumber(slot.getId(), 2).orElseThrow().isEquipmentBlocked()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM equipment_maintenance WHERE id=?",
                Integer.class, secondMaintenance)).isZero();
    }

    private UUID addConfirmedGroup(UUID slotId) {
        var booking = new BookingJpaEntity();
        booking.setCustomerId(customerUser.getId());
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setTotalAmount(paidOrder.getTotalAmount());
        var saved = bookings.saveAndFlush(booking);
        var item = BookingItemJpaEntity.builder()
                .booking(saved)
                .slotId(slotId)
                .serviceId(serviceA1.getId())
                .vendorId(vendorProfileA.getId())
                .quantity(3)
                .pricingUnit("PER_PERSON")
                .bookingDate(slotA1TomorrowMorning.getDate())
                .bookingTime(slotA1TomorrowMorning.getStartTime())
                .price(paidSubOrder.getUnitPrice())
                .build();
        item.addAllocation(BookingItemAllocationJpaEntity.builder()
                .slotId(slotId).unitNumber(1).allocatedSeats(3).isPrivateLock(false).build());
        return items.saveAndFlush(item).getId();
    }
}
