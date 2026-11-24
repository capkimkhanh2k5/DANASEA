package com.danasea.backend.modules.ai.application.usecases;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.danasea.backend.modules.report.domain.models.TimeRange;
import com.fasterxml.jackson.databind.JsonNode;

final class CopilotReportAnswer {
    private static final DateTimeFormatter VI_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final Set<String> CORE_REASON_KEYS = Set.of(
            "WEATHER", "VENDOR_FAULT", "CUSTOMER_CANCEL", "CUSTOMER_REQUEST", "ADMIN_OVERRIDE", "DISPUTE"
    );

    private static final Pattern ZERO_REVENUE_QUERY = CopilotRequest.words(
            "khong phat sinh doanh thu|ngay nao khong|khong co doanh thu|zero revenue|no revenue|days without revenue");
    private static final Pattern TIMEZONE_QUERY = CopilotRequest.words(
            "hom nay|today|gio viet nam|mui gio|timezone|utc|asia/ho_chi_minh");
    private static final Pattern VENDOR_PAYOUT_GAP_QUERY = CopilotRequest.words(
            "khac nhau|chenh lech|sau hoa hong|hoa hong|tai sao.*thap|quyet toan.*thap|hoan tien|"
                    + "khau tru|giam doanh thu|commission|deduction|refund|difference|payout");
    private static final Pattern REVIEW_TOPIC_WAITING = CopilotRequest.words(
            "cho|cano|tre|phan nan|che|thoi gian cho|cho doi|xuat ben|delay|delayed|wait|waiting|late|punctual");
    private static final Pattern DIAGNOSTICS_LOW_REVIEW_COUNT = Pattern.compile(
            "\\b2 danh gia\\b|\\b2 sao\\b|\\bratingcount\\b|du.*ket luan|khong du.*danh gia|minimum review|enough reviews");
    private static final Pattern DIAGNOSTICS_HIGH_OCC_LOW_RATING = Pattern.compile(
            "(?:lap day|occupancy|cong suat).*(?:danh gia thap|rating.*thap|sao.*thap|low rating)"
                    + "|(?:danh gia thap|low rating).*(?:lap day|occupancy)");
    private static final Pattern DIAGNOSTICS_THRESHOLD_RULE = CopilotRequest.words(
            "45%|40%|20%|nghiem trong|critical|warning|nguong|vi sao.*warning|why.*warning|why.*critical");
    private static final Pattern DIAGNOSTICS_SUSPENSION = CopilotRequest.words(
            "dinh chi|suspend|tu dong.*khoa|xu phat");
    private static final Pattern DIAGNOSTICS_IMPROVE = CopilotRequest.words(
            "cai thien|de xuat|khuyen nghi|improve|recommendation");
    private static final Pattern SLOT_UNSAFE_OR_UNKNOWN = CopilotRequest.words(
            "nguy hiem|chua co du lieu|khong ro|unsafe|high_risk|unknown|khong an toan|thoi tiet xau");
    private static final Pattern SLOT_LOW_OCCUPANCY = Pattern.compile(
            "1/20|it khach|cong suat thap|low occupancy|con trong|vang khach");
    private static final Pattern SLOT_HIGH_OCCUPANCY = Pattern.compile(
            "90%|95%|kin cho|mo them ca|tang suc chua|high demand|extra slot|expand capacity");
    private static final Pattern CANCELLATION_DETAIL_REQUEST = CopilotRequest.words(
            "phan loai|chia theo|nguyen nhan|ly do|nhieu nhat|top|reasons?|causes?|"
                    + "khach hang|vendor|thoi tiet|admin|hoan tien|refunds?|breakdown");

    private CopilotReportAnswer() {}

    static String vendorRevenue(JsonNode data, TimeRange period, boolean english) {
        return vendorRevenue(data, period, "", english);
    }

    static String vendorRevenue(JsonNode data, TimeRange period, String normalizedQuestion, boolean english) {
        if (!data.path("gmv").isNumber() || !data.path("netPayout").isNumber()) return unavailable(english);
        String dates = dates(period, english);
        BigDecimal gmvVal = data.path("gmv").decimalValue();
        BigDecimal payoutVal = data.path("netPayout").decimalValue();
        String gmv = money(gmvVal, english);
        String payout = money(payoutVal, english);

        StringBuilder sb = new StringBuilder();
        sb.append(english
                ? "From %s, your shop recorded %s in GMV and %s in estimated net payout.".formatted(dates, gmv, payout)
                : "Trong kỳ %s, gian hàng của bạn có GMV %s và thực thu dự kiến %s.".formatted(dates, gmv, payout));

        long totalBookings = data.path("totalBookings").asLong(0);
        long cancelledBookings = data.path("cancelledBookings").asLong(0);
        double cancelRate = data.path("cancellationRate").asDouble(0.0);
        if (data.has("totalBookings") || data.has("cancelledBookings")) {
            sb.append(english
                    ? " Orders: %d total, %d cancelled (%.2f%%).".formatted(totalBookings, cancelledBookings, cancelRate)
                    : " Tổng đơn: %d, đơn hủy: %d (%.2f%%).".formatted(totalBookings, cancelledBookings, cancelRate));
        }

        if (normalizedQuestion != null && CopilotRequest.matches(VENDOR_PAYOUT_GAP_QUERY, normalizedQuestion)) {
            BigDecimal gap = gmvVal.subtract(payoutVal);
            sb.append(english
                    ? "\nDifference (GMV - estimated net payout): %s. Net payout can be lower than GMV due to: "
                    .formatted(money(gap, true))
                    + "(1) platform commission per active commission rule, "
                    + "(2) refunded/cancelled bookings (%d cancelled in this period), and "
                    .formatted(cancelledBookings)
                    + "(3) settlement holds on orders with open disputes (ACTIVE_DISPUTE), active 7-day dispute windows (DISPUTE_WINDOW_OPEN), pending refunds (PENDING_REFUND), or disruptions (ACTIVE_DISRUPTION). "
                    + "Note: this summary metric view does not list individual refund/commission line items; check the order or settlement statement for transaction-level deductions."
                    : "\nChênh lệch (GMV - thực thu dự kiến): %s. Thực thu thấp hơn GMV có thể đến từ: "
                    .formatted(money(gap, false))
                    + "(1) hoa hồng nền tảng theo cấu hình/quy tắc áp dụng, "
                    + "(2) các đơn hủy/hoàn tiền (%d đơn hủy trong kỳ), và "
                    .formatted(cancelledBookings)
                    + "(3) khoản tạm giữ đối soát đối với đơn có khiếu nại đang mở (ACTIVE_DISPUTE), chưa hết cửa sổ khiếu nại 7 ngày (DISPUTE_WINDOW_OPEN), đang chờ hoàn tiền (PENDING_REFUND) hoặc có sự cố (ACTIVE_DISRUPTION). "
                    + "Lưu ý: báo cáo tổng hợp này chưa liệt kê từng giao dịch hoàn tiền hay từng dòng khấu trừ cụ thể; vui lòng xem chi tiết đơn hàng hoặc sao kê quyết toán để đối chiếu từng khoản.");
        }
        return sb.toString();
    }

