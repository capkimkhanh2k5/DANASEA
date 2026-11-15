package com.danasea.backend.modules.service.infrastructure.persistence.repositories;

import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaServiceSlotRepository
                extends JpaRepository<ServiceSlotJpaEntity, UUID>, JpaSpecificationExecutor<ServiceSlotJpaEntity> {

        @Query("SELECT s FROM ServiceSlotJpaEntity s WHERE s.serviceId = :serviceId " +
                        "AND (s.date > :fromDate OR (s.date = :fromDate AND s.startTime >= :fromTime)) " +
                        "AND (s.date < :toDate OR (s.date = :toDate AND s.endTime <= :toTime)) " +
                        "ORDER BY s.date ASC, s.startTime ASC")
        Page<ServiceSlotJpaEntity> findSlots(
                        @Param("serviceId") UUID serviceId,
                        @Param("fromDate") LocalDate fromDate, @Param("fromTime") LocalTime fromTime,
                        @Param("toDate") LocalDate toDate, @Param("toTime") LocalTime toTime,
                        Pageable pageable);

        boolean existsByServiceIdAndDateAndStartTime(UUID serviceId, LocalDate date, LocalTime startTime);

        List<ServiceSlotJpaEntity> findByServiceIdOrderByDateAscStartTimeAsc(UUID serviceId);

        List<ServiceSlotJpaEntity> findByServiceIdAndDateBetweenOrderByDateAscStartTimeAsc(UUID serviceId,
                        LocalDate from, LocalDate to);

        List<ServiceSlotJpaEntity> findByServiceIdAndDateAndStatus(UUID serviceId, LocalDate date, SlotStatus status);

        @Query("""
                        SELECT s FROM ServiceSlotJpaEntity s
                        WHERE (s.date > :fromDate OR (s.date = :fromDate AND s.startTime > :fromTime))
                          AND (s.date < :toDate OR (s.date = :toDate AND s.startTime <= :toTime))
                        ORDER BY s.date, s.startTime, s.id
                        """)
        Slice<ServiceSlotJpaEntity> findDeparturesInWindow(
                        @Param("fromDate") LocalDate fromDate, @Param("fromTime") LocalTime fromTime,
                        @Param("toDate") LocalDate toDate, @Param("toTime") LocalTime toTime,
                        Pageable pageable);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query(value = """
                        WITH locked_slot AS (
                            SELECT s.id FROM service_slots s JOIN booking_items bi ON bi.slot_id = s.id
                            WHERE bi.id = :itemId FOR UPDATE OF s
                        ), released AS (
                            UPDATE booking_items bi SET capacity_released = TRUE
                            WHERE bi.id = :itemId AND NOT bi.capacity_released
                              AND EXISTS (SELECT 1 FROM locked_slot)
                              AND EXISTS (SELECT 1 FROM bookings b WHERE b.id = bi.booking_id
                                          AND b.status IN ('CONFIRMED', 'COMPLETED'))
                            RETURNING bi.id, bi.slot_id, bi.quantity
                        ), allocations AS (
                            SELECT a.slot_id, a.unit_number, SUM(a.allocated_seats)::integer AS seats
                            FROM booking_item_allocations a JOIN released r ON r.id = a.booking_item_id
                            GROUP BY a.slot_id, a.unit_number
                        ), released_units AS (
                            UPDATE service_slot_units u SET booked_count = GREATEST(0, u.booked_count - a.seats)
                            FROM allocations a WHERE u.slot_id = a.slot_id AND u.unit_number = a.unit_number
                            RETURNING u.id
                        )
                        UPDATE service_slots s
                        SET booked_count = GREATEST(0, s.booked_count - COALESCE(
                            (SELECT SUM(a.seats)::integer FROM allocations a), r.quantity))
                        FROM released r WHERE s.id = r.slot_id
                        """, nativeQuery = true)
        int releaseBookingItemCapacity(@Param("itemId") UUID itemId);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query(value = """
                        WITH locked_slot AS (
                            SELECT s.id FROM service_slots s JOIN booking_items bi ON bi.slot_id = s.id
                            WHERE bi.id = :itemId FOR UPDATE OF s
                        ), released AS (
                            UPDATE booking_items bi SET capacity_released = TRUE
                            WHERE bi.id = :itemId AND NOT bi.capacity_released
                              AND EXISTS (SELECT 1 FROM locked_slot)
                              AND EXISTS (SELECT 1 FROM bookings b WHERE b.id = bi.booking_id
                                          AND b.status IN ('CONFIRMED', 'COMPLETED'))
                        )
                        UPDATE service_slots SET booked_count = GREATEST(0, booked_count - (
                            SELECT bi.quantity FROM booking_items bi WHERE bi.id = :itemId
                        )) WHERE id = (SELECT id FROM locked_slot)
                        """, nativeQuery = true)
        int releaseCapacityAtomically(@Param("itemId") UUID bookingItemId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT s FROM ServiceSlotJpaEntity s WHERE s.id = :id")
        Optional<ServiceSlotJpaEntity> findByIdForUpdate(@Param("id") UUID id);

        @Query("SELECT s FROM ServiceSlotJpaEntity s WHERE s.id = :id")
        Optional<ServiceSlotJpaEntity> findByIdReadOnly(@Param("id") UUID id);

        List<ServiceSlotJpaEntity> findByServiceIdAndStatusAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(
                        UUID serviceId,
                        SlotStatus status,
                        LocalDate date);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("UPDATE ServiceSlotJpaEntity s SET s.bookedCount = s.bookedCount + :quantity WHERE s.id = :slotId AND (s.capacity - s.bookedCount) >= :quantity")
        int incrementBookedCount(@Param("slotId") UUID slotId, @Param("quantity") int quantity);

        List<ServiceSlotJpaEntity> findByDateBetween(LocalDate start, LocalDate end);

        List<ServiceSlotJpaEntity> findByDate(LocalDate date);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("UPDATE ServiceSlotJpaEntity s SET s.bookedCount = GREATEST(0, s.bookedCount - :quantity) WHERE s.id = :slotId")
        int decrementBookedCount(@Param("slotId") UUID slotId, @Param("quantity") int quantity);

        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Transactional
        @Query("UPDATE ServiceSlotJpaEntity s SET s.status = com.danasea.backend.modules.service.domain.models.SlotStatus.CLOSED WHERE s.id = :slotId AND s.status <> com.danasea.backend.modules.service.domain.models.SlotStatus.CANCELLED")
        int closeForWeather(@Param("slotId") UUID slotId);

        @Query(value = """
                        SELECT EXISTS (
                            SELECT 1 FROM booking_item_allocations a
                            JOIN service_slot_units u ON u.slot_id=a.slot_id AND u.unit_number=a.unit_number
                            WHERE a.booking_item_id=:bookingItemId AND u.equipment_blocked
                        )
                        """, nativeQuery = true)
        boolean hasBlockedEquipmentForBookingItem(@Param("bookingItemId") UUID bookingItemId);

        @Query(value = """
                        SELECT reason, requested_by AS "requestedBy" FROM vendor_departure_cancellations WHERE slot_id = :slotId
                        """, nativeQuery = true)
        Optional<VendorCancellationDetails> findVendorCancellation(@Param("slotId") UUID slotId);

        interface VendorCancellationDetails {
                String getReason();

                UUID getRequestedBy();
        }
}
