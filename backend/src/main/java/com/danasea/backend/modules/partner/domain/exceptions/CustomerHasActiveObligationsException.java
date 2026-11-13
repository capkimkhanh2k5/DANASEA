package com.danasea.backend.modules.partner.domain.exceptions;

public class CustomerHasActiveObligationsException extends RuntimeException {
    public CustomerHasActiveObligationsException(String message) {
        super(message);
    }
}