    static String adminRevenue(JsonNode data, TimeRange period, boolean english) {
        return adminRevenue(data, period, "", english);
    }

    static String adminRevenue(JsonNode data, TimeRange period, String normalizedQuestion, boolean english) {
        if (!data.isArray()) return unavailable(english);
        String dates = dates(period, english);
        String gmv = total(data, "gmv", english);
        String collected = total(data, "collectedCash", english);
        String refunds = total(data, "refunds", english);
        String commission = total(data, "commission", english);

        StringBuilder sb = new StringBuilder();
        sb.append(english
                ? "Platform totals for %s. GMV %s, cash collected %s, refunds %s and platform commission %s."
                        .formatted(dates, gmv, collected, refunds, commission)
                : "Số liệu toàn sàn kỳ %s. GMV %s, tiền đã thu %s, hoàn tiền %s và hoa hồng nền tảng %s."
                        .formatted(dates, gmv, collected, refunds, commission));

        if (normalizedQuestion != null && CopilotRequest.matches(TIMEZONE_QUERY, normalizedQuestion)) {
            sb.append(english
                    ? "\nReporting timezone: Asia/Ho_Chi_Minh (UTC+7, Vietnam time). Queried period: %s.".formatted(dates)
                    : "\nMúi giờ báo cáo: Asia/Ho_Chi_Minh (UTC+7, giờ Việt Nam). Khoảng ngày truy vấn: %s.".formatted(dates));
        }

        if (normalizedQuestion != null && CopilotRequest.matches(ZERO_REVENUE_QUERY, normalizedQuestion)) {
            if (data.isEmpty()) {
                sb.append(english
                        ? "\nNo daily timeseries records were returned for %s.".formatted(dates)
                        : "\nKhông có dữ liệu chuỗi ngày nào trong kỳ %s.".formatted(dates));
            } else {
                List<String> zeroDates = new ArrayList<>();
                for (JsonNode row : data) {
                    BigDecimal rowGmv = row.path("gmv").decimalValue();
                    BigDecimal rowCash = row.path("collectedCash").decimalValue();
                    if (rowGmv.compareTo(BigDecimal.ZERO) == 0 && rowCash.compareTo(BigDecimal.ZERO) == 0) {
                        zeroDates.add(row.path("period").asText("?"));
                    }
                }
                if (zeroDates.isEmpty()) {
                    sb.append(english
                            ? "\nZero-revenue days in %s: none (all %d days recorded revenue).".formatted(dates, data.size())
                            : "\nNgày không phát sinh doanh thu trong kỳ %s: không có (cả %d ngày đều phát sinh doanh thu).".formatted(dates, data.size()));
                } else {
                    sb.append(english
                            ? "\nZero-revenue days (GMV = 0 and cash collected = 0) in %s (%d/%d days): %s."
                                    .formatted(dates, zeroDates.size(), data.size(), String.join(", ", zeroDates))
                            : "\nCác ngày không phát sinh doanh thu (GMV = 0 và tiền đã thu = 0) trong kỳ %s (%d/%d ngày): %s."
                                    .formatted(dates, zeroDates.size(), data.size(), String.join(", ", zeroDates)));
                }
            }
        }
        return sb.toString();
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
        return adminCancellations(data, period, "", english);
    }

    static String adminCancellations(JsonNode data, TimeRange period, String normalizedQuestion, boolean english) {
        if (!data.path("totalBookings").isNumber() || !data.path("totalCancelled").isNumber()) return unavailable(english);
        long all = data.path("totalBookings").asLong();
        long cancelled = data.path("totalCancelled").asLong();
        var formatter = NumberFormat.getPercentInstance(english ? Locale.US : Locale.forLanguageTag("vi-VN"));
        formatter.setMaximumFractionDigits(2);
        String share = all == 0 ? (english ? "no bookings" : "không có đơn") : formatter.format((double) cancelled / all);

        long weather = data.path("weatherCancellations").asLong(0);
        long vendorFault = data.path("vendorFaultCancellations").asLong(0);
        long customer = data.path("customerCancellations").asLong(0);
        long adminOverride = data.path("adminOverrideCancellations").asLong(0);
        long dispute = data.path("disputeCancellations").asLong(0);

        List<String> categoryParts = new ArrayList<>();
        categoryParts.add((english ? "Weather" : "Thời tiết") + ": " + weather);
        categoryParts.add((english ? "Vendor fault" : "Lỗi vendor") + ": " + vendorFault);
        categoryParts.add((english ? "Customer" : "Khách hàng") + ": " + customer);
        categoryParts.add((english ? "Admin override" : "Admin xử lý") + ": " + adminOverride);
        categoryParts.add((english ? "Dispute" : "Tranh chấp") + ": " + dispute);

        long accounted = weather + vendorFault + customer + adminOverride + dispute;
        Map<String, Long> byReason = new LinkedHashMap<>();
        JsonNode reasonNode = data.path("cancellationsByReason");
        if (reasonNode.isObject()) {
            reasonNode.fields().forEachRemaining(entry -> byReason.put(entry.getKey(), entry.getValue().asLong(0)));
        }

        for (var entry : byReason.entrySet()) {
            if (!CORE_REASON_KEYS.contains(entry.getKey()) && entry.getValue() > 0) {
                categoryParts.add(entry.getKey() + ": " + entry.getValue());
                accounted += entry.getValue();
            }
        }
        if (cancelled > accounted) {
            categoryParts.add((english ? "Other/Unclassified" : "Khác/Chưa phân loại") + ": " + (cancelled - accounted));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(english
                ? "%s had %d cancellations out of %d bookings (%s).".formatted(dates(period, true), cancelled, all, share)
                : "Kỳ %s có %d/%d đơn hủy (%s).".formatted(dates(period, false), cancelled, all, share));

        String q = normalizedQuestion != null ? normalizedQuestion : "";
        boolean asksDetail = CopilotRequest.matches(CANCELLATION_DETAIL_REQUEST, q);

        if (!asksDetail) {
            if (cancelled == 0) {
                sb.append(english
                        ? " No cancellation or refund records in this period. See the card below for details."
                        : " Chưa ghi nhận yêu cầu hủy/hoàn tiền nào trong kỳ này. Chi tiết xem thẻ bên dưới.");
            } else {
                sb.append(english
                        ? " See the breakdown card below for details by cause."
                        : " Chi tiết phân loại nguyên nhân xem thẻ bên dưới.");
            }
            return sb.toString();
        }

        sb.append(english
                ? "\nBreakdown by category: %s.".formatted(String.join(", ", categoryParts))
                : "\nPhân loại theo nhóm nguyên nhân: %s.".formatted(String.join(", ", categoryParts)));

        if (!byReason.isEmpty()) {
            List<String> rankedReasons = byReason.entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                            .thenComparing(Map.Entry.comparingByKey()))
                    .map(e -> e.getKey() + " (" + e.getValue() + ")")
                    .toList();
            if (!rankedReasons.isEmpty()) {
                sb.append(english
                        ? "\nTop cancellation/refund reasons: %s.".formatted(String.join(", ", rankedReasons))
                        : "\nCác lý do hủy/hoàn tiền phổ biến: %s.".formatted(String.join(", ", rankedReasons)));
            }
        } else if (cancelled == 0) {
            sb.append(english
                    ? "\nNo cancellation or refund reason records in this period."
                    : "\nChưa ghi nhận yêu cầu hủy/hoàn tiền nào trong kỳ này.");
        }
        sb.append(english ? "\nSee the card below for full details." : "\nChi tiết xem thẻ bên dưới.");
        return sb.toString();
    }

