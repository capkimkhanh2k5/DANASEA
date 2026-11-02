package com.danasea.backend.modules.order.application.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.danasea.backend.modules.ai.application.tools.GetPolicyTool;
import com.danasea.backend.modules.ai.infrastructure.adapters.TravelPolicyAdapter;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.services.RefundPolicyEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AiPolicyReaderTest {

    private final RefundPolicyEngine engine = new RefundPolicyEngine();
    private final AiPolicyReader reader = new AiPolicyReader(engine);

    @Test
    void advertisesExactlyTheTwoCurrentCancellationIntervalsAndWeatherOverride() {
        AiPolicyReadApi.Snapshot snapshot = reader.current();

        assertThat(snapshot.scope()).isEqualTo("COMMON_PLATFORM_POLICY");
        assertThat(snapshot.source()).isEqualTo("RefundPolicyEngine");
        assertThat(snapshot.applicability())
                .isEqualTo("USE_OWNED_ORDER_CANCELLATION_PREVIEW_FOR_ACTUAL_ELIGIBILITY_AND_AMOUNT");
        assertThat(snapshot.customerCancellation()).extracting(AiPolicyReadApi.RefundTier::condition)
                .containsExactly("MORE_THAN_24_HOURS", "AT_MOST_24_HOURS");
        assertThat(snapshot.customerCancellation().get(0).percentage()).isEqualByComparingTo("100");
        assertThat(snapshot.customerCancellation().get(1).percentage()).isZero();
        assertThat(snapshot.weatherOrVendorFaultPercentage()).isEqualByComparingTo("100");
    }

    @ParameterizedTest
    @CsvSource({"86400,AT_MOST_24_HOURS,0", "86401,MORE_THAN_24_HOURS,100",
            "172800,MORE_THAN_24_HOURS,100", "172801,MORE_THAN_24_HOURS,100",
            "7200,AT_MOST_24_HOURS,0", "0,AT_MOST_24_HOURS,0"})
    void advertisedIntervalMatchesRealEngineAtAndAroundThe24HourBoundary(
            long seconds, String condition, String expectedPercentage) {
        LocalDateTime cancellation = LocalDateTime.of(2030, 5, 10, 8, 0);
        BigDecimal actualPolicyPercentage = engine.evaluate(RefundReason.CUSTOMER_CANCEL,
                cancellation.plusSeconds(seconds), cancellation, new BigDecimal("900000.00")).refundPercentage();
        AiPolicyReadApi.RefundTier advertised = reader.current().customerCancellation().stream()
                .filter(tier -> condition.equals(tier.condition())).findFirst().orElseThrow();

        assertThat(actualPolicyPercentage).isEqualByComparingTo(expectedPercentage);
        assertThat(advertised.percentage()).isEqualByComparingTo(expectedPercentage);
    }

    @Test
    void realAiConsumerReturnsCurrentPlatformPolicyWithoutClaimingOrderSpecificEligibility() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        GetPolicyTool tool = new GetPolicyTool(null, mapper, engine);
        tool.setCanonicalPolicy(new TravelPolicyAdapter(reader));

        JsonNode result = mapper.readTree(tool.execute("{\"policy_type\":\"REFUND\"}"));

        assertThat(result.path("status").asText()).isEqualTo("AVAILABLE");
        assertThat(result.path("policy").path("source").asText()).isEqualTo("RefundPolicyEngine");
        assertThat(result.path("policy").path("scope").asText()).isEqualTo("COMMON_PLATFORM_POLICY");
        assertThat(result.path("policy").path("customerCancellation").size()).isEqualTo(2);
        JsonNode first = result.path("policy").path("customerCancellation").get(0);
        JsonNode second = result.path("policy").path("customerCancellation").get(1);
        assertThat(first.path("condition").asText()).isEqualTo("MORE_THAN_24_HOURS");
        assertThat(first.path("percentage").decimalValue()).isEqualByComparingTo("100");
        assertThat(second.path("condition").asText()).isEqualTo("AT_MOST_24_HOURS");
        assertThat(second.path("percentage").decimalValue()).isZero();
        assertThat(result.path("policy").path("weatherOrVendorFaultPercentage").decimalValue())
                .isEqualByComparingTo("100");
        assertThat(result.path("eligibilitySource").asText()).isEqualTo("OWNED_ORDER_CANCELLATION_PREVIEW");
        assertThat(result.path("orderSpecificEligibilityChecked").isBoolean()).isTrue();
        assertThat(result.path("orderSpecificEligibilityChecked").booleanValue()).isFalse();
        assertThat(result.path("rescheduleSupported").isBoolean()).isTrue();
        assertThat(result.path("rescheduleSupported").booleanValue()).isFalse();
        assertThat(result.path("changeRequestRequiresHumanApproval").booleanValue()).isTrue();
    }
}
