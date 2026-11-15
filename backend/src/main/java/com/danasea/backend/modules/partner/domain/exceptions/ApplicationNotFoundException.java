package com.danasea.backend.modules.partner.domain.exceptions;

public class ApplicationNotFoundException extends RuntimeException {
    public ApplicationNotFoundException(String message) {
        super(message);
    }

    public ApplicationNotFoundException() {
        super("error.application_not_found");
    }
}
