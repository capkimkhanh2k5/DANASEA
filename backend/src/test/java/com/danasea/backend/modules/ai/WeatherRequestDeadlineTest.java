package com.danasea.backend.modules.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.danasea.backend.modules.ai.application.ports.AiExecutionBudget;
import com.danasea.backend.modules.weather.infrastructure.api.OpenMeteoApiClient;
import com.sun.net.httpserver.HttpServer;

class WeatherRequestDeadlineTest {
    @Test void weatherToolTransportUsesRemainingAiTimeAndDoesNotStartMarineAfterExpiry() throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/v1/", exchange -> {
            requests.incrementAndGet();
            try { Thread.sleep(1500); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            byte[] payload = "{\"latitude\":16.0,\"longitude\":108.0}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload); exchange.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        var weather = new OpenMeteoApiClient(RestClient.builder(), base, base);
        long start = System.nanoTime();
        try (var budget = AiExecutionBudget.open(Duration.ofMillis(500), 6)) {
            assertThat(weather.fetchWeather(16, 108)).isNull();
            assertThat(weather.fetchMarine(16, 108)).isNull();
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
            assertThat(requests.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }
}
