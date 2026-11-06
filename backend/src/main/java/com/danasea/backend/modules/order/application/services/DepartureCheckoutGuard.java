package com.danasea.backend.modules.order.application.services;

import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingItemJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.order.domain.exceptions.InvalidOrderStateException;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DepartureCheckoutGuard {
    private final JpaBookingRepository bookings;
    private final JpaServiceSlotRepository slots;

    public BookingJpaEntity lockBooking(UUID bookingId) {
        return bookings.findByIdWithItemsForUpdate(bookingId)
                .orElseThrow(() -> new InvalidOrderStateException("Booking is missing from the checkout."));
    }

    public Set<UUID> affectedItems(BookingJpaEntity booking) {
        var cancelled = new HashSet<UUID>();
        booking.getItems().stream().map(BookingItemJpaEntity::getSlotId).distinct().sorted()
                .forEach(slotId -> {
                    var slot = slots.findByIdForUpdate(slotId)
                            .orElseThrow(() -> new InvalidOrderStateException("A departure is missing from the checkout."));
                    if (slot.getStatus() == SlotStatus.CANCELLED) {
                        cancelled.add(slotId);
                    }
                });
        var affected = new HashSet<UUID>();
        for (var item : booking.getItems()) {
            if (cancelled.contains(item.getSlotId())
                    || (!item.getAllocations().isEmpty() && slots.hasBlockedEquipmentForBookingItem(item.getId()))) {
                affected.add(item.getId());
            }
        }
        return affected;
    }

    public void requireBookable(BookingJpaEntity booking) {
        if (!affectedItems(booking).isEmpty()) {
            throw new InvalidOrderStateException("A departure or assigned equipment is unavailable. Start a new checkout.");
        }
    }
}
