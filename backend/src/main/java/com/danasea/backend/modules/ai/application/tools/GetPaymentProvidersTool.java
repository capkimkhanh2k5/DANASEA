package com.danasea.backend.modules.ai.application.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.danasea.backend.modules.order.application.services.PaymentProviderAvailability;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class GetPaymentProvidersTool implements ToolExecutor {

    private final PaymentProviderAvailability availability;
    private final ObjectMapper objectMapper;

    @Autowired
    public GetPaymentProvidersTool(
            @Autowired(required = false) PaymentProviderAvailability availability,
            ObjectMapper objectMapper
    ) {
        this.availability = availability;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public GetPaymentProvidersTool(PaymentProviderAvailability availability) {
        this(availability, new ObjectMapper());
    }

    @Override
    public String getName() {
        return "get_payment_providers";
    }

    @Override
    public String execute(String argumentsJson) {
        return execute(argumentsJson, null);
    }

    @Override
    public String execute(String argumentsJson, ToolExecutionContext context) {
        if (availability == null) {
            return error("FEATURE_UNAVAILABLE", "Payment provider availability service is unavailable");
        }

        List<String> enabledProviders = new ArrayList<>();
        List<String> disabledProviders = new ArrayList<>();
        List<Map<String, Object>> providers = new ArrayList<>();

        for (PaymentProvider provider : PaymentProvider.values()) {
            boolean enabled = availability.isEnabled(provider);
            if (enabled) {
                enabledProviders.add(provider.name());
            } else {
                disabledProviders.add(provider.name());
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("provider", provider.name());
            entry.put("enabled", enabled);
            providers.add(entry);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "AVAILABLE");
        result.put("source", "PaymentProviderAvailability");
        result.put("endpoint", "/api/payments/providers");
        result.put("enabledProviders", enabledProviders);
        result.put("disabledProviders", disabledProviders);
        result.put("providers", providers);

        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("Failed to serialize payment providers response", e);
            return "{\"error\":\"TOOL_EXECUTION_FAILED\",\"message\":\"Failed to serialize payment providers\"}";
        }
    }

    private String error(String code, String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("error", code, "message", message));
        } catch (Exception e) {
            return "{\"error\":\"FEATURE_UNAVAILABLE\"}";
        }
    }
}
