package com.danasea.backend.configs.properties;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Cấu hình nguồn API tỷ giá tự động và các quy tắc kiểm soát biến động cho PayPal.
 */
@Validated
@ConfigurationProperties(prefix = "app.payment.paypal.exchange-rate")
public record ExchangeRateProperties(
        @NotBlank String apiUrl,
        @NotBlank String cron,
        @NotBlank String zone,
        @NotNull Integer maxAgeDays,
        @NotNull BigDecimal volatilityThreshold,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {
    public ExchangeRateProperties {
        if (apiUrl == null || apiUrl.isBlank()) {
            apiUrl = "https://api.frankfurter.dev/v2/rate/usd/vnd";
        }
        if (cron == null || cron.isBlank()) {
            cron = "0 0 20 * * *";
        }
        if (zone == null || zone.isBlank()) {
            zone = "Asia/Ho_Chi_Minh";
        }
        if (maxAgeDays == null || maxAgeDays <= 0) {
            maxAgeDays = 3;
        }
        if (volatilityThreshold == null || volatilityThreshold.compareTo(BigDecimal.ZERO) <= 0) {
            volatilityThreshold = new BigDecimal("0.03");
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
    }
}
