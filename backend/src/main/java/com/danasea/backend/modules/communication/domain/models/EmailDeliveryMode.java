package com.danasea.backend.modules.communication.domain.models;

public enum EmailDeliveryMode {
    OFF,
    CAPTURE,
    LIVE;

    public static EmailDeliveryMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return OFF;
        }
        try {
            return EmailDeliveryMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OFF;
        }
    }
}
