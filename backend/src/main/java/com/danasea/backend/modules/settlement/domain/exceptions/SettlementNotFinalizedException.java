package com.danasea.backend.modules.settlement.domain.exceptions;

public class SettlementNotFinalizedException extends RuntimeException {
    public SettlementNotFinalizedException(String message) {
        super(message);
    }
}
