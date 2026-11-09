package com.danasea.backend.modules.ai.application.usecases;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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
            String recommendedResolution,
            Integer recommendedRefundPercent,
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

        systemFacts.add(isEn
                ? "RECORDED SLOT STATUS: " + evidence.slotStatus() + " (" + evidence.slotDate() + ", " + evidence.slotTimeRange() + ")."
                : "TRẠNG THÁI CA TRONG HỆ THỐNG: " + evidence.slotStatus() + " (" + evidence.slotDate() + ", " + evidence.slotTimeRange() + ").");

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

        String recommendedRes = "MANUAL_REVIEW_REQUIRED";
        String reason = isEn
                ? "The recorded slot status, check-in and statements are evidence for review. Confirm the circumstances and applicable refund policy before deciding."
                : "Trạng thái ca, lượt check-in và lời trình bày là dữ liệu để đối chiếu. Hãy xác minh tình huống và chính sách hoàn tiền áp dụng trước khi quyết định.";
        String draftNote = isEn
                ? "Pending administrator review of both parties' evidence and the applicable refund policy."
                : "Chờ quản trị viên đối chiếu bằng chứng của hai bên và chính sách hoàn tiền áp dụng.";
        String analysisMethod = "DETERMINISTIC_EVIDENCE_SUMMARY";

        DisputeArbitrationResult result = new DisputeArbitrationResult(
                disputeId, evidence.subOrderId(), evidence.orderId(),
                evidence.customerName(), evidence.vendorBusinessName(), evidence.serviceTitle(),
                evidence.disputeReason(), timeline, customerClaim, vendorDefense, systemFacts,
                recommendedRes, null, reason, draftNote, analysisMethod, OffsetDateTime.now()
        );

        // Persist to ai_dispute_summaries
        try {
            String summaryJson = mapper.writeValueAsString(result);
            insightStore.saveDisputeSummary(disputeId, evidence.subOrderId(), adminUserId,
                    recommendedRes, null, summaryJson, analysisMethod);
        } catch (Exception e) {
            log.warn("Failed to persist dispute arbitration summary: {}", e.getMessage());
        }

        return result;
    }
}
