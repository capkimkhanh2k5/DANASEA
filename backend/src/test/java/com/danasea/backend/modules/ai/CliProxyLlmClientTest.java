package com.danasea.backend.modules.ai;

import com.danasea.backend.modules.ai.domain.models.AiMessage;
import com.danasea.backend.modules.ai.domain.models.AiMessageRole;
import com.danasea.backend.modules.ai.domain.models.LlmResponse;
import com.danasea.backend.modules.ai.domain.services.AIToolRegistry;
import com.danasea.backend.modules.ai.infrastructure.cliproxy.CliProxyDto;
import com.danasea.backend.modules.ai.infrastructure.cliproxy.CliProxyException;
import com.danasea.backend.modules.ai.infrastructure.cliproxy.CliProxyLlmClient;
import com.danasea.backend.shared.i18n.SupportedLanguage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, org.springframework.boot.test.system.OutputCaptureExtension.class})
@DisplayName("CliProxyLlmClient Unit Tests")
public class CliProxyLlmClientTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    @Mock
    private AIToolRegistry aiToolRegistry;

    private CliProxyLlmClient cliProxyLlmClient;

    private final String defaultModel = "gemini-3.5-flash";
    private final String fallbackModel = "openai/gpt-oss-120b";
    private final String apiKey = "sk-cliproxy-test";

    @BeforeEach
    void setUp() {
        lenient().when(restClient.post()).thenReturn(requestBodyUriSpec);
        lenient().when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        lenient().when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        lenient().when(requestBodySpec.body(any(Object.class))).thenReturn(requestBodySpec);
        lenient().when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        lenient().when(aiToolRegistry.getToolDefinitions()).thenReturn(Collections.emptyList());

        cliProxyLlmClient = new CliProxyLlmClient(
                restClient,
                aiToolRegistry,
                apiKey,
                defaultModel,
                fallbackModel
        );
    }

    @Test
    @DisplayName("Primary model (gemini-3.5-flash) succeeds on first attempt")
    void testPrimaryModel_Gemini35Flash_Success() {
        CliProxyDto.Message msg = new CliProxyDto.Message("assistant", "Xin chào từ Gemini 3.5 Flash!");
        CliProxyDto.ChatResponse chatResponse = new CliProxyDto.ChatResponse(List.of(new CliProxyDto.ChatResponse.Choice(msg)));

        when(responseSpec.body(eq(CliProxyDto.ChatResponse.class))).thenReturn(chatResponse);

        AiMessage userMsg = new AiMessage();
        userMsg.setRole(AiMessageRole.USER);
        userMsg.setContent("Xin chào");

        LlmResponse response = cliProxyLlmClient.generateResponse(List.of(userMsg), SupportedLanguage.VI);

        assertNotNull(response);
        assertEquals("Xin chào từ Gemini 3.5 Flash!", response.getContent());
        assertEquals("...test", response.getKeyMasked());

        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
        verify(requestBodySpec).body(bodyCaptor.capture());
        CliProxyDto.ChatRequest captured = (CliProxyDto.ChatRequest) bodyCaptor.getValue();
        assertEquals("gemini-3.5-flash", captured.model());
        verify(restClient, times(1)).post();
    }

    @Test
    @DisplayName("Primary model fails with 500 -> Falls back to secondary model (openai/gpt-oss-120b)")
    void testPrimaryModel_Fails500_FallsBackToSecondaryModel() {
        RestClientResponseException server500 = new RestClientResponseException(
                "Internal Server Error", HttpStatusCode.valueOf(500), "500", null, null, null
        );

        CliProxyDto.Message fallbackMsg = new CliProxyDto.Message("assistant", "Phản hồi từ GPT-OSS 120B");
        CliProxyDto.ChatResponse fallbackResponse = new CliProxyDto.ChatResponse(List.of(new CliProxyDto.ChatResponse.Choice(fallbackMsg)));

        when(responseSpec.body(eq(CliProxyDto.ChatResponse.class)))
                .thenThrow(server500)
                .thenReturn(fallbackResponse);

        AiMessage userMsg = new AiMessage();
        userMsg.setRole(AiMessageRole.USER);
        userMsg.setContent("Test 500 error fallback");

        LlmResponse response = cliProxyLlmClient.generateResponse(List.of(userMsg));

        assertNotNull(response);
        assertEquals("Phản hồi từ GPT-OSS 120B", response.getContent());
        verify(restClient, times(2)).post();
    }

    @Test
    @DisplayName("Primary transport times out early -> Falls back to secondary model")
    void testPrimaryModel_Timeout_FallsBackToSecondaryModel() {
        ResourceAccessException timeoutEx = new ResourceAccessException("Read timed out after 15000ms");

        CliProxyDto.Message fallbackMsg = new CliProxyDto.Message("assistant", "Phản hồi sau khi Gemini timeout");
        CliProxyDto.ChatResponse fallbackResponse = new CliProxyDto.ChatResponse(List.of(new CliProxyDto.ChatResponse.Choice(fallbackMsg)));

        when(responseSpec.body(eq(CliProxyDto.ChatResponse.class)))
                .thenThrow(timeoutEx)
                .thenReturn(fallbackResponse);

        AiMessage userMsg = new AiMessage();
        userMsg.setRole(AiMessageRole.USER);
        userMsg.setContent("Test timeout fallback");

        LlmResponse response = cliProxyLlmClient.generateResponse(List.of(userMsg));

        assertNotNull(response);
        assertEquals("Phản hồi sau khi Gemini timeout", response.getContent());
        verify(restClient, times(2)).post();
    }

    @Test
    @DisplayName("Both primary and secondary models fail -> Throws CliProxyException")
    void testBothModelsFail_ThrowsCliProxyException() {
        RestClientResponseException server502 = new RestClientResponseException(
                "Bad Gateway", HttpStatusCode.valueOf(502), "502", null, null, null
        );

        when(responseSpec.body(eq(CliProxyDto.ChatResponse.class))).thenThrow(server502);

        AiMessage userMsg = new AiMessage();
        userMsg.setRole(AiMessageRole.USER);
        userMsg.setContent("Test both fail");

        assertThrows(CliProxyException.class, () -> cliProxyLlmClient.generateResponse(List.of(userMsg)));
        verify(restClient, times(2)).post();
    }

    @Test
    @DisplayName("Tool calling supported and parsed properly")
    void testToolCalling_ParsedProperly() {
        CliProxyDto.ToolCallDto toolCallDto = new CliProxyDto.ToolCallDto(
                "call_123",
                "function",
                new CliProxyDto.FunctionDto("getCatalog", "{\"category\":\"tours\"}")
        );
        CliProxyDto.Message msg = new CliProxyDto.Message("assistant", null, List.of(toolCallDto), null);
        CliProxyDto.ChatResponse chatResponse = new CliProxyDto.ChatResponse(List.of(new CliProxyDto.ChatResponse.Choice(msg)));

        when(responseSpec.body(eq(CliProxyDto.ChatResponse.class))).thenReturn(chatResponse);

        AiMessage userMsg = new AiMessage();
        userMsg.setRole(AiMessageRole.USER);
        userMsg.setContent("Tìm tour giúp tôi");

        LlmResponse response = cliProxyLlmClient.generateResponse(List.of(userMsg));

        assertNotNull(response);
        assertNotNull(response.getToolCalls());
        assertEquals(1, response.getToolCalls().size());
        assertEquals("call_123", response.getToolCalls().getFirst().getId());
        assertEquals("getCatalog", response.getToolCalls().getFirst().getName());
        assertEquals("{\"category\":\"tours\"}", response.getToolCalls().getFirst().getArguments());
    }

    @Test
    void upstreamErrorBodyIsNotExposedInLogsOrFinalException(org.springframework.boot.test.system.CapturedOutput output) {
        String sensitive = "upstream-error-credential-must-not-leak";
        var failure = new RestClientResponseException("upstream failure: " + sensitive, HttpStatusCode.valueOf(500),
                "failure", null, sensitive.getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.charset.StandardCharsets.UTF_8);
        when(responseSpec.body(eq(CliProxyDto.ChatResponse.class))).thenThrow(failure);
        CliProxyException thrown = assertThrows(CliProxyException.class,
                () -> cliProxyLlmClient.generateResponse(List.of()));
        assertFalse(thrown.getMessage().contains(sensitive));
        assertFalse(output.getAll().contains(sensitive));
    }
}
