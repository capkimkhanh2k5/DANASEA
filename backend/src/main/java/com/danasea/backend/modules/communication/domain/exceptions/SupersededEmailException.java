package com.danasea.backend.modules.communication.domain.exceptions;

import lombok.Getter;

@Getter
public class SupersededEmailException extends RuntimeException {
    private final String reasonCode;

    public SupersededEmailException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }
}
