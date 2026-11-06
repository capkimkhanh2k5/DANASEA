package com.danasea.backend.modules.order.application.services;

import com.danasea.backend.shared.presentation.ApiConflictException;
import com.danasea.backend.modules.booking.application.dtos.CancelBookingHoldCommand;
import com.danasea.backend.modules.booking.application.usecases.CancelBookingHoldUseCase;
import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class VendorDepartureCancellation {
    private final VendorInternalApi vendors;
    private final JpaServiceRepository services;
    private final JpaServiceSlotRepository slots;
    private final JdbcTemplate jdbc;
    private final DisruptionDecisionService decisions;
    private final CancelBookingHoldUseCase cancelHold;

    public record Cancellation(UUID slotId, String reason, OffsetDateTime cancelledAt) {}
    private record AffectedOrder(UUID subOrderId, UUID slotId, UUID actor, String reason) {}

    @Transactional
    public Cancellation cancel(UUID actor, UUID serviceId, UUID slotId, String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new IllegalArgumentException("A cancellation reason of at most 500 characters is required.");
        }
        UUID vendorId = requireOwnership(actor, serviceId);
        var slot = slots.findByIdForUpdate(slotId)
                .filter(value -> serviceId.equals(value.getServiceId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Departure not found."));
        var existing = findCancellation(slotId);
        if (!existing.isEmpty()) {
            if (!existing.getFirst().reason().equals(reason.trim())) {
                throw new ApiConflictException("DEPARTURE_CANCELLATION_CONFLICT", "Departure was already cancelled with a different reason.");
            }
            return existing.getFirst();
        }
        if (!slot.getDate().atTime(slot.getStartTime()).isAfter(LocalDateTime.now(Booking.VIETNAM_ZONE))) {
            throw new ApiConflictException("DEPARTURE_CANCELLATION_CONFLICT", "Only upcoming departures can be cancelled.");
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM sub_orders WHERE slot_id = ?
                    AND (checked_in_at IS NOT NULL OR status IN ('CHECKED_IN', 'IN_PROGRESS', 'COMPLETED')))
                """, Boolean.class, slotId))) {
            throw new ApiConflictException("DEPARTURE_CANCELLATION_CONFLICT", "A passenger has checked in. Escalate to operations.");
        }
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
        slot.setStatus(SlotStatus.CANCELLED);
        slots.save(slot);
        jdbc.update("""
                INSERT INTO vendor_departure_cancellations (slot_id, vendor_id, requested_by, reason, cancelled_at)
                VALUES (?, ?, ?, ?, ?)
                """, slotId, vendorId, actor, reason.trim(), now);
        return new Cancellation(slotId, reason.trim(), now);
    }

    public record RefundReview(UUID subOrderId, UUID refundId, String status) {}
    public record CancellationStatus(Cancellation cancellation, List<RefundReview> existingRefundsToReview) {}

    @Transactional(readOnly = true)
    public CancellationStatus status(UUID actor, UUID serviceId, UUID slotId) {
        UUID vendorId = requireOwnership(actor, serviceId);
        slots.findById(slotId).filter(slot -> serviceId.equals(slot.getServiceId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Departure not found."));
        var cancellation = findCancellation(slotId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Departure has not been cancelled."));
        var review = jdbc.query("""
                SELECT s.id, r.id, r.status FROM sub_orders s JOIN refunds r ON r.sub_order_id = s.id
                WHERE s.slot_id = ? AND s.vendor_id = ? AND r.created_at <= ?
                  AND (r.status IN ('PENDING', 'FAILED')
                    OR (r.status = 'PROCESSED' AND r.amount < s.final_amount))
                ORDER BY s.id, r.created_at, r.id
                """, (rs, index) -> new RefundReview(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3)), slotId, vendorId, cancellation.cancelledAt());
        return new CancellationStatus(cancellation, review);
    }

    private List<Cancellation> findCancellation(UUID slotId) {
        return jdbc.query("""
                SELECT reason, cancelled_at FROM vendor_departure_cancellations WHERE slot_id = ?
                """, (rs, index) -> new Cancellation(slotId, rs.getString(1),
                        rs.getObject(2, OffsetDateTime.class)), slotId);
    }

    private UUID requireOwnership(UUID actor, UUID serviceId) {
        UUID vendorId = vendors.findByUserId(actor)
                .orElseThrow(() -> new AccessDeniedException("Vendor profile not found.")).getId();
        var service = services.findById(serviceId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found."));
        if (!vendorId.equals(service.getVendorId())) {
            throw new AccessDeniedException("Vendor does not own this service.");
        }
        return vendorId;
    }

    @Scheduled(fixedDelayString = "${app.scheduler.vendor-departure-cancellation-delay:10s}")
    public void process() {
        var affected = jdbc.query("""
                SELECT s.id, c.slot_id, c.requested_by, c.reason
                FROM vendor_departure_cancellations c JOIN sub_orders s ON s.slot_id = c.slot_id
                JOIN master_orders m ON m.id = s.master_order_id
                WHERE s.vendor_id = c.vendor_id AND s.status = 'CONFIRMED' AND s.checked_in_at IS NULL
                  AND m.payment_status = 'PAID' AND m.status IN ('PAID', 'PARTIALLY_COMPLETED')
                  AND NOT EXISTS (SELECT 1 FROM refunds r WHERE r.sub_order_id = s.id)
                  AND NOT EXISTS (SELECT 1 FROM sub_order_disruptions d WHERE d.sub_order_id = s.id
                      AND d.original_slot_id = c.slot_id AND d.status = 'AWAITING_DECISION')
                  AND NOT EXISTS (SELECT 1 FROM sub_order_disruptions d WHERE d.sub_order_id = s.id
                      AND d.source_key = 'vendor-departure:' || c.slot_id::text)
                ORDER BY c.cancelled_at, s.id LIMIT 100
                """, (rs, index) -> new AffectedOrder(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getString(4)));
        for (var order : affected) {
            try {
                decisions.recordVendorDeparture(order.subOrderId(), order.slotId(), order.reason(), order.actor());
            } catch (RuntimeException ex) {
                log.error("Could not resolve cancelled departure for sub-order {}", order.subOrderId(), ex);
            }
        }

        var holds = jdbc.query("""
                SELECT DISTINCT b.id FROM vendor_departure_cancellations c
                JOIN booking_items i ON i.slot_id = c.slot_id JOIN bookings b ON b.id = i.booking_id
                WHERE b.status = 'HOLD' ORDER BY b.id LIMIT 100
                """, (rs, index) -> rs.getObject(1, UUID.class));
        for (UUID hold : holds) {
            try {
                cancelHold.execute(new CancelBookingHoldCommand(hold, null));
            } catch (RuntimeException ex) {
                log.warn("Could not release hold {} for a cancelled departure", hold, ex);
            }
        }
    }
}
