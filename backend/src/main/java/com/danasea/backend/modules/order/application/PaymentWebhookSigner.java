package com.danasea.backend.modules.order.application;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.danasea.backend.configs.properties.PaymentProperties;

@Component
public class PaymentWebhookSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private final byte[] secret;

    public PaymentWebhookSigner(PaymentProperties properties) {
        this.secret = properties.webhookSecret().getBytes(StandardCharsets.UTF_8);
    }

    public String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Unable to initialize webhook signature verification.", ex);
        }
    }

    public boolean isValid(String payload, String suppliedSignature) {
        if (payload == null || suppliedSignature == null || suppliedSignature.isBlank()) {
            return false;
        }
        byte[] expected = sign(payload).getBytes(StandardCharsets.US_ASCII);
        byte[] supplied = suppliedSignature.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, supplied);
    }
}
