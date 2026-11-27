package com.danasea.backend.modules.booking.application.usecases;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.danasea.backend.modules.booking.application.dtos.BookingCancelResult;
import com.danasea.backend.modules.booking.application.dtos.CancelBookingCommand;
import com.danasea.backend.modules.booking.domain.exceptions.BookingNotFoundException;
import com.danasea.backend.modules.booking.domain.exceptions.InvalidBookingStateException;
import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.booking.domain.models.BookingStatus;
import com.danasea.backend.modules.booking.domain.models.CancellationFinancialResult;
import com.danasea.backend.modules.booking.domain.models.InventoryLockItem;
import com.danasea.backend.modules.booking.domain.ports.BookingRepositoryPort;
import com.danasea.backend.modules.booking.domain.ports.BookingCancellationFinancialPort;
import com.danasea.backend.modules.booking.domain.ports.InventoryLockPort;
import com.danasea.backend.modules.booking.domain.ports.ServiceSlotPort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.UUID;

public class CancelBookingUseCase {

    private final BookingRepositoryPort bookingRepository;
    private final ServiceSlotPort serviceSlotPort;
    private final InventoryLockPort inventoryLockPort;
    private final BookingCancellationFinancialPort financialPort;

    public CancelBookingUseCase(
            BookingRepositoryPort bookingRepository,
            ServiceSlotPort serviceSlotPort,
            InventoryLockPort inventoryLockPort,
            BookingCancellationFinancialPort financialPort) {
        this.bookingRepository = bookingRepository;
        this.serviceSlotPort = serviceSlotPort;
        this.inventoryLockPort = inventoryLockPort;
        this.financialPort = financialPort;
    }

    @Transactional
    public BookingCancelResult execute(CancelBookingCommand command) {
        if (command == null || command.bookingId() == null) {
            throw new IllegalArgumentException("Booking ID cannot be null");
        }

        Booking booking = bookingRepository.findByIdWithItemsForUpdate(command.bookingId())
                .orElseThrow(() -> new BookingNotFoundException(command.bookingId()));

        // Chống IDOR: Khách hàng chỉ được hủy booking của chính mình (ngoại trừ ADMIN)
        if (!command.isAdmin()) {
            booking.validateOwner(command.customerId());
        }

        // Kiểm tra tính hợp lệ của trạng thái hiện tại
        if (BookingStatus.CANCELLED.equals(booking.getStatus())) {
            throw new InvalidBookingStateException("Booking is already cancelled.");
        }

        if (!BookingStatus.HOLD.equals(booking.getStatus())
                && !BookingStatus.CONFIRMED.equals(booking.getStatus())
                && !BookingStatus.PENDING_PAYMENT.equals(booking.getStatus())) {
            throw new InvalidBookingStateException("Cannot cancel booking in status " + booking.getStatus());
        }

        String reason = (command.reason() != null && !command.reason().isBlank())
                ? command.reason().trim()
                : "Customer requested cancellation";

        OffsetDateTime now = OffsetDateTime.now();
        Optional<OffsetDateTime> earliestServiceTimeOpt = booking.findEarliestServiceStartTime();
        OffsetDateTime earliestServiceTime = earliestServiceTimeOpt.orElse(null);

        boolean refundEligible;
        int refundPercentage;
        BigDecimal refundAmount;
        String message;

        if (BookingStatus.CONFIRMED.equals(booking.getStatus())) {
            CancellationFinancialResult financialResult = financialPort.requestRefund(
                    booking.getId(),
                    command.customerId(),
                    command.idempotencyKey(),
                    now);
            refundEligible = financialResult.refundEligible();
            refundPercentage = financialResult.refundPercentage();
            refundAmount = financialResult.refundAmount();
            message = refundEligible
                    ? "Cancellation accepted. The refund request is pending provider processing."
                    : "Cancellation accepted. The cancellation policy does not provide a refund.";

            serviceSlotPort.releaseCapacityBatch(booking.getItems());
        } else {
            refundEligible = false;
            refundPercentage = 0;
            refundAmount = BigDecimal.ZERO;
            message = "Booking hold cancelled successfully.";

            if (BookingStatus.PENDING_PAYMENT.equals(booking.getStatus())) {
                financialPort.cancelUnpaidOrder(booking.getId());
            }

            if (booking.getItems() != null && !booking.getItems().isEmpty()) {
                List<InventoryLockItem> lockItems = booking.getItems().stream()
                        .map(item -> InventoryLockItem.of(item.getSlotId(), item.getQuantity(), 0))
                        .toList();
                releaseHoldsAfterCommit(booking.getId(), lockItems);
            }
        }

        booking.cancel(now);
        Booking saved = bookingRepository.save(booking);

        return new BookingCancelResult(
                saved.getId(),
                saved.getCustomerId(),
                saved.getStatus(),
                saved.getUpdatedAt(),
                earliestServiceTime,
                refundEligible,
                refundPercentage,
                refundAmount,
                reason,
                message
        );
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
}
