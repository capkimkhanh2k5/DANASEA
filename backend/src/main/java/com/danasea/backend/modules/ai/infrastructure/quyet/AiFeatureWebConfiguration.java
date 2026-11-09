package com.danasea.backend.modules.ai.infrastructure.quyet;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.danasea.backend.modules.ai.application.ports.RateLimiterPort;
import com.danasea.backend.modules.ai.domain.TrustTier;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import com.danasea.backend.shared.i18n.LocalizedMessageService;
import com.danasea.backend.shared.presentation.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class AiFeatureWebConfiguration implements WebMvcConfigurer {
    private final ObjectProvider<RateLimiterPort> limiter;
    private final ObjectProvider<ObjectMapper> mapper;
    private final ObjectProvider<LocalizedMessageService> messages;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                if ("GET".equals(request.getMethod()) && !request.getRequestURI().contains("review-summaries") && !request.getRequestURI().contains("marine-briefing")) return true;
                var userId = SecurityUtils.getCurrentUserId();
                if (userId.isEmpty()) return true;
                if (limiter.getObject().isAllowed(userId.get().toString(), request.getRemoteAddr(), TrustTier.VERIFIED)) return true;
                response.setStatus(429); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                mapper.getObject().writeValue(response.getWriter(), new ErrorResponse("AI_RATE_LIMIT_EXCEEDED", messages.getObject().get("error.ai_rate_limit_exceeded")));
                return false;
            }
        }).addPathPatterns("/api/ai/**", "/api/vendor/ai/**", "/api/admin/ai/**");
    }
}