    static String adminQueue(JsonNode data, boolean english) {
        if (!data.path("pendingSupportRequestsCount").isIntegralNumber()) return unavailable(english);
        long support = data.path("pendingSupportRequestsCount").asLong();
        long risk = data.path("pendingRiskCasesCount").asLong(0);
        long disputes = data.path("openDisputesCount").asLong(0);
        return english
                ? "%d support requests are waiting for review. Queue overview: %d support/reschedule requests in WAITING_REVIEW, %d risk/moderation cases in NEEDS_REVIEW, and %d open disputes (OPEN/UNDER_REVIEW). This summary does not auto-approve or resolve any request."
                        .formatted(support, support, risk, disputes)
                : "Hiện có %d yêu cầu hỗ trợ đang chờ xử lý. Tổng quan hàng đợi: %d yêu cầu hỗ trợ/đổi lịch trạng thái WAITING_REVIEW chưa được phê duyệt, %d hồ sơ rủi ro/kiểm duyệt (NEEDS_REVIEW), và %d tranh chấp đang mở (OPEN/UNDER_REVIEW). Thao tác tra cứu không tự động phê duyệt hay đánh dấu đã giải quyết."
                        .formatted(support, support, risk, disputes);
    }

    static String adminVendorDiagnostics(JsonNode data, TimeRange period, String message, String normalized, boolean english) {
        if (!data.isArray()) return unavailable(english);
        String q = normalized != null ? normalized : "";

        boolean asksLowReviewCount = CopilotRequest.matches(DIAGNOSTICS_LOW_REVIEW_COUNT, q);
        boolean asksHighOccLowRating = CopilotRequest.matches(DIAGNOSTICS_HIGH_OCC_LOW_RATING, q);
        boolean asksThreshold = CopilotRequest.matches(DIAGNOSTICS_THRESHOLD_RULE, q);
        boolean asksSuspension = CopilotRequest.matches(DIAGNOSTICS_SUSPENSION, q);
        boolean asksImprove = CopilotRequest.matches(DIAGNOSTICS_IMPROVE, q);
        boolean hasDiagnosticIntent = asksLowReviewCount || asksHighOccLowRating || asksThreshold
                || asksSuspension || asksImprove;
        boolean hasRevenueOrRankingIntent = CopilotRequest.isRevenue(q)
                || CopilotRequest.isVendorRanking(q)
                || CopilotRequest.isVendorCompare(q)
                || CopilotRequest.isVendorStats(q);

        if (hasRevenueOrRankingIntent && !hasDiagnosticIntent && period != null) {
            return adminVendorDiagnostics(data, period, english);
        }
        return adminVendorDiagnostics(data, message, normalized, english);
    }

