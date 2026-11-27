package com.danasea.backend.modules.report.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.report.application.ports.RefundRecord;
import com.danasea.backend.modules.report.application.ports.ReportDataPort;
import com.danasea.backend.modules.report.application.ports.SubOrderRecord;
import com.danasea.backend.modules.report.domain.enums.GroupByPeriod;
import com.danasea.backend.modules.report.domain.models.BookingReportItem;
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
@DisplayName("GetBookingReportUseCase Booking Breakdown & Zero-Filling Tests")
class GetBookingReportUseCaseTest {

    @Mock private ReportDataPort reportDataPort;

    private GetBookingReportUseCase useCase;
    private final ZoneId zone = TimeRange.VIETNAM_ZONE;

    @BeforeEach
    void setUp() {
        useCase = new GetBookingReportUseCase(reportDataPort);
    }

    @Test
    @DisplayName("Zero-filling các chu kỳ không có đơn đặt nào")
    void shouldZeroFillEmptyPeriodsForBookings() {
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

        List<BookingReportItem> result = useCase.execute(timeRange, GroupByPeriod.DAY);

        assertThat(result).hasSize(3);
        for (BookingReportItem item : result) {
            assertThat(item.getTotalOrders()).isZero();
            assertThat(item.getCompletedOrders()).isZero();
            assertThat(item.getCancelledOrders()).isZero();
            assertThat(item.getCompletionRate()).isEqualTo(0.0);
            assertThat(item.getCancellationRate()).isEqualTo(0.0);
            assertThat(item.getCancellationReasonBreakdown()).isEmpty();
        }
    }

    @Test
    @DisplayName("Thống kê số lượng đơn và phân rã chính xác theo từng RefundReason")
    void shouldBreakdownCancellationsByReason() {
        TimeRange timeRange = TimeRange.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1));
        OffsetDateTime dt = LocalDate.of(2026, 10, 1).atTime(9, 0).atZone(zone).toOffsetDateTime();

        // 3 đơn: 1 COMPLETED, 1 CANCELLED (WEATHER), 1 CANCELLED (CUSTOMER_CANCEL)
        SubOrderRecord o1 =
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
                        dt,
                        dt);
        SubOrderRecord o2 =
                new SubOrderRecord(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
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
                        dt);
        SubOrderRecord o3 =
                new SubOrderRecord(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
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
                        dt);

        RefundRecord r1 =
                new RefundRecord(
                        UUID.randomUUID(),
                        o2.id(),
                        UUID.randomUUID(),
                        BigDecimal.valueOf(500_000),
                        BigDecimal.valueOf(1.0),
                        RefundReason.WEATHER,
                        RefundStatus.PROCESSED,
                        dt,
                        dt);
        RefundRecord r2 =
                new RefundRecord(
                        UUID.randomUUID(),
                        o3.id(),
                        UUID.randomUUID(),
                        BigDecimal.valueOf(350_000),
                        BigDecimal.valueOf(0.7),
                        RefundReason.CUSTOMER_CANCEL,
                        RefundStatus.PROCESSED,
                        dt,
                        dt);

        Mockito.lenient()
                .when(reportDataPort.findSubOrders(eq(timeRange), any()))
                .thenReturn(List.of(o1, o2, o3));
        ReportFinancialTestFixtures.paidOrders(
                reportDataPort, List.of(o1, o2, o3));
        Mockito.lenient()
                .when(reportDataPort.findRefunds(eq(timeRange), any()))
                .thenReturn(List.of(r1, r2));
        Mockito.lenient()
                .when(reportDataPort.findRefundsBySubOrderIds(any()))
                .thenReturn(List.of(r1, r2));

        List<BookingReportItem> result = useCase.execute(timeRange, GroupByPeriod.DAY);

        assertThat(result).hasSize(1);
        BookingReportItem item = result.getFirst();
        assertThat(item.getTotalOrders()).isEqualTo(3);
        assertThat(item.getCompletedOrders()).isEqualTo(1);
        assertThat(item.getCancelledOrders()).isEqualTo(2);

        // Completion Rate: 1 / 3 = 33.33%
        assertThat(item.getCompletionRate()).isEqualTo(33.33);
        // Cancellation Rate: 2 / 3 = 66.67%
        assertThat(item.getCancellationRate()).isEqualTo(66.67);

        // Breakdown: 1 WEATHER, 1 CUSTOMER_CANCEL
        assertThat(item.getCancellationCount(RefundReason.WEATHER)).isEqualTo(1);
        assertThat(item.getCancellationCount(RefundReason.CUSTOMER_CANCEL)).isEqualTo(1);
        assertThat(item.getCancellationCount(RefundReason.VENDOR_FAULT)).isZero();
    }
}
