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
    private static final Pattern ADMIN_QUEUE = CopilotRequest.words("rui ro|risk|hang doi|queue|yeu cau ho tro|cho xu ly|pending");
    private static final Pattern ADMIN_DISPUTE = CopilotRequest.words("khieu nai|dispute|tranh chap|arbitration");
    private static final Pattern UUID_REGEX = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern VENDOR_REVIEW = CopilotRequest.words("reviews?|danh gia|diem yeu|diem manh|coaching");
    private static final Pattern VENDOR_SLOT = CopilotRequest.words("slots?|ca|cong suat|thoi tiet|capacity|weather");
    private static final Pattern VENDOR_LISTING = CopilotRequest.words("bai dang|mo ta|toi uu|tieu de|listing|optimize");
    private static final Pattern VENDOR_OVERVIEW = CopilotRequest.words("kpi|tong quan|summary");
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
        this.adminAiData = adminAiData;
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

        String effCtxType = contextType != null ? contextType : conversation.contextType();
        UUID effCtxId = contextId != null ? contextId : conversation.contextId();

        List<Map<String, Object>> artifacts = new ArrayList<>();
        List<String> toolsUsed = new ArrayList<>();
        var toolContext = new ToolExecutionContext(effectiveLanguage, convId, actorUserId);
        String assistantResponse = answer(effectiveContent, admin, isEn, toolContext, artifacts, toolsUsed,
                effCtxType, effCtxId);

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
        return answer(message, admin, english, context, artifacts, toolsUsed, null, null);
    }

    private String answer(
            String message, boolean admin, boolean english, ToolExecutionContext context,
            List<Map<String, Object>> artifacts, List<String> toolsUsed,
            String contextType, UUID contextId
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

        String tool = selectTool(normalized, admin, message, contextType, contextId);
        if (tool == null && CopilotRequest.isPeriodFollowUp(normalized)) {
            String revenueTool = admin ? "admin_revenue_timeseries" : "vendor_dashboard_summary";
            if (lastReplyUsedTool(context.conversationId(), revenueTool)) tool = revenueTool;
        }
        if (tool == null) return generateText(admin, english, context, today);

        TimeRange period = null;
        String args = switch (tool) {
            case "vendor_slot_advisor" -> "{\"horizonDays\":7}";
            case "admin_dispute_summarize" -> {
                UUID disputeId = resolveDisputeId(message, contextType, contextId);
                yield disputeId != null
                        ? mapper.createObjectNode().put("disputeId", disputeId.toString()).toString()
                        : "{}";
            }
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
        } else if ("admin_vendor_diagnostics".equals(tool)) {
            try {
                period = CopilotRequest.revenuePeriod(message, normalized, today);
            } catch (DateTimeException | IllegalArgumentException exception) {
                return english ? "Please enter a valid reporting period (start date must be before or equal to end date)."
                        : "Vui lòng nhập khoảng ngày báo cáo hợp lệ (ngày bắt đầu không được sau ngày kết thúc).";
            }

            Integer limit = CopilotRequest.extractTopLimit(normalized);
            boolean isCompare = CopilotRequest.isVendorCompare(normalized);

            List<AdminAiDataPort.VendorDiagnosticItem> allVendors;
            try {
                allVendors = adminAiData.getVendorDiagnostics(today, today, null);
            } catch (Exception e) {
                allVendors = List.of();
            }

            List<AdminAiDataPort.VendorDiagnosticItem> matchedVendors = findMatchingVendors(normalized, allVendors);

            var objectNode = mapper.createObjectNode()
                    .put("from", period.getFrom().toString())
                    .put("to", period.getTo().toString());

            if (isCompare) {
                if (matchedVendors.size() < 2) {
                    return english
                            ? "Please specify at least two vendor names to compare."
                            : "Vui lòng chỉ định rõ ít nhất hai đối tác cần so sánh doanh thu.";
                }
                var arr = objectNode.putArray("vendorIds");
                for (var v : matchedVendors) {
                    arr.add(v.vendorId().toString());
                }
            } else if (matchedVendors.size() == 1 && limit == null && !CopilotRequest.isVendorStats(normalized)) {
                objectNode.put("vendorId", matchedVendors.get(0).vendorId().toString());
            } else if (matchedVendors.size() > 1 && limit == null && !CopilotRequest.isVendorStats(normalized) && !CopilotRequest.isVendorRanking(normalized)) {
                String names = matchedVendors.stream().map(AdminAiDataPort.VendorDiagnosticItem::businessName).limit(3).toList().toString();
                return english
                        ? "Multiple matching vendors found: %s. Please specify the exact full name.".formatted(names)
                        : "Tìm thấy nhiều đối tác phù hợp: %s. Vui lòng nhập tên đầy đủ của đối tác.".formatted(names);
            } else if (isExplicitVendorTargeted(normalized) && matchedVendors.isEmpty() && limit == null
                    && !CopilotRequest.isVendorStats(normalized)
                    && !CopilotRequest.isVendorRanking(normalized)
                    && !CopilotRequest.isAllVendors(normalized)) {
                String candidate = extractVendorQueryName(message, normalized);
                return english
                        ? "Vendor '%s' not found on the system. Please verify the vendor name.".formatted(candidate)
                        : "Không tìm thấy đối tác '%s' trên hệ thống. Vui lòng kiểm tra lại tên đối tác.".formatted(candidate);
            } else {
                if (limit != null) {
                    objectNode.put("limit", limit);
                }
            }

            args = objectNode.toString();
        }

        JsonNode data = runToolAndCollectArtifact(tool, args, admin ? adminToolMap : vendorToolMap,
                context, artifacts, toolsUsed);
        if (data == null) return CopilotReportAnswer.unavailable(english);
        if (period != null) {
            artifacts.getLast().put("period", Map.of("from", period.getFrom().toString(), "to", period.getTo().toString()));
        }

        return switch (tool) {
            case "admin_vendor_diagnostics" -> CopilotReportAnswer.adminVendorDiagnostics(data, period, english);
            case "vendor_dashboard_summary" -> CopilotReportAnswer.vendorRevenue(data, period, english);
            case "admin_revenue_timeseries" -> CopilotReportAnswer.adminRevenue(data, period, english);
            case "vendor_service_count" -> CopilotReportAnswer.vendorServices(data,
                    CopilotRequest.isPublishedCount(normalized), english);
            case "admin_risk_and_support_queue_overview" -> english
                    ? "I retrieved the current risk and support queue status. See the breakdown below."
                    : "Tôi đã tổng hợp các trường hợp rủi ro, yêu cầu hỗ trợ và khiếu nại đang chờ xử lý. Bạn có thể xem chi tiết bên dưới.";
            case "admin_dispute_summarize" -> english
                    ? "I have analyzed the dispute case and compiled the objective evidence with arbitration recommendations below."
                    : "Tôi đã tổng hợp hồ sơ khiếu nại, đối chiếu bằng chứng hệ thống và đưa ra khuyến nghị xử lý bên dưới.";
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
        return selectTool(text, admin, text, null, null);
    }

    private String selectTool(String text, boolean admin, String rawMessage, String contextType, UUID contextId) {
        if (admin) {
            if ((CopilotRequest.matches(ADMIN_DISPUTE, text) || "DISPUTE".equalsIgnoreCase(contextType))
                    && (hasUuid(rawMessage) || ("DISPUTE".equalsIgnoreCase(contextType) && contextId != null))) {
                return "admin_dispute_summarize";
            }
            if (CopilotRequest.matches(ADMIN_QUEUE, text)) return "admin_risk_and_support_queue_overview";
            if (isVendorDiagnosticOrRevenue(text, rawMessage)) return "admin_vendor_diagnostics";
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

    private boolean isVendorDiagnosticOrRevenue(String text, String rawMessage) {
        if (CopilotRequest.isVendorCompare(text)) return true;
        if (CopilotRequest.isVendorRanking(text)) return true;
        if (CopilotRequest.isVendorStats(text)) return true;
        if (CopilotRequest.matches(ADMIN_DIAGNOSTICS, text)) return true;
        if (CopilotRequest.isRevenue(text)) {
            if (text.contains("cua ") || text.contains("doi tac") || text.contains("vendor") || text.contains("gian hang")) {
                return true;
            }
            try {
                LocalDate now = LocalDate.now(TimeRange.VIETNAM_ZONE);
                var vendors = adminAiData.getVendorDiagnostics(now, now, null);
                if (!findMatchingVendors(text, vendors).isEmpty()) {
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    private List<AdminAiDataPort.VendorDiagnosticItem> findMatchingVendors(
            String normalizedQuery, List<AdminAiDataPort.VendorDiagnosticItem> allVendors
    ) {
        if (allVendors == null || allVendors.isEmpty()) return List.of();
        List<AdminAiDataPort.VendorDiagnosticItem> matched = new ArrayList<>();
        for (var v : allVendors) {
            if (v.businessName() == null) continue;
            var variants = getVendorNameVariants(v.businessName());
            for (String var : variants) {
                if (var.length() >= 4 && normalizedQuery.contains(var)) {
                    if (!matched.contains(v)) {
                        matched.add(v);
                    }
                    break;
                }
            }
        }
        return matched;
    }

    private List<String> getVendorNameVariants(String name) {
        List<String> variants = new ArrayList<>();
        String normFull = CopilotRequest.normalize(name).trim();
        variants.add(normFull);

        String clean = name;
        int parenIdx = clean.indexOf('(');
        if (parenIdx > 0) clean = clean.substring(0, parenIdx);
        int dashIdx = clean.indexOf('–');
        if (dashIdx < 0) dashIdx = clean.indexOf('-');
        if (dashIdx > 0) clean = clean.substring(0, dashIdx);
        String normClean = CopilotRequest.normalize(clean).trim();
        if (!normClean.isEmpty()) {
            variants.add(normClean);
        }

        if (normClean.endsWith(" da nang") && normClean.length() > 12) {
            variants.add(normClean.substring(0, normClean.length() - 8).trim());
        }
        for (String p : List.of("cong ty co phan ", "cong ty tnhh ")) {
            if (normClean.startsWith(p)) {
                variants.add(normClean.substring(p.length()).trim());
            }
        }
        return variants;
    }

    private boolean isExplicitVendorTargeted(String normalized) {
        return normalized.contains("cua ") || normalized.contains("doi tac ") || normalized.contains("vendor ")
                || normalized.contains("gian hang ");
    }

    private static final Pattern VENDOR_CANDIDATE = Pattern.compile(
            "(?:doanh thu|gmv)\\s+(?:cua|doi tac|vendor|gian hang)\\s+(.+?)(?:\\s+(?:tu|trong|thang|quy|nam|hom|7 ngay|30 ngay|\\d{1,2}/)|[?!.]|$)",
            Pattern.CASE_INSENSITIVE
    );

    private String extractVendorQueryName(String original, String normalized) {
        var m = VENDOR_CANDIDATE.matcher(normalized);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "đối tác yêu cầu";
    }

    private boolean hasUuid(String text) {
        return text != null && UUID_REGEX.matcher(text).find();
    }

    private UUID resolveDisputeId(String rawMessage, String contextType, UUID contextId) {
        if ("DISPUTE".equalsIgnoreCase(contextType) && contextId != null) {
            return contextId;
        }
        if (rawMessage != null) {
            var matcher = UUID_REGEX.matcher(rawMessage);
            if (matcher.find()) {
                try {
                    return UUID.fromString(matcher.group());
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return contextId;
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
