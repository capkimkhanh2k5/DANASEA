package com.danasea.backend.modules.booking.application.dtos;

import java.util.UUID;

public record BookingHoldItemDto(
        UUID slotId,
        Integer quantity,
        UUID optionId,
        Integer participantsCount,
        boolean allowSplit,
        UUID flashSaleId
) {
    public BookingHoldItemDto(UUID slotId,Integer quantity,UUID optionId,Integer participantsCount,boolean allowSplit) {
        this(slotId,quantity,optionId,participantsCount,allowSplit,null);
    }
    public BookingHoldItemDto(UUID slotId, Integer quantity, UUID optionId, Integer participantsCount) {
        this(slotId, quantity, optionId, participantsCount, false);
    }
    public BookingHoldItemDto(UUID slotId, Integer quantity) {
        this(slotId, quantity, null, null, false);
    }
}
