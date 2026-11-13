package com.danasea.backend.modules.settlement.application.dtos;

import com.danasea.backend.modules.settlement.domain.models.SettlementStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Builder
public record EligibleSettlementPayoutResponse(
    UUID settlementId,
    LocalDate periodStart,
    LocalDate periodEnd,
    BigDecimal grossAmount,
    BigDecimal commissionAmount,
    BigDecimal netPayableAmount,
    BigDecimal alreadyRequestedAmount,
    BigDecimal alreadyPaidAmount,
    BigDecimal remainingEligibleBalance,
    SettlementStatus status
) {}
