package com.danasea.backend.modules.order.infrastructure.adapters.zalopay;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class ZaloPaySignatureHelper {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private ZaloPaySignatureHelper() {}

    /**
     * Tạo MAC cho Create Order API v2.
     * data = app_id + "|" + app_trans_id + "|" + app_user + "|" + amount + "|" + app_time + "|" + embed_data + "|" + item
     */
    public static String createOrderMac(
            int appId,
            String appTransId,
            String appUser,
            long amount,
            long appTime,
            String embedData,
            String item,
            String key1) {

        String data = appId + "|" + appTransId + "|" + appUser + "|" + amount + "|" + appTime + "|" + embedData + "|" + item;
        return hmacSha256(key1, data);
    }

    /**
     * Tạo MAC cho Cancel Order API v2 theo đặc tả chính thức:
     * hmacinput = app_id + "|" + app_trans_id + "|" + key1
     */
    public static String cancelOrderMac(int appId, String appTransId, String key1) {
        String data = appId + "|" + appTransId + "|" + key1;
        return hmacSha256(key1, data);
    }

    /**
     * Tạo MAC cho Query Order API v2:
     * hmacinput = app_id + "|" + app_trans_id + "|" + key1
     */
    public static String queryOrderMac(int appId, String appTransId, String key1) {
        String data = appId + "|" + appTransId + "|" + key1;
        return hmacSha256(key1, data);
    }

    /**
     * Xác thực MAC Callback từ ZaloPay:
     * mac = HmacSHA256(key2, data)
     */
    public static boolean verifyCallbackMac(String data, String mac, String key2) {
        if (data == null || mac == null || key2 == null) {
            return false;
        }
        String expectedMac = hmacSha256(key2, data);
        return expectedMac.equalsIgnoreCase(mac);
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
            throw new IllegalStateException("Failed to calculate ZaloPay HMAC-SHA256", e);
        }
    }
}
