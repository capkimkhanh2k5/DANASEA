package com.danasea.backend.modules.ai.domain.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.danasea.backend.modules.ai.domain.models.TravelContext;

public final class TravelQueryParser {
    private static volatile BigDecimal usdToVndRate = new BigDecimal("25775");
    private static final Pattern BUDGET = Pattern.compile("(?:duoi|toi da|ngan sach(?:\\s+la)?|under|below|max|up to|budget(?:\\s+of)?|around|about)\\s+(\\d{1,3}(?:[.,]\\d{3})+|\\d+(?:[.,]\\d+)?)\\s*(trieu|tr|million|nghin|ngan|k|vnd|dong|d|₫)(?![\\p{L}\\d])");
    private static final Pattern USD_BUDGET_PREFIX = Pattern.compile(
            "(?:duoi|toi da|ngan sach(?:\\s+la)?|under|below|max|up to|budget(?:\\s+of)?|for|around|about|khoang|tam|gia(?:\\s+duoi)?)?\\s*(?:\\$|\\busd)\\s*(\\d+(?:[.,]\\d+)*)(?!\\d)");
    private static final Pattern USD_BUDGET_SUFFIX = Pattern.compile(
            "(?:duoi|toi da|ngan sach(?:\\s+la)?|under|below|max|up to|budget(?:\\s+of)?|for|around|about|khoang|tam|gia(?:\\s+duoi)?)?\\s*(\\d+(?:[.,]\\d+)*)\\s*(?:\\$|usd|dollars?|bucks?)(?![\\p{L}\\d])");
    private static final Pattern BUDGET_REQUEST = Pattern.compile("\\b(?:ngan sach|budget)\\b|"
            + "\\b(?:duoi|toi da|under|below|max|up to)\\s+\\$\\s*\\d[\\d.,]*|"
            + "\\b(?:duoi|toi da|under|below|max|up to)\\s+\\d[\\d.,]*\\s*(?:trieu|tr|million|nghin|ngan|k|vnd|dong|d|₫|\\$|usd|dollars?|bucks?|eur)(?![\\p{L}\\d])");
    private static final Pattern NO_BUDGET_LIMIT = Pattern.compile(
            "\\b(?:khong gioi han ngan sach|khong gioi han ve ngan sach|no budget limit|unlimited budget)\\b");
    private static final Pattern MAX_ACTIVITIES = Pattern.compile(
            "(?:\\b(?:toi da|max|maximum|up to)\\s+)?(?<!\\d)(\\d{1,3})\\s*(?:hoat dong|trai nghiem|activities|activity)\\b");
    private static final Pattern ACCENTED_DIVING = Pattern.compile("(?<![\\p{L}])lặn(?![\\p{L}])");
    private static final List<String> COASTAL_AREAS = List.of(
            "man thai", "bai rang", "bai nam", "my khe", "non nuoc",
            "ngu hanh son", "cu lao cham", "nam o", "xuan thieu", "song han", "son tra");
    private static final Map<String, String> ACTIVITIES = Map.ofEntries(
            Map.entry("sup", "SUP"),
            Map.entry("paddleboard", "SUP"),
            Map.entry("paddleboarding", "SUP"),
            Map.entry("stand up paddle", "SUP"),
            Map.entry("kayak", "kayak"),
            Map.entry("kayaking", "kayak"),
            Map.entry("lan bien", "lặn"),
            Map.entry("lan sau", "lặn"),
            Map.entry("lan ngam san ho", "lặn"),
            Map.entry("lan san ho", "lặn"),
            Map.entry("snorkel", "lặn"),
            Map.entry("snorkeling", "lặn"),
            Map.entry("diving", "lặn"),
            Map.entry("scuba", "lặn"),
            Map.entry("cano", "cano"),
            Map.entry("canoe", "cano"),
            Map.entry("speedboat", "cano"),
            Map.entry("du thuyen", "du thuyền"),
            Map.entry("yacht", "du thuyền"),
            Map.entry("cruise", "du thuyền"),
            Map.entry("boat", "du thuyền"),
            Map.entry("cau muc", "câu mực"),
            Map.entry("squid fishing", "câu mực"));

    private TravelQueryParser() {}

    public static void setUsdToVndRate(BigDecimal rate) {
        if (rate != null && rate.compareTo(BigDecimal.ZERO) > 0) {
            usdToVndRate = rate;
        }
    }

