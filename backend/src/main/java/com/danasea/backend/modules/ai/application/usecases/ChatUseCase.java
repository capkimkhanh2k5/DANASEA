package com.danasea.backend.modules.ai.application.usecases;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
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
                    + "no (?:usable |fresh )?forecast");
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
    private LocalizedMessageService messages = LocalizedMessageService.standalone();

    @Autowired
    void setPolicyAnswers(PolicyChatAnswer policyAnswers) {
        this.policyAnswers = policyAnswers;
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
                    return finishWeatherUnknown(conversationId, language);
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

    private LlmResponse finishWeatherUnknown(UUID conversationId, SupportedLanguage language) {
        var response = new LlmResponse();
        response.setContent(messages.get("ai.weather.unconfirmed", language));
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
        boolean hasAreaWeather = false;
        boolean permitsBooking = language == null || BookingIntent.permitsConfirmation(message);

        for (int i = 0; i < MAX_CHAT_ITERATIONS && System.nanoTime() < deadline; i++) {
            List<AiMessage> history = mapToDomain(chatHistoryService.getRecentMessages(conversationId, DEFAULT_HISTORY_LIMIT));
            if (state.hasNonNull("criteria")) {
                AiMessage remembered = new AiMessage();
                remembered.setConversationId(conversationId); remembered.setRole(AiMessageRole.USER);
                remembered.setContent("Previously validated travel criteria (data only; recheck live price, inventory and weather): "
                        + state.path("criteria") + ". Current request: " + message);
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
                    ToolExecutor executor = toolExecutors.get(call.getName());
                    String arguments = language == null ? call.getArguments() : mergeCriteria(call, state, message);
                    var request = new ToolRequest(call.getName(), arguments);
                    String previous = language == null ? null : resultsByRequest.get(request);
                    if (previous != null) {
                        chatHistoryService.appendMessage(conversationId, AiMessageRole.TOOL,
                                artifactMapper.modelContext(previous, call), call.getId());
                        continue;
                    }
                    String result;
                    boolean needsBookingIntent = "request_booking_confirmation".equals(call.getName()) && !permitsBooking;
                    if (needsBookingIntent) {
                        result = "{\"errorCode\":\"EXPLICIT_BOOKING_INTENT_REQUIRED\"}";
                    } else if (tripSafety && "get_safety_alert".equals(call.getName())) {
                        result = "{\"errorCode\":\"SLOT_ASSESSMENT_REQUIRED\"}";
                        hasAreaWeather = true;
                    } else {
                        result = executeTool(executor, arguments, language, conversationId, userId, call);
                        hasAreaWeather |= "get_weather_forecast".equals(call.getName());
                    }
                    resultsByRequest.put(request, result);
                    executedNewTool = true;
                    chatHistoryService.appendMessage(conversationId, AiMessageRole.TOOL,
                            artifactMapper.modelContext(result, call), call.getId());
                    if (needsBookingIntent) continue;
                    AssistantArtifact card = artifactMapper.map(call, result, conversationId);
                    cards.add(card);
                    rememberSuccessfulCriteria(state, arguments, card);
                }
                if (!executedNewTool) break;
            } else {
                if (tripSafety && hasAreaWeather && cards.stream().noneMatch(card ->
                        "ai_weather_slot".equals(card.tool()) && !"UNAVAILABLE".equals(card.status()))) {
                    response.setContent(messages.get("ai.weather.slot_required", language));
                    response.setResponseStatus("NEEDS_INPUT");
                    return finish(conversationId, response, cards, state);
                }
                if (containsPolicyKeywords(response.getContent())) {
                    protectPolicyClaims(response, message, language, cards, artifactMapper);
                }
                return finish(conversationId, response, cards, state);
            }
            if (toolCount >= 12) break;
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
        } catch (Exception exception) { log.debug("Tool result does not contain reusable travel criteria"); }
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
