package com.danasea.backend.modules.booking.application.dtos;

import java.util.List;
import java.util.UUID;

public record CreateBookingHoldCommand(
        UUID customerId,
        List<BookingHoldItemDto> items,
        String idempotencyKey
) {
    public CreateBookingHoldCommand(UUID customerId,List<BookingHoldItemDto> items) { this(customerId,items,null); }
}
