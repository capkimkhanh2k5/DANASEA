package com.danasea.backend.modules.ai.application.tools;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;

import com.danasea.backend.modules.ai.application.dtos.TravelRequest;
import com.danasea.backend.modules.ai.application.usecases.CompareServicesUseCase;
import com.danasea.backend.modules.ai.application.usecases.CustomerMarineBriefingUseCase;
import com.danasea.backend.modules.ai.application.usecases.CustomerSupportUseCase;
import com.danasea.backend.modules.ai.application.usecases.DiscoverServicesUseCase;
import com.danasea.backend.modules.ai.application.usecases.PlanItineraryUseCase;
import com.danasea.backend.modules.ai.application.usecases.ReviewSummaryUseCase;
import com.danasea.backend.modules.ai.application.usecases.WeatherAwareUseCase;
import com.danasea.backend.modules.ai.domain.models.TravelContext;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CustomerFeatureTool implements ToolExecutor {
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
            if (Set.of("ai_plan_itinerary", "ai_replan_itinerary", "ai_customer_support", "ai_marine_briefing").contains(name) && userId == null) {
                return error("AUTHENTICATION_REQUIRED", "Authenticate before using private AI features");
            }
            Object result = switch (name) {
                case "ai_smart_search" -> discovery.execute(criteria(args), "SEARCH", language);
                case "ai_recommend_services" -> discovery.execute(criteria(args), "RECOMMENDATION", language, userId);
                case "ai_nearby_services" -> discovery.execute(criteria(args), "NEARBY", language);
                case "ai_compare_services" -> comparisons.execute(ids(args, "serviceIds"), criteria(args), language);
                case "ai_plan_itinerary" -> plans.preview(userId, criteria(args), language);
                case "ai_replan_itinerary" -> plans.replan(id(args, "itineraryId"), userId,
                        version(args), args.hasNonNull("criteria") ? criteria(args) : null,
                        Set.copyOf(optionalIds(args, "excludedServiceIds")), Set.copyOf(optionalIds(args, "excludedSlotIds")),
                        args.path("trigger").asText("CUSTOMER_REQUEST"), language);
                case "ai_review_summary" -> reviews.execute(id(args, "serviceId"), language);
                case "ai_weather_slot" -> weather.execute(id(args, "serviceId"), id(args, "optionId"), id(args, "slotId"), criteria(args), language);
                case "ai_customer_support" -> support.execute(userId, args.hasNonNull("orderId") ? id(args, "orderId") : null,
                        args.path("message").asText(), args.path("includeCancellationPreview").asBoolean(false));
                case "ai_marine_briefing" -> {
                    if (marineBriefing == null) yield error("FEATURE_UNAVAILABLE", "Marine briefing feature is not configured");
                    if (!args.hasNonNull("orderId")) yield error("ORDER_ID_REQUIRED", "orderId is required");
                    yield marineBriefing.getMarineBriefing(userId, id(args, "orderId"), language);
                }
                default -> throw new IllegalArgumentException("Unknown customer feature tool");
            };
            return mapper.writeValueAsString(result);
        } catch (IllegalArgumentException exception) { return error("INVALID_ARGUMENTS", exception.getMessage()); }
        catch (Exception exception) { return error("FEATURE_UNAVAILABLE", "The requested feature is unavailable or access was denied"); }
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
