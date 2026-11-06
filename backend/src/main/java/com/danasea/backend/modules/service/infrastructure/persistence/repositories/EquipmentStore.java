package com.danasea.backend.modules.service.infrastructure.persistence.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EquipmentStore {

    private final JdbcTemplate jdbc;

    public record Equipment(UUID id, String name, String compatibilityGroup, int capacity) {}
    public record Slot(UUID id, LocalDateTime start, LocalDateTime end, String status) {}
    public record Unit(UUID id, int number, UUID equipmentId, String compatibilityGroup,
            int capacity, int booked, boolean blocked) {}
    public record Commitment(UUID allocationId, UUID bookingItemId, int unitNumber, int allocated,
            int required, boolean privateRental, boolean held, boolean checkedIn) {}
    public record Maintenance(UUID id, UUID equipmentId, LocalDateTime start, LocalDateTime end,
            boolean urgent, String reason, int affectedSlots, int movedAllocations) {}
    public record AffectedBooking(UUID bookingItemId, UUID slotId, String type, boolean processed) {}
    public record Impact(UUID maintenanceId, UUID bookingItemId, UUID subOrderId, UUID slotId,
            String reason, UUID requestedBy) {}

    public Equipment create(UUID vendorId, String name, String compatibilityGroup, int capacity) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO vendor_equipment(id,vendor_id,name,compatibility_group,capacity)
                VALUES (?,?,?,?,?)
                """, id, vendorId, name, compatibilityGroup, capacity);
        return new Equipment(id, name, compatibilityGroup, capacity);
    }

    public List<Equipment> list(UUID vendorId) {
        return jdbc.query("""
                SELECT id,name,compatibility_group,capacity FROM vendor_equipment
                WHERE vendor_id=? ORDER BY name,id
                """, (rs, i) -> new Equipment(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("compatibility_group"), rs.getInt("capacity")), vendorId);
    }

    public Optional<Equipment> equipment(UUID vendorId, UUID id, boolean lock) {
        String sql = """
                SELECT id,name,compatibility_group,capacity FROM vendor_equipment
                WHERE vendor_id=? AND id=?
                """;
        return jdbc.query(lock ? sql + " FOR UPDATE" : sql, (rs, i) ->
                new Equipment(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("compatibility_group"), rs.getInt("capacity")),
                vendorId, id).stream().findFirst();
    }

    public Optional<Slot> lockSlot(UUID vendorId, UUID slotId) {
        return jdbc.query("""
                SELECT sl.id, sl.date + sl.start_time AS starts_at, sl.date + sl.end_time AS ends_at, sl.status
                FROM service_slots sl JOIN services s ON s.id = sl.service_id
                WHERE sl.id=? AND s.vendor_id=?
                FOR UPDATE OF sl
                """, (rs, i) -> new Slot(rs.getObject("id", UUID.class),
                rs.getObject("starts_at", LocalDateTime.class), rs.getObject("ends_at", LocalDateTime.class),
                rs.getString("status")), slotId, vendorId).stream().findFirst();
    }

    public List<Unit> units(UUID slotId) {
        return jdbc.query("""
                SELECT u.id,u.unit_number,u.equipment_id,u.capacity,u.booked_count,u.equipment_blocked,
                       e.compatibility_group
                FROM service_slot_units u LEFT JOIN vendor_equipment e ON e.id=u.equipment_id
                WHERE u.slot_id=? ORDER BY u.unit_number
                """, (rs, i) -> new Unit(rs.getObject("id", UUID.class), rs.getInt("unit_number"),
                rs.getObject("equipment_id", UUID.class), rs.getString("compatibility_group"),
                rs.getInt("capacity"), rs.getInt("booked_count"), rs.getBoolean("equipment_blocked")), slotId);
    }

    public boolean alreadyAssignedDuring(UUID equipmentId, Slot slot) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM service_slot_units u JOIN service_slots s ON s.id=u.slot_id
                    WHERE u.equipment_id=? AND s.id<>? AND s.status<>'CANCELLED'
                      AND s.date+s.start_time<? AND s.date+s.end_time>?
                )
                """, Boolean.class, equipmentId, slot.id(), slot.end(), slot.start()));
    }

    public boolean maintenanceOverlaps(UUID equipmentId, LocalDateTime start, LocalDateTime end) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM equipment_maintenance WHERE equipment_id=? AND starts_at<? AND ends_at>?
                )
                """, Boolean.class, equipmentId, end, start));
    }

    public void bind(UUID unitId, UUID equipmentId, boolean blocked) {
        jdbc.update("""
                UPDATE service_slot_units SET equipment_id=?, equipment_blocked=?, updated_at=now()
                WHERE id=?
                """, equipmentId, blocked, unitId);
    }

    public List<UUID> affectedSlots(UUID equipmentId, LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        return jdbc.query("""
                SELECT s.id
                FROM service_slot_units u JOIN service_slots s ON s.id=u.slot_id
                WHERE u.equipment_id=? AND s.status<>'CANCELLED'
                  AND s.date+s.start_time<? AND s.date+s.end_time>? AND s.date+s.end_time>?
                """, (rs, i) -> rs.getObject("id", UUID.class), equipmentId, end, start, now)
                .stream().sorted().toList();
    }

    public List<Commitment> commitments(UUID slotId) {
        return jdbc.query("""
                SELECT a.id,bi.id AS item_id,a.unit_number,a.allocated_seats,a.is_private_lock,
                       CASE WHEN a.is_private_lock THEN COALESCE(bi.max_pax_per_package,a.allocated_seats)
                            ELSE a.allocated_seats END AS required,
                       b.status IN ('HOLD','PENDING_PAYMENT') AS held,
                       (o.checked_in_at IS NOT NULL OR o.status IN ('CHECKED_IN','COMPLETED')
                            OR b.status='COMPLETED') AS checked_in
                FROM booking_item_allocations a
                JOIN booking_items bi ON bi.id=a.booking_item_id
                JOIN bookings b ON b.id=bi.booking_id
                LEFT JOIN sub_orders o ON o.booking_item_id=bi.id
                WHERE a.slot_id=? AND bi.slot_id=a.slot_id AND NOT bi.capacity_released
                  AND (b.status IN ('CONFIRMED','COMPLETED')
                    OR (b.status IN ('HOLD','PENDING_PAYMENT') AND b.hold_expires_at>CURRENT_TIMESTAMP))
                ORDER BY COALESCE(
                    (SELECT MIN(p.paid_at) FROM payments p WHERE p.master_order_id=o.master_order_id),
                    b.updated_at,b.created_at),bi.id,a.id
                """, (rs, i) -> new Commitment(rs.getObject("id", UUID.class),
                rs.getObject("item_id", UUID.class), rs.getInt("unit_number"), rs.getInt("allocated_seats"),
                rs.getInt("required"), rs.getBoolean("is_private_lock"), rs.getBoolean("held"),
                rs.getBoolean("checked_in")), slotId);
    }

    public boolean maintenance(UUID id, UUID equipmentId, LocalDateTime start, LocalDateTime end,
            boolean urgent, String reason, UUID actor) {
        return jdbc.update("""
                INSERT INTO equipment_maintenance(id,equipment_id,starts_at,ends_at,urgent,reason,requested_by)
                VALUES (?,?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING
                """, id, equipmentId, start, end, urgent, reason, actor) == 1;
    }

    public Optional<Maintenance> findMaintenance(UUID equipmentId, UUID maintenanceId) {
        return jdbc.query("""
                SELECT * FROM equipment_maintenance WHERE equipment_id=? AND id=?
                """, (rs, i) -> new Maintenance(rs.getObject("id", UUID.class), equipmentId,
                rs.getObject("starts_at", LocalDateTime.class), rs.getObject("ends_at", LocalDateTime.class),
                rs.getBoolean("urgent"), rs.getString("reason"), rs.getInt("affected_slots"),
                rs.getInt("moved_allocations")), equipmentId, maintenanceId).stream().findFirst();
    }

    public void updateMaintenanceCounts(UUID maintenanceId, int affectedSlots, int movedAllocations) {
        jdbc.update("""
                UPDATE equipment_maintenance SET affected_slots=?, moved_allocations=? WHERE id=?
                """, affectedSlots, movedAllocations, maintenanceId);
    }

    public List<AffectedBooking> affectedBookings(UUID maintenanceId) {
        return jdbc.query("""
                SELECT booking_item_id,slot_id,impact_type,resolved_at FROM equipment_maintenance_impacts
                WHERE maintenance_id=? ORDER BY slot_id,booking_item_id
                """, (rs, i) -> new AffectedBooking(rs.getObject("booking_item_id", UUID.class),
                rs.getObject("slot_id", UUID.class), rs.getString("impact_type"), rs.getObject("resolved_at") != null),
                maintenanceId);
    }

    public void block(UUID unitId) {
        jdbc.update("""
                UPDATE service_slot_units SET equipment_blocked=TRUE, updated_at=now() WHERE id=?
                """, unitId);
    }

    public void move(UUID maintenanceId, UUID slotId, Unit source, Commitment commitment, int targetNumber, int seats) {
        jdbc.update("""
                INSERT INTO equipment_maintenance_moves(maintenance_id,allocation_id,booking_item_id,slot_id,
                    source_unit_number,target_unit_number,original_seats,moved_seats)
                VALUES (?,?,?,?,?,?,?,?)
                """, maintenanceId, commitment.allocationId(), commitment.bookingItemId(), slotId,
                source.number(), targetNumber, commitment.allocated(), seats);
        jdbc.update("""
                UPDATE booking_item_allocations SET unit_number=?, allocated_seats=?, updated_at=now() WHERE id=?
                """, targetNumber, seats, commitment.allocationId());
        jdbc.update("""
                UPDATE service_slot_units SET booked_count=booked_count-?, updated_at=now() WHERE id=?
                """, commitment.allocated(), source.id());
        jdbc.update("""
                UPDATE service_slot_units SET booked_count=booked_count+?, updated_at=now()
                WHERE slot_id=? AND unit_number=?
                """, seats, slotId, targetNumber);
        int delta = seats - commitment.allocated();
        if (delta != 0) {
            jdbc.update("""
                    UPDATE service_slots SET booked_count=booked_count+?, updated_at=now() WHERE id=?
                    """, delta, slotId);
        }
    }

    public void impact(UUID maintenanceId, UUID slotId, Commitment commitment) {
        jdbc.update("""
                INSERT INTO equipment_maintenance_impacts(maintenance_id,booking_item_id,slot_id,impact_type)
                VALUES (?,?,?,?) ON CONFLICT DO NOTHING
                """, maintenanceId, commitment.bookingItemId(), slotId,
                commitment.held() ? "ACTIVE_HOLD" : commitment.checkedIn() ? "IN_OPERATION" : "CONFIRMED");
    }

    public List<Impact> pendingConfirmedImpacts() {
        return jdbc.query("""
                SELECT i.maintenance_id,i.booking_item_id,i.slot_id,o.id AS sub_order_id,m.reason,m.requested_by
                FROM equipment_maintenance_impacts i
                JOIN equipment_maintenance m ON m.id=i.maintenance_id
                JOIN sub_orders o ON o.booking_item_id=i.booking_item_id
                JOIN master_orders mo ON mo.id=o.master_order_id
                WHERE i.resolved_at IS NULL AND i.impact_type='CONFIRMED' AND o.slot_id=i.slot_id
                  AND o.status='CONFIRMED' AND o.checked_in_at IS NULL AND mo.payment_status='PAID'
                ORDER BY m.created_at,i.booking_item_id LIMIT 100
                """, (rs, i) -> new Impact(rs.getObject("maintenance_id", UUID.class),
                rs.getObject("booking_item_id", UUID.class), rs.getObject("sub_order_id", UUID.class),
                rs.getObject("slot_id", UUID.class), rs.getString("reason"), rs.getObject("requested_by", UUID.class)));
    }

    public void resolve(Impact impact) {
        jdbc.update("""
                UPDATE equipment_maintenance_impacts SET resolved_at=now()
                WHERE maintenance_id=? AND booking_item_id=?
                """, impact.maintenanceId(), impact.bookingItemId());
    }
}
