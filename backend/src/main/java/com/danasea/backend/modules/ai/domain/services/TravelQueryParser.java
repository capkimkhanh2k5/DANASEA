package com.danasea.backend.modules.ai.domain.services;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.danasea.backend.modules.ai.domain.models.TravelContext;

public final class TravelQueryParser {
    private static final Pattern BUDGET = Pattern.compile("(?:duoi|toi da|ngan sach|under|budget|up to)\\s+(\\d{1,3}(?:[.,]\\d{3})+|\\d+(?:[.,]\\d+)?)\\s*(trieu|tr|million|nghin|ngan|k|vnd|dong|d|₫)(?![\\p{L}\\d])");
    private static final Pattern BUDGET_REQUEST = Pattern.compile("\\b(?:ngan sach|budget)\\b|"
            + "\\b(?:duoi|toi da|under|up to)\\s+\\d[\\d.,]*\\s*(?:trieu|nghin|ngan|k|vnd|dong|d|₫|usd|eur)(?![\\p{L}\\d])");
    private static final Pattern NO_BUDGET_LIMIT = Pattern.compile(
            "\\b(?:khong gioi han ngan sach|khong gioi han ve ngan sach|no budget limit|unlimited budget)\\b");
    private static final Map<String, String> ACTIVITIES = Map.of(
            "sup", "SUP", "kayak", "kayak", "lan bien", "lặn", "snorkel", "lặn",
            "diving", "lặn", "cano", "cano", "du thuyen", "du thuyền");

    private TravelQueryParser() {}

    public static String normalize(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }

    public static String activityKeyword(String text) {
        List<String> unique = includedActivities(text);
        return unique.size() == 1 ? unique.getFirst() : null;
    }

    public static List<String> includedActivities(String text) { return activities(text, false); }
    public static List<String> excludedActivities(String text) { return activities(text, true); }

    private static List<String> activities(String text, boolean negative) {
        String normalized = normalize(text).replaceAll("\\b(nhung|but|ma|instead|chon|tim|choose|find)\\b", ",");
        List<String> found = new ArrayList<>();
        for (var entry : ACTIVITIES.entrySet()) {
            var matcher = Pattern.compile("(?<![a-z])" + Pattern.quote(entry.getKey()) + "(?![a-z])").matcher(normalized);
            while (matcher.find()) {
                int clause = Math.max(normalized.lastIndexOf(',', matcher.start()), normalized.lastIndexOf('.', matcher.start())) + 1;
                boolean excluded = normalized.substring(clause, matcher.start()).matches(".*\\b(khong|tranh|not|except|no|without)\\b.*");
                if (excluded == negative) found.add(normalize(entry.getValue()));
            }
        }
        return found.stream().distinct().sorted().toList();
    }

