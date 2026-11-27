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
import java.time.DayOfWeek;

final class CopilotRequest {
    private static final Pattern REVENUE = words("doanh thu|gmv|revenue|turnover");
    private static final Pattern METRIC_DEFINITION = Pattern.compile(
            "^(?:gmv la gi|doanh thu la gi|what is gmv|what does gmv mean)[?!.]*$");
    private static final Pattern PLATFORM = words("toan san|ca san|toan bo doi tac|tat ca doi tac|toan nen tang|toan he thong|cua (?:san )?danasea|"
            + "doanh thu he thong|gmv danasea|platform wide|platform revenue|platform gmv|across the platform|entire platform|all vendors");
    private static final Pattern OTHER_VENDOR = words("gian hang khac|doi tac khac|vendor khac|nha cung cap khac|doi thu|"
            + "another vendor|other vendors|another shop|competitor");
    private static final Pattern SERVICE = words("dich vu|services?");
    private static final Pattern COUNT = words("may|bao nhieu|tong|so luong|dem|how many|count|total|number of");
    private static final Pattern ATTACHED_CONTEXT_PREFIX = Pattern.compile(
            "^\\s*\\[(?:v[eê]|about)\\b(?:[^\\[\\]]|\\[[^\\[\\]]*\\])*\\]\\s*",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SERVICE_PRICE_QUERY = Pattern.compile(
            "(?i)\\b(?:gia bao nhieu|bao nhieu tien|gia ve|gia tour|gia dich vu|bang gia|muc gia|don gia|"
                    + "co gia|gia hien tai|gia niem yet|how much|what(?:\\s+is|'s)?\\s+the\\s+price|pricing|price\\s+of)\\b"
    );
    private static final Pattern SERVICE_COUNT_PHRASE = Pattern.compile(
            "(?i)\\b(?:may|bao nhieu|tong(?: so)?|so luong|dem|how many|number of|count|total)\\s+"
                    + "(?:cac\\s+|nhung\\s+|so\\s+|published\\s+|active\\s+|draft\\s+|paused\\s+|pending\\s+)?(?:dich vu|services?)\\b"
                    + "|\\b(?:dich vu|services?)\\b[^?!.,;]{0,40}\\b(?:la\\s+|co\\s+)?(?:bao nhieu|may)\\b"
                    + "(?!\\s*(?:tien|vnd|dong|ca|slots?|khach|nguoi|don|bookings?|orders?|danh gia|reviews?|sao)\\b)"
    );
    private static final Pattern NON_SERVICE_COUNT_OBJECT = Pattern.compile(
            "(?i)\\b(?:may|bao nhieu|so luong|tong(?: so)?|how many|number of|count)\\s+"
                    + "(?:ca|slots?|don|bookings?|orders?|khach|nguoi|danh gia|reviews?|luot|ve|tien)\\b"
    );
    private static final Pattern TOMORROW_PERIOD = words("ngay mai|tomorrow");
    private static final Pattern DAY_AFTER_TOMORROW = words("ngay kia|day after tomorrow");
    private static final Pattern PUBLISHED = words("da dang|dang cong khai|dang hoat dong|da xuat ban|published|active");
    private static final Pattern CUSTOMER_BOOKING = words("dat tour|dat ve|dat chuyen|dat dich vu|tim tour|tim dich vu|"
            + "goi y tour|tu van tour|len lich trinh|book a tour|book a service|find a tour|find services|"
            + "recommend a tour|plan an itinerary");
    private static final Pattern BOOKING_REPORT = words("luot dat|don dat|tinh hinh dat|thong ke|bao cao|booking metrics|"
            + "booking volume|booking count|how many|top|nhieu nhat|bao nhieu|ti le");
    private static final Pattern MODERATION_OR_OPERATIONAL = words(
            "bai dang|bai viet|mo ta|kiem duyet|duong link|link|so dien thoai|sdt|hotline|"
                    + "chuyen khoan|chuyen tien|ngoai san|ngoai danasea|ngoai nen tang|quang cao|"
                    + "vi pham|xu ly sao|listing|moderate|moderation|external link|outside danasea|off[- ]?platform");
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
    private static final Pattern THIS_YEAR = words(
            "ca nam|nam nay|trong nam|nam hien tai|ca nam nay|ca nam hien tai|this year|full year|whole year|current year|all year"
    );
    private static final Pattern LAST_YEAR = words(
            "nam ngoai|nam truoc|last year|previous year"
    );
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
            "^(?:con |the con |vay con |what about |and )?(?:rieng |just )?(?:hom nay|hom qua|thang nay|thang truoc|"
                    + "today|yesterday|this month|last month)(?: thi sao)?[?!.]*$");

    private static final Pattern VENDOR_RANKING = Pattern.compile(
            "(?i)\\b(?:top\\s*\\d*|xep hang|ranking|rank|dung dau|cao nhat|thap nhat|lon nhat|bac nhat)\\b"
    );
    private static final Pattern VENDOR_COMPARE = words("so sanh|compare|doi chieu");
    private static final Pattern VENDOR_STATS = words(
            "theo doi tac|tung doi tac|cac doi tac|tat ca doi tac|moi doi tac|toan bo doi tac|"
            + "theo vendor|tung vendor|cac vendor|tat ca vendor|moi vendor|toan bo vendor|"
            + "theo gian hang|tung gian hang|cac gian hang|tat ca gian hang|moi gian hang|toan bo gian hang"
    );
    private static final Pattern TOP_N = Pattern.compile(
            "(?i)\\b(?:top\\s*(\\d+)|(\\d+)\\s*(?:doi tac|vendor|gian hang|shop))\\b"
    );
    private static final Pattern TOP_1 = Pattern.compile(
            "(?i)\\b(?:ai|doi tac|gian hang|vendor|don vi)\\b.*\\b(?:cao nhat|lon nhat|thap nhat|be nhat|dung dau|dan dau|so 1|top 1)\\b|"
            + "\\b(?:cao nhat|lon nhat|thap nhat|be nhat|dung dau|dan dau|so 1|top 1)\\b.*\\b(?:ai|doi tac|gian hang|vendor|don vi)\\b"
    );
    private static final Pattern ALL_VENDORS = words(
            "toan bo|tat ca|all|moi doi tac|moi vendor|moi gian hang"
    );

