package com.danasea.backend.modules.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.report.application.ports.MasterOrderRecord;
import com.danasea.backend.modules.report.application.ports.RefundRecord;
import com.danasea.backend.modules.report.application.ports.ReportDataPort;
import com.danasea.backend.modules.report.application.ports.SubOrderRecord;
import com.danasea.backend.modules.report.application.ports.VendorRecord;
import com.danasea.backend.modules.report.application.usecases.ExportReportCsvUseCase;
import com.danasea.backend.modules.report.application.usecases.GetAdminDashboardUseCase;
import com.danasea.backend.modules.report.application.usecases.GetBookingReportUseCase;
import com.danasea.backend.modules.report.application.usecases.GetRevenueReportUseCase;
import com.danasea.backend.modules.report.application.usecases.GetVendorDashboardUseCase;
import com.danasea.backend.modules.report.application.usecases.GetVendorPerformanceReportUseCase;
import com.danasea.backend.modules.report.domain.enums.GroupByPeriod;
import com.danasea.backend.modules.report.domain.enums.ReportType;
import com.danasea.backend.modules.report.domain.enums.VendorAlertSeverity;
import com.danasea.backend.modules.report.domain.enums.VendorIssueType;
import com.danasea.backend.modules.report.domain.models.BookingReportItem;
import com.danasea.backend.modules.report.domain.models.DashboardMetrics;
import com.danasea.backend.modules.report.domain.models.RevenueReportItem;
import com.danasea.backend.modules.report.domain.models.TimeRange;
import com.danasea.backend.modules.report.domain.models.VendorPerformanceItem;
import com.danasea.backend.modules.report.infrastructure.csv.Rfc4180Utf8BomCsvExporter;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.danasea.backend.modules.report.ReportFinancialTestFixtures;
import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
@DisplayName("Milestone 2 Adversarial Boundary, Stress & Integrity Test Suite")
class Milestone2AdversarialStressTest {

    @Mock private ReportDataPort reportDataPort;

    private GetRevenueReportUseCase getRevenueReportUseCase;
    private GetBookingReportUseCase getBookingReportUseCase;
    private GetVendorPerformanceReportUseCase getVendorPerformanceReportUseCase;
    private GetAdminDashboardUseCase getAdminDashboardUseCase;
    private GetVendorDashboardUseCase getVendorDashboardUseCase;
    private ExportReportCsvUseCase exportReportCsvUseCase;
    private Rfc4180Utf8BomCsvExporter csvExporter;

    private final ZoneId zone = TimeRange.VIETNAM_ZONE;

    @BeforeEach
    void setUp() {
        csvExporter = new Rfc4180Utf8BomCsvExporter();
        getRevenueReportUseCase = new GetRevenueReportUseCase(reportDataPort);
        getBookingReportUseCase = new GetBookingReportUseCase(reportDataPort);
        getVendorPerformanceReportUseCase = new GetVendorPerformanceReportUseCase(reportDataPort);
        getAdminDashboardUseCase = new GetAdminDashboardUseCase(reportDataPort);
        getVendorDashboardUseCase = new GetVendorDashboardUseCase(reportDataPort);
        exportReportCsvUseCase =
                new ExportReportCsvUseCase(
                        getRevenueReportUseCase,
                        getBookingReportUseCase,
                        getVendorPerformanceReportUseCase,
                        csvExporter);
    }

    @Nested
    @DisplayName("1. Financial Reconciliation & High-Scale Numbers Stress")
    class FinancialReconciliationStress {

        @Test
        @DisplayName(
                "Đối soát tài chính với số tiền lớn (hàng chục tỷ VNĐ) kết hợp voucher và hoàn"
                        + " tiền")
        void shouldReconcileLargeFinancialAmountsAccurately() {
            TimeRange timeRange =
                    TimeRange.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1));
            OffsetDateTime dt =
                    LocalDate.of(2026, 10, 1).atTime(15, 30).atZone(zone).toOffsetDateTime();
            UUID masterOrderId = UUID.randomUUID();
            UUID subOrderId = UUID.randomUUID();
            UUID vendorId = UUID.randomUUID();

