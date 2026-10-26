package com.danasea.backend.modules.ai;

import com.danasea.backend.modules.ai.application.ports.LlmClientPort;
import com.danasea.backend.modules.ai.application.ports.ModerationPort;
import com.danasea.backend.modules.ai.application.tools.ToolExecutor;
import com.danasea.backend.modules.ai.application.tools.ToolExecutionContext;
import com.danasea.backend.modules.ai.application.usecases.ChatUseCase;
import com.danasea.backend.modules.ai.domain.models.AiMessageRole;
import com.danasea.backend.modules.ai.domain.models.AiMessage;
import com.danasea.backend.modules.ai.domain.models.LlmResponse;
import com.danasea.backend.modules.ai.domain.models.ToolCall;
import com.danasea.backend.modules.ai.domain.services.ChatHistoryService;
import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiMessageJpaEntity;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Acceptance Criteria & 4-Tier Test Suite for AssistantChatUseCaseTest.
 *
 * <p>Requirements:
 * - R2: Verifies `search` (search_service), `get_detail` (get_service_detail), and `get_policy` are called in correct contexts.
 * - R2: `get_policy` accepts policy_type (CANCELLATION, REFUND, WEATHER_CANCELLATION, SAFETY, GENERAL).
 * - R6: Verifies `get_weather_forecast` and `get_safety_alert` tool execution.
 * - Chat loop: Multi-step tool loop (max 5 iterations), history recording, unknown tool handling.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AssistantChatUseCase Acceptance Test Suite (Tiers 1-4)")
public class AssistantChatUseCaseTest {

    @Mock
    private LlmClientPort llmClientPort;

    @Mock
    private ModerationPort moderationPort;

    @Mock
    private ChatHistoryService chatHistoryService;

    @Mock
    private ToolExecutor searchTool;

    @Mock
    private ToolExecutor detailTool;

    @Mock
    private ToolExecutor policyTool;

    @Mock
    private ToolExecutor weatherTool;

    @Mock
    private ToolExecutor safetyTool;

    private ChatUseCase chatUseCase;
    private final UUID conversationId = UUID.randomUUID();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        lenient().when(searchTool.getName()).thenReturn("search_service");
        lenient().when(detailTool.getName()).thenReturn("get_service_detail");
        lenient().when(policyTool.getName()).thenReturn("get_policy");
        lenient().when(weatherTool.getName()).thenReturn("get_weather_forecast");
        lenient().when(safetyTool.getName()).thenReturn("get_safety_alert");

        chatUseCase = new ChatUseCase(
                llmClientPort,
                moderationPort,
                chatHistoryService,
                List.of(searchTool, detailTool, policyTool, weatherTool, safetyTool),
                objectMapper
        );

