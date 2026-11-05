package com.danasea.backend.modules.service.application.usecases;

import static com.danasea.backend.modules.service.application.usecases.EquipmentAssignmentService.conflict;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.order.application.services.DisruptionDecisionService;
import com.danasea.backend.modules.order.application.services.RescheduleSupport;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.EquipmentStore;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.EquipmentStore.AffectedBooking;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.EquipmentStore.Commitment;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.EquipmentStore.Impact;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.EquipmentStore.Maintenance;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class EquipmentMaintenanceService {

    private final EquipmentAssignmentService assignment;
    private final EquipmentStore store;
    private final RescheduleSupport orderLocks;
    private final DisruptionDecisionService disruptions;

    public record Result(Maintenance maintenance, List<AffectedBooking> affectedBookings) {}
    public record SlotConflict(UUID slotId, List<UUID> bookingItemIds, boolean inconsistentInventory) {}

    public static final class PlannedConflict extends ResponseStatusException {
        private final List<SlotConflict> conflicts;

        private PlannedConflict(List<SlotConflict> conflicts) {
            super(HttpStatus.CONFLICT, "Maintenance would affect active commitments");
            this.conflicts = List.copyOf(conflicts);
        }

        public List<SlotConflict> conflicts() {
            return conflicts;
        }
    }

    @Transactional
    public Result schedule(UUID userId, UUID equipmentId, UUID maintenanceId,
            LocalDateTime start, LocalDateTime end, boolean urgent, String reason) {
        String details = reason.strip();
        start = start.truncatedTo(ChronoUnit.MICROS);
        end = end.truncatedTo(ChronoUnit.MICROS);
        UUID vendorId = assignment.vendorId(userId);
        var equipment = store.equipment(vendorId, equipmentId, true)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));
        var previous = store.findMaintenance(equipmentId, maintenanceId);
        if (previous.isPresent()) {
            Maintenance existing = previous.get();
            if (!existing.start().equals(start) || !existing.end().equals(end)
                    || existing.urgent() != urgent || !existing.reason().equals(details)) {
                throw conflict("Maintenance ID was already used with different details");
            }
            return new Result(existing, store.affectedBookings(maintenanceId));
        }
        LocalDateTime now = LocalDateTime.now(Booking.VIETNAM_ZONE);
        if (!end.isAfter(start) || !end.isAfter(now) || (!urgent && start.isBefore(now))) {
            throw new IllegalArgumentException("Maintenance must end in the future and planned maintenance must start in the future");
        }
        if (store.maintenanceOverlaps(equipmentId, start, end)) {
            throw conflict("Equipment already has maintenance for the requested period");
        }
        if (!store.maintenance(maintenanceId, equipmentId, start, end, urgent, details, userId)) {
            throw conflict("Maintenance ID is already in use");
        }

        int moved = 0;
        int affected = 0;
        List<SlotConflict> conflicts = new ArrayList<>();
        for (UUID slotId : store.affectedSlots(equipmentId, start, end, now)) {
            var slot = store.lockSlot(vendorId, slotId).orElseThrow();
            if ("CANCELLED".equals(slot.status())) {
                continue;
            }
            var units = store.units(slotId);
            var source = units.stream().filter(unit -> equipmentId.equals(unit.equipmentId())).findFirst().orElseThrow();
            var commitments = store.commitments(slotId);
            var onSource = commitments.stream().filter(c -> c.unitNumber() == source.number()).toList();
            int trackedSeats = onSource.stream().filter(c -> !c.held()).mapToInt(Commitment::allocated).sum();
            var movable = onSource.stream()
                    .filter(c -> trackedSeats == source.booked())
                    .filter(c -> !c.held() && !c.checkedIn() && slot.start().isAfter(now))
                    .toList();
            var compatibleUnits = units.stream()
                    .filter(u -> !u.id().equals(source.id()) && !u.blocked()
                            && equipment.compatibilityGroup().equals(u.compatibilityGroup()))
                    .map(u -> new EquipmentReallocation.Unit(u.number(), u.capacity(), u.booked()
                            + commitments.stream().filter(c -> c.held() && c.unitNumber() == u.number())
                                    .mapToInt(Commitment::allocated).sum()))
                    .toList();
            var plan = EquipmentReallocation.plan(
                    movable.stream().map(c -> new EquipmentReallocation.Commitment(
                            c.allocationId(), c.required(), c.privateRental())).toList(),
                    compatibleUnits, urgent);

            Set<UUID> movedIds = new HashSet<>();
            for (var move : plan.moves()) {
                movedIds.add(move.commitmentId());
            }
            List<Commitment> unplaced = onSource.stream()
                    .filter(c -> !movedIds.contains(c.allocationId())).toList();
            if (!urgent && (!unplaced.isEmpty() || trackedSeats != source.booked())) {
                conflicts.add(new SlotConflict(slotId,
                        unplaced.stream().map(Commitment::bookingItemId).distinct().toList(),
                        trackedSeats != source.booked()));
                continue;
            }

            for (var move : plan.moves()) {
                Commitment commitment = movable.stream()
                        .filter(c -> c.allocationId().equals(move.commitmentId())).findFirst().orElseThrow();
                store.move(maintenanceId, slotId, source, commitment, move.unitNumber(), move.allocatedSeats());
                moved++;
            }
            store.block(source.id());
            affected++;
            for (Commitment commitment : unplaced) {
                store.impact(maintenanceId, slotId, commitment);
            }
        }
        if (!conflicts.isEmpty()) {
            throw new PlannedConflict(conflicts);
        }
        store.updateMaintenanceCounts(maintenanceId, affected, moved);
        return new Result(new Maintenance(maintenanceId, equipmentId, start, end, urgent, details, affected, moved),
                store.affectedBookings(maintenanceId));
    }

    @Transactional(readOnly = true)
    public Result get(UUID userId, UUID equipmentId, UUID maintenanceId) {
        if (store.equipment(assignment.vendorId(userId), equipmentId, false).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found");
        }
        var entry = store.findMaintenance(equipmentId, maintenanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Maintenance not found"));
        return new Result(entry, store.affectedBookings(maintenanceId));
    }

    @Transactional
    public void handleImpact(Impact impact) {
        var order = orderLocks.lock(impact.subOrderId());
        if (!Objects.equals(order.sub().getSlotId(), impact.slotId())
                || order.sub().getStatus() != SubOrderStatus.CONFIRMED
                || order.sub().getCheckedInAt() != null) {
            store.resolve(impact);
            return;
        }
        disruptions.record(impact.subOrderId(), RefundReason.VENDOR_FAULT,
                impact.reason(), "equipment-maintenance-" + impact.maintenanceId(), impact.requestedBy());
        store.resolve(impact);
    }
}
