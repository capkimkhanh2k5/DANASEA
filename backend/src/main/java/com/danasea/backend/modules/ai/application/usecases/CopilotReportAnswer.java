package com.danasea.backend.modules.ai.application.usecases;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;

import com.danasea.backend.modules.report.domain.models.TimeRange;
import com.fasterxml.jackson.databind.JsonNode;

final class CopilotReportAnswer {
    private static final DateTimeFormatter VI_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private CopilotReportAnswer() {}

    static String vendorRevenue(JsonNode data, TimeRange period, boolean english) {
        if (!data.path("gmv").isNumber() || !data.path("netPayout").isNumber()) return unavailable(english);
        String dates = dates(period, english);
        String gmv = money(data.path("gmv").decimalValue(), english);
        String payout = money(data.path("netPayout").decimalValue(), english);
        return english
                ? "From %s, your shop recorded %s in GMV and %s in estimated net payout.".formatted(dates, gmv, payout)
                : "Trong kỳ %s, gian hàng của bạn có GMV %s và thực thu dự kiến %s.".formatted(dates, gmv, payout);
    }

    static String adminRevenue(JsonNode data, TimeRange period, boolean english) {
        if (!data.isArray()) return unavailable(english);
        String dates = dates(period, english);
        String gmv = total(data, "gmv", english);
        String collected = total(data, "collectedCash", english);
        String refunds = total(data, "refunds", english);
        String commission = total(data, "commission", english);
        return english
                ? "Platform totals for %s. GMV %s, cash collected %s, refunds %s and platform commission %s."
                        .formatted(dates, gmv, collected, refunds, commission)
                : "Số liệu toàn sàn kỳ %s. GMV %s, tiền đã thu %s, hoàn tiền %s và hoa hồng nền tảng %s."
                        .formatted(dates, gmv, collected, refunds, commission);
    }

    static String compareAdminRevenue(JsonNode current, TimeRange currentPeriod, JsonNode previous,
                                      TimeRange previousPeriod, boolean english) {
        if (!current.isArray() || !previous.isArray()) return unavailable(english);
        BigDecimal currentCash = sum(current, "collectedCash");
        BigDecimal previousCash = sum(previous, "collectedCash");
        BigDecimal difference = currentCash.subtract(previousCash);
        String change = money(difference, english);
        if (difference.signum() > 0) change = "+" + change;
        return english
                ? "Cash collected was %s (%s) versus %s (%s). Difference: %s. The current month is in progress. The report does not establish the cause."
                        .formatted(money(currentCash, true), dates(currentPeriod, true), money(previousCash, true), dates(previousPeriod, true), change)
                : "Tiền đã thu là %s (%s), so với %s (%s). Chênh lệch %s. Tháng hiện tại chưa kết thúc. Báo cáo chưa đủ dữ liệu để kết luận nguyên nhân."
                        .formatted(money(currentCash, false), dates(currentPeriod, false), money(previousCash, false), dates(previousPeriod, false), change);
    }

    static String adminCancellations(JsonNode data, TimeRange period, boolean english) {
        if (!data.path("totalBookings").isNumber() || !data.path("totalCancelled").isNumber()) return unavailable(english);
        long all = data.path("totalBookings").asLong();
        long cancelled = data.path("totalCancelled").asLong();
        var formatter = NumberFormat.getPercentInstance(english ? Locale.US : Locale.forLanguageTag("vi-VN"));
        formatter.setMaximumFractionDigits(2);
        String share = all == 0 ? (english ? "no bookings" : "không có đơn") : formatter.format((double) cancelled / all);
        return english ? "%s had %d cancellations out of %d bookings (%s).".formatted(dates(period, true), cancelled, all, share)
                : "Kỳ %s có %d/%d đơn hủy (%s).".formatted(dates(period, false), cancelled, all, share);
    }

    static String adminQueue(JsonNode data, boolean english) {
        if (!data.path("pendingSupportRequestsCount").isIntegralNumber()) return unavailable(english);
        long support = data.path("pendingSupportRequestsCount").asLong();
        return english ? "%d support requests are waiting for review.".formatted(support)
                : "Hiện có %d yêu cầu hỗ trợ đang chờ xử lý.".formatted(support);
    }

    static String vendorServices(JsonNode data, boolean publishedOnly, boolean english) {
        if (!data.path("total").isIntegralNumber() || !data.path("byStatus").isObject()) return unavailable(english);
        long total = data.path("total").longValue();
        JsonNode statuses = data.path("byStatus");
        long published = statuses.path("PUBLISHED").asLong();
        long drafts = statuses.path("DRAFT").asLong();
        long pending = statuses.path("PENDING_REVIEW").asLong();
        long rejected = statuses.path("REJECTED").asLong();
        long paused = statuses.path("PAUSED").asLong();
        if (publishedOnly) {
            return english ? "Your shop has %d published services.".formatted(published)
                    : "Gian hàng của bạn có %d dịch vụ đã đăng.".formatted(published);
        }
        if (total == 0) return english ? "Your shop has 0 services." : "Gian hàng của bạn có 0 dịch vụ.";

        var breakdown = new ArrayList<String>();
        breakdown.add(english ? "%d published".formatted(published) : "%d đã đăng".formatted(published));
        if (drafts > 0) breakdown.add(english ? "%d drafts".formatted(drafts) : "%d bản nháp".formatted(drafts));
        if (pending > 0) breakdown.add(english ? "%d pending review".formatted(pending) : "%d chờ duyệt".formatted(pending));
        if (rejected > 0) breakdown.add(english ? "%d rejected".formatted(rejected) : "%d bị từ chối".formatted(rejected));
        if (paused > 0) breakdown.add(english ? "%d paused".formatted(paused) : "%d tạm dừng".formatted(paused));
        return english ? "Your shop has %d services (%s).".formatted(total, String.join(", ", breakdown))
                : "Gian hàng của bạn có %d dịch vụ (%s).".formatted(total, String.join(", ", breakdown));
    }

    static String unavailable(boolean english) {
        return english ? "I could not retrieve the report right now. Please try again."
                : "Tôi chưa tra cứu được dữ liệu lúc này. Vui lòng thử lại.";
    }

    private static String dates(TimeRange range, boolean english) {
        var format = english ? DateTimeFormatter.ISO_LOCAL_DATE : VI_DATE;
        return range.getFrom().format(format) + " – " + range.getTo().format(format);
    }

    private static String total(JsonNode rows, String field, boolean english) {
        return money(sum(rows, field), english);
    }

    private static BigDecimal sum(JsonNode rows, String field) {
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode row : rows) {
            sum = sum.add(row.path(field).decimalValue());
        }
        return sum;
    }

    private static String money(BigDecimal amount, boolean english) {
        var formatter = NumberFormat.getNumberInstance(english ? Locale.US : Locale.forLanguageTag("vi-VN"));
        formatter.setMaximumFractionDigits(Math.max(0, amount.stripTrailingZeros().scale()));
        return formatter.format(amount) + (english ? " VND" : " ₫");
    }
}
