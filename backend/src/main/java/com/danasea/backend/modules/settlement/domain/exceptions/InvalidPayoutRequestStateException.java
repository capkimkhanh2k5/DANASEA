package com.danasea.backend.modules.settlement.domain.exceptions;

public class InvalidPayoutRequestStateException extends RuntimeException {
    public InvalidPayoutRequestStateException(String message) {
        super(message);
    }
}
