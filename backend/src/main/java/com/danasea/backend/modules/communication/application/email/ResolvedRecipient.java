package com.danasea.backend.modules.communication.application.email;

public record ResolvedRecipient(
        String email,
        String locale,
        boolean isEmailVerified,
        boolean isLocked
) {
    public ResolvedRecipient {
        if (locale == null || locale.isBlank()) {
            locale = "vi";
        }
    }
}
