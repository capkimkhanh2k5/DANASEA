package com.danasea.backend.modules.ai.application.usecases;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.danasea.backend.modules.ai.application.ports.LlmClientPort;
import com.danasea.backend.modules.ai.application.ports.AiExecutionBudget;
import com.danasea.backend.modules.ai.application.ports.ModerationPort;
import com.danasea.backend.modules.ai.application.tools.ToolExecutionContext;
import com.danasea.backend.modules.ai.application.tools.ToolExecutor;
import com.danasea.backend.modules.ai.application.dtos.TravelRequest;
import com.danasea.backend.modules.ai.domain.models.AssistantArtifact;
import com.danasea.backend.modules.ai.domain.models.AiMessage;
import com.danasea.backend.modules.ai.domain.models.AiMessageRole;
import com.danasea.backend.modules.ai.domain.models.LlmResponse;
import com.danasea.backend.modules.ai.domain.models.ToolCall;
import com.danasea.backend.modules.ai.domain.services.AssistantAuditLogService;
import com.danasea.backend.modules.ai.domain.services.ChatHistoryService;
import com.danasea.backend.modules.ai.domain.services.BookingIntent;
import com.danasea.backend.modules.ai.domain.services.TravelQueryParser;
import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiMessageJpaEntity;
import com.danasea.backend.shared.i18n.LocalizedException;
import com.danasea.backend.shared.i18n.LocalizedMessageService;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.type.TypeReference;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ChatUseCase {

    private static final Pattern POLICY_CLAIM = CopilotRequest.words(
            "hoan tien|hoan huy|hoan phi|hoan lai|boi hoan|huy mien phi|huy khong mat phi|"
                    + "chinh sach huy|chinh sach hoan|chinh sach hoan lich|quy dinh huy|quy dinh hoan|"
                    + "hoan \\d+(?:[.,]\\d+)?(?=%)|doi lich mien phi|mien phi doi lich|"
                    + "refunds?|refundable|cancellation policy|free cancellation|cancel for free|free reschedul(?:e|ing)");
    private static final Pattern FORECAST_UNAVAILABLE = CopilotRequest.words(
            "du bao(?: thoi tiet)? (?:dang )?(?:loi|hong|mat|khong co|khong kha dung|het han)|"
                    + "khong co (?:du lieu )?du bao|forecast (?:is )?(?:down|unavailable|stale|expired)|"
                    + "no (?:usable |fresh )?forecast|"
                    + "(?:weather(?: api)?|cache) (?:dang )?(?:loi|hong|het han|down|unavailable|stale|expired)");
    private static final Pattern TRIP_SAFETY = CopilotRequest.words(
            "co nen|an toan de|co an toan|co dam bao|dam bao an toan|safe to|is it safe|should i");
    private static final Pattern TRIP_BRIEFING = CopilotRequest.words(
            "can mang gi|chuan bi gi|luu y an toan|cam nang|what should i bring|packing|marine briefing");
    private static final Pattern BUDGET_SEARCH = CopilotRequest.words(
            "tim (?:tour|dich vu|trai nghiem)|co (?:nhung )?(?:tour|goi|dich vu) nao|find|search|looking for");
    private static final Pattern OTHER_TRAVEL_INTENT = CopilotRequest.words(
            "lich trinh|so sanh|gan toi|thoi tiet|itinerary|compare|near me|forecast|weather");
    private static final Pattern BOOKING_GUIDE = CopilotRequest.words("huong dan|lam the nao|how (?:do i|to)|guide");
    private static final Pattern BOOKING_WORKFLOW = CopilotRequest.words("dat tour|dat dich vu|book (?:a|the|my) (?:tour|service)|booking");
    private static final Pattern DISCOVERY_OR_PAYMENT = CopilotRequest.words("tim kiem|thanh toan|pay|search");
    private static final Pattern PAYMENT_PROVIDERS_QUERY = CopilotRequest.words(
            "phuong thuc thanh toan|cong thanh toan|ho tro thanh toan|cach thanh toan|hinh thuc thanh toan|"
                    + "payment method|payment provider|supported payment");
    private static final Pattern MISSING_TICKET_QUERY = Pattern.compile(
            "(?:thanh toan|tra tien|paid).*(?:khong nhan duoc ve|chua nhan duoc ve|chua co ve|missing ticket|didn'?t receive.*ticket|no ticket)"
                    + "|(?:khong nhan duoc ve|chua nhan duoc ve|chua co ve).*(?:thanh toan|tra tien)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QR_RULES_QUERY = Pattern.compile(
            "(?:\\bve qr\\b|\\bma qr\\b|qr code|qr ticket).*(?:dung the nao|su dung|quet lai|nhieu lan|chu ky|cua so|hieu luc|scan|reuse|replay|how)"
                    + "|(?:quet lai|nhieu lan|replay).*(?:\\bve qr\\b|\\bma qr\\b|\\bqr\\b)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BEYOND_HORIZON_WEATHER = Pattern.compile(
            "(?:thoi tiet|an toan|dam bao|du bao|forecast|weather|safe).*(?:(?:1[7-9]|[2-9]\\d) ngay|1 thang|(?:1[7-9]|[2-9]\\d) days|next month)"
                    + "|(?:(?:1[7-9]|[2-9]\\d) ngay|1 thang).*(?:thoi tiet|an toan|dam bao|du bao)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE_CHANGED_QUERY = Pattern.compile(
            "(?:xac nhan|confirm|chot).*(?:gia da tang|gia tang|doi gia|gia thay doi|tang gia|price changed|price increased)"
                    + "|(?:gia da tang|doi gia|gia thay doi).*(?:xac nhan|xu ly|confirm)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SOLD_OUT_QUERY = Pattern.compile(
            "(?:xac nhan|confirm|chot).*(?:het cho|het slot|khong con cho|sold out|out of stock|slot.*unavailable)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IDEMPOTENCY_QUERY = Pattern.compile(
            "(?:xac nhan|confirm|dat cho).*(?:hai lan|2 lan|nhieu lan|lap lai|trung don|trung booking|duplicate|twice|double|idempoten)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PREFERENCE_OPT_OUT = Pattern.compile(
            "(?:khong muon|tat|bo|ngung|dung|stop|disable|opt out).*(?:so thich|lich su xem|ca nhan hoa|personaliz|preference|view history|browsing history)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ITINERARY_INDEX_REF = Pattern.compile(
            "(?:lich trinh|phuong an|itinerary)(?:\\s+(?:so|thu|number|#))?\\s*#?(\\d{1,2})\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RADIUS_KM_PATTERN = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*km\\b", Pattern.CASE_INSENSITIVE);
    private record ToolRequest(String name, String arguments) {}
    private static final int MAX_USER_MESSAGE_CHARS = 1800;
    private static final int MAX_CHAT_ITERATIONS = 5;
    private static final int DEFAULT_HISTORY_LIMIT = 20;
    private static final String RESET_CRITERIA_REGEX =
            ".*\\b(xoa bo loc|bo dieu kien cu|bo tieu chi cu|tim moi hoan toan|bat dau lai|reset|clear filter|clear criteria|new search|start over)\\b.*";

    private final LlmClientPort llmClientPort;
    private final ModerationPort moderationPort;
    private final ChatHistoryService chatHistoryService;
    private final Map<String, ToolExecutor> toolExecutors;
    private final ObjectMapper objectMapper;
    private final AssistantAuditLogService auditLogService;
    private PolicyChatAnswer policyAnswers;
    private CustomerPreferenceUseCase customerPreferences;
    private LocalizedMessageService messages = LocalizedMessageService.standalone();

    @Autowired
    void setPolicyAnswers(PolicyChatAnswer policyAnswers) {
        this.policyAnswers = policyAnswers;
    }

    @Autowired(required = false)
    void setCustomerPreferences(CustomerPreferenceUseCase customerPreferences) {
        this.customerPreferences = customerPreferences;
    }

    @Autowired
    void setLocalizedMessageService(LocalizedMessageService messages) {
        this.messages = messages;
    }

    @Autowired
    public ChatUseCase(LlmClientPort llmClientPort, 
                       ModerationPort moderationPort, 
                       ChatHistoryService chatHistoryService, 
                       List<ToolExecutor> tools,
                       ObjectMapper objectMapper,
                       @Autowired(required = false) AssistantAuditLogService auditLogService) {
        this.llmClientPort = llmClientPort;
        this.moderationPort = moderationPort;
        this.chatHistoryService = chatHistoryService;
        this.toolExecutors = tools.stream().collect(Collectors.toMap(ToolExecutor::getName, t -> t));
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
    }

    public ChatUseCase(LlmClientPort llmClientPort, 
                       ModerationPort moderationPort, 
                       ChatHistoryService chatHistoryService, 
                       List<ToolExecutor> tools,
                       ObjectMapper objectMapper) {
        this(llmClientPort, moderationPort, chatHistoryService, tools, objectMapper, null);
    }

    public LlmResponse processMessage(UUID conversationId, String userMessageContent) {
        return processMessageInternal(conversationId, userMessageContent, null, null);
    }

    public LlmResponse processMessage(
            UUID conversationId, String userMessageContent, SupportedLanguage language) {
        return processMessageInternal(conversationId, userMessageContent,
                language == null ? SupportedLanguage.VI : language, null);
    }

    public LlmResponse processMessage(UUID conversationId, String content, SupportedLanguage language, UUID userId) {
        return processMessageInternal(conversationId, content, language == null ? SupportedLanguage.VI : language, userId);
    }

    private LlmResponse processMessageInternal(
            UUID conversationId, String userMessageContent, SupportedLanguage language, UUID userId) {
        try (var budget = AiExecutionBudget.open(AiExecutionBudget.LLM_REQUEST_TIMEOUT, 6)) {
            return processWithinBudget(conversationId, userMessageContent, language, userId);
        }
    }

    private LlmResponse processWithinBudget(
            UUID conversationId, String userMessageContent, SupportedLanguage language, UUID userId) {
        String effectiveContent = userMessageContent;
        if (effectiveContent != null && effectiveContent.length() > MAX_USER_MESSAGE_CHARS) {
            int maxChars = MAX_USER_MESSAGE_CHARS;
            if (Character.isHighSurrogate(effectiveContent.charAt(maxChars - 1))) {
                maxChars--;
            }
            effectiveContent = effectiveContent.substring(0, maxChars);
        }

        if (!moderationPort.isSafe(effectiveContent)) {
            if (auditLogService != null) {
                try {
                    auditLogService.logChatAction(conversationId, null, userMessageContent, "BLOCKED: Prompt injection detected", null);
                } catch (Exception e) {
                    log.error("Failed to log moderation blocked event", e);
                }
            }
            if (language != null) {
                throw new LocalizedException("AI_MODERATION_BLOCKED", "ai.moderation.blocked");
            }
            throw new IllegalArgumentException(messages.get("ai.moderation.blocked", SupportedLanguage.EN));
        }

        UUID processing = language == null ? null : chatHistoryService.beginProcessing(conversationId);
        try {
            chatHistoryService.appendMessage(conversationId, AiMessageRole.USER, effectiveContent, null);
            if (CopilotRequest.isRevenue(CopilotRequest.normalize(effectiveContent))) {
                var answer = new LlmResponse();
                answer.setContent(messages.get("ai.customer.finance_scope", language == null ? SupportedLanguage.EN : language));
                return finish(conversationId, answer, List.of(), readContext(conversationId));
            }
            if (policyAnswers != null && language != null) {
                var policyReply = policyAnswers.resolve(effectiveContent, language, userId, null, false, null);
                if (policyReply != null) return finishPolicyReply(conversationId, policyReply);
            }
            if (language != null) {
                String query = CopilotRequest.normalize(effectiveContent);
                if (FORECAST_UNAVAILABLE.matcher(query).find()) {
                    return finishWeatherUnknown(conversationId, language, query);
                }
                if (PREFERENCE_OPT_OUT.matcher(query).find() && !BUDGET_SEARCH.matcher(query).find()) {
                    return finishPreferenceOptOut(conversationId, userId, language);
                }
                if (MISSING_TICKET_QUERY.matcher(query).find()) {
                    return finishGroundedGuidance(conversationId, language, "CUSTOMER_SUPPORT", "ai_customer_support",
                            "ai.payment.missing_ticket", Map.of("topic", "MISSING_TICKET", "endpoint", "/api/orders",
                                    "autoConfirmPayment", false, "autoReissueTicket", false));
                }
                if (PAYMENT_PROVIDERS_QUERY.matcher(query).find() && !BUDGET_SEARCH.matcher(query).find()
                        && toolExecutors.containsKey("get_payment_providers")) {
                    return finishPaymentProviders(conversationId, userId, language);
                }
                if (QR_RULES_QUERY.matcher(query).find()) {
                    return finishGroundedGuidance(conversationId, language, "POLICY", "get_policy",
                            "ai.checkin.qr_rules", Map.of("topic", "QR_CHECKIN", "signature", "HMAC-SHA256",
                                    "opensBeforeHours", 2, "sharedStandardCutoffMinutes", 30,
                                    "sharedAbsoluteCutoffMinutes", 15, "singleCheckinEnforced", true));
                }
                if (BEYOND_HORIZON_WEATHER.matcher(query).find()
                        && !CopilotRequest.words("lich trinh|itinerary").matcher(query).find()) {
                    return finishBeyondHorizonWeather(conversationId, language);
                }
                if (PolicyChatAnswer.reference(effectiveContent) == null) {
                    if (PRICE_CHANGED_QUERY.matcher(query).find()) {
                        return finishGroundedGuidance(conversationId, language, "POLICY", "get_policy",
                                "ai.booking.price_changed_flow", Map.of("topic", "PRICE_CHANGED",
                                        "reason", "PRICE_CHANGED", "maxRequotes", 2,
                                        "requiresExplicitReconfirmation", true, "silentCharge", false));
                    }
                    if (SOLD_OUT_QUERY.matcher(query).find()) {
                        return finishGroundedGuidance(conversationId, language, "POLICY", "get_policy",
                                "ai.booking.sold_out_flow", Map.of("topic", "SOLD_OUT",
                                        "statuses", List.of("out_of_stock", "SLOT_UNAVAILABLE"),
                                        "overbookingAllowed", false));
                    }
                    if (IDEMPOTENCY_QUERY.matcher(query).find()) {
                        return finishGroundedGuidance(conversationId, language, "POLICY", "get_policy",
                                "ai.booking.idempotency_flow", Map.of("topic", "IDEMPOTENCY",
                                        "distributedLock", "Redis", "durableOutcomeStore", "ai_confirmation_outcomes",
                                        "maxHoldsPerCard", 1));
                    }
                }
                if (BOOKING_GUIDE.matcher(query).find() && BOOKING_WORKFLOW.matcher(query).find()
                        && DISCOVERY_OR_PAYMENT.matcher(query).find()) {
                    var answer = new LlmResponse();
                    answer.setContent(messages.get("ai.booking.guide", language));
                    return finish(conversationId, answer, List.of(), readContext(conversationId));
                }
                if (shouldReadBudgetSearch(effectiveContent, query)
                        && (!readContext(conversationId).has("criteria") || needsBudgetClarification(effectiveContent))) {
                    return finishBudgetSearch(conversationId, userId, language, effectiveContent);
                }
                UUID orderId = PolicyChatAnswer.reference(effectiveContent);
                if (orderId != null && TRIP_BRIEFING.matcher(query).find() && toolExecutors.containsKey("ai_marine_briefing")) {
                    return finishMarineBriefing(conversationId, userId, language, orderId);
                }
            }
            return executeChatLoop(conversationId, language, userId, effectiveContent);
        } finally {
            chatHistoryService.finishProcessing(conversationId, processing);
        }
    }

    private boolean shouldReadBudgetSearch(String message, String normalized) {
        return toolExecutors.containsKey("ai_smart_search") && !BookingIntent.permitsConfirmation(message)
                && !OTHER_TRAVEL_INTENT.matcher(normalized).find()
                && (TravelQueryParser.budget(message) != null || TravelQueryParser.requestsBudget(message)
                    || BookingIntent.isSearchOnly(message))
                && (BUDGET_SEARCH.matcher(normalized).find() || !TravelQueryParser.includedActivities(message).isEmpty()
                    || BookingIntent.isSearchOnly(message));
    }

    private boolean needsBudgetClarification(String message) {
        return TravelQueryParser.requestsBudget(message) && TravelQueryParser.budget(message) == null;
    }

    private LlmResponse finishBudgetSearch(UUID conversationId, UUID userId, SupportedLanguage language, String message) {
        ObjectNode state = readContext(conversationId);
        var request = objectMapper.createObjectNode();
        request.putObject("criteria").put("query", message);
        var call = new ToolCall(UUID.randomUUID().toString(), "ai_smart_search", request.toString());
        String arguments = mergeCriteria(call, state, message);
        String result = executeTool(toolExecutors.get(call.getName()), arguments, language, conversationId, userId, call);
        var card = new AssistantArtifactMapper(objectMapper).map(call, result, conversationId);
        rememberSuccessfulCriteria(state, arguments, card);
        var response = new LlmResponse();
        String key = switch (card.status()) {
            case "AVAILABLE" -> "ai.search.results_available";
            case "NO_MATCHES" -> "ai.search.no_matches";
            case "NEEDS_INPUT" -> card.requiredInputs().contains("BUDGET_REQUIRED")
                    ? "ai.search.budget_required" : "ai.search.needs_input";
            case "PARTIAL" -> hasServiceResults(card) ? "ai.search.results_available" : "ai.search.partial";
            default -> "ai.unavailable";
        };
        response.setContent(messages.get(key, language));
        if (Set.of("PARTIAL", "NEEDS_INPUT", "UNAVAILABLE").contains(card.status())) {
            response.setResponseStatus("UNAVAILABLE".equals(card.status()) ? "PARTIAL" : card.status());
        }
        return finish(conversationId, response, List.of(card), state);
    }

    private LlmResponse finishPolicyReply(UUID conversationId, PolicyChatAnswer.Reply reply) {
        var response = new LlmResponse();
        response.setContent(reply.message());
        response.setResponseStatus(reply.status());
        var cards = new ArrayList<AssistantArtifact>();
        if (!reply.facts().isEmpty()) {
            var facts = objectMapper.convertValue(reply.facts(), new TypeReference<Map<String, Object>>() { });
            cards.add(new AssistantArtifact(UUID.randomUUID().toString(),
                    "ai_cancellation_preview".equals(reply.tool()) ? "CUSTOMER_SUPPORT" : "POLICY",
                    reply.tool(), "AVAILABLE", facts, List.of(new AssistantArtifact.Source(
                    reply.tool(), "BACKEND_TOOL", reply.tool(), java.time.OffsetDateTime.now())),
                    List.of(), List.of(), false));
        }
        return finish(conversationId, response, cards, readContext(conversationId));
    }

    private LlmResponse finishWeatherUnknown(UUID conversationId, SupportedLanguage language, String normalizedQuery) {
        var response = new LlmResponse();
        String base = messages.get("ai.weather.unconfirmed", language);
        if (normalizedQuery != null && (normalizedQuery.contains("quyet") || normalizedQuery.contains("rui ro") || normalizedQuery.contains("low risk"))) {
            base = base + (language == SupportedLanguage.EN
                    ? " Risk classifications from Quyet cannot override missing or expired weather forecasts (weatherStatus=UNKNOWN, bookingAuthorized=false)."
                    : " Điểm rủi ro thấp từ Quyet tuyệt đối không thể dùng để vượt qua yêu cầu xác minh dữ liệu thời tiết còn hiệu lực (weatherStatus=UNKNOWN, bookingAuthorized=false).");
        }
        response.setContent(base);
        var card = new AssistantArtifact(UUID.randomUUID().toString(), "WEATHER", "ai_weather_slot", "UNKNOWN",
                Map.of("status", "UNKNOWN", "bookingAuthorized", false, "requiresRevalidation", true),
                List.of(), List.of(), List.of(), false);
        return finish(conversationId, response, List.of(card), readContext(conversationId));
    }

    private LlmResponse finishMarineBriefing(UUID conversationId, UUID userId, SupportedLanguage language, UUID orderId) {
        var call = new ToolCall(UUID.randomUUID().toString(), "ai_marine_briefing",
                objectMapper.createObjectNode().put("orderId", orderId.toString()).toString());
        String result = executeTool(toolExecutors.get(call.getName()), call.getArguments(), language, conversationId, userId, call);
        var card = new AssistantArtifactMapper(objectMapper).map(call, result, conversationId);
        var response = new LlmResponse();
        boolean available = !"UNAVAILABLE".equals(card.status());
        response.setContent(messages.get(available ? "ai.weather.briefing" : "ai.policy.order_unavailable", language));
        if (!available) response.setResponseStatus("PARTIAL");
        return finish(conversationId, response, List.of(card), readContext(conversationId));
    }

    private LlmResponse finishPaymentProviders(UUID conversationId, UUID userId, SupportedLanguage language) {
        var call = new ToolCall(UUID.randomUUID().toString(), "get_payment_providers", "{}");
        String result = executeTool(toolExecutors.get(call.getName()), "{}", language, conversationId, userId, call);
        var card = new AssistantArtifactMapper(objectMapper).map(call, result, conversationId);
        var response = new LlmResponse();
        if ("UNAVAILABLE".equals(card.status())) {
            response.setContent(messages.get("ai.unavailable", language));
            response.setResponseStatus("PARTIAL");
        } else {
            response.setContent(formatPaymentProvidersMessage(card, language));
            response.setResponseStatus("ANSWERED");
        }
        return finish(conversationId, response, List.of(card), readContext(conversationId));
    }

    private LlmResponse finishGroundedGuidance(UUID conversationId, SupportedLanguage language, String cardType,
                                               String toolName, String messageKey, Map<String, Object> facts) {
        var response = new LlmResponse();
        response.setContent(messages.get(messageKey, language));
        response.setResponseStatus("ANSWERED");
        String callId = UUID.randomUUID().toString();
        var card = new AssistantArtifact(callId, cardType, toolName, "AVAILABLE", facts,
                List.of(new AssistantArtifact.Source(callId, "DANASEA_BACKEND", toolName + ":" + callId,
                        java.time.OffsetDateTime.now())),
                List.of(), List.of(), false);
        return finish(conversationId, response, List.of(card), readContext(conversationId));
    }

    private LlmResponse finishBeyondHorizonWeather(UUID conversationId, SupportedLanguage language) {
        var response = new LlmResponse();
        response.setContent(messages.get("ai.weather.horizon_limit", language));
        response.setResponseStatus("ANSWERED");
        String callId = UUID.randomUUID().toString();
        var card = new AssistantArtifact(callId, "WEATHER", "ai_weather_slot", "UNKNOWN",
                Map.of("status", "UNKNOWN", "reason", "WAITING_FOR_FORECAST", "forecastHorizonDays", 16,
                        "plannerHorizonDays", 14, "bookingAuthorized", false, "requiresRevalidation", true),
                List.of(new AssistantArtifact.Source(callId, "DANASEA_BACKEND", "ai_weather_slot:" + callId,
                        java.time.OffsetDateTime.now())),
                List.of(), List.of(), false);
        return finish(conversationId, response, List.of(card), readContext(conversationId));
    }

    private LlmResponse finishPreferenceOptOut(UUID conversationId, UUID userId, SupportedLanguage language) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("endpoint", "/api/ai/preferences");
        facts.put("method", "PUT");
        boolean disabledNow = false;
        long version = 0;
        boolean enabled = false;
        if (customerPreferences != null && userId != null) {
            try {
                var profile = customerPreferences.get(userId);
                if (profile.enabled()) {
                    profile = customerPreferences.replace(userId, profile.version(), false,
                            profile.interests(), profile.exclusions());
                    disabledNow = true;
                }
                version = profile.version();
                enabled = profile.enabled();
            } catch (RuntimeException exception) {
                log.warn("Unable to inspect/disable preferences in chat for {}: {}", userId, exception.getMessage());
            }
        }
        facts.put("enabled", enabled);
        facts.put("version", version);
        facts.put("updatedByAssistant", disabledNow);
        String prefix = language == SupportedLanguage.EN
                ? (disabledNow ? "Personalization is now disabled (enabled = false, version = " + version + "). "
                        : "Personalization is currently disabled (enabled = false, version = " + version + "). ")
                : (disabledNow ? "Đã tắt cá nhân hóa gợi ý (enabled = false, version = " + version + "). "
                        : "Cá nhân hóa gợi ý hiện đang ở trạng thái tắt (enabled = false, version = " + version + "). ");
        var response = new LlmResponse();
        response.setContent(prefix + messages.get("ai.preferences.opt_out", language));
        response.setResponseStatus("ANSWERED");
        String callId = UUID.randomUUID().toString();
        var card = new AssistantArtifact(callId, "CUSTOMER_SUPPORT", "ai_customer_support", "AVAILABLE", facts,
                List.of(new AssistantArtifact.Source(callId, "DANASEA_BACKEND", "ai_preferences:" + callId,
                        java.time.OffsetDateTime.now())),
                List.of(), List.of(), false);
        return finish(conversationId, response, List.of(card), readContext(conversationId));
    }

    private String formatPaymentProvidersMessage(AssistantArtifact card, SupportedLanguage language) {
        List<String> enabled = stringList(card.facts().get("enabledProviders"));
        List<String> disabled = stringList(card.facts().get("disabledProviders"));
        String enabledText = enabled.isEmpty() ? (language == SupportedLanguage.EN ? "none" : "chưa có") : String.join(", ", enabled);
        String disabledText = disabled.isEmpty() ? (language == SupportedLanguage.EN ? "none" : "không có") : String.join(", ", disabled);
        if (language == SupportedLanguage.EN) {
            return messages.get("ai.payment.providers_available", language)
                    + "\n- Enabled: " + enabledText
                    + "\n- Currently disabled / unavailable: " + disabledText;
        }
        return messages.get("ai.payment.providers_available", language)
                + "\n- Đang bật: " + enabledText
                + "\n- Hiện đang tắt / chưa khả dụng: " + disabledText;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> items)) return List.of();
        return items.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private static final Pattern NEARBY_INTENT = CopilotRequest.words("gan (?:vi tri|toi)|ban kinh|\\bkm\\b|nearby|near me|radius");
    private LlmResponse executeChatLoop(UUID conversationId, SupportedLanguage language, UUID userId, String message) {
        LlmResponse lastResponse = null;
        List<AssistantArtifact> cards = new ArrayList<>();
        AssistantArtifactMapper artifactMapper = new AssistantArtifactMapper(objectMapper);
        ObjectNode state = readContext(conversationId);
        if (message != null && TravelQueryParser.normalize(message).matches(RESET_CRITERIA_REGEX)) {
            state.remove("criteria");
        }
        long deadline = System.nanoTime() + AiExecutionBudget.remaining(AiExecutionBudget.LLM_REQUEST_TIMEOUT).toNanos();
        Map<ToolRequest, String> resultsByRequest = new HashMap<>();
        int toolCount = 0;
        boolean tripSafety = language != null && TRIP_SAFETY.matcher(CopilotRequest.normalize(message)).find();
        boolean nearbyIntent = language != null && NEARBY_INTENT.matcher(CopilotRequest.normalize(message)).find()
                && toolExecutors.containsKey("ai_nearby_services");
        boolean hasAreaWeather = false;
        boolean permitsBooking = language == null || BookingIntent.permitsConfirmation(message);

        for (int i = 0; i < MAX_CHAT_ITERATIONS && System.nanoTime() < deadline; i++) {
            List<AiMessage> history = mapToDomain(chatHistoryService.getRecentMessages(conversationId, DEFAULT_HISTORY_LIMIT));
            String contextHint = buildRememberedHint(state, message);
            if (contextHint != null) {
                AiMessage remembered = new AiMessage();
                remembered.setConversationId(conversationId); remembered.setRole(AiMessageRole.USER);
                remembered.setContent(contextHint);
                history.add(remembered);
            }
            LlmResponse response = language == null ? llmClientPort.generateResponse(history)
                    : llmClientPort.generateResponse(history, language);
            if (response == null) break;
            lastResponse = response;
            if (response.getToolCalls() != null && !response.getToolCalls().isEmpty()) {
                try {
                    chatHistoryService.appendMessage(conversationId, AiMessageRole.ASSISTANT, response.getContent(),
                            objectMapper.writeValueAsString(response.getToolCalls()));
                } catch (JsonProcessingException exception) {
                    throw new IllegalStateException("Unable to serialize assistant tool calls", exception);
                }
                boolean executedNewTool = false;
                for (ToolCall call : response.getToolCalls()) {
                    if (++toolCount > 12 || System.nanoTime() >= deadline) break;
                    ToolCall effectiveCall = maybeRouteNearbyCall(call, nearbyIntent);
                    ToolExecutor executor = toolExecutors.get(effectiveCall.getName());
                    String arguments = language == null ? effectiveCall.getArguments() : mergeCriteria(effectiveCall, state, message);
                    var request = new ToolRequest(effectiveCall.getName(), canonicalJson(arguments));
                    String previous = language == null ? null : resultsByRequest.get(request);
                    if (previous != null) {
                        chatHistoryService.appendMessage(conversationId, AiMessageRole.TOOL,
                                artifactMapper.modelContext(previous, effectiveCall), effectiveCall.getId());
                        continue;
                    }
                    String result;
                    boolean needsBookingIntent = "request_booking_confirmation".equals(effectiveCall.getName()) && !permitsBooking;
                    if (needsBookingIntent) {
                        result = "{\"errorCode\":\"EXPLICIT_BOOKING_INTENT_REQUIRED\"}";
                    } else if (tripSafety && "get_safety_alert".equals(effectiveCall.getName())) {
                        result = "{\"errorCode\":\"SLOT_ASSESSMENT_REQUIRED\"}";
                        hasAreaWeather = true;
                    } else {
                        result = executeTool(executor, arguments, language, conversationId, userId, effectiveCall);
                        hasAreaWeather |= "get_weather_forecast".equals(effectiveCall.getName());
                    }
                    resultsByRequest.put(request, result);
                    executedNewTool = true;
                    chatHistoryService.appendMessage(conversationId, AiMessageRole.TOOL,
                            artifactMapper.modelContext(result, effectiveCall), effectiveCall.getId());
                    if (needsBookingIntent) continue;
                    AssistantArtifact card = artifactMapper.map(effectiveCall, result, conversationId);
                    cards.add(card);
                    rememberSuccessfulCriteria(state, arguments, card);
                }
                if (!executedNewTool) {
                    if (language != null && !cards.isEmpty()) {
                        return finalizeFromCards(conversationId, language, cards, state, lastResponse);
                    }
                    break;
                }
            } else {
                if (tripSafety && hasAreaWeather && cards.stream().noneMatch(card ->
                        "ai_weather_slot".equals(card.tool()) && !"UNAVAILABLE".equals(card.status()))) {
                    String areaSummary = weatherSummaryLine(cards, language);
                    String slotPrompt = messages.get("ai.weather.slot_required", language);
                    response.setContent(areaSummary == null ? slotPrompt : areaSummary + "\n\n" + slotPrompt);
                    response.setResponseStatus("NEEDS_INPUT");
                    return finish(conversationId, response, cards, state);
                }
                if (containsPolicyKeywords(response.getContent())) {
                    protectPolicyClaims(response, message, language, cards, artifactMapper);
                }
                if (language != null) {
                    enrichGroundedCitations(response, cards, language);
                }
                return finish(conversationId, response, cards, state);
            }
            if (toolCount >= 12) break;
        }
        if (language != null && !cards.isEmpty()) {
            return finalizeFromCards(conversationId, language, cards, state, lastResponse);
        }
        LlmResponse timeout = new LlmResponse();
        boolean hasServiceResults = cards.stream().anyMatch(this::hasServiceResults);
        timeout.setContent(messages.get(hasServiceResults ? "ai.search.results_available" : "ai.timeout",
                language == null ? SupportedLanguage.EN : language));
        timeout.setKeyMasked(lastResponse == null ? null : lastResponse.getKeyMasked());
        timeout.setResponseStatus("PARTIAL");
        return finish(conversationId, timeout, cards, state);
    }

    private void protectPolicyClaims(LlmResponse response, String message, SupportedLanguage language,
                                     List<AssistantArtifact> cards, AssistantArtifactMapper artifactMapper) {
        var replyLanguage = language == null ? SupportedLanguage.EN : language;
        boolean requestedPolicy = containsPolicyKeywords(message);
        if (!requestedPolicy) {
            if (cards.stream().anyMatch(this::hasServiceResults)) {
                response.setContent(messages.get("ai.search.results_available", replyLanguage));
                return;
            }
            boolean foundComparison = cards.stream().anyMatch(card ->
                    "COMPARISON".equals(card.type()) && !card.sources().isEmpty() && hasResults(card.facts().get("matrix")));
            if (foundComparison) {
                response.setContent(messages.get("ai.search.comparison_available", replyLanguage));
                return;
            }
            var quote = cards.stream().filter(card -> "SERVICE_DETAIL".equals(card.type()) && !card.sources().isEmpty())
                    .map(card -> serviceQuote(card, replyLanguage)).filter(java.util.Objects::nonNull).findFirst();
            if (quote.isPresent()) {
                response.setContent(quote.get());
                return;
            }
            if (language != null) {
                String remaining = Arrays.stream(response.getContent().split("(?<=[.!?。])\\s+|\\R+"))
                        .filter(part -> !containsPolicyKeywords(part))
                        .filter(part -> part.codePoints().anyMatch(Character::isLetter))
                        .collect(Collectors.joining("\n"));
                // Keep the useful portion of a response when the model adds an unrelated policy claim.
                if (!remaining.isBlank()) {
                    response.setContent(remaining);
                    return;
                }
            }
        }
        boolean sourcedPolicy = cards.stream().anyMatch(artifactMapper::hasPolicyEvidence);
        if (!sourcedPolicy) {
            response.setContent(messages.get("ai.policy.lookup_required", replyLanguage));
        } else if (language != null) {
            response.setContent(messages.get("ai.policy.verified_facts", language));
        }
    }

    private String serviceQuote(AssistantArtifact card, SupportedLanguage language) {
        var facts = card.facts();
        if (!"ACTIVE_OPTION_PARTY_TOTAL".equals(facts.get("priceBasis"))
                || !(facts.get("partySize") instanceof Number party)
                || !(facts.get("name") instanceof String name)
                || !(facts.get("options") instanceof List<?> options)) return null;
        var price = options.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .filter(option -> hasResults(option.get("slots")) && option.get("partyTotal") instanceof Number)
                .map(option -> new BigDecimal(option.get("partyTotal").toString())).min(BigDecimal::compareTo);
        return price.map(amount -> messages.get("ai.search.service_quote", language, name, party.intValue(),
                NumberFormat.getNumberInstance(language.locale()).format(amount))).orElse(null);
    }

    private String executeTool(ToolExecutor executor, String arguments, SupportedLanguage language,
                               UUID conversationId, UUID userId, ToolCall call) {
        if (executor == null) return "{\"errorCode\":\"AI_TOOL_UNKNOWN\"}";
        try {
            return language == null ? executor.execute(arguments)
                    : executor.execute(arguments, new ToolExecutionContext(language, conversationId, userId));
        } catch (RuntimeException exception) {
            log.warn("Assistant tool {} failed", call.getName());
            return "{\"errorCode\":\"AI_TOOL_UNAVAILABLE\"}";
        }
    }

    private boolean hasServiceResults(AssistantArtifact card) {
        return "SERVICE_LIST".equals(card.type()) && !card.sources().isEmpty()
                && (hasResults(card.facts().get("candidates")) || hasResults(card.facts().get("results")));
    }

    private boolean hasResults(Object value) {
        return value instanceof List<?> results && !results.isEmpty();
    }

    private LlmResponse finish(UUID conversationId, LlmResponse response, List<AssistantArtifact> cards, ObjectNode state) {
        response.setCards(List.copyOf(cards));
        Set<String> required = new LinkedHashSet<>();
        cards.forEach(card -> required.addAll(card.requiredInputs()));
        response.setRequiredInputs(List.copyOf(required));
        if (!required.isEmpty() && !"PARTIAL".equals(response.getResponseStatus())) response.setResponseStatus("NEEDS_INPUT");
        response.setConversationContext(objectMapper.convertValue(state, new TypeReference<Map<String, Object>>() { }));
        response.setGeneratedTextVerified(false);
        var persisted = chatHistoryService.appendMessage(conversationId, AiMessageRole.ASSISTANT, response.getContent(), null);
        try {
            chatHistoryService.saveStructuredContext(conversationId, objectMapper.writeValueAsString(state));
            if (persisted != null) chatHistoryService.saveResponsePayload(persisted.getId(), objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize assistant context", exception);
        }
        return response;
    }

    private ObjectNode readContext(UUID conversationId) {
        try {
            String stored = chatHistoryService.getStructuredContext(conversationId).orElse(null);
            if (stored != null && stored.length() <= 32768 && objectMapper.readTree(stored) instanceof ObjectNode object) return object;
        } catch (Exception exception) { log.warn("Unable to read structured assistant context for {}", conversationId); }
        return objectMapper.createObjectNode();
    }

    private String mergeCriteria(ToolCall call, ObjectNode state, String message) {
        if (Set.of("ai_replan_itinerary", "ai_get_itinerary", "ai_itinerary_history").contains(call.getName())) {
            return mergeItineraryArguments(call, state, message);
        }
        if (!Set.of("ai_smart_search", "ai_recommend_services", "ai_nearby_services", "ai_compare_services",
                "ai_plan_itinerary", "ai_weather_slot", "search_services", "get_service_detail").contains(call.getName())) return call.getArguments();
        try {
            if (call.getArguments() == null || call.getArguments().length() > 8000) return call.getArguments();
            if (!(objectMapper.readTree(call.getArguments()) instanceof ObjectNode arguments)) return call.getArguments();
            String normalizedMessage = TravelQueryParser.normalize(message);
            boolean explicitReset = !normalizedMessage.isBlank() && normalizedMessage.matches(RESET_CRITERIA_REGEX);
            if (explicitReset) {
                state.remove("criteria");
            }
            ObjectNode saved = (!explicitReset && state.path("criteria") instanceof ObjectNode s) ? s : null;
            ObjectNode criteria = saved != null ? saved.deepCopy() : objectMapper.createObjectNode();
            ObjectNode supplied = arguments.get("criteria") instanceof ObjectNode sup ? sup : null;

            if (saved != null && message != null && !message.isBlank()) {
                List<String> savedActivities = TravelQueryParser.includedActivities(saved.path("query").asText(""));
                String currentQueryCandidate = (supplied != null && supplied.hasNonNull("query"))
                        ? supplied.get("query").asText() : message;
                List<String> currentActivities = TravelQueryParser.includedActivities(currentQueryCandidate);
                boolean switchedActivity = !currentActivities.isEmpty() && !savedActivities.isEmpty()
                        && Collections.disjoint(savedActivities, currentActivities);
                if (switchedActivity) {
                    if (supplied == null || !supplied.hasNonNull("query")) {
                        criteria.put("query", message);
                    }
                    if (supplied == null || !supplied.has("categoryId")) {
                        criteria.remove("categoryId");
                    }
                    if (supplied == null || !supplied.has("interests")) {
                        criteria.remove("interests");
                    }
                }
            }

            if (supplied != null) {
                supplied.fields().forEachRemaining(field -> {
                    if (!field.getValue().isNull()) criteria.set(field.getKey(), field.getValue());
                    else criteria.remove(field.getKey());
                });
            }

            if (saved != null && message != null && !message.isBlank()) {
                if (supplied == null || (!supplied.has("from") && !supplied.has("to"))) {
                    try {
                        LocalDate[] dates = TravelQueryParser.dates(message);
                        if (dates[0] != null) {
                            criteria.put("from", dates[0].toString());
                            criteria.put("to", (dates[1] != null ? dates[1] : dates[0]).toString());
                        }
                    } catch (Exception ignored) { }
                }
                if (supplied == null || (!supplied.has("dayStart") && !supplied.has("dayEnd"))) {
                    try {
                        LocalTime[] times = TravelQueryParser.times(message);
                        if (times[0] != null) criteria.put("dayStart", times[0].toString());
                        if (times[1] != null) criteria.put("dayEnd", times[1].toString());
                    } catch (Exception ignored) { }
                }
            }

            BigDecimal budget = TravelQueryParser.budget(message);
            Integer partySize = TravelQueryParser.partySize(message);
            if (partySize != null) {
                criteria.put("partySize", partySize);
                if ("search_services".equals(call.getName())) arguments.remove("partySize");
            }
            if ("ai_nearby_services".equals(call.getName()) && !criteria.hasNonNull("radiusKm") && message != null) {
                Matcher radiusMatch = RADIUS_KM_PATTERN.matcher(TravelQueryParser.normalize(message));
                if (radiusMatch.find()) {
                    try {
                        criteria.put("radiusKm", Double.parseDouble(radiusMatch.group(1).replace(',', '.')));
                    } catch (NumberFormatException ignored) { }
                }
            }
            if (budget != null) {
                boolean perPerson = TravelQueryParser.perPersonBudget(message);
                if (perPerson) {
                    int travelers = Math.max(1, criteria.path("partySize").asInt(1));
                    budget = budget.multiply(BigDecimal.valueOf(travelers));
                }
                criteria.put("totalBudget", budget).put("budgetBasis", perPerson ? "PER_PERSON" : "TOTAL");
                if ("search_services".equals(call.getName())) arguments.remove("max_price");
            } else if (needsBudgetClarification(message)) {
                criteria.remove("totalBudget");
                criteria.put("query", message);
                if ("search_services".equals(call.getName())) arguments.remove(List.of("query", "category", "max_price"));
            } else if (saved != null && saved.hasNonNull("totalBudget") && !explicitReset) {
                criteria.set("totalBudget", saved.get("totalBudget"));
                if ("search_services".equals(call.getName())) arguments.remove("max_price");
            }

            if ("search_services".equals(call.getName()) && arguments.hasNonNull("query")) criteria.set("query", arguments.get("query"));
            if (!criteria.hasNonNull("query")) criteria.put("query", message);
            if (criteria.path("limit").asInt(15) > 15 || !criteria.hasNonNull("limit")) criteria.put("limit", 15);
            arguments.set("criteria", criteria);
            return objectMapper.writeValueAsString(arguments);
        } catch (Exception exception) { return call.getArguments(); }
    }

    private void rememberSuccessfulCriteria(ObjectNode state, String arguments, AssistantArtifact card) {
        if (card.sources().isEmpty()) return;
        try {
            JsonNode criteria = objectMapper.readTree(arguments).get("criteria");
            if (criteria != null && criteria.isObject()) {
                objectMapper.treeToValue(criteria, TravelRequest.class).context();
                state.set("criteria", criteria.deepCopy());
            }
            for (String field : List.of("serviceId", "itineraryId", "orderId", "id")) {
                Object value = card.facts().get(field);
                if (value instanceof String text && text.matches("[0-9a-fA-F-]{36}")) state.put(field, text);
            }
            rememberItineraryState(state, card);
            rememberBookingCandidate(state, card);
        } catch (Exception exception) { log.debug("Tool result does not contain reusable travel criteria"); }
    }

    private void rememberItineraryState(ObjectNode state, AssistantArtifact card) {
        if ("ai_list_itineraries".equals(card.tool()) && card.facts().get("itineraries") instanceof List<?> itineraries) {
            var displayed = objectMapper.createArrayNode();
            for (int idx = 0; idx < Math.min(10, itineraries.size()); idx++) {
                if (!(itineraries.get(idx) instanceof Map<?, ?> item)) continue;
                ObjectNode entry = objectMapper.createObjectNode();
                entry.put("index", item.get("index") instanceof Number n ? n.intValue() : idx + 1);
                if (item.get("id") instanceof String id) entry.put("id", id);
                if (item.get("version") instanceof Number v) entry.put("version", v.longValue());
                if (item.get("lifecycle") instanceof String lc) entry.put("lifecycle", lc);
                if (item.get("from") instanceof String f) entry.put("from", f);
                if (item.get("to") instanceof String t) entry.put("to", t);
                displayed.add(entry);
            }
            state.set("displayedItineraries", displayed);
        }
        if (Set.of("ai_get_itinerary", "ai_replan_itinerary", "ai_itinerary_history").contains(card.tool())) {
            Object version = card.facts().getOrDefault("version", card.facts().get("currentVersion"));
            if (!(version instanceof Number) && card.facts().get("itinerary") instanceof Map<?, ?> it) {
                version = it.get("version");
                if (it.get("id") instanceof String id && id.matches("[0-9a-fA-F-]{36}")) state.put("itineraryId", id);
            }
            if (version instanceof Number n) state.put("itineraryVersion", n.longValue());
        }
    }

    private void rememberBookingCandidate(ObjectNode state, AssistantArtifact card) {
        if ("SERVICE_LIST".equals(card.type()) && "AVAILABLE".equals(card.status())
                && card.facts().get("candidates") instanceof List<?> candidates) {
            for (Object cObj : candidates) {
                if (!(cObj instanceof Map<?, ?> c)) continue;
                Object svcObj = c.get("service");
                if (!(svcObj instanceof Map<?, ?> svc)) continue;
                if (!(svc.get("id") instanceof String serviceId) || !serviceId.matches("[0-9a-fA-F-]{36}")) continue;
                if (saveBookableOption(state, serviceId, svc.get("name") instanceof String n ? n : null, svc.get("options"))) {
                    return;
                }
            }
            return;
        }
        if (!"SERVICE_DETAIL".equals(card.type())) return;
        Object rawId = card.facts().getOrDefault("serviceId", card.facts().get("id"));
        if (!(rawId instanceof String serviceId) || !serviceId.matches("[0-9a-fA-F-]{36}")) return;
        saveBookableOption(state, serviceId,
                card.facts().get("name") instanceof String name ? name : null,
                card.facts().get("options"));
    }

    private boolean saveBookableOption(ObjectNode state, String serviceId, String serviceName, Object optionsObj) {
        if (!(optionsObj instanceof List<?> options)) return false;
        for (Object optObj : options) {
            if (!(optObj instanceof Map<?, ?> opt) || !(opt.get("id") instanceof String optionId)) continue;
            if (!(opt.get("slots") instanceof List<?> slots) || slots.isEmpty()) continue;
            if (!(slots.getFirst() instanceof Map<?, ?> slot) || !(slot.get("id") instanceof String slotId)) continue;
            ObjectNode selected = objectMapper.createObjectNode();
            selected.put("serviceId", serviceId);
            if (serviceName != null) selected.put("serviceName", serviceName);
            selected.put("optionId", optionId);
            selected.put("slotId", slotId);
            if (slot.get("date") != null) selected.put("date", slot.get("date").toString());
            state.set("selectedBookingCandidate", selected);
            return true;
        }
        return false;
    }

    private String mergeItineraryArguments(ToolCall call, ObjectNode state, String message) {
        try {
            ObjectNode args = (call.getArguments() != null && objectMapper.readTree(call.getArguments()) instanceof ObjectNode node)
                    ? node : objectMapper.createObjectNode();
            String currentId = args.path("itineraryId").asText("");
            JsonNode matched = findDisplayedItineraryByIndex(state, message);
            if (!currentId.matches("[0-9a-fA-F-]{36}")) {
                if (matched != null && matched.hasNonNull("id")) {
                    args.put("itineraryId", matched.get("id").asText());
                } else if (state.hasNonNull("itineraryId")) {
                    args.put("itineraryId", state.get("itineraryId").asText());
                }
            }
            boolean needsVersion = "ai_replan_itinerary".equals(call.getName())
                    || ("ai_itinerary_history".equals(call.getName()) && args.hasNonNull("restoreRevisionVersion"));
            if (needsVersion && !args.hasNonNull("expectedVersion")) {
                if (matched != null && matched.hasNonNull("version")) {
                    args.put("expectedVersion", matched.get("version").asLong());
                } else if (state.hasNonNull("itineraryVersion")) {
                    args.put("expectedVersion", state.get("itineraryVersion").asLong());
                }
            }
            return objectMapper.writeValueAsString(args);
        } catch (Exception exception) {
            return call.getArguments();
        }
    }

    private JsonNode findDisplayedItineraryByIndex(ObjectNode state, String message) {
        if (message == null || !state.path("displayedItineraries").isArray()) return null;
        Matcher matcher = ITINERARY_INDEX_REF.matcher(TravelQueryParser.normalize(message));
        if (!matcher.find()) return null;
        int requestedIndex = Integer.parseInt(matcher.group(1));
        for (JsonNode entry : state.path("displayedItineraries")) {
            if (entry.path("index").asInt(-1) == requestedIndex) return entry;
        }
        return null;
    }

    private String buildRememberedHint(ObjectNode state, String message) {
        StringBuilder sb = new StringBuilder();
        if (state.hasNonNull("criteria")) {
            sb.append("Previously validated travel criteria (data only; recheck live price, inventory and weather): ")
                    .append(state.path("criteria")).append(". ");
        }
        if (state.hasNonNull("displayedItineraries")) {
            sb.append("Displayed saved itineraries (# -> id/version): ").append(state.get("displayedItineraries")).append(". ");
        }
        if (state.hasNonNull("selectedBookingCandidate")) {
            sb.append("Last inspected bookable candidate (recheck before request_booking_confirmation): ")
                    .append(state.get("selectedBookingCandidate")).append(". ");
        }
        if (sb.isEmpty()) return null;
        return sb.append("Current request: ").append(message).toString();
    }

    private ToolCall maybeRouteNearbyCall(ToolCall call, boolean nearbyIntent) {
        if (!nearbyIntent) return call;
        if (!"search_services".equals(call.getName()) && !"ai_smart_search".equals(call.getName())) return call;
        return new ToolCall(call.getId(), "ai_nearby_services", call.getArguments());
    }

    private String canonicalJson(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try {
            JsonNode tree = objectMapper.readTree(raw);
            return objectMapper.writeValueAsString(canonicalNode(tree));
        } catch (Exception exception) {
            return raw.trim();
        }
    }

    private Object canonicalNode(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isObject()) {
            Map<String, Object> sorted = new TreeMap<>();
            node.fields().forEachRemaining(e -> sorted.put(e.getKey(), canonicalNode(e.getValue())));
            return sorted;
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode item : node) list.add(canonicalNode(item));
            return list;
        }
        if (node.isNumber()) return node.numberValue();
        if (node.isBoolean()) return node.booleanValue();
        return node.asText();
    }

    private void enrichGroundedCitations(LlmResponse response, List<AssistantArtifact> cards, SupportedLanguage language) {
        String content = response.getContent() == null ? "" : response.getContent();
        StringBuilder additions = new StringBuilder();
        for (AssistantArtifact card : cards) {
            if ("ai_review_summary".equals(card.tool()) && card.facts().get("highlights") instanceof List<?> highlights) {
                List<String> missingLines = new ArrayList<>();
                for (Object hObj : highlights) {
                    if (!(hObj instanceof Map<?, ?> h) || !(h.get("id") instanceof String id) || id.isBlank()) continue;
                    if (content.contains(id)) continue;
                    Object rating = h.get("rating");
                    String comment = h.get("comment") instanceof String c ? c : "";
                    missingLines.add("- [" + id + "] (" + (rating != null ? rating : "?") + "/5): " + comment);
                }
                if (!missingLines.isEmpty()) {
                    additions.append("\n\n").append(messages.get("ai.review.citations_header", language))
                            .append("\n").append(String.join("\n", missingLines));
                }
            }
            if ("ai_recommend_services".equals(card.tool()) && "AVAILABLE".equals(card.status())) {
                String lower = TravelQueryParser.normalize(content);
                if (!lower.contains("consent") && !lower.contains("ca nhan hoa") && !lower.contains("cold-start")
                        && !lower.contains("personaliz")) {
                    boolean consentUsed = card.facts().get("reasons") instanceof List<?> r
                            && r.stream().anyMatch(x -> "CONSENTED_PREFERENCE_SIGNALS".equals(x));
                    additions.append("\n\n").append(messages.get(
                            consentUsed ? "ai.recommend.consent_used" : "ai.recommend.cold_start", language));
                }
            }
        }
        if (!additions.isEmpty()) {
            response.setContent(content + additions);
        }
    }

    private String weatherSummaryLine(List<AssistantArtifact> cards, SupportedLanguage language) {
        for (AssistantArtifact card : cards) {
            if (!"WEATHER".equals(card.type()) || !"AVAILABLE".equals(card.status())) continue;
            Map<String, Object> f = card.facts();
            Object loc = f.get("location"), date = f.get("date");
            Object wind = f.get("peakWindSpeed"), gust = f.get("peakWindGust");
            Object wave = f.get("peakWaveHeight"), rain = f.get("totalPrecipitation");
            if (wind == null && wave == null) continue;
            return language == SupportedLanguage.EN
                    ? String.format(Locale.US, "Area forecast (%s, %s): wind %s km/h, gusts %s km/h, waves %s m, rain %s mm.",
                            loc, date, fmtMetric(wind), fmtMetric(gust), fmtMetric(wave), fmtMetric(rain))
                    : String.format(Locale.US, "Dự báo khu vực (%s, %s): gió %s km/h, gió giật %s km/h, sóng %s m, mưa %s mm.",
                            loc, date, fmtMetric(wind), fmtMetric(gust), fmtMetric(wave), fmtMetric(rain));
        }
        return null;
    }

    private String fmtMetric(Object value) {
        if (value instanceof Number number) {
            double d = number.doubleValue();
            if (Double.isFinite(d)) {
                return Math.abs(d - Math.rint(d)) < 1e-6 ? Long.toString(Math.round(d))
                        : String.format(Locale.US, "%.1f", d);
            }
        }
        return String.valueOf(value);
    }

    private LlmResponse finalizeFromCards(UUID conversationId, SupportedLanguage language,
                                          List<AssistantArtifact> cards, ObjectNode state, LlmResponse lastResponse) {
        LlmResponse out = new LlmResponse();
        out.setKeyMasked(lastResponse == null ? null : lastResponse.getKeyMasked());
        boolean hasNeedsInput = cards.stream().anyMatch(c -> "NEEDS_INPUT".equals(c.status()) || !c.requiredInputs().isEmpty());
        boolean hasBookingCard = cards.stream().anyMatch(c -> "BOOKING_CONFIRMATION".equals(c.type()) && !"UNAVAILABLE".equals(c.status()));
        boolean hasServiceResults = cards.stream().anyMatch(this::hasServiceResults);
        AssistantArtifact itineraryCard = cards.stream().filter(c -> "ITINERARY".equals(c.type()) && !"UNAVAILABLE".equals(c.status())).findFirst().orElse(null);
        AssistantArtifact listCard = cards.stream().filter(c -> "ITINERARY_LIST".equals(c.type())).findFirst().orElse(null);
        AssistantArtifact historyCard = cards.stream().filter(c -> "ITINERARY_HISTORY".equals(c.type())).findFirst().orElse(null);
        AssistantArtifact reviewCard = cards.stream().filter(c -> "REVIEW_SUMMARY".equals(c.type()) && !"UNAVAILABLE".equals(c.status())).findFirst().orElse(null);
        AssistantArtifact paymentCard = cards.stream().filter(c -> "PAYMENT_PROVIDERS".equals(c.type()) && !"UNAVAILABLE".equals(c.status())).findFirst().orElse(null);
        boolean noMatches = cards.stream().anyMatch(c -> "NO_MATCHES".equals(c.status()));

        if (hasNeedsInput) {
            boolean missingCoords = cards.stream().anyMatch(c -> c.requiredInputs().contains("COORDINATES_REQUIRED"));
            boolean missingBudget = cards.stream().anyMatch(c -> c.requiredInputs().contains("BUDGET_REQUIRED"));
            String key = missingCoords ? "ai.search.needs_input"
                    : (missingBudget ? "ai.search.budget_required" : "ai.search.needs_input");
            out.setContent(messages.get(key, language));
            out.setResponseStatus("NEEDS_INPUT");
        } else if (hasBookingCard) {
            out.setContent(messages.get("ai.booking.confirmation_ready", language));
            out.setResponseStatus("ANSWERED");
        } else if (itineraryCard != null) {
            String key = "ai_replan_itinerary".equals(itineraryCard.tool())
                    ? "ai.itinerary.proposal_created" : "ai.itinerary.preview_created";
            out.setContent(messages.get(key, language));
            out.setResponseStatus("NEEDS_REVIEW".equals(itineraryCard.status()) ? "NEEDS_REVIEW" : "ANSWERED");
        } else if (listCard != null) {
            out.setContent(messages.get("ai.itinerary.list_available", language));
            out.setResponseStatus("ANSWERED");
        } else if (historyCard != null) {
            out.setContent(messages.get("ai.itinerary.history_available", language));
            out.setResponseStatus("ANSWERED");
        } else if (paymentCard != null) {
            out.setContent(formatPaymentProvidersMessage(paymentCard, language));
            out.setResponseStatus("ANSWERED");
        } else if (reviewCard != null) {
            out.setContent(formatReviewFallbackSummary(reviewCard, language));
            out.setResponseStatus("ANSWERED");
        } else if (hasServiceResults) {
            out.setContent(messages.get("ai.search.results_available", language));
            out.setResponseStatus("ANSWERED");
        } else if (noMatches) {
            out.setContent(messages.get("ai.search.no_matches", language));
            out.setResponseStatus("ANSWERED");
        } else {
            out.setContent(messages.get("ai.timeout", language));
            out.setResponseStatus("PARTIAL");
        }
        enrichGroundedCitations(out, cards, language);
        return finish(conversationId, out, cards, state);
    }

    private String formatReviewFallbackSummary(AssistantArtifact card, SupportedLanguage language) {
        Map<String, Object> f = card.facts();
        Object avg = f.get("sampledAverageRating");
        Object sampled = f.get("sampledReviews");
        Object total = f.get("totalPublicReviews");
        Object status = f.get("status");
        return language == SupportedLanguage.EN
                ? String.format("Review summary: average %s/5 from %s/%s public reviews (status: %s).", avg, sampled, total, status)
                : String.format("Tổng hợp đánh giá: điểm trung bình %s/5 trên %s/%s đánh giá công khai (trạng thái: %s).", avg, sampled, total, status);
    }

    private boolean containsPolicyKeywords(String content) {
        return POLICY_CLAIM.matcher(TravelQueryParser.normalize(content)).find();
    }

    private List<AiMessage> mapToDomain(List<AiMessageJpaEntity> jpaList) {
        List<AiMessageJpaEntity> modifiableList = new ArrayList<>(jpaList);
        Collections.reverse(modifiableList);
        return modifiableList.stream().map(jpa -> {
            AiMessage msg = new AiMessage();
            msg.setConversationId(jpa.getConversationId());
            msg.setRole(jpa.getRole());
            msg.setContent(jpa.getContent());
            msg.setToolCalls(jpa.getToolCalls());
            return msg;
        }).collect(Collectors.toList());
    }
}
