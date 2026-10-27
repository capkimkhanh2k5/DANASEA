package com.danasea.backend.modules.ai;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.web.client.RestClient;

import com.danasea.backend.modules.ai.domain.models.DecisionTask;
import com.danasea.backend.modules.ai.infrastructure.quyet.QuyetDecisionAdapter;
import com.danasea.backend.modules.ai.infrastructure.quyet.QuyetProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

class QuyetDecisionAdapterTest {
    private static final String REVISION = "233167bba5df61b5375a522bf8a042d8d2189379";
    private static final String KEY = "test-local-key-with-at-least-24-characters";
    private HttpServer server;
    private final AtomicReference<String> response = new AtomicReference<>();
    private final AtomicReference<String> received = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private int status = 200;
    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/decisions", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start(); response.set(valid());
    }
    @AfterEach void teardown() { server.stop(0); }
    private QuyetDecisionAdapter adapter(boolean enabled, String url, String key) {
        return new QuyetDecisionAdapter(RestClient.builder(), new QuyetProperties(enabled, url, key,
                Duration.ofMillis(300), Duration.ofSeconds(3), REVISION), new ObjectMapper());
    }
    private QuyetDecisionAdapter adapter() { return adapter(true, "http://127.0.0.1:" + server.getAddress().getPort(), KEY); }
    private String valid() { return """
        {"model":"chinhnc/Quyet-1.0-Small","revision":"%s","rubricVersion":"danasea-2026-10-08-v1",
         "task":"risk","latencyMs":50,"warnings":[],"answers":{"needs_review":{"type":"noul","noul":0.71}}}
        """.formatted(REVISION); }
    @Test void postsFixedTaskWithBearerAndReturnsSuggestionMetadata() throws Exception {
        var result = adapter().decide(DecisionTask.RISK, Map.of("complaint", "Failed payments"));
        assertThat(result.available()).isTrue(); assertThat(result.probability("needs_review")).isEqualTo(.71);
        assertThat(result.suggestionOnly()).isTrue(); assertThat(authorization.get()).isEqualTo("Bearer " + KEY);
        var request = new ObjectMapper().readTree(received.get());
        assertThat(request.path("task").asText()).isEqualTo("risk"); assertThat(request.has("questions")).isFalse();
    }
    @Test void mismatchedMetadataCannotReachBusinessDecisions() {
        for (String corrupt : List.of(valid().replace(REVISION, "other"), valid().replace("danasea-2026-10-08-v1", "v2"),
                valid().replace("Quyet-1.0-Small", "Other"), valid().replace("\"risk\"", "\"review\""))) {
            response.set(corrupt); assertThat(adapter().decide(DecisionTask.RISK, Map.of()).available()).isFalse();
        }
    }
    @Test void missingWrongTypeOutOfRangeAndTruncatedAnswersAreUnavailable() {
        for (String corrupt : List.of(valid().replace("needs_review", "other"), valid().replace("\"noul\"", "\"choice\""),
                valid().replace("0.71", "1.5"), valid().replace("\"noul\":0.71", "\"noul\":0.71,\"truncated\":true"))) {
            response.set(corrupt); assertThat(adapter().decide(DecisionTask.RISK, Map.of()).available()).isFalse();
        }
    }
    @Test void busyServiceReturnsUnavailable() {
        status = 429;
        var client = adapter();
        assertThat(client.decide(DecisionTask.RISK, Map.of()).unavailableReason()).isEqualTo("QUYET_UNAVAILABLE");
        received.set(null);
        assertThat(client.decide(DecisionTask.RISK, Map.of()).unavailableReason()).isEqualTo("QUYET_COOLDOWN");
        assertThat(received.get()).isNull();
    }
    @Test void disabledMissingKeyAndOversizedInputDoNotCallWorker() {
        assertThat(adapter(false, "http://127.0.0.1:1", KEY).decide(DecisionTask.RISK, Map.of()).unavailableReason()).isEqualTo("QUYET_DISABLED");
        assertThat(adapter(true, "http://127.0.0.1:1", "").decide(DecisionTask.RISK, Map.of()).unavailableReason()).isEqualTo("QUYET_NOT_CONFIGURED");
        assertThat(adapter().decide(DecisionTask.RISK, Map.of("text", "a".repeat(33000))).unavailableReason()).isEqualTo("INPUT_TOO_LARGE");
        assertThat(received.get()).isNull();
    }
    @Test @EnabledIfSystemProperty(named = "ai.local.smoke", matches = "true")
    void actualPinnedLocalWorkerExecutesAllSevenTasksThroughJavaAdapter() throws Exception {
        Path defaultKey = Path.of(System.getProperty("user.home"), ".cache/danasea/quyet-small/service.key");
        String key = Files.readString(Path.of(System.getProperty("ai.local.key-file", defaultKey.toString()))).trim();
        var actual = adapter(true, System.getProperty("ai.local.base-url", "http://127.0.0.1:8091"), key);
        for (DecisionTask task : DecisionTask.values()) {
            var result = actual.decide(task, Map.of("message", "Tìm kayak cho 3 người", "content", "Tour kayak có hướng dẫn viên",
                    "review", "Hướng dẫn tốt", "complaint", "Tour đổi giờ", "context", "Du lịch Đà Nẵng", "service", "Kayak", "risk_signals", List.of("FAILED_PAYMENTS")));
            assertThat(result.available()).as(task.name()).isTrue(); assertThat(result.revision()).isEqualTo(REVISION);
            assertThat(result.suggestionOnly()).isTrue();
        }
    }
}
