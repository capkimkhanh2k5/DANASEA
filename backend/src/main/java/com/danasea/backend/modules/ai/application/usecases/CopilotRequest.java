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
import com.danasea.backend.shared.i18n.SupportedLanguage;

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
    public enum RelativeDateQuery {
        YESTERDAY(-1, false),
        TODAY(0, false),
        TOMORROW(1, false),
        DAY_AFTER_TOMORROW(2, false),
        CURRENT_TIME(0, true),
        CURRENT_TIME_AND_DATE(0, true);

        private final int dayOffset;
        private final boolean includesTime;

        RelativeDateQuery(int dayOffset, boolean includesTime) {
            this.dayOffset = dayOffset;
            this.includesTime = includesTime;
        }

        public int dayOffset() {
            return dayOffset;
        }

        public boolean includesTime() {
            return includesTime;
        }
    }

    private static final Pattern BUSINESS_METRIC_OR_ENTITY = words(
            "doanh thu|gmv|revenue|dich vu|service|services|don dat|booking|bookings|khach|khach hang|"
            + "don hang|don|suborder|gian hang|doi tac|vendor|vendors|slot|slots|cano|tau|thuyen|tour|tours|"
            + "thong ke|bao cao|metrics|chart|bang|top|xep hang|rank|ranking|huy|cancel|danh gia|review|"
            + "yeu cau|ho tro|support|tranh chap|dispute|rui ro|risk"
    );

    private static final Pattern CALENDAR_TODAY = Pattern.compile(
            "^(?:cho (?:toi|minh|em) (?:biet|hoi) |hoi |ban oi |ad oi )?"
            + "(?:hom nay|nay)"
            + "(?:\\s+la)?"
            + "(?:\\s+(?:ngay\\s+(?:may|gi|nao|bao nhieu)|thu\\s+may))"
            + "(?:\\s+(?:ngay\\s+(?:may|gi|nao|bao nhieu)|thu\\s+may))?$"
            + "|^what\\s+(?:is\\s+today\\s+s\\s+date|date\\s+is\\s+(?:it\\s+)?today|day\\s+is\\s+(?:it\\s+)?today|is\\s+the\\s+date\\s+today)$"
    );

    private static final Pattern CALENDAR_TOMORROW = Pattern.compile(
            "^(?:cho (?:toi|minh|em) (?:biet|hoi) |hoi |ban oi |ad oi )?"
            + "(?:ngay\\s+mai|mai)"
            + "(?:\\s+la)?"
            + "(?:\\s+(?:ngay\\s+(?:may|gi|nao|bao nhieu)|thu\\s+may))"
            + "(?:\\s+(?:ngay\\s+(?:may|gi|nao|bao nhieu)|thu\\s+may))?$"
            + "|^what\\s+(?:is\\s+tomorrow\\s+s\\s+date|date\\s+is\\s+(?:it\\s+)?tomorrow|day\\s+is\\s+(?:it\\s+)?tomorrow|is\\s+the\\s+date\\s+tomorrow)$"
    );

    private static final Pattern CALENDAR_YESTERDAY = Pattern.compile(
            "^(?:cho (?:toi|minh|em) (?:biet|hoi) |hoi |ban oi |ad oi )?"
            + "(?:hom\\s+qua|qua)"
            + "(?:\\s+la)?"
            + "(?:\\s+(?:ngay\\s+(?:may|gi|nao|bao nhieu)|thu\\s+may))"
            + "(?:\\s+(?:ngay\\s+(?:may|gi|nao|bao nhieu)|thu\\s+may))?$"
            + "|^what\\s+(?:was\\s+yesterday\\s+s\\s+date|date\\s+was\\s+yesterday|day\\s+was\\s+yesterday|was\\s+the\\s+date\\s+yesterday)$"
    );

    private static final Pattern CALENDAR_DAY_AFTER_TOMORROW = Pattern.compile(
            "^(?:cho (?:toi|minh|em) (?:biet|hoi) |hoi |ban oi |ad oi )?"
            + "(?:ngay\\s+)?(?:mot|kia)"
            + "(?:\\s+la)?"
            + "(?:\\s+(?:ngay\\s+(?:may|gi|nao|bao nhieu)|thu\\s+may))"
            + "(?:\\s+(?:ngay\\s+(?:may|gi|nao|bao nhieu)|thu\\s+may))?$"
            + "|^what\\s+(?:is|day\\s+is|date\\s+is)\\s+(?:the\\s+)?day\\s+after\\s+tomorrow$"
    );

    private static final Pattern TODAY = CALENDAR_TODAY;
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
    private static final Pattern THIS_QUARTER = words("quy nay|quy hien tai|this quarter|current quarter");
    private static final Pattern LAST_QUARTER = words("quy truoc|quy vua roi|quy vua qua|last quarter|previous quarter");
    private static final Pattern TOMORROW_PERIOD = words("ngay mai|tomorrow");
    private static final Pattern DAY_AFTER_TOMORROW_PERIOD = words("ngay mot|ngay kia|day after tomorrow");
    private static final Pattern NEXT_MONTH = words("thang sau|thang toi|next month");
    private static final Pattern NEXT_WEEK = words("tuan sau|tuan toi|next week");
    private static final Pattern NEXT_YEAR = words("nam sau|nam toi|next year");
    private static final Pattern TODAY_TO_TOMORROW = words("tu hom nay den ngay mai|from today to tomorrow");
    private static final Pattern FUTURE_KEYWORD = words(
            "ngay mai|mai|thang sau|nam sau|tuan sau|tuong lai|next month|next week|next year|tomorrow|"
            + "tu hom nay den|tu bay gio|tu nay den|from today to|from now to|from now"
    );
    private static final Pattern FROM_NOW = words("tu hom nay|tu bay gio|tu nay|from today|from now");
    private static final Pattern SPECIFIC_YEAR = Pattern.compile(
            "\\b(?:nam|year)\\s*(\\d{4})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_DAYS = Pattern.compile(
            "\\b(?:last (\\d{1,4}) days?|(?:trong )?(\\d{1,4}) ngay (?:qua|gan day|gan nhat))\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PERIOD_FOLLOW_UP = Pattern.compile(
            "^(?:con |the con |vay con |what about |and )?(?:rieng |just )?(?:hom nay|hom qua|thang nay|thang truoc|"
                    + "today|yesterday|this month|last month)(?: thi sao)?[?!.]*$");

    private static final Pattern VENDOR_RANKING = Pattern.compile(
            "(?i)\\b(?:top\\s*\\d*|xep hang|ranking|rank|dung dau|dan dau|cao nhat|thap nhat|lon nhat|bac nhat|"
            + "top\\s+earn(?:ing|er|ers)?|best\\s+performing|top\\s+revenue|leaderboards?)\\b"
    );
    private static final Pattern VENDOR_COMPARE = words("so sanh|compare|doi chieu");
    private static final Pattern VENDOR_STATS = words(
            "theo doi tac|tung doi tac|cac doi tac|tat ca doi tac|moi doi tac|toan bo doi tac|"
            + "theo vendor|tung vendor|cac vendor|tat ca vendor|moi vendor|toan bo vendor|"
            + "theo gian hang|tung gian hang|cac gian hang|tat ca gian hang|moi gian hang|toan bo gian hang"
    );
    private static final Pattern TOP_N = Pattern.compile(
            "(?i)\\b(?:top\\s*(\\d+)|(\\d+)\\s*(?:doi tac|vendors?|gian hang|shops?|partners?))\\b"
    );
    private static final Pattern TOP_1 = Pattern.compile(
            "(?i)\\b(?:ai|doi tac|gian hang|vendors?|don vi|who|which\\s+(?:vendor|shop|partner)|what\\s+(?:vendor|shop|partner)|shops?|partners?)\\b.*"
            + "\\b(?:cao nhat|lon nhat|thap nhat|be nhat|dung dau|dan dau|so 1|top 1|highest|lowest|top\\s+earn(?:ing|er|ers)?|most\\s+revenue|best\\s+performing|number\\s+1|no\\s+1|#1|leading)\\b|"
            + "\\b(?:cao nhat|lon nhat|thap nhat|be nhat|dung dau|dan dau|so 1|top 1|highest|lowest|top\\s+earn(?:ing|er|ers)?|most\\s+revenue|best\\s+performing|number\\s+1|no\\s+1|#1|leading)\\b.*"
            + "\\b(?:ai|doi tac|gian hang|vendors?|don vi|who|which\\s+(?:vendor|shop|partner)|what\\s+(?:vendor|shop|partner)|shops?|partners?)\\b"
    );
    private static final Pattern ALL_VENDORS = words(
            "toan bo|tat ca|all|moi doi tac|moi vendor|moi gian hang"
    );

    private static final Pattern VIETNAMESE_DIACRITICS = Pattern.compile(
            "[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ"
            + "ÀÁẠẢÃÂẦẤẬẨẪĂẰẮẶẲẴÈÉẸẺẼÊỀẾỆỂỄÌÍỊỈĨÒÓỌỎÕÔỒỐỘỔỖƠỜỚỢỞỠÙÚỤỦŨƯỪỨỰỬỮỲÝỴỶỸĐ]");

    private static final Pattern VIETNAMESE_WORDS = Pattern.compile(
            "\\b(?:doanh thu|toan san|ca san|hom nay|ngay mai|ngay mot|hom qua|thu may|nay thu may|"
            + "tinh hinh|hoat dong|doi tac|gian hang|huy don|khieu nai|tranh chap|hang doi|rui ro|ho tro|"
            + "tat ca|toan bo|bao nhieu|cho toi|tong quan|cao nhat|thap nhat|xep hang|so sanh|"
            + "tu ngay|den ngay|thang truoc|thang sau|thang nay|tuan truoc|tuan sau|tuan nay|"
            + "quy nay|quy truoc|quy sau|nam nay|nam truoc|nam sau|xin chao|bang may|la gi|nhung gi|"
            + "the nao|thi sao|co bao nhieu|con lai|chua|nhieu nhat|la bao nhieu|dung khong|co phai|"
            + "danh gia|canh bao|suc khoe|chuyen tien|chuyen khoan|ngoai san|khach hang|don hang|booking)\\b");

    private static final Pattern ENGLISH_WORDS = Pattern.compile(
            "\\b(?:what|which|how|who|when|where|why|is|are|was|were|could|would|should|will|"
            + "does|did|have|has|had|you|your|please|show|tell|give|list|highest|lowest|"
            + "ranking|diagnostics|health|cancellation|cancellations|dispute|disputes|overview|"
            + "dashboard|breakdown|today|tomorrow|yesterday|hello|hey|help|"
            + "earning|earner|seller|earns|sells)\\b");

    private CopilotRequest() {}

    static SupportedLanguage detectLanguage(String text, SupportedLanguage defaultLang) {
        if (text == null || text.isBlank()) {
            return defaultLang != null ? defaultLang : SupportedLanguage.VI;
        }
        if (VIETNAMESE_DIACRITICS.matcher(text).find()) {
            return SupportedLanguage.VI;
        }
        String norm = normalize(text);
        if (VIETNAMESE_WORDS.matcher(norm).find()) {
            return SupportedLanguage.VI;
        }
        if (ENGLISH_WORDS.matcher(norm).find()) {
            return SupportedLanguage.EN;
        }
        return defaultLang != null ? defaultLang : SupportedLanguage.VI;
    }

    static String normalize(String text) {
        return TravelQueryParser.normalize(text).replaceAll("[\\s-]+", " ").strip();
    }

    static boolean isRevenue(String text) {
        return matches(REVENUE, text) && !matches(METRIC_DEFINITION, text);
    }

    static boolean isVendorRanking(String text) {
        return matches(VENDOR_RANKING, text) || matches(TOP_1, text);
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

    static boolean isServiceCount(String text) {
        return matches(SERVICE, text) && matches(COUNT, text);
    }

    static boolean isPublishedCount(String text) {
        return matches(PUBLISHED, text) && !matches(words("cho duyet|ngung ban|tam dung|pending|paused|draft|rejected"), text);
    }

    static boolean isPersonalBooking(String text) {
        return matches(CUSTOMER_BOOKING, text) && !matches(BOOKING_REPORT, text) && !matches(MODERATION_OR_OPERATIONAL, text);
    }

    static RelativeDateQuery detectDateQuery(String text) {
        if (text == null || text.isBlank()) return null;
        String clean = normalize(text).replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
        if (matches(BUSINESS_METRIC_OR_ENTITY, clean) || isRevenue(clean) || isServiceCount(clean)) {
            return null;
        }
        boolean asksTime = matches(Pattern.compile("\\b(?:may gio|may gio roi|gio roi|what time|time is it)\\b"), clean)
                || (matches(Pattern.compile("\\b(?:bay gio|hien tai)\\b"), clean) && matches(Pattern.compile("\\b(?:may gio|gio)\\b"), clean));
        boolean asksDate = matches(Pattern.compile("\\b(?:ngay may|thu may|ngay gi|what date|what day)\\b"), clean);
        if (asksTime && asksDate) return RelativeDateQuery.CURRENT_TIME_AND_DATE;
        if (asksTime) return RelativeDateQuery.CURRENT_TIME;

        if (CALENDAR_TODAY.matcher(clean).find()) return RelativeDateQuery.TODAY;
        if (CALENDAR_TOMORROW.matcher(clean).find()) return RelativeDateQuery.TOMORROW;
        if (CALENDAR_YESTERDAY.matcher(clean).find()) return RelativeDateQuery.YESTERDAY;
        if (CALENDAR_DAY_AFTER_TOMORROW.matcher(clean).find()) return RelativeDateQuery.DAY_AFTER_TOMORROW;
        return null;
    }

    static boolean isDateQuestion(String text) {
        return detectDateQuery(text) != null;
    }

    static boolean hasFutureIntent(String text) {
        if (text == null || text.isBlank()) return false;
        String normalized = normalize(text);
        return matches(FUTURE_KEYWORD, normalized)
                || matches(FROM_NOW, normalized)
                || matches(TODAY_TO_TOMORROW, normalized)
                || matches(TOMORROW_PERIOD, normalized)
                || matches(NEXT_MONTH, normalized)
                || matches(NEXT_WEEK, normalized)
                || matches(NEXT_YEAR, normalized);
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
            LocalDate from;
            LocalDate to;
            if (dates.size() == 1 && matches(FROM_NOW, normalized)) {
                from = today;
                to = dates.getFirst();
            } else {
                from = dates.getFirst();
                to = dates.getLast();
            }
            if (from.isAfter(to)) {
                throw new IllegalArgumentException("Start date cannot be after end date");
            }
            return TimeRange.of(from, to);
        }

        if (matches(TODAY_TO_TOMORROW, normalized)) {
            return TimeRange.of(today, today.plusDays(1));
        }

        var trailingDays = TRAILING_DAYS.matcher(normalized);
        if (trailingDays.find()) {
            int days = Integer.parseInt(trailingDays.group(1) != null ? trailingDays.group(1) : trailingDays.group(2));
            if (days < 1) throw new IllegalArgumentException("Report period must be positive");
            return TimeRange.of(today.minusDays(days - 1L), today);
        }

        if (matches(THIS_QUARTER, normalized)) {
            int currentQ = (today.getMonthValue() - 1) / 3 + 1;
            int startMonth = (currentQ - 1) * 3 + 1;
            LocalDate start = LocalDate.of(today.getYear(), startMonth, 1);
            LocalDate quarterEnd = start.plusMonths(3).minusDays(1);
            LocalDate end = today.isBefore(quarterEnd) ? today : quarterEnd;
            return TimeRange.of(start, end);
        }

        if (matches(LAST_QUARTER, normalized)) {
            LocalDate prevQDate = today.minusMonths(3);
            int prevQ = (prevQDate.getMonthValue() - 1) / 3 + 1;
            int startMonth = (prevQ - 1) * 3 + 1;
            LocalDate start = LocalDate.of(prevQDate.getYear(), startMonth, 1);
            LocalDate end = start.plusMonths(3).minusDays(1);
            return TimeRange.of(start, end);
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
        if (matches(NEXT_YEAR, normalized)) {
            int y = today.getYear() + 1;
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
        if (matches(NEXT_WEEK, normalized)) {
            LocalDate nextMonday = today.plusWeeks(1).with(java.time.DayOfWeek.MONDAY);
            return TimeRange.of(nextMonday, nextMonday.plusDays(6));
        }
        if (matches(THIS_MONTH, normalized)) return TimeRange.of(today.withDayOfMonth(1), today);
        if (matches(LAST_MONTH, normalized)) {
            LocalDate end = today.withDayOfMonth(1).minusDays(1);
            return TimeRange.of(end.withDayOfMonth(1), end);
        }
        if (matches(NEXT_MONTH, normalized)) {
            LocalDate nextM = today.plusMonths(1);
            LocalDate start = nextM.withDayOfMonth(1);
            LocalDate end = start.plusMonths(1).minusDays(1);
            return TimeRange.of(start, end);
        }
        if (matches(TODAY_PERIOD, normalized)) return TimeRange.of(today, today);
        if (matches(YESTERDAY, normalized)) {
            LocalDate yesterday = today.minusDays(1);
            return TimeRange.of(yesterday, yesterday);
        }
        if (matches(TOMORROW_PERIOD, normalized)) {
            LocalDate tomorrow = today.plusDays(1);
            return TimeRange.of(tomorrow, tomorrow);
        }
        if (matches(DAY_AFTER_TOMORROW_PERIOD, normalized)) {
            LocalDate dayAfter = today.plusDays(2);
            return TimeRange.of(dayAfter, dayAfter);
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
