package com.danasea.backend.modules.ai.infrastructure.cliproxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.danasea.backend.configs.properties.HttpClientProperties;
import com.danasea.backend.modules.ai.application.ports.AiExecutionBudget;
import com.danasea.backend.modules.ai.infrastructure.cliproxy.configs.CliProxyProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class ProxyDeadlineRoutingTest {
    private static final String COMPLETE = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"OK\"}}]}";

    @Test
    void shortRemainingBudgetStillAllowsFastModelInsteadOfSkippingIt() throws Exception {
        var count = new AtomicInteger();
        var server = server(exchange -> { count.incrementAndGet(); respond(exchange, 200, COMPLETE); });
        try (var budget = AiExecutionBudget.open(Duration.ofSeconds(2), 6)) {
            assertThat(client(server).generateResponse(List.of()).getContent()).isEqualTo("OK");
            assertThat(count.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }

    @Test
    void slowBodyCannotOutliveSharedBudgetOrStartFallbackAfterExpiry() throws Exception {
        var count = new AtomicInteger();
        var server = server(exchange -> {
            count.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            try {
                for (byte value : COMPLETE.getBytes(StandardCharsets.UTF_8)) {
                    exchange.getResponseBody().write(value);
                    exchange.getResponseBody().flush();
                    pause(50);
                }
            } finally { exchange.close(); }
        });
        long start = System.nanoTime();
        try (var budget = AiExecutionBudget.open(Duration.ofMillis(500), 6)) {
            assertThat(client(server).generateResponse(List.of()).getResponseStatus()).isEqualTo("PARTIAL");
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
            assertThat(count.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }

    @Test
    void fallbackUsesOnlyTimeRemainingAfterPrimaryFailure() throws Exception {
        var count = new AtomicInteger();
        var server = server(exchange -> {
            if (count.incrementAndGet() == 1) {
                pause(200);
                respond(exchange, 503, "{}");
            } else {
                pause(1500);
                respond(exchange, 200, COMPLETE);
            }
        });
        long start = System.nanoTime();
        try (var budget = AiExecutionBudget.open(Duration.ofMillis(800), 6)) {
            assertThat(client(server).generateResponse(List.of()).getResponseStatus()).isEqualTo("PARTIAL");
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1400));
            assertThat(count.get()).isEqualTo(2);
        } finally { server.stop(0); }
    }

    @Test
    void responseAfterOldFifteenSecondTimeoutIsAccepted() throws Exception {
        var count = new AtomicInteger();
        var server = server(exchange -> {
            count.incrementAndGet(); pause(16100); respond(exchange, 200, COMPLETE);
        });
        try (var budget = AiExecutionBudget.open(Duration.ofSeconds(20), 6)) {
            assertThat(client(server).generateResponse(List.of()).getContent()).isEqualTo("OK");
            assertThat(count.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }

    @Test
    void lengthLimitedResponseIsPartialAndCannotExecuteTruncatedToolCalls() throws Exception {
        var server = server(exchange -> respond(exchange, 200,
                "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"unfinished\",\"tool_calls\":[{\"id\":\"1\",\"type\":\"function\",\"function\":{\"name\":\"book\",\"arguments\":\"{\"}}]}}]}"));
        try {
            var response = client(server).generateResponse(List.of());
            assertThat(response.getResponseStatus()).isEqualTo("PARTIAL");
            assertThat(response.getToolCalls()).isNullOrEmpty();
        } finally { server.stop(0); }
    }

    @Test
    void moderationStopsAtSharedDeadlineAndFailsClosed() throws Exception {
        var count = new AtomicInteger();
        var server = server(exchange -> {
            count.incrementAndGet(); pause(1500);
            respond(exchange, 200, COMPLETE.replace("OK", "BENIGN"));
        });
        var moderation = new CliProxyModerationClient(RestClient.builder(), properties(server),
                new HttpClientProperties(Duration.ofSeconds(2), Duration.ofSeconds(10)), new ObjectMapper());
        long start = System.nanoTime();
        try (var budget = AiExecutionBudget.open(Duration.ofMillis(500), 6)) {
            assertThat(moderation.isSafe("Xin chào")).isFalse();
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
            assertThat(count.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }

    private CliProxyLlmClient client(HttpServer server) {
        return new CliProxyLlmClient(RestClient.builder(), null, properties(server));
    }

    private CliProxyProperties properties(HttpServer server) {
        return new CliProxyProperties(true, "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-key", "gemini-3.5-flash-lite", "gpt-oss-120b-medium");
    }

    private HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes(); handler.handle(exchange);
        });
        server.start();
        return server;
    }

    private static void respond(HttpExchange exchange, int status, String content) throws IOException {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body); exchange.close();
    }

    private static void pause(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
    }
}
