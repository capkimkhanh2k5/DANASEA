package com.danasea.backend.configs.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "danasea.payment.zalopay")
public record ZaloPayProperties(
        Integer appId,
        String key1,
        String key2,
        String endpoint,
        String returnUrl,
        String cancelUrl,
        String callbackUrl,
        boolean enabled
) {
    public ZaloPayProperties(Integer appId, String key1, String key2,
            String endpoint, String returnUrl, String cancelUrl) {
        this(appId, key1, key2, endpoint, returnUrl, cancelUrl, null, false);
    }

    @ConstructorBinding
    public ZaloPayProperties {
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "https://openapi.zalopay.vn/v2";
        }
        if (returnUrl == null || returnUrl.isBlank()) {
            returnUrl = "http://localhost:3000/payment/success";
        }
        if (cancelUrl == null || cancelUrl.isBlank()) {
            cancelUrl = "http://localhost:3000/payment/cancel";
        }
    }
}
