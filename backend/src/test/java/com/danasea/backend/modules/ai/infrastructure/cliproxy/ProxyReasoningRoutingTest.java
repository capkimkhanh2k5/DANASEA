package com.danasea.backend.modules.ai.infrastructure.cliproxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.danasea.backend.modules.ai.domain.models.AiMessage;
import com.danasea.backend.modules.ai.domain.models.AiMessageRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

class ProxyReasoningRoutingTest {
    @Test
    void primaryAndFallbackSendHighReasoningWithConversationHistory() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<JsonNode> requests = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requests.add(mapper.readTree(exchange.getRequestBody()));
            boolean primary = requests.size() == 1;
            byte[] response = (primary ? "{}" : "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"SUP\"}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(primary ? 503 : 200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var transport = RestClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1").build();
            var client = new CliProxyLlmClient(transport, null, "test-key",
                    "gemini-3.5-flash-lite", "gpt-oss-120b-medium");
            List<AiMessage> history = List.of(message(AiMessageRole.USER, "Tôi thích chèo SUP."),
                    message(AiMessageRole.ASSISTANT, "Tôi đã ghi nhận."),
                    message(AiMessageRole.USER, "Tôi thích hoạt động gì?"));

            assertThat(client.generateResponse(history).getContent()).isEqualTo("SUP");
            assertThat(requests).hasSize(2);
            assertThat(requests.get(0).path("model").asText()).isEqualTo("gemini-3.5-flash-lite");
            assertThat(requests.get(1).path("model").asText()).isEqualTo("gpt-oss-120b-medium");
            for (JsonNode request : requests) {
                assertThat(request.path("reasoning_effort").asText()).isEqualTo("high");
                JsonNode messages = request.path("messages");
                assertThat(messages.size()).isEqualTo(4);
                assertThat(messages.path(0).path("role").asText()).isEqualTo("system");
                assertThat(messages.path(1).path("content").asText()).isEqualTo("Tôi thích chèo SUP.");
                assertThat(messages.path(2).path("role").asText()).isEqualTo("assistant");
                assertThat(messages.path(3).path("content").asText()).isEqualTo("Tôi thích hoạt động gì?");
            }
        } finally {
            server.stop(0);
        }
    }

    private AiMessage message(AiMessageRole role, String content) {
        AiMessage message = new AiMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
