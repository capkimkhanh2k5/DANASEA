package com.danasea.backend.configs.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "danasea.payment.payos")
public record PayOSProperties(
        String clientId,
        String apiKey,
        String checksumKey,
        String baseUrl,
        String returnUrl,
        String cancelUrl,
        boolean enabled
) {
    public PayOSProperties(String clientId, String apiKey, String checksumKey,
            String baseUrl, String returnUrl, String cancelUrl) {
        this(clientId, apiKey, checksumKey, baseUrl, returnUrl, cancelUrl, false);
    }

    @ConstructorBinding
    public PayOSProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api-merchant.payos.vn";
        }
        if (returnUrl == null || returnUrl.isBlank()) {
            returnUrl = "http://localhost:3000/payment/success";
        }
        if (cancelUrl == null || cancelUrl.isBlank()) {
            cancelUrl = "http://localhost:3000/payment/cancel";
        }
    }
}
