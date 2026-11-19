package com.danasea.backend.modules.order.application.dtos;

import java.util.UUID;

public record GetCancellationPreviewQuery(
        UUID currentUserId,
        UUID orderOrSubOrderId,
        boolean isAdmin,
        UUID vendorId
) {
    public GetCancellationPreviewQuery(UUID currentUserId, UUID orderOrSubOrderId, boolean isAdmin) {
        this(currentUserId, orderOrSubOrderId, isAdmin, null);
    }
}