            // GMV = 50,000,000,000 VND (50 tỷ)
            // Voucher Discount = 2,000,000,000 VND (2 tỷ)
            // Collected Cash = 48,000,000,000 VND (48 tỷ)
            // Hoàn tiền một phần = 8,000,000,000 VND (8 tỷ)
            // Platform commission = (50B gross - 8B refunds) * 10% = 4.2B
            BigDecimal gmv = BigDecimal.valueOf(50_000_000_000L);
            BigDecimal discount = BigDecimal.valueOf(2_000_000_000L);
            BigDecimal refundAmount = BigDecimal.valueOf(8_000_000_000L);
            BigDecimal commission = BigDecimal.valueOf(4_000_000_000L);

            SubOrderRecord subOrder =
                    new SubOrderRecord(
                            subOrderId,
                            masterOrderId,
                            vendorId,
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            100,
                            BigDecimal.valueOf(500_000_000L),
                            gmv,
                            BigDecimal.valueOf(0.10),
                            commission,
                            BigDecimal.valueOf(36_000_000_000L),
                            SubOrderStatus.PARTIALLY_REFUNDED,
                            dt,
                            dt);

            MasterOrderRecord masterOrder =
                    new MasterOrderRecord(
                            masterOrderId,
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            null,
                            BigDecimal.valueOf(48_000_000_000L),
                            discount,
                            null,
                            PaymentOrderStatus.PAID,
                            dt,
                            dt);

            RefundRecord refund =
                    new RefundRecord(
                            UUID.randomUUID(),
                            subOrderId,
                            UUID.randomUUID(),
                            refundAmount,
                            BigDecimal.valueOf(0.1667),
                            RefundReason.CUSTOMER_CANCEL,
                            RefundStatus.PROCESSED,
                            dt,
                            dt);

            Mockito.lenient()
                    .when(reportDataPort.findSubOrders(eq(timeRange), eq(null)))
                    .thenReturn(List.of(subOrder));
            ReportFinancialTestFixtures.paidOrders(
                    reportDataPort, List.of(subOrder));
            Mockito.lenient()
                    .when(reportDataPort.findRefunds(eq(timeRange), eq(null)))
                    .thenReturn(List.of(refund));
            Mockito.lenient()
                    .when(reportDataPort.findRefundsBySubOrderIds(any()))
                    .thenReturn(List.of(refund));
            Mockito.lenient()
                    .when(reportDataPort.findMasterOrders(eq(timeRange)))
                    .thenReturn(List.of(masterOrder));

            List<RevenueReportItem> result =
                    getRevenueReportUseCase.execute(timeRange, GroupByPeriod.DAY, null);

            assertThat(result).hasSize(1);
            RevenueReportItem item = result.getFirst();

            assertThat(item.getGmv()).isEqualByComparingTo("50000000000");
            assertThat(item.getDiscountAmount()).isEqualByComparingTo("2000000000");
            assertThat(item.getCollectedCash()).isEqualByComparingTo("48000000000");
            assertThat(item.getRefunds()).isEqualByComparingTo("8000000000");
            assertThat(item.getPlatformCommission()).isEqualByComparingTo("4200000000");

            // Đối soát: Net Vendor Payout = 48B cash - 4.2B commission - 8B refunds = 35.8B
            assertThat(item.getNetVendorPayout()).isEqualByComparingTo("35800000000");

