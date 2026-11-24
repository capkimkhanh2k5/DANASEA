package com.danasea.backend.modules.ai.application.usecases;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.danasea.backend.modules.ai.application.ports.AdminAiDataPort;
import com.danasea.backend.modules.ai.application.ports.AiExecutionBudget;
import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort;
import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort.CopilotConversation;
import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort.CopilotMessage;
import com.danasea.backend.modules.ai.application.ports.AssessmentCaseStorePort;
import com.danasea.backend.modules.ai.application.ports.LlmClientPort;
import com.danasea.backend.modules.ai.application.ports.ModerationPort;
import com.danasea.backend.modules.ai.application.ports.TransactionRiskReadPort;
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
    private static final Pattern VENDOR_ACTOR = CopilotRequest.words("vendor|doi tac");
    private static final Pattern VENDOR_DIAGNOSTIC_SIGNAL = CopilotRequest.words(
            "huy|ty le huy|ti le huy|canh bao|nghiem trong|critical|warning|danh gia|sao|lap day|occupancy|"
                    + "dinh chi|suspend|cai thien|kem hieu qua|hieu suat");
    private static final Pattern REFUND_CAUSE_QUERY = Pattern.compile(
            "(?:nguyen nhan|ly do|nhieu nhat|top|reasons?|causes?).*(?:hoan tien|yeu cau hoan|refunds?)"
                    + "|(?:hoan tien|yeu cau hoan|refunds?).*(?:nguyen nhan|ly do|nhieu nhat|top|reasons?|causes?)");
    private static final Pattern CANCELLATION_BREAKDOWN_EXPLICIT = Pattern.compile(
            "(?:phan loai|chia theo|thong ke).*(?:don|booking).*(?:huy|cancel)"
                    + "|(?:theo khach hang.*vendor|khach hang.*vendor.*thoi tiet)");
    private static final Pattern ADMIN_OVERVIEW = CopilotRequest.words("dashboard|tong quan|tong hop tinh hinh|suc khoe|overview");
    private static final Pattern ADMIN_QUEUE = CopilotRequest.words("rui ro|risk|hang doi|queue|yeu cau ho tro|cho xu ly|pending");
    private static final Pattern UUID_REGEX = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern SHORT_ENTITY_CODE = Pattern.compile("#(?:ORD|SRV|SVC)-([0-9a-fA-F]{8})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern VENDOR_REVIEW = CopilotRequest.words(
            "reviews?|danh gia|diem yeu|diem manh|phan tich chat luong|coaching|"
                    + "phan nan|che|khach keu|thoi gian cho|cho cano|cham tre|den tre|don tre|cho doi|complain|complaints?|waiting time");
    private static final Pattern VENDOR_REVIEW_REPLY = CopilotRequest.words(
            "viet phan hoi|soan phan hoi|tra loi danh gia|reply to (?:the )?review|draft (?:a )?reply");
    private static final Pattern REVIEW_CONTEXT = CopilotRequest.words("danh gia|review|sao");
    private static final Pattern NO_REVIEWS_QUESTION = Pattern.compile(
            "moi dang.*chua co danh gia|chua co danh gia|chua co review|khong co danh gia|new service.*no reviews|no reviews yet");
    private static final Pattern VENDOR_SLOT = Pattern.compile(
            "\\b(?:slots?|cong suat|thoi tiet|capacity|weather|mo them ca|them ca|gop ca|dong ca|cac ca|nhung ca|"
                    + "ty le dat cho|ti le dat cho|ty le lap day|ti le lap day|lap day)\\b"
                    + "|(?<!\\btat )(?<!\\bcau )(?<!\\bdan )(?<!\\bnha )(?<!\\bbat )\\bca\\b"
                    + "(?!\\s+(?:nhan|hai|ba|bon|biet|san|tuan|thang|nam|phe|nhac|si|doi|nha|nuoc)\\b)");
    private static final Pattern VENDOR_LISTING = CopilotRequest.words("bai dang|mo ta|toi uu|tieu de|listing|optimize");
    private static final Pattern LISTING_AUDIT_ONLY = CopilotRequest.words("thieu|con thieu|kiem tra|check|missing|audit");
    private static final Pattern VENDOR_OVERVIEW = CopilotRequest.words("kpi|tong quan|summary");
    private static final Pattern ADMIN_DISPUTE = CopilotRequest.words("khieu nai|tranh chap|dispute|arbitration");
    private static final Pattern DISPUTE_SUMMARY = CopilotRequest.words("tom tat|doi chieu|mau thuan|summari[sz]e|conflict");
    private static final Pattern CHECKIN_CONFLICT = CopilotRequest.words("check[- ]?in|chua duoc phuc vu|phuc vu");
    private static final Pattern SUPPORT_QUEUE = CopilotRequest.words(
            "yeu cau ho tro|yeu cau doi lich|support queue|support requests?");
    private static final Pattern QUEUE_STATE = CopilotRequest.words(
            "cho xu ly|cho duyet|chua duoc phe duyet|dang cho|pending|waiting|how many|bao nhieu|tom tat");
    private static final Pattern SLOT_HORIZON = Pattern.compile("\\b(\\d{1,3}) (?:ngay|days?)\\b");

    private static final Pattern CONTENT_MODERATION = Pattern.compile(
            "\\b(?:so dien thoai|sdt|hotline|zalo|duong link|link dat tour|link ben ngoai|ngoai san|ngoai danasea|"
                    + "ngoai nen tang|quang cao qua muc|kiem duyet|duyet dang bai|tu dong.*duyet|"
                    + "off[- ]?platform|external link|external payment|phone number|moderate|moderation)\\b"
                    + "|\\b(?:chuyen khoan|chuyen tien|thanh toan)\\b.*\\b(?:ngoai|ben ngoai|outside)\\b"
                    + "|(?<!\\d)(?:\\+84|0)(?:[ .-]?\\d){9}(?!\\d)|https?://[^\\s<>]+|[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern QUOTED_SNIPPET = Pattern.compile("[\"“]([^\"”]{5,2000})[\"”]");
    private static final Pattern UNQUOTED_NEGATED_WARNING = Pattern.compile(
            "(?i)(?:vui long |xin |luu y )?(?:không|khong|không nên|khong nen|tuyệt đối không|tuyet doi khong|đừng|dung)\\s+"
                    + "(?:chuyển tiền|chuyen tien|chuyển khoản|chuyen khoan|thanh toán|thanh toan|trả tiền|tra tien)\\s+"
                    + "(?:bên )?ngoài\\s+(?:nền tảng |nen tang )?danasea");
    private static final Pattern CONTACT_LITERAL = Pattern.compile(
            "(?<!\\d)(?:\\+84|0)(?:[ .-]?\\d){9}(?!\\d)|https?://[^\\s<>]+|[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PERSIST_MODERATION = CopilotRequest.words(
            "tao case|luu case|lap case|gui duyet|create case|persist");
    private static final Pattern ADMIN_ORDER_RISK = Pattern.compile(
            "\\b(?:canh bao rui ro|tin hieu rui ro|co so rui ro|can cu rui ro|dua tren.*du lieu.*rui ro|"
                    + "danh gia rui ro.*don|risk signals?|risk basis|risk evidence|risk assessment)\\b"
                    + "|(?:why.*flagged)"
                    + "|\\b(?:rui ro|risk)\\b.*\\b(?:don nay|don hang|ma don|order|case|ho so)\\b");
    private static final Pattern THIS_ORDER_OR_CASE = CopilotRequest.words("don nay|this order|ho so nay|case nay");
    private static final Pattern CREATE_RISK_CASE = CopilotRequest.words(
            "tao case|lap case|danh gia moi|tao danh gia|create assessment|create case|run risk");
    private static final Pattern UNCLEAR_PAYMENT_REVENUE = Pattern.compile(
            "(?:chua ro trang thai|cho doi soat|unclear status|unreconciled|pending payment).*(?:ghi nhan doanh thu|doanh thu|thanh cong|recognize revenue)"
                    + "|(?:ghi nhan doanh thu|recognize revenue).*(?:chua ro trang thai|ngay|immediately)");
    private static final Pattern SETTLEMENT_DISPUTE_HOLD = Pattern.compile(
            "(?:khieu nai|tranh chap|dispute|hoan thanh.*chua|chua duoc dua vao).*(?:doi soat|quyet toan|payout|settlement|chi tra)"
                    + "|(?:doi soat|quyet toan|payout|settlement).*(?:khieu nai|tranh chap|dispute|hoan thanh.*chua|chua duoc dua vao)");
    private static final Pattern CONCURRENT_SUPPORT_LOCK = Pattern.compile(
            "(?:hai admin|2 admin|cung xu ly|dong thoi|concurrent|optimistic lock|xung dot).*(?:yeu cau ho tro|support request|ticket)");
    private static final Pattern QUYET_UNAVAILABLE_SAFE = Pattern.compile(
            "(?:quyet|model|ai).*(?:loi|unavailable|hong|chet|timeout|error|down).*(?:an toan|safe|bo qua|tu dong)"
                    + "|tu dong danh dau.*an toan");
    private static final Pattern AUTO_LOCK_ACCOUNT = Pattern.compile(
            "(?:tu dong khoa|auto[- ]?lock|auto[- ]?ban|khoa tat ca tai khoan|khoa tai khoan)");
    private static final Pattern VELOCITY_RULE_QUESTION = Pattern.compile(
            "(?:(?:3 lan|ba lan).*thanh toan that bai.*24|5 don.*1 gio|3 lan.*hoan tien.*7 ngay|velocity|failed.*24|orders.*1h|refund.*7d)"
                    + "|(?:(?:thanh toan that bai|tao.*don|hoan tien).*(?:xem xet rui ro|chac chan la gian lan|ai nen xu ly))");
    private static final Pattern COMMISSION_POLICY_QUERY = Pattern.compile(
            "(?:hoa hong|commission).*(?:nhu the nao|tinh.*sao|90 ngay|uu dai|onboarding|danh muc|mac dinh)");
    private static final Pattern VENDOR_FAULT_CANCEL_IMPACT = Pattern.compile(
            "(?:huy chuyen|loi van hanh|vendor huy|vendor fault|operational fault).*(?:khach hang|doanh thu|anh huong|quyet toan|hoan tien)");
    private static final Pattern MAINTENANCE_CONFLICT = Pattern.compile(
            "(?:bao tri|sua chua|hu hong|maintenance|repair).*(?:cano|tau|thuyen|van|thiet bi)"
                    + "|(?:cano|tau|thuyen).*(?:bao tri|maintenance)");
    private static final Pattern RETROACTIVE_PRICE_CHANGE = Pattern.compile(
            "(?:tang gia|doi gia|cap nhat gia|sua gia|change price|increase price|update price).*(?:booking|don|da thanh toan|da dat|confirmed|paid)");

    private final AdminAiDataPort adminAiData;
    private final AiExtendedInsightStorePort insightStore;
    private final LlmClientPort llmClient;
    private final ModerationPort moderationPort;
    private final ObjectMapper mapper;
    private final PolicyChatAnswer policyAnswers;
    private final VendorAiDataPort vendorAiData;

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
            ObjectMapper mapper, PolicyChatAnswer policyAnswers
    ) {
        this(vendorAiData, reviewCoaching, slotAdvisor, listingOptimizer,
                adminAiData, disputeSummaryUseCase, insightStore, llmClient, moderationPort, mapper, policyAnswers,
                null, null, null, null);
    }

    @Autowired
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
            ObjectMapper mapper,
            PolicyChatAnswer policyAnswers,
            @Autowired(required = false) AssessTextUseCase assessTextUseCase,
            @Autowired(required = false) TransactionRiskReadPort transactionRiskReadPort,
            @Autowired(required = false) AssessmentCaseStorePort assessmentCaseStorePort,
            @Autowired(required = false) AnalyzeTransactionRiskUseCase analyzeTransactionRiskUseCase
    ) {
        this.adminAiData = adminAiData;
        this.insightStore = insightStore;
        this.llmClient = llmClient;
        this.moderationPort = moderationPort;
        this.mapper = mapper;
        this.policyAnswers = policyAnswers;
        this.vendorAiData = vendorAiData;

        // Register Vendor Tools
        registerVendorTool("vendor_dashboard_summary", vendorAiData, reviewCoaching, slotAdvisor, listingOptimizer, assessTextUseCase);
        registerVendorTool("vendor_revenue_and_bookings", vendorAiData, reviewCoaching, slotAdvisor, listingOptimizer, assessTextUseCase);
        registerVendorTool("vendor_service_count", vendorAiData, reviewCoaching, slotAdvisor, listingOptimizer, assessTextUseCase);
        registerVendorTool("vendor_service_detail", vendorAiData, reviewCoaching, slotAdvisor, listingOptimizer, assessTextUseCase);
        registerVendorTool("vendor_review_coaching", vendorAiData, reviewCoaching, slotAdvisor, listingOptimizer, assessTextUseCase);
        registerVendorTool("vendor_review_reply_draft", vendorAiData, reviewCoaching, slotAdvisor, listingOptimizer, assessTextUseCase);
        registerVendorTool("vendor_slot_advisor", vendorAiData, reviewCoaching, slotAdvisor, listingOptimizer, assessTextUseCase);
        registerVendorTool("vendor_optimize_listing", vendorAiData, reviewCoaching, slotAdvisor, listingOptimizer, assessTextUseCase);
        registerVendorTool("vendor_text_assessment", vendorAiData, reviewCoaching, slotAdvisor, listingOptimizer, assessTextUseCase);

        // Register Admin Tools
        registerAdminTool("admin_platform_dashboard", adminAiData, disputeSummaryUseCase,
                assessmentCaseStorePort, transactionRiskReadPort, analyzeTransactionRiskUseCase, assessTextUseCase);
        registerAdminTool("admin_revenue_timeseries", adminAiData, disputeSummaryUseCase,
                assessmentCaseStorePort, transactionRiskReadPort, analyzeTransactionRiskUseCase, assessTextUseCase);
        registerAdminTool("admin_booking_cancellation_breakdown", adminAiData, disputeSummaryUseCase,
                assessmentCaseStorePort, transactionRiskReadPort, analyzeTransactionRiskUseCase, assessTextUseCase);
        registerAdminTool("admin_vendor_diagnostics", adminAiData, disputeSummaryUseCase,
                assessmentCaseStorePort, transactionRiskReadPort, analyzeTransactionRiskUseCase, assessTextUseCase);
        registerAdminTool("admin_dispute_summarize", adminAiData, disputeSummaryUseCase,
                assessmentCaseStorePort, transactionRiskReadPort, analyzeTransactionRiskUseCase, assessTextUseCase);
        registerAdminTool("admin_risk_and_support_queue_overview", adminAiData, disputeSummaryUseCase,
                assessmentCaseStorePort, transactionRiskReadPort, analyzeTransactionRiskUseCase, assessTextUseCase);
        registerAdminTool("admin_text_assessment", adminAiData, disputeSummaryUseCase,
                assessmentCaseStorePort, transactionRiskReadPort, analyzeTransactionRiskUseCase, assessTextUseCase);
        registerAdminTool("admin_order_risk_assessment", adminAiData, disputeSummaryUseCase,
                assessmentCaseStorePort, transactionRiskReadPort, analyzeTransactionRiskUseCase, assessTextUseCase);
    }

    private void registerVendorTool(
            String name, VendorAiDataPort data, VendorReviewCoachingUseCase r,
            VendorSlotAdvisorUseCase s, VendorListingOptimizerUseCase l, AssessTextUseCase assessTextUseCase
    ) {
        vendorToolMap.put(name, new VendorFeatureTool(name, data, r, s, l, mapper, assessTextUseCase));
    }

    private void registerAdminTool(
            String name, AdminAiDataPort data, AdminDisputeSummaryUseCase d,
            AssessmentCaseStorePort cases, TransactionRiskReadPort riskRead,
            AnalyzeTransactionRiskUseCase riskUseCase, AssessTextUseCase assessTextUseCase
    ) {
        adminToolMap.put(name, new AdminFeatureTool(name, data, d, mapper, cases, riskRead, riskUseCase, assessTextUseCase));
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
        String attachmentType = contextType != null ? contextType : conversation.contextType();
        UUID attachmentId = contextId != null ? contextId : conversation.contextId();
        String assistantResponse = answer(effectiveContent, admin, vendorId, isEn, toolContext,
                attachmentType, attachmentId, artifacts, toolsUsed);

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
            String message, boolean admin, UUID vendorId, boolean english, ToolExecutionContext context,
            String attachmentType, UUID attachmentId,
            List<Map<String, Object>> artifacts, List<String> toolsUsed
    ) {
        String strippedMessage = CopilotRequest.stripAttachedContextPrefix(message);
        String normalized = CopilotRequest.normalize(message);
        String intentNormalized = CopilotRequest.normalize(strippedMessage);
        LocalDate today = LocalDate.now(TimeRange.VIETNAM_ZONE);
        if (!admin && CopilotRequest.isOutsideVendorScope(intentNormalized)) {
            return english
                    ? "I can show data for your shop. Platform-wide reports and other vendors' data are available to administrators."
                    : "Tôi có thể tra cứu số liệu của gian hàng bạn. Báo cáo toàn sàn và số liệu của gian hàng khác dành cho quản trị viên.";
        }
        var policyReply = policyAnswers.resolve(strippedMessage, context.language(), context.userId(), vendorId, admin,
                "ORDER".equalsIgnoreCase(attachmentType) ? attachmentId : null);
        if (policyReply != null) {
            if (policyReply.tool() != null) {
                toolsUsed.add(policyReply.tool());
                if (!policyReply.facts().isEmpty()) {
                    artifacts.add(Map.of("type", "POLICY", "tool", policyReply.tool(), "data", policyReply.facts()));
                }
            }
            return policyReply.message();
        }
        if (CopilotRequest.isPersonalBooking(intentNormalized)) {
            return english ? "Use the Customer Assistant to find or book a tour."
                    : "Bạn có thể tìm và đặt tour trong Trợ lý khách hàng.";
        }
        if (CopilotRequest.isDateQuestion(intentNormalized) && !CopilotRequest.isRevenue(intentNormalized)
                && !CopilotRequest.isServiceCount(intentNormalized)) {
            var format = DateTimeFormatter.ofPattern(english ? "'Today is' EEEE, MMMM d, uuuu." : "'Hôm nay là' EEEE, dd/MM/uuuu.",
                    english ? Locale.ENGLISH : Locale.forLanguageTag("vi-VN"));
            return today.format(format);
        }

        UUID messageReference = PolicyChatAnswer.reference(message);
        String ruleReply = resolveOperationalRuleReply(intentNormalized, admin, vendorId, english, attachmentType, attachmentId, messageReference);
        if (ruleReply != null) {
            return ruleReply;
        }

        String tool = selectTool(intentNormalized, admin, message, attachmentType, attachmentId);
        if (tool == null && CopilotRequest.isPeriodFollowUp(intentNormalized)) {
            String revenueTool = admin ? "admin_revenue_timeseries" : "vendor_dashboard_summary";
            if (lastReplyUsedTool(context.conversationId(), revenueTool)) tool = revenueTool;
        }
        if (tool == null) return generateText(admin, english, context, today);

        var args = mapper.createObjectNode();
        if ("admin_dispute_summarize".equals(tool)) {
            UUID disputeId = messageReference != null ? messageReference
                    : ("DISPUTE".equalsIgnoreCase(attachmentType) ? attachmentId : resolveDisputeId(message, attachmentType, attachmentId));
            if (disputeId == null) {
                if (CHECKIN_CONFLICT.matcher(normalized).find() || DISPUTE_SUMMARY.matcher(normalized).find()) {
                    return english
                            ? "Dispute conflict verification methodology (customer 'not served' claim vs vendor 'checked in' claim):\n"
                            + "1. Inspect QR check-in scan logs (HMAC-SHA256 signature), verified scan timestamp, scanning vendor device/account, and valid check-in window.\n"
                            + "2. Cross-check slot operational status, departure manifest, and when the customer filed the complaint.\n"
                            + "3. Collect corroborated evidence from both sides (messages, pier/beach pickup proof). NEVER side with either party or auto-refund without verified evidence. Attach the dispute case or provide its ID to extract the exact timeline."
                            : "Quy trình đối soát mâu thuẫn tranh chấp (khách báo chưa được phục vụ nhưng vendor báo đã check-in):\n"
                            + "1. Đối chiếu log quét mã QR check-in (chữ ký HMAC-SHA256), thời điểm quét check-in thực tế, tài khoản/thiết bị vendor thực hiện và cửa sổ check-in hợp lệ trước giờ khởi hành.\n"
                            + "2. Đối chiếu trạng thái vận hành của ca dịch vụ, danh sách hành khách/nhật ký xuất bến và thời điểm phát sinh khiếu nại.\n"
                            + "3. Thu thập bằng chứng từ cả hai phía (tin nhắn điều phối, hình ảnh/biên nhận điểm đón). Tuyệt đối KHÔNG tự động kết luận nghiêng về một bên hay tự động hoàn tiền khi chưa xác minh đủ bằng chứng. Hãy đính kèm hồ sơ tranh chấp hoặc cung cấp mã để tôi trích xuất timeline chi tiết.";
                }
                return english ? "Which dispute should I review? Attach it or provide its ID."
                        : "Bạn muốn xem hồ sơ tranh chấp nào? Hãy đính kèm hồ sơ hoặc cung cấp mã.";
            }
            args.put("disputeId", disputeId.toString());
        }
        if ("admin_order_risk_assessment".equals(tool)) {
            UUID caseId = ("RISK_CASE".equalsIgnoreCase(attachmentType)
                    || "ASSESSMENT_CASE".equalsIgnoreCase(attachmentType)
                    || "CASE".equalsIgnoreCase(attachmentType)) ? attachmentId : null;
            UUID orderId = "ORDER".equalsIgnoreCase(attachmentType) ? attachmentId : null;
            if (caseId == null && orderId == null && messageReference != null) {
                if (CopilotRequest.matches(CopilotRequest.words("case|ho so"), normalized)) {
                    caseId = messageReference;
                } else {
                    orderId = messageReference;
                }
            }
            if (caseId == null && orderId == null) {
                return english
                        ? "Which order or risk assessment case should I inspect? Attach the order/case or provide its ID so I can show verified observed facts, Velocity 3/5/3 signals, and limitations."
                        : "Bạn muốn xem căn cứ cảnh báo rủi ro của đơn hàng hoặc hồ sơ nào? Hãy đính kèm đơn/hồ sơ hoặc cung cấp mã ID để tôi hiển thị dữ liệu thực tế (observed), tín hiệu Velocity 3/5/3 và giới hạn đánh giá.";
            }
            if (orderId != null) args.put("orderId", orderId.toString());
            if (caseId != null) args.put("caseId", caseId.toString());
            args.put("createNewAssessment", CopilotRequest.matches(CREATE_RISK_CASE, normalized));
        }
        if ("admin_vendor_diagnostics".equals(tool)) {
            UUID targetVendorId = messageReference != null ? messageReference
                    : "VENDOR".equalsIgnoreCase(attachmentType) ? attachmentId : null;
            if (targetVendorId != null) args.put("vendorId", targetVendorId.toString());
        }
        if ("vendor_text_assessment".equals(tool) || "admin_text_assessment".equals(tool)) {
            args.put("persist", CopilotRequest.matches(PERSIST_MODERATION, normalized));
            String quoted = extractQuotedText(message);
            if (!admin && "SERVICE".equalsIgnoreCase(attachmentType) && attachmentId != null) {
                var ownedService = vendorAiData.getService(vendorId, attachmentId);
                if (ownedService.isEmpty()) {
                    return english ? "The attached service is not found in your shop."
                            : "Không tìm thấy dịch vụ đính kèm thuộc gian hàng của bạn.";
                }
                if (quoted != null) {
                    args.put("text", quoted);
                } else if (CopilotRequest.matches(CopilotRequest.words("toi muon them|if i add|co duoc them"), normalized)) {
                    args.put("text", message);
                } else {
                    args.put("serviceId", attachmentId.toString());
                }
            } else {
                args.put("text", quoted != null ? quoted : message);
            }
        }
        if ("vendor_review_reply_draft".equals(tool)) {
            UUID reviewId = messageReference != null ? messageReference
                    : "REVIEW".equalsIgnoreCase(attachmentType) ? attachmentId : null;
            if (reviewId == null) {
                reviewId = findSingleMatchingReview(vendorId, normalized);
            }
            if (reviewId == null) {
                return english ? "Provide the review ID, or open the draft action from that review."
                        : "Hãy cung cấp mã đánh giá hoặc mở nút soạn phản hồi ngay trên đánh giá đó.";
            }
            args.put("reviewId", reviewId.toString());
        }
        if (!admin && "vendor_service_detail".equals(tool)) {
            UUID serviceId = messageReference != null ? messageReference
                    : "SERVICE".equalsIgnoreCase(attachmentType) ? attachmentId : null;
            if (serviceId == null) {
                serviceId = findMentionedService(vendorId, normalized, message);
            }
            if (serviceId == null) {
                var shopServices = vendorAiData.listAllOwnedServices(vendorId);
                if (shopServices == null || shopServices.isEmpty()) {
                    shopServices = vendorAiData.listServices(vendorId);
                }
                if (shopServices.size() == 1) {
                    serviceId = shopServices.getFirst().id();
                } else {
                    return english
                            ? "Which service would you like to check the price for? Please attach the service or specify its name."
                            : "Bạn muốn tra cứu giá của dịch vụ nào? Hãy ghim dịch vụ đó hoặc nêu rõ tên dịch vụ trong gian hàng.";
                }
            }
            args.put("serviceId", serviceId.toString());
        }
        if (!admin && Set.of("vendor_review_coaching", "vendor_optimize_listing", "vendor_slot_advisor").contains(tool)) {
            UUID serviceId = messageReference != null ? messageReference
                    : "SERVICE".equalsIgnoreCase(attachmentType) ? attachmentId : null;
            if (serviceId == null && normalized.length() > 20) {
                serviceId = findMentionedService(vendorId, normalized, message);
            }
            if ("vendor_optimize_listing".equals(tool) && serviceId == null && normalized.length() > 20) {
                if (LISTING_AUDIT_ONLY.matcher(intentNormalized).find()) {
                    return english ? "Attach a service from your shop so I can review its actual listing."
                            : "Hãy đính kèm dịch vụ thuộc gian hàng để tôi xem đúng nội dung bài đăng.";
                }
                args.put("title", strippedMessage);
                args.put("description", strippedMessage);
            }
            if (serviceId != null) args.put("serviceId", serviceId.toString());
        }
        LocalDate slotTargetDate = null;
        if ("vendor_slot_advisor".equals(tool)) {
            var horizon = SLOT_HORIZON.matcher(intentNormalized);
            if (horizon.find()) {
                int days = Integer.parseInt(horizon.group(1));
                if (days < 1 || days > 14) return english ? "Choose a slot advisory period between 1 and 14 days."
                        : "Hãy chọn khoảng thời gian cố vấn từ 1 đến 14 ngày.";
                args.put("horizonDays", days);
            } else {
                slotTargetDate = CopilotRequest.slotTargetDate(strippedMessage, intentNormalized, today);
                if (slotTargetDate != null) {
                    long delta = ChronoUnit.DAYS.between(today, slotTargetDate);
                    if (delta < 0 || delta > 14) {
                        return english ? "Choose a slot advisory date within the next 14 days."
                                : "Hãy chọn ngày tra cứu ca trong vòng 14 ngày tới.";
                    }
                    args.put("horizonDays", Math.max(1, (int) delta + 1));
                    args.put("targetDate", slotTargetDate.toString());
                } else {
                    args.put("horizonDays", 7);
                }
            }
        }

        TimeRange period = null;
        if (Set.of("admin_revenue_timeseries", "vendor_dashboard_summary",
                "admin_booking_cancellation_breakdown", "admin_vendor_diagnostics").contains(tool)) {
            try {
                period = CopilotRequest.revenuePeriod(message, normalized, today);
            } catch (DateTimeException | IllegalArgumentException exception) {
                return english ? "Please enter a valid reporting period."
                        : "Vui lòng nhập khoảng ngày báo cáo hợp lệ.";
            }
            if ("admin_revenue_timeseries".equals(tool) && CopilotRequest.comparesMonths(normalized)) {
                return compareAdminRevenue(today, english, tool, context, artifacts, toolsUsed);
            }
            args.put("from", period.getFrom().toString()).put("to", period.getTo().toString());

            if ("admin_vendor_diagnostics".equals(tool) && !args.has("vendorId")) {
                Integer limit = CopilotRequest.extractTopLimit(normalized);
                boolean isCompare = CopilotRequest.isVendorCompare(normalized);
                List<AdminAiDataPort.VendorDiagnosticItem> allVendors;
                try {
                    allVendors = adminAiData.getVendorDiagnostics(today, today, null);
                } catch (Exception e) {
                    allVendors = List.of();
                }
                List<AdminAiDataPort.VendorDiagnosticItem> matchedVendors = findMatchingVendors(normalized, allVendors);
                if (isCompare) {
                    if (matchedVendors.size() < 2) {
                        return english
                                ? "Please specify at least two vendor names to compare."
                                : "Vui lòng chỉ định rõ ít nhất hai đối tác cần so sánh doanh thu.";
                    }
                    var arr = args.putArray("vendorIds");
                    for (var v : matchedVendors) {
                        arr.add(v.vendorId().toString());
                    }
                } else if (matchedVendors.size() == 1 && limit == null && !CopilotRequest.isVendorStats(normalized)) {
                    args.put("vendorId", matchedVendors.get(0).vendorId().toString());
                } else if (matchedVendors.size() > 1 && limit == null
                        && !CopilotRequest.isVendorStats(normalized) && !CopilotRequest.isVendorRanking(normalized)) {
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
                } else if (limit != null) {
                    args.put("limit", limit);
                }
            }
        }

        JsonNode data = runToolAndCollectArtifact(tool, args.toString(), admin ? adminToolMap : vendorToolMap,
                context, artifacts, toolsUsed);
        if (data == null) return CopilotReportAnswer.unavailable(english);
        if (period != null) {
            artifacts.getLast().put("period", Map.of("from", period.getFrom().toString(), "to", period.getTo().toString()));
        }

        return switch (tool) {
            case "vendor_dashboard_summary" -> CopilotReportAnswer.vendorRevenue(data, period, normalized, english);
            case "admin_revenue_timeseries" -> CopilotReportAnswer.adminRevenue(data, period, normalized, english);
            case "admin_booking_cancellation_breakdown" -> CopilotReportAnswer.adminCancellations(data, period, normalized, english);
            case "admin_vendor_diagnostics" -> CopilotReportAnswer.adminVendorDiagnostics(data, period, message, normalized, english);
            case "admin_order_risk_assessment" -> CopilotReportAnswer.adminOrderRiskAssessment(data, english);
            case "admin_risk_and_support_queue_overview" -> CopilotReportAnswer.adminQueue(data, english);
            case "admin_dispute_summarize" -> english ? "I found the dispute evidence below. An administrator must review it before deciding."
                    : "Tôi đã tra cứu bằng chứng của tranh chấp bên dưới. Quản trị viên cần thẩm định trước khi quyết định.";
            case "vendor_review_coaching" -> CopilotReportAnswer.vendorReviewCoaching(data, intentNormalized, english);
            case "vendor_slot_advisor" -> CopilotReportAnswer.vendorSlotAdvisor(data, intentNormalized, slotTargetDate, english);
            case "vendor_service_detail" -> CopilotReportAnswer.vendorServiceDetail(data, english);
            case "vendor_optimize_listing" -> CopilotReportAnswer.vendorListingOptimization(data, english);
            case "vendor_text_assessment", "admin_text_assessment" -> CopilotReportAnswer.contentAssessment(data, english);
            case "vendor_review_reply_draft" -> data.path("draftedReply").asText(CopilotReportAnswer.unavailable(english));
            case "vendor_service_count" -> CopilotReportAnswer.vendorServices(data,
                    CopilotRequest.isPublishedCount(intentNormalized), english);
            default -> english ? "I retrieved the operational data. See the details below."
                    : "Tôi đã tra cứu dữ liệu vận hành. Bạn có thể xem chi tiết bên dưới.";
        };
    }

    private String resolveOperationalRuleReply(
            String normalized, boolean admin, UUID vendorId, boolean english,
            String attachmentType, UUID attachmentId, UUID messageReference
    ) {
        if (UNCLEAR_PAYMENT_REVENUE.matcher(normalized).find()) {
            return english
                    ? "NO. Never recognize revenue as successful when a payment transaction has an unclear or pending status. Reconcile the final payment status with the payment gateway or verified webhook first."
                    : "KHÔNG. Tuyệt đối không ghi nhận doanh thu thành công khi giao dịch thanh toán chưa rõ trạng thái. Cần đối soát trạng thái cuối cùng với cổng thanh toán hoặc webhook xác thực trước khi hạch toán.";
        }
        if (SETTLEMENT_DISPUTE_HOLD.matcher(normalized).find()) {
            return english
                    ? "NO. Orders with an open dispute (OPEN or UNDER_REVIEW) are excluded from vendor payout settlement (ACTIVE_DISPUTE) and held until resolved. Completed orders can also remain held while inside the 7-day dispute window (DISPUTE_WINDOW_OPEN), during a pending refund (PENDING_REFUND), or under an active disruption (ACTIVE_DISRUPTION)."
                    : "KHÔNG. Các đơn hàng đang có khiếu nại chưa giải quyết (OPEN hoặc UNDER_REVIEW) bị loại trừ khỏi kỳ quyết toán (ACTIVE_DISPUTE) và tạm giữ tiền cho đến khi xử lý xong. Ngoài ra, đơn đã hoàn thành vẫn có thể chưa vào quyết toán nếu chưa hết cửa sổ khiếu nại 7 ngày (DISPUTE_WINDOW_OPEN), đang chờ hoàn tiền (PENDING_REFUND) hoặc có sự cố chờ xử lý (ACTIVE_DISRUPTION).";
        }
        if (admin && CONCURRENT_SUPPORT_LOCK.matcher(normalized).find()) {
            return english
                    ? "Support requests use optimistic locking via version/expectedVersion. The first administrator action updates the status and increments version; the second concurrent request carrying the stale expectedVersion is rejected with a conflict error (409 Conflict / AiStateConflictException: 'Support request version has changed'), preventing conflicting updates."
                    : "Hệ thống áp dụng optimistic locking theo trường expectedVersion trên yêu cầu hỗ trợ. Quản trị viên xử lý trước sẽ cập nhật trạng thái và tăng version; yêu cầu đồng thời gửi sau với expectedVersion cũ sẽ bị từ chối với lỗi xung đột trạng thái (HTTP 409 Conflict / AiStateConflictException: 'Support request version has changed'), đảm bảo không tạo hai kết quả trái ngược.";
        }
        if (admin && QUYET_UNAVAILABLE_SAFE.matcher(normalized).find()) {
            return english
                    ? "NO. Under fail-closed backend rules (AnalyzeTransactionRiskUseCase and AssessTextUseCase), when Quyet AI is unavailable or errors out, the transaction/content MUST route to NEEDS_REVIEW (MODEL_UNAVAILABLE) for manual review — never automatically marked safe."
                    : "KHÔNG. Theo cơ chế fail-closed của backend (AnalyzeTransactionRiskUseCase và AssessTextUseCase), khi Quyet AI gặp lỗi hoặc không khả dụng, giao dịch/nội dung được chuyển sang trạng thái NEEDS_REVIEW (MODEL_UNAVAILABLE) để quản trị viên thẩm định thủ công, tuyệt đối không tự động đánh dấu an toàn.";
        }
        if (admin && AUTO_LOCK_ACCOUNT.matcher(normalized).find()) {
            return english
                    ? "NO. AI only creates NEEDS_REVIEW risk cases (fraudConfirmed=false) and surfaces evidence; it has no authority to automatically lock or suspend user accounts. An authorized administrator must verify payment/order evidence and execute account actions manually."
                    : "KHÔNG. AI chỉ lập hồ sơ đánh giá NEEDS_REVIEW (fraudConfirmed=false) và tổng hợp bằng chứng, tuyệt đối không tự động khóa tài khoản người dùng hay đối tác. Quyết định khóa tài khoản phải do quản trị viên có thẩm quyền xác minh và thực hiện.";
        }
        if (admin && messageReference == null
                && !"ORDER".equalsIgnoreCase(attachmentType)
                && !"RISK_CASE".equalsIgnoreCase(attachmentType)
                && !CopilotRequest.matches(THIS_ORDER_OR_CASE, normalized)
                && VELOCITY_RULE_QUESTION.matcher(normalized).find()) {
            return english
                    ? "Under Velocity 3/5/3 rules (velocity-demo-v1): (1) >= 3 failed payments in 24h triggers THREE_FAILED_PAYMENTS_IN_24H -> NEEDS_REVIEW; (2) >= 5 orders in 1h triggers FIVE_ORDERS_IN_1H -> NEEDS_REVIEW (velocity alert only, NOT confirmed fraud: fraudConfirmed=false); (3) >= 3 refund requests in 7d triggers THREE_REFUND_REQUESTS_IN_7D -> NEEDS_REVIEW for administrator verification."
                    : "Theo bộ quy tắc Velocity 3/5/3 (velocity-demo-v1): (1) >= 3 lần thanh toán thất bại trong 24 giờ kích hoạt THREE_FAILED_PAYMENTS_IN_24H -> chuyển NEEDS_REVIEW; (2) >= 5 đơn hàng trong 1 giờ kích hoạt FIVE_ORDERS_IN_1H -> chuyển NEEDS_REVIEW để kiểm tra, KHÔNG kết luận chắc chắn là gian lận (fraudConfirmed=false); (3) >= 3 yêu cầu hoàn tiền trong 7 ngày kích hoạt THREE_REFUND_REQUESTS_IN_7D -> lập case NEEDS_REVIEW để Admin thẩm định.";
        }
        if (!admin && COMMISSION_POLICY_QUERY.matcher(normalized).find() && !CopilotRequest.isRevenue(normalized)) {
            return english
                    ? "Commission rates follow CommissionPolicyAdapter priority: (1) ONBOARDING rule (priority 100, default 90-day window from approval/creation if an active ONBOARDING rule is configured); (2) CATEGORY rule (priority 50); (3) platform default PLATFORM_COMMISSION_RATE in system_configs. Exact percentages depend on active system configuration and cannot be guessed."
                    : "Tỷ lệ hoa hồng được xác định theo thứ tự ưu tiên của CommissionPolicyAdapter: (1) Quy tắc ONBOARDING (ưu tiên 100 — mặc định 90 ngày kể từ ngày duyệt/tạo tài khoản nếu hệ thống đang kích hoạt rule ONBOARDING cho đối tác mới); (2) Quy tắc theo danh mục dịch vụ CATEGORY (ưu tiên 50); (3) Tỷ lệ mặc định toàn sàn PLATFORM_COMMISSION_RATE trong system_configs. Mức % cụ thể phụ thuộc cấu hình thực tế đang bật trên hệ thống.";
        }
        if (!admin && VENDOR_FAULT_CANCEL_IMPACT.matcher(normalized).find()) {
            return english
                    ? "When a vendor cancels due to operational fault (VENDOR_FAULT): (1) the customer receives a 100% refund (or supported reschedule); (2) the fully refunded order is excluded from vendor payout settlement (FULL_REFUND) with applicable fault adjustments; (3) vendor cancellationRate increases (> 20% triggers HIGH_CANCELLATION WARNING; > 40% triggers CRITICAL)."
                    : "Khi đối tác hủy chuyến do lỗi vận hành (VENDOR_FAULT): (1) Khách hàng được hoàn 100% tiền vé (hoặc hỗ trợ đổi lịch phù hợp); (2) Đơn hoàn 100% bị loại khỏi quyết toán (FULL_REFUND), không ghi nhận thực thu và có thể bị khấu trừ bồi hoàn theo đối soát; (3) Tỷ lệ hủy của gian hàng tăng lên (> 20.0% bật cảnh báo WARNING; > 40.0% bật CRITICAL).";
        }
        if (!admin && MAINTENANCE_CONFLICT.matcher(normalized).find()) {
            return english
                    ? "To avoid booking conflicts during canoe/equipment maintenance: (1) close or pause affected slots immediately to stop new bookings; (2) inspect all confirmed bookings on those slots; (3) assign a safety-compliant replacement vessel and notify guests, or coordinate rescheduling / 100% VENDOR_FAULT refund via support — never operate unmaintained equipment."
                    : "Khi cần bảo trì cano/thiết bị đang gắn với booking: (1) Đóng bán ngay các slot liên quan để ngừng nhận đơn mới; (2) Rà soát danh sách booking đã xác nhận trên các ca bị ảnh hưởng; (3) Điều phối cano đạt chuẩn an toàn thay thế và báo trước cho khách, hoặc hỗ trợ đổi lịch / hoàn 100% theo lỗi vận hành (VENDOR_FAULT) — tuyệt đối không dùng thiết bị chưa bảo trì để phục vụ.";
        }
        if (!admin && RETROACTIVE_PRICE_CHANGE.matcher(normalized).find()) {
            return english
                    ? "CANNOT update prices on already-paid or confirmed bookings. Booking prices are locked at checkout/confirmation; price changes only apply to future bookings."
                    : "KHÔNG THỂ thay đổi giá trên các booking cũ đã được khách thanh toán/xác nhận. Giá đơn hàng được khóa theo giao dịch tại thời điểm thanh toán; việc tăng giá chỉ áp dụng cho các lượt đặt mới trong tương lai.";
        }
        if (!admin && CopilotRequest.matches(NO_REVIEWS_QUESTION, normalized)) {
            UUID mentioned = messageReference != null ? messageReference
                    : "SERVICE".equalsIgnoreCase(attachmentType) ? attachmentId : findMentionedService(vendorId, normalized);
            if (mentioned == null) {
                return english
                        ? "This newly published service currently has 0 verified reviews (totalReviewsAnalyzed = 0). I cannot assess its quality, invent scores, or reuse reviews from your other services."
                        : "Dịch vụ mới đăng này hiện có 0 đánh giá (totalReviewsAnalyzed = 0). AI không đủ dữ liệu để nhận xét chất lượng và không được tạo đánh giá giả hay lấy điểm của các dịch vụ khác thay thế.";
            }
        }
        return null;
    }

    private String extractQuotedText(String message) {
        if (message == null) return null;
        Matcher matcher = QUOTED_SNIPPET.matcher(message);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        if (!CONTACT_LITERAL.matcher(message).find()
                && CopilotRequest.matches(CopilotRequest.words("co nen xem|co phai|vi pham|quang cao thanh toan ngoai|is this"),
                CopilotRequest.normalize(message))) {
            Matcher unquoted = UNQUOTED_NEGATED_WARNING.matcher(message);
            if (unquoted.find()) {
                return unquoted.group().trim();
            }
        }
        return null;
    }

    private UUID findSingleMatchingReview(UUID vendorId, String normalizedQuestion) {
        var starMatcher = Pattern.compile("\\b([1-5])\\s*(?:sao|stars?)\\b").matcher(normalizedQuestion);
        if (!starMatcher.find()) return null;
        int rating = Integer.parseInt(starMatcher.group(1));
        var matches = vendorAiData.listReviews(vendorId, null, 50).stream()
                .filter(review -> review.rating() == rating)
                .toList();
        return matches.size() == 1 ? matches.getFirst().id() : null;
    }

    private UUID findMentionedService(UUID vendorId, String question) {
        return findMentionedService(vendorId, question, null);
    }

    private UUID findMentionedService(UUID vendorId, String question, String rawMessage) {
        List<VendorAiDataPort.VendorServiceItem> services = vendorAiData.listAllOwnedServices(vendorId);
        if (services == null || services.isEmpty()) {
            services = vendorAiData.listServices(vendorId);
        }
        if (services == null || services.isEmpty()) return null;

        if (rawMessage != null) {
            Matcher codeMatcher = SHORT_ENTITY_CODE.matcher(rawMessage);
            if (codeMatcher.find()) {
                String prefix = codeMatcher.group(1).toLowerCase(Locale.ROOT);
                var codeMatches = services.stream()
                        .filter(service -> service.id() != null
                                && service.id().toString().replace("-", "").toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
                if (codeMatches.size() == 1) return codeMatches.getFirst().id();
            }
        }

        var exactMatches = services.stream()
                .filter(service -> service.title() != null && !service.title().isBlank()
                        && question.contains(CopilotRequest.normalize(service.title())))
                .toList();
        if (exactMatches.size() == 1) return exactMatches.getFirst().id();
        if (exactMatches.size() > 1) return null;

        Set<String> stopwords = Set.of("tour", "dich", "vu", "bai", "dang", "mo", "ta", "tieu", "de",
                "cua", "toi", "cho", "thue", "bien", "da", "nang", "hay", "kiem", "tra", "viet", "lai",
                "thieu", "thong", "tin", "sao", "hap", "dan", "tim", "danh", "gia", "review",
                "ngay", "mai", "chi", "moi", "khach", "nen", "the", "nao", "lam", "con", "trong", "vang",
                "ca", "slot", "thap", "cao", "kin", "gio", "sang", "chieu", "thu", "bay", "chu", "nhat", "tuan",
                "service", "listing", "optimize", "check", "update", "write", "rewrite");
        List<String> queryTokens = Arrays.stream(question.split("[^a-z0-9]+"))
                .filter(token -> token.length() >= 3 && !stopwords.contains(token))
                .distinct()
                .toList();
        if (queryTokens.isEmpty()) return null;

        var tokenMatches = services.stream()
                .filter(service -> service.title() != null && !service.title().isBlank())
                .filter(service -> {
                    String normTitle = " " + CopilotRequest.normalize(service.title()) + " ";
                    return queryTokens.stream().anyMatch(token -> normTitle.contains(" " + token + " "));
                })
                .toList();
        return tokenMatches.size() == 1 ? tokenMatches.getFirst().id() : null;
    }

    private String compareAdminRevenue(LocalDate today, boolean english, String tool, ToolExecutionContext context,
                                       List<Map<String, Object>> artifacts, List<String> toolsUsed) {
        var current = TimeRange.of(today.withDayOfMonth(1), today);
        var priorEnd = current.getFrom().minusDays(1);
        var previous = TimeRange.of(priorEnd.withDayOfMonth(1), priorEnd);
        JsonNode currentData = readAdminRevenue(tool, current, context, artifacts, toolsUsed);
        JsonNode previousData = readAdminRevenue(tool, previous, context, artifacts, toolsUsed);
        if (currentData == null || previousData == null) return CopilotReportAnswer.unavailable(english);
        return CopilotReportAnswer.compareAdminRevenue(currentData, current, previousData, previous, english);
    }

    private JsonNode readAdminRevenue(String tool, TimeRange period, ToolExecutionContext context,
                                      List<Map<String, Object>> artifacts, List<String> toolsUsed) {
        var from = period.getFrom().toString();
        var to = period.getTo().toString();
        var args = mapper.createObjectNode().put("from", from).put("to", to);
        var data = runToolAndCollectArtifact(tool, args.toString(), adminToolMap, context, artifacts, toolsUsed);
        if (data != null) artifacts.getLast().put("period", Map.of("from", from, "to", to));
        return data;
    }

    private boolean lastReplyUsedTool(UUID conversationId, String tool) {
        return insightStore.listCopilotMessages(conversationId).reversed().stream()
                .filter(message -> "ASSISTANT".equals(message.role()))
                .findFirst().map(CopilotMessage::toolsUsed)
                .filter(used -> Arrays.asList(used.split(",")).contains(tool)).isPresent();
    }

    private String selectTool(String text, boolean admin) {
        return selectTool(text, admin, text, null, null);
    }

    private String selectTool(String text, boolean admin, String rawMessage, String attachmentType, UUID attachmentId) {
        if (admin) {
            if (ADMIN_ORDER_RISK.matcher(text).find()
                    || (("ORDER".equalsIgnoreCase(attachmentType) || "RISK_CASE".equalsIgnoreCase(attachmentType)
                    || "ASSESSMENT_CASE".equalsIgnoreCase(attachmentType))
                    && CopilotRequest.matches(CopilotRequest.words("rui ro|risk|canh bao|tin hieu|flag"), text))) {
                return "admin_order_risk_assessment";
            }
            if (CONTENT_MODERATION.matcher(text).find()) return "admin_text_assessment";
            if ((ADMIN_DISPUTE.matcher(text).find() && DISPUTE_SUMMARY.matcher(text).find())
                    || (DISPUTE_SUMMARY.matcher(text).find() && CHECKIN_CONFLICT.matcher(text).find())
                    || ((CopilotRequest.matches(ADMIN_DISPUTE, text) || "DISPUTE".equalsIgnoreCase(attachmentType))
                        && (hasUuid(rawMessage) || ("DISPUTE".equalsIgnoreCase(attachmentType) && attachmentId != null)))) {
                return "admin_dispute_summarize";
            }
            if (SUPPORT_QUEUE.matcher(text).find() && QUEUE_STATE.matcher(text).find()) {
                return "admin_risk_and_support_queue_overview";
            }
            if (REFUND_CAUSE_QUERY.matcher(text).find() || CANCELLATION_BREAKDOWN_EXPLICIT.matcher(text).find()) {
                return "admin_booking_cancellation_breakdown";
            }
            if ((VENDOR_ACTOR.matcher(text).find() && VENDOR_DIAGNOSTIC_SIGNAL.matcher(text).find())
                    || isVendorDiagnosticOrRevenue(text, rawMessage)) {
                return "admin_vendor_diagnostics";
            }
            if (CopilotRequest.isRevenue(text) || CopilotRequest.matches(ADMIN_REVENUE, text)) return "admin_revenue_timeseries";
            if (CopilotRequest.matches(ADMIN_CANCELLATION, text)) return "admin_booking_cancellation_breakdown";
            if (CopilotRequest.matches(ADMIN_DIAGNOSTICS, text)) return "admin_vendor_diagnostics";
            if (CopilotRequest.matches(ADMIN_QUEUE, text)) return "admin_risk_and_support_queue_overview";
            if (CopilotRequest.matches(ADMIN_OVERVIEW, text)) return "admin_platform_dashboard";
        } else {
            if (CONTENT_MODERATION.matcher(text).find()) return "vendor_text_assessment";
            if (CopilotRequest.isRevenue(text)) return "vendor_dashboard_summary";
            if (CopilotRequest.matches(VENDOR_SLOT, text)) return "vendor_slot_advisor";
            if (CopilotRequest.isServicePrice(text)) return "vendor_service_detail";
            if (CopilotRequest.isServiceCount(text)) return "vendor_service_count";
            if (VENDOR_REVIEW_REPLY.matcher(text).find() && REVIEW_CONTEXT.matcher(text).find()) return "vendor_review_reply_draft";
            if (CopilotRequest.matches(VENDOR_REVIEW, text)) return "vendor_review_coaching";
            if (CopilotRequest.matches(VENDOR_LISTING, text)) return "vendor_optimize_listing";
            if (CopilotRequest.matches(VENDOR_OVERVIEW, text)) return "vendor_dashboard_summary";
        }
        return null;
    }

    private boolean isVendorDiagnosticOrRevenue(String text, String rawMessage) {
        if (CopilotRequest.comparesMonths(text)) return false;
        if (CopilotRequest.isVendorRanking(text)) return true;
        if (CopilotRequest.isVendorStats(text)) return true;
        if (CopilotRequest.isVendorCompare(text)) {
            if (text.contains("doi tac") || text.contains("vendor") || text.contains("gian hang") || text.contains("cua ")) {
                return true;
            }
            try {
                LocalDate now = LocalDate.now(TimeRange.VIETNAM_ZONE);
                var vendors = adminAiData.getVendorDiagnostics(now, now, null);
                if (!findMatchingVendors(text, vendors).isEmpty()) {
                    return true;
                }
            } catch (Exception ignored) {}
            return false;
        }
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

    private static final Pattern NON_SPECIFIC_VENDOR_TARGET = CopilotRequest.words(
            "nao|cac|tung|tat ca|toan bo|toan san|he thong|san|moi|nhung|mot|co ty le|chi co"
    );

    private boolean isExplicitVendorTargeted(String normalized) {
        var m = VENDOR_CANDIDATE.matcher(normalized);
        if (!m.find()) return false;
        String candidate = m.group(1).trim();
        return !candidate.isBlank() && !CopilotRequest.matches(NON_SPECIFIC_VENDOR_TARGET, candidate);
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
            case "admin_order_risk_assessment" -> "ADMIN_ORDER_RISK_ASSESSMENT_CARD";
            case "admin_dispute_summarize" -> "ADMIN_DISPUTE_BRIEF_CARD";
            case "admin_platform_dashboard", "admin_risk_and_support_queue_overview" -> "ADMIN_KPI_SUMMARY_CARD";
            case "vendor_text_assessment", "admin_text_assessment" -> "COPILOT_CONTENT_ASSESSMENT_CARD";
            case "vendor_dashboard_summary", "vendor_revenue_and_bookings" -> "VENDOR_KPI_SUMMARY_CARD";
            case "vendor_service_count" -> "VENDOR_SERVICE_COUNT_CARD";
            case "vendor_service_detail" -> "VENDOR_SERVICE_DETAIL_CARD";
            case "vendor_review_coaching" -> "VENDOR_REVIEW_COACHING_CARD";
            case "vendor_slot_advisor" -> "VENDOR_SLOT_ADVISORY_CARD";
            case "vendor_optimize_listing" -> "VENDOR_LISTING_DRAFT_CARD";
            default -> "TOOL_RESULT_CARD";
        };
    }
}