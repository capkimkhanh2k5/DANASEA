package com.danasea.backend.modules.settlement.domain.services;

import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.settlement.domain.exceptions.InvalidCommissionRateException;
import com.danasea.backend.modules.settlement.domain.exceptions.InvalidOrderAmountException;
import com.danasea.backend.modules.settlement.domain.exceptions.SettlementAlreadyFinalizedException;
import com.danasea.backend.modules.settlement.domain.models.LineItemExclusionReason;
import com.danasea.backend.modules.settlement.domain.models.Settlement;
import com.danasea.backend.modules.settlement.domain.models.SettlementCalculationResult;
import com.danasea.backend.modules.settlement.domain.models.SettlementLineItem;
import com.danasea.backend.modules.settlement.domain.models.SettlementStatus;
import com.danasea.backend.modules.settlement.domain.models.SubOrderCalculationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Adversarial Settlement Stress Testing - Challenger M4")
class AdversarialSettlementStressTest {

    private SettlementCalculationEngine calculationEngine;
    private final UUID vendorId = UUID.randomUUID();
    private final LocalDate periodStart = LocalDate.of(2026, 9, 1);
    private final LocalDate periodEnd = LocalDate.of(2026, 9, 30);

    @BeforeEach
    void setUp() {
        calculationEngine = new SettlementCalculationEngine();
    }

    // =========================================================================
    // 1. FINANCIAL PRECISION & ROUNDING CHECKS (HALF_UP SCALE 2)
    // =========================================================================
    @Nested
    @DisplayName("1. Financial Precision & Accounting Invariant Stress Tests")
    class FinancialPrecisionTests {

        @ParameterizedTest(name = "Subtotal: {0}, Rate: {1}")
        @CsvSource({
                "0.01, 0.1000",
                "0.01, 0.1500",
                "0.01, 0.0750",
                "0.02, 0.1500",
                "0.03, 0.0750",
                "0.05, 0.1500",
                "0.07, 0.1000",
                "0.09, 0.3333",
                "33.33, 0.1000",
                "33.33, 0.1500",
                "33.33, 0.0750",
                "33.33, 0.3333",
                "100.05, 0.1000",
                "100.05, 0.1500",
                "100.05, 0.0750",
                "100.05, 0.0000",
                "100.05, 1.0000",
                "999999.99, 0.0750",
                "999999.99, 0.1500",
                "9999999999.99, 0.1000"
        })
        @DisplayName("Kiểm tra bất biến kế toán grossAmount == commissionAmount + netAmount trên các số lẻ")
        void testAccountingInvariantOnOddAmounts(String subtotalStr, String rateStr) {
            BigDecimal subtotal = new BigDecimal(subtotalStr);
            BigDecimal rate = new BigDecimal(rateStr);

            SubOrderCalculationContext context = SubOrderCalculationContext.builder()
                    .subOrderId(UUID.randomUUID())
                    .status(SubOrderStatus.COMPLETED)
                    .subtotalAmount(subtotal)
                    .refundAmount(BigDecimal.ZERO)
                    .hasActiveDispute(false)
                    .commissionRate(rate)
                    .build();

            SettlementLineItem item = calculationEngine.calculateLineItem(context);

            assertThat(item.isExcluded()).isFalse();
            assertThat(item.getCommissionAmount().scale()).isEqualTo(2);
            assertThat(item.getNetAmount().scale()).isEqualTo(2);
            assertThat(item.getGrossAmount().scale()).isEqualTo(2);

            // Invariant: grossAmount == commissionAmount + netAmount
            BigDecimal sum = item.getCommissionAmount().add(item.getNetAmount());
            assertThat(sum).isEqualByComparingTo(item.getGrossAmount());
            assertThat(item.getGrossAmount()).isEqualByComparingTo(subtotal);
        }