            // Tổng kiểm tra toàn vẹn phương trình tài chính
            BigDecimal totalAccounted =
                    item.getNetVendorPayout()
                            .add(item.getPlatformCommission())
                            .add(item.getRefunds());
            assertThat(item.getCollectedCash()).isEqualByComparingTo(totalAccounted);
        }

        @Test
        @DisplayName("Preserve negative payout adjustments when refunds exceed collected cash")
        void shouldPreserveNegativeAdjustmentWhenRefundsExceedCash() {
            TimeRange timeRange =
                    TimeRange.of(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 2));
            OffsetDateTime dt =
                    LocalDate.of(2026, 10, 2).atTime(10, 0).atZone(zone).toOffsetDateTime();
            UUID subOrderId = UUID.randomUUID();

            SubOrderRecord subOrder =
                    new SubOrderRecord(
                            subOrderId,
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            1,
                            BigDecimal.valueOf(100_000),
                            BigDecimal.valueOf(100_000),
                            BigDecimal.valueOf(0.10),
                            BigDecimal.valueOf(10_000),
                            BigDecimal.valueOf(90_000),
                            SubOrderStatus.REFUNDED,
                            dt,
                            dt);

            // Bồi thường vượt mức GMV: 150,000 VND
            RefundRecord refund =
                    new RefundRecord(
                            UUID.randomUUID(),
                            subOrderId,
                            UUID.randomUUID(),
                            BigDecimal.valueOf(150_000),
                            BigDecimal.valueOf(1.5),
                            RefundReason.COMPENSATION,
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

            List<RevenueReportItem> result =
                    getRevenueReportUseCase.execute(timeRange, GroupByPeriod.DAY);

            assertThat(result).hasSize(1);
            RevenueReportItem item = result.getFirst();
            assertThat(item.getNetVendorPayout()).isEqualByComparingTo("-50000");
        }
    }

    @Nested
    @DisplayName("2. Time-Series Leap Year & Long Range Stress")
    class TimeSeriesLeapYearStress {

        @Test
        @DisplayName("Bao phủ chính xác ngày nhuận 29/02 trong năm nhuận 2024")
        void shouldIncludeLeapDayInDailyZeroFill() {
            TimeRange leapRange = TimeRange.of(LocalDate.of(2024, 2, 27), LocalDate.of(2024, 3, 2));

            Mockito.lenient()
                    .when(reportDataPort.findSubOrders(eq(leapRange), any()))
                    .thenReturn(Collections.emptyList());
            ReportFinancialTestFixtures.paidOrders(
                    reportDataPort, Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findRefunds(eq(leapRange), any()))
                    .thenReturn(Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findRefundsBySubOrderIds(any()))
                    .thenReturn(Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findMasterOrders(eq(leapRange)))
                    .thenReturn(Collections.emptyList());

            List<RevenueReportItem> result =
                    getRevenueReportUseCase.execute(leapRange, GroupByPeriod.DAY);

            assertThat(result).hasSize(5);
            assertThat(result)
                    .extracting(RevenueReportItem::getPeriodKey)
                    .containsExactly(
                            "2024-02-27", "2024-02-28", "2024-02-29", "2024-03-01", "2024-03-02");
        }

        @Test
        @DisplayName("Zero-filling khoảng thời gian dài 365 ngày chạy mượt mà không quá tải")
        void shouldHandleFullYearDailyZeroFillEfficiently() {
            TimeRange fullYear = TimeRange.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

            Mockito.lenient()
                    .when(reportDataPort.findSubOrders(eq(fullYear), any()))
                    .thenReturn(Collections.emptyList());
            ReportFinancialTestFixtures.paidOrders(
                    reportDataPort, Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findRefunds(eq(fullYear), any()))
                    .thenReturn(Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findRefundsBySubOrderIds(any()))
                    .thenReturn(Collections.emptyList());

            List<BookingReportItem> result =
                    getBookingReportUseCase.execute(fullYear, GroupByPeriod.DAY);

            assertThat(result).hasSize(365);
            assertThat(result.getFirst().getPeriodKey()).isEqualTo("2025-01-01");
            assertThat(result.getLast().getPeriodKey()).isEqualTo("2025-12-31");
        }
    }

    @Nested
    @DisplayName("3. Diagnostic Threshold & Boundary Logic")
    class DiagnosticThresholdStress {

        @Test
        @DisplayName(
                "Kiểm tra ngưỡng chẩn đoán chính xác: 20.0% hủy không cảnh báo, 20.01% hủy phát"
                        + " sinh HIGH_CANCELLATION")
        void shouldTestCancellationRateThresholds() {
            UUID vendorId = UUID.randomUUID();
            VendorRecord vendor =
                    new VendorRecord(
                            vendorId,
                            UUID.randomUUID(),
                            "Vendor Test",
                            VerificationStatus.APPROVED,
                            BigDecimal.valueOf(4.0),
                            10,
                            BadgeTier.VERIFIED);

            // 100 đơn, đúng 20 đơn hủy -> 20.0% (<= 20% -> không có lỗi HIGH_CANCELLATION)
            VendorPerformanceItem item20 =
                    VendorPerformanceItem.evaluate(
                            vendorId,
                            "Vendor Test",
                            BigDecimal.valueOf(100),
                            BigDecimal.valueOf(100),
                            100,
                            80,
                            20,
                            50,
                            100,
                            BigDecimal.valueOf(4.0),
                            10);
            assertThat(item20.getCancellationRate()).isEqualTo(20.0);
            assertThat(item20.getIssues()).doesNotContain(VendorIssueType.HIGH_CANCELLATION);
            assertThat(item20.getSeverity()).isEqualTo(VendorAlertSeverity.HEALTHY);

            // 100 đơn, 21 đơn hủy -> 21.0% (> 20% -> có lỗi HIGH_CANCELLATION)
            VendorPerformanceItem item21 =
                    VendorPerformanceItem.evaluate(
                            vendorId,
                            "Vendor Test",
                            BigDecimal.valueOf(100),
                            BigDecimal.valueOf(100),
                            100,
                            79,
                            21,
                            50,
                            100,
                            BigDecimal.valueOf(4.0),
                            10);
            assertThat(item21.getCancellationRate()).isEqualTo(21.0);
            assertThat(item21.getIssues()).contains(VendorIssueType.HIGH_CANCELLATION);
            assertThat(item21.getSeverity()).isEqualTo(VendorAlertSeverity.WARNING);
        }

        @Test
        @DisplayName("Kiểm tra ngưỡng quá tải slot: 94% không cảnh báo, 95% phát sinh OVERLOADED")
        void shouldTestSlotOccupancyThresholds() {
            UUID vendorId = UUID.randomUUID();

            VendorPerformanceItem item94 =
                    VendorPerformanceItem.evaluate(
                            vendorId,
                            "Vendor Test",
                            BigDecimal.valueOf(100),
                            BigDecimal.valueOf(100),
                            10,
                            10,
                            0,
                            94,
                            100,
                            BigDecimal.valueOf(4.0),
                            10);
            assertThat(item94.getSlotOccupancyRate()).isEqualTo(94.0);
            assertThat(item94.getIssues()).doesNotContain(VendorIssueType.OVERLOADED);

            VendorPerformanceItem item95 =
                    VendorPerformanceItem.evaluate(
                            vendorId,
                            "Vendor Test",
                            BigDecimal.valueOf(100),
                            BigDecimal.valueOf(100),
                            10,
                            10,
                            0,
                            95,
                            100,
                            BigDecimal.valueOf(4.0),
                            10);
            assertThat(item95.getSlotOccupancyRate()).isEqualTo(95.0);
            assertThat(item95.getIssues()).contains(VendorIssueType.OVERLOADED);
        }
    }

    @Nested
    @DisplayName("4. Real-World Application Scenarios (TEST_INFRA §Tier 4)")
    class RealWorldScenarios {

        @Test
        @DisplayName(
                "Kịch bản 1 - Mùa Bão (Weather Disruption): 50 đơn bị hủy đột ngột do WEATHER -> tỷ"
                        + " lệ hủy tăng vọt, hoàn 100%")
        void shouldHandleWeatherDisruptionScenario() {
            TimeRange stormDay =
                    TimeRange.of(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15));
            UUID vendorId = UUID.randomUUID();
            OffsetDateTime dt =
                    LocalDate.of(2026, 10, 15).atTime(8, 0).atZone(zone).toOffsetDateTime();

            List<SubOrderRecord> subOrders = new ArrayList<>();
            List<RefundRecord> refunds = new ArrayList<>();

            // 50 đơn đều bị hủy do thời tiết
            for (int i = 0; i < 50; i++) {
                UUID orderId = UUID.randomUUID();
                subOrders.add(
                        new SubOrderRecord(
                                orderId,
                                UUID.randomUUID(),
                                vendorId,
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                1,
                                BigDecimal.valueOf(500_000),
                                BigDecimal.valueOf(500_000),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                SubOrderStatus.CANCELLED,
                                dt,
                                dt));
                refunds.add(
                        new RefundRecord(
                                UUID.randomUUID(),
                                orderId,
                                UUID.randomUUID(),
                                BigDecimal.valueOf(500_000),
                                BigDecimal.valueOf(1.0),
                                RefundReason.WEATHER,
                                RefundStatus.PROCESSED,
                                dt,
                                dt));
            }

            Mockito.lenient()
                    .when(reportDataPort.findSubOrders(eq(stormDay), eq(vendorId)))
                    .thenReturn(subOrders);
            ReportFinancialTestFixtures.paidOrders(
                    reportDataPort, subOrders);
            Mockito.lenient()
                    .when(reportDataPort.findRefunds(eq(stormDay), eq(vendorId)))
                    .thenReturn(refunds);
            Mockito.lenient()
                    .when(reportDataPort.findRefundsBySubOrderIds(any()))
                    .thenReturn(refunds);
            when(reportDataPort.findServiceSlots(eq(stormDay), eq(vendorId)))
                    .thenReturn(Collections.emptyList());
            when(reportDataPort.findVendorById(vendorId))
                    .thenReturn(
                            Optional.of(
                                    new VendorRecord(
                                            vendorId,
                                            UUID.randomUUID(),
                                            "Tàu Bão",
                                            VerificationStatus.APPROVED,
                                            BigDecimal.valueOf(4.0),
                                            10,
                                            BadgeTier.VERIFIED)));

            DashboardMetrics metrics = getVendorDashboardUseCase.execute(vendorId, stormDay);

            assertThat(metrics.getNewOrders()).isEqualTo(50);
            assertThat(metrics.getCompletedOrders()).isZero();
            assertThat(metrics.getCancelledOrders()).isEqualTo(50);
            assertThat(metrics.getCancellationRate()).isEqualTo(100.0);
            assertThat(metrics.getNetRevenue()).isEqualByComparingTo(BigDecimal.ZERO);

            // Cảnh báo CRITICAL phát sinh
            assertThat(metrics.getAlerts()).isNotEmpty();
            assertThat(metrics.getAlerts().getFirst().getSeverity())
                    .isEqualTo(VendorAlertSeverity.CRITICAL);
            assertThat(metrics.getAlerts().getFirst().getIssueType())
                    .isEqualTo(VendorIssueType.HIGH_CANCELLATION);
        }

        @Test
        @DisplayName(
                "Kịch bản 4 - Kế toán xuất file CSV: Đảm bảo UTF-8 BOM, ký tự tiếng Việt có dấu,"
                        + " chuẩn RFC 4180")
        void shouldExportAccountingCsvWithValidEncodingAndBom() {
            TimeRange range = TimeRange.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2));

            Mockito.lenient()
                    .when(reportDataPort.findSubOrders(eq(range), any()))
                    .thenReturn(Collections.emptyList());
            ReportFinancialTestFixtures.paidOrders(
                    reportDataPort, Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findRefunds(eq(range), any()))
                    .thenReturn(Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findRefundsBySubOrderIds(any()))
                    .thenReturn(Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findMasterOrders(eq(range)))
                    .thenReturn(Collections.emptyList());

            byte[] csvBytes =
                    exportReportCsvUseCase.execute(
                            ReportType.REVENUE, range, GroupByPeriod.DAY, null);

            // 1. Kiểm tra 3 byte đầu là UTF-8 BOM
            assertThat(csvBytes[0]).isEqualTo((byte) 0xEF);
            assertThat(csvBytes[1]).isEqualTo((byte) 0xBB);
            assertThat(csvBytes[2]).isEqualTo((byte) 0xBF);

            // 2. Kiểm tra chuỗi decode từ byte thứ 3 có chứa tiêu đề tiếng Việt chuẩn
            String content = new String(csvBytes, 3, csvBytes.length - 3, StandardCharsets.UTF_8);
            assertThat(content).contains("Kỳ báo cáo");
            assertThat(content).contains("Tổng giá trị đặt (GMV)");
            assertThat(content).contains("Thực thu từ khách");
            assertThat(content).contains("Hoa hồng sàn");
            assertThat(content).contains("Thực nhận Vendor");
            assertThat(content).contains("2026-10-01");
            assertThat(content).contains("2026-10-02");
        }
    }
}
