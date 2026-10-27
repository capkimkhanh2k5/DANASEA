package com.danasea.backend.modules.ai.domain.services;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

@Service
public class AIToolRegistry {

    private static final List<Map<String, Object>> CANONICAL_DEFINITIONS = buildCanonicalDefinitions();

    public List<Map<String, Object>> getToolDefinitions() {
        return CANONICAL_DEFINITIONS;
    }

    private static List<Map<String, Object>> buildCanonicalDefinitions() {
        List<Map<String, Object>> legacy = List.of(
            Map.of(
                "name", "search_services",
                "description", "Search for travel and marine services based on user criteria",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "query", Map.of("type", List.of("string", "null"), "description", "Search keywords"),
                        "min_price", Map.of("type", List.of("number", "null"), "description", "Minimum price"),
                        "max_price", Map.of("type", List.of("number", "null"), "description", "Maximum price"),
                        "category", Map.of("type", List.of("string", "null"), "description", "Category ID or name")
                    )
                )
            ),
            Map.of(
                "name", "get_service_detail",
                "description", "Get detailed information about a specific service, including real-time price and availability",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "service_id", Map.of("type", "string", "description", "The ID of the service")
                    ),
                    "required", List.of("service_id")
                )
            ),
            Map.of(
                "name", "get_policy",
                "description", "Get the exact policy text from system configs for cancellation, refunds, weather cancellation, or safety",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "policy_type", Map.of(
                            "type", "string",
                            "enum", List.of("CANCELLATION", "REFUND", "WEATHER_CANCELLATION", "SAFETY", "GENERAL"),
                            "description", "Type of policy to retrieve: CANCELLATION, REFUND, WEATHER_CANCELLATION, SAFETY, or GENERAL"
                        )
                    ),
                    "required", List.of("policy_type")
                )
            ),
            Map.of(
                "name", "get_weather_forecast",
                "description", "Get weather forecast and sea conditions for a specific location and date in Da Nang",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "location", Map.of("type", "string", "description", "Location in Da Nang (for example, Son Tra, My Khe, or Man Thai)"),
                        "date", Map.of("type", "string", "description", "Date in YYYY-MM-DD format, from today through 16 days ahead"),
                        "latitude", Map.of("type", List.of("number", "null"), "description", "Optional latitude; provide together with longitude"),
                        "longitude", Map.of("type", List.of("number", "null"), "description", "Optional longitude; provide together with latitude")
                    ),
                    "required", List.of("location", "date")
                )
            ),
            Map.of(
                "name", "get_safety_alert",
                "description", "Get maritime safety status, wave alerts, and danger warnings for sea activities in Da Nang",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "location", Map.of("type", "string", "description", "Location in Da Nang (for example, Son Tra Peninsula or My Khe Beach)"),
                        "category", Map.of("type", List.of("string", "null"), "description", "Optional activity category slug used for its safety thresholds"),
                        "latitude", Map.of("type", List.of("number", "null"), "description", "Optional latitude; provide together with longitude"),
                        "longitude", Map.of("type", List.of("number", "null"), "description", "Optional longitude; provide together with latitude")
                    ),
                    "required", List.of("location")
                )
            ),
            Map.of(
                "name", "request_booking_confirmation",
                "description", "Request confirmation from the user to proceed with booking a service",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "service_id", Map.of("type", "string", "description", "The ID of the service to book"),
                        "date", Map.of("type", "string", "description", "Booking date"),
                        "option_id", Map.of("type", "string", "description", "Active option ID from current service data"),
                        "slot_id", Map.of("type", "string", "description", "Current bookable slot ID from AI search data"),
                        "participants", Map.of("type", "integer", "description", "Number of participants")
                    ),
                    "required", List.of("service_id", "option_id", "slot_id", "date", "participants")
                )
            )
        );
        return java.util.stream.Stream.concat(legacy.stream(), CustomerFeatureToolDefinitions.definitions().stream())
                .map(AIToolRegistry::canonicalizeMap)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> canonicalizeMap(Map<String, Object> raw) {
        Map<String, Object> ordered = new LinkedHashMap<>();
        for (String priorityKey : List.of("name", "description", "type", "format", "enum", "properties", "items", "required", "parameters")) {
            if (raw.containsKey(priorityKey)) {
                ordered.put(priorityKey, canonicalizeValue(raw.get(priorityKey)));
            }
        }
        Map<String, Object> remaining = new TreeMap<>(raw);
        for (var entry : remaining.entrySet()) {
            if (!ordered.containsKey(entry.getKey())) {
                ordered.put(entry.getKey(), canonicalizeValue(entry.getValue()));
            }
        }
        return Collections.unmodifiableMap(ordered);
    }

    @SuppressWarnings("unchecked")
    private static Object canonicalizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return canonicalizeMap((Map<String, Object>) map);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(AIToolRegistry::canonicalizeValue).toList();
        }
        return value;
    }
}
