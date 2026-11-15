package com.danasea.backend.modules.ai.application.usecases;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.danasea.backend.modules.ai.application.ports.AdminAiDataPort;
import com.danasea.backend.modules.ai.application.ports.AiExecutionBudget;
import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort;
import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort.CopilotConversation;
import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort.CopilotMessage;
import com.danasea.backend.modules.ai.application.ports.LlmClientPort;
import com.danasea.backend.modules.ai.application.ports.ModerationPort;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort;
import com.danasea.backend.modules.ai.application.tools.AdminFeatureTool;
import com.danasea.backend.modules.ai.application.tools.ToolExecutionContext;
import com.danasea.backend.modules.ai.application.tools.ToolExecutor;
import com.danasea.backend.modules.ai.application.tools.VendorFeatureTool;
import com.danasea.backend.modules.ai.domain.models.AiMessage;
import com.danasea.backend.modules.ai.domain.models.AiMessageRole;
import com.danasea.backend.modules.ai.domain.models.LlmResponse;
import com.danasea.backend.modules.report.domain.models.TimeRange;
import com.danasea.backend.shared.i18n.LocalizedException;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RoleCopilotUseCase {

    public record CopilotChatResponse(
            String status,
            UUID conversationId,
            String message,
            String role,
            List<Map<String, Object>> artifacts,
            List<String> toolsUsed,
            boolean generatedTextVerified
    ) {}

    private static final int MAX_MESSAGE_CHARS = 2000;

    private static final Pattern ADMIN_REVENUE = CopilotRequest.words("hoa hong|commission|tien da thu|cash collected|"
            + "tien hoan|so tien hoan|refunds|^tien$");
    private static final Pattern ADMIN_CANCELLATION = CopilotRequest.words("huy|ly do|thoi tiet|cancel|cancellations?|weather");
    private static final Pattern ADMIN_DIAGNOSTICS = CopilotRequest.words("vendor|doi tac|chan doan|canh bao|diagnostic");
    private static final Pattern ADMIN_OVERVIEW = CopilotRequest.words("dashboard|tong quan|suc khoe|overview");
    private static final Pattern VENDOR_REVIEW = CopilotRequest.words("reviews?|danh gia|diem yeu|diem manh|coaching");
    private static final Pattern VENDOR_SLOT = CopilotRequest.words("slots?|ca|cong suat|thoi tiet|capacity|weather");
    private static final Pattern VENDOR_LISTING = CopilotRequest.words("bai dang|mo ta|toi uu|tieu de|listing|optimize");
    private static final Pattern VENDOR_OVERVIEW = CopilotRequest.words("kpi|tong quan|summary");
    private final AiExtendedInsightStorePort insightStore;
    private final LlmClientPort llmClient;
    private final ModerationPort moderationPort;
    private final ObjectMapper mapper;

    private final Map<String, ToolExecutor> vendorToolMap = new HashMap<>();
    private final Map<String, ToolExecutor> adminToolMap = new HashMap<>();

    public RoleCopilotUseCase(
            VendorAiDataPort vendorAiData,
            VendorReviewCoachingUseCase reviewCoaching,
            VendorSlotAdvisorUseCase slotAdvisor,
            VendorListingOptimizerUseCase listingOptimizer,
            AdminAiDataPort adminAiData,
            AdminDisputeSummaryUseCase disputeSummaryUseCase,
            AiExtendedInsightStorePort insightStore,
            LlmClientPort llmClient,
            ModerationPort moderationPort,
            ObjectMapper mapper
    ) {
        this.insightStore = insightStore;
        this.llmClient = llmClient;
        this.moderationPort = moderationPort;
        this.mapper = mapper;

        // Register Vendor Tools
        registerVendorTool("vendor_dashboard_summary", vendorAiData, reviewCoaching, slotAdvisor, listingOptimizer);
        registerVendorTool("vendor_revenue_and_bookings", vendorAiData, reviewCoaching, slotAdvisor, listingOptimizer);
        registerVendorTool("vendor_service_count", vendorAiData, reviewCoaching, slotAdvisor, listingOptimizer);
        registerVendorTool("vendor_review_coaching", vendorAiData, reviewCoaching, slotAdvisor, listingOptimizer);
        registerVendorTool("vendor_slot_advisor", vendorAiData, reviewCoaching, slotAdvisor, listingOptimizer);
        registerVendorTool("vendor_optimize_listing", vendorAiData, reviewCoaching, slotAdvisor, listingOptimizer);

        // Register Admin Tools
        registerAdminTool("admin_platform_dashboard", adminAiData, disputeSummaryUseCase);
        registerAdminTool("admin_revenue_timeseries", adminAiData, disputeSummaryUseCase);
        registerAdminTool("admin_booking_cancellation_breakdown", adminAiData, disputeSummaryUseCase);
        registerAdminTool("admin_vendor_diagnostics", adminAiData, disputeSummaryUseCase);
        registerAdminTool("admin_dispute_summarize", adminAiData, disputeSummaryUseCase);
        registerAdminTool("admin_risk_and_support_queue_overview", adminAiData, disputeSummaryUseCase);
    }

    private void registerVendorTool(String name, VendorAiDataPort data, VendorReviewCoachingUseCase r, VendorSlotAdvisorUseCase s, VendorListingOptimizerUseCase l) {
        vendorToolMap.put(name, new VendorFeatureTool(name, data, r, s, l, mapper));
    }

    private void registerAdminTool(String name, AdminAiDataPort data, AdminDisputeSummaryUseCase d) {
        adminToolMap.put(name, new AdminFeatureTool(name, data, d, mapper));
    }

    // ==================== SESSION MANAGEMENT ====================

    public List<CopilotConversation> listConversations(UUID actorUserId, String actorRole, UUID vendorId, Boolean archived, int limit) {
        return insightStore.listCopilotConversations(actorUserId, actorRole, vendorId, archived, limit);
    }

    public List<CopilotMessage> getConversationHistory(UUID id, UUID actorUserId, String actorRole, UUID vendorId) {
        requireConversationScope(id, actorUserId, actorRole, vendorId);
        return insightStore.listCopilotMessages(id);
    }

    public CopilotConversation updateConversationMeta(UUID id, UUID actorUserId, String actorRole, UUID vendorId,
            String title, Boolean pinned, Boolean archived) {
        requireConversationScope(id, actorUserId, actorRole, vendorId);
        return insightStore.updateCopilotConversationMeta(id, actorUserId, title, pinned, archived);
    }

    public void deleteConversation(UUID id, UUID actorUserId, String actorRole, UUID vendorId) {
        requireConversationScope(id, actorUserId, actorRole, vendorId);
        insightStore.softDeleteCopilotConversation(id, actorUserId);
    }

    private CopilotConversation requireConversationScope(UUID id, UUID actorUserId, String actorRole, UUID vendorId) {
        var conversation = insightStore.getCopilotConversation(id)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        if (!conversation.actorUserId().equals(actorUserId) || !conversation.actorRole().equals(actorRole)
                || !Objects.equals(conversation.vendorId(), vendorId)) {
            throw new AccessDeniedException("Access to conversation denied");
        }
        return conversation;
    }

    // ==================== COPILOT CHAT PROCESSING ====================

    public CopilotChatResponse processMessage(
            UUID actorUserId, String actorRole, UUID vendorId, UUID conversationId,
            String messageContent, String contextType, UUID contextId, SupportedLanguage language
    ) {
        try (var budget = AiExecutionBudget.open(AiExecutionBudget.LLM_REQUEST_TIMEOUT, 6)) {
            return processWithinBudget(actorUserId, actorRole, vendorId, conversationId, messageContent, contextType, contextId, language);
        }
    }

    private CopilotChatResponse processWithinBudget(
            UUID actorUserId, String actorRole, UUID vendorId, UUID conversationId,
            String messageContent, String contextType, UUID contextId, SupportedLanguage language
    ) {
        boolean admin = "ADMIN".equals(actorRole);
        if (!admin && (!"VENDOR".equals(actorRole) || vendorId == null)) {
            throw new AccessDeniedException("Copilot role or vendor scope is invalid");
        }
        SupportedLanguage effectiveLanguage = language != null ? language : SupportedLanguage.VI;
        String effectiveContent = messageContent != null ? messageContent.trim() : "";
        if (effectiveContent.length() > MAX_MESSAGE_CHARS) {
            int end = Character.isHighSurrogate(effectiveContent.charAt(MAX_MESSAGE_CHARS - 1))
                    ? MAX_MESSAGE_CHARS - 1 : MAX_MESSAGE_CHARS;
            effectiveContent = effectiveContent.substring(0, end);
        }

        if (!moderationPort.isSafe(effectiveContent)) {
            throw new LocalizedException("AI_MODERATION_BLOCKED", "ai.moderation.blocked");
        }

        // 1. Get or create conversation (Multi-Session Thread)
        boolean isEn = effectiveLanguage == SupportedLanguage.EN;
        CopilotConversation conversation;
        if (conversationId != null) {
            conversation = requireConversationScope(conversationId, actorUserId, actorRole, vendorId);
        } else {
            conversation = insightStore.createCopilotConversation(
                    actorUserId, actorRole, vendorId, effectiveLanguage.code(),
                    null, contextType, contextId);
        }

        UUID convId = conversation.id();

        // 2. Auto-generate title if newly created or empty
        insightStore.autoGenerateCopilotTitleIfEmpty(convId, effectiveContent);

        // 3. Append User Message
        insightStore.appendCopilotMessage(convId, "USER", effectiveContent, null, null, true);

        List<Map<String, Object>> artifacts = new ArrayList<>();
        List<String> toolsUsed = new ArrayList<>();
        var toolContext = new ToolExecutionContext(effectiveLanguage, convId, actorUserId);
        String assistantResponse = answer(effectiveContent, admin, isEn, toolContext, artifacts, toolsUsed);

        // 6. Persist Assistant Message
        String artifactsJson = null;
        try {
            if (!artifacts.isEmpty()) artifactsJson = mapper.writeValueAsString(artifacts);
        } catch (Exception ignored) {}

        String toolsUsedStr = toolsUsed.isEmpty() ? null : String.join(",", toolsUsed);
        insightStore.appendCopilotMessage(convId, "ASSISTANT", assistantResponse, artifactsJson, toolsUsedStr, false);

        return new CopilotChatResponse(
                "success",
                convId,
                assistantResponse,
                "ASSISTANT",
                artifacts,
                toolsUsed,
                false
        );
    }

    private String answer(
            String message, boolean admin, boolean english, ToolExecutionContext context,
            List<Map<String, Object>> artifacts, List<String> toolsUsed
    ) {
        String normalized = CopilotRequest.normalize(message);
        LocalDate today = LocalDate.now(TimeRange.VIETNAM_ZONE);
        if (!admin && CopilotRequest.isOutsideVendorScope(normalized)) {
            return english
                    ? "I can show data for your shop. Platform-wide reports and other vendors' data are available to administrators."
                    : "Tôi có thể tra cứu số liệu của gian hàng bạn. Báo cáo toàn sàn và số liệu của gian hàng khác dành cho quản trị viên.";
        }
        if (CopilotRequest.isPersonalBooking(normalized)) {
            return english ? "Use the Customer Assistant to find or book a tour."
                    : "Bạn có thể tìm và đặt tour trong Trợ lý khách hàng.";
        }
        if (CopilotRequest.isDateQuestion(normalized) && !CopilotRequest.isRevenue(normalized)
                && !CopilotRequest.isServiceCount(normalized)) {
            var format = DateTimeFormatter.ofPattern(english ? "'Today is' EEEE, MMMM d, uuuu." : "'Hôm nay là' EEEE, dd/MM/uuuu.",
                    english ? Locale.ENGLISH : Locale.forLanguageTag("vi-VN"));
            return today.format(format);
        }

        String tool = selectTool(normalized, admin);
        if (tool == null && CopilotRequest.isPeriodFollowUp(normalized)) {
            String revenueTool = admin ? "admin_revenue_timeseries" : "vendor_dashboard_summary";
            if (lastReplyUsedTool(context.conversationId(), revenueTool)) tool = revenueTool;
        }
        if (tool == null) return generateText(admin, english, context, today);

        TimeRange period = null;
        String args = switch (tool) {
            case "vendor_slot_advisor" -> "{\"horizonDays\":7}";
            default -> "{}";
        };
        if ("admin_revenue_timeseries".equals(tool) || "vendor_dashboard_summary".equals(tool)) {
            try {
                period = CopilotRequest.revenuePeriod(message, normalized, today);
            } catch (DateTimeException | IllegalArgumentException exception) {
                return english ? "Please enter a valid reporting period."
                        : "Vui lòng nhập khoảng ngày báo cáo hợp lệ.";
            }
            args = mapper.createObjectNode().put("from", period.getFrom().toString())
                    .put("to", period.getTo().toString()).toString();
        }

        JsonNode data = runToolAndCollectArtifact(tool, args, admin ? adminToolMap : vendorToolMap,
                context, artifacts, toolsUsed);
        if (data == null) return CopilotReportAnswer.unavailable(english);
        if (period != null) {
            artifacts.getLast().put("period", Map.of("from", period.getFrom().toString(), "to", period.getTo().toString()));
        }

        return switch (tool) {
            case "vendor_dashboard_summary" -> CopilotReportAnswer.vendorRevenue(data, period, english);
            case "admin_revenue_timeseries" -> CopilotReportAnswer.adminRevenue(data, period, english);
            case "vendor_service_count" -> CopilotReportAnswer.vendorServices(data,
                    CopilotRequest.isPublishedCount(normalized), english);
            default -> english ? "I retrieved the operational data. See the details below."
                    : "Tôi đã tra cứu dữ liệu vận hành. Bạn có thể xem chi tiết bên dưới.";
        };
    }

    private boolean lastReplyUsedTool(UUID conversationId, String tool) {
        return insightStore.listCopilotMessages(conversationId).reversed().stream()
                .filter(message -> "ASSISTANT".equals(message.role()))
                .findFirst().map(message -> tool.equals(message.toolsUsed())).orElse(false);
    }

    private String selectTool(String text, boolean admin) {
        if (admin) {
            if (CopilotRequest.isRevenue(text) || CopilotRequest.matches(ADMIN_REVENUE, text)) return "admin_revenue_timeseries";
            if (CopilotRequest.matches(ADMIN_CANCELLATION, text)) return "admin_booking_cancellation_breakdown";
            if (CopilotRequest.matches(ADMIN_DIAGNOSTICS, text)) return "admin_vendor_diagnostics";
            if (CopilotRequest.matches(ADMIN_OVERVIEW, text)) return "admin_platform_dashboard";
        } else {
            if (CopilotRequest.isRevenue(text)) return "vendor_dashboard_summary";
            if (CopilotRequest.isServiceCount(text)) return "vendor_service_count";
            if (CopilotRequest.matches(VENDOR_REVIEW, text)) return "vendor_review_coaching";
            if (CopilotRequest.matches(VENDOR_SLOT, text)) return "vendor_slot_advisor";
            if (CopilotRequest.matches(VENDOR_LISTING, text)) return "vendor_optimize_listing";
            if (CopilotRequest.matches(VENDOR_OVERVIEW, text)) return "vendor_dashboard_summary";
        }
        return null;
    }

    private String generateText(boolean admin, boolean english, ToolExecutionContext context, LocalDate today) {
        List<AiMessage> messages = insightStore.listCopilotMessages(context.conversationId()).stream()
                .map(message -> {
                    var modelMessage = new AiMessage();
                    modelMessage.setRole("USER".equals(message.role()) ? AiMessageRole.USER : AiMessageRole.ASSISTANT);
                    modelMessage.setContent(message.content());
                    return modelMessage;
                }).toList();
        String scope = admin
                ? "You are DANASEA Admin BI & Operations Copilot. Help administrators interpret platform operations, vendor performance, financial reports and disputes."
                : "You are DANASEA Vendor Growth & Operations Copilot. Help this vendor manage their own listings, reviews, bookings, operating schedule and sales.";
        String systemPrompt = scope + " The current date in Da Nang is " + today + "."
                + " Only provide guidance within your role. For finding or booking a personal tour, direct the user to the Customer Assistant."
                + " For live data or a specific report, ask the user to specify the requested metric and period."
                + " Do not invent figures, claim to have queried a report, or treat prior chat as a current data source."
                + (english ? " Reply concisely in English." : " Reply concisely in Vietnamese.");
        try {
            LlmResponse response = llmClient.generateText(messages, context.language(), systemPrompt);
            if (response != null && "ANSWERED".equals(response.getResponseStatus())
                    && (response.getToolCalls() == null || response.getToolCalls().isEmpty())
                    && response.getContent() != null && !response.getContent().isBlank()) {
                return response.getContent().trim();
            }
        } catch (RuntimeException exception) {
            log.info("Copilot text generation unavailable ({})", exception.getClass().getSimpleName());
        }
        return english ? "I could not answer this request right now. Please try again."
                : "Tôi chưa thể trả lời yêu cầu này lúc này. Vui lòng thử lại.";
    }

    private JsonNode runToolAndCollectArtifact(
            String toolName, String args, Map<String, ToolExecutor> activeTools,
            ToolExecutionContext context, List<Map<String, Object>> artifacts, List<String> toolsUsed
    ) {
        try {
            String rawJson = activeTools.get(toolName).execute(args, context);
            if (rawJson == null) return null;
            JsonNode result = mapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(rawJson);
            if (result == null || result.isNull()) return null;
            if (result.has("error")) {
                log.warn("Copilot tool {} returned {}", toolName, result.path("error").asText());
                return null;
            }
            Map<String, Object> artifact = new LinkedHashMap<>();
            artifact.put("tool", toolName);
            artifact.put("type", deriveArtifactType(toolName));
            artifact.put("data", mapper.readerFor(Object.class)
                    .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readValue(result.traverse(mapper)));
            artifacts.add(artifact);
            toolsUsed.add(toolName);
            return result;
        } catch (Exception exception) {
            log.warn("Copilot tool {} unavailable ({})", toolName, exception.getClass().getSimpleName());
            return null;
        }
    }

    private String deriveArtifactType(String toolName) {
        return switch (toolName) {
            case "admin_revenue_timeseries" -> "ADMIN_REVENUE_CHART_CARD";
            case "admin_booking_cancellation_breakdown" -> "ADMIN_CANCELLATION_BREAKDOWN_CARD";
            case "admin_vendor_diagnostics" -> "ADMIN_CRITICAL_VENDORS_TABLE";
            case "admin_dispute_summarize" -> "ADMIN_DISPUTE_BRIEF_CARD";
            case "admin_platform_dashboard", "admin_risk_and_support_queue_overview" -> "ADMIN_KPI_SUMMARY_CARD";
            case "vendor_dashboard_summary", "vendor_revenue_and_bookings" -> "VENDOR_KPI_SUMMARY_CARD";
            case "vendor_service_count" -> "VENDOR_SERVICE_COUNT_CARD";
            case "vendor_review_coaching" -> "VENDOR_REVIEW_COACHING_CARD";
            case "vendor_slot_advisor" -> "VENDOR_SLOT_ADVISORY_CARD";
            case "vendor_optimize_listing" -> "VENDOR_LISTING_DRAFT_CARD";
            default -> "TOOL_RESULT_CARD";
        };
    }
}
