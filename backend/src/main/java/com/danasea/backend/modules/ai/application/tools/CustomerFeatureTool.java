package com.danasea.backend.modules.ai.application.tools;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.danasea.backend.modules.ai.application.dtos.TravelRequest;
import com.danasea.backend.modules.ai.application.usecases.CompareServicesUseCase;
import com.danasea.backend.modules.ai.application.usecases.CustomerMarineBriefingUseCase;
import com.danasea.backend.modules.ai.application.usecases.CustomerSupportUseCase;
import com.danasea.backend.modules.ai.application.usecases.DiscoverServicesUseCase;
import com.danasea.backend.modules.ai.application.usecases.PlanItineraryUseCase;
import com.danasea.backend.modules.ai.application.usecases.PlanItineraryUseCase.PlanningDiagnostics;
import com.danasea.backend.modules.ai.application.usecases.PlanItineraryUseCase.Replanned;
import com.danasea.backend.modules.ai.application.usecases.ReviewSummaryUseCase;
import com.danasea.backend.modules.ai.application.usecases.WeatherAwareUseCase;
import com.danasea.backend.modules.ai.domain.exceptions.AiResourceNotFoundException;
import com.danasea.backend.modules.ai.domain.exceptions.AiStateConflictException;
import com.danasea.backend.modules.ai.domain.models.ItineraryPlan.Item;
import com.danasea.backend.modules.ai.domain.models.ItineraryPlan.Preview;
import com.danasea.backend.modules.ai.domain.models.ItineraryPlan.Proposal;
import com.danasea.backend.modules.ai.domain.models.ItineraryPlan.Revision;
import com.danasea.backend.modules.ai.domain.models.ItineraryPlan.Saved;
import com.danasea.backend.modules.ai.domain.models.TravelContext;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class CustomerFeatureTool implements ToolExecutor {
    private static final Set<String> AUTHENTICATED_TOOLS = Set.of(
            "ai_plan_itinerary", "ai_replan_itinerary", "ai_list_itineraries",
            "ai_get_itinerary", "ai_itinerary_history", "ai_customer_support", "ai_marine_briefing");
    private final String name;
    private final DiscoverServicesUseCase discovery;
    private final CompareServicesUseCase comparisons;
    private final PlanItineraryUseCase plans;
    private final ReviewSummaryUseCase reviews;
    private final WeatherAwareUseCase weather;
    private final CustomerSupportUseCase support;
    private final CustomerMarineBriefingUseCase marineBriefing;
    private final ObjectMapper mapper;

    public CustomerFeatureTool(String name, DiscoverServicesUseCase discovery, CompareServicesUseCase comparisons,
                               PlanItineraryUseCase plans, ReviewSummaryUseCase reviews, WeatherAwareUseCase weather,
                               CustomerSupportUseCase support, ObjectMapper mapper) {
        this(name, discovery, comparisons, plans, reviews, weather, support, null, mapper);
    }

    public CustomerFeatureTool(String name, DiscoverServicesUseCase discovery, CompareServicesUseCase comparisons,
                               PlanItineraryUseCase plans, ReviewSummaryUseCase reviews, WeatherAwareUseCase weather,
                               CustomerSupportUseCase support, CustomerMarineBriefingUseCase marineBriefing, ObjectMapper mapper) {
        this.name = name; this.discovery = discovery; this.comparisons = comparisons; this.plans = plans;
        this.reviews = reviews; this.weather = weather; this.support = support; this.marineBriefing = marineBriefing; this.mapper = mapper;
    }

    @Override public String getName() { return name; }
    @Override public String execute(String argumentsJson) { return execute(argumentsJson, null); }

    @Override
    public String execute(String argumentsJson, ToolExecutionContext context) {
        try {
            if (argumentsJson == null || argumentsJson.length() > 8000) throw new IllegalArgumentException("Invalid tool input length");
            JsonNode args = mapper.readTree(argumentsJson);
            if (args == null || !args.isObject()) throw new IllegalArgumentException("Arguments must be a JSON object");
            SupportedLanguage language = context == null || context.language() == null ? SupportedLanguage.VI : context.language();
            UUID userId = context == null ? null : context.userId();
            if (AUTHENTICATED_TOOLS.contains(name) && userId == null) {
                return error("AUTHENTICATION_REQUIRED", "Authenticate before using private AI features");
            }
            if ("ai_marine_briefing".equals(name)) {
                if (marineBriefing == null) return error("FEATURE_UNAVAILABLE", "Marine briefing feature is not configured");
                if (!args.hasNonNull("orderId")) return error("ORDER_ID_REQUIRED", "orderId is required");
            }
            Object result = switch (name) {
                case "ai_smart_search" -> discovery.execute(criteria(args), "SEARCH", language);
                case "ai_recommend_services" -> discovery.execute(criteria(args), "RECOMMENDATION", language, userId);
                case "ai_nearby_services" -> discovery.execute(criteria(args), "NEARBY", language);
                case "ai_compare_services" -> comparisons.execute(ids(args, "serviceIds"), criteria(args), language);
                case "ai_plan_itinerary" -> planItinerary(userId, args, language);
                case "ai_replan_itinerary" -> replanItinerary(userId, args, language);
                case "ai_list_itineraries" -> listItineraries(userId, args);
                case "ai_get_itinerary" -> getItinerary(userId, args);
                case "ai_itinerary_history" -> itineraryHistory(userId, args, language);
                case "ai_review_summary" -> reviews.execute(id(args, "serviceId"), language);
                case "ai_weather_slot" -> weather.execute(id(args, "serviceId"), id(args, "optionId"), id(args, "slotId"), criteria(args), language);
                case "ai_customer_support" -> support.execute(userId, args.hasNonNull("orderId") ? id(args, "orderId") : null,
                        args.path("message").asText(), args.path("includeCancellationPreview").asBoolean(false));
                case "ai_marine_briefing" -> marineBriefing.getMarineBriefing(userId, id(args, "orderId"), language);
                default -> throw new IllegalArgumentException("Unknown customer feature tool");
            };
            return mapper.writeValueAsString(result);
        } catch (IllegalArgumentException exception) {
            return error("INVALID_ARGUMENTS", exception.getMessage());
        } catch (AiResourceNotFoundException exception) {
            String message = exception.getMessage();
            String code = message != null && message.contains("revision") ? "REVISION_NOT_FOUND" : "ITINERARY_NOT_FOUND";
            return error(code, message != null ? message : "Itinerary not found");
        } catch (AiStateConflictException | ObjectOptimisticLockingFailureException exception) {
            return error("VERSION_CONFLICT_RELOAD_REQUIRED",
                    "Itinerary version has changed. Read the latest itinerary and proposals before replanning.");
        } catch (Exception exception) {
            return error("FEATURE_UNAVAILABLE", "The requested feature is unavailable or access was denied");
        }
    }

    private ObjectNode planItinerary(UUID userId, JsonNode args, SupportedLanguage language) throws Exception {
        TravelContext criteria = criteria(args);
        PlanningDiagnostics diagnostics;
        Preview preview;
        try {
            preview = plans.preview(userId, criteria, language);
            diagnostics = plans.lastDiagnostics();
        } finally {
            plans.clearLastDiagnostics();
        }
        if (diagnostics == null) {
            diagnostics = PlanItineraryUseCase.extractDiagnostics(preview.alternatives());
        }
        ObjectNode out = mapper.valueToTree(preview);
        String status = preview.alternatives().isEmpty() ? "NEEDS_REVIEW"
                : preview.alternatives().getFirst().plan().status();
        out.put("status", status);
        if (!preview.alternatives().isEmpty()) {
            List<String> requiredInputs = preview.alternatives().getFirst().plan().unresolvedConstraints().stream()
                    .filter(value -> value.startsWith("REQUIRED_INPUT:"))
                    .map(value -> value.substring("REQUIRED_INPUT:".length()))
                    .toList();
            if (!requiredInputs.isEmpty()) {
                out.putPOJO("requiredInputs", requiredInputs);
            }
        }
        out.put("planningDurationMs", diagnostics.planningDurationMs());
        out.put("choicesEvaluated", diagnostics.choicesEvaluated());
        out.putPOJO("rejectionCounts", diagnostics.rejectionCounts());
        return out;
    }

    private ObjectNode replanItinerary(UUID userId, JsonNode args, SupportedLanguage language) throws Exception {
        UUID itineraryId = id(args, "itineraryId");
        long expectedVersion = version(args);
        TravelContext replacement = args.hasNonNull("criteria") ? criteria(args) : null;
        Set<UUID> excludedServices = Set.copyOf(optionalIds(args, "excludedServiceIds"));
        Set<UUID> excludedSlots = Set.copyOf(optionalIds(args, "excludedSlotIds"));
        Integer targetItemIndex = args.hasNonNull("targetItemIndex") ? boundedInt(args, "targetItemIndex", 1, 20) : null;
        LocalTime targetDayStart = optionalTime(args, "targetDayStart");
        LocalTime targetDayEnd = optionalTime(args, "targetDayEnd");
        if (targetItemIndex == null && (targetDayStart != null || targetDayEnd != null)) {
            throw new IllegalArgumentException("targetItemIndex is required when targetDayStart or targetDayEnd is provided");
        }
        PlanningDiagnostics diagnostics;
        Replanned replanned;
        try {
            if (targetItemIndex != null) {
                replanned = plans.replanItemToWindow(itineraryId, userId, expectedVersion, targetItemIndex,
                        targetDayStart, targetDayEnd, replacement, excludedServices, excludedSlots, language);
            } else {
                replanned = plans.replan(itineraryId, userId, expectedVersion, replacement,
                        excludedServices, excludedSlots, args.path("trigger").asText("CUSTOMER_REQUEST"), language);
            }
            diagnostics = plans.lastDiagnostics();
        } finally {
            plans.clearLastDiagnostics();
        }
        if (diagnostics == null) {
            diagnostics = new PlanningDiagnostics(0L, 0, 0, 0, Map.of());
        }
        ObjectNode out = mapper.valueToTree(replanned);
        out.put("itineraryId", replanned.itinerary().id().toString());
        String status = replanned.proposal() != null && replanned.proposal().plan() != null
                ? replanned.proposal().plan().status() : "PROPOSED";
        out.put("status", status);
        out.put("planningDurationMs", diagnostics.planningDurationMs());
        out.put("choicesEvaluated", diagnostics.choicesEvaluated());
        out.putPOJO("rejectionCounts", diagnostics.rejectionCounts());
        return out;
    }

    private Map<String, Object> listItineraries(UUID userId, JsonNode args) {
        int page = args.hasNonNull("page") ? boundedInt(args, "page", 0, 10000) : 0;
        int size = args.hasNonNull("size") ? boundedInt(args, "size", 1, 20) : 10;
        List<Saved> savedList = plans.list(userId, page, size);
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (int index = 0; index < savedList.size(); index++) {
            Saved saved = savedList.get(index);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("index", index + 1);
            entry.put("id", saved.id());
            entry.put("version", saved.version());
            entry.put("lifecycle", saved.lifecycle());
            entry.put("totalPrice", saved.plan() == null ? null : saved.plan().totalPrice());
            entry.put("from", saved.plan() != null && saved.plan().context() != null ? saved.plan().context().from() : null);
            entry.put("to", saved.plan() != null && saved.plan().context() != null ? saved.plan().context().to() : null);
            List<Map<String, Object>> itemSummaries = summarizeItems(saved);
            entry.put("itemCount", itemSummaries.size());
            entry.put("items", itemSummaries);
            entry.put("createdAt", saved.createdAt());
            entry.put("updatedAt", saved.updatedAt());
            summaries.add(entry);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", savedList.isEmpty() ? "NO_ITINERARIES" : "AVAILABLE");
        out.put("page", page);
        out.put("size", size);
        out.put("count", summaries.size());
        out.put("itineraries", summaries);
        return out;
    }

    private Map<String, Object> getItinerary(UUID userId, JsonNode args) {
        UUID itineraryId = id(args, "itineraryId");
        Saved saved = plans.get(itineraryId, userId);
        List<Proposal> pendingProposals = plans.proposals(itineraryId, userId).stream()
                .filter(proposal -> "PENDING".equals(proposal.state()))
                .toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "AVAILABLE");
        out.put("id", saved.id());
        out.put("itineraryId", saved.id());
        out.put("version", saved.version());
        out.put("lifecycle", saved.lifecycle());
        out.put("bookingChanged", saved.bookingChanged());
        out.put("createdAt", saved.createdAt());
        out.put("updatedAt", saved.updatedAt());
        out.put("plan", saved.plan());
        out.put("items", summarizeItems(saved));
        out.put("pendingProposals", pendingProposals);
        return out;
    }

    private Map<String, Object> itineraryHistory(UUID userId, JsonNode args, SupportedLanguage language) {
        UUID itineraryId = id(args, "itineraryId");
        Long restoreRevisionVersion = args.hasNonNull("restoreRevisionVersion")
                ? nonNegativeLong(args, "restoreRevisionVersion") : null;
        Long expectedVersion = args.hasNonNull("expectedVersion") ? nonNegativeLong(args, "expectedVersion") : null;
        if (restoreRevisionVersion != null && expectedVersion == null) {
            throw new IllegalArgumentException("expectedVersion is required when restoring a revision");
        }
        Saved saved = plans.get(itineraryId, userId);
        Proposal restoredProposal = null;
        if (restoreRevisionVersion != null) {
            try {
                restoredProposal = plans.restoreFromRevision(itineraryId, userId, expectedVersion, restoreRevisionVersion, language);
            } finally {
                plans.clearLastDiagnostics();
            }
        }
        List<Revision> revisions = plans.revisions(itineraryId, userId);
        List<Proposal> proposals = plans.proposals(itineraryId, userId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "AVAILABLE");
        out.put("itineraryId", saved.id());
        out.put("currentVersion", saved.version());
        out.put("lifecycle", saved.lifecycle());
        out.put("revisions", revisions);
        out.put("proposals", proposals);
        if (restoredProposal != null) {
            out.put("restoredProposal", restoredProposal);
        }
        out.put("bookingChanged", false);
        return out;
    }

    private List<Map<String, Object>> summarizeItems(Saved saved) {
        if (saved.plan() == null || saved.plan().items() == null) return List.of();
        List<Item> items = saved.plan().items();
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            Item item = items.get(index);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("index", index + 1);
            row.put("serviceId", item.serviceId());
            row.put("serviceName", item.serviceName());
            row.put("optionId", item.optionId());
            row.put("slotId", item.slotId());
            row.put("start", item.start());
            row.put("end", item.end());
            row.put("partyTotal", item.partyTotal());
            row.put("weatherStatus", item.weatherStatus());
            result.add(row);
        }
        return result;
    }

    private TravelContext criteria(JsonNode args) throws Exception {
        if (!args.hasNonNull("criteria") || !args.get("criteria").isObject()) throw new IllegalArgumentException("criteria is required");
        return mapper.treeToValue(args.get("criteria"), TravelRequest.class).context();
    }

    private UUID id(JsonNode args, String key) {
        if (!args.path(key).isTextual()) throw new IllegalArgumentException(key + " must be a UUID");
        return UUID.fromString(args.get(key).asText());
    }

    private long version(JsonNode args) {
        if (!args.path("expectedVersion").isIntegralNumber() || !args.path("expectedVersion").canConvertToLong()
                || args.path("expectedVersion").asLong() < 0) throw new IllegalArgumentException("expectedVersion is required");
        return args.get("expectedVersion").asLong();
    }

    private long nonNegativeLong(JsonNode args, String key) {
        if (!args.path(key).isIntegralNumber() || !args.path(key).canConvertToLong()
                || args.path(key).asLong() < 0) throw new IllegalArgumentException(key + " must be a non-negative integer");
        return args.get(key).asLong();
    }

    private int boundedInt(JsonNode args, String key, int min, int max) {
        if (!args.path(key).isIntegralNumber() || !args.path(key).canConvertToInt()) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        int value = args.get(key).asInt();
        if (value < min || value > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        }
        return value;
    }

    private LocalTime optionalTime(JsonNode args, String key) {
        if (!args.hasNonNull(key)) return null;
        if (!args.path(key).isTextual()) throw new IllegalArgumentException(key + " must be HH:mm");
        try {
            return LocalTime.parse(args.get(key).asText().trim());
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Invalid " + key + " time format", exception);
        }
    }

    private List<UUID> optionalIds(JsonNode args, String key) { return args.hasNonNull(key) ? ids(args, key) : List.of(); }
    private List<UUID> ids(JsonNode args, String key) {
        if (!args.path(key).isArray() || args.path(key).size() > 50) throw new IllegalArgumentException(key + " must be a bounded UUID array");
        return StreamSupport.stream(args.get(key).spliterator(), false).map(value -> UUID.fromString(value.asText())).toList();
    }
    private String error(String code, String message) {
        try { return mapper.writeValueAsString(Map.of("error", code, "message", message)); }
        catch (Exception exception) { return "{\"error\":\"FEATURE_UNAVAILABLE\"}"; }
    }
}
