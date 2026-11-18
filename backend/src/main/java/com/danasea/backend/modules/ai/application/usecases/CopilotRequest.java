package com.danasea.backend.modules.ai.application.usecases;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;

import com.danasea.backend.modules.ai.domain.services.TravelQueryParser;
import com.danasea.backend.modules.report.domain.models.TimeRange;

final class CopilotRequest {
    private static final Pattern REVENUE = words("doanh thu|gmv|revenue|turnover");
    private static final Pattern METRIC_DEFINITION = Pattern.compile(
            "^(?:gmv la gi|doanh thu la gi|what is gmv|what does gmv mean)[?!.]*$");
    private static final Pattern PLATFORM = words("toan san|ca san|toan nen tang|toan he thong|cua (?:san )?danasea|"
            + "doanh thu he thong|gmv danasea|platform wide|platform revenue|platform gmv|across the platform|entire platform|all vendors");
    private static final Pattern OTHER_VENDOR = words("gian hang khac|doi tac khac|vendor khac|nha cung cap khac|"
            + "another vendor|other vendors|another shop");
    private static final Pattern SERVICE = words("dich vu|services?");
    private static final Pattern COUNT = words("may|bao nhieu|tong|so luong|dem|how many|count|total|number of");
    private static final Pattern PUBLISHED = words("da dang|dang cong khai|dang hoat dong|da xuat ban|published|active");
    private static final Pattern CUSTOMER_BOOKING = words("dat tour|dat ve|dat chuyen|dat dich vu|tim tour|tim dich vu|"
            + "goi y tour|tu van tour|len lich trinh|book a tour|book a service|find a tour|find services|"
            + "recommend a tour|plan an itinerary");
    private static final Pattern BOOKING_REPORT = words("luot dat|don dat|tinh hinh dat|thong ke|bao cao|booking metrics|"
            + "booking volume|booking count|how many|top|nhieu nhat|bao nhieu|ti le");
    private static final Pattern TODAY = Pattern.compile("^(?:hom nay la ngay may|hom nay la ngay gi|hom nay thu may|"
            + "hom nay la thu may|what is today's date|what date is it today|what day is it today)[?!.]*$");
    private static final Pattern DATE = Pattern.compile("(?<![\\d/])(?:\\d{4}-\\d{2}-\\d{2}|"
            + "\\d{1,2}/\\d{1,2}(?:/\\d{4})?)(?![\\d/])");
    private static final Pattern FOUR_DIGIT_YEAR = Pattern.compile("(?<!\\d)(20\\d{2})(?!\\d)");
    private static final DateTimeFormatter LOCAL_DATE = new DateTimeFormatterBuilder()
            .appendPattern("d/M/uuuu").toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern THIS_MONTH = words("thang nay|this month");
    private static final Pattern LAST_MONTH = words("thang truoc|last month");
    private static final Pattern TODAY_PERIOD = words("hom nay|today");
    private static final Pattern YESTERDAY = words("hom qua|yesterday");
    private static final Pattern THIS_WEEK = words("tuan nay|this week");
    private static final Pattern LAST_WEEK = words("tuan truoc|last week");
    private static final Pattern SPECIFIC_MONTH = Pattern.compile(
            "\\b(?:thang|month)\\s*(\\d{1,2})(?:[/-]|\\s+(?:nam\\s+)?|\\s*)(\\d{4})?\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QUARTER = Pattern.compile(
            "\\b(?:quy|quarter|q)\\s*([1-4])(?:[/-]|\\s+(?:nam\\s+)?|\\s*)(\\d{4})?\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SPECIFIC_YEAR = Pattern.compile(
            "\\b(?:nam|year)\\s*(\\d{4})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_DAYS = Pattern.compile(
            "\\b(?:last (\\d{1,4}) days?|(?:trong )?(\\d{1,4}) ngay (?:qua|gan day|gan nhat))\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PERIOD_FOLLOW_UP = Pattern.compile(
            "^(?:con |the con |vay con |what about |and )?(?:hom nay|hom qua|thang nay|thang truoc|"
                    + "today|yesterday|this month|last month)(?: thi sao)?[?!.]*$");

    private static final Pattern VENDOR_RANKING = Pattern.compile(
            "(?i)\\b(?:top\\s*\\d*|xep hang|dung dau|cao nhat|thap nhat|lon nhat|bac nhat)\\b"
    );
    private static final Pattern VENDOR_COMPARE = words("so sanh|compare|doi chieu");
    private static final Pattern VENDOR_STATS = words(
            "theo doi tac|tung doi tac|cac doi tac|tat ca doi tac|moi doi tac|toan bo doi tac"
    );
    private static final Pattern TOP_N = Pattern.compile("(?i)\\btop\\s*(\\d+)\\b");
    private static final Pattern TOP_1 = Pattern.compile(
            "(?i)\\b(?:ai|doi tac nao|gian hang nao)\\b.*\\b(?:cao nhat|lon nhat|dung dau|so 1)\\b"
    );
    private static final Pattern ALL_VENDORS_RANKING = Pattern.compile(
            "(?i)\\b(?:xep hang tat ca|toan bo doi tac|tat ca doi tac)\\b"
    );

    private CopilotRequest() {}

    static String normalize(String text) {
        return TravelQueryParser.normalize(text).replaceAll("[\\s-]+", " ").strip();
    }

    static boolean isRevenue(String text) {
        return matches(REVENUE, text) && !matches(METRIC_DEFINITION, text);
    }

    static boolean isVendorRanking(String text) {
        return matches(VENDOR_RANKING, text);
    }

