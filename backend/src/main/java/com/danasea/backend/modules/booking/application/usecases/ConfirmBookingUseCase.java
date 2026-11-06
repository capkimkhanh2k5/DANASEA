package com.danasea.backend.modules.booking.application.usecases;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.danasea.backend.modules.booking.application.dtos.BookingHoldItemResult;
import com.danasea.backend.modules.booking.application.dtos.BookingHoldResult;
import com.danasea.backend.modules.booking.application.dtos.ConfirmBookingCommand;
import com.danasea.backend.modules.booking.domain.exceptions.BookingNotFoundException;
import com.danasea.backend.modules.booking.domain.exceptions.InvalidBookingStateException;
import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.booking.domain.models.InventoryLockItem;
import com.danasea.backend.modules.booking.domain.ports.BookingRepositoryPort;
import com.danasea.backend.modules.booking.domain.ports.BookingPaymentStatusPort;
import com.danasea.backend.modules.booking.domain.ports.InventoryLockPort;
import com.danasea.backend.modules.booking.domain.ports.ServiceSlotPort;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.UUID;

public class ConfirmBookingUseCase {

    private final BookingRepositoryPort bookingRepository;
    private final InventoryLockPort inventoryLockPort;
    private final ServiceSlotPort serviceSlotPort;
    private final BookingPaymentStatusPort bookingPaymentStatusPort;

    public ConfirmBookingUseCase(
            BookingRepositoryPort bookingRepository,
            InventoryLockPort inventoryLockPort,
            ServiceSlotPort serviceSlotPort,
            BookingPaymentStatusPort bookingPaymentStatusPort) {
        this.bookingRepository = bookingRepository;
        this.inventoryLockPort = inventoryLockPort;
        this.serviceSlotPort = serviceSlotPort;
        this.bookingPaymentStatusPort = bookingPaymentStatusPort;
    }

    public ConfirmBookingUseCase(
            BookingRepositoryPort bookingRepository,
            InventoryLockPort inventoryLockPort,
            ServiceSlotPort serviceSlotPort) {
        this(bookingRepository, inventoryLockPort, serviceSlotPort, bookingId -> false);
    }

    @Transactional
    public BookingHoldResult execute(ConfirmBookingCommand command) {
        return executeExcluding(command, Set.of());
    }

    @Transactional
    public BookingHoldResult executeExcluding(ConfirmBookingCommand command, Set<UUID> unavailableItems) {
        if (command == null || command.bookingId() == null) {
            throw new IllegalArgumentException("Booking ID cannot be null");
        }
        if (command.customerId() == null) {
            throw new IllegalArgumentException("Customer ID cannot be null");
        }

        Booking booking = bookingRepository.findByIdWithItemsForUpdate(command.bookingId())
                .orElseThrow(() -> new BookingNotFoundException(command.bookingId()));

        booking.validateOwner(command.customerId());

        if (!bookingPaymentStatusPort.hasSuccessfulPayment(booking.getId())) {
            throw new InvalidBookingStateException(
                    "Booking confirmation requires a successful payment webhook.");
        }

        if (booking.isConfirmed()) {
            return mapToResult(booking);
        }

        OffsetDateTime now = OffsetDateTime.now();
        booking.confirm(now);

        // Deduct/commit capacity into PostgreSQL service_slots
        serviceSlotPort.commitCapacityBatch(booking.getItems().stream()
                .filter(item -> !unavailableItems.contains(item.getId())).toList());

        List<InventoryLockItem> lockItems = booking.getItems().stream()
                .map(item -> InventoryLockItem.of(item.getSlotId(), item.getQuantity(), 0))
                .toList();

        Booking savedBooking = bookingRepository.save(booking);
        releaseHoldsAfterCommit(booking.getId(), lockItems);
        return mapToResult(savedBooking);
    }

    @Transactional
    public void voidUnfulfilledPayment(UUID bookingId) {
        Booking booking = bookingRepository.findByIdWithItemsForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (booking.isConfirmed()) {
            throw new InvalidBookingStateException("A confirmed booking cannot be voided as an unfulfilled payment.");
        }
        if (!booking.isCancelled()) {
            booking.cancel(OffsetDateTime.now());
            bookingRepository.save(booking);
        }
        var locks = booking.getItems().stream()
                .map(item -> InventoryLockItem.of(item.getSlotId(), item.getQuantity(), 0)).toList();
        releaseHoldsAfterCommit(bookingId, locks);
    }

    private void releaseHoldsAfterCommit(UUID bookingId, List<InventoryLockItem> lockItems) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            inventoryLockPort.releaseHolds(bookingId, lockItems);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                inventoryLockPort.releaseHolds(bookingId, lockItems);
            }
        });
    }

    private BookingHoldResult mapToResult(Booking booking) {
        List<BookingHoldItemResult> itemResults = booking.getItems().stream()
                .map(item -> new BookingHoldItemResult(
                        item.getId(),
                        item.getServiceId(),
                        item.getVendorId(),
                        item.getSlotId(),
                        item.getQuantity(),
                        item.getBookingDate(),
                        item.getBookingTime(),
                        item.getPrice(),
                        item.calculateSubtotal()
                ))
                .toList();

        return new BookingHoldResult(
                booking.getId(),
                booking.getCustomerId(),
                booking.getStatus(),
                booking.getTotalAmount(),
                booking.getHoldExpiresAt(),
                itemResults,
                booking.getCreatedAt()
        );
    }
}
