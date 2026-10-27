package com.danasea.backend.modules.ai.infrastructure.cliproxy.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.AssertTrue;

@Validated
@ConfigurationProperties(prefix = "ai.cliproxy")
public record CliProxyProperties(
        boolean enabled,
        @NotBlank String baseUrl,
        @NotBlank String apiKey,
        @NotBlank String model,
        @NotBlank String fallbackModel,
        @DefaultValue("65536") @Min(1) @Max(65536) int maxOutputTokens,
        @DefaultValue("1048576") @Min(1) @Max(1048576) int maxInputTokens,
        @DefaultValue("131072") @Min(1) @Max(131072) int fallbackContextTokens,
        @DefaultValue("8192") @Min(1) @Max(1048575) int inputSafetyTokens
) {
    @ConstructorBinding
    public CliProxyProperties { }

    public CliProxyProperties(boolean enabled, String baseUrl, String apiKey, String model, String fallbackModel) {
        this(enabled, baseUrl, apiKey, model, fallbackModel, 65536);
    }

    public CliProxyProperties(boolean enabled, String baseUrl, String apiKey, String model, String fallbackModel,
            int maxOutputTokens) {
        this(enabled, baseUrl, apiKey, model, fallbackModel, maxOutputTokens, 1048576, 131072, 8192);
    }

    @AssertTrue(message = "Input safety reserve and output ceiling must fit the configured context limits")
    public boolean isInputBudgetValid() {
        return inputSafetyTokens < maxInputTokens
                && (long) maxOutputTokens + inputSafetyTokens < fallbackContextTokens;
    }
    public String sanitizedBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        String url = baseUrl.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.endsWith("/v1")) {
            url += "/v1";
        }
        return url;
    }
}
