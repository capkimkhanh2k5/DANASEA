package com.danasea.backend.modules.booking.presentation.dtos;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BookingHoldItemRequest(
        @NotNull(message = "{validation.booking.slot.required}")
        UUID slotId,

        @NotNull(message = "{validation.booking.quantity.required}")
        @Min(value = 1, message = "{validation.booking.quantity.min}")
        Integer quantity,

        UUID optionId,

        @Min(value = 1, message = "{validation.booking.participants.min}")
        Integer participantsCount,

        Boolean allowSplit,
        UUID flashSaleId
) {
    public BookingHoldItemRequest(UUID slotId,Integer quantity,UUID optionId,Integer participantsCount,Boolean allowSplit) {
        this(slotId,quantity,optionId,participantsCount,allowSplit,null);
    }
    public BookingHoldItemRequest {
        allowSplit = Boolean.TRUE.equals(allowSplit);
    }
    public BookingHoldItemRequest(UUID slotId, Integer quantity, UUID optionId, Integer participantsCount) {
        this(slotId, quantity, optionId, participantsCount, false);
    }
    public BookingHoldItemRequest(UUID slotId, Integer quantity) {
        this(slotId, quantity, null, null, false);
    }
}
