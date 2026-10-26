package com.danasea.backend.modules.ai.infrastructure.cliproxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import com.danasea.backend.configs.properties.HttpClientProperties;
import com.danasea.backend.modules.ai.infrastructure.cliproxy.configs.CliProxyProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

class ProxyModerationRoutingTest {
    @ParameterizedTest
    @CsvSource({"BENIGN,true", "MALICIOUS,false", "BENIGN with extra text,false", "SAFE,false", "benign,false", "0.05,false", "0.50,false", "0.99,false", "UNSAFE,false", "INJECTION,false", "JAILBREAK,false", "'',false"})
    void moderationUsesOnlyGatewayAndRequiresExactClassification(String output, boolean safe) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        ObjectMapper mapper = new ObjectMapper();
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = mapper.writeValueAsBytes(java.util.Map.of("choices", List.of(
                    java.util.Map.of("message", java.util.Map.of("content", output)))));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            new ApplicationContextRunner()
                    .withBean(RestClient.Builder.class, RestClient::builder)
                    .withBean(CliProxyProperties.class, () -> new CliProxyProperties(true, base + "/v1", "gateway-key", "gateway-model", "fallback"))
                    .withBean(HttpClientProperties.class, () -> new HttpClientProperties(Duration.ofSeconds(2), Duration.ofSeconds(2)))
                    .withBean(ObjectMapper.class, () -> mapper)
                    .withBean(CliProxyModerationClient.class)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean(CliProxyModerationClient.class).isSafe("Xin chào" + "a".repeat(2400))).isEqualTo(safe);
                        assertThat(path.get()).isEqualTo("/v1/chat/completions");
                        assertThat(authorization.get()).isEqualTo("Bearer gateway-key");
                        assertThat(body.get()).contains("gateway-model", "system", "BENIGN", "Xin chào");
                        try {
                            assertThat(mapper.readTree(body.get()).path("reasoning_effort").asText()).isEqualTo("high");
                            assertThat(mapper.readTree(body.get()).path("messages").path(1).path("content").asText()).hasSize(2000);
                        } catch (java.io.IOException exception) {
                            throw new AssertionError(exception);
                        }
                    });
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @CsvSource({"401,denied", "429,limited", "503,unavailable", "200,not-json", "200,{}"})
    void transportOrMalformedResponseFailsClosed(int status, String payload) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var client = new CliProxyModerationClient(RestClient.builder(), new CliProxyProperties(true,
                    "http://127.0.0.1:" + server.getAddress().getPort(), "gateway-key", "model", "fallback"),
                    new HttpClientProperties(Duration.ofSeconds(2), Duration.ofSeconds(2)), new ObjectMapper());
            assertThat(client.isSafe("unsafe input")).isFalse();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void disabledGatewayRejectsInputWithoutCallingTransport() {
        var client = new CliProxyModerationClient(RestClient.builder(), new CliProxyProperties(false,
                "http://127.0.0.1:1", "gateway-key", "model", "fallback"),
                new HttpClientProperties(Duration.ofMillis(50), Duration.ofMillis(50)), new ObjectMapper());
        assertThat(client.isSafe("input")).isFalse();
        assertThat(client.isSafe(null)).isTrue();
        assertThat(client.isSafe(" ")).isTrue();
    }
}