        @Test
        @DisplayName("Monte Carlo Stress: 10,000 giao dịch ngẫu nhiên đều bảo toàn bất biến kế toán 100%")
        void monteCarloAccountingInvariantStress() {
            Random random = new Random(42); // Deterministic seed
            int iterations = 10000;

            for (int i = 0; i < iterations; i++) {
                long cents = 1 + random.nextInt(100_000_000); // 0.01 to 1,000,000.00
                BigDecimal subtotal = BigDecimal.valueOf(cents, 2);
                int rateBasisPoints = random.nextInt(10001); // 0.0000 to 1.0000
                BigDecimal rate = BigDecimal.valueOf(rateBasisPoints, 4);

                SubOrderCalculationContext context = SubOrderCalculationContext.builder()
                        .subOrderId(UUID.randomUUID())
                        .status(SubOrderStatus.COMPLETED)
                        .subtotalAmount(subtotal)
                        .refundAmount(BigDecimal.ZERO)
                        .hasActiveDispute(false)
                        .commissionRate(rate)
                        .build();

                SettlementLineItem item = calculationEngine.calculateLineItem(context);

                BigDecimal sum = item.getCommissionAmount().add(item.getNetAmount());
                assertThat(sum).as("Mismatch at subtotal=%s, rate=%s", subtotal, rate)
                        .isEqualByComparingTo(item.getGrossAmount());
            }
        }

        @Test
        @DisplayName("Từ chối số tiền đơn hàng âm (InvalidOrderAmountException)")
        void shouldRejectNegativeOrderAmount() {
            SubOrderCalculationContext context = SubOrderCalculationContext.builder()
                    .subOrderId(UUID.randomUUID())
                    .status(SubOrderStatus.COMPLETED)
                    .subtotalAmount(new BigDecimal("-100.00"))
                    .commissionRate(new BigDecimal("0.1000"))
                    .build();

            assertThatThrownBy(() -> calculationEngine.calculateLineItem(context))
                    .isInstanceOf(InvalidOrderAmountException.class)
                    .hasMessageContaining("cannot be negative");
        }

        @Test
        @DisplayName("Từ chối tỷ lệ hoa hồng âm hoặc > 1.0 (InvalidCommissionRateException)")
        void shouldRejectInvalidCommissionRates() {
            SubOrderCalculationContext negativeRate = SubOrderCalculationContext.builder()
                    .subOrderId(UUID.randomUUID())
                    .status(SubOrderStatus.COMPLETED)
                    .subtotalAmount(new BigDecimal("100.00"))
                    .commissionRate(new BigDecimal("-0.0001"))
                    .build();

            assertThatThrownBy(() -> calculationEngine.calculateLineItem(negativeRate))
                    .isInstanceOf(InvalidCommissionRateException.class);

            SubOrderCalculationContext rateAboveOne = SubOrderCalculationContext.builder()
                    .subOrderId(UUID.randomUUID())
                    .status(SubOrderStatus.COMPLETED)
                    .subtotalAmount(new BigDecimal("100.00"))
                    .commissionRate(new BigDecimal("1.0001"))
                    .build();

            assertThatThrownBy(() -> calculationEngine.calculateLineItem(rateAboveOne))
                    .isInstanceOf(InvalidCommissionRateException.class);
        }
    }

    // =========================================================================
    // 2. REFUND EXCLUSION & NET-AFTER-REFUND CHECKS
    // =========================================================================
    @Nested
    @DisplayName("2. Refund Exclusion & Net-After-Refund Adversarial Tests")
    class RefundAdversarialTests {

