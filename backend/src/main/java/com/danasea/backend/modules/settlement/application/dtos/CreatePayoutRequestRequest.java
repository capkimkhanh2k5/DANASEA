package com.danasea.backend.modules.settlement.application.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CreatePayoutRequestRequest(
    @NotNull(message = "{validation.payout.settlement_id.required}")
    UUID settlementId,

    @NotNull(message = "{validation.payout.amount.required}")
    @DecimalMin(value = "1000.00", message = "{validation.payout.amount.min}")
    BigDecimal amount,

    String note
) {}
