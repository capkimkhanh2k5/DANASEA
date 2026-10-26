package com.danasea.backend.modules.booking.application.usecases;

import com.danasea.backend.modules.booking.domain.events.BookingHoldExpiredEvent;
import com.danasea.backend.modules.booking.domain.models.InventoryLockItem;
import com.danasea.backend.modules.booking.domain.ports.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

@Service
public class ExpireBookingHoldUseCase {
    private final BookingRepositoryPort bookings;
    private final InventoryLockPort inventory;
    private final ApplicationEventPublisher events;
    public ExpireBookingHoldUseCase(BookingRepositoryPort bookings,InventoryLockPort inventory,ApplicationEventPublisher events) {
        this.bookings=bookings; this.inventory=inventory; this.events=events;
    }
    @Transactional
    public void execute(UUID id,OffsetDateTime now) {
        var booking=bookings.findByIdWithItemsForUpdate(id).orElse(null);
        if(booking==null || (!booking.isHold() && !booking.isPendingPayment()) || !booking.isExpired(now)) return;
        booking.cancel(now);
        bookings.save(booking);
        var locks=booking.getItems().stream().map(i->InventoryLockItem.of(i.getSlotId(),i.getQuantity(),0)).toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { inventory.releaseHolds(id,locks); }
        });
        events.publishEvent(new BookingHoldExpiredEvent(id,now));
    }
}