    public static Integer partySize(String text) {
        var matcher = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*(nguoi|khach|people|pax)\\b").matcher(normalize(text));
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    public static LocalDate date(String text) { return dates(text)[0]; }

    public static LocalDate[] dates(String text) {
        String normalized = normalize(text);
        LocalDate today = LocalDate.now(TravelContext.ZONE);
        var iso = Pattern.compile("(?<!\\d)(20\\d{2}-\\d{2}-\\d{2})(?!\\d)").matcher(normalized);
        List<LocalDate> explicit = new ArrayList<>();
        while (iso.find()) explicit.add(LocalDate.parse(iso.group(1)));
        if (!explicit.isEmpty()) return new LocalDate[]{explicit.getFirst(), explicit.getLast()};
        var local = Pattern.compile("(?<!\\d)(\\d{1,2})/(\\d{1,2})(?:/(20\\d{2}))?(?!\\d)").matcher(normalized);
        if (local.find()) {
            LocalDate date = LocalDate.of(local.group(3) == null ? today.getYear() : Integer.parseInt(local.group(3)),
                    Integer.parseInt(local.group(2)), Integer.parseInt(local.group(1)));
            return new LocalDate[]{date, date};
        }
        if (normalized.contains("ngay kia") || normalized.contains("day after tomorrow")) return new LocalDate[]{today.plusDays(2), today.plusDays(2)};
        if (normalized.contains("ngay mai") || normalized.contains("tomorrow")) return new LocalDate[]{today.plusDays(1), today.plusDays(1)};
        if (normalized.contains("hom nay") || normalized.contains("today")) return new LocalDate[]{today, today};
        if (normalized.contains("cuoi tuan") || normalized.contains("weekend")) {
            LocalDate saturday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
            if (today.getDayOfWeek() == DayOfWeek.SUNDAY) saturday = today;
            if (normalized.contains("tuan sau") || normalized.contains("next weekend")) saturday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
            return new LocalDate[]{saturday, saturday.getDayOfWeek() == DayOfWeek.SUNDAY ? saturday : saturday.plusDays(1)};
        }
        String[] days = {"thu hai", "thu ba", "thu tu", "thu nam", "thu sau", "thu bay", "chu nhat"};
        String[] english = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};
        for (int index = 0; index < days.length; index++) {
            if (normalized.contains(days[index]) || normalized.contains(english[index])) {
                LocalDate date = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(index + 1)));
                return new LocalDate[]{date, date};
            }
        }
        return new LocalDate[]{null, null};
    }

    public static LocalTime[] times(String text) {
        String normalized = normalize(text);
        var matcher = Pattern.compile("(?<!\\d)(\\d{1,2})(?:h|gio|:)(\\d{2})?(?!\\d)").matcher(normalized);
        List<LocalTime> found = new ArrayList<>();
        while (matcher.find()) found.add(LocalTime.of(Integer.parseInt(matcher.group(1)), matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2))));
        if (found.size() >= 2) return new LocalTime[]{found.getFirst(), found.get(1)};
        if (!found.isEmpty()) return normalized.matches(".*\\b(truoc|before)\\b.*") ? new LocalTime[]{null, found.getFirst()} : new LocalTime[]{found.getFirst(), null};
        if (normalized.contains("buoi sang") || normalized.contains("morning")) return new LocalTime[]{LocalTime.of(8, 0), LocalTime.NOON};
        if (normalized.contains("buoi chieu") || normalized.contains("afternoon")) return new LocalTime[]{LocalTime.of(13, 0), LocalTime.of(18, 0)};
        return new LocalTime[]{null, null};
    }

    public static boolean perPersonBudget(String text) {
        return normalize(text).matches(".*(moi nguoi|/nguoi|per person|each person).*?");
    }

    public static boolean requestsBudget(String text) {
        String normalized = normalize(text);
        return BUDGET_REQUEST.matcher(normalized).find() && !NO_BUDGET_LIMIT.matcher(normalized).find();
    }

    public static BigDecimal budget(String text) {
        var matcher = BUDGET.matcher(normalize(text));
        if (!matcher.find()) return null;
        String unit = matcher.group(2);
        String number = matcher.group(1);
        boolean currency = Set.of("vnd", "dong", "d", "₫").contains(unit);
        if (currency && number.matches("\\d{1,3}(?:[.,]\\d{3})+")) number = number.replace(".", "").replace(",", "");
        BigDecimal amount = new BigDecimal(number.replace(',', '.'));
        if (unit.equals("trieu") || unit.equals("tr") || unit.equals("million")) amount = amount.multiply(new BigDecimal("1000000"));
        else if (unit.equals("nghin") || unit.equals("ngan") || unit.equals("k")) amount = amount.multiply(new BigDecimal("1000"));
        else if (number.matches("\\d{1,3}(?:[.,]\\d{3})+")) amount = new BigDecimal(number.replace(".", "").replace(",", ""));
        return amount;
    }

    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dlat = Math.toRadians(lat2 - lat1);
        double dlon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dlat / 2), 2) + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dlon / 2), 2);
        return 6371 * 2 * Math.asin(Math.sqrt(Math.min(1, a)));
    }
}
