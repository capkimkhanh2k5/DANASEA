package com.danasea.backend.modules.partner.domain.exceptions;

public class ApplicationStatusConflictException extends RuntimeException {
    public ApplicationStatusConflictException(String message) {
        super(message);
    }
}
