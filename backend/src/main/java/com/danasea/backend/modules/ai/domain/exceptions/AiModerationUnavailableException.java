package com.danasea.backend.modules.ai.domain.exceptions;

public class AiModerationUnavailableException extends RuntimeException {
    public AiModerationUnavailableException() {
        super("AI moderation is unavailable");
    }
}
