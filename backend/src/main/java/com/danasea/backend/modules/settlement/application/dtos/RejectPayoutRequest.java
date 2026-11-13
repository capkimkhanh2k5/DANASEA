package com.danasea.backend.modules.settlement.application.dtos;

import jakarta.validation.constraints.NotBlank;

public record RejectPayoutRequest(
    @NotBlank(message = "{validation.payout.rejection_reason.required}")
    String rejectionReason,

    String adminNote
) {}
