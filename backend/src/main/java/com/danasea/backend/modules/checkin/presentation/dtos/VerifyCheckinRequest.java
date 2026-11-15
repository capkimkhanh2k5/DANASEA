package com.danasea.backend.modules.checkin.presentation.dtos;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record VerifyCheckinRequest(
        @NotBlank(message = "{validation.checkin.qr.required}") @JsonAlias({ "token", "qrCode" }) String qrToken,
        Boolean allowLateOverride,
        String overrideReason,
        UUID selectedSubOrderId) {

    public VerifyCheckinRequest(String qrToken) {
        this(qrToken, false, null, null);
    }

    public VerifyCheckinRequest(String qrToken, Boolean allowLateOverride, String overrideReason) {
        this(qrToken, allowLateOverride, overrideReason, null);
    }

    public VerifyCheckinRequest(String qrToken, UUID selectedSubOrderId) {
        this(qrToken, false, null, selectedSubOrderId);
    }

    public boolean isAllowLateOverride() {
        return Boolean.TRUE.equals(allowLateOverride);
    }
}
