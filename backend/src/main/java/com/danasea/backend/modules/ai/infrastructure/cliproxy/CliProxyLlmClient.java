package com.danasea.backend.modules.ai.infrastructure.cliproxy;

import com.danasea.backend.modules.ai.application.ports.LlmClientPort;
import com.danasea.backend.modules.ai.application.ports.AiExecutionBudget;
import com.danasea.backend.modules.ai.domain.models.AiMessage;
import com.danasea.backend.modules.ai.domain.models.LlmResponse;
import com.danasea.backend.modules.ai.domain.models.ToolCall;
import com.danasea.backend.modules.ai.domain.services.AIToolRegistry;
import com.danasea.backend.modules.ai.domain.services.SystemPromptBuilder;
import com.danasea.backend.modules.ai.infrastructure.cliproxy.configs.CliProxyProperties;
import com.danasea.backend.shared.i18n.LocalizedMessageService;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.danasea.backend.shared.infrastructure.http.HttpRequestDeadline;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CliProxyLlmClient implements LlmClientPort {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient;
    private final AIToolRegistry aiToolRegistry;
    private final String apiKey;
    private final String defaultModel;
    private final String fallbackModel;
    private final int maxOutputTokens;
    private final CliProxyContextWindow contextWindow;

    private SystemPromptBuilder systemPromptBuilder = new SystemPromptBuilder();
    private LocalizedMessageService localizedMessages = LocalizedMessageService.standalone();

    @Autowired
    void configureI18n(SystemPromptBuilder systemPromptBuilder, LocalizedMessageService messages) {
        this.systemPromptBuilder = systemPromptBuilder;
        this.localizedMessages = messages;
    }

    @Autowired
    public CliProxyLlmClient(
            RestClient.Builder restClientBuilder,
            AIToolRegistry aiToolRegistry,
            CliProxyProperties properties
    ) {
        var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(AiExecutionBudget.LLM_REQUEST_TIMEOUT);

        String baseUrl = properties != null ? properties.sanitizedBaseUrl() : "";
        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(baseUrl != null ? baseUrl : "")
                .build();
        this.aiToolRegistry = aiToolRegistry;
        this.apiKey = properties != null ? properties.apiKey() : null;
        this.defaultModel = properties != null ? properties.model() : null;
        this.fallbackModel = properties != null ? properties.fallbackModel() : null;
        this.maxOutputTokens = properties != null ? properties.maxOutputTokens() : 65536;
        var effectiveProperties = properties != null ? properties
                : new CliProxyProperties(true, baseUrl, apiKey, defaultModel, fallbackModel);
        var counter = new CliProxyTokenCounter(restClient, apiKey);
        this.contextWindow = new CliProxyContextWindow(effectiveProperties, counter,
                new CliProxyHistorySummarizer(restClient, apiKey, effectiveProperties, counter));
    }

    public CliProxyLlmClient(
            RestClient restClient,
            AIToolRegistry aiToolRegistry,
            String apiKey,
            String defaultModel,
            String fallbackModel
    ) {
        this.restClient = restClient;
        this.aiToolRegistry = aiToolRegistry;
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.fallbackModel = fallbackModel;
        this.maxOutputTokens = 65536;
        var properties = new CliProxyProperties(true, "", apiKey, defaultModel, fallbackModel);
        var counter = new CliProxyTokenCounter(restClient, apiKey);
        this.contextWindow = new CliProxyContextWindow(properties, counter,
                new CliProxyHistorySummarizer(restClient, apiKey, properties, counter));
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public String getFallbackModel() {
        return fallbackModel;
    }

    @Override
    public LlmResponse generateResponse(List<AiMessage> messages) {
        return generateResponse(messages, SupportedLanguage.VI);
    }

    @Override
    public LlmResponse generateResponse(List<AiMessage> messages, SupportedLanguage language) {
        SupportedLanguage promptLanguage = language == null ? SupportedLanguage.VI : language;
        try (var budget = AiExecutionBudget.open(AiExecutionBudget.LLM_REQUEST_TIMEOUT, 6)) {
            return generateWithinBudget(messages, promptLanguage);
        }
    }

    private LlmResponse generateWithinBudget(List<AiMessage> messages, SupportedLanguage promptLanguage) {
        try {
            log.info("Attempting primary CLI Proxy model: {}", defaultModel);
            return callModel(messages, defaultModel, promptLanguage);
        } catch (Exception ex) {
            if (!AiExecutionBudget.hasTimeFor(Duration.ofMillis(10))) return partial(promptLanguage);
            log.warn("CLI Proxy primary model {} failed ({}). Attempting secondary model {}",
                    defaultModel, ex.getClass().getSimpleName(), fallbackModel);
            try {
                return callModel(messages, fallbackModel, promptLanguage);
            } catch (Exception fallbackEx) {
                if (!AiExecutionBudget.hasTimeFor(Duration.ofMillis(10))) return partial(promptLanguage);
                log.error("CLI Proxy secondary model {} also failed: {}", fallbackModel, fallbackEx.getClass().getSimpleName());
                throw new CliProxyException(
                        "Both models on CLI Proxy failed. Primary [" + defaultModel
                                + "], Secondary [" + fallbackModel + "]"
                );
            }
        }
    }

    private LlmResponse callModel(List<AiMessage> messages, String model, SupportedLanguage language) {
        if (!AiExecutionBudget.hasTimeFor(Duration.ofMillis(10))) return partial(language);
        if (model == null || model.isBlank()) {
            throw new CliProxyException("CLI Proxy model is not configured");
        }
        List<CliProxyDto.Message> chatMessages = new ArrayList<>();
        chatMessages.add(new CliProxyDto.Message("system", systemPromptBuilder.buildBasePrompt(language)));

        if (messages != null) {
            for (AiMessage m : messages) {
                String role = m.getRole() != null ? m.getRole().name().toLowerCase() : "user";
                String content = m.getContent();

                if ("assistant".equals(role) && m.getToolCalls() != null) {
                    try {
                        List<ToolCall> tcs = OBJECT_MAPPER.readValue(m.getToolCalls(), new TypeReference<>() {});
                        List<CliProxyDto.ToolCallDto> dtos = tcs.stream()
                                .map(tc -> new CliProxyDto.ToolCallDto(
                                        tc.getId(),
                                        "function",
                                        new CliProxyDto.FunctionDto(tc.getName(), tc.getArguments())
                                ))
                                .toList();
                        chatMessages.add(new CliProxyDto.Message(role, content, dtos, null));
                    } catch (Exception e) {
                        chatMessages.add(new CliProxyDto.Message(role, content));
                    }
                } else if ("tool".equals(role)) {
                    chatMessages.add(new CliProxyDto.Message(role, content, null, m.getToolCalls()));
                } else {
                    chatMessages.add(new CliProxyDto.Message(role, content));
                }
            }
        }

        List<Map<String, Object>> tools = (aiToolRegistry != null && aiToolRegistry.getToolDefinitions() != null)
                ? aiToolRegistry.getToolDefinitions().stream()
                        .map(t -> Map.of("type", "function", "function", t))
                        .toList()
                : List.of();

        CliProxyDto.ChatRequest request = new CliProxyDto.ChatRequest(
                model,
                chatMessages,
                tools.isEmpty() ? null : tools,
                "high", maxOutputTokens
        );

        final CliProxyContextWindow.Prepared prepared;
        try {
            prepared = contextWindow.prepare(request);
        } catch (CliProxyContextWindow.InputLimitException exception) {
            int recoveryOutput = Math.min(8192, Math.max(64, request.max_tokens() / 4));
            if (!model.toLowerCase(java.util.Locale.ROOT).startsWith("gemini-") && recoveryOutput < request.max_tokens()) {
                var recovery = new CliProxyDto.ChatRequest(model, request.messages(), request.tools(), "high", recoveryOutput);
                return callPreparedModel(contextWindow.prepare(recovery), model, language);
            }
            throw exception;
        }
        return callPreparedModel(prepared, model, language);
    }

    private LlmResponse callPreparedModel(CliProxyContextWindow.Prepared prepared, String model, SupportedLanguage language) {
        if (!AiExecutionBudget.hasTimeFor(Duration.ofMillis(10))) return partial(language);

        try {
            CliProxyDto.ChatResponse response = HttpRequestDeadline.execute(AiExecutionBudget.LLM_REQUEST_TIMEOUT, () -> restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(prepared.request())
                    .retrieve()
                    .body(CliProxyDto.ChatResponse.class));

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new CliProxyException("CLI Proxy returned empty choices for model " + model);
            }

            var choice = response.choices().get(0);
            if ("length".equals(choice.finish_reason())) return partial(language);
            CliProxyDto.Message msg = choice.message();
            LlmResponse llmResponse = new LlmResponse();
            llmResponse.setContent(msg.content());
            if (apiKey != null && apiKey.length() >= 4) {
                llmResponse.setKeyMasked("..." + apiKey.substring(apiKey.length() - 4));
            } else {
                llmResponse.setKeyMasked("cliproxy");
            }

            if (msg.tool_calls() != null && !msg.tool_calls().isEmpty()) {
                List<ToolCall> toolCalls = msg.tool_calls().stream()
                        .map(tc -> new ToolCall(tc.id(), tc.function().name(), tc.function().arguments()))
                        .toList();
                llmResponse.setToolCalls(toolCalls);
            }

            return llmResponse;
        } catch (ResourceAccessException ex) {
            log.warn("CLI Proxy request timed out within shared request budget for model {}", model);
            throw new CliProxyException("CLI Proxy timeout for model " + model);
        } catch (RestClientResponseException ex) {
            log.warn("CLI Proxy API returned HTTP {} for model {}", ex.getStatusCode().value(), model);
            throw new CliProxyException("CLI Proxy HTTP " + ex.getStatusCode().value() + " for model " + model);
        }
    }

    private LlmResponse partial(SupportedLanguage language) {
        LlmResponse response = new LlmResponse();
        response.setContent(localizedMessages.get("ai.timeout", language));
        response.setResponseStatus("PARTIAL");
        return response;
    }
}
