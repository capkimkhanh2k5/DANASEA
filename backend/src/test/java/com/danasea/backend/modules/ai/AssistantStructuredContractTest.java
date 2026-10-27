package com.danasea.backend.modules.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.danasea.backend.modules.ai.application.ports.LlmClientPort;
import com.danasea.backend.modules.ai.application.ports.ModerationPort;
import com.danasea.backend.modules.ai.application.tools.ToolExecutionContext;
import com.danasea.backend.modules.ai.application.tools.ToolExecutor;
import com.danasea.backend.modules.ai.application.usecases.ChatUseCase;
import com.danasea.backend.modules.ai.domain.models.LlmResponse;
import com.danasea.backend.modules.ai.domain.models.ToolCall;
import com.danasea.backend.modules.ai.domain.services.ChatHistoryService;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.databind.ObjectMapper;

class AssistantStructuredContractTest {
    private final UUID conversation = UUID.randomUUID(), actor = UUID.randomUUID();
    private final LlmClientPort llm = mock(LlmClientPort.class);
    private final ModerationPort moderation = mock(ModerationPort.class);
    private final ChatHistoryService history = mock(ChatHistoryService.class);
    private final ToolExecutor tool = mock(ToolExecutor.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private LlmResponse toolResponse(String name, String arguments) {
        LlmResponse response = new LlmResponse();
        response.setToolCalls(List.of(new ToolCall("call", name, arguments)));
        return response;
    }

    private LlmResponse answer(String text) {
        LlmResponse response = new LlmResponse(); response.setContent(text); return response;
    }

    private ChatUseCase chat(String name) {
        when(moderation.isSafe(any())).thenReturn(true);
        when(tool.getName()).thenReturn(name);
        return new ChatUseCase(llm, moderation, history, List.of(tool), mapper);
    }

    @Test void followupMergesStoredCriteriaAndKeepsBackendFactsSeparateFromProse() throws Exception {
        var chat = chat("ai_smart_search");
        when(history.getStructuredContext(conversation)).thenReturn(Optional.of(
                "{\"criteria\":{\"query\":\"kayak\",\"partySize\":3,\"limit\":15}}"));
        when(llm.generateResponse(any(), eq(SupportedLanguage.EN))).thenReturn(
                toolResponse("ai_smart_search", "{\"criteria\":{\"totalBudget\":900000,\"limit\":50}}"), answer("Here are the results."));
        when(tool.execute(any(), any(ToolExecutionContext.class))).thenReturn(
                "{\"status\":\"OK\",\"candidates\":[{\"service\":{\"id\":\"" + UUID.randomUUID() + "\"}}]}");
        var result = chat.processMessage(conversation, "Under 900000 total", SupportedLanguage.EN, actor);
        var argument = ArgumentCaptor.forClass(String.class);
        verify(tool).execute(argument.capture(), any(ToolExecutionContext.class));
        var criteria = mapper.readTree(argument.getValue()).path("criteria");
        assertThat(criteria.path("query").asText()).isEqualTo("kayak");
        assertThat(criteria.path("partySize").asInt()).isEqualTo(3);
        assertThat(criteria.path("limit").asInt()).isEqualTo(15);
        assertThat(result.getCards()).hasSize(1);
        assertThat(result.getCards().getFirst().sources()).hasSize(1);
        assertThat(result.isGeneratedTextVerified()).isFalse();
        verify(history).saveStructuredContext(eq(conversation), any());
        verify(history).finishProcessing(eq(conversation), any());
    }

    @Test void failedPolicyToolCannotAuthorizeGeneratedRefundClaims() {
        var chat = chat("get_policy");
        when(llm.generateResponse(any(), eq(SupportedLanguage.EN))).thenReturn(
                toolResponse("get_policy", "{}"), answer("Refund policy guarantees 100 percent."));
        when(tool.execute(any(), any(ToolExecutionContext.class))).thenReturn("{\"error\":\"UNAVAILABLE\"}");
        var result = chat.processMessage(conversation, "Refund?", SupportedLanguage.EN, actor);
        assertThat(result.getContent()).contains("without consulting the official policy source");
        assertThat(result.getCards().getFirst().sources()).isEmpty();
    }

    @Test void ownedCancellationPreviewProducesDeterministicPolicyTextAndSourcedCard() {
        var chat = chat("ai_customer_support");
        when(llm.generateResponse(any(), eq(SupportedLanguage.EN))).thenReturn(
                toolResponse("ai_customer_support", "{}"), answer("Refund policy guarantees 100 percent."));
        when(tool.execute(any(), any(ToolExecutionContext.class))).thenReturn(
                "{\"status\":\"READY\",\"cancellationPreview\":{\"totalRefund\":100000},\"financialActionPerformed\":false}");
        var result = chat.processMessage(conversation, "Refund?", SupportedLanguage.EN, actor);
        assertThat(result.getContent()).contains("official backend policy and cancellation preview");
        assertThat(result.getContent()).doesNotContain("100 percent");
        assertThat(result.getCards().getFirst().facts()).containsKey("cancellationPreview");
        assertThat(result.getCards().getFirst().actions()).isEmpty();
    }

    @Test void clarificationIsReturnedAsStructuredRequiredInputs() {
        var chat = chat("ai_smart_search");
        when(llm.generateResponse(any(), eq(SupportedLanguage.EN))).thenReturn(
                toolResponse("ai_smart_search", "{\"criteria\":{\"query\":\"family kayak\"}}"), answer("How many people?"));
        when(tool.execute(any(), any(ToolExecutionContext.class))).thenReturn(
                "{\"status\":\"NEEDS_INPUT\",\"requiredInputs\":[\"PARTY_SIZE_REQUIRED\"],\"candidates\":[]}");
        var result = chat.processMessage(conversation, "Family kayak", SupportedLanguage.EN, actor);
        assertThat(result.getResponseStatus()).isEqualTo("NEEDS_INPUT");
        assertThat(result.getRequiredInputs()).containsExactly("PARTY_SIZE_REQUIRED");
    }

    @Test void switchingActivityAndNaturalLanguageConstraintsOverridesStaleSavedCriteria() throws Exception {
        var chat = chat("ai_smart_search");
        when(history.getStructuredContext(conversation)).thenReturn(Optional.of(
                "{\"criteria\":{\"query\":\"chèo SUP\",\"partySize\":2,\"totalBudget\":2000000,"
                + "\"categoryId\":\"" + UUID.randomUUID() + "\",\"interests\":[\"sup\"]}}"));
        when(llm.generateResponse(any(), eq(SupportedLanguage.VI))).thenReturn(
                toolResponse("ai_smart_search", "{\"criteria\":{\"query\":\"du thuyền\"}}"),
                answer("Đã tìm thấy du thuyền phù hợp."));
        when(tool.execute(any(), any(ToolExecutionContext.class))).thenReturn(
                "{\"status\":\"OK\",\"candidates\":[{\"service\":{\"id\":\"" + UUID.randomUUID() + "\"}}]}");

        chat.processMessage(conversation,
                "Đổi sang tìm du thuyền cho 6 người ngân sách dưới 12 triệu ngày 2026-11-20",
                SupportedLanguage.VI, actor);

        var argument = ArgumentCaptor.forClass(String.class);
        verify(tool).execute(argument.capture(), any(ToolExecutionContext.class));
        var criteria = mapper.readTree(argument.getValue()).path("criteria");
        assertThat(criteria.path("query").asText()).isEqualTo("du thuyền");
        assertThat(criteria.path("partySize").asInt()).isEqualTo(6);
        assertThat(criteria.path("totalBudget").asLong()).isEqualTo(12_000_000L);
        assertThat(criteria.path("from").asText()).isEqualTo("2026-11-20");
        assertThat(criteria.has("categoryId")).isFalse();
        assertThat(criteria.has("interests")).isFalse();
    }

    @Test void explicitResetPhraseClearsAllStaleSavedCriteria() throws Exception {
        var chat = chat("ai_smart_search");
        when(history.getStructuredContext(conversation)).thenReturn(Optional.of(
                "{\"criteria\":{\"query\":\"kayak\",\"partySize\":4,\"totalBudget\":5000000,\"weatherSafeOnly\":true}}"));
        when(llm.generateResponse(any(), eq(SupportedLanguage.VI))).thenReturn(
                toolResponse("ai_smart_search", "{\"criteria\":{\"query\":\"lặn ngắm san hô\"}}"),
                answer("Kết quả tìm kiếm mới."));
        when(tool.execute(any(), any(ToolExecutionContext.class))).thenReturn(
                "{\"status\":\"OK\",\"candidates\":[]}");

        chat.processMessage(conversation,
                "Xóa bộ lọc cũ, tìm mới hoàn toàn tour lặn ngắm san hô",
                SupportedLanguage.VI, actor);

        var argument = ArgumentCaptor.forClass(String.class);
        verify(tool).execute(argument.capture(), any(ToolExecutionContext.class));
        var criteria = mapper.readTree(argument.getValue()).path("criteria");
        assertThat(criteria.path("query").asText()).isEqualTo("lặn ngắm san hô");
        assertThat(criteria.has("partySize")).isFalse();
        assertThat(criteria.has("totalBudget")).isFalse();
        assertThat(criteria.has("weatherSafeOnly")).isFalse();
    }
}
