package com.danasea.backend.modules.settlement.application.dtos;

import com.danasea.backend.modules.operation.domain.models.PayoutRequestStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record PayoutRequestResponse(
    UUID id,
    UUID vendorId,
    UUID settlementId,
    BigDecimal amount,
    PayoutRequestStatus status,
    UUID processedBy,
    OffsetDateTime processedAt,
    String rejectionReason,
    String bankReference,
    String adminNote,
    String note,
    String vendorNote,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
