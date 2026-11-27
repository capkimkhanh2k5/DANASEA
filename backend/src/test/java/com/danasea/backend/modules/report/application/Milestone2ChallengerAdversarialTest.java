package com.danasea.backend.modules.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.danasea.backend.modules.report.application.usecases.TimeSeriesPeriodHelper;
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
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.danasea.backend.modules.report.ReportFinancialTestFixtures;
import org.mockito.Mockito;

/**
 * Empirical Adversarial Challenger Test Suite for Milestone 2. Focuses on: 1. Single-day range
 * (from == to) across all periods 2. Multi-year long ranges (quarter & year cycles) 3. Leap-year
 * boundaries (28/02 - 01/03/2024) and ISO week transitions (W52/W53 -> W01) 4. Zero-filling
 * integrity when DB is completely empty (no NPEs, valid 0 values, continuous keys) 5. Timezone
 * shift resilience (UTC vs Asia/Ho_Chi_Minh) and null safety
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Milestone 2 Empirical Adversarial Challenger Test Suite")
class Milestone2ChallengerAdversarialTest {

    @Mock private ReportDataPort reportDataPort;

    private GetRevenueReportUseCase getRevenueReportUseCase;
    private GetBookingReportUseCase getBookingReportUseCase;
    private GetVendorPerformanceReportUseCase getVendorPerformanceReportUseCase;
    private GetAdminDashboardUseCase getAdminDashboardUseCase;
    private GetVendorDashboardUseCase getVendorDashboardUseCase;
    private ExportReportCsvUseCase exportReportCsvUseCase;
    private Rfc4180Utf8BomCsvExporter csvExporter;

    private final ZoneId vnZone = TimeRange.VIETNAM_ZONE;

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

    // =========================================================================
    // 1. KHOẢNG NGÀY ĐƠN LẺ (from == to)
    // =========================================================================
    @Nested
    @DisplayName("1. Single-Day Range (from == to) Stress Tests")
    class SingleDayRangeTests {

        @Test
        @DisplayName(
                "TimeSeriesPeriodHelper: from == to trả về đúng 1 key duy nhất cho tất cả chu kỳ")
        void shouldGenerateExactlyOneKeyForSingleDayAcrossAllPeriods() {
            LocalDate date = LocalDate.of(2024, 2, 29); // Leap day

            List<String> dayKeys =
                    TimeSeriesPeriodHelper.generatePeriodKeys(date, date, GroupByPeriod.DAY);
            assertThat(dayKeys).containsExactly("2024-02-29");

            List<String> weekKeys =
                    TimeSeriesPeriodHelper.generatePeriodKeys(date, date, GroupByPeriod.WEEK);
            assertThat(weekKeys).containsExactly("2024-W09");

            List<String> quarterKeys =
                    TimeSeriesPeriodHelper.generatePeriodKeys(date, date, GroupByPeriod.QUARTER);
            assertThat(quarterKeys).containsExactly("2024-Q1");

            List<String> yearKeys =
                    TimeSeriesPeriodHelper.generatePeriodKeys(date, date, GroupByPeriod.YEAR);
            assertThat(yearKeys).containsExactly("2024");
        }

        @Test
        @DisplayName(
                "GetRevenueReportUseCase: from == to khi không có giao dịch trả về 1 item"
                        + " zero-filled")
        void shouldReturnSingleZeroFilledItemForSingleDayWithoutTransactions() {
            LocalDate singleDay = LocalDate.of(2026, 10, 8);
            TimeRange range = TimeRange.of(singleDay, singleDay);

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

            List<RevenueReportItem> result =
                    getRevenueReportUseCase.execute(range, GroupByPeriod.DAY);

            assertThat(result).hasSize(1);
            RevenueReportItem item = result.getFirst();
            assertThat(item.getPeriodKey()).isEqualTo("2026-10-08");
            assertThat(item.getGmv()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.getCollectedCash()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.getRefunds()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.getPlatformCommission()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.getNetVendorPayout()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.getOrderCount()).isZero();
        }

        @Test
        @DisplayName(
                "GetBookingReportUseCase: from == to khi không có giao dịch trả về 1 item"
                        + " zero-filled")
        void shouldReturnSingleZeroFilledBookingItemForSingleDay() {
            LocalDate singleDay = LocalDate.of(2026, 10, 8);
            TimeRange range = TimeRange.of(singleDay, singleDay);

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

            List<BookingReportItem> result =
                    getBookingReportUseCase.execute(range, GroupByPeriod.DAY);

            assertThat(result).hasSize(1);
            BookingReportItem item = result.getFirst();
            assertThat(item.getPeriodKey()).isEqualTo("2026-10-08");
            assertThat(item.getTotalOrders()).isZero();
            assertThat(item.getCompletedOrders()).isZero();
            assertThat(item.getCancelledOrders()).isZero();
            assertThat(item.getCancellationReasonBreakdown()).isEmpty();
        }

        @Test
        @DisplayName(
                "GetRevenueReportUseCase: from == to với nhiều giao dịch từ 00:00:01 đến 23:59:59"
                        + " gom nhóm chính xác")
        void shouldAggregateMultipleTransactionsAcrossSameDayBoundaries() {
            LocalDate singleDay = LocalDate.of(2026, 5, 20);
            TimeRange range = TimeRange.of(singleDay, singleDay);

            OffsetDateTime morning = singleDay.atTime(0, 0, 1).atZone(vnZone).toOffsetDateTime();
            OffsetDateTime noon = singleDay.atTime(12, 0, 0).atZone(vnZone).toOffsetDateTime();
            OffsetDateTime night = singleDay.atTime(23, 59, 59).atZone(vnZone).toOffsetDateTime();

            SubOrderRecord order1 =
                    new SubOrderRecord(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            1,
                            BigDecimal.valueOf(100_000),
                            BigDecimal.valueOf(100_000),
                            BigDecimal.valueOf(0.1),
                            BigDecimal.valueOf(10_000),
                            BigDecimal.valueOf(90_000),
                            SubOrderStatus.COMPLETED,
                            morning,
                            morning);
            SubOrderRecord order2 =
                    new SubOrderRecord(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            2,
                            BigDecimal.valueOf(200_000),
                            BigDecimal.valueOf(200_000),
                            BigDecimal.valueOf(0.1),
                            BigDecimal.valueOf(20_000),
                            BigDecimal.valueOf(180_000),
                            SubOrderStatus.COMPLETED,
                            noon,
                            noon);
            SubOrderRecord order3 =
                    new SubOrderRecord(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            1,
                            BigDecimal.valueOf(300_000),
                            BigDecimal.valueOf(300_000),
                            BigDecimal.valueOf(0.1),
                            BigDecimal.valueOf(30_000),
                            BigDecimal.valueOf(270_000),
                            SubOrderStatus.COMPLETED,
                            night,
                            night);

            Mockito.lenient()
                    .when(reportDataPort.findSubOrders(eq(range), any()))
                    .thenReturn(List.of(order1, order2, order3));
            ReportFinancialTestFixtures.paidOrders(
                    reportDataPort, List.of(order1, order2, order3));
            Mockito.lenient()
                    .when(reportDataPort.findRefunds(eq(range), any()))
                    .thenReturn(Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findRefundsBySubOrderIds(any()))
                    .thenReturn(Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findMasterOrders(eq(range)))
                    .thenReturn(Collections.emptyList());

            List<RevenueReportItem> result =
                    getRevenueReportUseCase.execute(range, GroupByPeriod.DAY);

            assertThat(result).hasSize(1);
            RevenueReportItem item = result.getFirst();
            assertThat(item.getPeriodKey()).isEqualTo("2026-05-20");
            assertThat(item.getGmv()).isEqualByComparingTo("600000");
            assertThat(item.getPlatformCommission()).isEqualByComparingTo("60000");
            assertThat(item.getNetVendorPayout()).isEqualByComparingTo("540000");
            assertThat(item.getOrderCount()).isEqualTo(3);
        }
    }

    // =========================================================================
    // 2. KHOẢNG THỜI GIAN DÀI (NHIỀU NĂM, CHU KỲ QUÝ/NĂM)
    // =========================================================================
    @Nested
    @DisplayName("2. Multi-Year Long Ranges & Quarter/Year Cycles Stress Tests")
    class MultiYearLongRangeTests {

        @Test
        @DisplayName(
                "TimeSeriesPeriodHelper: Khoảng 8 năm (2020-2027) gom theo YEAR sinh đúng 8 năm"
                        + " liên tục")
        void shouldGenerateContinuousYearsOverMultiYearRange() {
            LocalDate from = LocalDate.of(2020, 1, 1);
            LocalDate to = LocalDate.of(2027, 12, 31);

            List<String> yearKeys =
                    TimeSeriesPeriodHelper.generatePeriodKeys(from, to, GroupByPeriod.YEAR);

            assertThat(yearKeys)
                    .containsExactly(
                            "2020", "2021", "2022", "2023", "2024", "2025", "2026", "2027");
        }

        @Test
        @DisplayName(
                "TimeSeriesPeriodHelper: Khoảng thời gian vắt qua nhiều năm gom theo QUARTER sinh"
                        + " đầy đủ không bỏ sót quý nào")
        void shouldGenerateContinuousQuartersOverMultiYearRange() {
            LocalDate from = LocalDate.of(2022, 5, 10); // Q2 2022
            LocalDate to = LocalDate.of(2025, 8, 20); // Q3 2025

            List<String> quarterKeys =
                    TimeSeriesPeriodHelper.generatePeriodKeys(from, to, GroupByPeriod.QUARTER);

            assertThat(quarterKeys)
                    .containsExactly(
                            "2022-Q2", "2022-Q3", "2022-Q4", "2023-Q1", "2023-Q2", "2023-Q3",
                            "2023-Q4", "2024-Q1", "2024-Q2", "2024-Q3", "2024-Q4", "2025-Q1",
                            "2025-Q2", "2025-Q3");
            assertThat(quarterKeys).hasSize(14);
        }

        @Test
        @DisplayName(
                "GetRevenueReportUseCase: Gom theo QUARTER trong 3 năm với 1 giao dịch duy nhất ở"
                        + " giữa kỳ")
        void shouldZeroFillQuartersOverThreeYearsWithSingleTransaction() {
            LocalDate from = LocalDate.of(2023, 1, 1);
            LocalDate to = LocalDate.of(2025, 12, 31);
            TimeRange range = TimeRange.of(from, to);

            // Giao dịch duy nhất vào Q2 2024
            OffsetDateTime dt =
                    LocalDate.of(2024, 5, 15).atTime(10, 0).atZone(vnZone).toOffsetDateTime();
            SubOrderRecord order =
                    new SubOrderRecord(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            1,
                            BigDecimal.valueOf(1_000_000),
                            BigDecimal.valueOf(1_000_000),
                            BigDecimal.valueOf(0.1),
                            BigDecimal.valueOf(100_000),
                            BigDecimal.valueOf(900_000),
                            SubOrderStatus.COMPLETED,
                            dt,
                            dt);

            Mockito.lenient()
                    .when(reportDataPort.findSubOrders(eq(range), any()))
                    .thenReturn(List.of(order));
            ReportFinancialTestFixtures.paidOrders(
                    reportDataPort, List.of(order));
            Mockito.lenient()
                    .when(reportDataPort.findRefunds(eq(range), any()))
                    .thenReturn(Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findRefundsBySubOrderIds(any()))
                    .thenReturn(Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findMasterOrders(eq(range)))
                    .thenReturn(Collections.emptyList());

            List<RevenueReportItem> result =
                    getRevenueReportUseCase.execute(range, GroupByPeriod.QUARTER);

            // 3 năm * 4 quý = 12 quý
            assertThat(result).hasSize(12);

            for (RevenueReportItem item : result) {
                if ("2024-Q2".equals(item.getPeriodKey())) {
                    assertThat(item.getGmv()).isEqualByComparingTo("1000000");
                    assertThat(item.getPlatformCommission()).isEqualByComparingTo("100000");
                    assertThat(item.getNetVendorPayout()).isEqualByComparingTo("900000");
                    assertThat(item.getOrderCount()).isEqualTo(1);
                } else {
                    assertThat(item.getGmv()).isEqualByComparingTo(BigDecimal.ZERO);
                    assertThat(item.getOrderCount()).isZero();
                }
            }
        }
    }

    // =========================================================================
    // 3. RANH GIỚI NĂM NHUẬN & CHUYỂN TUẦN GIỮA HAI NĂM
    // =========================================================================
    @Nested
    @DisplayName("3. Leap-Year Boundary & ISO Week Transition Stress Tests")
    class LeapYearAndWeekBoundaryTests {

        @Test
        @DisplayName("Năm nhuận 2024 vs Năm thường 2023: Xử lý chính xác ngày 29/02")
        void shouldHandleLeapYearVsNonLeapYearCorrectly() {
            // Năm nhuận 2024: 28/02 đến 01/03 gồm 3 ngày (28, 29, 01)
            LocalDate leapFrom = LocalDate.of(2024, 2, 28);
            LocalDate leapTo = LocalDate.of(2024, 3, 1);
            List<String> leapKeys =
                    TimeSeriesPeriodHelper.generatePeriodKeys(leapFrom, leapTo, GroupByPeriod.DAY);
            assertThat(leapKeys).containsExactly("2024-02-28", "2024-02-29", "2024-03-01");

            // Năm thường 2023: 28/02 đến 01/03 chỉ có 2 ngày (28, 01)
            LocalDate nonLeapFrom = LocalDate.of(2023, 2, 28);
            LocalDate nonLeapTo = LocalDate.of(2023, 3, 1);
            List<String> nonLeapKeys =
                    TimeSeriesPeriodHelper.generatePeriodKeys(
                            nonLeapFrom, nonLeapTo, GroupByPeriod.DAY);
            assertThat(nonLeapKeys).containsExactly("2023-02-28", "2023-03-01");
        }

        @Test
        @DisplayName("Năm nhuận thế kỷ (2000 nhuận vs 1900 không nhuận)")
        void shouldHandleCenturialLeapYearRules() {
            // 2000 là năm nhuận thế kỷ (chia hết cho 400)
            List<String> y2000 =
                    TimeSeriesPeriodHelper.generatePeriodKeys(
                            LocalDate.of(2000, 2, 28), LocalDate.of(2000, 3, 1), GroupByPeriod.DAY);
            assertThat(y2000).containsExactly("2000-02-28", "2000-02-29", "2000-03-01");

            // 1900 không phải năm nhuận (chia hết cho 100 nhưng không chia hết cho 400)
            List<String> y1900 =
                    TimeSeriesPeriodHelper.generatePeriodKeys(
                            LocalDate.of(1900, 2, 28), LocalDate.of(1900, 3, 1), GroupByPeriod.DAY);
            assertThat(y1900).containsExactly("1900-02-28", "1900-03-01");
        }

        @Test
        @DisplayName("Đơn hàng ngày nhuận bị lệch múi giờ UTC -> UTC+7 rơi chính xác vào 29/02")
        void shouldPlaceLeapDayOrderCorrectlyWhenCreatedInUtc() {
            TimeRange range = TimeRange.of(LocalDate.of(2024, 2, 28), LocalDate.of(2024, 3, 1));

            // Đơn hàng tạo lúc 2024-02-28 17:30:00 UTC
            // Sang giờ Việt Nam (UTC+7): 2024-02-29 00:30:00+07:00 -> rơi vào ngày 29/02!
            OffsetDateTime orderUtc = OffsetDateTime.of(2024, 2, 28, 17, 30, 0, 0, ZoneOffset.UTC);

            SubOrderRecord order =
                    new SubOrderRecord(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            1,
                            BigDecimal.valueOf(500_000),
                            BigDecimal.valueOf(500_000),
                            BigDecimal.valueOf(0.1),
                            BigDecimal.valueOf(50_000),
                            BigDecimal.valueOf(450_000),
                            SubOrderStatus.COMPLETED,
                            orderUtc,
                            orderUtc);

            Mockito.lenient()
                    .when(reportDataPort.findSubOrders(eq(range), any()))
                    .thenReturn(List.of(order));
            ReportFinancialTestFixtures.paidOrders(
                    reportDataPort, List.of(order));
            Mockito.lenient()
                    .when(reportDataPort.findRefunds(eq(range), any()))
                    .thenReturn(Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findRefundsBySubOrderIds(any()))
                    .thenReturn(Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findMasterOrders(eq(range)))
                    .thenReturn(Collections.emptyList());

            List<RevenueReportItem> result =
                    getRevenueReportUseCase.execute(range, GroupByPeriod.DAY);

            assertThat(result).hasSize(3);
            RevenueReportItem day28 = result.get(0);
            RevenueReportItem day29 = result.get(1);
            RevenueReportItem day01 = result.get(2);

            assertThat(day28.getPeriodKey()).isEqualTo("2024-02-28");
            assertThat(day28.getOrderCount()).isZero();

            // Đơn phải rơi đúng vào ngày 29
            assertThat(day29.getPeriodKey()).isEqualTo("2024-02-29");
            assertThat(day29.getOrderCount()).isEqualTo(1);
            assertThat(day29.getGmv()).isEqualByComparingTo("500000");

            assertThat(day01.getPeriodKey()).isEqualTo("2024-03-01");
            assertThat(day01.getOrderCount()).isZero();
        }

        @Test
        @DisplayName(
                "Chuyển tuần ISO 2020 sang 2021: 2020 có 53 tuần (2020-W52 -> 2020-W53 ->"
                        + " 2021-W01)")
        void shouldHandle53WeekYearTransitionToWeek1() {
            LocalDate from = LocalDate.of(2020, 12, 21); // Thứ Hai tuần 52
            LocalDate to = LocalDate.of(2021, 1, 10); // Chủ Nhật tuần 1

            List<String> weekKeys =
                    TimeSeriesPeriodHelper.generatePeriodKeys(from, to, GroupByPeriod.WEEK);

            assertThat(weekKeys).containsExactly("2020-W52", "2020-W53", "2021-W01");
        }

        @Test
        @DisplayName(
                "Chuyển tuần ISO 2024 sang 2025: Ngày 30/12/2024 (Thứ Hai) thuộc tuần 1 năm 2025"
                        + " (2025-W01)")
        void shouldHandleIsoWeekYearTransitionWhereDecemberBelongsToNextYear() {
            // Ngày 29/12/2024 là Chủ Nhật tuần 52 năm 2024
            // Ngày 30/12/2024 là Thứ Hai tuần 1 năm 2025 (2025-W01)
            LocalDate from = LocalDate.of(2024, 12, 23);
            LocalDate to = LocalDate.of(2025, 1, 5);

            List<String> weekKeys =
                    TimeSeriesPeriodHelper.generatePeriodKeys(from, to, GroupByPeriod.WEEK);

            // Đảm bảo không bị sinh ngược key '2024-W01' mà phải là '2025-W01'
            assertThat(weekKeys).containsExactly("2024-W52", "2025-W01");
        }

        @Test
        @DisplayName(
                "Chuyển tuần ISO 2021 sang 2022: Ngày 01/01/2022 (Thứ Bảy) thuộc tuần 52 năm 2021"
                        + " (2021-W52)")
        void shouldHandleIsoWeekYearTransitionWhereJanuaryBelongsToPreviousYear() {
            // Ngày 31/12/2021 (Thứ Sáu), 01/01/2022 (Thứ Bảy), 02/01/2022 (Chủ Nhật) đều thuộc
            // 2021-W52
            // Ngày 03/01/2022 (Thứ Hai) bắt đầu 2022-W01
            LocalDate from = LocalDate.of(2021, 12, 27);
            LocalDate to = LocalDate.of(2022, 1, 9);

            List<String> weekKeys =
                    TimeSeriesPeriodHelper.generatePeriodKeys(from, to, GroupByPeriod.WEEK);

            assertThat(weekKeys).containsExactly("2021-W52", "2022-W01");
        }
    }

    // =========================================================================
    // 4. KIỂM TRA ZERO-FILLING HOÀN HẢO KHI CƠ SỞ DỮ LIỆU HOÀN TOÀN TRỐNG
    // =========================================================================
    @Nested
    @DisplayName("4. Perfect Zero-Filling on Completely Empty Database Stress Tests")
    class EmptyDatabaseZeroFillingTests {

        private final TimeRange thirtyDays =
                TimeRange.of(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        @Test
        @DisplayName(
                "GetRevenueReportUseCase: DB hoàn toàn trống vẫn trả về 30 ngày đầy đủ với số 0,"
                        + " không null, không NPE")
        void shouldZeroFillRevenueReportForEntireMonthWithZeroOrders() {
            Mockito.lenient()
                    .when(reportDataPort.findSubOrders(eq(thirtyDays), any()))
                    .thenReturn(Collections.emptyList());
            ReportFinancialTestFixtures.paidOrders(
                    reportDataPort, Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findRefunds(eq(thirtyDays), any()))
                    .thenReturn(Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findRefundsBySubOrderIds(any()))
                    .thenReturn(Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findMasterOrders(eq(thirtyDays)))
                    .thenReturn(Collections.emptyList());

            List<RevenueReportItem> result =
                    getRevenueReportUseCase.execute(thirtyDays, GroupByPeriod.DAY);

            assertThat(result).hasSize(30);

            for (int i = 0; i < 30; i++) {
                RevenueReportItem item = result.get(i);
                String expectedDate = String.format("2026-06-%02d", i + 1);

                assertThat(item.getPeriodKey()).isEqualTo(expectedDate);
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
        @DisplayName(
                "GetBookingReportUseCase: DB hoàn toàn trống vẫn trả về 30 ngày đầy đủ,"
                        + " completionRate = 0.0")
        void shouldZeroFillBookingReportForEntireMonthWithZeroOrders() {
            Mockito.lenient()
                    .when(reportDataPort.findSubOrders(eq(thirtyDays), any()))
                    .thenReturn(Collections.emptyList());
            ReportFinancialTestFixtures.paidOrders(
                    reportDataPort, Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findRefunds(eq(thirtyDays), any()))
                    .thenReturn(Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findRefundsBySubOrderIds(any()))
                    .thenReturn(Collections.emptyList());

            List<BookingReportItem> result =
                    getBookingReportUseCase.execute(thirtyDays, GroupByPeriod.DAY);

            assertThat(result).hasSize(30);

            for (int i = 0; i < 30; i++) {
                BookingReportItem item = result.get(i);
                String expectedDate = String.format("2026-06-%02d", i + 1);

                assertThat(item.getPeriodKey()).isEqualTo(expectedDate);
                assertThat(item.getTotalOrders()).isZero();
                assertThat(item.getCompletedOrders()).isZero();
                assertThat(item.getCancelledOrders()).isZero();
                assertThat(item.getCompletionRate()).isEqualTo(0.0);
                assertThat(item.getCancellationRate()).isEqualTo(0.0);
                assertThat(item.getCancellationReasonBreakdown()).isNotNull().isEmpty();
            }
        }

        @Test
        @DisplayName(
                "GetAdminDashboardUseCase: DB hoàn toàn trống không ném ngoại lệ, trả về metrics 0"
                        + " an toàn")
        void shouldReturnCleanZeroDashboardMetricsWhenDatabaseIsEmpty() {
            Mockito.lenient()
                    .when(reportDataPort.findSubOrders(any(), any()))
                    .thenReturn(Collections.emptyList());
            ReportFinancialTestFixtures.paidOrders(
                    reportDataPort, Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findRefunds(any(), any()))
                    .thenReturn(Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findRefundsBySubOrderIds(any()))
                    .thenReturn(Collections.emptyList());
            when(reportDataPort.findServiceSlots(any(), any())).thenReturn(Collections.emptyList());
            when(reportDataPort.findApprovedVendors()).thenReturn(Collections.emptyList());

            DashboardMetrics metrics = getAdminDashboardUseCase.execute(thirtyDays, null);

            assertThat(metrics).isNotNull();
            assertThat(metrics.getTotalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(metrics.getNetRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(metrics.getNewOrders()).isZero();
            assertThat(metrics.getCompletedOrders()).isZero();
            assertThat(metrics.getCancelledOrders()).isZero();
            assertThat(metrics.getCompletionRate()).isEqualTo(0.0);
            assertThat(metrics.getCancellationRate()).isEqualTo(0.0);
            assertThat(metrics.getAlerts()).isEmpty();
        }

        @Test
        @DisplayName(
                "GetVendorDashboardUseCase: DB hoàn toàn trống không ném ngoại lệ, trả về metrics 0"
                        + " an toàn")
        void shouldReturnCleanZeroVendorDashboardMetricsWhenDatabaseIsEmpty() {
            UUID vendorId = UUID.randomUUID();

            Mockito.lenient()
                    .when(reportDataPort.findSubOrders(any(), eq(vendorId)))
                    .thenReturn(Collections.emptyList());
            ReportFinancialTestFixtures.paidOrders(
                    reportDataPort, Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findRefunds(any(), eq(vendorId)))
                    .thenReturn(Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findRefundsBySubOrderIds(any()))
                    .thenReturn(Collections.emptyList());
            when(reportDataPort.findServiceSlots(any(), eq(vendorId)))
                    .thenReturn(Collections.emptyList());
            when(reportDataPort.findVendorById(vendorId)).thenReturn(Optional.empty());

            DashboardMetrics metrics = getVendorDashboardUseCase.execute(vendorId, thirtyDays);

            assertThat(metrics).isNotNull();
            assertThat(metrics.getTotalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(metrics.getNetRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(metrics.getNewOrders()).isZero();
            assertThat(metrics.getCompletedOrders()).isZero();
            assertThat(metrics.getCancelledOrders()).isZero();
            assertThat(metrics.getCompletionRate()).isEqualTo(0.0);
            assertThat(metrics.getCancellationRate()).isEqualTo(0.0);
            assertThat(metrics.getAlerts()).isEmpty();
        }

        @Test
        @DisplayName(
                "GetVendorPerformanceReportUseCase: Vendor tồn tại nhưng 0 đơn, 0 slot không bị lỗi"
                        + " chia cho 0")
        void shouldHandleVendorWithZeroActivityWithoutArithmeticException() {
            UUID vendorId = UUID.randomUUID();
            VendorRecord vendor =
                    new VendorRecord(
                            vendorId,
                            UUID.randomUUID(),
                            "Đối Tác Mới",
                            VerificationStatus.APPROVED,
                            BigDecimal.ZERO,
                            0,
                            BadgeTier.NONE);

            when(reportDataPort.findApprovedVendors()).thenReturn(List.of(vendor));
            Mockito.lenient()
                    .when(reportDataPort.findSubOrders(eq(thirtyDays), any()))
                    .thenReturn(Collections.emptyList());
            ReportFinancialTestFixtures.paidOrders(
                    reportDataPort, Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findRefunds(eq(thirtyDays), any()))
                    .thenReturn(Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findRefundsBySubOrderIds(any()))
                    .thenReturn(Collections.emptyList());
            when(reportDataPort.findServiceSlots(eq(thirtyDays), any()))
                    .thenReturn(Collections.emptyList());
            when(reportDataPort.findReviews(eq(thirtyDays), any()))
                    .thenReturn(Collections.emptyList());

            List<VendorPerformanceItem> results =
                    getVendorPerformanceReportUseCase.execute(thirtyDays);

            assertThat(results).hasSize(1);
            VendorPerformanceItem item = results.getFirst();
            assertThat(item.getBusinessName()).isEqualTo("Đối Tác Mới");
            assertThat(item.getTotalOrders()).isZero();
            assertThat(item.getRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.getNetPayout()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.getCancellationRate()).isEqualTo(0.0);
            assertThat(item.getSlotOccupancyRate()).isEqualTo(0.0);
            assertThat(item.getRatingCount()).isZero();
            assertThat(item.getIssues()).contains(VendorIssueType.UNDERPERFORMING);
            assertThat(item.getSeverity()).isEqualTo(VendorAlertSeverity.WARNING);
        }

        @Test
        @DisplayName(
                "ExportReportCsvUseCase: Xuất file CSV khi DB trống vẫn tạo file hợp lệ có UTF-8"
                        + " BOM và headers")
        void shouldExportValidEmptyCsvFilesForRevenueBookingsAndVendors() {
            Mockito.lenient()
                    .when(reportDataPort.findSubOrders(eq(thirtyDays), any()))
                    .thenReturn(Collections.emptyList());
            ReportFinancialTestFixtures.paidOrders(
                    reportDataPort, Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findRefunds(eq(thirtyDays), any()))
                    .thenReturn(Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findRefundsBySubOrderIds(any()))
                    .thenReturn(Collections.emptyList());
            Mockito.lenient()
                    .when(reportDataPort.findMasterOrders(eq(thirtyDays)))
                    .thenReturn(Collections.emptyList());
            when(reportDataPort.findApprovedVendors()).thenReturn(Collections.emptyList());

            // 1. Revenue CSV
            byte[] revCsv =
                    exportReportCsvUseCase.execute(
                            ReportType.REVENUE, thirtyDays, GroupByPeriod.DAY, null);
            assertThat(revCsv[0]).isEqualTo((byte) 0xEF);
            assertThat(revCsv[1]).isEqualTo((byte) 0xBB);
            assertThat(revCsv[2]).isEqualTo((byte) 0xBF);
            String revContent = new String(revCsv, 3, revCsv.length - 3, StandardCharsets.UTF_8);
            assertThat(revContent).contains("Kỳ báo cáo");
            assertThat(revContent).contains("2026-06-01");
            assertThat(revContent).contains("2026-06-30");

            // 2. Booking CSV
            byte[] bookCsv =
                    exportReportCsvUseCase.execute(
                            ReportType.BOOKINGS, thirtyDays, GroupByPeriod.DAY, null);
            assertThat(bookCsv[0]).isEqualTo((byte) 0xEF);
            String bookContent = new String(bookCsv, 3, bookCsv.length - 3, StandardCharsets.UTF_8);
            assertThat(bookContent).contains("Tổng số đơn");
            assertThat(bookContent).contains("2026-06-01");

            // 3. Vendors CSV
            byte[] venCsv =
                    exportReportCsvUseCase.execute(
                            ReportType.VENDORS, thirtyDays, GroupByPeriod.DAY, null);
            assertThat(venCsv[0]).isEqualTo((byte) 0xEF);
            String venContent = new String(venCsv, 3, venCsv.length - 3, StandardCharsets.UTF_8);
            assertThat(venContent).contains("Mã đối tác");
            assertThat(venContent).contains("Tên đối tác");
        }
    }

    // =========================================================================
    // 5. PHÒNG THỦ NGOẠI LỆ & DỮ LIỆU THIẾU SÓT (DEFENSIVE ROBUSTNESS)
    // =========================================================================
    @Nested
    @DisplayName("5. Defensive Robustness & Null Safety Stress Tests")
    class DefensiveRobustnessTests {

        @Test
        @DisplayName("Validation: from sau to phải ném IllegalArgumentException")
        void shouldThrowWhenFromIsAfterTo() {
            LocalDate from = LocalDate.of(2026, 10, 10);
            LocalDate to = LocalDate.of(2026, 10, 9);

            assertThatThrownBy(
                            () ->
                                    TimeSeriesPeriodHelper.generatePeriodKeys(
                                            from, to, GroupByPeriod.DAY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("From date cannot be after to date");
        }

        @Test
        @DisplayName("Validation: from hoặc to null phải ném NullPointerException")
        void shouldThrowWhenFromOrToIsNull() {
            LocalDate validDate = LocalDate.of(2026, 10, 10);

            assertThatThrownBy(
                            () ->
                                    TimeSeriesPeriodHelper.generatePeriodKeys(
                                            null, validDate, GroupByPeriod.DAY))
                    .isInstanceOf(NullPointerException.class);

            assertThatThrownBy(
                            () ->
                                    TimeSeriesPeriodHelper.generatePeriodKeys(
                                            validDate, null, GroupByPeriod.DAY))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Defaulting: groupBy == null tự động fallback về DAY")
        void shouldFallbackToDayWhenGroupByIsNull() {
            LocalDate date = LocalDate.of(2026, 10, 10);
            List<String> keys = TimeSeriesPeriodHelper.generatePeriodKeys(date, date, null);
            assertThat(keys).containsExactly("2026-10-10");
        }

        @Test
        @DisplayName(
                "Null safety: SubOrder và Refund có trường số tiền null không gây"
                        + " NullPointerException")
        void shouldNotThrowNpeWhenAmountsAreNull() {
            LocalDate day = LocalDate.of(2026, 7, 1);
            TimeRange range = TimeRange.of(day, day);
            OffsetDateTime dt = day.atTime(10, 0).atZone(vnZone).toOffsetDateTime();

            SubOrderRecord orderWithNulls =
                    new SubOrderRecord(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            1,
                            null,
                            null,
                            null,
                            null,
                            null,
                            SubOrderStatus.COMPLETED,
                            dt,
                            dt);

            RefundRecord refundWithNulls =
                    new RefundRecord(
                            UUID.randomUUID(),
                            orderWithNulls.id(),
                            UUID.randomUUID(),
                            null,
                            null,
                            RefundReason.CUSTOMER_CANCEL,
                            RefundStatus.PROCESSED,
                            null,
                            dt);

            MasterOrderRecord masterWithNulls =
                    new MasterOrderRecord(
                            orderWithNulls.masterOrderId(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            null,
                            null,
                            null,
                            null,
                            PaymentOrderStatus.PAID,
                            dt,
                            dt);

            Mockito.lenient()
                    .when(reportDataPort.findSubOrders(eq(range), any()))
                    .thenReturn(List.of(orderWithNulls));
            ReportFinancialTestFixtures.paidOrders(
                    reportDataPort, List.of(orderWithNulls));
            Mockito.lenient()
                    .when(reportDataPort.findRefunds(eq(range), any()))
                    .thenReturn(List.of(refundWithNulls));
            Mockito.lenient()
                    .when(reportDataPort.findRefundsBySubOrderIds(any()))
                    .thenReturn(List.of(refundWithNulls));
            Mockito.lenient()
                    .when(reportDataPort.findMasterOrders(eq(range)))
                    .thenReturn(List.of(masterWithNulls));

            List<RevenueReportItem> result =
                    getRevenueReportUseCase.execute(range, GroupByPeriod.DAY);

            assertThat(result).hasSize(1);
            RevenueReportItem item = result.getFirst();
            assertThat(item.getGmv()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.getPlatformCommission()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.getNetVendorPayout()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.getOrderCount()).isEqualTo(1);
        }
    }
}
