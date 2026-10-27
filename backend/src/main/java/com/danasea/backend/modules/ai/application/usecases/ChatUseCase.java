package com.danasea.backend.modules.ai.application.usecases;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
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

    private static final List<String> POLICY_KEYWORDS = List.of(
            "hoàn tiền",
            "chính sách hủy",
            "chính sách hoãn",
            "quy định hoàn",
            "quy định hủy",
            "cancellation policy",
            "refund policy", "hoàn", "hủy", "refund", "cancellation", "reschedule", "đổi lịch"
    );
    private static final int MAX_USER_MESSAGE_CHARS = 1800;
    private static final int MAX_CHAT_ITERATIONS = 5;
    private static final int DEFAULT_HISTORY_LIMIT = 20;

    private final LlmClientPort llmClientPort;
    private final ModerationPort moderationPort;
    private final ChatHistoryService chatHistoryService;
    private final Map<String, ToolExecutor> toolExecutors;
    private final ObjectMapper objectMapper;
    private final AssistantAuditLogService auditLogService;
    private LocalizedMessageService messages = LocalizedMessageService.standalone();

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
            return executeChatLoop(conversationId, language, userId, effectiveContent);
        } finally {
            chatHistoryService.finishProcessing(conversationId, processing);
        }
    }

    private LlmResponse executeChatLoop(UUID conversationId, SupportedLanguage language, UUID userId, String message) {
        LlmResponse lastResponse = null;
        List<AssistantArtifact> cards = new ArrayList<>();
        AssistantArtifactMapper artifactMapper = new AssistantArtifactMapper(objectMapper);
        ObjectNode state = readContext(conversationId);
        long deadline = System.nanoTime() + AiExecutionBudget.remaining(AiExecutionBudget.LLM_REQUEST_TIMEOUT).toNanos();
        int toolCount = 0;

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
                for (ToolCall call : response.getToolCalls()) {
                    if (++toolCount > 12 || System.nanoTime() >= deadline) break;
                    ToolExecutor executor = toolExecutors.get(call.getName());
                    String arguments = language == null ? call.getArguments() : mergeCriteria(call, state, message);
                    String result;
                    if (executor == null) result = "{\"errorCode\":\"AI_TOOL_UNKNOWN\"}";
                    else {
                        try {
                            result = language == null ? executor.execute(arguments)
                                    : executor.execute(arguments, new ToolExecutionContext(language, conversationId, userId));
                        } catch (RuntimeException exception) {
                            log.warn("Assistant tool {} failed", call.getName());
                            result = "{\"errorCode\":\"AI_TOOL_UNAVAILABLE\"}";
                        }
                    }
                    chatHistoryService.appendMessage(conversationId, AiMessageRole.TOOL,
                            artifactMapper.modelContext(result, call), call.getId());
                    AssistantArtifact card = artifactMapper.map(call, result, conversationId);
                    cards.add(card);
                    rememberSuccessfulCriteria(state, arguments, card);
                }
            } else {
                boolean sourcedPolicy = cards.stream().anyMatch(artifactMapper::hasPolicyEvidence);
                if (containsPolicyKeywords(response.getContent())) {
                    if (!sourcedPolicy) response.setContent(language == null
                            ? messages.get("ai.policy.lookup_required", SupportedLanguage.EN)
                            : messages.get("ai.policy.lookup_required", language));
                    else if (language != null) response.setContent(messages.get("ai.policy.verified_facts", language));
                }
                return finish(conversationId, response, cards, state);
            }
            if (toolCount >= 12) break;
        }
        LlmResponse timeout = new LlmResponse();
        timeout.setContent(messages.get("ai.timeout", language == null ? SupportedLanguage.EN : language));
        timeout.setKeyMasked(lastResponse == null ? null : lastResponse.getKeyMasked());
        timeout.setResponseStatus("PARTIAL");
        return finish(conversationId, timeout, cards, state);
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
            ObjectNode criteria = state.path("criteria") instanceof ObjectNode saved ? saved.deepCopy() : objectMapper.createObjectNode();
            if (arguments.get("criteria") instanceof ObjectNode supplied) {
                supplied.fields().forEachRemaining(field -> {
                    if (!field.getValue().isNull()) criteria.set(field.getKey(), field.getValue());
                    else criteria.remove(field.getKey());
                });
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
        if (content == null || content.isBlank()) {
            return false;
        }
        String lower = content.toLowerCase();
        for (String kw : POLICY_KEYWORDS) {
            if (lower.contains(kw)) {
                return true;
            }
        }
        return false;
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
