package com.danasea.backend.modules.ai.application.tools;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort;
import com.danasea.backend.modules.ai.application.usecases.AssessTextUseCase;
import com.danasea.backend.modules.ai.application.usecases.VendorListingOptimizerUseCase;
import com.danasea.backend.modules.ai.application.usecases.VendorReviewCoachingUseCase;
import com.danasea.backend.modules.ai.application.usecases.VendorSlotAdvisorUseCase;
import com.danasea.backend.modules.ai.domain.models.DecisionResult;
import com.danasea.backend.modules.ai.domain.services.TravelQueryParser;
import com.danasea.backend.modules.report.domain.models.TimeRange;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class VendorFeatureTool implements ToolExecutor {

    private static final int MAX_ASSESS_TEXT_CHARS = 2500;
    private static final Pattern NEGATED_PAYMENT_WARNING = Pattern.compile(
            "^(?:(?:vui long |xin |luu y |chu y )?(?:khong|khong nen|tuyet doi khong|dung|tranh) "
                    + "(?:chuyen tien|chuyen khoan|thanh toan|tra tien|giao dich) (?:ben )?ngoai (?:nen tang |san )?danasea"
                    + "|(?:please )?(?:do not|never) (?:pay|transfer money) outside (?:the )?danasea(?: platform)?)[.!。]*$");
    private static final Pattern EXTERNAL_CONTACT_SOLICITATION = Pattern.compile(
            "\\b(?:them so dien thoai|chua so dien thoai|so dien thoai ca nhan|sdt ca nhan|"
                    + "duong link dat tour|link dat tour|link ben ngoai|them link|chua link|"
                    + "external link|phone number)\\b");
    private static final Pattern NEGATED_PAYMENT_IN_SENTENCE = Pattern.compile(
            "\\b(?:khong|khong nen|tuyet doi khong|dung|tranh|do not|never) "
                    + "(?:chuyen tien|chuyen khoan|thanh toan|tra tien|giao dich|pay|transfer)\\b");
    private static final Pattern EXTERNAL_PAYMENT_MENTION = Pattern.compile(
            "\\b(?:chuyen khoan|chuyen tien|thanh toan|tra tien|transfer money|pay)\\b.*\\b(?:ngoai danasea|ngoai san|ngoai nen tang|ben ngoai|outside danasea|off platform)\\b");
    private static final Pattern EXCESSIVE_PROMOTION = Pattern.compile(
            "\\b(?:quang cao qua muc|loi quang cao qua muc|phong dai|cam ket qua muc|excessive advertising|exaggerated)\\b");

    private final String name;
    private final VendorAiDataPort vendorAiData;
    private final VendorReviewCoachingUseCase reviewCoaching;
    private final VendorSlotAdvisorUseCase slotAdvisor;
    private final VendorListingOptimizerUseCase listingOptimizer;
    private final ObjectMapper mapper;
    private final AssessTextUseCase assessTextUseCase;

    public VendorFeatureTool(
            String name,
            VendorAiDataPort vendorAiData,
            VendorReviewCoachingUseCase reviewCoaching,
            VendorSlotAdvisorUseCase slotAdvisor,
            VendorListingOptimizerUseCase listingOptimizer,
            ObjectMapper mapper
    ) {
        this(name, vendorAiData, reviewCoaching, slotAdvisor, listingOptimizer, mapper, null);
    }

    public VendorFeatureTool(
            String name,
            VendorAiDataPort vendorAiData,
            VendorReviewCoachingUseCase reviewCoaching,
            VendorSlotAdvisorUseCase slotAdvisor,
            VendorListingOptimizerUseCase listingOptimizer,
            ObjectMapper mapper,
            AssessTextUseCase assessTextUseCase
    ) {
        this.name = name;
        this.vendorAiData = vendorAiData;
        this.reviewCoaching = reviewCoaching;
        this.slotAdvisor = slotAdvisor;
        this.listingOptimizer = listingOptimizer;
        this.mapper = mapper;
        this.assessTextUseCase = assessTextUseCase;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String execute(String argumentsJson) {
        return execute(argumentsJson, null);
    }

    @Override
    public String execute(String argumentsJson, ToolExecutionContext context) {
        try {
            UUID userId = context != null ? context.userId() : null;
            if (userId == null) {
                return error("AUTHENTICATION_REQUIRED", "Vendor authentication required for this tool");
            }
            var profileOpt = vendorAiData.findProfileByUserId(userId);
            if (profileOpt.isEmpty()) {
                return error("VENDOR_PROFILE_NOT_FOUND", "No vendor profile associated with the user");
            }
            UUID vendorId = profileOpt.get().id();

            JsonNode args = argumentsJson != null && !argumentsJson.isBlank()
                    ? mapper.readTree(argumentsJson) : mapper.createObjectNode();

            UUID serviceId = args.hasNonNull("serviceId") ? UUID.fromString(args.get("serviceId").asText()) : null;
            SupportedLanguage language = context != null && context.language() != null ? context.language() : SupportedLanguage.VI;

            Object result = switch (name) {
                case "vendor_dashboard_summary", "vendor_revenue_and_bookings" -> {
                    LocalDate today = LocalDate.now(TimeRange.VIETNAM_ZONE);
                    LocalDate from = args.hasNonNull("from") ? LocalDate.parse(args.get("from").asText()) : today.minusDays(30);
                    LocalDate to = args.hasNonNull("to") ? LocalDate.parse(args.get("to").asText()) : today;
                    yield vendorAiData.getPerformanceMetrics(vendorId, from, to);
                }
                case "vendor_service_count" -> {
                    var counts = vendorAiData.countServicesByStatus(vendorId);
                    yield Map.of("total", counts.values().stream().mapToLong(Long::longValue).sum(), "byStatus", counts);
                }
                case "vendor_review_coaching" -> reviewCoaching.execute(userId, serviceId, null, null, language);
                case "vendor_review_reply_draft" -> reviewCoaching.draftReply(userId,
                        UUID.fromString(args.path("reviewId").asText()), "EMPATHETIC", null, language);
                case "vendor_slot_advisor" -> slotAdvisor.execute(userId, serviceId, args.path("horizonDays").asInt(7), language);
                case "vendor_optimize_listing" -> {
                    String title = args.path("title").asText("");
                    String desc = args.path("description").asText("");
                    if (serviceId != null) {
                        var service = vendorAiData.getService(vendorId, serviceId)
                                .orElseThrow(() -> new IllegalArgumentException("Listing is unavailable to this vendor"));
                        title = service.title();
                        desc = service.description();
                    }
                    yield listingOptimizer.execute(userId, new VendorListingOptimizerUseCase.OptimizeRequest(
                            serviceId, title, desc, List.of(), List.of(), List.of("VI", "EN")
                    ), language);
                }
                case "vendor_text_assessment" -> {
                    boolean persist = args.path("persist").asBoolean(false);
                    String text = args.hasNonNull("text") ? args.get("text").asText("") : "";
                    if (serviceId != null) {
                        var service = vendorAiData.getService(vendorId, serviceId)
                                .orElseThrow(() -> new IllegalArgumentException("Listing is unavailable to this vendor"));
                        if (text.isBlank()) {
                            String title = service.title() != null ? service.title() : "";
                            String desc = service.description() != null ? service.description() : "";
                            text = (title + "\n" + desc).trim();
                        }
                    }
                    yield assessContent(assessTextUseCase, userId, text, serviceId, persist);
                }
                default -> throw new IllegalArgumentException("Unknown vendor tool: " + name);
            };

            return mapper.writeValueAsString(result);
        } catch (IllegalArgumentException e) {
            return error("INVALID_ARGUMENTS", e.getMessage());
        } catch (Exception e) {
            return error("TOOL_EXECUTION_FAILED", "Failed to execute vendor tool: " + e.getMessage());
        }
    }

    static Map<String, Object> assessContent(
            AssessTextUseCase assessTextUseCase,
            UUID actorId,
            String rawText,
            UUID serviceId,
            boolean persist
    ) {
        String effectiveText = rawText == null ? "" : rawText.trim();
        if (effectiveText.isBlank()) {
            throw new IllegalArgumentException("text or serviceId is required for text assessment");
        }
        if (effectiveText.length() > MAX_ASSESS_TEXT_CHARS) {
            int end = Character.isHighSurrogate(effectiveText.charAt(MAX_ASSESS_TEXT_CHARS - 1))
                    ? MAX_ASSESS_TEXT_CHARS - 1 : MAX_ASSESS_TEXT_CHARS;
            effectiveText = effectiveText.substring(0, end);
        }

        AssessTextUseCase effectiveUseCase = assessTextUseCase;
        if (effectiveUseCase == null) {
            if (persist) {
                throw new IllegalStateException("Text assessment persistence is unavailable");
            }
            effectiveUseCase = new AssessTextUseCase((task, input) -> DecisionResult.unavailable(task, "FALLBACK"), null);
        }

        AssessTextUseCase.Result res = effectiveUseCase.execute(actorId, effectiveText, persist);
        String normalized = TravelQueryParser.normalize(effectiveText).replaceAll("[\\s-]+", " ").strip();
        boolean hasContacts = !res.contactMatches().isEmpty();
        boolean negatedPaymentWarning = !hasContacts && NEGATED_PAYMENT_WARNING.matcher(normalized).matches();

        List<String> reasonCodes = new ArrayList<>(res.reasonCodes());
        if (!negatedPaymentWarning) {
            if (EXTERNAL_CONTACT_SOLICITATION.matcher(normalized).find()
                    && !reasonCodes.contains("EXTERNAL_CONTACT_DETECTED")) {
                reasonCodes.add("EXTERNAL_CONTACT_SOLICITATION_SIGNAL");
            }
            if (EXTERNAL_PAYMENT_MENTION.matcher(normalized).find()
                    && !NEGATED_PAYMENT_IN_SENTENCE.matcher(normalized).find()
                    && !reasonCodes.contains("MODEL_SUGGESTS_EXTERNAL_PAYMENT")) {
                reasonCodes.add("EXTERNAL_PAYMENT_POLICY_SIGNAL");
            }
            if (EXCESSIVE_PROMOTION.matcher(normalized).find()) {
                reasonCodes.add("EXCESSIVE_PROMOTION_SIGNAL");
            }
        }

        boolean hasModelRisk = reasonCodes.stream().anyMatch(code ->
                code.startsWith("MODEL_SUGGESTS_") || "EXTERNAL_CONTACT_DETECTED".equals(code) || code.endsWith("_SIGNAL"));
        String status;
        if (negatedPaymentWarning && !hasModelRisk) {
            reasonCodes.remove("MODEL_UNAVAILABLE");
            status = "NO_RULE_SIGNAL";
        } else {
            status = !reasonCodes.isEmpty() ? "NEEDS_REVIEW" : res.status();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("reasonCodes", List.copyOf(reasonCodes));
        payload.put("contactMatches", res.contactMatches());
        payload.put("publicationAuthorized", false);
        payload.put("evidenceType", serviceId != null ? "SERVICE_LISTING" : res.evidenceType());
        payload.put("negatedWarningOnly", negatedPaymentWarning);
        payload.put("persisted", persist && res.caseRecord() != null);
        if (serviceId != null) {
            payload.put("serviceId", serviceId);
        }
        if (res.caseRecord() != null) {
            payload.put("caseId", res.caseRecord().id());
        }
        if (res.category() != null && res.category().available()) {
            payload.put("suggestedCategory", res.category().choice("category", null));
        }
        payload.put("modelAvailable", res.moderation() != null && res.moderation().available());
        payload.put("assessedTextPreview", effectiveText.length() > 240
                ? effectiveText.substring(0, 240) + "..." : effectiveText);
        return payload;
    }

    private String error(String code, String message) {
        try {
            return mapper.writeValueAsString(Map.of("error", code, "message", message));
        } catch (Exception e) {
            return "{\"error\":\"FEATURE_UNAVAILABLE\"}";
        }
    }
}
