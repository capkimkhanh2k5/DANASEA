package com.danasea.backend.modules.report.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.report.application.usecases.GetAdminDashboardUseCase;
import com.danasea.backend.modules.report.application.usecases.GetBookingReportUseCase;
import com.danasea.backend.modules.report.application.usecases.GetRevenueReportUseCase;
import com.danasea.backend.modules.report.application.usecases.GetVendorDashboardUseCase;
import com.danasea.backend.modules.report.application.usecases.GetVendorPerformanceReportUseCase;
import com.danasea.backend.modules.report.domain.enums.GroupByPeriod;
import com.danasea.backend.modules.report.domain.models.RevenueReportItem;
import com.danasea.backend.modules.report.domain.models.TimeRange;
import com.danasea.backend.modules.report.infrastructure.csv.Rfc4180Utf8BomCsvExporter;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

@Transactional
class ReportFinancialIntegrationTest extends BaseSecurityIntegrationTest {
    @DynamicPropertySource
    static void isolatedReportSchema(DynamicPropertyRegistry registry) {
        String url =
                "jdbc:postgresql://"
                        + postgres.getHost()
                        + ":"
                        + postgres.getFirstMappedPort()
                        + "/testdb?currentSchema=report_financial";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.schemas", () -> "report_financial");
        registry.add("spring.flyway.default-schema", () -> "report_financial");
        registry.add("spring.datasource.hikari.schema", () -> "report_financial");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "report_financial");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("app.scheduler.enabled", () -> "false");
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private GetRevenueReportUseCase revenue;
    @Autowired private GetBookingReportUseCase bookings;
    @Autowired private GetAdminDashboardUseCase admin;
    @Autowired private GetVendorDashboardUseCase vendorDashboard;
    @Autowired private GetVendorPerformanceReportUseCase vendors;
    @Autowired private Rfc4180Utf8BomCsvExporter csv;
    private UUID vendor;
    private final OffsetDateTime dt = OffsetDateTime.parse("2026-10-09T10:00:00+07:00");
    private final TimeRange day =
            TimeRange.of(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 9));

    @BeforeEach
    void setup() {
        vendor = vendor("Vendor A");
    }

    private UUID vendor(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO vendors(id,business_name,verification_status,created_at,updated_at)"
                        + " VALUES (?,?,'APPROVED',?,?)",
                id,
                name,
                dt,
                dt);
        return id;
    }

    private UUID master() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO master_orders(id,status,created_at,updated_at) VALUES (?,'PAID',?,?)",
                id,
                dt,
                dt);
        return id;
    }

    private UUID order(
            UUID master,
            UUID owner,
            String status,
            String amount,
            OffsetDateTime created,
            String reason) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " sub_orders(id,master_order_id,vendor_id,status,subtotal_amount,commission_rate,commission_amount,cancellation_reason,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,0.10,?,?,?,?)",
                id,
                master,
                owner,
                status,
                new BigDecimal(amount),
                new BigDecimal(amount).multiply(new BigDecimal("0.1")),
                reason,
                created,
                created);
        return id;
    }

    private void payment(UUID master, String status, String amount, OffsetDateTime occurred) {
        jdbc.update(
                "INSERT INTO"
                    + " payments(id,master_order_id,status,provider,amount,paid_at,created_at,updated_at)"
                    + " VALUES (?,?,?,'VNPAY',?,?,?,?)",
                UUID.randomUUID(),
                master,
                status,
                new BigDecimal(amount),
                occurred,
                dt.minusDays(30),
                dt);
    }

    private void refund(
            UUID line, String status, String amount, OffsetDateTime processed, String reason) {
        jdbc.update(
                "INSERT INTO"
                    + " refunds(id,sub_order_id,status,amount,reason,processed_at,created_at,updated_at,retry_count,verification_attempts)"
                    + " VALUES (?,?,?,?,?,?,?,?,0,0)",
                UUID.randomUUID(),
                line,
                status,
                new BigDecimal(amount),
                reason,
                processed,
                dt.minusDays(30),
                dt);
    }

    private RevenueReportItem row(TimeRange range, UUID owner) {
        return revenue.execute(range, GroupByPeriod.DAY, owner).getFirst();
    }

    private BigDecimal total(List<RevenueReportItem> rows) {
        return rows.stream()
                .map(RevenueReportItem::getNetVendorPayout)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void unpaidCancelledOrdersAndFailedOrPendingPaymentsHaveNoCash() {
        UUID m = master();
        order(m, vendor, "CANCELLED", "1000", dt, "CUSTOMER_CANCEL");
        payment(m, "PENDING", "1000", null);
        payment(m, "FAILED", "1000", null);
        assertThat(row(day, vendor).getCollectedCash()).isEqualByComparingTo("0");
        assertThat(vendorDashboard.execute(vendor, day).getTotalRevenue())
                .isEqualByComparingTo("0");
    }

    @Test
    void paidAtAndExclusiveEndExcludeNextDayMidnight() {
        UUID m = master();
        order(m, vendor, "COMPLETED", "1000", dt.minusDays(30), null);
        payment(m, "SUCCESS", "1000", dt);
        UUID next = master();
        order(next, vendor, "COMPLETED", "2000", dt, null);
        payment(next, "SUCCESS", "2000", day.getEndExclusiveDateTime());
        assertThat(row(day, vendor).getCollectedCash()).isEqualByComparingTo("1000");
        assertThat(
                        row(TimeRange.of(day.getTo().plusDays(1), day.getTo().plusDays(1)), vendor)
                                .getCollectedCash())
                .isEqualByComparingTo("2000");
    }

    @Test
    void pendingAndFailedRefundsDoNotReduceActualPayout() {
        UUID m = master();
        UUID o = order(m, vendor, "COMPLETED", "1000", dt, null);
        payment(m, "SUCCESS", "1000", dt);
        refund(o, "PENDING", "500", null, "CUSTOMER_CANCEL");
        refund(o, "FAILED", "500", null, "CUSTOMER_CANCEL");
        assertThat(row(day, vendor).getRefunds()).isEqualByComparingTo("0");
        assertThat(row(day, vendor).getNetVendorPayout()).isEqualByComparingTo("900");
    }

    @Test
    void partialRefundMatchesSettlementPolicyAndAllDashboards() {
        UUID m = master();
        UUID o = order(m, vendor, "PARTIALLY_REFUNDED", "1000", dt, "CUSTOMER_CANCEL");
        payment(m, "SUCCESS", "1000", dt);
        refund(o, "PROCESSED", "500", dt, "CUSTOMER_CANCEL");
        assertThat(row(day, vendor).getPlatformCommission()).isEqualByComparingTo("50");
        assertThat(row(day, vendor).getNetVendorPayout()).isEqualByComparingTo("450");
        assertThat(vendorDashboard.execute(vendor, day).getNetRevenue())
                .isEqualByComparingTo("450");
        assertThat(admin.execute(day, vendor).getNetRevenue()).isEqualByComparingTo("450");
        assertThat(admin.execute(day, null).getNetRevenue()).isEqualByComparingTo("50");
        assertThat(
                        vendors.execute(day, null).stream()
                                .filter(v -> v.getVendorId().equals(vendor))
                                .findFirst()
                                .orElseThrow()
                                .getNetPayout())
                .isEqualByComparingTo("450");
    }

    @Test
    void refundedPaymentRetainsOriginalCashAndReversesCommission() {
        UUID m = master();
        UUID o = order(m, vendor, "REFUNDED", "1000", dt, "WEATHER");
        payment(m, "REFUNDED", "1000", dt);
        refund(o, "PROCESSED", "1000", dt, "WEATHER");
        assertThat(row(day, vendor).getCollectedCash()).isEqualByComparingTo("1000");
        assertThat(row(day, vendor).getPlatformCommission()).isEqualByComparingTo("0");
        assertThat(row(day, vendor).getNetVendorPayout()).isEqualByComparingTo("0");
    }

    @Test
    void previousMonthRefundHasSignedAdjustmentAndConsistentVendorTotals() {
        UUID m = master();
        UUID o =
                order(m, vendor, "PARTIALLY_REFUNDED", "1000", dt.minusDays(30), "CUSTOMER_CANCEL");
        payment(m, "SUCCESS", "1000", dt.minusDays(30));
        refund(o, "PROCESSED", "300", dt, "CUSTOMER_CANCEL");
        assertThat(row(day, vendor).getRefunds()).isEqualByComparingTo("300");
        assertThat(row(day, vendor).getPlatformCommission()).isEqualByComparingTo("-30");
        assertThat(row(day, vendor).getNetVendorPayout()).isEqualByComparingTo("-270");
        var all =
                vendors.execute(day, null).stream()
                        .filter(v -> v.getVendorId().equals(vendor))
                        .findFirst()
                        .orElseThrow();
        assertThat(all.getNetPayout())
                .isEqualByComparingTo(vendors.execute(day, vendor).getFirst().getNetPayout())
                .isEqualByComparingTo("-270");
    }

    @Test
    void totalsAgreeAcrossAllGroupingsWithCrossDayRefund() {
        UUID m = master();
        UUID o = order(m, vendor, "REFUNDED", "1000", dt, "WEATHER");
        payment(m, "REFUNDED", "1000", dt);
        refund(o, "PROCESSED", "1000", dt.plusDays(1), "WEATHER");
        TimeRange range = TimeRange.of(day.getFrom(), day.getTo().plusDays(1));
        assertThat(revenue.execute(range, GroupByPeriod.DAY, vendor).get(1).getNetVendorPayout())
                .isEqualByComparingTo("-900");
        for (GroupByPeriod group : GroupByPeriod.values())
            assertThat(total(revenue.execute(range, group, vendor))).isEqualByComparingTo("0");
    }

    @Test
    void centAllocationAcrossVendorsPreservesCashAndDiscount() {
        UUID m = master();
        UUID b = vendor("Vendor B"), c = vendor("Vendor C");
        for (UUID owner : List.of(vendor, b, c)) order(m, owner, "COMPLETED", "100", dt, null);
        payment(m, "SUCCESS", "200", dt);
        BigDecimal cash = BigDecimal.ZERO, discount = BigDecimal.ZERO;
        for (UUID owner : List.of(vendor, b, c)) {
            cash = cash.add(row(day, owner).getCollectedCash());
            discount = discount.add(row(day, owner).getDiscountAmount());
        }
        assertThat(cash).isEqualByComparingTo("200");
        assertThat(discount).isEqualByComparingTo("100");
        assertThat(row(day, null).getCollectedCash()).isEqualByComparingTo(cash);
    }

    @Test
    void repeatedPartialRefundsReverseRoundedCommissionOnlyOnce() {
        UUID m = master();
        UUID o = order(m, vendor, "REFUNDED", "0.05", dt, "CUSTOMER_CANCEL");
        payment(m, "REFUNDED", "0.05", dt);
        refund(o, "PROCESSED", "0.02", dt.plusMinutes(1), "CUSTOMER_CANCEL");
        refund(o, "PROCESSED", "0.03", dt.plusMinutes(2), "CUSTOMER_CANCEL");
        assertThat(row(day, vendor).getPlatformCommission()).isEqualByComparingTo("0");
        assertThat(row(day, vendor).getNetVendorPayout()).isEqualByComparingTo("0");
        assertThat(row(day, vendor).getNetVendorPayout()).isEqualByComparingTo("0");
    }

    @Test
    void cancellationCountsEachOrderOnceAndExcludesCompensation() {
        UUID m = master();
        UUID complete = order(m, vendor, "COMPLETED", "1000", dt, null);
        refund(complete, "PROCESSED", "100", dt, "COMPENSATION");
        UUID cancel = order(m, vendor, "CANCELLED", "1000", dt, "CUSTOMER_CANCEL");
        refund(cancel, "PROCESSED", "100", dt, "CUSTOMER_CANCEL");
        refund(cancel, "PROCESSED", "100", dt.plusMinutes(1), "CUSTOMER_CANCEL");
        order(m, vendor, "CANCELLED", "1000", dt, "CUSTOMER_CANCEL");
        order(m, vendor, "CANCELLED", "1000", dt, null);
        var item = bookings.execute(day, GroupByPeriod.DAY, vendor).getFirst();
        assertThat(item.getCancelledOrders()).isEqualTo(3);
        assertThat(item.getCancellationCount(RefundReason.CUSTOMER_CANCEL)).isEqualTo(2);
        assertThat(item.getUnknownCancellationCount()).isEqualTo(1);
        assertThat(item.getCancellationReasonBreakdown())
                .doesNotContainKey(RefundReason.COMPENSATION);
    }

    @Test
    void vendorIsolationHoldsForRefundOnlyPeriods() {
        UUID m = master();
        UUID o = order(m, vendor, "REFUNDED", "1000", dt.minusDays(30), "WEATHER");
        refund(o, "PROCESSED", "1000", dt, "WEATHER");
        assertThat(row(day, UUID.randomUUID()).getRefunds()).isEqualByComparingTo("0");
    }

    @Test
    void paidAtSurvivesSuccessReplayAndRefund() {
        PaymentJpaEntity p = new PaymentJpaEntity();
        p.setStatus(PaymentStatus.SUCCESS);
        OffsetDateTime original = p.getPaidAt();
        p.setStatus(PaymentStatus.SUCCESS);
        p.setStatus(PaymentStatus.REFUNDED);
        assertThat(p.getPaidAt()).isEqualTo(original);
    }

    @Test
    void csvNeutralizesFormulasAndKeepsSignedAmountsNumeric() {
        UUID evil = vendor(" \t=1+1"), m = master();
        UUID o = order(m, evil, "REFUNDED", "1000", dt.minusDays(30), "WEATHER");
        refund(o, "PROCESSED", "1000", dt, "WEATHER");
        String text =
                new String(
                        csv.exportVendorPerformanceReport(vendors.execute(day, evil), day),
                        StandardCharsets.UTF_8);
        assertThat(text).contains(",' \t=1+1,").contains(",-900.00,");
    }

    @Test
    void reportRangeIsBounded() {
        assertThatThrownBy(() -> TimeRange.of(LocalDate.of(2000, 1, 1), LocalDate.of(2099, 12, 31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3660 days");
    }
}
