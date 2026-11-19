package com.danasea.backend.modules.ai.domain.models;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public record TravelContext(String query, UUID categoryId, BigDecimal totalBudget, int partySize,
                            LocalDate from, LocalDate to, LocalTime dayStart, LocalTime dayEnd,
                            Double latitude, Double longitude, Double radiusKm,
                            List<String> interests, int limit, int maxActivities, boolean weatherSafeOnly, TravelCriteria criteria) {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public TravelContext(String query, UUID categoryId, BigDecimal totalBudget, int partySize,
                         LocalDate from, LocalDate to, LocalTime dayStart, LocalTime dayEnd,
                         Double latitude, Double longitude, Double radiusKm, List<String> interests,
                         int limit, int maxActivities, boolean weatherSafeOnly) {
        this(query, categoryId, totalBudget, partySize, from, to, dayStart, dayEnd, latitude, longitude,
                radiusKm, interests, limit, maxActivities, weatherSafeOnly, TravelCriteria.empty());
    }

    public TravelContext {
        query = query == null ? "" : query.trim();
        criteria = criteria == null ? TravelCriteria.empty() : criteria;
        if (interests != null && interests.stream().anyMatch(interest -> interest == null)) throw new IllegalArgumentException("Interests must not contain null values");
        interests = interests == null ? List.of() : List.copyOf(interests);
        LocalDate today = LocalDate.now(ZONE);
        from = from == null ? today : from;
        to = to == null ? from.plusDays(7) : to;
        dayStart = dayStart == null ? LocalTime.of(8, 0) : dayStart;
        dayEnd = dayEnd == null ? LocalTime.of(18, 0) : dayEnd;
        if (query.length() > 1800 || interests.size() > 10
                || interests.stream().anyMatch(interest -> interest == null || interest.length() > 100)) {
            throw new IllegalArgumentException("Query or interests exceed their length limits");
        }
        if (partySize < 1 || partySize > 50 || limit < 1 || limit > 15 || maxActivities < 1 || maxActivities > 5) {
            throw new IllegalArgumentException("Invalid party size, result limit or activity count");
        }
        if (totalBudget != null && (totalBudget.signum() < 0 || totalBudget.compareTo(new BigDecimal("1000000000")) > 0)) {
            throw new IllegalArgumentException("Total budget must be between zero and one billion VND");
        }
        if (to.isBefore(from) || to.isAfter(from.plusDays(14)) || !dayStart.isBefore(dayEnd)) {
            throw new IllegalArgumentException("Invalid date range or daily time window");
        }
        if ((latitude == null) != (longitude == null)
                || latitude != null && (!Double.isFinite(latitude) || Math.abs(latitude) > 90
                || !Double.isFinite(longitude) || Math.abs(longitude) > 180)
                || radiusKm != null && (latitude == null || !Double.isFinite(radiusKm) || radiusKm <= 0 || radiusKm > 200)) {
            throw new IllegalArgumentException("Invalid coordinates or search radius");
        }
    }

    public void validateCurrentDates() {
        LocalDate today = LocalDate.now(ZONE);
        if (from.isBefore(today)) {
            throw new IllegalArgumentException("Update the travel date before searching or replanning");
        }
        if (from.isAfter(today.plusDays(14)) || to.isAfter(today.plusDays(14))) {
            throw new IllegalArgumentException("Travel dates must be within 14 days from today");
        }
    }
}
