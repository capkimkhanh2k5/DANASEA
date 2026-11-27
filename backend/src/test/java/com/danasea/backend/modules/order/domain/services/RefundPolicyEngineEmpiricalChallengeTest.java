package com.danasea.backend.modules.order.domain.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.danasea.backend.modules.order.application.usecases.GetCancellationPreviewUseCase;
import com.danasea.backend.modules.order.domain.models.RefundEvaluationResult;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.infrastructure.adapters.ServiceSlotDepartureLookupAdapter;
import com.danasea.backend.modules.order.infrastructure.persistence.adapters.MasterOrderRepositoryAdapter;
import com.danasea.backend.modules.order.infrastructure.persistence.adapters.SubOrderRepositoryAdapter;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.mappers.MasterOrderMapper;
import com.danasea.backend.modules.order.infrastructure.persistence.mappers.SubOrderMapper;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.order.presentation.controllers.OrderController;
import com.danasea.backend.modules.order.presentation.dtos.CancellationPreviewResponse;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Arrays;
import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
@DisplayName("Empirical Challenge & Stress Test Suite: RefundPolicyEngine & OrderController")
public class RefundPolicyEngineEmpiricalChallengeTest {

    private RefundPolicyEngine engine;
    private LocalDateTime baseDeparture;

    @Mock
    private JpaMasterOrderRepository masterOrderRepository;

    @Mock
    private JpaSubOrderRepository subOrderRepository;

    @Mock
    private JpaServiceSlotRepository serviceSlotRepository;

    private final RefundPolicyEngine spyRefundPolicyEngine = new RefundPolicyEngine();
    private OrderController orderController;

