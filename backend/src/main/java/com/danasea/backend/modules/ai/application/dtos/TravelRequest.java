package com.danasea.backend.modules.ai.application.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.danasea.backend.modules.ai.domain.models.TravelContext;
import com.danasea.backend.modules.ai.domain.models.TravelCriteria;
import com.danasea.backend.modules.ai.domain.services.TravelQueryParser;
import java.time.DateTimeException;

public record TravelRequest(String query, UUID categoryId, BigDecimal totalBudget, Integer partySize,
                            LocalDate from, LocalDate to, LocalTime dayStart, LocalTime dayEnd,
                            Double latitude, Double longitude, Double radiusKm, List<String> interests,
                            Integer limit, Integer maxActivities, Boolean weatherSafeOnly, String budgetBasis,
                            List<String> excludedInterests, Boolean useSavedPreferences, Integer offset) {
    public TravelRequest(String query, UUID categoryId, BigDecimal totalBudget, Integer partySize,
                         LocalDate from, LocalDate to, LocalTime dayStart, LocalTime dayEnd,
                         Double latitude, Double longitude, Double radiusKm, List<String> interests,
                         Integer limit, Integer maxActivities, Boolean weatherSafeOnly) {
        this(query, categoryId, totalBudget, partySize, from, to, dayStart, dayEnd, latitude, longitude,
                radiusKm, interests, limit, maxActivities, weatherSafeOnly, null, null, null, null);
    }

    public TravelContext context() {
        Integer parsedParty = TravelQueryParser.partySize(query);
        LocalDate[] dates;
        LocalTime[] times;
        try { dates = TravelQueryParser.dates(query); times = TravelQueryParser.times(query); }
        catch (DateTimeException exception) { throw new IllegalArgumentException("Invalid date or time in travel criteria", exception); }
        List<String> defaults = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        List<String> questions = new ArrayList<>();
        String normalized = TravelQueryParser.normalize(query);
        if (partySize == null && parsedParty == null) {
            defaults.add("partySize");
            if (normalized.matches(".*(gia dinh|nhom|family|group).*")) questions.add("PARTY_SIZE_REQUIRED");
        }
        if (from == null && dates[0] == null) defaults.add("dateRange");
        if (dayStart == null && times[0] == null) defaults.add("dayStart");
        if (dayEnd == null && times[1] == null) defaults.add("dayEnd");
        if (normalized.matches(".*(tre em|children|child|biet boi|swim|thiet bi|equipment|wheelchair).*")) {
            unsupported.add("PARTICIPATION_REQUIREMENTS_NEED_VENDOR_VERIFICATION");
        }
        if (latitude == null && normalized.matches(".*(gan toi|near me|my location).*")) questions.add("COORDINATES_REQUIRED");
        if (latitude == null && normalized.matches(".*(my khe|man thai|son tra|hoi an|ngu hanh son).*")) unsupported.add("PLACE_NAME_REQUIRES_COORDINATES");
        BigDecimal parsedBudget = TravelQueryParser.budget(query);
        if (totalBudget == null && parsedBudget == null && TravelQueryParser.requestsBudget(query)) {
            questions.add("BUDGET_REQUIRED");
        }
        String basis = budgetBasis == null ? TravelQueryParser.perPersonBudget(query) ? "PER_PERSON" : "TOTAL" : budgetBasis;
        if (totalBudget == null && parsedBudget != null && !TravelQueryParser.perPersonBudget(query)
                && !normalized.matches(".*(ca nhom|tong|total|group budget|gia dinh|family).*")) {
            defaults.add("budgetBasis=TOTAL");
        }
        List<String> exclusions = new ArrayList<>(TravelQueryParser.excludedActivities(query));
        if (excludedInterests != null && (excludedInterests.size() > 10 || excludedInterests.stream().anyMatch(value -> value == null || value.length() > 100))) throw new IllegalArgumentException("Invalid excluded interests");
        if (excludedInterests != null) exclusions.addAll(excludedInterests.stream().map(TravelQueryParser::normalize).toList());
        int party = partySize != null ? partySize : parsedParty == null ? 1 : parsedParty;
        // Explicit totalBudget always denotes the party total, preserving the existing API contract.
        BigDecimal budget = totalBudget != null ? totalBudget : parsedBudget;
        if (totalBudget == null && budget != null && "PER_PERSON".equals(basis)) budget = budget.multiply(BigDecimal.valueOf(party));
        Integer parsedMaxActivities = TravelQueryParser.maxActivities(query);
        int resolvedMaxActivities = maxActivities != null ? maxActivities : parsedMaxActivities != null ? parsedMaxActivities : 3;
        TravelContext context = new TravelContext(query, categoryId, budget, party,
                from != null ? from : dates[0], to != null ? to : from != null ? from : dates[1],
                dayStart != null ? dayStart : times[0], dayEnd != null ? dayEnd : times[1], latitude, longitude, radiusKm,
                interests, limit == null ? 15 : limit, resolvedMaxActivities,
                Boolean.TRUE.equals(weatherSafeOnly), new TravelCriteria(basis, TravelQueryParser.includedActivities(query),
                exclusions.stream().distinct().toList(), defaults, unsupported, questions, offset == null ? 0 : offset,
                Boolean.TRUE.equals(useSavedPreferences)));
        context.validateCurrentDates();
        return context;
    }
}
