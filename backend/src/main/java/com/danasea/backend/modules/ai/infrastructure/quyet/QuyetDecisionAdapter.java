package com.danasea.backend.modules.ai.infrastructure.quyet;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.web.client.RestClientException;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.danasea.backend.modules.ai.application.ports.DecisionModelPort;
import com.danasea.backend.modules.ai.application.ports.AiExecutionBudget;
import com.danasea.backend.modules.ai.domain.models.DecisionResult.Answer;
import com.danasea.backend.modules.ai.domain.models.DecisionResult;
import com.danasea.backend.modules.ai.domain.models.DecisionTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class QuyetDecisionAdapter implements DecisionModelPort {
    private static final String MODEL = "chinhnc/Quyet-1.0-Small";
    private static final String RUBRIC_VERSION = "danasea-2026-11-18-v2";
    private static final Map<DecisionTask, Set<String>> CHOICES = Map.of(
            DecisionTask.INTENT, Set.of("SEARCH", "BOOKING", "WEATHER", "COMPARE", "ITINERARY", "SUPPORT", "REVIEW", "NEARBY", "OTHER"),
            DecisionTask.SERVICE_CATEGORY, Set.of("SUP", "KAYAK", "DIVING", "BOAT", "BEACH", "OTHER"),
            DecisionTask.REVIEW, Set.of("GUIDE", "SAFETY", "PUNCTUALITY", "VALUE", "OTHER"),
            DecisionTask.COMPLAINT, Set.of("PAYMENT", "REFUND", "SCHEDULE", "SAFETY", "QUALITY", "OTHER"));
    private final QuyetProperties properties;
    private final ObjectMapper mapper;
    private final RestClient client;
    private final AtomicLong retryAfterNanos = new AtomicLong();

    public QuyetDecisionAdapter(RestClient.Builder builder, QuyetProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        this.client = builder.requestFactory(factory).baseUrl(properties.baseUrl()).build();
    }

    @Override
    public DecisionResult decide(DecisionTask task, Map<String, Object> state) {
        if (!properties.enabled()) return DecisionResult.unavailable(task, "QUYET_DISABLED");
        if (properties.apiKey() == null || properties.apiKey().length() < 24) {
            return DecisionResult.unavailable(task, "QUYET_NOT_CONFIGURED");
        }
        if (System.nanoTime() < retryAfterNanos.get()) return DecisionResult.unavailable(task, "QUYET_COOLDOWN");
        if (!AiExecutionBudget.permitDecision(properties.connectTimeout().plus(properties.readTimeout()))) return DecisionResult.unavailable(task, "REQUEST_DECISION_BUDGET_EXHAUSTED");
        try {
            String request = mapper.writeValueAsString(Map.of("task", task.wireName(), "state", state));
            if (request.getBytes(StandardCharsets.UTF_8).length > 32768) {
                return DecisionResult.unavailable(task, "INPUT_TOO_LARGE");
            }
            String raw = client.post().uri("/v1/decisions").contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(properties.apiKey()))
                    .body(request).retrieve().body(String.class);
            JsonNode root = mapper.readTree(raw);
            if (root == null || !MODEL.equals(root.path("model").asText())
                    || !properties.revision().equals(root.path("revision").asText())
                    || !RUBRIC_VERSION.equals(root.path("rubricVersion").asText())
                    || !task.wireName().equals(root.path("task").asText())) {
                return DecisionResult.unavailable(task, "INVALID_MODEL_RESPONSE");
            }
            JsonNode nodes = root.path("answers");
            Map<String, Answer> answers = new LinkedHashMap<>();
            var fields = nodes.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                answers.put(field.getKey(), parse(task, field.getKey(), field.getValue()));
            }
            if (!answers.keySet().equals(task.answerKeys()) || root.path("warnings").size() > 0) {
                return DecisionResult.unavailable(task, "INCOMPLETE_MODEL_RESPONSE");
            }
            double latency = root.path("latencyMs").asDouble(Double.NaN);
            if (!Double.isFinite(latency) || latency < 0) return DecisionResult.unavailable(task, "INVALID_MODEL_RESPONSE");
            return new DecisionResult(true, task.wireName(), MODEL, properties.revision(), RUBRIC_VERSION,
                    latency, Map.copyOf(answers), null, true);
        } catch (Exception exception) {
            if (exception instanceof RestClientException) retryAfterNanos.set(System.nanoTime() + Duration.ofSeconds(5).toNanos());
            return DecisionResult.unavailable(task, "QUYET_UNAVAILABLE");
        }
    }

    private Answer parse(DecisionTask task, String key, JsonNode node) {
        if (node.path("truncated").asBoolean()) throw new IllegalArgumentException("Truncated model input");
        String type = node.path("type").asText();
        String expectedType = Set.of("positive", "external_payment", "spam", "abuse", "needs_review").contains(key)
                ? "noul" : Set.of("urgency", "relevance").contains(key) ? "score" : "choice";
        if (!expectedType.equals(type)) throw new IllegalArgumentException("Invalid answer type");
        Double confidence = number(node, "confidence");
        if (confidence != null && (confidence < 0 || confidence > 1)) throw new IllegalArgumentException("Invalid confidence");
        if ("noul".equals(type)) {
            Double probability = number(node, "noul");
            if (probability == null || probability < 0 || probability > 1) throw new IllegalArgumentException("Invalid probability");
            return new Answer(type, null, confidence, probability, null, Map.of());
        }
        Map<String, Double> probabilities = new LinkedHashMap<>();
        node.path("probabilities").fields().forEachRemaining(entry -> {
            double value = entry.getValue().asDouble(Double.NaN);
            if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Invalid distribution");
            probabilities.put(entry.getKey(), value);
        });
        double sum = probabilities.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(sum - 1) > 0.002) throw new IllegalArgumentException("Invalid distribution total");
        if ("choice".equals(type)) {
            String choice = node.path("choice").asText();
            if (!probabilities.keySet().equals(CHOICES.get(task)) || !probabilities.containsKey(choice)) {
                throw new IllegalArgumentException("Invalid choice labels");
            }
            return new Answer(type, choice, confidence, null, null, Map.copyOf(probabilities));
        }
        Double score = number(node, "score");
        int maximum = task == DecisionTask.COMPLAINT ? 2 : 3;
        Set<String> levels = IntStream.rangeClosed(0, maximum).mapToObj(Integer::toString).collect(Collectors.toSet());
        if (!probabilities.keySet().equals(levels)) throw new IllegalArgumentException("Invalid score levels");
        if (!"score".equals(type) || score == null || score < 0 || score > maximum) throw new IllegalArgumentException("Invalid score");
        return new Answer(type, null, confidence, null, score, Map.copyOf(probabilities));
    }

    private Double number(JsonNode node, String key) {
        if (!node.hasNonNull(key)) return null;
        double value = node.get(key).asDouble(Double.NaN);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite model value");
        return value;
    }
}
