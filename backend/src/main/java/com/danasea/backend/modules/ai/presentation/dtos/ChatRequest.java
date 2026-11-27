package com.danasea.backend.modules.ai.presentation.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ChatRequest(
        UUID conversationId,
        @NotBlank @Size(max = 1800) String message,
        @Size(max = 50) String contextType,
        @Size(max = 64) String contextId
) {
    public ChatRequest(UUID conversationId, String message) {
        this(conversationId, message, null, null);
    }
}
