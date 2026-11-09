package com.danasea.backend.modules.ai.application.usecases;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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
import com.danasea.backend.shared.i18n.LocalizedException;
import com.danasea.backend.shared.i18n.SupportedLanguage;
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

    private final VendorAiDataPort vendorAiData;
    private final AdminAiDataPort adminAiData;
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
        this.vendorAiData = vendorAiData;
        this.adminAiData = adminAiData;
        this.insightStore = insightStore;
        this.llmClient = llmClient;
        this.moderationPort = moderationPort;
        this.mapper = mapper;

        // Register Vendor Tools
        registerVendorTool("vendor_dashboard_summary", vendorAiData, reviewCoaching, slotAdvisor, listingOptimizer);
        registerVendorTool("vendor_revenue_and_bookings", vendorAiData, reviewCoaching, slotAdvisor, listingOptimizer);
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
        SupportedLanguage effectiveLanguage = language != null ? language : SupportedLanguage.VI;
        String effectiveContent = messageContent != null ? messageContent.trim() : "";
        if (effectiveContent.length() > MAX_MESSAGE_CHARS) {
            effectiveContent = effectiveContent.substring(0, MAX_MESSAGE_CHARS);
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

        // 4. Load past messages for LLM context
        List<CopilotMessage> history = insightStore.listCopilotMessages(convId);
        List<AiMessage> aiMessages = new ArrayList<>();

        // System prompt according to role & language
        AiMessage systemMsg = new AiMessage();
        systemMsg.setRole(AiMessageRole.USER);
        if ("ADMIN".equalsIgnoreCase(actorRole)) {
            systemMsg.setContent(isEn
                    ? "You are DANASEA Admin BI & Operations Copilot. Assist the Administrator in analyzing platform-wide financial metrics (GMV, net revenue, refunds, commission), explaining cancellation causes (marine weather vs vendor fault), diagnosing critical vendors, summarizing disputes, and providing objective operational insights. Respond concisely and professionally in English with factual data."
                    : "Bạn là DANASEA Admin BI & Operations Copilot. Nhiệm vụ của bạn là hỗ trợ Quản trị viên phân tích số liệu tài chính toàn sàn (GMV, doanh thu, hoàn tiền, hoa hồng), giải thích lý do hủy đơn (thời tiết vs lỗi đối tác), chẩn đoán vendor báo động đỏ, tóm tắt tranh chấp và cung cấp góc nhìn kinh doanh khách quan. Trả lời bằng tiếng Việt chuyên nghiệp, ngắn gọn, có dẫn chứng số liệu thực tế.");
        } else {
            systemMsg.setContent(isEn
                    ? "You are DANASEA Vendor Growth & Operations Copilot. Assist marine tour providers in Da Nang by tracking revenue KPIs, coaching on guest reviews across 4 pillars (Guide, Safety, Punctuality, Value), optimizing bilingual SEO listings, and advising on slot capacity based on marine forecasts. Respond empathetically and actionably in English."
                    : "Bạn là DANASEA Vendor Growth & Operations Copilot. Nhiệm vụ của bạn là trợ lý đồng hành cùng Đối tác cung cấp dịch vụ du lịch biển tại Đà Nẵng: theo dõi KPIs doanh thu, tư vấn cải thiện đánh giá review (4 trụ cột Hướng dẫn viên, An toàn, Đúng giờ, Giá trị), tối ưu bài đăng chuẩn SEO song ngữ và tư vấn mở ca slot theo dự báo thời tiết biển. Trả lời bằng tiếng Việt thân thiện, thấu hiểu, đưa ra các hành động cụ thể.");
        }
        aiMessages.add(systemMsg);

        for (CopilotMessage m : history) {
            AiMessage msg = new AiMessage();
            msg.setRole("USER".equalsIgnoreCase(m.role()) ? AiMessageRole.USER : AiMessageRole.ASSISTANT);
            msg.setContent(m.content());
            aiMessages.add(msg);
        }

        // 5. Tool Selection & Execution
        Map<String, ToolExecutor> activeTools = "ADMIN".equalsIgnoreCase(actorRole) ? adminToolMap : vendorToolMap;
        ToolExecutionContext toolContext = new ToolExecutionContext(effectiveLanguage, convId, actorUserId);

        List<Map<String, Object>> artifacts = new ArrayList<>();
        List<String> toolsUsed = new ArrayList<>();
        String assistantResponse = "";

        // Check keyword intents for instant deterministic dispatch if LLM is offline or requests tools
        String lowerMsg = effectiveContent.toLowerCase(Locale.ROOT);

        if ("ADMIN".equalsIgnoreCase(actorRole)) {
            if (lowerMsg.contains("doanh thu") || lowerMsg.contains("gmv") || lowerMsg.contains("tiền") || lowerMsg.contains("revenue")) {
                runToolAndCollectArtifact("admin_revenue_timeseries", "{}", activeTools, toolContext, artifacts, toolsUsed);
            } else if (lowerMsg.contains("hủy") || lowerMsg.contains("lý do") || lowerMsg.contains("thời tiết") || lowerMsg.contains("cancel") || lowerMsg.contains("weather")) {
                runToolAndCollectArtifact("admin_booking_cancellation_breakdown", "{}", activeTools, toolContext, artifacts, toolsUsed);
            } else if (lowerMsg.contains("vendor") || lowerMsg.contains("đối tác") || lowerMsg.contains("chẩn đoán") || lowerMsg.contains("cảnh báo") || lowerMsg.contains("diagnostic")) {
                runToolAndCollectArtifact("admin_vendor_diagnostics", "{}", activeTools, toolContext, artifacts, toolsUsed);
            } else if (lowerMsg.contains("dashboard") || lowerMsg.contains("tổng quan") || lowerMsg.contains("sức khỏe") || lowerMsg.contains("overview")) {
                runToolAndCollectArtifact("admin_platform_dashboard", "{}", activeTools, toolContext, artifacts, toolsUsed);
            }
        } else {
            if (lowerMsg.contains("review") || lowerMsg.contains("đánh giá") || lowerMsg.contains("điểm yếu") || lowerMsg.contains("điểm mạnh") || lowerMsg.contains("coaching")) {
                runToolAndCollectArtifact("vendor_review_coaching", "{}", activeTools, toolContext, artifacts, toolsUsed);
            } else if (lowerMsg.contains("slot") || lowerMsg.contains("ca") || lowerMsg.contains("công suất") || lowerMsg.contains("thời tiết") || lowerMsg.contains("capacity") || lowerMsg.contains("weather")) {
                runToolAndCollectArtifact("vendor_slot_advisor", "{\"horizonDays\": 7}", activeTools, toolContext, artifacts, toolsUsed);
            } else if (lowerMsg.contains("bài đăng") || lowerMsg.contains("mô tả") || lowerMsg.contains("tối ưu") || lowerMsg.contains("tiêu đề") || lowerMsg.contains("listing") || lowerMsg.contains("optimize")) {
                runToolAndCollectArtifact("vendor_optimize_listing", "{}", activeTools, toolContext, artifacts, toolsUsed);
            } else if (lowerMsg.contains("doanh thu") || lowerMsg.contains("kpi") || lowerMsg.contains("tổng quan") || lowerMsg.contains("revenue") || lowerMsg.contains("summary")) {
                runToolAndCollectArtifact("vendor_dashboard_summary", "{}", activeTools, toolContext, artifacts, toolsUsed);
            }
        }

        // Try calling LLM to synthesize natural language answer
        try {
            LlmResponse resp = llmClient.generateResponse(aiMessages, effectiveLanguage);
            if (resp != null && resp.getContent() != null && !resp.getContent().isBlank() && !resp.getContent().contains("error")) {
                assistantResponse = resp.getContent().trim();
            }
        } catch (Exception e) {
            log.info("Copilot LLM synthesis fallback to deterministic response: {}", e.getMessage());
        }

        if (assistantResponse.isBlank()) {
            if ("ADMIN".equalsIgnoreCase(actorRole)) {
                assistantResponse = isEn
                        ? "I have aggregated and extracted the operational data from the platform. You can inspect the metrics and analytical charts below."
                        : "Tôi đã tổng hợp và trích xuất dữ liệu vận hành từ hệ thống. Bạn có thể xem chi tiết qua các thẻ số liệu và biểu đồ phân tích bên dưới.";
            } else {
                assistantResponse = isEn
                        ? "I have analyzed your operational metrics. Below are your performance indicators and tailored action items."
                        : "Tôi đã phân tích dữ liệu hoạt động của bạn. Dưới đây là các chỉ số vận hành và kế hoạch hành động chi tiết dành riêng cho đối tác.";
            }
        }

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

    private void runToolAndCollectArtifact(
            String toolName, String args, Map<String, ToolExecutor> activeTools,
            ToolExecutionContext context, List<Map<String, Object>> artifacts, List<String> toolsUsed
    ) {
        var tool = activeTools.get(toolName);
        if (tool == null) return;
        try {
            String rawJson = tool.execute(args, context);
            if (rawJson != null && !rawJson.contains("\"error\"")) {
                toolsUsed.add(toolName);
                JsonNode tree = mapper.readTree(rawJson);
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("tool", toolName);
                map.put("type", deriveArtifactType(toolName));
                map.put("data", mapper.convertValue(tree, Map.class));
                artifacts.add(map);
            }
        } catch (Exception e) {
            log.warn("Copilot tool {} execution skipped: {}", toolName, e.getMessage());
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
            case "vendor_review_coaching" -> "VENDOR_REVIEW_COACHING_CARD";
            case "vendor_slot_advisor" -> "VENDOR_SLOT_ADVISORY_CARD";
            case "vendor_optimize_listing" -> "VENDOR_LISTING_DRAFT_CARD";
            default -> "TOOL_RESULT_CARD";
        };
    }
}