    static String adminVendorDiagnostics(JsonNode data, String message, String normalized, boolean english) {
        if (!data.isArray()) return unavailable(english);
        String q = normalized != null ? normalized : "";
        StringBuilder sb = new StringBuilder();

        boolean asksLowReviewCount = CopilotRequest.matches(DIAGNOSTICS_LOW_REVIEW_COUNT, q);
        boolean asksHighOccLowRating = CopilotRequest.matches(DIAGNOSTICS_HIGH_OCC_LOW_RATING, q);
        boolean asksThreshold = CopilotRequest.matches(DIAGNOSTICS_THRESHOLD_RULE, q);
        boolean asksSuspension = CopilotRequest.matches(DIAGNOSTICS_SUSPENSION, q);
        boolean asksImprove = CopilotRequest.matches(DIAGNOSTICS_IMPROVE, q);

        if (asksSuspension) {
            sb.append(english
                    ? "NO. AI diagnostics and risk alerts only mark items for review (NEEDS_REVIEW / WARNING / CRITICAL); administrators must never auto-suspend or penalize a vendor solely on AI scoring without manual evidence verification.\n"
                    : "KHÔNG. Các cảnh báo chẩn đoán hoặc điểm rủi ro AI chỉ là tín hiệu cần xem xét (NEEDS_REVIEW / WARNING / CRITICAL), tuyệt đối không tự động đình chỉ (suspend) hay xử phạt đối tác nếu Admin chưa xác minh bằng chứng thực tế.\n");
        }

        if (asksLowReviewCount) {
            sb.append(english
                    ? "Insufficient sample size under diagnostic rules: LOW_RATING requires rating < 3.5 AND ratingCount >= 3 (WARNING), while CRITICAL requires rating < 2.5 AND ratingCount >= 5. Therefore, 2 reviews averaging 2.0 stars do NOT meet the minimum count (ratingCount < 3) to conclude poor quality.\n"
                    : "Chưa đủ căn cứ kết luận theo quy tắc chẩn đoán: cảnh báo LOW_RATING yêu cầu điểm < 3.5 VÀ tối thiểu 3 đánh giá (ratingCount >= 3) cho mức WARNING, còn mức CRITICAL yêu cầu điểm < 2.5 VÀ tối thiểu 5 đánh giá (ratingCount >= 5). Vì vậy, vendor chỉ có 2 đánh giá 2.0 sao chưa đạt ngưỡng tối thiểu để kết luận chất lượng kém.\n");
        }

        if (asksThreshold) {
            sb.append(english
                    ? "Diagnostic threshold rules (VendorPerformanceItem): HIGH_CANCELLATION triggers WARNING when cancellationRate > 20.0%, and triggers CRITICAL when cancellationRate > 40.0% (boundary: exactly 40.0% is WARNING; > 40.0% such as 45% triggers CRITICAL). LOW_RATING triggers WARNING when rating < 3.5 and ratingCount >= 3, or CRITICAL when rating < 2.5 and ratingCount >= 5. OVERLOADED: slotOccupancyRate >= 95.0%; UNDERPERFORMING: slotOccupancyRate < 20.0% and totalOrders < 5.\n"
                    : "Quy tắc ngưỡng cảnh báo (VendorPerformanceItem): HIGH_CANCELLATION bật WARNING khi tỷ lệ hủy > 20.0% và bật CRITICAL (nghiêm trọng) khi tỷ lệ hủy > 40.0% (ranh giới: đúng 40.0% chỉ ở mức WARNING; vượt 40.0% như 45% sẽ kích hoạt CRITICAL). LOW_RATING bật WARNING khi rating < 3.5 và ratingCount >= 3, bật CRITICAL khi rating < 2.5 và ratingCount >= 5. OVERLOADED: lấp đầy >= 95.0%; UNDERPERFORMING: lấp đầy < 20.0% và tổng đơn < 5.\n");
        }

        if (asksImprove) {
            sb.append(english
                    ? "Improvement guidance must distinguish cancellation causes: WEATHER cancellations require strict adherence to marine safety alerts and timely rescheduling/full refunds, whereas VENDOR_FAULT cancellations require slot capacity control, equipment maintenance, and crew readiness.\n"
                    : "Đề xuất cải thiện cần phân biệt nguyên nhân hủy: hủy do thời tiết (WEATHER) cần tuân thủ cảnh báo an toàn biển và chủ động đổi lịch/hoàn tiền chuẩn; hủy do lỗi đối tác (VENDOR_FAULT) cần rà soát giới hạn nhận slot, bảo trì tàu/cano và chuẩn bị nhân sự dự phòng.\n");
        }

        List<JsonNode> matchedByName = new ArrayList<>();
        List<JsonNode> highOccLowRating = new ArrayList<>();
        List<JsonNode> highCancel = new ArrayList<>();
        List<JsonNode> flagged = new ArrayList<>();

        for (JsonNode item : data) {
            String name = item.path("businessName").asText("");
            String normName = CopilotRequest.normalize(name);
            if (!normName.isBlank() && q.contains(normName)) {
                matchedByName.add(item);
            }
            double cancelRate = item.path("cancellationRate").asDouble(0.0);
            double occRate = item.path("slotOccupancyRate").asDouble(0.0);
            BigDecimal rating = item.path("ratingAvg").decimalValue();
            int ratingCount = item.path("ratingCount").asInt(0);
            String severity = item.path("severity").asText("HEALTHY");
            if (!"HEALTHY".equals(severity)) {
                flagged.add(item);
            }
            if (cancelRate > 20.0) {
                highCancel.add(item);
            }
            if (occRate >= 85.0 && rating.compareTo(BigDecimal.valueOf(3.5)) < 0 && ratingCount >= 3) {
                highOccLowRating.add(item);
            }
        }

        if (asksHighOccLowRating) {
            if (highOccLowRating.isEmpty()) {
                sb.append(english
                        ? "Live diagnostics: 0 vendors currently match both high occupancy (>= 85%) and verified low rating (< 3.5 with >= 3 reviews) out of %d evaluated vendor(s)."
                                .formatted(data.size())
                        : "Dữ liệu hiện tại: 0 đối tác đồng thời có tỷ lệ lấp đầy cao (>= 85%%) và điểm đánh giá thấp (< 3.5 với >= 3 đánh giá) trong số %d đối tác được kiểm tra."
                                .formatted(data.size()));
            } else {
                sb.append(english
                        ? "Vendors with high occupancy and low rating (%d): ".formatted(highOccLowRating.size())
                        : "Đối tác có tỷ lệ lấp đầy cao nhưng đánh giá thấp (%d): ".formatted(highOccLowRating.size()));
                sb.append(formatVendorList(highOccLowRating, english));
            }
            return sb.toString().strip();
        }

        if (!matchedByName.isEmpty()) {
            sb.append(english ? "Matched vendor diagnostics: " : "Số liệu đối tác được nhắc tới: ");
            sb.append(formatVendorList(matchedByName, english));
            sb.append(english ? ". See the table below for details." : ". Chi tiết xem bảng bên dưới.");
            return sb.toString().strip();
        }

        if (asksImprove) {
            if (highCancel.isEmpty()) {
                sb.append(english
                        ? "Live diagnostics: 0 vendors currently exceed the 20.0%% cancellation threshold across %d evaluated vendor(s)."
                                .formatted(data.size())
                        : "Dữ liệu hiện tại: 0 đối tác vượt ngưỡng hủy 20.0%% trong số %d đối tác.".formatted(data.size()));
            } else {
                sb.append(english ? "Vendors with a high cancellation rate (over 20%): " : "Đối tác có tỷ lệ hủy cao (trên 20%): ");
                highCancel.sort(Comparator.comparingDouble((JsonNode i) -> i.path("cancellationRate").asDouble(0.0)).reversed());
                sb.append(formatVendorList(highCancel, english));
            }
            return sb.toString().strip();
        }

        if (!flagged.isEmpty()) {
            sb.append(flaggedSummary(flagged, data.size(), english));
        } else if (data.isEmpty()) {
            sb.append(english
                    ? "Live diagnostics: no vendor performance records in this period."
                    : "Dữ liệu hiện tại: không có bản ghi hiệu suất đối tác trong kỳ này.");
        } else {
            sb.append(english
                    ? "All %d evaluated vendor(s) are currently stable with no threshold alerts. See the table below for details."
                            .formatted(data.size())
                    : "Toàn bộ %d đối tác hiện đang hoạt động ổn định, chưa ghi nhận cảnh báo vượt ngưỡng. Chi tiết xem bảng bên dưới."
                            .formatted(data.size()));
        }
        return sb.toString().strip();
    }

    private static final int MAX_NAMED_VENDORS = 3;

    private static String severityLabel(String severity, boolean english) {
        return switch (severity) {
            case "CRITICAL" -> english ? "critical" : "nghiêm trọng";
            case "WARNING" -> english ? "needs attention" : "cần lưu ý";
            default -> english ? "stable" : "ổn định";
        };
    }

    /** Short natural-language list of vendor names; details live in the card, not in the message. */
    private static String vendorNames(List<JsonNode> items, boolean english) {
        List<String> names = items.stream().limit(MAX_NAMED_VENDORS)
                .map(item -> item.path("businessName").asText("Vendor")).toList();
        String joined = String.join(", ", names);
        int rest = items.size() - names.size();
        if (rest <= 0) return joined;
        return english ? joined + " and " + rest + " more" : joined + " và " + rest + " đối tác khác";
    }

    /** Human-readable one-liner for explicitly requested vendors (no raw enum/field dump). */
    private static String formatVendorList(List<JsonNode> items, boolean english) {
        List<String> lines = new ArrayList<>();
        for (JsonNode item : items.stream().limit(MAX_NAMED_VENDORS).toList()) {
            String name = item.path("businessName").asText("Vendor");
            String severity = severityLabel(item.path("severity").asText("HEALTHY"), english);
            double cancel = item.path("cancellationRate").asDouble(0.0);
            double occ = item.path("slotOccupancyRate").asDouble(0.0);
            BigDecimal rating = item.path("ratingAvg").decimalValue();
            int ratingCount = item.path("ratingCount").asInt(0);
            String ratingText = ratingCount == 0
                    ? (english ? "no reviews yet" : "chưa có đánh giá")
                    : (english ? "%s stars (%d reviews)" : "%s sao (%d lượt)")
                            .formatted(rating.stripTrailingZeros().toPlainString(), ratingCount);
            lines.add(english
                    ? "%s - %s: cancellation %.1f%%, occupancy %.1f%%, %s"
                            .formatted(name, severity, cancel, occ, ratingText)
                    : "%s - %s: tỷ lệ hủy %.1f%%, lấp đầy %.1f%%, %s"
                            .formatted(name, severity, cancel, occ, ratingText));
        }
        int rest = items.size() - lines.size();
        if (rest > 0) lines.add(english ? "and " + rest + " more (see the table below)" : "và " + rest + " đối tác khác (xem bảng bên dưới)");
        return String.join("; ", lines);
    }

