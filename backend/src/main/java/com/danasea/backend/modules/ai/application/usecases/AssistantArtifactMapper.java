package com.danasea.backend.modules.ai.application.usecases;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.danasea.backend.modules.ai.domain.models.AssistantArtifact;
import com.danasea.backend.modules.ai.domain.models.ToolCall;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

final class AssistantArtifactMapper {
    private final ObjectMapper mapper;
    AssistantArtifactMapper(ObjectMapper mapper) { this.mapper = mapper; }

    String modelContext(String result, ToolCall call) {
        if (result == null || result.length() <= 16384) return result;
        try {
            JsonNode reduced = reduce(mapper.readTree(result), 0);
            if (reduced instanceof ObjectNode object) object.put("toolContextCompacted", true);
            String json = mapper.writeValueAsString(reduced);
            if (json.length() <= 16384) return json;
            return mapper.writeValueAsString(Map.of("status", "RESULT_AVAILABLE_IN_STRUCTURED_CARD",
                    "cardId", call.getId(), "tool", call.getName(), "toolContextCompacted", true));
        } catch (Exception exception) { return "{\"errorCode\":\"AI_TOOL_RESULT_UNAVAILABLE\"}"; }
    }

    private JsonNode reduce(JsonNode node, int depth) {
        if (depth > 8) return TextNode.valueOf("See structured backend card");
        if (node.isTextual()) return TextNode.valueOf(node.asText().length() > 900 ? node.asText().substring(0, 900) : node.asText());
        if (node instanceof ArrayNode array) {
            ArrayNode result = mapper.createArrayNode();
            for (int index = 0; index < Math.min(8, array.size()); index++) result.add(reduce(array.get(index), depth + 1));
            return result;
        }
        if (node instanceof ObjectNode object) {
            ObjectNode result = mapper.createObjectNode();
            object.fields().forEachRemaining(field -> result.set(field.getKey(), reduce(field.getValue(), depth + 1)));
            return result;
        }
        return node;
    }

    AssistantArtifact map(ToolCall call, String result, UUID conversationId) {
        try {
            if (result == null || result.length() > 262144) return unavailable(call);
            JsonNode node = mapper.readTree(result);
            if (node == null || !node.isObject()) return unavailable(call);
            Map<String, Object> facts = mapper.convertValue(node, new TypeReference<Map<String, Object>>() { });
            boolean error = node.hasNonNull("error") || node.hasNonNull("errorCode");
            String errorCode = node.path("error").asText(node.path("errorCode").asText(""));
            String errorMessage = node.path("message").asText("").toLowerCase();
            boolean missingCoordinates = error && "ai_nearby_services".equals(call.getName())
                    && "INVALID_ARGUMENTS".equals(errorCode)
                    && (errorMessage.contains("latitude") || errorMessage.contains("longitude") || errorMessage.contains("coordinate"));
            String status = missingCoordinates ? "NEEDS_INPUT"
                    : (error ? "UNAVAILABLE" : node.path("status").asText("AVAILABLE"));
            List<AssistantArtifact.Source> sources = error ? List.of() : List.of(new AssistantArtifact.Source(
                    call.getId(), "DANASEA_BACKEND", call.getName() + ":" + call.getId(), OffsetDateTime.now()));
            List<AssistantArtifact.Action> actions = new ArrayList<>();
            if (!error && "CONFIRMATION_PENDING".equals(status) && node.path("cardId").isTextual()) {
                actions.add(new AssistantArtifact.Action("CONFIRM_BOOKING", "POST",
                        "/api/assistant/conversations/" + conversationId + "/confirm",
                        Map.of("cardId", node.get("cardId").asText()), true));
            }
            if (!error && ("ai_replan_itinerary".equals(call.getName()) || "ai_itinerary_history".equals(call.getName())
                    || "ai_get_itinerary".equals(call.getName()))) {
                appendProposalActions(call.getName(), node, actions);
            }
            if (!error && "ai_customer_support".equals(call.getName())) {
                for (JsonNode action : node.path("actionDetails")) {
                    String path = action.path("path").asText();
                    String method = action.path("method").asText();
                    if ((path.startsWith("/api/orders/") || path.startsWith("/api/ai/support/"))
                            && List.of("GET", "POST").contains(method)) {
                        actions.add(new AssistantArtifact.Action(action.path("code").asText(), method, path,
                                Map.of("requiresIdempotencyKey", action.path("requiresIdempotencyKey").asBoolean()),
                                action.path("requiresConfirmation").asBoolean(true)));
                    }
                }
            }
            List<String> required = new ArrayList<>();
            if (missingCoordinates) required.add("COORDINATES_REQUIRED");
            for (JsonNode field : node.path("requiredInputs")) if (field.isTextual()) required.add(field.asText());
            for (JsonNode field : node.path("clarificationQuestions")) if (field.isTextual()) required.add(field.asText());
            return new AssistantArtifact(call.getId(), type(call.getName()), call.getName(), status,
                    facts, sources, List.copyOf(actions), List.copyOf(required), false);
        } catch (Exception exception) { return unavailable(call); }
    }

