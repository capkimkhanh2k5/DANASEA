package com.danasea.backend.modules.ai.domain.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CustomerFeatureToolDefinitions {
    private CustomerFeatureToolDefinitions() {}

    public static List<Map<String, Object>> definitions() {
        Map<String, Object> criteria = Map.of("type", "object", "properties", Map.ofEntries(
                Map.entry("query", Map.of("type", "string")),
                Map.entry("partySize", Map.of("type", "integer", "minimum", 1, "maximum", 50)),
                Map.entry("totalBudget", Map.of("type", "number", "description", "Total VND budget for the entire party")),
                Map.entry("from", Map.of("type", "string", "format", "date")),
                Map.entry("to", Map.of("type", "string", "format", "date")),
                Map.entry("dayStart", Map.of("type", "string", "description", "HH:mm daily start")),
                Map.entry("dayEnd", Map.of("type", "string", "description", "HH:mm daily end")),
                Map.entry("latitude", Map.of("type", "number")),
                Map.entry("longitude", Map.of("type", "number")),
                Map.entry("radiusKm", Map.of("type", "number")),
                Map.entry("interests", Map.of("type", "array", "items", Map.of("type", "string"), "maxItems", 10)),
                Map.entry("limit", Map.of("type", "integer", "maximum", 20)),
                Map.entry("maxActivities", Map.of("type", "integer", "minimum", 1, "maximum", 5)),
                Map.entry("weatherSafeOnly", Map.of("type", "boolean"))));
        return List.of(
                definition("ai_smart_search", "Search published services using natural language and explicit party, budget and date constraints. Prices are current active-option party quotes.", Map.of("criteria", criteria), List.of("criteria")),
                definition("ai_recommend_services", "Recommend currently bookable services using provided preferences and optional Quyet relevance suggestions. This is not a proven personalized ranking model.", Map.of("criteria", criteria), List.of("criteria")),
                definition("ai_nearby_services", "Find currently bookable experiences near explicit coordinates; ask for location if missing.", Map.of("criteria", criteria), List.of("criteria")),
                definition("ai_compare_services", "Compare two to four published services using source data and current option/party prices.", Map.of("criteria", criteria, "serviceIds", ids(4)), List.of("criteria", "serviceIds")),
                definition("ai_plan_itinerary", "Create an owned itinerary proposal with current slots, budget and estimated transfers. It reserves no inventory and charges nothing.", Map.of("criteria", criteria), List.of("criteria")),
                definition("ai_replan_itinerary", "Replan an owned itinerary or move a specific activity (1-based targetItemIndex) to a target time window at an expected version. Creates a PENDING proposal and never changes existing bookings.", Map.ofEntries(
                        Map.entry("criteria", criteria),
                        Map.entry("itineraryId", uuid()),
                        Map.entry("expectedVersion", Map.of("type", "integer", "minimum", 0)),
                        Map.entry("excludedServiceIds", ids(20)),
                        Map.entry("excludedSlotIds", ids(50)),
                        Map.entry("trigger", Map.of("type", "string")),
                        Map.entry("targetItemIndex", Map.of("type", "integer", "minimum", 1, "maximum", 20, "description", "1-based index of the activity to move or replace")),
                        Map.entry("targetDayStart", Map.of("type", "string", "description", "HH:mm target start time for the moved activity")),
                        Map.entry("targetDayEnd", Map.of("type", "string", "description", "HH:mm target end time for the moved activity"))), List.of("itineraryId", "expectedVersion")),
                definition("ai_list_itineraries", "List the authenticated customer's saved itineraries with 1-based index, version, lifecycle, and activity items.", Map.of(
                        "page", Map.of("type", "integer", "minimum", 0, "maximum", 10000),
                        "size", Map.of("type", "integer", "minimum", 1, "maximum", 20)), List.of()),
                definition("ai_get_itinerary", "Read a saved itinerary owned by the authenticated customer, including current version, items, and pending proposals.", Map.of("itineraryId", uuid()), List.of("itineraryId")),
                definition("ai_itinerary_history", "Read revision history and proposals for an owned itinerary, and optionally create a PENDING restore proposal from a prior revision after live revalidation.", Map.of(
                        "itineraryId", uuid(),
                        "restoreRevisionVersion", Map.of("type", "integer", "minimum", 0),
                        "expectedVersion", Map.of("type", "integer", "minimum", 0)), List.of("itineraryId")),
                definition("ai_review_summary", "Read an extractive summary with real public review IDs, rating counts and tentative Quyet aspect/sentiment suggestions. No reviews means no claims.", Map.of("serviceId", uuid()), List.of("serviceId")),
                definition("ai_weather_slot", "Evaluate backend weather rules for a specific current service option and slot; unknown data never authorizes booking.", Map.of("criteria", criteria, "serviceId", uuid(), "optionId", uuid(), "slotId", uuid()), List.of("criteria", "serviceId", "optionId", "slotId")),
                definition("ai_customer_support", "Read the authenticated customer's own order and optional cancellation preview. Ask for an order ID if missing. Never cancel, refund or change payment state.", Map.of("orderId", uuid(), "message", Map.of("type", "string"), "includeCancellationPreview", Map.of("type", "boolean")), List.of("message")),
                definition("ai_marine_briefing", "Read pre-trip marine briefing, safety checklist, and slot weather summary for an owned order.", Map.of("orderId", uuid()), List.of("orderId")));
    }

    private static Map<String, Object> definition(String name, String description, Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object"); schema.put("properties", properties); schema.put("required", required);
        return Map.of("name", name, "description", description, "parameters", schema);
    }
    private static Map<String, Object> uuid() { return Map.of("type", "string", "format", "uuid"); }
    private static Map<String, Object> ids(int maximum) { return Map.of("type", "array", "items", uuid(), "maxItems", maximum); }
}
