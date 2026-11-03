package com.danasea.backend.modules.ai.application.usecases;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalTime;
import java.time.Instant;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import com.danasea.backend.modules.ai.application.api.CustomerPreferenceReadApi;
import com.danasea.backend.modules.ai.domain.models.TravelCriteria;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.danasea.backend.modules.ai.application.ports.DecisionModelPort;
import com.danasea.backend.modules.ai.application.ports.TravelDataPort;
import com.danasea.backend.modules.ai.application.ports.TravelWeatherPort;
import com.danasea.backend.modules.ai.domain.models.DecisionResult;
import com.danasea.backend.modules.ai.domain.models.DecisionTask;
import com.danasea.backend.modules.ai.domain.models.TravelContext;
import com.danasea.backend.modules.ai.domain.services.TravelQueryParser;
import com.danasea.backend.modules.service.application.api.AiCatalogReadApi.Option;
import com.danasea.backend.modules.service.application.api.AiCatalogReadApi.PublishedService;
import com.danasea.backend.modules.service.application.api.AiCatalogReadApi.Query;
import com.danasea.backend.modules.service.application.api.AiCatalogReadApi.Slot;
import com.danasea.backend.shared.i18n.SupportedLanguage;

import lombok.RequiredArgsConstructor;
import java.time.Duration;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class DiscoverServicesUseCase {
    public record Candidate(PublishedService service, BigDecimal minimumPartyTotal, Double distanceKm,
                            double score, List<String> reasonCodes, DecisionResult relevance) {}
    public record Result(String status, String mode, TravelContext context, String retrievalKeyword,
                         BigDecimal effectiveTotalBudget, List<Candidate> candidates, List<String> requiredInputs,
                         DecisionResult intent, String rankingMethod, boolean exhaustive, List<String> limitations, TravelCriteria resolvedCriteria,
                         Instant checkedAt, Integer nextOffset, String rankingVersion, UUID recommendationId) {}

    private final TravelDataPort data;
    private final DecisionModelPort decisions;
    private final TravelWeatherPort weather;

    private CustomerPreferenceReadApi preferences;

    @Autowired
    public void setPreferences(CustomerPreferenceReadApi preferences) { this.preferences = preferences; }

    public Result execute(TravelContext context, String mode, SupportedLanguage language) {
        return execute(context, mode, language, null);
    }

    public Result execute(TravelContext context, String mode, SupportedLanguage language, UUID userId) {
        context.validateCurrentDates();
        if (!List.of("SEARCH", "RECOMMENDATION", "NEARBY").contains(mode)) throw new IllegalArgumentException("Invalid discovery mode");
        if (!context.criteria().clarificationQuestions().isEmpty()) {
            return missing(context, mode, context.criteria().clarificationQuestions());
        }
        if ("NEARBY".equals(mode) && context.latitude() == null) return missing(context, mode, List.of("latitude", "longitude"));
        CustomerPreferenceReadApi.Signals signals = preferences != null && userId != null && context.criteria().useSavedPreferences()
                ? preferences.load(userId) : null;
        String keyword = TravelQueryParser.activityKeyword(context.query());
        BigDecimal budget = context.totalBudget() != null ? context.totalBudget() : TravelQueryParser.budget(context.query());
        if (context.totalBudget() == null && budget != null && TravelQueryParser.perPersonBudget(context.query())) {
            budget = budget.multiply(BigDecimal.valueOf(context.partySize()));
        }
        final BigDecimal effectiveBudget = budget;
        if (budget != null && budget.compareTo(new BigDecimal("1000000000")) > 0) {
            throw new IllegalArgumentException("Parsed budget exceeds the supported maximum");
        }
        Query query = query(context, keyword, language, mode, budget, signals);
        long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        List<Candidate> candidates = new ArrayList<>();
        Map<UUID, TravelWeatherPort.Assessment> weatherCache = new HashMap<>();
        List<String> exclusions = new ArrayList<>(TravelQueryParser.excludedActivities(context.query()));
        exclusions.addAll(context.criteria().excludedActivities());
        if (signals != null && signals.personalizationEnabled()) exclusions.addAll(signals.exclusions().stream().map(TravelQueryParser::normalize).toList());
        var page = data.searchPage(query);
        List<PublishedService> retrieved = page == null ? data.search(query) : page.services();
        for (PublishedService service : retrieved) {
            String categoryText = TravelQueryParser.normalize(service.name() + " " + service.categoryName() + " " + service.categorySlug());
            if (exclusions.stream().anyMatch(categoryText::contains)) continue;
            List<Option> validOptions = service.options().stream()
                    .filter(option -> effectiveBudget == null || option.partyTotal().compareTo(effectiveBudget) <= 0)
                    .map(option -> new Option(option.id(), option.name(), option.pricingUnit(), option.unitPrice(),
                            option.quantity(), option.partyTotal(), option.benefits(), option.slots().stream()
                            .filter(slot -> slot.start() != null && !slot.start().isBefore(context.dayStart()))
                            .filter(slot -> endTime(slot.end(), slot.start(), service.durationMinutes()) != null)
                            .filter(slot -> !endTime(slot.end(), slot.start(), service.durationMinutes()).isAfter(context.dayEnd()))
                            .filter(slot -> !context.weatherSafeOnly() || weatherAcceptable(service, slot, weatherCache)).toList(), option.maxPaxPerPackage()))
                    .filter(option -> !option.slots().isEmpty()).toList();
            if (validOptions.isEmpty()) continue;
            Double distance = distance(context, service);
            if ("NEARBY".equals(mode) && distance == null || context.radiusKm() != null && (distance == null || distance > context.radiusKm())) continue;
            double baseline = baseline(context, service, distance);
            DecisionResult relevance = DecisionResult.unavailable(DecisionTask.RELEVANCE, "NOT_REQUESTED");
            if (signals != null && signals.personalizationEnabled()) {
                String text = TravelQueryParser.normalize(service.name() + " " + service.description() + " " + service.categoryName());
                for (String interest : signals.interests()) if (text.contains(TravelQueryParser.normalize(interest))) baseline += 0.5;
                if (signals.wishedServiceIds().contains(service.id())) baseline += 0.35;
                if (signals.recentlyViewedServiceIds().contains(service.id())) baseline += 0.1;
                if (signals.positiveServiceIds().contains(service.id())) baseline += 0.25;
                if (signals.negativeServiceIds().contains(service.id())) baseline -= 0.5;
            }
            PublishedService snapshot = new PublishedService(service.id(), service.vendorId(), service.name(), service.description(),
                    service.categoryId(), service.categoryName(), service.categorySlug(), service.address(), service.latitude(),
                    service.longitude(), service.durationMinutes(), service.weatherSensitive(), service.averageRating(), service.reviewCount(), validOptions);
            List<String> reasons = new ArrayList<>(List.of("PUBLISHED", "CURRENT_BOOKABLE_OPTION", "PARTY_QUOTE"));
            if (budget != null) reasons.add("WITHIN_TOTAL_BUDGET");
            if (distance != null) reasons.add("DISTANCE_KNOWN");
            if (relevance.available()) reasons.add("QUYET_RELEVANCE_SUGGESTION");
            candidates.add(new Candidate(snapshot, validOptions.stream().map(Option::partyTotal).min(BigDecimal::compareTo).orElseThrow(),
                    distance, baseline, List.copyOf(reasons), relevance));
        }
        Comparator<Candidate> comparator = "NEARBY".equals(mode)
                ? Comparator.comparing(Candidate::distanceKm, Comparator.nullsLast(Double::compareTo)).thenComparing(Candidate::minimumPartyTotal)
                : Comparator.comparingDouble(Candidate::score).reversed().thenComparing(candidate -> candidate.service().id());
        candidates.sort(comparator);
        if ("RECOMMENDATION".equals(mode)) {
            for (int index = 0; index < Math.min(5, candidates.size()) && System.nanoTime() < deadline; index++) {
                Candidate candidate = candidates.get(index);
                DecisionResult relevance = decisions.decide(DecisionTask.RELEVANCE,
                        Map.of("context", Map.of("query", context.query(), "interests", context.interests()),
                                "service", Map.of("name", candidate.service().name(), "description", truncate(candidate.service().description(), 900))));
                Double score = relevance.score("relevance");
                List<String> reasons = new ArrayList<>(candidate.reasonCodes());
                if (score != null) reasons.add("QUYET_RELEVANCE_SUGGESTION");
                if (signals != null && signals.personalizationEnabled()) reasons.add("CONSENTED_PREFERENCE_SIGNALS");
                candidates.set(index, new Candidate(candidate.service(), candidate.minimumPartyTotal(), candidate.distanceKm(),
                        candidate.score() + (score == null ? 0 : Math.max(0, Math.min(3, score)) / 3), List.copyOf(reasons), relevance));
            }
            candidates.sort(comparator);
        }
        List<Candidate> ranked = candidates.stream().limit(context.limit()).toList();
        DecisionResult intent = context.query().isBlank() || System.nanoTime() >= deadline
                ? DecisionResult.unavailable(DecisionTask.INTENT, "EMPTY_QUERY_OR_DEADLINE")
                : decisions.decide(DecisionTask.INTENT, Map.of("message", context.query()));
        List<String> limitations = new ArrayList<>(List.of("CURRENT_INVENTORY_REVALIDATED_AT_BOOKING", "CATALOG_SCAN_LIMIT_10000", "PAGINATION_CAN_CHANGE_WITH_INVENTORY"));
        limitations.addAll(context.criteria().unsupportedConstraints());
        if (page != null) limitations.addAll(page.limitations());
        boolean partialWeather = context.weatherSafeOnly() && weatherCache.values().stream().anyMatch(assessment -> !assessment.acceptable() && !"UNSAFE".equals(assessment.status()))
                || context.weatherSafeOnly() && weatherCache.size() >= 20
                || context.weatherSafeOnly() && page != null && !page.exhausted();
        if (partialWeather) limitations.add("WEATHER_PARTIAL_REVALIDATION_REQUIRED");
        UUID recommendationId = "RECOMMENDATION".equals(mode) && userId != null && preferences != null
                ? preferences.recordRecommendation(userId, ranked.stream().map(candidate -> candidate.service().id()).toList(), fingerprint(context.toString())) : null;
        return new Result(partialWeather || limitations.contains("CATALOG_SCAN_PARTIAL_COVERAGE") ? "PARTIAL" : ranked.isEmpty() ? "NO_MATCHES" : "AVAILABLE", mode, context, keyword, budget, ranked,
                List.of(), intent, "RECOMMENDATION".equals(mode) ? "CONTEXTUAL_BASELINE_WITH_OPTIONAL_QUYET" : "CATALOG_BASELINE", false,
                List.copyOf(limitations), context.criteria(), Instant.now(), page == null ? ranked.size() == context.limit() ? context.criteria().offset() + ranked.size() : null : page.nextOffset(),
                "danasea-customer-ranking-v2", recommendationId);
    }

    private Result missing(TravelContext context, String mode, List<String> questions) {
        return new Result("NEEDS_INPUT", mode, context, null, context.totalBudget(), List.of(), questions,
                DecisionResult.unavailable(DecisionTask.INTENT, "NOT_REQUESTED"), "CONTEXTUAL_BASELINE", false,
                context.criteria().unsupportedConstraints(), context.criteria(), Instant.now(), null, "danasea-customer-ranking-v2", null);
    }

    private boolean weatherAcceptable(PublishedService service, Slot slot,
                                      Map<UUID, TravelWeatherPort.Assessment> cache) {
        if (!service.weatherSensitive()) return true;
        if (!cache.containsKey(slot.id()) && cache.size() >= 20) return false;
        return cache.computeIfAbsent(slot.id(), ignored -> weather.assess(service, slot)).acceptable();
    }

    public PublishedService detail(UUID id, TravelContext context, SupportedLanguage language) {
        return data.find(id, query(context, null, language));
    }

    public Query query(TravelContext context, String keyword, SupportedLanguage language) {
        return query(context, keyword, language, "SEARCH", context.totalBudget(), null);
    }

    private Query query(TravelContext context, String keyword, SupportedLanguage language, String mode,
                        BigDecimal budget, CustomerPreferenceReadApi.Signals signals) {
        List<String> exclusions = new ArrayList<>(context.criteria().excludedActivities());
        exclusions.addAll(TravelQueryParser.excludedActivities(context.query()));
        if (signals != null && signals.personalizationEnabled()) exclusions.addAll(signals.exclusions());
        List<String> included = context.criteria().includedActivities().isEmpty()
                ? TravelQueryParser.includedActivities(context.query()) : context.criteria().includedActivities();
        List<String> rankingInterests = new ArrayList<>(context.interests());
        if (signals != null && signals.personalizationEnabled()) rankingInterests.addAll(signals.interests());
        return new Query(keyword, context.categoryId(), context.from(), context.to(), context.partySize(),
                context.latitude(), context.longitude(), context.radiusKm(), Math.min(15, context.limit()), language,
                budget, context.dayStart(), context.dayEnd(), included, exclusions.stream().distinct().toList(), mode, context.criteria().offset(), rankingInterests.stream().distinct().limit(20).toList());
    }

    private double baseline(TravelContext context, PublishedService service, Double distance) {
        double score = service.averageRating() == null ? 0 : service.averageRating().doubleValue() / 5.0 * Math.min(1, service.reviewCount() / 10.0);
        String text = TravelQueryParser.normalize(service.name() + " " + service.description() + " " + service.categoryName());
        for (String interest : context.interests()) if (text.contains(TravelQueryParser.normalize(interest))) score += 1;
        if (distance != null) score += 1 / (1 + distance);
        return score;
    }

    private Double distance(TravelContext context, PublishedService service) {
        if (context.latitude() == null || service.latitude() == null || service.longitude() == null) return null;
        return TravelQueryParser.distanceKm(context.latitude(), context.longitude(), service.latitude(), service.longitude());
    }

    public static LocalTime endTime(LocalTime end, LocalTime start, Integer duration) {
        if (end != null && start != null && end.isAfter(start)) return end;
        if (end == null && start != null && duration != null && duration > 0 && duration < 1440) {
            LocalTime derived = start.plusMinutes(duration);
            return derived.isAfter(start) ? derived : null;
        }
        return null;
    }

    private String fingerprint(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("Criteria fingerprint is unavailable", exception); }
    }

    private String truncate(String text, int limit) { return text == null ? "" : text.substring(0, Math.min(limit, text.length())); }
}