    private void appendProposalActions(String toolName, JsonNode node, List<AssistantArtifact.Action> actions) {
        JsonNode proposal = null;
        if ("ai_replan_itinerary".equals(toolName)) {
            proposal = node.path("proposal");
        } else if ("ai_itinerary_history".equals(toolName)) {
            proposal = node.path("restoredProposal");
        } else if ("ai_get_itinerary".equals(toolName) && node.path("pendingProposals").isArray()
                && !node.path("pendingProposals").isEmpty()) {
            proposal = node.path("pendingProposals").get(0);
        }
        if (proposal == null || !proposal.isObject() || !"PENDING".equals(proposal.path("state").asText())) return;
        String proposalId = proposal.path("id").asText("");
        String itineraryId = proposal.path("itineraryId").asText(node.path("itineraryId").asText(node.path("id").asText("")));
        if (proposalId.isBlank() || itineraryId.isBlank() || !proposal.has("baseVersion")) return;
        long baseVersion = proposal.get("baseVersion").asLong();
        actions.add(new AssistantArtifact.Action("ACCEPT_PROPOSAL", "POST",
                "/api/ai/itineraries/" + itineraryId + "/proposals/" + proposalId + "/accept",
                Map.of("expectedVersion", baseVersion), true));
        actions.add(new AssistantArtifact.Action("REJECT_PROPOSAL", "POST",
                "/api/ai/itineraries/" + itineraryId + "/proposals/" + proposalId + "/reject",
                Map.of("expectedVersion", baseVersion), true));
    }

    boolean hasPolicyEvidence(AssistantArtifact card) {
        if (card.sources().isEmpty()) return false;
        if (List.of("get_policy", "get_cancellation_policy").contains(card.tool()))
            return card.facts().get("policy") instanceof Map<?, ?> policy && !policy.isEmpty()
                    || card.facts().values().stream().anyMatch(value -> value instanceof String text && !text.isBlank());
        return "ai_customer_support".equals(card.tool())
                && card.facts().get("cancellationPreview") instanceof Map<?, ?> preview && !preview.isEmpty();
    }

    private String type(String tool) {
        return switch (tool) {
            case "search_services", "search_service", "ai_smart_search", "ai_recommend_services", "ai_nearby_services" -> "SERVICE_LIST";
            case "get_service_detail" -> "SERVICE_DETAIL";
            case "ai_compare_services" -> "COMPARISON";
            case "ai_plan_itinerary", "ai_replan_itinerary", "ai_get_itinerary" -> "ITINERARY";
            case "ai_list_itineraries" -> "ITINERARY_LIST";
            case "ai_itinerary_history" -> "ITINERARY_HISTORY";
            case "ai_review_summary" -> "REVIEW_SUMMARY";
            case "get_weather_forecast", "get_safety_alert", "ai_weather_slot" -> "WEATHER";
            case "get_policy", "get_cancellation_policy" -> "POLICY";
            case "get_payment_providers" -> "PAYMENT_PROVIDERS";
            case "request_booking_confirmation" -> "BOOKING_CONFIRMATION";
            case "ai_customer_support" -> "CUSTOMER_SUPPORT";
            case "ai_marine_briefing" -> "MARINE_BRIEFING_CARD";
            default -> "TOOL_RESULT";
        };
    }

    private AssistantArtifact unavailable(ToolCall call) {
        return new AssistantArtifact(call.getId(), "TOOL_RESULT", call.getName(), "UNAVAILABLE",
                Map.of("errorCode", "AI_TOOL_RESULT_UNAVAILABLE"), List.of(), List.of(), List.of(), false);
    }
}