    private CopilotRequest() {}

    static String stripAttachedContextPrefix(String text) {
        if (text == null || text.isBlank()) return "";
        String stripped = ATTACHED_CONTEXT_PREFIX.matcher(text).replaceFirst("").strip();
        return stripped.isBlank() ? text.strip() : stripped;
    }

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

    static boolean isAllVendors(String text) {
        return matches(ALL_VENDORS, text);
    }

    static Integer extractTopLimit(String text) {
        var m = TOP_N.matcher(text);
        if (m.find()) {
            String val = m.group(1) != null ? m.group(1) : m.group(2);
            if (val != null) {
                try {
                    int n = Integer.parseInt(val);
                    if (n > 0) return n;
                } catch (NumberFormatException ignored) {}
            }
        }
        if (matches(ALL_VENDORS, text)) return null;
        if (matches(TOP_1, text)) return 1;
        if (matches(VENDOR_RANKING, text)) return 5;
        return null;
    }

    static boolean isOutsideVendorScope(String text) {
        return (isRevenue(text) || isServiceCount(text) || matches(words("thong ke|so lieu|metrics|stats|bao cao"), text))
                && (matches(PLATFORM, text) || matches(OTHER_VENDOR, text));
    }

    static boolean isServicePrice(String text) {
        return matches(SERVICE_PRICE_QUERY, text);
    }

    static boolean isServiceCount(String text) {
        if (isServicePrice(text) || matches(NON_SERVICE_COUNT_OBJECT, text)) return false;
        return matches(SERVICE, text) && matches(COUNT, text) && matches(SERVICE_COUNT_PHRASE, text);
    }

    static LocalDate slotTargetDate(String original, String normalized, LocalDate today) {
        if (original != null) {
            var dates = new ArrayList<LocalDate>();
            var yearMatcher = FOUR_DIGIT_YEAR.matcher(original);
            Integer contextYear = null;
            if (yearMatcher.find()) {
                try {
                    contextYear = Integer.parseInt(yearMatcher.group(1));
                } catch (Exception ignored) {}
            }
            var matcher = DATE.matcher(original);
            while (matcher.find()) {
                String value = matcher.group();
                try {
                    if (!value.contains("/")) {
                        dates.add(LocalDate.parse(value));
                    } else {
                        long slashCount = value.chars().filter(ch -> ch == '/').count();
                        String withYear = slashCount == 1
                                ? value + "/" + (contextYear != null ? contextYear : today.getYear())
                                : value;
                        dates.add(LocalDate.parse(withYear, LOCAL_DATE));
                    }
                } catch (Exception ignored) {}
            }
            if (dates.size() == 1) return dates.getFirst();
        }
        if (normalized == null) return null;
        if (matches(DAY_AFTER_TOMORROW, normalized)) return today.plusDays(2);
        if (matches(TOMORROW_PERIOD, normalized)) return today.plusDays(1);
        if (matches(TODAY_PERIOD, normalized)) return today;
        return null;
    }

    static boolean isPublishedCount(String text) {
        return matches(PUBLISHED, text) && !matches(words("cho duyet|ngung ban|tam dung|pending|paused|draft|rejected"), text);
    }

    static boolean isPersonalBooking(String text) {
        return matches(CUSTOMER_BOOKING, text) && !matches(BOOKING_REPORT, text) && !matches(MODERATION_OR_OPERATIONAL, text);
    }

    static boolean isDateQuestion(String text) {
        return matches(TODAY, text);
    }

    static boolean isPeriodFollowUp(String text) {
        return PERIOD_FOLLOW_UP.matcher(text).matches();
    }

    static boolean comparesMonths(String text) {
        return THIS_MONTH.matcher(text).find() && LAST_MONTH.matcher(text).find()
                && matches(words("so sanh|chenh lech|compare|versus|vs"), text);
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

        if (matches(THIS_YEAR, normalized)) {
            int y = today.getYear();
            return TimeRange.of(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31));
        }
        if (matches(LAST_YEAR, normalized)) {
            int y = today.getYear() - 1;
            return TimeRange.of(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31));
        }

        if (matches(THIS_WEEK, normalized)) {
            LocalDate monday = today.with(DayOfWeek.MONDAY);
            return TimeRange.of(monday, today);
        }
        if (matches(LAST_WEEK, normalized)) {
            LocalDate prevMonday = today.minusWeeks(1).with(DayOfWeek.MONDAY);
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

        return TimeRange.of(today.minusDays(29), today);
    }

    static boolean matches(Pattern pattern, String text) {
        return pattern.matcher(text).find();
    }

    static Pattern words(String alternatives) {
        return Pattern.compile("\\b(?:" + alternatives + ")\\b");
    }
}
