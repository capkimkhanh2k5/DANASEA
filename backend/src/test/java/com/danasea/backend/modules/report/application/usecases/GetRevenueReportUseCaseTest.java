package com.danasea.backend.modules.report.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.report.application.ports.MasterOrderRecord;
import com.danasea.backend.modules.report.application.ports.RefundRecord;
import com.danasea.backend.modules.report.application.ports.ReportDataPort;
import com.danasea.backend.modules.report.application.ports.SubOrderRecord;
import com.danasea.backend.modules.report.domain.enums.GroupByPeriod;
import com.danasea.backend.modules.report.domain.models.RevenueReportItem;
import com.danasea.backend.modules.report.domain.models.TimeRange;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import com.danasea.backend.modules.report.ReportFinancialTestFixtures;
import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetRevenueReportUseCase Financial Math & Zero-Filling Tests")
class GetRevenueReportUseCaseTest {

    @Mock private ReportDataPort reportDataPort;

    private GetRevenueReportUseCase useCase;
    private final ZoneId zone = TimeRange.VIETNAM_ZONE;

    @BeforeEach
    void setUp() {
        useCase = new GetRevenueReportUseCase(reportDataPort);
    }

    @Test
    @DisplayName("Zero-filling đầy đủ các ngày trong khoảng khi không có giao dịch nào phát sinh")
    void shouldZeroFillEmptyPeriodsForDailyReport() {
        TimeRange timeRange = TimeRange.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3));

        Mockito.lenient()
                .when(reportDataPort.findSubOrders(eq(timeRange), any()))
                .thenReturn(Collections.emptyList());
        ReportFinancialTestFixtures.paidOrders(
                reportDataPort, Collections.emptyList());
        Mockito.lenient()
                .when(reportDataPort.findRefunds(eq(timeRange), any()))
                .thenReturn(Collections.emptyList());
        Mockito.lenient()
                .when(reportDataPort.findRefundsBySubOrderIds(any()))
                .thenReturn(Collections.emptyList());
        Mockito.lenient()
                .when(reportDataPort.findMasterOrders(eq(timeRange)))
                .thenReturn(Collections.emptyList());

        List<RevenueReportItem> result = useCase.execute(timeRange, GroupByPeriod.DAY);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getPeriodKey()).isEqualTo("2026-10-01");
        assertThat(result.get(1).getPeriodKey()).isEqualTo("2026-10-02");
        assertThat(result.get(2).getPeriodKey()).isEqualTo("2026-10-03");

        for (RevenueReportItem item : result) {
            assertThat(item.getGmv()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.getCollectedCash()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.getRefunds()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.getPlatformCommission()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.getNetVendorPayout()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.getOrderCount()).isZero();
        }
    }

    @Test
    @DisplayName("Zero-filling cho báo cáo gom nhóm theo QUARTER và YEAR")
    void shouldZeroFillQuarterlyAndYearlyReports() {
        TimeRange quarterRange = TimeRange.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 30));
        Mockito.lenient()
                .when(reportDataPort.findSubOrders(eq(quarterRange), any()))
                .thenReturn(Collections.emptyList());
        ReportFinancialTestFixtures.paidOrders(
                reportDataPort, Collections.emptyList());
        Mockito.lenient()
                .when(reportDataPort.findRefunds(eq(quarterRange), any()))
                .thenReturn(Collections.emptyList());
        Mockito.lenient()
                .when(reportDataPort.findRefundsBySubOrderIds(any()))
                .thenReturn(Collections.emptyList());
        Mockito.lenient()
                .when(reportDataPort.findMasterOrders(eq(quarterRange)))
                .thenReturn(Collections.emptyList());

        List<RevenueReportItem> quarterResult =
                useCase.execute(quarterRange, GroupByPeriod.QUARTER);
        assertThat(quarterResult).hasSize(3);
        assertThat(quarterResult)
                .extracting(RevenueReportItem::getPeriodKey)
                .containsExactly("2026-Q1", "2026-Q2", "2026-Q3");

        TimeRange yearRange = TimeRange.of(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 12, 31));
        Mockito.lenient()
                .when(reportDataPort.findSubOrders(eq(yearRange), any()))
                .thenReturn(Collections.emptyList());
        ReportFinancialTestFixtures.paidOrders(
                reportDataPort, Collections.emptyList());
        Mockito.lenient()
                .when(reportDataPort.findRefunds(eq(yearRange), any()))
                .thenReturn(Collections.emptyList());
        Mockito.lenient()
                .when(reportDataPort.findRefundsBySubOrderIds(any()))
                .thenReturn(Collections.emptyList());
        Mockito.lenient()
                .when(reportDataPort.findMasterOrders(eq(yearRange)))
                .thenReturn(Collections.emptyList());

        List<RevenueReportItem> yearResult = useCase.execute(yearRange, GroupByPeriod.YEAR);
        assertThat(yearResult).hasSize(3);
        assertThat(yearResult)
                .extracting(RevenueReportItem::getPeriodKey)
                .containsExactly("2024", "2025", "2026");
    }

    @Test
    @DisplayName(
            "Kiểm chứng công thức tài chính: Collected Cash = GMV - Discount; Net Vendor Payout ="
                    + " Collected Cash - Commission - Refunds")
    void shouldReconcileFinancialFormulasWithRealData() {
        TimeRange timeRange = TimeRange.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2));
        UUID masterOrderId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();

        // Đơn 1 ngày 01/10: GMV 1,000,000, Commission 100,000, Discount 100,000, Refund 0
        OffsetDateTime date1 =
                LocalDate.of(2026, 10, 1).atTime(10, 0).atZone(zone).toOffsetDateTime();
        SubOrderRecord subOrder1 =
                new SubOrderRecord(
                        UUID.randomUUID(),
                        masterOrderId,
                        vendorId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        2,
                        BigDecimal.valueOf(500_000),
                        BigDecimal.valueOf(1_000_000),
                        BigDecimal.valueOf(0.10),
                        BigDecimal.valueOf(100_000),
                        BigDecimal.valueOf(900_000),
                        SubOrderStatus.COMPLETED,
                        date1,
                        date1);

        MasterOrderRecord masterOrder =
                new MasterOrderRecord(
                        masterOrderId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null,
                        BigDecimal.valueOf(900_000),
                        BigDecimal.valueOf(100_000),
                        null,
                        PaymentOrderStatus.PAID,
                        date1,
                        date1);

        Mockito.lenient()
                .when(reportDataPort.findSubOrders(eq(timeRange), eq(null)))
                .thenReturn(List.of(subOrder1));
        ReportFinancialTestFixtures.paidOrders(
                reportDataPort, List.of(subOrder1));
        Mockito.lenient()
                .when(reportDataPort.findRefunds(eq(timeRange), eq(null)))
                .thenReturn(Collections.emptyList());
        Mockito.lenient()
                .when(reportDataPort.findRefundsBySubOrderIds(any()))
                .thenReturn(Collections.emptyList());
        Mockito.lenient()
                .when(reportDataPort.findMasterOrders(eq(timeRange)))
                .thenReturn(List.of(masterOrder));

        List<RevenueReportItem> result = useCase.execute(timeRange, GroupByPeriod.DAY, null);

        assertThat(result).hasSize(2);

        // Ngày 01/10: Có dữ liệu
        RevenueReportItem day1 = result.get(0);
        assertThat(day1.getPeriodKey()).isEqualTo("2026-10-01");
        assertThat(day1.getGmv()).isEqualByComparingTo("1000000");
        assertThat(day1.getDiscountAmount()).isEqualByComparingTo("100000");
        assertThat(day1.getCollectedCash()).isEqualByComparingTo("900000"); // 1,000,000 - 100,000
        assertThat(day1.getRefunds()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(day1.getPlatformCommission()).isEqualByComparingTo("100000");
        // Net Vendor Payout = Collected Cash (900,000) - Commission (100,000) - Refunds (0) =
        // 800,000
        assertThat(day1.getNetVendorPayout()).isEqualByComparingTo("800000");
        assertThat(day1.getOrderCount()).isEqualTo(1);

        // Ngày 02/10: Zero-filled
        RevenueReportItem day2 = result.get(1);
        assertThat(day2.getPeriodKey()).isEqualTo("2026-10-02");
        assertThat(day2.getGmv()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(day2.getOrderCount()).isZero();
    }

    @Test
    @DisplayName("Kiểm chứng đối soát kịch bản hoàn tiền 100% do bão WEATHER")
    void shouldReconcileWeatherFullRefundScenario() {
        TimeRange timeRange = TimeRange.of(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5));
        UUID subOrderId = UUID.randomUUID();
        UUID masterOrderId = UUID.randomUUID();
        OffsetDateTime dt = LocalDate.of(2026, 10, 5).atTime(14, 0).atZone(zone).toOffsetDateTime();

        SubOrderRecord subOrder =
                new SubOrderRecord(
                        subOrderId,
                        masterOrderId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        BigDecimal.valueOf(2_000_000),
                        BigDecimal.valueOf(2_000_000),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        SubOrderStatus.REFUNDED,
                        dt,
                        dt);

        RefundRecord refund =
                new RefundRecord(
                        UUID.randomUUID(),
                        subOrderId,
                        UUID.randomUUID(),
                        BigDecimal.valueOf(2_000_000),
                        BigDecimal.valueOf(1.0),
                        RefundReason.WEATHER,
                        RefundStatus.PROCESSED,
                        dt,
                        dt);

        Mockito.lenient()
                .when(reportDataPort.findSubOrders(eq(timeRange), any()))
                .thenReturn(List.of(subOrder));
        ReportFinancialTestFixtures.paidOrders(
                reportDataPort, List.of(subOrder));
        Mockito.lenient()
                .when(reportDataPort.findRefunds(eq(timeRange), any()))
                .thenReturn(List.of(refund));
        Mockito.lenient()
                .when(reportDataPort.findRefundsBySubOrderIds(any()))
                .thenReturn(List.of(refund));
        Mockito.lenient()
                .when(reportDataPort.findMasterOrders(eq(timeRange)))
                .thenReturn(Collections.emptyList());

        List<RevenueReportItem> result = useCase.execute(timeRange, GroupByPeriod.DAY);

        assertThat(result).hasSize(1);
        RevenueReportItem item = result.getFirst();
        assertThat(item.getGmv()).isEqualByComparingTo("2000000");
        assertThat(item.getCollectedCash()).isEqualByComparingTo("2000000");
        assertThat(item.getRefunds()).isEqualByComparingTo("2000000");
        assertThat(item.getPlatformCommission()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(item.getNetVendorPayout()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
