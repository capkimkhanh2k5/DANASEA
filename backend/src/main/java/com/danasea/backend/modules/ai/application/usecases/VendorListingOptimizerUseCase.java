package com.danasea.backend.modules.ai.application.usecases;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort;
import com.danasea.backend.modules.ai.application.ports.DecisionModelPort;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort;
import com.danasea.backend.modules.ai.domain.models.DecisionResult;
import com.danasea.backend.modules.ai.domain.models.DecisionTask;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class VendorListingOptimizerUseCase {

    public record OptimizeRequest(
            UUID serviceId,
            String rawTitle,
            String rawDescription,
            List<String> highlights,
            List<String> includedItems,
            List<String> targetLanguages
    ) {}

    public record MissingChecklistItem(String itemCode, String description, String suggestion, boolean critical) {}

    public record LocalizedListingDraft(
            String language,
            String optimizedTitle,
            String optimizedDescription,
            List<String> sellingPoints,
            List<String> travelerPackingList,
            String safetyAdvisory
    ) {}

    public record OptimizeResult(
            UUID vendorId,
            UUID serviceId,
            String detectedCategory,
            int listingQualityScore, // 0 - 100
            List<MissingChecklistItem> missingItems,
            Map<String, LocalizedListingDraft> drafts,
            boolean policyCompliant,
            String analysisMethod,
            OffsetDateTime generatedAt
    ) {}

    private final VendorAiDataPort vendorAiData;
    private final AiExtendedInsightStorePort insightStore;
    private final DecisionModelPort decisions;
    private final AssessTextUseCase textAssessment;
    private final ObjectMapper mapper;

    public OptimizeResult execute(UUID vendorUserId, OptimizeRequest request) {
        SupportedLanguage lang = SupportedLanguage
                .fromTag(LocaleContextHolder.getLocale().toLanguageTag())
                .orElse(SupportedLanguage.VI);
        return execute(vendorUserId, request, lang);
    }

    public OptimizeResult execute(UUID vendorUserId, OptimizeRequest request, SupportedLanguage language) {
        boolean isEn = language == SupportedLanguage.EN;
        var profile = vendorAiData.findProfileByUserId(vendorUserId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor profile not found for user: " + vendorUserId));
        UUID vendorId = profile.id();

        String rawTitle = request.rawTitle() != null ? request.rawTitle().trim() : "";
        String rawDesc = request.rawDescription() != null ? request.rawDescription().trim() : "";
        List<String> highlights = request.highlights() != null ? request.highlights() : List.of();
        List<String> included = request.includedItems() != null ? request.includedItems() : List.of();

        // 1. Category Detection via Quyet SERVICE_CATEGORY
        String combinedText = (rawTitle + " " + rawDesc).trim();
        String detectedCategory = "COASTAL_EXPERIENCE";
        try {
            DecisionResult decision = decisions.decide(DecisionTask.SERVICE_CATEGORY, Map.of("content", combinedText.isEmpty() ? "Tour biển Đà Nẵng" : combinedText));
            if (decision != null) {
                detectedCategory = decision.choice("category", "COASTAL_EXPERIENCE");
            }
        } catch (Exception e) {
            log.info("Category decision fallback: {}", e.getMessage());
        }

        // 2. Listing Quality Score & Missing Checklist
        int score = 0;
        List<MissingChecklistItem> missing = new ArrayList<>();
        String fullContent = (rawTitle + " " + rawDesc + " " + String.join(" ", highlights) + " " + String.join(" ", included)).toLowerCase(Locale.ROOT);

        // Check meeting point
        if (fullContent.contains("bến") || fullContent.contains("cảng") || fullContent.contains("bãi") || fullContent.contains("mân thái")
                || fullContent.contains("sơn trà") || fullContent.contains("địa chỉ") || fullContent.contains("tập trung")
                || fullContent.contains("pier") || fullContent.contains("beach") || fullContent.contains("meeting point")) {
            score += 20;
        } else {
            missing.add(new MissingChecklistItem("MISSING_MEETING_POINT",
                    isEn ? "Missing specific meeting point or departure pier" : "Chưa nêu địa điểm đón hoặc bến tập trung cụ thể",
                    isEn ? "Add the exact beach or pier address (e.g., Man Thai Beach, Han River Port) so guests can find the departure point easily."
                         : "Thêm địa chỉ bãi biển hoặc bến cảng tập trung (ví dụ: Bãi biển Mân Thái, Cảng Sông Hàn) để khách dễ tìm đường.",
                    true));
        }

        // Check safety equipment
        if (fullContent.contains("áo phao") || fullContent.contains("bảo hộ") || fullContent.contains("an toàn") || fullContent.contains("hướng dẫn viên")
                || fullContent.contains("kính lặn") || fullContent.contains("life jacket") || fullContent.contains("safety") || fullContent.contains("instructor")) {
            score += 25;
        } else {
            missing.add(new MissingChecklistItem("MISSING_SAFETY_EQUIPMENT",
                    isEn ? "Missing safety equipment details" : "Chưa nêu rõ trang bị an toàn đi kèm",
                    isEn ? "List the safety equipment and staff support that are confirmed for this activity."
                         : "Liệt kê thiết bị an toàn và nhân sự hỗ trợ đã được xác nhận cho hoạt động.",
                    true));
        }

        // Check age and requirements
        if (fullContent.contains("tuổi") || fullContent.contains("trẻ em") || fullContent.contains("sức khỏe") || fullContent.contains("bơi")
                || fullContent.contains("không biết bơi") || fullContent.contains("age") || fullContent.contains("swim") || fullContent.contains("children")) {
            score += 20;
        } else {
            missing.add(new MissingChecklistItem("MISSING_AGE_CONSTRAINTS",
                    isEn ? "Missing age & swimming skill requirements" : "Chưa ghi rõ độ tuổi & yêu cầu biết bơi",
                    isEn ? "Clarify whether non-swimmers can join and specify the minimum age for children."
                         : "Nêu rõ tour có phù hợp người không biết bơi hay không và độ tuổi tối thiểu của trẻ em tham gia.",
                    false));
        }

        // Check duration
        if (fullContent.contains("phút") || fullContent.contains("tiếng") || fullContent.contains("giờ") || fullContent.contains("thời lượng")
                || fullContent.contains("minute") || fullContent.contains("hour") || fullContent.contains("duration")) {
            score += 15;
        } else {
            missing.add(new MissingChecklistItem("MISSING_DURATION",
                    isEn ? "Missing activity duration" : "Chưa nêu rõ thời lượng trải nghiệm",
                    isEn ? "Specify the activity duration (e.g., 120 minutes or 2 hours) to help travelers plan their itinerary."
                         : "Thêm thời lượng hoạt động (ví dụ: 120 phút hoặc 2 tiếng) để khách hàng sắp xếp lịch trình.",
                    false));
        }

        // Title and description length
        if (rawTitle.length() >= 15 && rawDesc.length() >= 50) {
            score += 20;
        } else {
            missing.add(new MissingChecklistItem("CONTENT_TOO_SHORT",
                    isEn ? "Title or description is too brief" : "Tiêu đề hoặc mô tả còn quá ngắn",
                    isEn ? "Expand the title with key selling points (e.g., 'Guided Sunrise SUP at Man Thai Beach') to boost click-through rate."
                         : "Mở rộng tiêu đề mô tả rõ điểm nhấn (ví dụ: 'Tour Chèo SUP Ngắm Bình Minh Biển Mân Thái') để tăng tỷ lệ click.",
                    false));
        }

        // 3. Draft Generation (VI & EN)
        String titleVi = rawTitle.isEmpty() ? "Trải Nghiệm Thể Thao Biển Đà Nẵng" : rawTitle;
        String descVi = rawDesc;
        String titleEn = "Da Nang Marine Adventure Experience";
        String descEn = "";
        List<String> pointsVi = List.copyOf(highlights);
        List<String> packingVi = List.of("Đồ bơi thoải mái", "Kem chống nắng thân thiện rạn san hô", "Kính râm có dây đeo", "Túi chống nước điện thoại");
        String safetyVi = "Khách hàng vui lòng mặc áo phao trong suốt thời gian hoạt động và tuân thủ tuyệt đối chỉ dẫn của huấn luyện viên an toàn.";

        List<String> pointsEn = List.of();
        List<String> packingEn = List.of("Comfortable swimwear", "Reef-safe sunscreen", "Sunglasses with strap", "Waterproof phone pouch");
        String safetyEn = "Please wear life jackets at all times on the water and strictly adhere to safety briefing guidelines.";

        Map<String, LocalizedListingDraft> drafts = Map.of(
                "VI", new LocalizedListingDraft("VI", titleVi, descVi, pointsVi, packingVi, safetyVi),
                "EN", new LocalizedListingDraft("EN", titleEn, descEn, pointsEn, packingEn, safetyEn)
        );

        // 4. Moderation Compliance Check
        boolean compliant = true;
        try {
            var assess = textAssessment.execute(vendorUserId, rawTitle + " " + rawDesc, false);
            if (assess != null && !"BENIGN".equalsIgnoreCase(assess.status()) && !"NO_RULE_SIGNAL".equalsIgnoreCase(assess.status())) {
                compliant = false;
            }
        } catch (Exception ignored) {}

        OptimizeResult result = new OptimizeResult(
                vendorId, request.serviceId(), detectedCategory, score, missing, drafts, compliant,
                "HYBRID_DUAL_BRAIN", OffsetDateTime.now()
        );

        // 5. Persist Insight
        try {
            String payloadJson = mapper.writeValueAsString(result);
            insightStore.saveVendorInsight(vendorId, request.serviceId(), "LISTING_OPTIMIZER",
                    vendorId + ":" + (request.serviceId() != null ? request.serviceId() : "new"),
                    payloadJson, "HYBRID_DUAL_BRAIN");
        } catch (Exception e) {
            log.warn("Failed to persist vendor listing optimizer insight: {}", e.getMessage());
        }

        return result;
    }
}
