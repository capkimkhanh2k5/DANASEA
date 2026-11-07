package com.danasea.backend.modules.order.infrastructure.adapters.payos;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class PayOSSignatureHelper {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private PayOSSignatureHelper() {}

    /**
     * Tạo signature cho Create Payment Link request theo chuẩn payOS.
     * Sắp xếp alphabet các trường: amount, cancelUrl, description, orderCode, returnUrl.
     */
    public static String createRequestSignature(
            long amount,
            String cancelUrl,
            String description,
            long orderCode,
            String returnUrl,
            String checksumKey) {

        String data = "amount=" + amount
                + "&cancelUrl=" + (cancelUrl != null ? cancelUrl : "")
                + "&description=" + (description != null ? description : "")
                + "&orderCode=" + orderCode
                + "&returnUrl=" + (returnUrl != null ? returnUrl : "");

        return hmacSha256(checksumKey, data);
    }

    /**
     * Xác thực signature webhook payOS bằng cách sắp xếp alphabet các key trong data object.
     */
    public static boolean verifyWebhookSignature(Map<String, Object> dataMap, String signature, String checksumKey) {
        if (dataMap == null || signature == null || checksumKey == null) {
            return false;
        }

        Map<String, Object> sortedMap = new TreeMap<>(dataMap);
        String data = sortedMap.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));

        String expectedSignature = hmacSha256(checksumKey, data);
        return expectedSignature.equalsIgnoreCase(signature);
    }

    public static String hmacSha256(String key, String data) {
        if (key == null || data == null) {
            throw new IllegalArgumentException("Key and data cannot be null");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256", e);
        }
    }
}
