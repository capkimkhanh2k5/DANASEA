package com.danasea.backend.modules.settlement.domain.exceptions;

public class PayoutRequestNotFoundException extends RuntimeException {
    public PayoutRequestNotFoundException(String message) {
        super(message);
    }
}
