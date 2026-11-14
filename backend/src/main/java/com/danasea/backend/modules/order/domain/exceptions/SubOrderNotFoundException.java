package com.danasea.backend.modules.order.domain.exceptions;

import java.util.UUID;

public class SubOrderNotFoundException extends RuntimeException {

    public SubOrderNotFoundException(UUID subOrderId) {
        super("Sub-order not found with id: " + subOrderId);
    }

    public SubOrderNotFoundException(String message) {
        super(message);
    }
}
