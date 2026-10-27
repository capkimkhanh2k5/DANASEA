package com.danasea.backend.modules.ai.infrastructure.cliproxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Random;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import com.danasea.backend.modules.ai.domain.services.SystemPromptBuilder;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

import com.danasea.backend.configs.properties.HttpClientProperties;
import com.danasea.backend.modules.ai.application.ports.AiExecutionBudget;
import com.danasea.backend.modules.ai.domain.models.AiMessage;
import com.danasea.backend.modules.ai.domain.models.AiMessageRole;
import com.danasea.backend.modules.ai.infrastructure.cliproxy.configs.CliProxyProperties;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.databind.ObjectMapper;

@EnabledIfSystemProperty(named = "ai.proxy.smoke", matches = "true")
class ProxyLiveSmokeTest {
    @Test void realProxyCompactionKeepsOldNonSwimmingConstraintAndAnswersCurrentQuestion() throws Exception {
        String key = Files.readString(Path.of(System.getProperty("ai.proxy.key-file"))).trim();
        String model = "gemini-3.5-flash-lite";
        var properties = new CliProxyProperties(true, System.getProperty("ai.proxy.base-url"), key, model, model,
                8192, 24000, 131072, 8192);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(120));
        var transport = RestClient.builder().requestFactory(factory).baseUrl(properties.sanitizedBaseUrl()).build();
        var counter = new CliProxyTokenCounter(transport, key);
        var window = new CliProxyContextWindow(properties, counter,
                new CliProxyHistorySummarizer(transport, key, properties, counter));
        var messages = new ArrayList<CliProxyDto.Message>();
        messages.add(new CliProxyDto.Message("system", new SystemPromptBuilder().buildBasePrompt(SupportedLanguage.EN)));
        messages.add(new CliProxyDto.Message("user", "I cannot swim. Please remember this safety constraint."));
        messages.add(new CliProxyDto.Message("assistant", "I have noted that."));
        var random = new Random(27);
        for (int i = 0; i < 10; i++) {
            var noise = new StringBuilder("Unimportant reference ");
            for (int j = 0; j < 1200; j++) noise.append(Integer.toHexString(random.nextInt()));
            messages.add(new CliProxyDto.Message("user", noise.toString()));
            messages.add(new CliProxyDto.Message("assistant", "Reference noted."));
        }
        String question = "Based on what I told you earlier, can I swim? Answer in one short sentence.";
        messages.add(new CliProxyDto.Message("user", question));
        var request = new CliProxyDto.ChatRequest(model, messages, null, "high", 8192);
        long start = System.nanoTime();
        try (var budget = AiExecutionBudget.open(Duration.ofSeconds(120), 6)) {
            var prepared = window.prepare(request);
            String serialized = new ObjectMapper().writeValueAsString(prepared.request());
            assertThat(prepared.removedMessages()).isPositive();
            assertThat(prepared.inputEstimate()).isLessThanOrEqualTo(15808);
            assertThat(serialized).contains("Earlier user quotations", "I cannot swim", question);
            var response = com.danasea.backend.shared.infrastructure.http.HttpRequestDeadline.execute(Duration.ofSeconds(120),
                    () -> transport.post().uri("/chat/completions").header("Authorization", "Bearer " + key)
                            .body(prepared.request()).retrieve().body(String.class));
            var responseTree = new ObjectMapper().readTree(response);
            assertThat(responseTree.path("choices").path(0).path("finish_reason").asText()).isEqualTo("stop");
            assertThat(responseTree.path("choices").path(0).path("message").path("content").asText().toLowerCase(Locale.ROOT))
                    .containsAnyOf("cannot", "can't", "not", "don't", "không");
            long reportedInput = responseTree.path("usage").path("prompt_tokens").asLong(-1);
            assertThat(reportedInput).isPositive().isLessThanOrEqualTo(24000);
            System.out.printf("Proxy live compaction model=%s removed=%d estimate=%d reportedInput=%d method=%s originalChars=%d compactedChars=%d seconds=%.2f%n", model,
                    prepared.removedMessages(), prepared.inputEstimate(), reportedInput, prepared.countingMethod(),
                    new ObjectMapper().writeValueAsString(request).length(), serialized.length(), (System.nanoTime() - start) / 1_000_000_000.0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"gemini-3.5-flash-lite", "gpt-oss-120b-medium"})
    void realModelAcceptsHighReasoningAndMaximumOutputCeiling(String model) throws Exception {
        String key = Files.readString(Path.of(System.getProperty("ai.proxy.key-file"))).trim();
        var properties = new CliProxyProperties(true, System.getProperty("ai.proxy.base-url"), key, model, model);
        var client = new CliProxyLlmClient(RestClient.builder(), null, properties);
        var moderation = new CliProxyModerationClient(RestClient.builder(), properties,
                new HttpClientProperties(java.time.Duration.ofSeconds(5), java.time.Duration.ofSeconds(10)),
                new ObjectMapper());
        var message = new AiMessage();
        message.setRole(AiMessageRole.USER);
        message.setContent("Which destination does DANASEA specialize in? Answer briefly.");
        long start = System.nanoTime();
        try (var budget = AiExecutionBudget.open(AiExecutionBudget.LLM_REQUEST_TIMEOUT, 6)) {
            assertThat(moderation.isSafe("Tôi muốn tìm tour chèo kayak ở Đà Nẵng.")).isTrue();
            var response = client.generateResponse(List.of(message), SupportedLanguage.EN);
            assertThat(response.getResponseStatus()).isEqualTo("ANSWERED");
            String content = response.getContent().toLowerCase(Locale.ROOT).replaceAll("\\p{Zs}", " ");
            assertThat(content).containsAnyOf("da nang", "đà nẵng", "danang");
        }
        System.out.printf("Proxy live model=%s moderation=PASS response=PASS seconds=%.2f%n",
                model, (System.nanoTime() - start) / 1_000_000_000.0);
    }
}
