package com.danasea.backend.modules.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.danasea.backend.modules.ai.application.ports.AiExecutionBudget;
import com.danasea.backend.modules.ai.infrastructure.quyet.AiExecutionBudgetFilter;
import com.danasea.backend.shared.i18n.LocalizedMessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AiExecutionBudgetTest {
    @Test void reviewSummaryAdmitsConfiguredLlmTransportWhileSearchKeepsShortBudget() throws Exception {
        var beans = new StaticListableBeanFactory();
        var filter = new AiExecutionBudgetFilter(beans.getBeanProvider(MeterRegistry.class),
                beans.getBeanProvider(ObjectMapper.class), beans.getBeanProvider(LocalizedMessageService.class));
        var review = new MockHttpServletRequest("GET", "/api/ai/review-summaries/00000000-0000-0000-0000-000000000001");
        filter.doFilter(review, new MockHttpServletResponse(), (request, response) ->
                assertThat(AiExecutionBudget.hasTimeFor(Duration.ofSeconds(119))).isTrue());
        var chat = new MockHttpServletRequest("POST", "/api/assistant/chat");
        filter.doFilter(chat, new MockHttpServletResponse(), (request, response) -> {
            assertThat(AiExecutionBudget.hasTimeFor(Duration.ofSeconds(119))).isTrue();
            assertThat(AiExecutionBudget.hasTimeFor(Duration.ofSeconds(121))).isFalse();
        });
        var search = new MockHttpServletRequest("POST", "/api/ai/search");
        filter.doFilter(search, new MockHttpServletResponse(), (request, response) ->
                assertThat(AiExecutionBudget.hasTimeFor(Duration.ofSeconds(20))).isFalse());
    }

    @Test void budgetBoundsModelFanoutAndDoesNotLeakAcrossRequests() {
        try (var ignored = AiExecutionBudget.open(Duration.ofSeconds(2), 2)) {
            assertThat(AiExecutionBudget.permitDecision(Duration.ZERO)).isTrue();
            assertThat(AiExecutionBudget.permitDecision(Duration.ZERO)).isTrue();
            assertThat(AiExecutionBudget.permitDecision(Duration.ZERO)).isFalse();
        }
        assertThat(AiExecutionBudget.permitDecision(Duration.ZERO)).isTrue();
    }
    @Test void insufficientTimeUsesFallbackBeforeStartingTransport() {
        try (var ignored = AiExecutionBudget.open(Duration.ofMillis(1), 6)) {
            assertThat(AiExecutionBudget.permitDecision(Duration.ofSeconds(3))).isFalse();
        }
    }
    @Test void nestedModelBudgetCannotExtendAnExistingRequestDeadline() {
        try (var request = AiExecutionBudget.open(Duration.ofMillis(500), 6)) {
            try (var model = AiExecutionBudget.open(Duration.ofMinutes(2), 6)) {
                assertThat(AiExecutionBudget.remaining(Duration.ofMinutes(2))).isLessThan(Duration.ofMillis(501));
            }
            assertThat(AiExecutionBudget.hasTimeFor(Duration.ofSeconds(1))).isFalse();
        }
    }
}
