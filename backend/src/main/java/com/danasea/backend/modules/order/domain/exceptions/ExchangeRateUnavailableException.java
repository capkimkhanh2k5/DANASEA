package com.danasea.backend.modules.order.domain.exceptions;

public class ExchangeRateUnavailableException extends InvalidOrderStateException {

    public ExchangeRateUnavailableException(String message) {
        super(message);
    }

    public ExchangeRateUnavailableException(String message, Throwable cause) {
        super(message);
        if (cause != null) {
            initCause(cause);
        }
    }
}