        lenient().when(moderationPort.isSafe(any())).thenReturn(true);
    }

    // =========================================================================
    // TIER 1: FEATURE COVERAGE (>=5 test cases across R2 & R6 core tools)
    // =========================================================================

    @Test
    void sendsRecentConversationChronologicallyWithSavedTravelContext() {
        AiMessageJpaEntity earlier = new AiMessageJpaEntity();
        earlier.setConversationId(conversationId);
        earlier.setRole(AiMessageRole.USER);
        earlier.setContent("Tôi thích chèo SUP.");
        AiMessageJpaEntity previousReply = new AiMessageJpaEntity();
        previousReply.setConversationId(conversationId);
        previousReply.setRole(AiMessageRole.ASSISTANT);
        previousReply.setContent("Tôi đã ghi nhận.");
        AiMessageJpaEntity current = new AiMessageJpaEntity();
        current.setConversationId(conversationId);
        current.setRole(AiMessageRole.USER);
        current.setContent("Tìm hoạt động phù hợp cho tôi.");
        when(chatHistoryService.getRecentMessages(conversationId, 20))
                .thenReturn(List.of(current, previousReply, earlier));
        when(chatHistoryService.getStructuredContext(conversationId))
                .thenReturn(Optional.of("{\"criteria\":{\"query\":\"SUP\",\"participants\":2}}"));
        List<AiMessage> sent = new ArrayList<>();
        when(llmClientPort.generateResponse(any(), eq(SupportedLanguage.VI))).thenAnswer(invocation -> {
            sent.addAll(invocation.getArgument(0));
            LlmResponse response = new LlmResponse();
            response.setContent("Tôi sẽ tìm hoạt động chèo SUP.");
            return response;
        });

        chatUseCase.processMessage(conversationId, current.getContent(), SupportedLanguage.VI);

        assertEquals(4, sent.size());
        assertEquals(List.of("Tôi thích chèo SUP.", "Tôi đã ghi nhận.", "Tìm hoạt động phù hợp cho tôi."),
                sent.subList(0, 3).stream().map(AiMessage::getContent).toList());
        assertEquals(AiMessageRole.ASSISTANT, sent.get(1).getRole());
        assertTrue(sent.subList(0, 3).stream().allMatch(message -> conversationId.equals(message.getConversationId())));
        assertTrue(sent.get(3).getContent().contains("\"participants\":2"));
        assertTrue(sent.get(3).getContent().contains("\"query\":\"SUP\""));
        verify(chatHistoryService).appendMessage(conversationId, AiMessageRole.USER, current.getContent(), null);
    }

    @Test
    @DisplayName("i18n: conversation locale is propagated to the LLM and tool execution context")
    void localizedConversation_propagatesLocaleToLlmAndTools() {
        String arguments = "{\"keyword\":\"kayak\"}";
        LlmResponse toolCallResponse = new LlmResponse();
        toolCallResponse.setToolCalls(List.of(new ToolCall("tc-i18n", "search_service", arguments)));
        LlmResponse finalResponse = new LlmResponse();
        finalResponse.setContent("I found a kayak tour.");

        when(llmClientPort.generateResponse(any(), eq(SupportedLanguage.EN)))
                .thenReturn(toolCallResponse)
                .thenReturn(finalResponse);
        when(searchTool.execute(eq(arguments), any(ToolExecutionContext.class)))
                .thenReturn("{\"results\":[]}");

        LlmResponse result = chatUseCase.processMessage(conversationId, "Find a kayak tour", SupportedLanguage.EN);

        assertEquals("I found a kayak tour.", result.getContent());
        verify(llmClientPort, times(2)).generateResponse(any(), eq(SupportedLanguage.EN));
        verify(searchTool).execute(eq(arguments), argThat(context ->
                context.language() == SupportedLanguage.EN
                        && conversationId.equals(context.conversationId())));
    }

    @Test
    @DisplayName("Tier 1 - F2.1: search_service is called in tourism service discovery context")
    void testSearchService_CalledInDiscoveryContext() {
        // Step 1: LLM decides to call search_service
        LlmResponse toolCallResp = new LlmResponse();
        toolCallResp.setContent("");
        ToolCall tc = new ToolCall("tc-search-1", "search_service", "{\"keyword\":\"lặn ngắm san hô\",\"location\":\"Sơn Trà\"}");
        toolCallResp.setToolCalls(List.of(tc));

        // Step 2: Final response after tool execution
        LlmResponse finalResp = new LlmResponse();
        finalResp.setContent("Tôi tìm thấy 2 tour lặn ngắm san hô phù hợp tại Sơn Trà.");

        when(llmClientPort.generateResponse(any()))
                .thenReturn(toolCallResp)
                .thenReturn(finalResp);

        when(searchTool.execute("{\"keyword\":\"lặn ngắm san hô\",\"location\":\"Sơn Trà\"}"))
                .thenReturn("{\"results\":[{\"id\":\"srv-1\",\"name\":\"Tour lặn Sơn Trà\",\"price\":800000}]}");

        LlmResponse result = chatUseCase.processMessage(conversationId, "Tìm cho tôi tour lặn ngắm san hô ở Sơn Trà");

        assertEquals("Tôi tìm thấy 2 tour lặn ngắm san hô phù hợp tại Sơn Trà.", result.getContent());
        verify(searchTool, times(1)).execute(any());
        verify(chatHistoryService).appendMessage(eq(conversationId), eq(AiMessageRole.TOOL), anyString(), eq("tc-search-1"));
    }

    @Test
    @DisplayName("Tier 1 - F2.2: get_service_detail is called in specific service detail inquiry context")
    void testGetServiceDetail_CalledInDetailContext() {
        LlmResponse toolCallResp = new LlmResponse();
        ToolCall tc = new ToolCall("tc-detail-1", "get_service_detail", "{\"serviceId\":\"srv-101\"}");
        toolCallResp.setToolCalls(List.of(tc));

        LlmResponse finalResp = new LlmResponse();
        finalResp.setContent("Tour srv-101 bao gồm ca nô cao tốc, áo phao và hướng dẫn viên chuyên nghiệp.");

        when(llmClientPort.generateResponse(any()))
                .thenReturn(toolCallResp)
                .thenReturn(finalResp);

        when(detailTool.execute("{\"serviceId\":\"srv-101\"}"))
                .thenReturn("{\"id\":\"srv-101\",\"title\":\"Lặn ngắm san hô VIP\",\"price\":1200000,\"included\":[\"cano\",\"guide\"]}");

        LlmResponse result = chatUseCase.processMessage(conversationId, "Cho tôi xem chi tiết tour srv-101");

        assertEquals("Tour srv-101 bao gồm ca nô cao tốc, áo phao và hướng dẫn viên chuyên nghiệp.", result.getContent());
        verify(detailTool, times(1)).execute("{\"serviceId\":\"srv-101\"}");
    }

    @Test
    @DisplayName("Tier 1 - F2.3: get_policy is called with CANCELLATION policy type in refund/cancellation context")
    void testGetPolicy_CalledInCancellationContext() {
        LlmResponse toolCallResp = new LlmResponse();
        ToolCall tc = new ToolCall("tc-pol-1", "get_policy", "{\"policy_type\":\"CANCELLATION\"}");
        toolCallResp.setToolCalls(List.of(tc));

        LlmResponse finalResp = new LlmResponse();
        finalResp.setContent("Theo chính sách hủy tour của DanaSea, quý khách được hoàn 100% nếu hủy trước 24 giờ.");

        when(llmClientPort.generateResponse(any()))
                .thenReturn(toolCallResp)
                .thenReturn(finalResp);

        when(policyTool.execute("{\"policy_type\":\"CANCELLATION\"}"))
                .thenReturn("{\"policy_type\":\"CANCELLATION\",\"text\":\"Hoàn tiền 100% khi hủy trước 24h. Hoàn 50% trước 12h.\"}");

        LlmResponse result = chatUseCase.processMessage(conversationId, "Nếu tôi bận đột xuất thì chính sách hủy tour thế nào?");

        assertEquals("Theo chính sách hủy tour của DanaSea, quý khách được hoàn 100% nếu hủy trước 24 giờ.", result.getContent());
        verify(policyTool, times(1)).execute("{\"policy_type\":\"CANCELLATION\"}");
        verify(chatHistoryService).appendMessage(eq(conversationId), eq(AiMessageRole.TOOL), contains("CANCELLATION"), eq("tc-pol-1"));
    }

    @Test
    @DisplayName("Tier 1 - F2.4: get_weather_forecast is called in weather check context")
    void testGetWeatherForecast_CalledInWeatherContext() {
        LlmResponse toolCallResp = new LlmResponse();
        ToolCall tc = new ToolCall("tc-wea-1", "get_weather_forecast", "{\"location\":\"Đà Nẵng\",\"date\":\"2026-09-20\"}");
        toolCallResp.setToolCalls(List.of(tc));

        LlmResponse finalResp = new LlmResponse();
        finalResp.setContent("Dự báo thời tiết ngày 20/09 tại Đà Nẵng: Trời nắng đẹp, sóng êm 0.5m, rất thích hợp đi biển.");

        when(llmClientPort.generateResponse(any()))
                .thenReturn(toolCallResp)
                .thenReturn(finalResp);

        when(weatherTool.execute(anyString()))
                .thenReturn("{\"location\":\"Đà Nẵng\",\"date\":\"2026-09-20\",\"condition\":\"Sunny\",\"waveHeight\":0.5}");

        LlmResponse result = chatUseCase.processMessage(conversationId, "Thời tiết ngày 20/09 ở Đà Nẵng có thích hợp đi tour biển không?");

        assertEquals("Dự báo thời tiết ngày 20/09 tại Đà Nẵng: Trời nắng đẹp, sóng êm 0.5m, rất thích hợp đi biển.", result.getContent());
        verify(weatherTool, times(1)).execute(anyString());
    }

    @Test
    @DisplayName("Tier 1 - F2.5: get_safety_alert is called in maritime safety context")
    void testGetSafetyAlert_CalledInSafetyContext() {
        LlmResponse toolCallResp = new LlmResponse();
        ToolCall tc = new ToolCall("tc-saf-1", "get_safety_alert", "{\"location\":\"Bán đảo Sơn Trà\"}");
        toolCallResp.setToolCalls(List.of(tc));

        LlmResponse finalResp = new LlmResponse();
        finalResp.setContent("Hiện tại khu vực biển Sơn Trà có cảnh báo sóng ngầm cấp độ nhẹ, khuyến cáo chỉ bơi trong vùng phao cứu sinh.");

        when(llmClientPort.generateResponse(any()))
                .thenReturn(toolCallResp)
                .thenReturn(finalResp);

        when(safetyTool.execute(anyString()))
                .thenReturn("{\"location\":\"Bán đảo Sơn Trà\",\"alertLevel\":\"YELLOW\",\"message\":\"Cảnh báo sóng ngầm nhẹ\"}");

        LlmResponse result = chatUseCase.processMessage(conversationId, "Khu vực biển Sơn Trà hiện tại có an toàn để tắm không?");

        assertEquals("Hiện tại khu vực biển Sơn Trà có cảnh báo sóng ngầm cấp độ nhẹ, khuyến cáo chỉ bơi trong vùng phao cứu sinh.", result.getContent());
        verify(safetyTool, times(1)).execute(anyString());
    }

    // =========================================================================
    // TIER 2: BOUNDARY & CORNER CASES (>=5 test cases)
    // =========================================================================

    @Test
    @DisplayName("Tier 2 - B2.1: Unknown tool requested by LLM returns error JSON without crashing")
    void testUnknownToolRequested_HandledGracefully() {
        LlmResponse toolCallResp = new LlmResponse();
        ToolCall tc = new ToolCall("tc-unk-1", "non_existent_payment_tool", "{\"amount\":1000}");
        toolCallResp.setToolCalls(List.of(tc));

        LlmResponse finalResp = new LlmResponse();
        finalResp.setContent("Xin lỗi, tôi không thể thực hiện công cụ này.");

        when(llmClientPort.generateResponse(any()))
                .thenReturn(toolCallResp)
                .thenReturn(finalResp);

        LlmResponse result = chatUseCase.processMessage(conversationId, "Thực hiện thanh toán tự động");

        assertEquals("Xin lỗi, tôi không thể thực hiện công cụ này.", result.getContent());
        // Verify unknown tool error was appended to chat history
        verify(chatHistoryService).appendMessage(eq(conversationId), eq(AiMessageRole.TOOL),
                eq("{\"errorCode\":\"AI_TOOL_UNKNOWN\"}"), eq("tc-unk-1"));
    }

    @Test
    @DisplayName("Tier 2 - B2.2: Max loop iterations (5 iterations) circuit breaker prevents infinite loop")
    void testMaxIterationsSafeguard_BreaksAfter5Turns() {
        // Mock LLM always requesting searchTool on every turn
        LlmResponse recursiveToolResp = new LlmResponse();
        ToolCall tc = new ToolCall("tc-loop", "search_service", "{}");
        recursiveToolResp.setToolCalls(List.of(tc));
        recursiveToolResp.setContent("Looping...");

        when(llmClientPort.generateResponse(any())).thenReturn(recursiveToolResp);
        when(searchTool.execute(anyString())).thenReturn("{\"status\":\"ok\"}");

        LlmResponse result = chatUseCase.processMessage(conversationId, "Run infinite loop");

        assertNotNull(result);
        assertEquals("Sorry, I am taking too long to process your request.", result.getContent());
        verify(llmClientPort, times(5)).generateResponse(any());
    }

    @Test
    @DisplayName("Tier 2 - B2.3: Multiple tool calls returned in single LLM turn are executed sequentially")
    void testMultipleToolCallsInSingleTurn_AllExecuted() {
        LlmResponse multiToolResp = new LlmResponse();
        ToolCall tc1 = new ToolCall("tc-multi-1", "search_service", "{\"keyword\":\"lặn\"}");
        ToolCall tc2 = new ToolCall("tc-multi-2", "get_weather_forecast", "{\"location\":\"Sơn Trà\"}");
        multiToolResp.setToolCalls(List.of(tc1, tc2));

        LlmResponse finalResp = new LlmResponse();
        finalResp.setContent("Đã tìm kiếm tour và thời tiết tại Sơn Trà.");

        when(llmClientPort.generateResponse(any()))
                .thenReturn(multiToolResp)
                .thenReturn(finalResp);

        when(searchTool.execute(anyString())).thenReturn("{\"count\": 1}");
        when(weatherTool.execute(anyString())).thenReturn("{\"weather\": \"Good\"}");

        LlmResponse result = chatUseCase.processMessage(conversationId, "Tìm tour lặn và xem thời tiết luôn giúp tôi");

        assertEquals("Đã tìm kiếm tour và thời tiết tại Sơn Trà.", result.getContent());
        verify(searchTool, times(1)).execute(anyString());
        verify(weatherTool, times(1)).execute(anyString());
        verify(chatHistoryService, times(2)).appendMessage(eq(conversationId), eq(AiMessageRole.TOOL), anyString(), anyString());
    }

    @Test
    @DisplayName("Tier 2 - B2.4: Tool call with empty arguments {} executed cleanly")
    void testToolCall_EmptyArguments_ExecutedSuccessfully() {
        LlmResponse toolResp = new LlmResponse();
        ToolCall tc = new ToolCall("tc-empty-1", "get_policy", "{}");
        toolResp.setToolCalls(List.of(tc));

        LlmResponse finalResp = new LlmResponse();
        finalResp.setContent("Chính sách chung của hệ thống...");

        when(llmClientPort.generateResponse(any()))
                .thenReturn(toolResp)
                .thenReturn(finalResp);

        when(policyTool.execute("{}")).thenReturn("{\"policy\":\"Chính sách mặc định\"}");

        LlmResponse result = chatUseCase.processMessage(conversationId, "Xem chính sách chung");

        assertEquals("Chính sách chung của hệ thống...", result.getContent());
        verify(policyTool).execute("{}");
    }

    @Test
    @DisplayName("Tier 2 - B2.5: Direct text response without tool calls terminates loop immediately on turn 1")
    void testDirectResponseWithoutTools_TerminatesImmediately() {
        LlmResponse directResp = new LlmResponse();
        directResp.setContent("Xin chào! Tôi có thể giúp gì cho chuyến du lịch biển Đà Nẵng của bạn?");
        directResp.setToolCalls(null);

        when(llmClientPort.generateResponse(any())).thenReturn(directResp);

        LlmResponse result = chatUseCase.processMessage(conversationId, "Xin chào");

        assertEquals("Xin chào! Tôi có thể giúp gì cho chuyến du lịch biển Đà Nẵng của bạn?", result.getContent());
        verify(llmClientPort, times(1)).generateResponse(any());
        verify(searchTool, never()).execute(anyString());
        verify(detailTool, never()).execute(anyString());
        verify(policyTool, never()).execute(anyString());
        verify(weatherTool, never()).execute(anyString());
        verify(safetyTool, never()).execute(anyString());
    }

    // =========================================================================
    // TIER 3: CROSS-FEATURE COMBINATIONS
    // =========================================================================

    @Test
    @DisplayName("Tier 3 - C2.1: Tool execution audit trail - tool calls serialized and appended to history")
    void testToolExecution_HistoryAuditTrailComplete() {
        LlmResponse toolResp = new LlmResponse();
        ToolCall tc = new ToolCall("tc-audit-10", "get_policy", "{\"policy_type\":\"REFUND\"}");
        toolResp.setToolCalls(List.of(tc));

        LlmResponse finalResp = new LlmResponse();
        finalResp.setContent("Chính sách hoàn tiền là 100%.");

        when(llmClientPort.generateResponse(any()))
                .thenReturn(toolResp)
                .thenReturn(finalResp);

        when(policyTool.execute(anyString())).thenReturn("{\"refund\":\"100%\"}");

        chatUseCase.processMessage(conversationId, "Hoàn tiền thế nào?");

        // 1. User message appended
        verify(chatHistoryService).appendMessage(eq(conversationId), eq(AiMessageRole.USER), eq("Hoàn tiền thế nào?"), isNull());
        // 2. Assistant message with tool calls JSON appended
        verify(chatHistoryService).appendMessage(eq(conversationId), eq(AiMessageRole.ASSISTANT), any(), contains("tc-audit-10"));
        // 3. Tool response message appended
        verify(chatHistoryService).appendMessage(eq(conversationId), eq(AiMessageRole.TOOL), eq("{\"refund\":\"100%\"}"), eq("tc-audit-10"));
        // 4. Final Assistant response appended
        verify(chatHistoryService).appendMessage(eq(conversationId), eq(AiMessageRole.ASSISTANT), eq("Chính sách hoàn tiền là 100%."), isNull());
    }

    // =========================================================================
    // TIER 4: REAL-WORLD APPLICATION SCENARIOS
    // =========================================================================

    @Test
    @DisplayName("Tier 4 - R2.1: Full conversational flow - Discovery to Detail to Policy validation")
    void testFullConversationalFlow_DiscoveryDetailPolicy() {
        // Step 1: User asks to find diving tour -> search_service executed
        LlmResponse turn1Tool = new LlmResponse();
        turn1Tool.setToolCalls(List.of(new ToolCall("tc-flow-1", "search_service", "{\"keyword\":\"lặn\"}")));
        LlmResponse turn1Final = new LlmResponse();
        turn1Final.setContent("Tôi tìm thấy tour Lặn Ngắm San Hô Sơn Trà (mã srv-001).");

        when(llmClientPort.generateResponse(any()))
                .thenReturn(turn1Tool)
                .thenReturn(turn1Final);
        when(searchTool.execute(anyString())).thenReturn("{\"services\":[{\"id\":\"srv-001\"}]}");

        LlmResponse res1 = chatUseCase.processMessage(conversationId, "Tôi muốn tìm tour lặn biển");
        assertEquals("Tôi tìm thấy tour Lặn Ngắm San Hô Sơn Trà (mã srv-001).", res1.getContent());

        // Step 2: User asks for refund policy of that tour -> get_policy executed
        LlmResponse turn2Tool = new LlmResponse();
        turn2Tool.setToolCalls(List.of(new ToolCall("tc-flow-2", "get_policy", "{\"policy_type\":\"REFUND\"}")));
        LlmResponse turn2Final = new LlmResponse();
        turn2Final.setContent("Tour này áp dụng chính sách hoàn 100% nếu hủy trước 24h.");

        when(llmClientPort.generateResponse(any()))
                .thenReturn(turn2Tool)
                .thenReturn(turn2Final);
        when(policyTool.execute(anyString())).thenReturn("{\"policy\":\"REFUND_100_24H\"}");

        LlmResponse res2 = chatUseCase.processMessage(conversationId, "Nếu trời mưa bão thì có được hoàn tiền không?");
        assertEquals("Tour này áp dụng chính sách hoàn 100% nếu hủy trước 24h.", res2.getContent());

        verify(searchTool, times(1)).execute(anyString());
        verify(policyTool, times(1)).execute(anyString());
    }
}
