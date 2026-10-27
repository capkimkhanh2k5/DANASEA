package com.danasea.backend.modules.ai.infrastructure.cliproxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.web.client.RestClient;

import com.danasea.backend.modules.ai.domain.models.AiMessage;
import com.danasea.backend.modules.ai.application.ports.AiExecutionBudget;
import com.danasea.backend.modules.ai.domain.models.AiMessageRole;
import com.danasea.backend.modules.ai.domain.services.AIToolRegistry;
import com.danasea.backend.modules.ai.domain.services.SystemPromptBuilder;
import com.danasea.backend.modules.ai.infrastructure.cliproxy.configs.CliProxyProperties;
import com.danasea.backend.shared.i18n.LocalizedMessageService;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

class ProxyInputBudgetTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<JsonNode> generations = new CopyOnWriteArrayList<>();
    private final List<JsonNode> counted = new CopyOnWriteArrayList<>();
    private final List<JsonNode> summaries = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private boolean primaryFails;
    private boolean counterFails;
    private String counterResponse;
    private long counterDelayMillis;
    private boolean summaryFails;
    private int inputLimit = 1000;
    private int outputLimit = 64;
    private String primaryModel = "gemini-3.5-flash-lite";

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void countsWholeSerializedPayloadIncludingSystemAndToolSchema() throws Exception {
        var client = client(true);
        var response = client.generateResponse(List.of(message(AiMessageRole.USER, "khả năng bơi 🛶 ".repeat(40))));
        assertThat(counted).isNotEmpty();
        assertThat(response.getContent()).as("counter payload lengths %s", counted.stream().map(n -> n.toString().length()).toList()).isEqualTo("OK");
        JsonNode payload = counted.getFirst();
        assertThat(payload.path("messages").path(0).path("content").asText()).isEqualTo("Keep business rules.");
        assertThat(payload.path("tools").path(0).path("function").path("name").asText()).isEqualTo("check_safety");
        assertThat(payload.path("tools").path(0).path("function").path("description").asText()).contains("equipment");
        assertThat(payload.path("reasoning_effort").asText()).isEqualTo("high");
    }

    @Test void trimsOldestCompleteTurnsAndKeepsLatestQuestion() throws Exception {
        var client = client(false);
        List<AiMessage> history = history();
        var response = client.generateResponse(history);
        assertThat(response.getContent()).isEqualTo("OK");
        JsonNode request = generations.getFirst();
        var userMessages = new ArrayList<String>();
        request.path("messages").forEach(m -> { if ("user".equals(m.path("role").asText())) userMessages.add(m.path("content").asText()); });
        assertThat(userMessages).doesNotContain(history.getFirst().getContent());
        assertThat(request.toString()).contains("LATEST_QUESTION");
        assertThat(request.path("messages").path(0).path("content").asText()).isEqualTo("Keep business rules.");
        assertThat(request.toString().length() / 2).isLessThanOrEqualTo(968);
        assertThat(history.getFirst().getContent()).contains("oldest_marker");
    }

    @Test void fallbackIsRebudgetedForItsSmallerCombinedContext() throws Exception {
        primaryFails = true;
        var response = client(false).generateResponse(history());
        assertThat(response.getContent()).isEqualTo("OK");
        assertThat(generations).hasSize(2);
        assertThat(generations.get(1).path("model").asText()).isEqualTo("gpt-oss-120b-medium");
        assertThat(generations.get(1).toString()).contains("LATEST_QUESTION");
        assertThat(generations.get(1).toString().getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(504);
        assertThat(generations.get(0).path("messages").size()).isGreaterThan(generations.get(1).path("messages").size());
    }

    @Test void invalidlyTinyContextFailsAsServiceErrorOnlyAfterRecoveryWithoutGeneration() throws Exception {
        var client = client(false);
        assertThatThrownBy(() -> client.generateResponse(List.of(message(AiMessageRole.USER, "CURRENT ".repeat(500)))))
                .isInstanceOf(CliProxyException.class);
        assertThat(generations).isEmpty();
    }

    @Test void adjacentUserInstructionsAndSourcesRemainOneCurrentTurn() throws Exception {
        var client = client(false);
        assertThatThrownBy(() -> client.generateResponse(List.of(
                message(AiMessageRole.USER, "MANDATORY_TASK ".repeat(300)),
                message(AiMessageRole.USER, "CURRENT_SOURCE"))))
                .isInstanceOf(CliProxyException.class);
        assertThat(generations).isEmpty();
    }

    @Test void finalValidatedCriteriaCannotSeparateCurrentToolEvidenceFromItsQuestion() throws Exception {
        var history = new ArrayList<>(history());
        var call = message(AiMessageRole.ASSISTANT, null);
        call.setToolCalls("[{\"id\":\"call1\",\"name\":\"check_safety\",\"arguments\":\"{}\"}]");
        history.add(call);
        var result = message(AiMessageRole.TOOL, "verified_source"); result.setToolCalls("call1"); history.add(result);
        history.add(message(AiMessageRole.USER, "Validated criteria; current request: LATEST_QUESTION"));
        assertThat(client(false).generateResponse(history).getContent()).isEqualTo("OK");
        assertThat(generations.getFirst().toString()).contains("LATEST_QUESTION", "call1", "verified_source", "Validated criteria");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "not-json", "{\"totalTokens\":0}", "{\"totalTokens\":-1}",
            "{\"totalTokens\":2.5}", "{\"totalTokens\":\"1\"}", "{\"totalTokens\":99999999999999999999999}"})
    void malformedTokenCountCannotAuthorizeAnOversizedRequest(String responseBody) throws Exception {
        counterResponse = responseBody;
        assertThat(client(false).generateResponse(history()).getContent()).isEqualTo("OK");
        assertThat(generations.getFirst().toString().getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(968);
    }

    @Test void exhaustedDeadlineDuringCountingPreventsGeneration() throws Exception {
        counterDelayMillis = 1500;
        var client = client(false);
        long start = System.nanoTime();
        try (var budget = AiExecutionBudget.open(Duration.ofMillis(500), 6)) {
            assertThat(client.generateResponse(history()).getResponseStatus()).isEqualTo("PARTIAL");
            assertThat(generations).isEmpty();
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
        }
    }

    @Test void unavailableCounterUsesConservativeUtf8BoundInsteadOfSendingOversizedInput() throws Exception {
        counterFails = true;
        var response = client(false).generateResponse(history());
        assertThat(response.getContent()).isEqualTo("OK");
        assertThat(generations.getFirst().toString().getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(968);
        assertThat(generations.getFirst().toString()).contains("LATEST_QUESTION");
    }

    @Test void retainsLatestCompleteToolExchangeAndRemovesOrphanResults() throws Exception {
        var history = new ArrayList<>(history());
        history.add(message(AiMessageRole.TOOL, "orphan_result"));
        var call = message(AiMessageRole.ASSISTANT, null);
        call.setToolCalls("[{\"id\":\"call1\",\"name\":\"check_safety\",\"arguments\":\"{}\"}]");
        history.add(call);
        var result = message(AiMessageRole.TOOL, "verified_source"); result.setToolCalls("call1"); history.add(result);
        assertThat(client(false).generateResponse(history).getContent()).isEqualTo("OK");
        String sent = generations.getFirst().toString();
        assertThat(sent).contains("LATEST_QUESTION", "call1", "verified_source").doesNotContain("orphan_result");
    }

    @Test void dropsIncompleteToolBlocksRatherThanSendingDanglingCalls() throws Exception {
        var history = new ArrayList<>(history());
        var call = message(AiMessageRole.ASSISTANT, null);
        call.setToolCalls("[{\"id\":\"missing_result\",\"name\":\"check_safety\",\"arguments\":\"{}\"}]");
        history.add(call);
        assertThat(client(false).generateResponse(history).getContent()).isEqualTo("OK");
        assertThat(generations.getFirst().toString()).doesNotContain("missing_result");
    }

    @Test void oversizedCurrentToolIsCompactedWithoutChangingCurrentQuestionOrToolPair() throws Exception {
        var history = new ArrayList<AiMessage>();
        history.add(message(AiMessageRole.USER, "LATEST_QUESTION"));
        var call = message(AiMessageRole.ASSISTANT, null);
        call.setToolCalls("[{\"id\":\"call1\",\"name\":\"check_safety\",\"arguments\":\"{}\"}]");
        history.add(call);
        var result = message(AiMessageRole.TOOL, "{\"description\":\"" + "long evidence ".repeat(3000) + "\"}");
        result.setToolCalls("call1"); history.add(result);
        assertThat(client(false).generateResponse(history).getContent()).isEqualTo("OK");
        String sent = generations.getFirst().toString();
        assertThat(sent).contains("LATEST_QUESTION", "call1", "contextCompacted", "sourceToolCallId");
        assertThat(result.getContent()).hasSizeGreaterThan(10000);
    }

    @Test void llmSelectsExactOldUserQuotesForMemoryWithoutPromotingAssistantClaims() throws Exception {
        inputLimit = 6000;
        var history = memoryHistory();
        assertThat(client(false).generateResponse(history).getContent()).isEqualTo("OK");
        assertThat(summaries).hasSize(1);
        assertThat(summaries.getFirst().path("tools").isNull() || summaries.getFirst().path("tools").isMissingNode()).isTrue();
        assertThat(generations.getFirst().toString()).contains("I cannot swim", "Earlier user quotations");
        assertThat(summaries.getFirst().toString()).doesNotContain("Assistant invented price");
    }

    @Test void unavailableSummarizerUsesExtractiveMemoryAndStillAnswersOriginalQuestion() throws Exception {
        inputLimit = 6000; summaryFails = true;
        assertThat(client(false).generateResponse(memoryHistory()).getContent()).isEqualTo("OK");
        assertThat(summaries).hasSize(1);
        assertThat(generations.getFirst().toString()).contains("I cannot swim", "LATEST_QUESTION");
    }

    @Test void oneRequestUsesAtMostOneLlmCompactionAcrossModelIterations() throws Exception {
        inputLimit = 6000;
        var client = client(false);
        try (var budget = AiExecutionBudget.open(Duration.ofMinutes(2), 6)) {
            assertThat(client.generateResponse(memoryHistory()).getContent()).isEqualTo("OK");
            assertThat(client.generateResponse(memoryHistory()).getContent()).isEqualTo("OK");
        }
        assertThat(summaries).hasSize(1);
    }

    @Test void newestOmittedCorrectionSurvivesMoreThanEightOldUserTurns() throws Exception {
        inputLimit = 6000; summaryFails = true;
        var history = new ArrayList<AiMessage>();
        history.add(message(AiMessageRole.USER, "I can swim."));
        history.add(message(AiMessageRole.ASSISTANT, "Noted."));
        for (int i = 0; i < 9; i++) {
            history.add(message(AiMessageRole.USER, "old discussion " + "a".repeat(1000)));
            history.add(message(AiMessageRole.ASSISTANT, "b".repeat(1000)));
        }
        history.add(message(AiMessageRole.USER, "Correction: I cannot swim."));
        history.add(message(AiMessageRole.ASSISTANT, "c".repeat(20000)));
        history.add(message(AiMessageRole.USER, "LATEST_QUESTION: recommend an activity"));
        assertThat(client(false).generateResponse(history).getContent()).isEqualTo("OK");
        String sent = generations.getFirst().toString();
        assertThat(sent).contains("Correction: I cannot swim.");
        assertThat(sent.indexOf("I can swim.")).isLessThan(sent.indexOf("Correction: I cannot swim."));
        assertThat(summaries.getFirst().toString()).contains("Correction: I cannot swim.");
    }

    @Test void evictsAnotherOldTurnToMakeRoomForSafetyMemory() throws Exception {
        inputLimit = 980;
        var history = history();
        history.getFirst().setContent("I cannot swim. " + "a".repeat(480));
        assertThat(client(false).generateResponse(history).getContent()).isEqualTo("OK");
        assertThat(generations.getFirst().toString()).contains("I cannot swim.", "LATEST_QUESTION");
        assertThat((generations.getFirst().toString().length() + 1) / 2).isLessThanOrEqualTo(948);
    }

    @Test void lowersOutputReservationToPreserveCurrentRequestBeforeGivingUpOnGpt() throws Exception {
        primaryModel = "gpt-oss-120b-medium"; outputLimit = 256;
        String question = "CURRENT_QUESTION " + "a".repeat(240);
        assertThat(client(false).generateResponse(List.of(message(AiMessageRole.USER, question))).getContent()).isEqualTo("OK");
        assertThat(generations).hasSize(1);
        assertThat(generations.getFirst().path("model").asText()).isEqualTo("gpt-oss-120b-medium");
        assertThat(generations.getFirst().path("max_tokens").asInt()).isEqualTo(64);
        assertThat(generations.getFirst().toString()).contains(question);
    }

    @Test void vietnameseCorrectionsAndLatestAdjustmentsSurviveInCompressedHistory() throws Exception {
        inputLimit = 8000; summaryFails = true;
        var history = new ArrayList<AiMessage>();
        history.add(message(AiMessageRole.USER, "Tôi muốn đặt tour 2 người, ngân sách 10 triệu."));
        history.add(message(AiMessageRole.ASSISTANT, "Dạ em đã ghi nhận thông tin."));
        for (int i = 0; i < 5; i++) {
            history.add(message(AiMessageRole.USER, "Thảo luận phụ " + "x".repeat(1200)));
            history.add(message(AiMessageRole.ASSISTANT, "Tư vấn " + "y".repeat(1200)));
        }
        history.add(message(AiMessageRole.USER, "Sửa lại: đổi thành 4 người (2 người lớn và 2 trẻ em)."));
        history.add(message(AiMessageRole.ASSISTANT, "Dạ em cập nhật số lượng khách."));
        for (int i = 0; i < 5; i++) {
            history.add(message(AiMessageRole.USER, "Chi tiết dịch vụ " + "u".repeat(1200)));
            history.add(message(AiMessageRole.ASSISTANT, "Phản hồi " + "v".repeat(1200)));
        }
        history.add(message(AiMessageRole.USER, "Cập nhật: tôi không biết bơi và dị ứng hải sản."));
        history.add(message(AiMessageRole.ASSISTANT, "Dạ em đã ghi nhận lưu ý an toàn."));
        history.add(message(AiMessageRole.USER, "LATEST_QUESTION: gợi ý hoạt động phù hợp"));

        assertThat(client(false).generateResponse(history).getContent()).isEqualTo("OK");
        String sent = generations.getFirst().toString();
        assertThat(sent).contains("đổi thành 4 người", "không biết bơi", "LATEST_QUESTION");
        assertThat(sent.indexOf("đổi thành 4 người")).isLessThan(sent.indexOf("không biết bơi"));
        assertThat(summaries.getFirst().toString()).contains("đổi thành 4 người");
    }

    @Test void evictsAdditionalOldTurnWhenMemoryExceedsInitialCandidateBudget() throws Exception {
        inputLimit = 1100;
        var history = new ArrayList<AiMessage>();
        history.add(message(AiMessageRole.USER, "Ràng buộc an toàn cốt lõi: tôi không biết bơi."));
        history.add(message(AiMessageRole.ASSISTANT, "Ghi nhận."));
        history.add(message(AiMessageRole.USER, "Lượt cũ cần bỏ bớt nếu thiếu chỗ " + "w".repeat(350)));
        history.add(message(AiMessageRole.ASSISTANT, "Trao đổi " + "z".repeat(300)));
        history.add(message(AiMessageRole.USER, "LATEST_QUESTION: câu hỏi hiện tại"));

        assertThat(client(false).generateResponse(history).getContent()).isEqualTo("OK");
        String sent = generations.getFirst().toString();
        assertThat(sent).contains("không biết bơi", "LATEST_QUESTION");
        assertThat((sent.length() + 1) / 2).isLessThanOrEqualTo(1100 - 32);
    }

    private List<AiMessage> memoryHistory() {
        var history = new ArrayList<AiMessage>();
        history.add(message(AiMessageRole.USER, "I cannot swim. We are travelling with two children."));
        history.add(message(AiMessageRole.ASSISTANT, "Assistant invented price 12345"));
        for (int i = 0; i < 7; i++) {
            history.add(message(AiMessageRole.USER, "previous activity discussion " + "x".repeat(2500)));
            history.add(message(AiMessageRole.ASSISTANT, "y".repeat(2500)));
        }
        history.add(message(AiMessageRole.USER, "LATEST_QUESTION: suggest a suitable activity"));
        return history;
    }

    private CliProxyLlmClient client(boolean includeTools) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/v1beta/models/", exchange -> {
            JsonNode body = mapper.readTree(exchange.getRequestBody());
            String text = body.path("contents").path(0).path("parts").path(0).path("text").asText();
            counted.add(mapper.readTree(text));
            if (counterDelayMillis > 0) {
                try { Thread.sleep(counterDelayMillis); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            }
            byte[] response = (counterResponse != null ? counterResponse
                    : counterFails ? "{}" : "{\"totalTokens\":" + (text.length() + 1) / 2 + "}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(counterFails ? 503 : 200, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.createContext("/v1/chat/completions", exchange -> {
            JsonNode request = mapper.readTree(exchange.getRequestBody());
            if (request.path("messages").path(0).path("content").asText().startsWith("Select prior user-message IDs")) {
                summaries.add(request);
                byte[] summary = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"keep\\\":[\\\"m0\\\"]}\"}}]}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(summaryFails ? 503 : 200, summary.length);
                exchange.getResponseBody().write(summary); exchange.close(); return;
            }
            generations.add(request);
            boolean fail = primaryFails && request.path("model").asText().startsWith("gemini");
            byte[] response = (fail ? "{}" : "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"OK\"}}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(fail ? 503 : 200, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        var values = new MapConfigurationPropertySource(Map.of(
                "ai.cliproxy.enabled", "true", "ai.cliproxy.base-url", "http://127.0.0.1:" + server.getAddress().getPort(),
                "ai.cliproxy.api-key", "test-key", "ai.cliproxy.model", primaryModel,
                "ai.cliproxy.fallback-model", "gpt-oss-120b-medium", "ai.cliproxy.max-output-tokens", String.valueOf(outputLimit),
                "ai.cliproxy.max-input-tokens", String.valueOf(inputLimit), "ai.cliproxy.fallback-context-tokens", "600",
                "ai.cliproxy.input-safety-tokens", "32"));
        var properties = new Binder(values).bind("ai.cliproxy", Bindable.of(CliProxyProperties.class)).get();
        AIToolRegistry registry = includeTools ? new AIToolRegistry() {
            @Override public List<Map<String, Object>> getToolDefinitions() {
                return List.of(Map.of("name", "check_safety", "description", "equipment ".repeat(30),
                        "parameters", Map.of("type", "object", "properties", Map.of("query", Map.of("type", "string")))));
            }
        } : null;
        var client = new CliProxyLlmClient(RestClient.builder(), registry, properties);
        client.configureI18n(new SystemPromptBuilder() {
            @Override public String buildBasePrompt(SupportedLanguage language) { return "Keep business rules."; }
        }, LocalizedMessageService.standalone());
        return client;
    }

    private List<AiMessage> history() {
        List<AiMessage> result = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            result.add(message(AiMessageRole.USER, (i == 0 ? "oldest_marker " : "OLD_" + i) + "a".repeat(450)));
            result.add(message(AiMessageRole.ASSISTANT, "b".repeat(300)));
        }
        result.add(message(AiMessageRole.USER, "LATEST_QUESTION"));
        return result;
    }

    private AiMessage message(AiMessageRole role, String content) {
        var result = new AiMessage(); result.setRole(role); result.setContent(content); return result;
    }
}
