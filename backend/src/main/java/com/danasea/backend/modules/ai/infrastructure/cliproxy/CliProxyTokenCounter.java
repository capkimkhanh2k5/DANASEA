package com.danasea.backend.modules.ai.infrastructure.cliproxy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import org.springframework.web.client.RestClient;

import com.danasea.backend.shared.infrastructure.http.HttpRequestDeadline;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
final class CliProxyTokenCounter {
    record Count(long tokens, String method) { }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RestClient client;
    private final String apiKey;

    CliProxyTokenCounter(RestClient client, String apiKey) {
        this.client = client;
        this.apiKey = apiKey;
    }

    Count count(CliProxyDto.ChatRequest request, long limit) {
        String serialized = serialize(request);
        long bytes = serialized.getBytes(StandardCharsets.UTF_8).length;
        if (bytes <= limit || !request.model().toLowerCase(Locale.ROOT).startsWith("gemini-")) {
            return new Count(bytes, "UTF8_BYTE_BOUND");
        }

        Map<String, Object> body = Map.of("contents", List.of(Map.of("role", "user", "parts",
                List.of(Map.of("text", serialized)))));
        try {
            String response = HttpRequestDeadline.execute(Duration.ofSeconds(3), () -> client.post()
                    .uri(builder -> {
                        String prefix = builder.build().getPath().replaceFirst("/v1/?$", "");
                        return builder.replacePath(prefix).pathSegment("v1beta", "models", request.model() + ":countTokens").build();
                    })
                    .header("Authorization", "Bearer " + apiKey)
                    .body(body).retrieve().body(String.class));
            JsonNode tree = response == null ? null : MAPPER.readTree(response);
            JsonNode total = tree == null ? null : tree.get("totalTokens");
            if (total != null && total.isIntegralNumber() && total.canConvertToLong() && total.longValue() > 0) {
                return new Count(total.longValue(), "PROXY_SERIALIZED_TEXT_ESTIMATE");
            }
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Proxy token count unavailable for model {} ({})", request.model(), exception.getClass().getSimpleName());
        }
        return new Count(bytes, "UTF8_BYTE_BOUND_FALLBACK");
    }

    Count estimateWithCalibration(CliProxyDto.ChatRequest request, long limit, long refTokens, long refBytes, boolean remoteFailed) {
        long bytes = utf8Bytes(request);
        if (bytes <= limit || !request.model().toLowerCase(Locale.ROOT).startsWith("gemini-")) {
            return new Count(bytes, "UTF8_BYTE_BOUND");
        }
        if (remoteFailed) {
            return new Count(bytes, "UTF8_BYTE_BOUND_FALLBACK");
        }
        if (refTokens > 0 && refBytes > 0) {
            long estimated = Math.max(1L, (bytes * refTokens + refBytes - 1L) / refBytes);
            return new Count(estimated, "CALIBRATED_RATIO_ESTIMATE");
        }
        return count(request, limit);
    }

    long utf8Bytes(CliProxyDto.ChatRequest request) {
        return serialize(request).getBytes(StandardCharsets.UTF_8).length;
    }

    private String serialize(CliProxyDto.ChatRequest request) {
        try {
            return MAPPER.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new CliProxyException("Cannot serialize model input for preflight", exception);
        }
    }
}
