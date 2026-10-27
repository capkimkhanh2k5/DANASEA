package com.danasea.backend.modules.ai.infrastructure.quyet;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Semaphore;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.danasea.backend.modules.ai.application.ports.AiExecutionBudget;
import com.danasea.backend.shared.i18n.LocalizedMessageService;
import com.danasea.backend.shared.presentation.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
@RequiredArgsConstructor
public class AiExecutionBudgetFilter extends OncePerRequestFilter {
    private final ObjectProvider<MeterRegistry> metrics;
    private final ObjectProvider<ObjectMapper> mapper;
    private final ObjectProvider<LocalizedMessageService> messages;
    private final Semaphore concurrentRequests = new Semaphore(8);

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/ai/") || path.equals("/api/assistant/chat") || path.matches("/api/assistant/conversations/[^/]+/confirm") || path.equals("/api/admin/ai/risk-cases"));
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (!concurrentRequests.tryAcquire()) {
            response.setStatus(429); response.setHeader("Retry-After", "2"); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.getIfAvailable(ObjectMapper::new).writeValue(response.getWriter(), new ErrorResponse("AI_RATE_LIMIT_EXCEEDED", messages.getIfAvailable(LocalizedMessageService::standalone).get("error.ai_rate_limit_exceeded")));
            return;
        }
        MeterRegistry registry = metrics.getIfAvailable();
        Timer.Sample timer = registry == null ? null : Timer.start(registry);
        String route = request.getRequestURI().equals("/api/assistant/chat") ? "assistant" : "customer_features";
        Duration timeout = "assistant".equals(route) || request.getRequestURI().startsWith("/api/ai/review-summaries/")
                ? AiExecutionBudget.LLM_REQUEST_TIMEOUT : Duration.ofSeconds(12);
        try (var budget = AiExecutionBudget.open(timeout, 6)) {
            chain.doFilter(request, response);
        } finally {
            concurrentRequests.release();
            if (timer != null) timer.stop(registry.timer("danasea.ai.request.duration", "group", route, "status", Integer.toString(response.getStatus())));
        }
    }
}
