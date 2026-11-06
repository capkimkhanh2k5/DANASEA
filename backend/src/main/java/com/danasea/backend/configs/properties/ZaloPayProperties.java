package com.danasea.backend.configs.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "danasea.payment.zalopay")
public record ZaloPayProperties(
        Integer appId,
        String key1,
        String key2,
        String endpoint,
        String returnUrl,
        String cancelUrl) {
    public ZaloPayProperties {
        if (appId == null) {
            appId = 2554;
        }
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "https://sb-openapi.zalopay.vn/v2";
        }
        if (returnUrl == null || returnUrl.isBlank()) {
            returnUrl = "http://localhost:3000/checkout/result";
        }
        if (cancelUrl == null || cancelUrl.isBlank()) {
            cancelUrl = "http://localhost:3000/checkout/result";
        }
    }
}
