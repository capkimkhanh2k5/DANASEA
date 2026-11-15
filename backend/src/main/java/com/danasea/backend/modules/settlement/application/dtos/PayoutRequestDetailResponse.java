package com.danasea.backend.modules.settlement.application.dtos;

import com.danasea.backend.modules.operation.domain.models.PayoutRequestStatus;
import com.danasea.backend.modules.settlement.domain.models.SettlementStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record PayoutRequestDetailResponse(
    UUID id,
    UUID vendorId,
    String vendorName,
    String bankName,
    String bankAccountNumber,
    String bankAccountHolder,
    UUID settlementId,
    LocalDate periodStart,
    LocalDate periodEnd,
    BigDecimal settlementNetPayableAmount,
    SettlementStatus settlementStatus,
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