    private static String flaggedSummary(List<JsonNode> flagged, int total, boolean english) {
        long critical = flagged.stream().filter(i -> "CRITICAL".equals(i.path("severity").asText())).count();
        long warning = flagged.size() - critical;
        List<String> breakdown = new ArrayList<>();
        if (critical > 0) breakdown.add(english ? critical + " critical" : critical + " nghiêm trọng");
        if (warning > 0) breakdown.add(english ? warning + " to monitor" : warning + " cần theo dõi");
        String breakdownText = breakdown.isEmpty() ? "" : " (" + String.join(", ", breakdown) + ")";

        List<JsonNode> ranked = flagged.stream()
                .sorted(Comparator.comparingInt((JsonNode i) -> "CRITICAL".equals(i.path("severity").asText()) ? 0 : 1)
                        .thenComparing((JsonNode i) -> -i.path("cancellationRate").asDouble(0.0)))
                .toList();
        String names = vendorNames(ranked, english);
        return english
                ? "%d/%d vendors need attention%s. Most notable: %s. See the table below for details."
                        .formatted(flagged.size(), total, breakdownText, names)
                : "Có %d/%d đối tác cần lưu ý%s. Đáng chú ý nhất: %s. Chi tiết từng chỉ số xem bảng bên dưới."
                        .formatted(flagged.size(), total, breakdownText, names);
    }

