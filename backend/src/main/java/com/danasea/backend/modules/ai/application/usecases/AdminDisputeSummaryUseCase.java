package com.danasea.backend.modules.ai.application.usecases;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import com.danasea.backend.modules.ai.application.ports.AdminAiDataPort;
import com.danasea.backend.modules.ai.application.ports.AdminAiDataPort.DisputeFullEvidence;
import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDisputeSummaryUseCase {

    public record DisputeArbitrationResult(
            UUID disputeId,
            UUID subOrderId,
            UUID orderId,
            String customerName,
            String vendorBusinessName,
            String serviceTitle,
            String disputeReason,
            String timelineSummary,
            String customerClaimSummary,
            String vendorDefenseSummary,
            List<String> objectiveSystemFacts,
            String recommendedResolution, // RESOLVED_REFUND, RESOLVED_PARTIAL, RESOLVED_REJECTED
            int recommendedRefundPercent,  // 0, 50, 100
            String recommendationReason,
            String draftAdminNote,
            String analysisMethod,
            OffsetDateTime generatedAt
    ) {}

    private final AdminAiDataPort adminAiData;
    private final AiExtendedInsightStorePort insightStore;
    private final ObjectMapper mapper;

    public DisputeArbitrationResult execute(UUID adminUserId, UUID disputeId) {
        SupportedLanguage lang = SupportedLanguage
                .fromTag(LocaleContextHolder.getLocale().toLanguageTag())
                .orElse(SupportedLanguage.VI);
        return execute(adminUserId, disputeId, lang);
    }

    public DisputeArbitrationResult execute(UUID adminUserId, UUID disputeId, SupportedLanguage language) {
        boolean isEn = language == SupportedLanguage.EN;
        DisputeFullEvidence evidence = adminAiData.getDisputeFullEvidence(disputeId)
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found with ID: " + disputeId));

        // 1. Compile Objective System Facts
        List<String> systemFacts = new ArrayList<>();
        if (evidence.checkinUsed()) {
            systemFacts.add(isEn
                    ? "CHECK-IN VERIFICATION: Ticket QR code was scanned and verified at " + evidence.checkinUsedAt() + "."
                    : "XÁC THỰC CHECK-IN: Mã QR vé đã được quét xác nhận vào lúc " + evidence.checkinUsedAt() + ".");
        } else {
            systemFacts.add(isEn
                    ? "CHECK-IN VERIFICATION: Ticket QR code was never scanned at the pier/departure point."
                    : "XÁC THỰC CHECK-IN: Vé QR chưa từng được quét check-in tại bến/điểm xuất phát.");
        }

        if ("CANCELLED".equalsIgnoreCase(evidence.slotStatus())) {
            systemFacts.add(isEn
                    ? "SLOT STATUS: The activity slot on " + evidence.slotDate() + " was marked as CANCELLED in the system."
                    : "TÌNH TRẠNG CA DỊCH VỤ: Ca hoạt động ngày " + evidence.slotDate() + " đã bị hủy trên hệ thống.");
        } else {
            systemFacts.add(isEn
                    ? "SLOT STATUS: The activity slot on " + evidence.slotDate() + " (" + evidence.slotTimeRange() + ") operated normally."
                    : "TÌNH TRẠNG CA DỊCH VỤ: Ca hoạt động ngày " + evidence.slotDate() + " (" + evidence.slotTimeRange() + ") diễn ra bình thường.");
        }

        if (evidence.vendorRespondedAt() != null) {
            systemFacts.add(isEn
                    ? "VENDOR RESPONSE: Vendor submitted defense statement with " + evidence.vendorEvidenceUrls().size() + " evidence attachment(s)."
                    : "ĐỐI TÁC PHẢN HỒI: Đối tác đã gửi giải trình và cung cấp " + evidence.vendorEvidenceUrls().size() + " tệp bằng chứng.");
        } else {
            systemFacts.add(isEn
                    ? "VENDOR RESPONSE: Vendor has not submitted any defense statement yet."
                    : "ĐỐI TÁC PHẢN HỒI: Đối tác chưa phản hồi giải trình đối chất.");
        }

        // 2. Timeline Summary
        String timeline = isEn
                ? String.format("1. Service '%s' scheduled on %s (%s). 2. Check-in: %s. 3. Customer opened dispute at %s (Reason: %s). 4. Vendor response: %s.",
                        evidence.serviceTitle(), evidence.slotDate(), evidence.slotTimeRange(),
                        evidence.checkinUsed() ? "Checked in (" + evidence.checkinUsedAt() + ")" : "Not checked in",
                        evidence.disputeCreatedAt(), evidence.disputeReason(),
                        evidence.vendorRespondedAt() != null ? "Responded (" + evidence.vendorRespondedAt() + ")" : "No response")
                : String.format("1. Đơn hàng dịch vụ '%s' khởi hành ngày %s (%s). 2. Check-in: %s. 3. Khách hàng mở khiếu nại lúc %s (Lý do: %s). 4. Đối tác giải trình: %s.",
                        evidence.serviceTitle(), evidence.slotDate(), evidence.slotTimeRange(),
                        evidence.checkinUsed() ? "Đã check-in (" + evidence.checkinUsedAt() + ")" : "Chưa check-in",
                        evidence.disputeCreatedAt(), evidence.disputeReason(),
                        evidence.vendorRespondedAt() != null ? "Đã phản hồi (" + evidence.vendorRespondedAt() + ")" : "Chưa phản hồi");

        // 3. Claims vs Defense
        String customerClaim = evidence.description() != null && !evidence.description().isBlank()
                ? evidence.description().trim()
                : (isEn ? "Customer requested refund due to: " + evidence.disputeReason()
                        : "Khách hàng khiếu nại yêu cầu hoàn tiền lý do: " + evidence.disputeReason());

        String vendorDefense = evidence.vendorResponse() != null && !evidence.vendorResponse().isBlank()
                ? evidence.vendorResponse().trim()
                : (isEn ? "Vendor has not provided a written defense statement."
                        : "Đối tác chưa cung cấp văn bản giải trình đối chất.");

        // 4. Arbitration Logic (Deterministic Rules grounded in Verified System Facts)
        String recommendedRes;
        int refundPercent;
        String reason;
        String draftNote;

        String descLower = customerClaim.toLowerCase(Locale.ROOT);

        if ("CANCELLED".equalsIgnoreCase(evidence.slotStatus())
                || descLower.contains("thời tiết") || descLower.contains("bão") || descLower.contains("sóng lớn")
                || descLower.contains("weather") || descLower.contains("storm") || descLower.contains("rough sea")) {
            recommendedRes = "RESOLVED_REFUND";
            refundPercent = 100;
            reason = isEn
                    ? "The trip was disrupted by hazardous marine weather or slot cancellation. Under the DANASEA Maritime Safety Charter, the customer is entitled to a 100% refund."
                    : "Chuyến đi bị ảnh hưởng bởi điều kiện thời tiết nguy hiểm hoặc ca dịch vụ bị hủy. Theo Điều lệ An toàn Hàng hải DANASEA, khách hàng được bảo đảm hoàn tiền 100%.";
            draftNote = isEn
                    ? "Approved 100% refund due to force majeure / unsafe marine weather conditions."
                    : "Chấp thuận hoàn tiền 100% do sự cố khách quan / thời tiết không đảm bảo an toàn hàng hải.";
        } else if (evidence.checkinUsed()) {
            if (descLower.contains("không đón") || descLower.contains("không có thuyền") || descLower.contains("bỏ rơi")
                    || descLower.contains("no pickup") || descLower.contains("abandoned")) {
                recommendedRes = "RESOLVED_REJECTED";
                refundPercent = 0;
                reason = isEn
                        ? "System audit logs confirm the customer's QR ticket was scanned at the pier. Vendor and system evidence confirm the service was delivered."
                        : "Dữ liệu nhật ký hệ thống ghi nhận mã QR của khách đã được quét check-in tại bến. Bằng chứng đối chất của đối tác và hệ thống cho thấy dịch vụ đã diễn ra, khiếu nại không xuất phát từ lỗi đối tác.";
                draftNote = isEn
                        ? "Dispute rejected: System logs verify the QR ticket was scanned and used on schedule."
                        : "Bác bỏ khiếu nại: Hệ thống xác thực vé QR đã được check-in sử dụng tại bến đúng lịch trình.";
            } else {
                recommendedRes = "RESOLVED_PARTIAL";
                refundPercent = 30;
                reason = isEn
                        ? "The customer participated in the activity (QR checked in) but reported service or equipment quality issues. A 30% goodwill partial refund is recommended."
                        : "Khách hàng đã tham gia trải nghiệm (đã check-in QR) nhưng phản ánh chất lượng dịch vụ hoặc thiết bị không đúng cam kết. Đề xuất bồi hoàn thiện chí 30% giá trị đơn hàng.";
                draftNote = isEn
                        ? "Approved a 30% partial goodwill refund to compensate for sub-optimal service experience."
                        : "Chấp thuận bồi hoàn một phần 30% giá trị dịch vụ nhằm hỗ trợ trải nghiệm chưa trọn vẹn của khách hàng.";
            }
        } else {
            if (evidence.vendorRespondedAt() == null) {
                recommendedRes = "RESOLVED_REFUND";
                refundPercent = 100;
                reason = isEn
                        ? "Vendor failed to submit a defense statement and the customer's QR ticket was never scanned. Resolved in favor of the customer."
                        : "Đối tác không gửi giải trình đối chất và khách hàng chưa từng được check-in. Quyền lợi được giải quyết theo hướng có lợi cho khách hàng.";
                draftNote = isEn
                        ? "Approved 100% refund because the vendor did not provide a valid defense statement."
                        : "Hoàn tiền 100% cho khách hàng do đối tác không cung cấp giải trình đối chất hợp lệ.";
            } else if (evidence.vendorResponse().toLowerCase(Locale.ROOT).contains("khách không đến")
                    || evidence.vendorResponse().toLowerCase(Locale.ROOT).contains("no-show")
                    || evidence.vendorResponse().toLowerCase(Locale.ROOT).contains("no show")) {
                recommendedRes = "RESOLVED_REJECTED";
                refundPercent = 0;
                reason = isEn
                        ? "Vendor prepared vessel and staff at the departure pier, but the customer did not show up (No-show) without prior notice per cancellation policy."
                        : "Dịch vụ đã chuẩn bị phương tiện và nhân sự đón tại bến nhưng khách hàng không có mặt (No-show) và không thông báo trước theo chính sách hủy.";
                draftNote = isEn
                        ? "Refund request rejected due to customer no-show at the scheduled departure point."
                        : "Bác bỏ yêu cầu hoàn tiền do khách hàng không có mặt tại điểm hẹn khởi hành (No-show).";
            } else {
                recommendedRes = "RESOLVED_PARTIAL";
                refundPercent = 50;
                reason = isEn
                        ? "Both parties had a miscommunication regarding the meeting point, preventing boarding. A 50% split refund is recommended for fair resolution."
                        : "Có sự bất đồng thông tin điểm hẹn giữa hai bên khiến khách hàng không lên được tàu. Đề xuất hoàn 50% để dung hòa quyền lợi hai bên.";
                draftNote = isEn
                        ? "Mediated resolution with a 50% partial refund due to meeting point communication gaps."
                        : "Giải quyết hòa giải hoàn 50% giá trị đơn do thiếu sót trong việc hướng dẫn điểm hẹn.";
            }
        }

        DisputeArbitrationResult result = new DisputeArbitrationResult(
                disputeId, evidence.subOrderId(), evidence.orderId(),
                evidence.customerName(), evidence.vendorBusinessName(), evidence.serviceTitle(),
                evidence.disputeReason(), timeline, customerClaim, vendorDefense, systemFacts,
                recommendedRes, refundPercent, reason, draftNote, "HYBRID_DUAL_BRAIN", OffsetDateTime.now()
        );

        // Persist to ai_dispute_summaries
        try {
            String summaryJson = mapper.writeValueAsString(result);
            insightStore.saveDisputeSummary(disputeId, evidence.subOrderId(), adminUserId,
                    recommendedRes, refundPercent, summaryJson, "HYBRID_DUAL_BRAIN");
        } catch (Exception e) {
            log.warn("Failed to persist dispute arbitration summary: {}", e.getMessage());
        }

        return result;
    }
}