    public static BigDecimal usdToVndRate() {
        return usdToVndRate;
    }

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
        String prepared = text == null ? "" : ACCENTED_DIVING.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("lan bien");
        String normalized = normalize(prepared).replaceAll("\\b(nhung|but|ma|instead|chon|tim|choose|find)\\b", ",");
        List<String> found = new ArrayList<>();
        for (var entry : ACTIVITIES.entrySet()) {
            var matcher = Pattern.compile("(?<![a-z])" + Pattern.quote(entry.getKey()) + "(?![a-z])").matcher(normalized);
            while (matcher.find()) {
                int clause = Math.max(normalized.lastIndexOf(',', matcher.start()), normalized.lastIndexOf('.', matcher.start())) + 1;
                boolean excluded = normalized.substring(clause, matcher.start())
                        .matches(".*\\b(khong|tranh|not|except|no|without|dont|don't|avoid|dislike|never)\\b.*");
                if (excluded == negative) found.add(normalize(entry.getValue()));
            }
        }
        return found.stream().distinct().sorted().toList();
    }

    public static Integer maxActivities(String text) {
        if (text == null || text.isBlank()) return null;
        var matcher = MAX_ACTIVITIES.matcher(normalize(text));
        if (!matcher.find()) return null;
        int parsed = Integer.parseInt(matcher.group(1));
        return Math.max(1, Math.min(5, parsed));
    }

    public static List<String> coastalAreas(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) return List.of();
        List<String> areas = new ArrayList<>();
        for (String area : COASTAL_AREAS) {
            if (Pattern.compile("(?<![a-z])" + Pattern.quote(area) + "(?![a-z])").matcher(normalized).find()) {
                areas.add(area);
            }
        }
        return List.copyOf(areas);
    }

    public static String primaryArea(String name, String address, String description, Collection<String> requestedAreas) {
        List<String> candidates = (requestedAreas == null || requestedAreas.isEmpty())
                ? COASTAL_AREAS
                : COASTAL_AREAS.stream().filter(requestedAreas::contains).toList();
        for (String field : List.of(normalize(name), normalize(description), normalize(address))) {
            if (field.isBlank()) continue;
            for (String area : candidates) {
                if (Pattern.compile("(?<![a-z])" + Pattern.quote(area) + "(?![a-z])").matcher(field).find()) {
                    return area;
                }
            }
        }
        return null;
    }

    public static boolean isMultiActivityOrMultiAreaRequest(String text) {
        if (text == null || text.isBlank()) return false;
        if (includedActivities(text).size() >= 2) return true;
        if (coastalAreas(text).size() >= 2) return true;
        String normalized = normalize(text);
        if (normalized.matches(".*\\bvua\\b.*\\bvua\\b.*")) return true;
        return !includedActivities(text).isEmpty()
                && normalized.matches(".*\\b(va|cung|ket hop|roi|and|with|then)\\b.*\\b(trai nghiem bien|hoat dong bien|tour bien|du lich bien|kham pha bien|marine experience|sea experience)\\b.*");
    }

    public static Integer partySize(String text) {
        var matcher = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*(nguoi|khach|people|pax|guests?|travelers?|persons?)\\b").matcher(normalize(text));
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
        return normalize(text).matches(".*(moi nguoi|/nguoi|per person|each person|/person|/pax).*?");
    }

    public static boolean requestsBudget(String text) {
        String normalized = normalize(text);
        return BUDGET_REQUEST.matcher(normalized).find() && !NO_BUDGET_LIMIT.matcher(normalized).find();
    }

    public static boolean hasUsdBudget(String text) {
        return usdBudgetAmount(text) != null;
    }

    public static BigDecimal usdBudgetAmount(String text) {
        if (text == null || text.isBlank()) return null;
        String normalized = normalize(text);
        Matcher prefix = USD_BUDGET_PREFIX.matcher(normalized);
        if (prefix.find()) {
            return parseUsdNumber(prefix.group(1));
        }
        Matcher suffix = USD_BUDGET_SUFFIX.matcher(normalized);
        if (suffix.find()) {
            return parseUsdNumber(suffix.group(1));
        }
        return null;
    }

    private static BigDecimal parseUsdNumber(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.matches("\\d{1,3}(?:,\\d{3})+(\\.\\d{1,2})?")) {
            return new BigDecimal(raw.replace(",", ""));
        }
        if (raw.matches("\\d+(?:\\.\\d{1,2})?")) {
            return new BigDecimal(raw);
        }
        if (raw.matches("\\d+,\\d{1,2}")) {
            return new BigDecimal(raw.replace(',', '.'));
        }
        return null;
    }

    public static BigDecimal budget(String text) {
        BigDecimal usd = usdBudgetAmount(text);
        if (usd != null) {
            return usd.multiply(usdToVndRate).setScale(0, RoundingMode.HALF_UP);
        }
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

    public static boolean mentionsToday(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) return false;
        if (normalized.matches(".*\\b(hom nay|sang nay|chieu nay|toi nay|today|tonight|this afternoon|this evening)\\b.*")) {
            return true;
        }
        LocalDate[] explicit = dates(text);
        LocalDate today = LocalDate.now(TravelContext.ZONE);
        return explicit[0] != null && explicit[0].equals(today);
    }
}
