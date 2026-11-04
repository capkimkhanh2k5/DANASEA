package com.danasea.backend.modules.settlement.application.dtos;

import com.danasea.backend.modules.settlement.domain.models.SettlementStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record SettlementResponse(
    UUID id,
    UUID vendorId,
    LocalDate periodStart,
    LocalDate periodEnd,
    BigDecimal grossAmount,
    BigDecimal commissionAmount,
    BigDecimal netPayableAmount,
    BigDecimal vendorFaultCompensationDeductedAmount,
    SettlementStatus status,
    OffsetDateTime generatedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
