package com.danasea.backend.modules.order.infrastructure.adapters;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

final class PaymentUrlHelper {

    private PaymentUrlHelper() {
    }

    static String appendQueryParams(String baseUrl, Map<String, String> params) {
        URI uri = URI.create(baseUrl);
        if (uri.getHost() == null || uri.getRawFragment() != null || uri.getUserInfo() != null
                || (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("Payment redirect must be an absolute HTTP or HTTPS URL without a fragment.");
        }
        if (params.isEmpty()) {
            return baseUrl;
        }
        String query = params.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        if (uri.getRawQuery() == null) {
            return baseUrl + "?" + query;
        }
        return baseUrl + (uri.getRawQuery().isEmpty() ? "" : "&") + query;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
