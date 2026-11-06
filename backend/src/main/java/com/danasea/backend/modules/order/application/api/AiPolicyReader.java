package com.danasea.backend.modules.order.application.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.services.RefundPolicyEngine;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AiPolicyReader implements AiPolicyReadApi {
    private final RefundPolicyEngine policy;

    @Override
    public Snapshot current() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        return new Snapshot("COMMON_PLATFORM_POLICY", List.of(
                tier("MORE_THAN_24_HOURS", now, 1441), tier("AT_MOST_24_HOURS", now, 1440)),
                policy.evaluate(RefundReason.WEATHER, now.plusDays(1), now, BigDecimal.valueOf(100)).refundPercentage(),
                "RefundPolicyEngine", "USE_OWNED_ORDER_CANCELLATION_PREVIEW_FOR_ACTUAL_ELIGIBILITY_AND_AMOUNT");
    }

    private RefundTier tier(String condition, LocalDateTime now, long minutes) {
        return new RefundTier(condition, policy.evaluate(RefundReason.CUSTOMER_CANCEL, now.plusMinutes(minutes),
                now, BigDecimal.valueOf(100)).refundPercentage());
    }
}
