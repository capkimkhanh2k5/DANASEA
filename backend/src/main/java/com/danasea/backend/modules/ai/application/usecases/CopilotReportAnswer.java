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

    static String adminVendorDiagnostics(JsonNode data, TimeRange period, boolean english) {
        if (!data.isArray() || data.isEmpty()) {
            return english
                    ? "No vendor data recorded for the period %s.".formatted(dates(period, english))
                    : "Không có dữ liệu đối tác trong kỳ %s.".formatted(dates(period, english));
        }
        String dates = dates(period, english);
        int count = data.size();

        if (count == 1) {
            JsonNode item = data.get(0);
            String name = item.path("businessName").asText("Đối tác");
            String gmv = money(item.path("gmv").decimalValue(), english);
            long orders = item.path("totalBookings").asLong(0);
            return english
                    ? "In period %s, vendor %s recorded GMV of %s (%d booking(s)). See details below."
                            .formatted(dates, name, gmv, orders)
                    : "Trong kỳ %s, đối tác %s ghi nhận GMV %s (%d đơn hàng). Chi tiết xem bảng bên dưới."
                            .formatted(dates, name, gmv, orders);
        }

        if (count == 2) {
            JsonNode v1 = data.get(0);
            JsonNode v2 = data.get(1);
            return english
                    ? "Comparison for %s: %s (GMV %s, %d bookings) vs %s (GMV %s, %d bookings). See details below."
                            .formatted(dates,
                                    v1.path("businessName").asText(), money(v1.path("gmv").decimalValue(), english), v1.path("totalBookings").asLong(0),
                                    v2.path("businessName").asText(), money(v2.path("gmv").decimalValue(), english), v2.path("totalBookings").asLong(0))
                    : "So sánh doanh thu kỳ %s: %s đạt GMV %s (%d đơn) và %s đạt GMV %s (%d đơn). Chi tiết xem bảng bên dưới."
                            .formatted(dates,
                                    v1.path("businessName").asText(), money(v1.path("gmv").decimalValue(), english), v1.path("totalBookings").asLong(0),
                                    v2.path("businessName").asText(), money(v2.path("gmv").decimalValue(), english), v2.path("totalBookings").asLong(0));
        }

        JsonNode leader = data.get(0);
        String leaderName = leader.path("businessName").asText();
        String leaderGmv = money(leader.path("gmv").decimalValue(), english);
        return english
                ? "Revenue statistics for %d vendor(s) in period %s. Leading is %s with GMV of %s. See details below."
                        .formatted(count, dates, leaderName, leaderGmv)
                : "Thống kê doanh thu %d đối tác kỳ %s. Dẫn đầu là %s với GMV %s. Chi tiết xem bảng bên dưới."
                        .formatted(count, dates, leaderName, leaderGmv);
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
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode row : rows) {
            sum = sum.add(row.path(field).decimalValue());
        }
        return money(sum, english);
    }

    private static String money(BigDecimal amount, boolean english) {
        var formatter = NumberFormat.getNumberInstance(english ? Locale.US : Locale.forLanguageTag("vi-VN"));
        formatter.setMaximumFractionDigits(Math.max(0, amount.stripTrailingZeros().scale()));
        return formatter.format(amount) + (english ? " VND" : " ₫");
    }
}
