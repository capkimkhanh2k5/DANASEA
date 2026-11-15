package com.danasea.backend.modules.settlement.domain.exceptions;

public class PayoutAmountExceedsBalanceException extends RuntimeException {
    public PayoutAmountExceedsBalanceException(String message) {
        super(message);
    }
}
