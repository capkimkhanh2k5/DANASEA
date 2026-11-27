package com.danasea.backend.security.authentication.domain.events;

import java.util.UUID;

public record OtpEmailRequestedEvent(
    UUID userId,
    String email,
    String otpCode,
    String locale
) {
    public OtpEmailRequestedEvent(UUID userId, String email, String otpCode) {
        this(userId, email, otpCode, "vi");
    }
}