    static String vendorReviewCoaching(JsonNode data, String normalizedQuestion, boolean english) {
        int totalAnalyzed = data.path("totalReviewsAnalyzed").asInt(0);
        if (totalAnalyzed == 0) {
            return english
                    ? "This service/scope currently has 0 verified customer reviews (totalReviewsAnalyzed = 0). I cannot evaluate quality, fabricate ratings, or use reviews from other services."
                    : "Dịch vụ/phạm vi này hiện có 0 đánh giá (totalReviewsAnalyzed = 0). AI không đủ dữ liệu để nhận xét chất lượng và không được tạo đánh giá giả hay lấy điểm của dịch vụ khác thay thế.";
        }

        String avg = data.path("averageRating").decimalValue().stripTrailingZeros().toPlainString();
        String q = normalizedQuestion != null ? normalizedQuestion : "";
        boolean topicWaiting = CopilotRequest.matches(REVIEW_TOPIC_WAITING, q);

        Set<String> seenQuotes = new LinkedHashSet<>();
        List<String> allQuotes = new ArrayList<>();
        List<String> topicQuotes = new ArrayList<>();
        List<String> pillarLines = new ArrayList<>();

        JsonNode pillarsNode = data.path("pillars");
        for (String key : List.of("GUIDE", "SAFETY", "PUNCTUALITY", "VALUE")) {
            JsonNode pillar = pillarsNode.path(key);
            if (!pillar.isMissingNode()) {
                pillarLines.add("%s: %d/10 (%s)".formatted(
                        pillarLabel(key, english),
                        pillar.path("scoreOutOf10").asInt(0),
                        sentimentLabel(pillar.path("sentiment").asText("NEUTRAL"), english)));
                collectQuotes(pillar.path("negativeQuotes"), key, seenQuotes, allQuotes, topicQuotes);
                collectQuotes(pillar.path("positiveQuotes"), key, seenQuotes, allQuotes, topicQuotes);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(english
                ? "Analyzed %d verified review(s) (average rating %s/5.0)."
                        .formatted(totalAnalyzed, avg)
                : "Đã phân tích %d đánh giá đã xác minh (điểm trung bình %s/5.0)."
                        .formatted(totalAnalyzed, avg));
        if (totalAnalyzed < 5) {
            sb.append(english
                    ? " Sample limitation: only %d review(s) available, so findings are preliminary."
                            .formatted(totalAnalyzed)
                    : " Lưu ý giới hạn mẫu: cỡ mẫu còn nhỏ (%d đánh giá), chưa đủ đại diện toàn bộ vận hành."
                            .formatted(totalAnalyzed));
        }

        if (!pillarLines.isEmpty()) {
            sb.append(english
                    ? "\n4-pillar overview: %s.".formatted(String.join(", ", pillarLines))
                    : "\nTổng hợp 4 trụ cột: %s.".formatted(String.join(", ", pillarLines)));
        }

        if (!topicWaiting) {
            String roadmap = data.path("badgeProgression").path("roadmapNote").asText("");
            if (!roadmap.isBlank()) {
                sb.append(english ? "\nBadge roadmap (non-guaranteed): " : "\nLộ trình huy hiệu (không cam kết chắc chắn): ").append(roadmap);
            }
            sb.append(english
                    ? "\nSee the review coaching card below for pillar details and action recommendations."
                    : "\nChi tiết điểm từng trụ cột và khuyến nghị cải thiện xem thẻ bên dưới.");
            return sb.toString();
        }

        sb.append(english
                ? "\n\nVerified Review Evidence:"
                : "\n\nBằng chứng từ đánh giá đã xác minh:");
        List<String> cited = topicQuotes;
        if (cited.isEmpty()) {
            sb.append(english
                    ? "\n- Found 0 matching quotes for this topic out of %d analyzed review(s).".formatted(totalAnalyzed)
                    : "\n- Tìm thấy 0 trích dẫn nhắc trực tiếp chủ đề này trong tổng số %d đánh giá đã phân tích.".formatted(totalAnalyzed));
        } else {
            for (String line : cited.stream().limit(5).toList()) {
                sb.append("\n- ").append(line);
            }
        }

        sb.append(english
                ? "\n\nOperational Hypotheses Requiring Verification (unconfirmed by system data):"
                : "\n\nGiả thuyết vận hành cần kiểm chứng thêm (chưa xác nhận từ dữ liệu hệ thống):");
        sb.append(english
                ? "\n- Possible operational factors to verify against pier/dispatch logs (not confirmed from telemetry): check-in buffer between slots, boat/cano turnaround scheduling, and guest delay notifications."
                : "\n- Các yếu tố vận hành cần đối chiếu thêm từ nhật ký bến/điều phối (chưa xác nhận từ dữ liệu hệ thống): thời gian đệm giữa các ca xuất bến, năng lực quay vòng cano giờ cao điểm, và quy trình báo trước khi trễ giờ.");
        String roadmap = data.path("badgeProgression").path("roadmapNote").asText("");
        if (!roadmap.isBlank()) {
            sb.append(english ? "\nBadge roadmap (non-guaranteed): " : "\nLộ trình huy hiệu (không cam kết chắc chắn): ").append(roadmap);
        }
        sb.append(english
                ? "\nSee the card below for full pillar scores and recommendations."
                : "\nChi tiết các khuyến nghị khắc phục xem thẻ bên dưới.");
        return sb.toString();
    }

    private static String pillarLabel(String key, boolean english) {
        return switch (key) {
            case "GUIDE" -> english ? "Guide" : "Hướng dẫn viên";
            case "SAFETY" -> english ? "Safety" : "An toàn";
            case "PUNCTUALITY" -> english ? "Punctuality" : "Đúng giờ";
            case "VALUE" -> english ? "Value" : "Giá trị";
            default -> key;
        };
    }

    private static String sentimentLabel(String sentiment, boolean english) {
        return switch (sentiment) {
            case "POSITIVE" -> english ? "positive" : "tích cực";
            case "NEGATIVE" -> english ? "needs improvement" : "cần cải thiện";
            case "NO_DATA" -> english ? "no data" : "chưa có dữ liệu";
            default -> english ? "neutral" : "trung lập";
        };
    }

    private static void collectQuotes(JsonNode quotesNode, String pillar, Set<String> seen,
                                      List<String> allQuotes, List<String> topicQuotes) {
        if (!quotesNode.isArray()) return;
        for (JsonNode q : quotesNode) {
            String reviewId = q.path("reviewId").asText("");
            int rating = q.path("rating").asInt(0);
            String exact = q.path("exactQuote").asText("");
            if (exact.isBlank()) continue;
            String dedupKey = reviewId + "|" + exact;
            String formatted = "[reviewId: %s | %d/5 | %s]: \"%s\"".formatted(reviewId, rating, pillar, exact);
            if (seen.add(dedupKey)) {
                allQuotes.add(formatted);
            }
            String normQuote = CopilotRequest.normalize(exact);
            if (("PUNCTUALITY".equals(pillar) || CopilotRequest.matches(REVIEW_TOPIC_WAITING, normQuote))
                    && !topicQuotes.contains(formatted)) {
                topicQuotes.add(formatted);
            }
        }
    }

    static String vendorSlotAdvisor(JsonNode data, String normalizedQuestion, boolean english) {
        return vendorSlotAdvisor(data, normalizedQuestion, null, english);
    }

    static String vendorSlotAdvisor(JsonNode data, String normalizedQuestion, LocalDate targetDate, boolean english) {
        if (!data.has("totalSlotsEvaluated")) return unavailable(english);
        int evaluated = data.path("totalSlotsEvaluated").asInt(0);
        long highDemand = data.path("highDemandSlotsCount").asLong(0);
        long lowDemand = data.path("lowDemandSlotsCount").asLong(0);
        long weatherRisk = data.path("weatherRiskSlotsCount").asLong(0);
        String q = normalizedQuestion != null ? normalizedQuestion : "";

        StringBuilder sb = new StringBuilder();
        if (targetDate != null) {
            String dateLabel = targetDate.format(english ? DateTimeFormatter.ISO_LOCAL_DATE : VI_DATE);
            if (evaluated == 0) {
                return english
                        ? "There are currently 0 scheduled slots on %s for this scope. See the advisory card below for details."
                                .formatted(dateLabel)
                        : "Ngày %s hiện có 0 ca hoạt động được mở cho phạm vi tra cứu này. Chi tiết xem thẻ bên dưới."
                                .formatted(dateLabel);
            }
            sb.append(english
                    ? "On %s, there are %d scheduled slot(s) (high demand: %d, low demand: %d, weather watch/risk: %d)."
                            .formatted(dateLabel, evaluated, highDemand, lowDemand, weatherRisk)
                    : "Ngày %s có %d ca hoạt động (nhu cầu cao: %d, công suất thấp: %d, cần lưu ý thời tiết: %d)."
                            .formatted(dateLabel, evaluated, highDemand, lowDemand, weatherRisk));
        } else {
            sb.append(english
                    ? "Evaluated %d upcoming slot(s) (high demand: %d, low demand: %d, weather watch/risk: %d)."
                            .formatted(evaluated, highDemand, lowDemand, weatherRisk)
                    : "Đã kiểm tra %d ca sắp tới (nhu cầu cao: %d, công suất thấp: %d, cần lưu ý thời tiết: %d)."
                            .formatted(evaluated, highDemand, lowDemand, weatherRisk));
        }

        boolean hasRiskyOrUnknownSlot = false;
        JsonNode recs = data.path("recommendations");
        if (recs.isArray() && !recs.isEmpty()) {
            List<JsonNode> ordered = new ArrayList<>();
            recs.forEach(ordered::add);
            if (CopilotRequest.matches(SLOT_HIGH_OCCUPANCY, q)) {
                ordered.sort(Comparator.comparingDouble((JsonNode r) -> r.path("occupancyRate").asDouble(0.0)).reversed());
            } else if (CopilotRequest.matches(SLOT_LOW_OCCUPANCY, q)) {
                ordered.sort(Comparator
                        .comparingInt((JsonNode r) -> {
                            int booked = r.path("bookedCount").asInt(0);
                            double occ = r.path("occupancyRate").asDouble(0.0);
                            return (booked > 0 && occ < 20.0) ? 0 : (occ < 20.0 ? 1 : 2);
                        })
                        .thenComparingInt(r -> r.path("bookedCount").asInt(0))
                        .thenComparingDouble(r -> r.path("occupancyRate").asDouble(0.0)));
            }
            List<String> lines = new ArrayList<>();
            int count = 0;
            for (JsonNode rec : ordered) {
                String weatherLevel = rec.path("weatherRiskLevel").asText("UNKNOWN");
                if ("HIGH_RISK".equals(weatherLevel) || "UNKNOWN".equals(weatherLevel) || "MODERATE".equals(weatherLevel)) {
                    hasRiskyOrUnknownSlot = true;
                }
                if (count++ < 3) {
                    String dateStr = formatJsonDate(rec.path("date"));
                    String timeStr = formatJsonTime(rec.path("startTime"));
                    lines.add(english
                            ? "- %s (%s %s): %d/%d (%.1f%%) — weather: %s, suggestion: %s."
                                    .formatted(
                                            rec.path("serviceTitle").asText("Service"),
                                            dateStr,
                                            timeStr,
                                            rec.path("bookedCount").asInt(0),
                                            rec.path("capacity").asInt(0),
                                            rec.path("occupancyRate").asDouble(0.0),
                                            weatherLabel(weatherLevel, true),
                                            slotRecommendationLabel(rec.path("recommendationType").asText(""), true))
                            : "- %s (%s %s): %d/%d (%.1f%%) — thời tiết %s, gợi ý %s."
                                    .formatted(
                                            rec.path("serviceTitle").asText("Dịch vụ"),
                                            dateStr,
                                            timeStr,
                                            rec.path("bookedCount").asInt(0),
                                            rec.path("capacity").asInt(0),
                                            rec.path("occupancyRate").asDouble(0.0),
                                            weatherLabel(weatherLevel, false),
                                            slotRecommendationLabel(rec.path("recommendationType").asText(""), false)));
                }
            }
            sb.append("\n").append(String.join("\n", lines));
            if (ordered.size() > 3) {
                sb.append(english
                        ? "\n... and %d more slot(s). See the advisory card below for details.".formatted(ordered.size() - 3)
                        : "\n... và %d ca khác. Chi tiết từng ca xem thẻ bên dưới.".formatted(ordered.size() - 3));
            } else {
                sb.append(english
                        ? "\nSee the advisory card below for full recommendations."
                        : "\nChi tiết khuyến nghị từng ca xem thẻ bên dưới.");
            }
        }

        if (hasRiskyOrUnknownSlot || CopilotRequest.matches(SLOT_UNSAFE_OR_UNKNOWN, q)) {
            sb.append(english
                    ? "\nSafety rule: Marine safety ALWAYS overrides demand. NEVER expand capacity or open extra slots when weather is HIGH_RISK (UNSAFE) or UNKNOWN (missing forecast is never assumed safe)."
                    : "\nQuy tắc an toàn: An toàn hàng hải luôn ưu tiên hơn nhu cầu. Tuyệt đối KHÔNG đề xuất tăng sức chứa hay mở thêm ca khi thời tiết nguy hiểm hoặc chưa có dữ liệu dự báo.");
        } else if (CopilotRequest.matches(SLOT_HIGH_OCCUPANCY, q)) {
            sb.append(english
                    ? "\nBefore opening an extra slot when occupancy is high (>= 85-90%), confirm safe marine weather, boat/equipment capacity, and available guide crew."
                    : "\nTrước khi mở thêm ca khi công suất đạt cao (>= 85–90%), cần kiểm tra dự báo thời tiết an toàn, giới hạn sức chứa tàu/thiết bị và nhân sự hướng dẫn viên.");
        } else if (CopilotRequest.matches(SLOT_LOW_OCCUPANCY, q)) {
            sb.append(english
                    ? "\nFor low-occupancy slots (< 20%), consider a promotional Flash Sale or proactively offering guests voluntary consolidation into an adjacent slot — never auto-cancel confirmed bookings."
                    : "\nVới ca công suất thấp (< 20%), bạn có thể cân nhắc khuyến mãi Flash Sale hoặc chủ động liên hệ khách đề xuất gộp sang ca gần kề — tuyệt đối không tự ý hủy đơn đã xác nhận.");
        }
        return sb.toString();
    }

    private static String weatherLabel(String weatherLevel, boolean english) {
        return switch (weatherLevel) {
            case "SAFE" -> english ? "safe" : "an toàn";
            case "MODERATE" -> english ? "moderate caution" : "cần theo dõi";
            case "HIGH_RISK" -> english ? "high risk" : "nguy hiểm";
            default -> english ? "forecast unavailable" : "chưa có dự báo";
        };
    }

    private static String slotRecommendationLabel(String recType, boolean english) {
        return switch (recType) {
            case "EXPAND_CAPACITY" -> english ? "consider expanding capacity" : "có thể cân nhắc mở rộng sức chứa";
            case "ADD_SLOT" -> english ? "consider adding an extra slot" : "có thể cân nhắc mở thêm ca";
            case "WEATHER_WATCH" -> english ? "monitor marine weather" : "theo dõi sát thời tiết biển";
            case "PROMOTE_OR_CONSOLIDATE" -> english ? "promote or offer voluntary consolidation" : "kích cầu hoặc đề xuất gộp ca";
            case "KEEP_OPTIMAL" -> english ? "maintain current schedule" : "duy trì lịch hiện tại";
            default -> english ? "review slot status" : "kiểm tra thêm tình trạng ca";
        };
    }

    private static String formatJsonDate(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        if (node.isArray() && node.size() >= 3) {
            return "%04d-%02d-%02d".formatted(node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
        }
        return node.asText("");
    }

    private static String formatJsonTime(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        if (node.isArray() && node.size() >= 2) {
            return "%02d:%02d".formatted(node.get(0).asInt(), node.get(1).asInt());
        }
        String raw = node.asText("");
        return raw.length() >= 5 ? raw.substring(0, 5) : raw;
    }

    static String vendorServiceDetail(JsonNode data, boolean english) {
        if (!data.has("title")) return unavailable(english);
        String title = data.path("title").asText(english ? "Service" : "Dịch vụ");
        JsonNode options = data.path("options");
        List<BigDecimal> optionPrices = new ArrayList<>();
        if (options.isArray()) {
            for (JsonNode opt : options) {
                if (opt.path("price").isNumber()) {
                    optionPrices.add(opt.path("price").decimalValue());
                }
            }
        }
        BigDecimal basePrice = data.path("basePrice").isNumber() ? data.path("basePrice").decimalValue() : null;

        if (!optionPrices.isEmpty()) {
            BigDecimal min = optionPrices.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            BigDecimal max = optionPrices.stream().max(BigDecimal::compareTo).orElse(min);
            String priceText = min.compareTo(max) == 0
                    ? money(min, english)
                    : (english ? "%s – %s".formatted(money(min, true), money(max, true))
                            : "từ %s đến %s".formatted(money(min, false), money(max, false)));
            return english
                    ? "Service \"%s\" currently has %d active pricing option(s) priced at %s. See the service card below for full package details."
                            .formatted(title, optionPrices.size(), priceText)
                    : "Dịch vụ \"%s\" hiện có %d gói đang áp dụng với mức giá %s. Chi tiết từng gói xem thẻ bên dưới."
                            .formatted(title, optionPrices.size(), priceText);
        }
        if (basePrice != null) {
            return english
                    ? "Service \"%s\" has a listed base price of %s. See the service card below for full details."
                            .formatted(title, money(basePrice, true))
                    : "Dịch vụ \"%s\" có giá niêm yết %s. Chi tiết xem thẻ bên dưới."
                            .formatted(title, money(basePrice, false));
        }
        return english
                ? "Service \"%s\" does not have a listed price yet. See the service card below for details."
                        .formatted(title)
                : "Dịch vụ \"%s\" hiện chưa có mức giá niêm yết. Chi tiết xem thẻ bên dưới."
                        .formatted(title);
    }

    static String vendorListingOptimization(JsonNode data, boolean english) {
        if (!data.has("listingQualityScore")) return unavailable(english);
        int score = data.path("listingQualityScore").asInt(0);
        boolean compliant = data.path("policyCompliant").asBoolean(true);
        List<String> missing = new ArrayList<>();
        for (JsonNode item : data.path("missingItems")) {
            String desc = item.path("description").asText("").trim();
            if (!desc.isBlank()) missing.add(desc);
        }
        String complianceNote = compliant
                ? (english ? "policy check passed" : "đạt chuẩn nội dung")
                : (english ? "needs policy review" : "cần rà soát lại nội dung");
        String missingSummary;
        if (missing.isEmpty()) {
            missingSummary = english
                    ? "All baseline checklist items are covered."
                    : "Bài đăng đã đáp ứng các tiêu chí cơ bản.";
        } else if (missing.size() <= 2) {
            missingSummary = english
                    ? "Missing items: %s. See the card below for details.".formatted(String.join("; ", missing))
                    : "Cần bổ sung: %s. Chi tiết xem thẻ bên dưới.".formatted(String.join("; ", missing));
        } else {
            missingSummary = english
                    ? "%d checklist items need attention (e.g. %s). See the card below for the full list."
                            .formatted(missing.size(), String.join("; ", missing.subList(0, 2)))
                    : "Còn thiếu %d tiêu chí cần bổ sung (ví dụ: %s). Chi tiết xem thẻ bên dưới."
                            .formatted(missing.size(), String.join("; ", missing.subList(0, 2)));
        }
        JsonNode draft = data.path("drafts").path(english ? "EN" : "VI");
        String draftTitle = draft.path("optimizedTitle").asText("");
        String draftDesc = draft.path("optimizedDescription").asText("");
        String draftBlock = (!draftTitle.isBlank() || !draftDesc.isBlank())
                ? (english
                        ? "\nSuggested title: \"%s\"\nSuggested description: %s".formatted(draftTitle, draftDesc)
                        : "\nTiêu đề đề xuất: \"%s\"\nMô tả đề xuất: %s".formatted(draftTitle, draftDesc))
                : "";
        return (english
                ? "Listing quality score: %d/100 (%s; advisory only, not automatically published). %s%s\nOnly use verified facts from your service; do not add unconfirmed perks or certifications."
                        .formatted(score, complianceNote, missingSummary, draftBlock)
                : "Điểm chất lượng bài đăng: %d/100 (%s; kết quả chỉ mang tính tham khảo, không tự động xuất bản). %s%s\nChỉ dùng thông tin thực tế của dịch vụ, không thêm quyền lợi hoặc chứng nhận chưa xác thực."
                        .formatted(score, complianceNote, missingSummary, draftBlock));
    }

    static String contentAssessment(JsonNode data, boolean english) {
        if (!data.has("status")) return unavailable(english);
        String status = data.path("status").asText("NEEDS_REVIEW");
        boolean negatedWarningOnly = data.path("negatedWarningOnly").asBoolean(false);

        int reasonCount = data.path("reasonCodes").isArray() ? data.path("reasonCodes").size() : 0;
        int contactCount = data.path("contactMatches").isArray() ? data.path("contactMatches").size() : 0;

        if (negatedWarningOnly && "NO_RULE_SIGNAL".equals(status)) {
            return english
                    ? "NO — this phrase is a customer-protection negation warning against off-platform payments, NOT an off-platform payment promotion. Only escalate if phone numbers, external links, or payment solicitation are included. See the card below for details."
                    : "KHÔNG — đây là câu cảnh báo phủ định nhằm nhắc nhở không giao dịch ngoài nền tảng, KHÔNG phải quảng cáo thanh toán ngoài. Chỉ cần chuyển kiểm duyệt nếu nội dung kèm số điện thoại, đường link hoặc lời mời chuyển khoản ngoài sàn. Chi tiết xem thẻ bên dưới.";
        }

        if ("NEEDS_REVIEW".equals(status)) {
            String detectedSummary = contactCount > 0
                    ? (english
                            ? "Detected %d policy signal(s) including %d external contact/link match(es). "
                                    .formatted(Math.max(reasonCount, 1), contactCount)
                            : "Phát hiện %d tín hiệu cần xem xét (trong đó có %d mẫu liên hệ/đường link ngoài nền tảng). "
                                    .formatted(Math.max(reasonCount, 1), contactCount))
                    : (english
                            ? "Detected %d policy signal(s) requiring moderator review. ".formatted(Math.max(reasonCount, 1))
                            : "Phát hiện %d tín hiệu cần quản trị viên kiểm duyệt. ".formatted(Math.max(reasonCount, 1)));
            return english
                    ? "Content flagged for manual review (AI never auto-approves publication). %sListings must not include personal phone numbers, external booking links, off-platform bank transfers, or exaggerated claims. See the card below for details."
                            .formatted(detectedSummary)
                    : "Nội dung cần được kiểm duyệt trước khi xuất bản (hệ thống không tự động duyệt bài). %sKhông được đưa số điện thoại cá nhân, đường link đặt tour ngoài, yêu cầu chuyển khoản ngoài DANASEA hoặc quảng cáo quá mức vào bài đăng. Chi tiết xem thẻ bên dưới."
                            .formatted(detectedSummary);
        }

        return english
                ? "Content check completed with no automated rule violations detected. AI checks are advisory only and never grant publication approval. See the card below for details."
                : "Đã hoàn tất kiểm tra nhanh nội dung, chưa phát hiện dấu hiệu vi phạm quy tắc tự động. Kết quả AI chỉ mang tính tham khảo, không thay thế quyền phê duyệt xuất bản bài đăng. Chi tiết xem thẻ bên dưới.";
    }

    static String adminOrderRiskAssessment(JsonNode data, boolean english) {
        if (!data.has("status") && !data.has("orderId")) return unavailable(english);
        boolean newCaseCreated = data.path("newCaseCreated").asBoolean(false);
        JsonNode observed = data.path("observed");
        long failed24h = observed.path("failedPayments24h").asLong(0);
        long orders1h = observed.path("orders1h").asLong(0);
        long refunds7d = observed.path("refundRequests7d").asLong(0);

        int signalCount = data.path("riskSignals").isArray() ? data.path("riskSignals").size() : 0;
        String signalSummary = signalCount == 0
                ? (english ? "no Velocity threshold signals triggered" : "chưa ghi nhận tín hiệu vượt ngưỡng Velocity")
                : (english ? "%d Velocity risk signal(s) triggered".formatted(signalCount)
                        : "ghi nhận %d tín hiệu rủi ro Velocity cần xem xét".formatted(signalCount));
        String modeNote = newCaseCreated
                ? (english ? "a new review case has been recorded" : "đã lập hồ sơ thẩm định mới")
                : (english ? "read-only check, no new case created" : "chế độ chỉ đọc, chưa tạo hồ sơ mới");

        return english
                ? ("Order risk assessment summary (%s; %s)."
                        + " Observed activity: %d failed payment(s) in 24h, %d order(s) in 1h, and %d refund request(s) in 7d."
                        + " Risk signals are advisory only and never confirm fraud automatically — verify payment logs before acting. See the risk card below for full details.")
                        .formatted(signalSummary, modeNote, failed24h, orders1h, refunds7d)
                : ("Tổng hợp căn cứ đánh giá rủi ro đơn hàng (%s; %s)."
                        + " Số liệu ghi nhận: %d lần thanh toán thất bại trong 24 giờ, %d đơn trong 1 giờ và %d yêu cầu hoàn tiền trong 7 ngày."
                        + " Các tín hiệu chỉ mang tính cảnh báo (chưa tự kết luận gian lận), quản trị viên cần đối chiếu log thanh toán trước khi xử lý. Chi tiết xem thẻ bên dưới.")
                        .formatted(signalSummary, modeNote, failed24h, orders1h, refunds7d);
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
