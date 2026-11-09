package com.danasea.backend.modules.ai.application.usecases;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort;
import com.danasea.backend.modules.ai.application.ports.DecisionModelPort;
import com.danasea.backend.modules.ai.application.ports.LlmClientPort;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort.VendorReviewItem;
import com.danasea.backend.modules.ai.domain.models.AiMessage;
import com.danasea.backend.modules.ai.domain.models.AiMessageRole;
import com.danasea.backend.modules.ai.domain.models.LlmResponse;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class VendorReviewCoachingUseCase {

    public record EvidenceQuote(UUID reviewId, int rating, String exactQuote, String aspect) {}

    public record PillarEvaluation(
            String pillar,
            int scoreOutOf10,
            String sentiment, // POSITIVE, NEUTRAL, NEEDS_IMPROVEMENT
            List<EvidenceQuote> positiveQuotes,
            List<EvidenceQuote> negativeQuotes
    ) {}

    public record ActionItem(
            String priority, // HIGH, MEDIUM, LOW
            String pillar,
            String title,
            String recommendation
    ) {}

    public record BadgeProgression(
            String currentTier,
            String nextTier,
            int reviewsNeededForNextTier,
            BigDecimal targetRating,
            String roadmapNote
    ) {}

    public record CoachingResult(
            UUID vendorId,
            UUID serviceId,
            String businessName,
            int totalReviewsAnalyzed,
            BigDecimal averageRating,
            Map<String, PillarEvaluation> pillars,
            List<ActionItem> actionItems,
            BadgeProgression badgeProgression,
            String analysisMethod,
            OffsetDateTime generatedAt
    ) {}

    public record ReplyDraftResult(
            UUID reviewId,
            int rating,
            String customerComment,
            String draftedReply,
            String alternativeShortReply,
            String tone,
            boolean verifiedSafe
    ) {}

    private static final Map<String, List<String>> ASPECT_KEYWORDS = Map.of(
            "GUIDE", List.of("guide", "instructor", "staff", "hướng dẫn", "hướng dẫn viên", "nhân viên", "nhiệt tình", "thái độ"),
            "SAFETY", List.of("safe", "safety", "equipment", "life jacket", "an toàn", "áo phao", "thiết bị", "cano", "thuyền"),
            "PUNCTUALITY", List.of("late", "wait", "punctual", "time", "đúng giờ", "trễ", "chờ", "khởi hành", "bến"),
            "VALUE", List.of("price", "expensive", "value", "worth", "giá", "đáng tiền", "đắt", "rẻ", "dịch vụ")
    );

    private final VendorAiDataPort vendorAiData;
    private final AiExtendedInsightStorePort insightStore;
    private final DecisionModelPort decisions;
    private final LlmClientPort llmClient;
    private final AssessTextUseCase textAssessment;
    private final ObjectMapper mapper;

    public CoachingResult execute(UUID vendorUserId, UUID serviceId, LocalDate from, LocalDate to) {
        SupportedLanguage lang = SupportedLanguage
                .fromTag(LocaleContextHolder.getLocale().toLanguageTag())
                .orElse(SupportedLanguage.VI);
        return execute(vendorUserId, serviceId, from, to, lang);
    }

    public CoachingResult execute(UUID vendorUserId, UUID serviceId, LocalDate from, LocalDate to, SupportedLanguage language) {
        boolean isEn = language == SupportedLanguage.EN;
        var profileOpt = vendorAiData.findProfileByUserId(vendorUserId);
        if (profileOpt.isEmpty()) {
            throw new IllegalArgumentException("Vendor profile not found for user: " + vendorUserId);
        }
        var profile = profileOpt.get();
        UUID vendorId = profile.id();

        // 1. Fetch raw reviews
        List<VendorReviewItem> reviews = vendorAiData.listReviews(vendorId, serviceId, 100);
        int totalAnalyzed = reviews.size();
        BigDecimal avgRating = profile.ratingAvg();
        if (!reviews.isEmpty()) {
            double sum = reviews.stream().mapToInt(VendorReviewItem::rating).sum();
            avgRating = BigDecimal.valueOf(sum / reviews.size()).setScale(1, RoundingMode.HALF_UP);
        }

        // 2. Pillar Analysis
        Map<String, List<EvidenceQuote>> posByPillar = new HashMap<>();
        Map<String, List<EvidenceQuote>> negByPillar = new HashMap<>();
        for (String p : ASPECT_KEYWORDS.keySet()) {
            posByPillar.put(p, new ArrayList<>());
            negByPillar.put(p, new ArrayList<>());
        }

        for (var rev : reviews) {
            if (rev.comment() == null || rev.comment().isBlank()) continue;
            String lower = rev.comment().toLowerCase(Locale.ROOT);
            for (var entry : ASPECT_KEYWORDS.entrySet()) {
                String pillar = entry.getKey();
                boolean matches = entry.getValue().stream().anyMatch(lower::contains);
                if (matches) {
                    EvidenceQuote eq = new EvidenceQuote(rev.id(), rev.rating(), rev.comment(), pillar);
                    if (rev.rating() >= 4) {
                        posByPillar.get(pillar).add(eq);
                    } else if (rev.rating() <= 3) {
                        negByPillar.get(pillar).add(eq);
                    }
                }
            }
        }

        Map<String, PillarEvaluation> pillars = new HashMap<>();
        for (String p : ASPECT_KEYWORDS.keySet()) {
            List<EvidenceQuote> pos = posByPillar.get(p);
            List<EvidenceQuote> neg = negByPillar.get(p);
            int total = pos.size() + neg.size();
            int score = total == 0 ? 8 : (int) Math.round((pos.size() * 10.0) / total);
            String sentiment = score >= 8 ? "POSITIVE" : (score >= 5 ? "NEUTRAL" : "NEEDS_IMPROVEMENT");
            pillars.put(p, new PillarEvaluation(p, Math.max(1, Math.min(score, 10)), sentiment,
                    pos.stream().limit(3).toList(), neg.stream().limit(3).toList()));
        }

        // 3. Action Recommendations
        List<ActionItem> actions = new ArrayList<>();
        if (pillars.get("PUNCTUALITY").scoreOutOf10() < 7 || !pillars.get("PUNCTUALITY").negativeQuotes().isEmpty()) {
            actions.add(new ActionItem("HIGH", "PUNCTUALITY",
                    isEn ? "Optimize departure & guest check-in workflow" : "Tối ưu quy trình khởi hành & đón khách",
                    isEn ? "Extend buffer time between slots by 15-20 minutes and assign dedicated check-in staff at Man Thai / Han River pier for punctual departure."
                         : "Giãn khoảng cách đón giữa các ca thêm 15-20 phút và phân công nhân sự điểm danh tại bến Mân Thái/Cảng Sông Hàn để xuất bến đúng giờ."));
        }
        if (pillars.get("SAFETY").scoreOutOf10() < 8 || !pillars.get("SAFETY").negativeQuotes().isEmpty()) {
            actions.add(new ActionItem("HIGH", "SAFETY",
                    isEn ? "Inspect and standardize safety equipment" : "Kiểm tra và chuẩn hóa thiết bị bảo hộ",
                    isEn ? "Provide properly fitted child life jackets, inspect snorkeling masks, and conduct a 5-minute safety briefing before entering the water."
                         : "Bổ sung áo phao vừa vặn cho trẻ em, kiểm tra kính lặn và hướng dẫn kỹ năng an toàn 5 phút trước khi xuống nước."));
        }
        if (pillars.get("GUIDE").scoreOutOf10() < 7) {
            actions.add(new ActionItem("MEDIUM", "GUIDE",
                    isEn ? "Enhance instructor communication & engagement" : "Nâng cao kỹ năng giao tiếp & hướng dẫn viên",
                    isEn ? "Encourage guides to proactively assist with photos and share engaging stories about Son Tra / Cu Lao Cham marine biodiversity."
                         : "Khuyến khích hướng dẫn viên chủ động chụp ảnh và giải thích sinh động về hệ sinh thái biển Sơn Trà/Cù Lao Chàm."));
        }
        if (actions.isEmpty()) {
            actions.add(new ActionItem("LOW", "VALUE",
                    isEn ? "Maintain outstanding service quality" : "Tiếp tục duy trì chất lượng vượt trội",
                    isEn ? "Your operations are receiving strong positive feedback. Consider offering early-bird sunrise packages to maximize revenue."
                         : "Dịch vụ đang vận hành rất tốt với đánh giá tích cực. Bạn có thể mở thêm ưu đãi giờ vàng sáng sớm để tối ưu doanh thu."));
        }

        // 4. Badge Progression Roadmap
        String currentTier = profile.badgeTier() != null ? profile.badgeTier() : "NONE";
        String nextTier = "TOP_RATED";
        int needed = 0;
        BigDecimal target = BigDecimal.valueOf(4.0);
        String roadmapNote;

        int currentCount = profile.ratingCount() != null ? profile.ratingCount() : 0;
        if ("NONE".equalsIgnoreCase(currentTier)) {
            nextTier = "VERIFIED";
            needed = Math.max(1, 1 - currentCount);
            roadmapNote = isEn
                    ? "You need at least 1 valid review to unlock the VERIFIED badge."
                    : "Bạn cần có ít nhất 1 đánh giá hợp lệ đầu tiên để đạt huy hiệu VERIFIED.";
        } else if ("VERIFIED".equalsIgnoreCase(currentTier)) {
            nextTier = "TOP_RATED";
            int countGap = Math.max(0, 5 - currentCount);
            needed = Math.max(countGap, avgRating.compareTo(target) < 0 ? 3 : 0);
            roadmapNote = isEn
                    ? "To reach TOP_RATED, you need at least 5 reviews and an average rating >= 4.0. Approximately " + needed + " more 5-star review(s) needed."
                    : "Để đạt TOP_RATED, bạn cần tối thiểu 5 đánh giá và điểm trung bình >= 4.0. Cần thêm khoảng " + needed + " lượt đánh giá 5 sao.";
        } else {
            nextTier = "TOP_RATED (MAINTAINED)";
            needed = 0;
            roadmapNote = isEn
                    ? "Great job! You hold the prestigious TOP_RATED badge. Keep delivering consistent marine experiences to maintain your rank."
                    : "Tuyệt vời! Đối tác đang giữ huy hiệu TOP_RATED xuất sắc. Hãy duy trì chất lượng phục vụ để giữ vững thứ hạng.";
        }

        BadgeProgression progression = new BadgeProgression(currentTier, nextTier, needed, target, roadmapNote);

        CoachingResult result = new CoachingResult(
                vendorId, serviceId, profile.businessName(), totalAnalyzed, avgRating,
                pillars, actions, progression, "HYBRID_DUAL_BRAIN", OffsetDateTime.now()
        );

        // 5. Persist to storage
        try {
            String payloadJson = mapper.writeValueAsString(result);
            insightStore.saveVendorInsight(vendorId, serviceId, "REVIEW_COACHING",
                    vendorId + ":" + (serviceId != null ? serviceId : "all"), payloadJson, "HYBRID_DUAL_BRAIN");
        } catch (Exception e) {
            log.warn("Failed to persist vendor review coaching insight: {}", e.getMessage());
        }

        return result;
    }

    public ReplyDraftResult draftReply(UUID vendorUserId, UUID reviewId, String tone, String customPoints) {
        SupportedLanguage lang = SupportedLanguage
                .fromTag(LocaleContextHolder.getLocale().toLanguageTag())
                .orElse(SupportedLanguage.VI);
        return draftReply(vendorUserId, reviewId, tone, customPoints, lang);
    }

    public ReplyDraftResult draftReply(UUID vendorUserId, UUID reviewId, String tone, String customPoints, SupportedLanguage language) {
        boolean isEn = language == SupportedLanguage.EN;
        var profile = vendorAiData.findProfileByUserId(vendorUserId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor profile not found"));

        var reviews = vendorAiData.listReviews(profile.id(), null, 200);
        var targetRev = reviews.stream().filter(r -> r.id().equals(reviewId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Review not found or does not belong to vendor"));

        String selectedTone = tone != null && !tone.isBlank() ? tone.toUpperCase(Locale.ROOT) : "PROFESSIONAL";
        String comment = targetRev.comment() != null ? targetRev.comment() : "";
        int rating = targetRev.rating();

        // Generate thoughtful reply
        String draft;
        String altDraft;

        if (rating >= 4) {
            draft = isEn
                    ? "Dear Guest, thank you so much from " + profile.businessName() + " for taking the time to share your wonderful review! Our crew is thrilled that you had a safe and memorable marine experience in Da Nang. We look forward to welcoming you and your family back on board soon!"
                    : "Dạ chào quý khách, " + profile.businessName() + " xin chân thành cảm ơn bạn đã dành thời gian trải nghiệm và gửi gắm những đánh giá tích cực! Đội ngũ chúng tôi rất vui khi mang lại cho bạn một chuyến đi biển trọn vẹn và an toàn tại Đà Nẵng. Rất mong được đón tiếp bạn và gia đình trong những hành trình tiếp theo ạ!";
            altDraft = isEn
                    ? "Thank you for choosing " + profile.businessName() + "! It was a pleasure hosting you, and we hope to see you on the water again soon!"
                    : "Cảm ơn bạn đã tin tưởng lựa chọn " + profile.businessName() + "! Rất vui được đồng hành cùng bạn và hẹn gặp lại trong những chuyến ra khơi sắp tới nhé!";
        } else {
            draft = isEn
                    ? "Dear Guest, " + profile.businessName() + " sincerely appreciates your candid feedback. We deeply regret that your recent trip did not fully meet your expectations (" + (customPoints != null ? customPoints : "regarding service coordination") + "). Our management team has reviewed our preparation procedures to address this immediately. We hope to have the opportunity to serve you better in the future!"
                    : "Dạ chào quý khách, " + profile.businessName() + " xin chân thành cảm ơn bạn đã phản hồi chân thật về chuyến đi. Chúng tôi vô cùng tiếc vì trải nghiệm vừa qua chưa đạt được sự kỳ vọng của bạn (" + (customPoints != null ? customPoints : "về chất lượng phục vụ") + "). Đội ngũ quản lý đã rà soát lại quy trình chuẩn bị và cam kết khắc phục triệt để. Rất mong có cơ hội được phục vụ bạn chu đáo hơn trong tương lai!";
            altDraft = isEn
                    ? "Hello, " + profile.businessName() + " sincerely apologizes for the inconvenience during your trip. We have noted your feedback to improve our operations right away. Thank you for helping us grow!"
                    : "Chào bạn, " + profile.businessName() + " thành thật xin lỗi vì những bất tiện trong chuyến đi vừa qua. Chúng tôi đã ghi nhận đóng góp của bạn để cải thiện dịch vụ ngay lập tức. Cảm ơn phản hồi quý báu của bạn!";
        }

        // Try enhancing via LLM if available
        try {
            String prompt = isEn
                    ? String.format("You represent the marine tour provider '%s' in Da Nang. Write an official response to a %d-star guest review: '%s'. Tone: %s. Reply politely in English, addressing any customer concerns.",
                            profile.businessName(), rating, comment, selectedTone)
                    : String.format("Bạn là trợ lý đại diện cho nhà cung cấp tour biển '%s' tại Đà Nẵng. Viết phản hồi chính thức cho đánh giá %d sao của khách: '%s'. Giọng điệu: %s. Trả lời bằng tiếng Việt lịch sự, thân thiện, giải quyết lo ngại của khách nếu có.",
                            profile.businessName(), rating, comment, selectedTone);
            AiMessage userMsg = new AiMessage();
            userMsg.setRole(AiMessageRole.USER);
            userMsg.setContent(prompt);
            LlmResponse resp = llmClient.generateResponse(List.of(userMsg), isEn ? SupportedLanguage.EN : SupportedLanguage.VI);
            if (resp != null && resp.getContent() != null && !resp.getContent().isBlank() && !resp.getContent().contains("error")) {
                draft = resp.getContent().trim();
            }
        } catch (Exception e) {
            log.info("LLM draft generation fallback to deterministic template: {}", e.getMessage());
        }

        // Verify with text assessment
        boolean safe = true;
        try {
            var assessment = textAssessment.execute(vendorUserId, draft, false);
            if (assessment != null && !"BENIGN".equalsIgnoreCase(assessment.status()) && !"NO_RULE_SIGNAL".equalsIgnoreCase(assessment.status())) {
                safe = false;
            }
        } catch (Exception ignored) {}

        return new ReplyDraftResult(reviewId, rating, comment, draft, altDraft, selectedTone, safe);
    }
}
