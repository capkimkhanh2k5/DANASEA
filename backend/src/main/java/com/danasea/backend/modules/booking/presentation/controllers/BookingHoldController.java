package com.danasea.backend.modules.booking.presentation.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.danasea.backend.modules.booking.application.dtos.BookingHoldItemDto;
import com.danasea.backend.modules.booking.application.dtos.BookingHoldResult;
import com.danasea.backend.modules.booking.application.dtos.CancelBookingHoldCommand;
import com.danasea.backend.modules.booking.application.dtos.ConfirmBookingCommand;
import com.danasea.backend.modules.booking.application.dtos.CreateBookingHoldCommand;
import com.danasea.backend.modules.booking.application.usecases.CancelBookingHoldUseCase;
import com.danasea.backend.modules.booking.application.usecases.ConfirmBookingUseCase;
import com.danasea.backend.modules.booking.application.usecases.CreateBookingHoldUseCase;
import com.danasea.backend.modules.booking.presentation.dtos.BookingHoldItemResponse;
import com.danasea.backend.modules.booking.presentation.dtos.BookingHoldRequest;
import com.danasea.backend.modules.booking.presentation.dtos.BookingHoldResponse;
import com.danasea.backend.security.infrastructure.SecurityUtils;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingHoldController {

    private final CreateBookingHoldUseCase createBookingHoldUseCase;
    private final ConfirmBookingUseCase confirmBookingUseCase;
    private final CancelBookingHoldUseCase cancelBookingHoldUseCase;

    public ResponseEntity<BookingHoldResponse> holdBooking(BookingHoldRequest request) { return holdBooking(request,null); }

    @PostMapping("/hold")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<BookingHoldResponse> holdBooking(
            @Valid @RequestBody BookingHoldRequest request,
            @RequestHeader(value="Idempotency-Key",required=false) String idempotencyKey) {

        UUID customerId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));

        List<BookingHoldItemDto> itemDtos = request.items().stream()
                .map(item -> new BookingHoldItemDto(item.slotId(), item.quantity(), item.optionId(), item.participantsCount(), item.allowSplit(),item.flashSaleId()))
                .toList();

        CreateBookingHoldCommand command = new CreateBookingHoldCommand(customerId, itemDtos,idempotencyKey);
        BookingHoldResult result = createBookingHoldUseCase.execute(command);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
    }

    @PostMapping("/{holdId}/confirm")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<BookingHoldResponse> confirmBooking(@PathVariable("holdId") UUID holdId) {
        UUID customerId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));

        ConfirmBookingCommand command = new ConfirmBookingCommand(holdId, customerId);
        BookingHoldResult result = confirmBookingUseCase.execute(command);

        return ResponseEntity.ok(toResponse(result));
    }

    @DeleteMapping("/hold/{holdId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<Void> cancelBookingHold(@PathVariable("holdId") UUID holdId) {
        UUID customerId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));

        CancelBookingHoldCommand command = new CancelBookingHoldCommand(holdId, customerId);
        cancelBookingHoldUseCase.execute(command);

        return ResponseEntity.noContent().build();
    }

    private BookingHoldResponse toResponse(BookingHoldResult result) {
        List<BookingHoldItemResponse> itemResponses = result.items().stream()
                .map(i -> new BookingHoldItemResponse(
                        i.id(),
                        i.serviceId(),
                        i.vendorId(),
                        i.slotId(),
                        i.quantity(),
                        i.bookingDate(),
                        i.bookingTime(),
                        i.price(),
                        i.subtotal()
                ))
                .toList();

        return new BookingHoldResponse(
                result.bookingId(),
                result.customerId(),
                result.status(),
                result.totalAmount(),
                result.holdExpiresAt(),
                itemResponses,
                result.createdAt()
        );
    }
}