        @Test
        @DisplayName("Đơn hoàn 100%: Tự động loại trừ tuyệt đối, net = 0, commission = 0, gross = 0")
        void testFullRefundExclusionAbsolute() {
            SubOrderCalculationContext context = SubOrderCalculationContext.builder()
                    .subOrderId(UUID.randomUUID())
                    .status(SubOrderStatus.REFUNDED)
                    .subtotalAmount(new BigDecimal("500000.00"))
                    .refundAmount(new BigDecimal("500000.00"))
                    .hasActiveDispute(false)
                    .commissionRate(new BigDecimal("0.1000"))
                    .build();

            SettlementLineItem item = calculationEngine.calculateLineItem(context);

            assertThat(item.isExcluded()).isTrue();
            assertThat(item.getExcludedReason()).isEqualTo(LineItemExclusionReason.FULL_REFUND);
            assertThat(item.getGrossAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.getCommissionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.getNetAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Đơn status COMPLETED nhưng refundAmount >= subtotalAmount: Phải tự động nhận diện FULL_REFUND")
        void testCompletedWithExcessiveRefundDetectedAsFullRefund() {
            SubOrderCalculationContext context = SubOrderCalculationContext.builder()
                    .subOrderId(UUID.randomUUID())
                    .status(SubOrderStatus.COMPLETED)
                    .subtotalAmount(new BigDecimal("500000.00"))
                    .refundAmount(new BigDecimal("550000.00")) // Refund > subtotal
                    .hasActiveDispute(false)
                    .commissionRate(new BigDecimal("0.1000"))
                    .build();

            SettlementLineItem item = calculationEngine.calculateLineItem(context);

            assertThat(item.isExcluded()).isTrue();
            assertThat(item.getExcludedReason()).isEqualTo(LineItemExclusionReason.FULL_REFUND);
            assertThat(item.getGrossAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.getCommissionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.getNetAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Đơn hoàn 99.99%: Hoa hồng tính trên 0.01% còn lại, không tính trên giá gốc")
        void testPartialRefund99Percent() {
            BigDecimal subtotal = new BigDecimal("10000.00");
            BigDecimal refund = new BigDecimal("9999.00"); // 99.99% refund -> netAfterRefund = 1.00
            BigDecimal rate = new BigDecimal("0.1000");    // 10%

            SubOrderCalculationContext context = SubOrderCalculationContext.builder()
                    .subOrderId(UUID.randomUUID())
                    .status(SubOrderStatus.PARTIALLY_REFUNDED)
                    .subtotalAmount(subtotal)
                    .refundAmount(refund)
                    .hasActiveDispute(false)
                    .commissionRate(rate)
                    .build();

            SettlementLineItem item = calculationEngine.calculateLineItem(context);

            assertThat(item.isExcluded()).isFalse();
            assertThat(item.getGrossAmount()).isEqualByComparingTo(new BigDecimal("1.00"));
            assertThat(item.getCommissionAmount()).isEqualByComparingTo(new BigDecimal("0.10"));
            assertThat(item.getNetAmount()).isEqualByComparingTo(new BigDecimal("0.90"));
            assertThat(item.getGrossAmount()).isEqualByComparingTo(item.getCommissionAmount().add(item.getNetAmount()));
        }

        @Test
        @DisplayName("Đơn hoàn 0.01%: Hoa hồng tính trên 99.99% còn lại")
        void testPartialRefundTinyAmount() {
            BigDecimal subtotal = new BigDecimal("100.00");
            BigDecimal refund = new BigDecimal("0.01"); // Net = 99.99
            BigDecimal rate = new BigDecimal("0.1500"); // 15%

            SubOrderCalculationContext context = SubOrderCalculationContext.builder()
                    .subOrderId(UUID.randomUUID())
                    .status(SubOrderStatus.PARTIALLY_REFUNDED)
                    .subtotalAmount(subtotal)
                    .refundAmount(refund)
                    .hasActiveDispute(false)
                    .commissionRate(rate)
                    .build();

            SettlementLineItem item = calculationEngine.calculateLineItem(context);

            // 99.99 * 0.15 = 14.9985 -> 15.00
            // Net = 99.99 - 15.00 = 84.99
            assertThat(item.isExcluded()).isFalse();
            assertThat(item.getGrossAmount()).isEqualByComparingTo(new BigDecimal("99.99"));
            assertThat(item.getCommissionAmount()).isEqualByComparingTo(new BigDecimal("15.00"));
            assertThat(item.getNetAmount()).isEqualByComparingTo(new BigDecimal("84.99"));
            assertThat(item.getGrossAmount()).isEqualByComparingTo(item.getCommissionAmount().add(item.getNetAmount()));
        }
    }

    // =========================================================================
    // 3. DISPUTE EXCLUSION & RESOLUTION CHECKS
    // =========================================================================
    @Nested
    @DisplayName("3. Dispute Exclusion & Resolution State Tests")
    class DisputeAdversarialTests {

        @Test
        @DisplayName("Đơn có Dispute OPEN: Bị tạm giữ tuyệt đối (ACTIVE_DISPUTE), amounts = 0")
        void testOpenDisputeHeld() {
            SubOrderCalculationContext context = SubOrderCalculationContext.builder()
                    .subOrderId(UUID.randomUUID())
                    .status(SubOrderStatus.COMPLETED)
                    .subtotalAmount(new BigDecimal("1000000.00"))
                    .hasActiveDispute(true)
                    .disputeStatus(DisputeStatus.OPEN)
                    .commissionRate(new BigDecimal("0.1000"))
                    .build();

            SettlementLineItem item = calculationEngine.calculateLineItem(context);

            assertThat(item.isExcluded()).isTrue();
            assertThat(item.getExcludedReason()).isEqualTo(LineItemExclusionReason.ACTIVE_DISPUTE);
            assertThat(item.getGrossAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.getCommissionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.getNetAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Đơn có Dispute UNDER_REVIEW: Cũng bị tạm giữ (ACTIVE_DISPUTE)")
        void testUnderReviewDisputeHeld() {
            SubOrderCalculationContext context = SubOrderCalculationContext.builder()
                    .subOrderId(UUID.randomUUID())
                    .status(SubOrderStatus.COMPLETED)
                    .subtotalAmount(new BigDecimal("2000000.00"))
                    .hasActiveDispute(true)
                    .disputeStatus(DisputeStatus.UNDER_REVIEW)
                    .commissionRate(new BigDecimal("0.1000"))
                    .build();

            SettlementLineItem item = calculationEngine.calculateLineItem(context);

            assertThat(item.isExcluded()).isTrue();
            assertThat(item.getExcludedReason()).isEqualTo(LineItemExclusionReason.ACTIVE_DISPUTE);
        }

        @Test
        @DisplayName("Đơn có Dispute đã RESOLVED_REJECTED: Không bị tạm giữ, tính bình thường")
        void testResolvedRejectedDisputeCalculatedNormally() {
            SubOrderCalculationContext context = SubOrderCalculationContext.builder()
                    .subOrderId(UUID.randomUUID())
                    .status(SubOrderStatus.COMPLETED)
                    .subtotalAmount(new BigDecimal("1000000.00"))
                    .hasActiveDispute(false)
                    .disputeStatus(DisputeStatus.RESOLVED_REJECTED)
                    .commissionRate(new BigDecimal("0.1000"))
                    .build();

            SettlementLineItem item = calculationEngine.calculateLineItem(context);

            assertThat(item.isExcluded()).isFalse();
            assertThat(item.getGrossAmount()).isEqualByComparingTo(new BigDecimal("1000000.00"));
            assertThat(item.getCommissionAmount()).isEqualByComparingTo(new BigDecimal("100000.00"));
            assertThat(item.getNetAmount()).isEqualByComparingTo(new BigDecimal("900000.00"));
        }

        @Test
        @DisplayName("Đơn có Dispute vừa OPEN vừa có refundAmount > 0: Ưu tiên tạm giữ ACTIVE_DISPUTE")
        void testActiveDisputePrecedenceOverRefund() {
            SubOrderCalculationContext context = SubOrderCalculationContext.builder()
                    .subOrderId(UUID.randomUUID())
                    .status(SubOrderStatus.PARTIALLY_REFUNDED)
                    .subtotalAmount(new BigDecimal("1000000.00"))
                    .refundAmount(new BigDecimal("200000.00"))
                    .hasActiveDispute(true)
                    .disputeStatus(DisputeStatus.OPEN)
                    .commissionRate(new BigDecimal("0.1000"))
                    .build();

            SettlementLineItem item = calculationEngine.calculateLineItem(context);

            assertThat(item.isExcluded()).isTrue();
            assertThat(item.getExcludedReason()).isEqualTo(LineItemExclusionReason.ACTIVE_DISPUTE);
            assertThat(item.getGrossAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // =========================================================================
    // 4. PERIOD AGGREGATION & LARGE BATCH INVARIANTS
    // =========================================================================
    @Nested
    @DisplayName("4. Aggregate & Settlement Invariant Stress Tests")
    class SettlementAggregateTests {

        @Test
        @DisplayName("Batch lớn 1,000 đơn hàng hỗn hợp: totalGross == totalCommission + totalNetPayout")
        void testLargeBatchAggregationInvariant() {
            Random random = new Random(12345);
            List<SubOrderCalculationContext> orders = new ArrayList<>();
            int count = 1000;

            for (int i = 0; i < count; i++) {
                int type = random.nextInt(4);
                long cents = 10000 + random.nextInt(50000000); // 100.00 to 500,000.00 VND
                BigDecimal subtotal = BigDecimal.valueOf(cents, 2);
                BigDecimal rate = new BigDecimal("0.1000");

                if (type == 0) { // Standard COMPLETED
                    orders.add(SubOrderCalculationContext.builder()
                            .subOrderId(UUID.randomUUID())
                            .status(SubOrderStatus.COMPLETED)
                            .subtotalAmount(subtotal)
                            .commissionRate(rate)
                            .build());
                } else if (type == 1) { // 100% Refund
                    orders.add(SubOrderCalculationContext.builder()
                            .subOrderId(UUID.randomUUID())
                            .status(SubOrderStatus.REFUNDED)
                            .subtotalAmount(subtotal)
                            .refundAmount(subtotal)
                            .commissionRate(rate)
                            .build());
                } else if (type == 2) { // Partial refund (20% to 80%)
                    long refundCents = (cents * (20 + random.nextInt(60))) / 100;
                    BigDecimal refund = BigDecimal.valueOf(refundCents, 2);
                    orders.add(SubOrderCalculationContext.builder()
                            .subOrderId(UUID.randomUUID())
                            .status(SubOrderStatus.PARTIALLY_REFUNDED)
                            .subtotalAmount(subtotal)
                            .refundAmount(refund)
                            .commissionRate(rate)
                            .build());
                } else { // Active Dispute
                    orders.add(SubOrderCalculationContext.builder()
                            .subOrderId(UUID.randomUUID())
                            .status(SubOrderStatus.COMPLETED)
                            .subtotalAmount(subtotal)
                            .hasActiveDispute(true)
                            .disputeStatus(DisputeStatus.OPEN)
                            .commissionRate(rate)
                            .build());
                }
            }

            SettlementCalculationResult result = calculationEngine.calculateSettlement(
                    vendorId, periodStart, periodEnd, orders, new BigDecimal("0.1000")
            );

            // Bất biến tổng hợp toàn kỳ
            BigDecimal sumLineGross = BigDecimal.ZERO;
            BigDecimal sumLineCom = BigDecimal.ZERO;
            BigDecimal sumLineNet = BigDecimal.ZERO;

            for (SettlementLineItem item : result.getLineItems()) {
                if (!item.isExcluded()) {
                    sumLineGross = sumLineGross.add(item.getGrossAmount());
                    sumLineCom = sumLineCom.add(item.getCommissionAmount());
                    sumLineNet = sumLineNet.add(item.getNetAmount());
                    // Invariant từng dòng
                    assertThat(item.getGrossAmount()).isEqualByComparingTo(item.getCommissionAmount().add(item.getNetAmount()));
                }
            }

            assertThat(result.getTotalGrossRevenue()).isEqualByComparingTo(sumLineGross);
            assertThat(result.getTotalCommission()).isEqualByComparingTo(sumLineCom);
            assertThat(result.getTotalNetPayout()).isEqualByComparingTo(sumLineNet);

            // Bất biến kỳ: totalGross == totalCommission + totalNetPayout
            assertThat(result.getTotalGrossRevenue())
                    .isEqualByComparingTo(result.getTotalCommission().add(result.getTotalNetPayout()));
        }

        @Test
        @DisplayName("Settlement State Transitions: DRAFT -> FINALIZED -> PAID và kiểm tra tính bất biến")
        void testSettlementStateTransitions() {
            Settlement settlement = Settlement.builder()
                    .id(UUID.randomUUID())
                    .vendorId(vendorId)
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .totalGrossRevenue(new BigDecimal("1000.00"))
                    .totalCommission(new BigDecimal("100.00"))
                    .totalNetPayout(new BigDecimal("900.00"))
                    .status(SettlementStatus.DRAFT)
                    .build();

            assertThat(settlement.canBeRegenerated()).isTrue();
            assertThat(settlement.isImmutable()).isFalse();

            // Chuyển sang FINALIZED
            settlement.finalizeSettlement();
            assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.FINALIZED);
            assertThat(settlement.canBeRegenerated()).isFalse();
            assertThat(settlement.isImmutable()).isTrue();

            // Thử finalize lại -> phải throw exception
            assertThatThrownBy(settlement::finalizeSettlement)
                    .isInstanceOf(SettlementAlreadyFinalizedException.class);

            // Chuyển sang PAID
            settlement.markAsPaid();
            assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PAID);
            assertThat(settlement.canBeRegenerated()).isFalse();
            assertThat(settlement.isImmutable()).isTrue();
        }
    }
}