    @BeforeEach
    void setUp() {
        engine = new RefundPolicyEngine();
        baseDeparture = LocalDateTime.of(2026, 10, 15, 12, 0, 0);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockSecurityUser(UUID userId, String... roles) {
        var authorities = Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        var auth = new UsernamePasswordAuthenticationToken(userId.toString(), "credentials", authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    // =========================================================================
    // 1. TIME BOUNDARIES CHALLENGE
    // =========================================================================
    @Nested
    @DisplayName("1. Time Boundaries Empirical Stress Tests")
    class TimeBoundariesChallenge {

        private final BigDecimal sampleAmount = new BigDecimal("1000000.00");

        @Test
        @DisplayName("Boundary: 48h01m before departure -> 100.0%")
        void challenge_48h01m_Yields100Percent() {
            LocalDateTime cancelTime = baseDeparture.minusHours(48).minusMinutes(1);
            RefundEvaluationResult res = engine.evaluate(RefundReason.CUSTOMER_REQUEST, baseDeparture, cancelTime, sampleAmount, null, RefundPolicyEngine.LEGACY_POLICY);

            assertEquals(BigDecimal.valueOf(100.0), res.refundPercentage());
            assertEquals(1, res.refundPercentage().scale());
            assertEquals(new BigDecimal("1000000.00"), res.refundAmount());
        }

        @Test
        @DisplayName("Boundary: 48h00m before departure -> Exactly 70.0% (Falls into [24h, 48h])")
        void challenge_48h00m_YieldsExactly70Percent() {
            LocalDateTime cancelTime = baseDeparture.minusHours(48);
            RefundEvaluationResult res = engine.evaluate(RefundReason.CUSTOMER_REQUEST, baseDeparture, cancelTime, sampleAmount, null, RefundPolicyEngine.LEGACY_POLICY);

            assertEquals(BigDecimal.valueOf(70.0), res.refundPercentage());
            assertEquals(1, res.refundPercentage().scale());
            assertEquals(new BigDecimal("700000.00"), res.refundAmount());
        }

        @Test
        @DisplayName("Boundary: 47h59m before departure -> 70.0%")
        void challenge_47h59m_Yields70Percent() {
            LocalDateTime cancelTime = baseDeparture.minusHours(47).minusMinutes(59);
            RefundEvaluationResult res = engine.evaluate(RefundReason.CUSTOMER_REQUEST, baseDeparture, cancelTime, sampleAmount, null, RefundPolicyEngine.LEGACY_POLICY);

            assertEquals(BigDecimal.valueOf(70.0), res.refundPercentage());
            assertEquals(new BigDecimal("700000.00"), res.refundAmount());
        }

        @Test
        @DisplayName("Boundary: 24h01m before departure -> 70.0%")
        void challenge_24h01m_Yields70Percent() {
            LocalDateTime cancelTime = baseDeparture.minusHours(24).minusMinutes(1);
            RefundEvaluationResult res = engine.evaluate(RefundReason.CUSTOMER_REQUEST, baseDeparture, cancelTime, sampleAmount, null, RefundPolicyEngine.LEGACY_POLICY);

            assertEquals(BigDecimal.valueOf(70.0), res.refundPercentage());
            assertEquals(new BigDecimal("700000.00"), res.refundAmount());
        }

        @Test
        @DisplayName("Boundary: 24h00m before departure -> Exactly 70.0% (Falls into [24h, 48h])")
        void challenge_24h00m_YieldsExactly70Percent() {
            LocalDateTime cancelTime = baseDeparture.minusHours(24);
            RefundEvaluationResult res = engine.evaluate(RefundReason.CUSTOMER_REQUEST, baseDeparture, cancelTime, sampleAmount, null, RefundPolicyEngine.LEGACY_POLICY);

            assertEquals(BigDecimal.valueOf(70.0), res.refundPercentage());
            assertEquals(new BigDecimal("700000.00"), res.refundAmount());
        }

        @Test
        @DisplayName("Boundary: 23h59m before departure -> 30.0%")
        void challenge_23h59m_Yields30Percent() {
            LocalDateTime cancelTime = baseDeparture.minusHours(23).minusMinutes(59);
            RefundEvaluationResult res = engine.evaluate(RefundReason.CUSTOMER_REQUEST, baseDeparture, cancelTime, sampleAmount, null, RefundPolicyEngine.LEGACY_POLICY);

            assertEquals(BigDecimal.valueOf(30.0), res.refundPercentage());
            assertEquals(new BigDecimal("300000.00"), res.refundAmount());
        }

        @Test
        @DisplayName("Boundary: 2h01m before departure -> 30.0%")
        void challenge_2h01m_Yields30Percent() {
            LocalDateTime cancelTime = baseDeparture.minusHours(2).minusMinutes(1);
            RefundEvaluationResult res = engine.evaluate(RefundReason.CUSTOMER_REQUEST, baseDeparture, cancelTime, sampleAmount, null, RefundPolicyEngine.LEGACY_POLICY);

            assertEquals(BigDecimal.valueOf(30.0), res.refundPercentage());
            assertEquals(new BigDecimal("300000.00"), res.refundAmount());
        }

        @Test
        @DisplayName("Boundary: 2h00m before departure -> Exactly 30.0% (Falls into [2h, 24h])")
        void challenge_2h00m_YieldsExactly30Percent() {
            LocalDateTime cancelTime = baseDeparture.minusHours(2);
            RefundEvaluationResult res = engine.evaluate(RefundReason.CUSTOMER_REQUEST, baseDeparture, cancelTime, sampleAmount, null, RefundPolicyEngine.LEGACY_POLICY);

            assertEquals(BigDecimal.valueOf(30.0), res.refundPercentage());
            assertEquals(new BigDecimal("300000.00"), res.refundAmount());
        }

        @Test
        @DisplayName("Boundary: 1h59m before departure -> 0.0%")
        void challenge_1h59m_Yields0Percent() {
            LocalDateTime cancelTime = baseDeparture.minusHours(1).minusMinutes(59);
            RefundEvaluationResult res = engine.evaluate(RefundReason.CUSTOMER_REQUEST, baseDeparture, cancelTime, sampleAmount, null, RefundPolicyEngine.LEGACY_POLICY);

            assertEquals(BigDecimal.valueOf(0.0), res.refundPercentage());
            assertEquals(new BigDecimal("0.00"), res.refundAmount());
        }

        @Test
        @DisplayName("Boundary: 1m before departure -> 0.0%")
        void challenge_1m_Yields0Percent() {
            LocalDateTime cancelTime = baseDeparture.minusMinutes(1);
            RefundEvaluationResult res = engine.evaluate(RefundReason.CUSTOMER_REQUEST, baseDeparture, cancelTime, sampleAmount, null, RefundPolicyEngine.LEGACY_POLICY);

            assertEquals(BigDecimal.valueOf(0.0), res.refundPercentage());
            assertEquals(new BigDecimal("0.00"), res.refundAmount());
        }

        @Test
        @DisplayName("Boundary: 0m (exactly at departure time) -> 0.0%")
        void challenge_0m_Yields0Percent() {
            LocalDateTime cancelTime = baseDeparture;
            RefundEvaluationResult res = engine.evaluate(RefundReason.CUSTOMER_REQUEST, baseDeparture, cancelTime, sampleAmount, null, RefundPolicyEngine.LEGACY_POLICY);

            assertEquals(BigDecimal.valueOf(0.0), res.refundPercentage());
            assertEquals(new BigDecimal("0.00"), res.refundAmount());
        }

        @Test
        @DisplayName("Boundary: Past departure time (+1s, +1m, +1h) -> 0.0%")
        void challenge_PastDeparture_Yields0Percent() {
            LocalDateTime cancelTimeSec = baseDeparture.plusSeconds(1);
            RefundEvaluationResult resSec = engine.evaluate(RefundReason.CUSTOMER_REQUEST, baseDeparture, cancelTimeSec, sampleAmount, null, RefundPolicyEngine.LEGACY_POLICY);
            assertEquals(BigDecimal.valueOf(0.0), resSec.refundPercentage());
            assertEquals(new BigDecimal("0.00"), resSec.refundAmount());

            LocalDateTime cancelTimeMin = baseDeparture.plusMinutes(1);
            RefundEvaluationResult resMin = engine.evaluate(RefundReason.CUSTOMER_REQUEST, baseDeparture, cancelTimeMin, sampleAmount, null, RefundPolicyEngine.LEGACY_POLICY);
            assertEquals(BigDecimal.valueOf(0.0), resMin.refundPercentage());

            LocalDateTime cancelTimeHour = baseDeparture.plusHours(1);
            RefundEvaluationResult resHour = engine.evaluate(RefundReason.CUSTOMER_REQUEST, baseDeparture, cancelTimeHour, sampleAmount, null, RefundPolicyEngine.LEGACY_POLICY);
            assertEquals(BigDecimal.valueOf(0.0), resHour.refundPercentage());
        }

        @Test
        @DisplayName("Sub-minute resolution: 48h 00m 30s before departure -> Evaluated at 2880 min -> 70.0%")
        void challenge_48h00m30s_DurationTruncatesToMinutes() {
            LocalDateTime cancelTime = baseDeparture.minusHours(48).minusSeconds(30);
            RefundEvaluationResult res = engine.evaluate(RefundReason.CUSTOMER_REQUEST, baseDeparture, cancelTime, sampleAmount, null, RefundPolicyEngine.LEGACY_POLICY);

            // Duration.between().toMinutes() truncates to 2880 minutes, thus strictly not > 2880
            assertEquals(BigDecimal.valueOf(70.0), res.refundPercentage());
        }
    }

    // =========================================================================
    // 2. FORCE MAJEURE & VENDOR FAULT CHALLENGE
    // =========================================================================
    @Nested
    @DisplayName("2. Force Majeure & Vendor Fault Empirical Stress Tests")
    class ForceMajeureChallenge {

        private final BigDecimal sampleAmount = new BigDecimal("5000000.00");

        @Test
        @DisplayName("WEATHER: 1 minute before departure -> 100.0%")
        void challenge_Weather_1mBeforeDeparture_Yields100Percent() {
            LocalDateTime cancelTime = baseDeparture.minusMinutes(1);
            RefundEvaluationResult res = engine.evaluate(RefundReason.WEATHER, baseDeparture, cancelTime, sampleAmount, null, RefundPolicyEngine.LEGACY_POLICY);

            assertEquals(BigDecimal.valueOf(100.0), res.refundPercentage());
            assertEquals(sampleAmount, res.refundAmount());
        }

        @Test
        @DisplayName("WEATHER: Exactly at departure time (0m) -> 100.0%")
        void challenge_Weather_ExactDeparture_Yields100Percent() {
            RefundEvaluationResult res = engine.evaluate(RefundReason.WEATHER, baseDeparture, baseDeparture, sampleAmount, null, RefundPolicyEngine.LEGACY_POLICY);

            assertEquals(BigDecimal.valueOf(100.0), res.refundPercentage());
            assertEquals(sampleAmount, res.refundAmount());
        }

        @Test
        @DisplayName("WEATHER: 1 minute AFTER departure (+1m) -> 100.0%")
        void challenge_Weather_1mAfterDeparture_Yields100Percent() {
            LocalDateTime cancelTime = baseDeparture.plusMinutes(1);
            RefundEvaluationResult res = engine.evaluate(RefundReason.WEATHER, baseDeparture, cancelTime, sampleAmount, null, RefundPolicyEngine.LEGACY_POLICY);

            assertEquals(BigDecimal.valueOf(100.0), res.refundPercentage());
            assertEquals(sampleAmount, res.refundAmount());
        }

        @Test
        @DisplayName("WEATHER: 24 hours AFTER departure -> 100.0%")
        void challenge_Weather_24hAfterDeparture_Yields100Percent() {
            LocalDateTime cancelTime = baseDeparture.plusHours(24);
            RefundEvaluationResult res = engine.evaluate(RefundReason.WEATHER, baseDeparture, cancelTime, sampleAmount, null, RefundPolicyEngine.LEGACY_POLICY);

            assertEquals(BigDecimal.valueOf(100.0), res.refundPercentage());
            assertEquals(sampleAmount, res.refundAmount());
        }

        @Test
        @DisplayName("WEATHER: Null timestamps -> 100.0%")
        void challenge_Weather_NullTimestamps_Yields100Percent() {
            RefundEvaluationResult res = engine.evaluate(RefundReason.WEATHER, null, null, sampleAmount, null, RefundPolicyEngine.LEGACY_POLICY);

            assertEquals(BigDecimal.valueOf(100.0), res.refundPercentage());
            assertEquals(sampleAmount, res.refundAmount());
        }

        @Test
        @DisplayName("VENDOR_FAULT: 1 minute before departure -> 100.0%")
        void challenge_VendorFault_1mBeforeDeparture_Yields100Percent() {
            LocalDateTime cancelTime = baseDeparture.minusMinutes(1);
            RefundEvaluationResult res = engine.evaluate(RefundReason.VENDOR_FAULT, baseDeparture, cancelTime, sampleAmount, null, RefundPolicyEngine.LEGACY_POLICY);

            assertEquals(BigDecimal.valueOf(100.0), res.refundPercentage());
            assertEquals(sampleAmount, res.refundAmount());
        }

        @Test
        @DisplayName("VENDOR_FAULT: Exactly at departure time (0m) -> 100.0%")
        void challenge_VendorFault_ExactDeparture_Yields100Percent() {
            RefundEvaluationResult res = engine.evaluate(RefundReason.VENDOR_FAULT, baseDeparture, baseDeparture, sampleAmount, null, RefundPolicyEngine.LEGACY_POLICY);

            assertEquals(BigDecimal.valueOf(100.0), res.refundPercentage());
            assertEquals(sampleAmount, res.refundAmount());
        }

        @Test
        @DisplayName("VENDOR_FAULT: 1 minute AFTER departure (+1m) -> 100.0%")
        void challenge_VendorFault_1mAfterDeparture_Yields100Percent() {
            LocalDateTime cancelTime = baseDeparture.plusMinutes(1);
            RefundEvaluationResult res = engine.evaluate(RefundReason.VENDOR_FAULT, baseDeparture, cancelTime, sampleAmount, null, RefundPolicyEngine.LEGACY_POLICY);

            assertEquals(BigDecimal.valueOf(100.0), res.refundPercentage());
            assertEquals(sampleAmount, res.refundAmount());
        }

        @Test
        @DisplayName("VENDOR_FAULT: 24 hours AFTER departure -> 100.0%")
        void challenge_VendorFault_24hAfterDeparture_Yields100Percent() {
            LocalDateTime cancelTime = baseDeparture.plusHours(24);
            RefundEvaluationResult res = engine.evaluate(RefundReason.VENDOR_FAULT, baseDeparture, cancelTime, sampleAmount, null, RefundPolicyEngine.LEGACY_POLICY);

            assertEquals(BigDecimal.valueOf(100.0), res.refundPercentage());
            assertEquals(sampleAmount, res.refundAmount());
        }

        @Test
        @DisplayName("VENDOR_FAULT: Null timestamps -> 100.0%")
        void challenge_VendorFault_NullTimestamps_Yields100Percent() {
            RefundEvaluationResult res = engine.evaluate(RefundReason.VENDOR_FAULT, null, null, sampleAmount, null, RefundPolicyEngine.LEGACY_POLICY);

            assertEquals(BigDecimal.valueOf(100.0), res.refundPercentage());
            assertEquals(sampleAmount, res.refundAmount());
        }
    }

    // =========================================================================
    // 3. NUMERIC EXTREMES & FRACTIONAL DIVISION CHALLENGE
    // =========================================================================
    @Nested
    @DisplayName("3. Numeric Extremes & Fractional Division Empirical Stress Tests")
    class NumericExtremesChallenge {

        @Test
        @DisplayName("Large amount: 1,000,000,000 VND (1 Billion VND)")
        void challenge_LargeAmount_1BillionVND() {
            BigDecimal oneBillion = new BigDecimal("1000000000.00");

            // 100%
            RefundEvaluationResult res100 = engine.evaluate(
                    RefundReason.CUSTOMER_REQUEST, baseDeparture, baseDeparture.minusHours(50), oneBillion, null, RefundPolicyEngine.LEGACY_POLICY);
            assertEquals(new BigDecimal("1000000000.00"), res100.refundAmount());

            // 70%
            RefundEvaluationResult res70 = engine.evaluate(
                    RefundReason.CUSTOMER_REQUEST, baseDeparture, baseDeparture.minusHours(30), oneBillion, null, RefundPolicyEngine.LEGACY_POLICY);
            assertEquals(new BigDecimal("700000000.00"), res70.refundAmount());

            // 30%
            RefundEvaluationResult res30 = engine.evaluate(
                    RefundReason.CUSTOMER_REQUEST, baseDeparture, baseDeparture.minusHours(10), oneBillion, null, RefundPolicyEngine.LEGACY_POLICY);
            assertEquals(new BigDecimal("300000000.00"), res30.refundAmount());

            // 0%
            RefundEvaluationResult res0 = engine.evaluate(
                    RefundReason.CUSTOMER_REQUEST, baseDeparture, baseDeparture.minusMinutes(30), oneBillion, null, RefundPolicyEngine.LEGACY_POLICY);
            assertEquals(new BigDecimal("0.00"), res0.refundAmount());
        }

        @Test
        @DisplayName("Very large amount: 100,000,000,000 VND (100 Billion VND)")
        void challenge_VeryLargeAmount_100BillionVND() {
            BigDecimal hundredBillion = new BigDecimal("100000000000.00");

            RefundEvaluationResult res70 = engine.evaluate(
                    RefundReason.CUSTOMER_REQUEST, baseDeparture, baseDeparture.minusHours(30), hundredBillion, null, RefundPolicyEngine.LEGACY_POLICY);
            assertEquals(new BigDecimal("70000000000.00"), res70.refundAmount());
        }

        @Test
        @DisplayName("Zero amounts: BigDecimal.ZERO (scale 0) and 0.00 (scale 2)")
        void challenge_ZeroAmounts() {
            RefundEvaluationResult resZeroScale0 = engine.evaluate(
                    RefundReason.CUSTOMER_REQUEST, baseDeparture, baseDeparture.minusHours(50), BigDecimal.ZERO, null, RefundPolicyEngine.LEGACY_POLICY);
            assertEquals(0, BigDecimal.ZERO.compareTo(resZeroScale0.refundAmount()));

            RefundEvaluationResult resZeroScale2 = engine.evaluate(
                    RefundReason.CUSTOMER_REQUEST, baseDeparture, baseDeparture.minusHours(50), new BigDecimal("0.00"), null, RefundPolicyEngine.LEGACY_POLICY);
            assertEquals(0, BigDecimal.ZERO.compareTo(resZeroScale2.refundAmount()));
        }

        @Test
        @DisplayName("Negative amounts: -500,000 VND returns zero refund amount safely")
        void challenge_NegativeAmounts() {
            RefundEvaluationResult res = engine.evaluate(
                    RefundReason.CUSTOMER_REQUEST, baseDeparture, baseDeparture.minusHours(50), new BigDecimal("-500000.00"), null, RefundPolicyEngine.LEGACY_POLICY);
            assertEquals(0, BigDecimal.ZERO.compareTo(res.refundAmount()));
        }

        @Test
        @DisplayName("Null amount: returns 0.00 refund amount safely without NPE")
        void challenge_NullAmount() {
            RefundEvaluationResult res = engine.evaluate(
                    RefundReason.CUSTOMER_REQUEST, baseDeparture, baseDeparture.minusHours(50), null, null, RefundPolicyEngine.LEGACY_POLICY);
            assertNotNull(res);
            assertEquals(BigDecimal.valueOf(100.0), res.refundPercentage());
            assertEquals(new BigDecimal("0.00"), res.refundAmount());
        }

        @Test
        @DisplayName("Odd amount: 70% of 999,999 VND -> 699,999.30 VND (scale 2 with HALF_UP)")
        void challenge_OddAmount_70PercentOf999999() {
            BigDecimal oddAmount = new BigDecimal("999999.00");
            RefundEvaluationResult res = engine.evaluate(
                    RefundReason.CUSTOMER_REQUEST, baseDeparture, baseDeparture.minusHours(30), oddAmount, null, RefundPolicyEngine.LEGACY_POLICY);

            assertEquals(BigDecimal.valueOf(70.0), res.refundPercentage());
            // 999999 * 0.70 = 699999.30
            assertEquals(new BigDecimal("699999.30"), res.refundAmount());
            assertEquals(2, res.refundAmount().scale());
        }

        @Test
        @DisplayName("Odd amount: 30% of 999,999 VND -> 299,999.70 VND (scale 2 with HALF_UP)")
        void challenge_OddAmount_30PercentOf999999() {
            BigDecimal oddAmount = new BigDecimal("999999.00");
            RefundEvaluationResult res = engine.evaluate(
                    RefundReason.CUSTOMER_REQUEST, baseDeparture, baseDeparture.minusHours(10), oddAmount, null, RefundPolicyEngine.LEGACY_POLICY);

            assertEquals(BigDecimal.valueOf(30.0), res.refundPercentage());
            // 999999 * 0.30 = 299999.70
            assertEquals(new BigDecimal("299999.70"), res.refundAmount());
        }

        @Test
        @DisplayName("Odd amount: 100% of 999,999 (scale 0) preserves scale 0 for integer VND")
        void challenge_OddAmount_100PercentOfIntegerVND() {
            BigDecimal oddInteger = new BigDecimal("999999");
            RefundEvaluationResult res = engine.evaluate(
                    RefundReason.WEATHER, baseDeparture, baseDeparture, oddInteger, null, RefundPolicyEngine.LEGACY_POLICY);

            assertEquals(BigDecimal.valueOf(100.0), res.refundPercentage());
            assertEquals(new BigDecimal("999999"), res.refundAmount());
            assertEquals(0, res.refundAmount().scale());
        }

        @Test
        @DisplayName("Smallest unit: 1 VND at 70% and 30%")
        void challenge_SmallestUnit_1VND() {
            BigDecimal oneVnd = new BigDecimal("1.00");

            // 1.00 * 70 / 100 = 0.70
            RefundEvaluationResult res70 = engine.evaluate(
                    RefundReason.CUSTOMER_REQUEST, baseDeparture, baseDeparture.minusHours(30), oneVnd, null, RefundPolicyEngine.LEGACY_POLICY);
            assertEquals(new BigDecimal("0.70"), res70.refundAmount());

            // 1.00 * 30 / 100 = 0.30
            RefundEvaluationResult res30 = engine.evaluate(
                    RefundReason.CUSTOMER_REQUEST, baseDeparture, baseDeparture.minusHours(10), oneVnd, null, RefundPolicyEngine.LEGACY_POLICY);
            assertEquals(new BigDecimal("0.30"), res30.refundAmount());
        }

        @Test
        @DisplayName("Fractional cents: 999,999.99 VND at 70% and 30% with HALF_UP rounding")
        void challenge_FractionalCents_999999_99() {
            BigDecimal fractionalAmount = new BigDecimal("999999.99");

            // 999,999.99 * 0.70 = 699,999.993 -> 699,999.99
            RefundEvaluationResult res70 = engine.evaluate(
                    RefundReason.CUSTOMER_REQUEST, baseDeparture, baseDeparture.minusHours(30), fractionalAmount, null, RefundPolicyEngine.LEGACY_POLICY);
            assertEquals(new BigDecimal("699999.99"), res70.refundAmount());

            // 999,999.99 * 0.30 = 299,999.997 -> 300,000.00
            RefundEvaluationResult res30 = engine.evaluate(
                    RefundReason.CUSTOMER_REQUEST, baseDeparture, baseDeparture.minusHours(10), fractionalAmount, null, RefundPolicyEngine.LEGACY_POLICY);
            assertEquals(new BigDecimal("300000.00"), res30.refundAmount());
        }
    }

    // =========================================================================
    // 4. ORDER CONTROLLER CANCELLATION PREVIEW STRESS TESTS
    // =========================================================================
    @Nested
    @DisplayName("4. OrderController Cancellation Preview Empirical Stress Tests")
    class OrderControllerStressTests {

        private UUID customerId;
        private UUID masterOrderId;

        @BeforeEach
        void init() {
            Mockito.reset(masterOrderRepository, subOrderRepository, serviceSlotRepository);
            customerId = UUID.randomUUID();
            masterOrderId = UUID.randomUUID();

            MasterOrderMapper masterOrderMapper = new MasterOrderMapper();
            SubOrderMapper subOrderMapper = new SubOrderMapper();
            MasterOrderRepositoryAdapter masterOrderAdapter =
                    new MasterOrderRepositoryAdapter(masterOrderRepository, masterOrderMapper);
            SubOrderRepositoryAdapter subOrderAdapter =
                    new SubOrderRepositoryAdapter(subOrderRepository, subOrderMapper);
            ServiceSlotDepartureLookupAdapter slotAdapter =
                    new ServiceSlotDepartureLookupAdapter(serviceSlotRepository);
            GetCancellationPreviewUseCase previewUseCase =
                    new GetCancellationPreviewUseCase(
                            masterOrderAdapter,
                            subOrderAdapter,
                            slotAdapter,
                            spyRefundPolicyEngine
                    );
            orderController = new OrderController(null, null, null, null, previewUseCase);
        }

        @Test
        @DisplayName("Multi-item mixed cancellation tiers: correct total refund, overall %, and mixed policy string")
        void challenge_MultiItem_MixedTiers_AggregatesCorrectly() {
            mockSecurityUser(customerId, "ROLE_CUSTOMER");
            LocalDateTime nowInVietnam = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));

            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(masterOrderId);
            masterOrder.setCustomerId(customerId);
            masterOrder.setTotalAmount(new BigDecimal("4000000.00"));

            // SubOrder 1: > 48h -> 100% of 1,000,000 = 1,000,000
            UUID slotId1 = UUID.randomUUID();
            SubOrderJpaEntity sub1 = new SubOrderJpaEntity();
            sub1.setId(UUID.randomUUID());
            sub1.setMasterOrderId(masterOrderId);
            sub1.setSlotId(slotId1);
            sub1.setSubtotalAmount(new BigDecimal("1000000.00"));

            ServiceSlotJpaEntity slot1 = new ServiceSlotJpaEntity();
            slot1.setId(slotId1);
            slot1.setDate(LocalDate.now().plusDays(4)); // > 48h
            slot1.setStartTime(LocalTime.of(10, 0));

            // SubOrder 2: 24h-48h -> 70% of 1,000,000 = 700,000
            UUID slotId2 = UUID.randomUUID();
            SubOrderJpaEntity sub2 = new SubOrderJpaEntity();
            sub2.setId(UUID.randomUUID());
            sub2.setMasterOrderId(masterOrderId);
            sub2.setSlotId(slotId2);
            sub2.setSubtotalAmount(new BigDecimal("1000000.00"));

            ServiceSlotJpaEntity slot2 = new ServiceSlotJpaEntity();
            slot2.setId(slotId2);
            // Tomorrow at current time + 5h -> around 29 hours away (in [24h, 48h])
            LocalDateTime departure2 = nowInVietnam.plusHours(30);
            slot2.setDate(departure2.toLocalDate());
            slot2.setStartTime(departure2.toLocalTime());

            // SubOrder 3: 2h-24h -> 30% of 1,000,000 = 300,000
            UUID slotId3 = UUID.randomUUID();
            SubOrderJpaEntity sub3 = new SubOrderJpaEntity();
            sub3.setId(UUID.randomUUID());
            sub3.setMasterOrderId(masterOrderId);
            sub3.setSlotId(slotId3);
            sub3.setSubtotalAmount(new BigDecimal("1000000.00"));

            ServiceSlotJpaEntity slot3 = new ServiceSlotJpaEntity();
            slot3.setId(slotId3);
            LocalDateTime departure3 = nowInVietnam.plusHours(5); // 5h away (in [2h, 24h])
            slot3.setDate(departure3.toLocalDate());
            slot3.setStartTime(departure3.toLocalTime());

            // SubOrder 4: < 2h -> 0% of 1,000,000 = 0
            UUID slotId4 = UUID.randomUUID();
            SubOrderJpaEntity sub4 = new SubOrderJpaEntity();
            sub4.setId(UUID.randomUUID());
            sub4.setMasterOrderId(masterOrderId);
            sub4.setSlotId(slotId4);
            sub4.setSubtotalAmount(new BigDecimal("1000000.00"));

            ServiceSlotJpaEntity slot4 = new ServiceSlotJpaEntity();
            slot4.setId(slotId4);
            LocalDateTime departure4 = nowInVietnam.plusMinutes(30); // 30m away (< 2h)
            slot4.setDate(departure4.toLocalDate());
            slot4.setStartTime(departure4.toLocalTime());

            when(masterOrderRepository.findById(masterOrderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of(sub1, sub2, sub3, sub4));
            when(serviceSlotRepository.findById(slotId1)).thenReturn(Optional.of(slot1));
            when(serviceSlotRepository.findById(slotId2)).thenReturn(Optional.of(slot2));
            when(serviceSlotRepository.findById(slotId3)).thenReturn(Optional.of(slot3));
            when(serviceSlotRepository.findById(slotId4)).thenReturn(Optional.of(slot4));

            ResponseEntity<CancellationPreviewResponse> response = orderController.getCancellationPreview(masterOrderId);

            assertNotNull(response);
            assertEquals(200, response.getStatusCode().value());
            CancellationPreviewResponse body = response.getBody();
            assertNotNull(body);

            assertEquals(new BigDecimal("4000000.00"), body.originalAmount());
            // Expected refund: 1,000,000 + 700,000 + 300,000 + 0 = 2,000,000.00
            assertEquals(new BigDecimal("2000000.00"), body.refundAmount());
            // Expected overall %: 2,000,000 / 4,000,000 * 100 = 50.0%
            assertEquals(new BigDecimal("50.0"), body.refundPercentage());
            assertTrue(body.policyApplied().contains("Mixed items"));
            assertEquals(4, body.items().size());
        }

        @Test
        @DisplayName("Order with zero suborders -> 0% refund and standard policy")
        void challenge_ZeroSubOrders_ReturnsZeroSafely() {
            mockSecurityUser(customerId, "ROLE_CUSTOMER");

            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(masterOrderId);
            masterOrder.setCustomerId(customerId);

            when(masterOrderRepository.findById(masterOrderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of());

            ResponseEntity<CancellationPreviewResponse> response = orderController.getCancellationPreview(masterOrderId);
            assertNotNull(response);
            CancellationPreviewResponse body = response.getBody();
            assertNotNull(body);
            assertEquals(BigDecimal.valueOf(0.0), body.refundPercentage());
            assertEquals(BigDecimal.ZERO, body.refundAmount());
            assertEquals("STANDARD_CANCELLATION_POLICY", body.policyApplied());
        }

        @Test
        @DisplayName("SubOrder with past departure date -> 0.0% refund")
        void challenge_PastDepartureDate_ReturnsZero() {
            mockSecurityUser(customerId, "ROLE_CUSTOMER");

            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(masterOrderId);
            masterOrder.setCustomerId(customerId);

            UUID slotId = UUID.randomUUID();
            SubOrderJpaEntity sub = new SubOrderJpaEntity();
            sub.setId(UUID.randomUUID());
            sub.setMasterOrderId(masterOrderId);
            sub.setSlotId(slotId);
            sub.setSubtotalAmount(new BigDecimal("1000000.00"));

            ServiceSlotJpaEntity pastSlot = new ServiceSlotJpaEntity();
            pastSlot.setId(slotId);
            pastSlot.setDate(LocalDate.now().minusDays(1));
            pastSlot.setStartTime(LocalTime.of(8, 0));

            when(masterOrderRepository.findById(masterOrderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of(sub));
            when(serviceSlotRepository.findById(slotId)).thenReturn(Optional.of(pastSlot));

            ResponseEntity<CancellationPreviewResponse> response = orderController.getCancellationPreview(masterOrderId);
            assertNotNull(response);
            CancellationPreviewResponse body = response.getBody();
            assertNotNull(body);
            assertEquals(BigDecimal.valueOf(0.0), body.refundPercentage());
            assertEquals(new BigDecimal("0.00"), body.refundAmount());
        }
    }
}
