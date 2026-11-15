package com.danasea.backend.modules.operation.presentation.dtos;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ReviewResponse(
        UUID id,
        UUID subOrderId,
        UUID customerId,
        String customerName,
        String customerAvatar,
        UUID vendorId,
        UUID serviceId,
        String serviceName,
        Short rating,
        String comment,
        List<String> images,
        String vendorReply,
        OffsetDateTime vendorRepliedAt,
        Boolean isFlagged,
        Boolean isVisible,
        String flagReason,
        String moderationNote,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
