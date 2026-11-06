package com.danasea.backend.modules.communication.application.email;

import lombok.Builder;

@Builder
public record RenderedEmail(
        String to,
        String subject,
        String textBody,
        String htmlBody
) {
    public RenderedEmail {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Recipient email must not be null or blank");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Email subject must not be null or blank");
        }
    }
}
