package com.danasea.backend.modules.ai.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.danasea.backend.modules.report.domain.models.TimeRange;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class CopilotVendorDiagnosticsAnswerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode vendors() throws Exception {
        return MAPPER.readTree("""
                [
                 {"businessName":"Danabeach Club","severity":"CRITICAL","cancellationRate":50.0,"slotOccupancyRate":2.86,
                  "ratingAvg":2.67,"ratingCount":12,"issueTypes":["HIGH_CANCELLATION","LOW_RATING"]},
                 {"businessName":"Cong ty Kajin Vina","severity":"CRITICAL","cancellationRate":50.0,"slotOccupancyRate":3.33,
                  "ratingAvg":3.24,"ratingCount":17,"issueTypes":["HIGH_CANCELLATION","LOW_RATING","UNDERPERFORMING"]},
                 {"businessName":"Da Nang Outdoor","severity":"WARNING","cancellationRate":14.29,"slotOccupancyRate":1.34,
                  "ratingAvg":3.29,"ratingCount":31,"issueTypes":["LOW_RATING"]},
                 {"businessName":"DaNaFi","severity":"WARNING","cancellationRate":33.33,"slotOccupancyRate":0.89,
                  "ratingAvg":3.44,"ratingCount":16,"issueTypes":["HIGH_CANCELLATION","LOW_RATING","UNDERPERFORMING"]},
                 {"businessName":"Surf Shack","severity":"CRITICAL","cancellationRate":50.0,"slotOccupancyRate":1.05,
                  "ratingAvg":3.63,"ratingCount":16,"issueTypes":["HIGH_CANCELLATION"]},
                 {"businessName":"Loca Travel","severity":"CRITICAL","cancellationRate":83.33,"slotOccupancyRate":0.54,
                  "ratingAvg":3.08,"ratingCount":12,"issueTypes":["HIGH_CANCELLATION","LOW_RATING"]},
                 {"businessName":"An Thuong","severity":"WARNING","cancellationRate":0,"slotOccupancyRate":0,
                  "ratingAvg":0,"ratingCount":0,"issueTypes":["UNDERPERFORMING"]},
                 {"businessName":"D BLUE","severity":"WARNING","cancellationRate":0,"slotOccupancyRate":0,
                  "ratingAvg":3.25,"ratingCount":8,"issueTypes":["LOW_RATING","UNDERPERFORMING"]}
                ]
                """);
    }

    @Test
    @DisplayName("Câu hỏi tổng quan đối tác chỉ trả tóm tắt, không dump trường kỹ thuật/enum")
    void overviewReplyIsSummaryWithoutRawFields() throws Exception {
        String message = CopilotReportAnswer.adminVendorDiagnostics(
                vendors(), "Xem tình hình hoạt động đối tác.",
                CopilotRequest.normalize("Xem tình hình hoạt động đối tác."), false);

        assertThat(message)
                .contains("8/8", "bảng bên dưới", "Danabeach Club")
                .doesNotContain("HIGH_CANCELLATION", "LOW_RATING", "UNDERPERFORMING", "OVERLOADED",
                        "tỷ lệ hủy=", "lấp đầy=", "[CRITICAL", "[WARNING", "cảnh báo=");
        assertThat(message.length()).isLessThan(500);
    }

    @Test
    @DisplayName("Bản tiếng Anh cũng không dump enum/field thô")
    void overviewReplyEnglishHasNoRawFields() throws Exception {
        String message = CopilotReportAnswer.adminVendorDiagnostics(
                vendors(), "Show vendor status", CopilotRequest.normalize("Show vendor status"), true);
        assertThat(message).contains("8", "below")
                .doesNotContain("HIGH_CANCELLATION", "cancel=", "occupancy=", "issues=", "[CRITICAL");
    }

    @Test
    @DisplayName("Danh sách lọc theo điều kiện vẫn nêu tên đối tác nhưng không có enum thô")
    void filteredListsStayNaturalLanguage() throws Exception {
        String message = CopilotReportAnswer.adminVendorDiagnostics(
                vendors(), "Hãy đưa ra các đề xuất cải thiện cho vendor có tỷ lệ hủy cao",
                CopilotRequest.normalize("Hãy đưa ra các đề xuất cải thiện cho vendor có tỷ lệ hủy cao"), false);
        assertThat(message).contains("Loca Travel", "WEATHER", "VENDOR_FAULT")
                .doesNotContain("tỷ lệ hủy=", "cảnh báo=", "[CRITICAL", ">", "%%");
    }

    @Test
    @DisplayName("Các báo cáo có kèm Card khác không rò rỉ JSON, key=value hay enum thô")
    void otherCardBackedAnswersAvoidRawFieldsAndEnums() throws Exception {
        TimeRange period = TimeRange.of(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 24));

        // 1. Admin cancellations (general query -> concise summary, no raw cancellationsByReason key)
        JsonNode cancelData = MAPPER.readTree("""
                {"totalBookings":20,"totalCancelled":6,"weatherCancellations":2,"vendorFaultCancellations":1,
                 "customerCancellations":3,"adminOverrideCancellations":0,"disputeCancellations":0,
                 "cancellationsByReason":{"CUSTOMER_CANCEL":3,"WEATHER":2,"VENDOR_FAULT":1}}
                """);
        String cancelOverview = CopilotReportAnswer.adminCancellations(
                cancelData, period, CopilotRequest.normalize("Tình hình hủy đơn tháng này"), false);
        assertThat(cancelOverview)
                .contains("6/20", "thẻ bên dưới")
                .doesNotContain("cancellationsByReason", "CUSTOMER_CANCEL");

        // 2. Vendor review coaching (general query -> localized pillars, no raw GUIDE/POSITIVE/actionItems dump)
        JsonNode reviewData = MAPPER.readTree("""
                {"totalReviewsAnalyzed":12,"averageRating":4.4,
                 "pillars":{
                   "GUIDE":{"scoreOutOf10":9,"sentiment":"POSITIVE","positiveQuotes":[],"negativeQuotes":[]},
                   "SAFETY":{"scoreOutOf10":9,"sentiment":"POSITIVE","positiveQuotes":[],"negativeQuotes":[]},
                   "PUNCTUALITY":{"scoreOutOf10":6,"sentiment":"NEGATIVE","positiveQuotes":[],"negativeQuotes":[]},
                   "VALUE":{"scoreOutOf10":8,"sentiment":"NEUTRAL","positiveQuotes":[],"negativeQuotes":[]}
                 },
                 "actionItems":[{"pillar":"PUNCTUALITY","title":"Giảm chờ bến","recommendation":"Thông báo trước 15 phút"}]}
                """);
        String reviewMsg = CopilotReportAnswer.vendorReviewCoaching(
                reviewData, CopilotRequest.normalize("Phân tích đánh giá review?"), false);
        assertThat(reviewMsg)
                .contains("Hướng dẫn viên", "An toàn", "Đúng giờ", "Giá trị", "thẻ bên dưới")
                .doesNotContain("GUIDE:", "PUNCTUALITY", "(POSITIVE)", "(NEGATIVE)", "[PUNCTUALITY]");

        // 3. Vendor slot advisor (no weather=UNKNOWN | PROMOTE_OR_CONSOLIDATE)
        JsonNode slotData = MAPPER.readTree("""
                {"totalSlotsEvaluated":4,"highDemandSlotsCount":1,"lowDemandSlotsCount":2,"weatherRiskSlotsCount":1,
                 "recommendations":[
                   {"serviceTitle":"SUP Bình Minh","date":"2026-11-25","startTime":"06:00",
                    "bookedCount":1,"capacity":20,"occupancyRate":5.0,
                    "weatherRiskLevel":"UNKNOWN","recommendationType":"PROMOTE_OR_CONSOLIDATE","adviceReason":"Ít khách"}
                 ]}
                """);
        String slotMsg = CopilotReportAnswer.vendorSlotAdvisor(
                slotData, CopilotRequest.normalize("Ca tour vắng khách 7 ngày tới?"), false);
        assertThat(slotMsg)
                .contains("SUP Bình Minh", "1/20 (5.0%)", "thẻ bên dưới")
                .doesNotContain("weather=", "PROMOTE_OR_CONSOLIDATE", "HIGH_RISK", "UNKNOWN");

        // 4. Vendor listing optimization (no publicationAuthorized=false, raw boolean or itemCode)
        JsonNode listingData = MAPPER.readTree("""
                {"listingQualityScore":72,"detectedCategory":"COASTAL_EXPERIENCE","policyCompliant":true,
                 "missingItems":[{"itemCode":"SAFETY_VEST","description":"Thiếu thông tin áo phao"}],
                 "drafts":{"VI":{"optimizedTitle":"Tour SUP","optimizedDescription":"Mô tả ngắn"}}}
                """);
        String listingMsg = CopilotReportAnswer.vendorListingOptimization(listingData, false);
        assertThat(listingMsg)
                .contains("Điểm chất lượng bài đăng: 72/100", "thẻ bên dưới", "Tiêu đề đề xuất:")
                .doesNotContain("publicationAuthorized=", "COASTAL_EXPERIENCE", "SAFETY_VEST", "=true");

        // 5. Content assessment (no status:, contactMatches:, publicationAuthorized:)
        JsonNode contentData = MAPPER.readTree("""
                {"status":"NEEDS_REVIEW","negatedWarningOnly":false,"publicationAuthorized":false,
                 "reasonCodes":["EXTERNAL_CONTACT_DETECTED"],"contactMatches":["0901234567"]}
                """);
        String contentMsg = CopilotReportAnswer.contentAssessment(contentData, false);
        assertThat(contentMsg)
                .contains("kiểm duyệt", "thẻ bên dưới")
                .doesNotContain("publicationAuthorized", "EXTERNAL_CONTACT_DETECTED", "NEEDS_REVIEW");

        // 6. Admin order risk assessment (no raw key=value or JSON dump of quyetDecision)
        JsonNode riskData = MAPPER.readTree("""
                {"orderId":"11111111-2222-3333-4444-555555555555","caseId":"99999999-2222-3333-4444-555555555555",
                 "status":"NEEDS_REVIEW","ruleVersion":"velocity-demo-v1","newCaseCreated":false,"fraudConfirmed":false,
                 "observed":{"failedPayments24h":3,"orders1h":2,"refundRequests7d":1,"paymentStatus":"FAILED"},
                 "riskSignals":["THREE_FAILED_PAYMENTS_IN_24H"],
                 "quyetDecision":{"available":true,"answers":{"needs_review":{"choice":"YES"}}}}
                """);
        String riskMsg = CopilotReportAnswer.adminOrderRiskAssessment(riskData, false);
        assertThat(riskMsg)
                .contains("3 lần thanh toán thất bại", "thẻ bên dưới")
                .doesNotContain("orderId=", "caseId=", "ruleVersion=", "fraudConfirmed=",
                        "available=true", "{\"needs_review\"", "THREE_FAILED_PAYMENTS_IN_24H");
    }
}