    static boolean isVendorCompare(String text) {
        return matches(VENDOR_COMPARE, text);
    }

    static boolean isVendorStats(String text) {
        return matches(VENDOR_STATS, text);
    }

    static Integer extractTopLimit(String text) {
        if (matches(TOP_1, text)) return 1;
        var m = TOP_N.matcher(text);
        if (m.find()) return Integer.parseInt(m.group(1));
        if (matches(ALL_VENDORS_RANKING, text)) return null;
        if (matches(VENDOR_RANKING, text)) return 5;
        return null;
    }

    static boolean isOutsideVendorScope(String text) {
        return (isRevenue(text) || isServiceCount(text))
                && (matches(PLATFORM, text) || matches(OTHER_VENDOR, text));
    }

    static boolean isServiceCount(String text) {
        return matches(SERVICE, text) && matches(COUNT, text);
    }

    static boolean isPublishedCount(String text) {
        return matches(PUBLISHED, text);
    }

    static boolean isPersonalBooking(String text) {
        return matches(CUSTOMER_BOOKING, text) && !matches(BOOKING_REPORT, text);
    }

    static boolean isDateQuestion(String text) {
        return matches(TODAY, text);
    }

    static boolean isPeriodFollowUp(String text) {
        return PERIOD_FOLLOW_UP.matcher(text).matches();
    }

    static TimeRange revenuePeriod(String original, String normalized, LocalDate today) {
        var dates = new ArrayList<LocalDate>();
        // Check if there is an explicit 4-digit year in original
        Integer contextYear = null;
        var yearMatcher = FOUR_DIGIT_YEAR.matcher(original);
        if (yearMatcher.find()) {
            try {
                contextYear = Integer.parseInt(yearMatcher.group(1));
            } catch (Exception ignored) {}
        }

        var matcher = DATE.matcher(original);
        while (matcher.find()) {
            String value = matcher.group();
            if (!value.contains("/")) {
                dates.add(LocalDate.parse(value));
            } else {
                long slashCount = value.chars().filter(ch -> ch == '/').count();
                String withYear;
                if (slashCount == 1) {
                    int effectiveYear = contextYear != null ? contextYear : today.getYear();
                    withYear = value + "/" + effectiveYear;
                } else {
                    withYear = value;
                }
                dates.add(LocalDate.parse(withYear, LOCAL_DATE));
            }
        }
        if (!dates.isEmpty()) {
            LocalDate from = dates.getFirst();
            LocalDate to = dates.getLast();
            if (from.isAfter(to)) {
                throw new IllegalArgumentException("Start date cannot be after end date");
            }
            return TimeRange.of(from, to);
        }

        var trailingDays = TRAILING_DAYS.matcher(normalized);
        if (trailingDays.find()) {
            int days = Integer.parseInt(trailingDays.group(1) != null ? trailingDays.group(1) : trailingDays.group(2));
            if (days < 1) throw new IllegalArgumentException("Report period must be positive");
            return TimeRange.of(today.minusDays(days - 1L), today);
        }

        var quarterMatcher = QUARTER.matcher(normalized);
        if (quarterMatcher.find()) {
            int q = Integer.parseInt(quarterMatcher.group(1));
            int y = quarterMatcher.group(2) != null ? Integer.parseInt(quarterMatcher.group(2)) : today.getYear();
            int startMonth = (q - 1) * 3 + 1;
            LocalDate start = LocalDate.of(y, startMonth, 1);
            LocalDate end = start.plusMonths(3).minusDays(1);
            return TimeRange.of(start, end);
        }

        var monthMatcher = SPECIFIC_MONTH.matcher(normalized);
        if (monthMatcher.find()) {
            int m = Integer.parseInt(monthMatcher.group(1));
            if (m >= 1 && m <= 12) {
                int y = monthMatcher.group(2) != null ? Integer.parseInt(monthMatcher.group(2)) : today.getYear();
                LocalDate start = LocalDate.of(y, m, 1);
                LocalDate end = start.plusMonths(1).minusDays(1);
                return TimeRange.of(start, end);
            }
        }

        var yearMatcherExplicit = SPECIFIC_YEAR.matcher(normalized);
        if (yearMatcherExplicit.find()) {
            int y = Integer.parseInt(yearMatcherExplicit.group(1));
            return TimeRange.of(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31));
        }

        if (matches(THIS_WEEK, normalized)) {
            LocalDate monday = today.with(java.time.DayOfWeek.MONDAY);
            return TimeRange.of(monday, today);
        }
        if (matches(LAST_WEEK, normalized)) {
            LocalDate prevMonday = today.minusWeeks(1).with(java.time.DayOfWeek.MONDAY);
            return TimeRange.of(prevMonday, prevMonday.plusDays(6));
        }
        if (matches(THIS_MONTH, normalized)) return TimeRange.of(today.withDayOfMonth(1), today);
        if (matches(LAST_MONTH, normalized)) {
            LocalDate end = today.withDayOfMonth(1).minusDays(1);
            return TimeRange.of(end.withDayOfMonth(1), end);
        }
        if (matches(TODAY_PERIOD, normalized)) return TimeRange.of(today, today);
        if (matches(YESTERDAY, normalized)) {
            LocalDate yesterday = today.minusDays(1);
            return TimeRange.of(yesterday, yesterday);
        }

        return TimeRange.of(today.minusDays(30), today);
    }

    static boolean matches(Pattern pattern, String text) {
        return pattern.matcher(text).find();
    }

    static Pattern words(String alternatives) {
        return Pattern.compile("\\b(?:" + alternatives + ")\\b");
    }
}
