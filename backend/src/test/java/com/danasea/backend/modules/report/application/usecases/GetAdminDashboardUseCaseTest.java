package com.danasea.backend.modules.report.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.report.application.ports.ReportDataPort;
import com.danasea.backend.modules.report.application.ports.ServiceSlotRecord;
import com.danasea.backend.modules.report.application.ports.SubOrderRecord;
import com.danasea.backend.modules.report.application.ports.VendorRecord;
import com.danasea.backend.modules.report.domain.enums.VendorIssueType;
import com.danasea.backend.modules.report.domain.models.DashboardMetrics;
import com.danasea.backend.modules.report.domain.models.TimeRange;
import com.danasea.backend.modules.report.domain.models.VendorDiagnosisAlert;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import com.danasea.backend.modules.report.ReportFinancialTestFixtures;
import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetAdminDashboardUseCase Platform Analytics & Alert Tests")
class GetAdminDashboardUseCaseTest {

    @Mock private ReportDataPort reportDataPort;

    private GetAdminDashboardUseCase useCase;
    private final ZoneId zone = TimeRange.VIETNAM_ZONE;
    private TimeRange timeRange;

    @BeforeEach
    void setUp() {
        useCase = new GetAdminDashboardUseCase(reportDataPort);
        timeRange = TimeRange.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8));
    }

    @Test
    @DisplayName("Tổng hợp chỉ số toàn sàn thời gian thực chính xác")
    void shouldAggregatePlatformRealtimeMetrics() {
        UUID vendorId = UUID.randomUUID();
        OffsetDateTime dt = LocalDate.of(2026, 10, 5).atTime(12, 0).atZone(zone).toOffsetDateTime();

        // 2 đơn hoàn tất, 1 đơn hủy
        SubOrderRecord o1 =
                new SubOrderRecord(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        vendorId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        BigDecimal.valueOf(1_000_000),
                        BigDecimal.valueOf(1_000_000),
                        BigDecimal.valueOf(0.10),
                        BigDecimal.valueOf(100_000),
                        BigDecimal.valueOf(900_000),
                        SubOrderStatus.COMPLETED,
                        dt,
                        dt);
        SubOrderRecord o2 =
                new SubOrderRecord(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        vendorId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        BigDecimal.valueOf(2_000_000),
                        BigDecimal.valueOf(2_000_000),
                        BigDecimal.valueOf(0.10),
                        BigDecimal.valueOf(200_000),
                        BigDecimal.valueOf(1_800_000),
                        SubOrderStatus.COMPLETED,
                        dt,
                        dt);
        SubOrderRecord o3 =
                new SubOrderRecord(
                        UUID.randomUUID(),
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
                        dt);

        Mockito.lenient()
                .when(reportDataPort.findSubOrders(eq(timeRange), eq(null)))
                .thenReturn(List.of(o1, o2, o3));
        ReportFinancialTestFixtures.paidOrders(
                reportDataPort, List.of(o1, o2, o3));
        Mockito.lenient()
                .when(reportDataPort.findRefunds(eq(timeRange), eq(null)))
                .thenReturn(Collections.emptyList());
        Mockito.lenient()
                .when(reportDataPort.findRefundsBySubOrderIds(any()))
                .thenReturn(Collections.emptyList());
        when(reportDataPort.findServiceSlots(eq(timeRange), eq(null)))
                .thenReturn(Collections.emptyList());
        when(reportDataPort.findApprovedVendors()).thenReturn(Collections.emptyList());

        DashboardMetrics metrics = useCase.execute(timeRange, null);

        // GMV = 1M + 2M + 500k = 3.5M
        assertThat(metrics.getTotalRevenue()).isEqualByComparingTo("3500000");
        // Net revenue sàn = Platform commission (100k + 200k = 300k)
        assertThat(metrics.getNetRevenue()).isEqualByComparingTo("300000");
        assertThat(metrics.getNewOrders()).isEqualTo(3);
        assertThat(metrics.getCompletedOrders()).isEqualTo(2);
        assertThat(metrics.getCancelledOrders()).isEqualTo(1);
        assertThat(metrics.getCompletionRate()).isEqualTo(66.67);
        assertThat(metrics.getCancellationRate()).isEqualTo(33.33);
    }

    @Test
    @DisplayName("Cảnh báo bất thường khi đơn vị bị quá tải slot hoặc tỷ lệ hủy cao")
    void shouldTriggerAnomalousAlertsForAdmin() {
        UUID v1 = UUID.randomUUID();
        UUID v2 = UUID.randomUUID();

        VendorRecord rec1 =
                new VendorRecord(
                        v1,
                        UUID.randomUUID(),
                        "Vendor Hủy Nhiều",
                        VerificationStatus.APPROVED,
                        BigDecimal.valueOf(4.0),
                        10,
                        BadgeTier.VERIFIED);
        VendorRecord rec2 =
                new VendorRecord(
                        v2,
                        UUID.randomUUID(),
                        "Vendor Quá Tải",
                        VerificationStatus.APPROVED,
                        BigDecimal.valueOf(4.9),
                        50,
                        BadgeTier.TOP_RATED);

        OffsetDateTime dt = LocalDate.of(2026, 10, 5).atTime(12, 0).atZone(zone).toOffsetDateTime();
        // v1 có 4 đơn, trong đó 2 đơn hủy (50% > 20% và > 40% -> CRITICAL)
        List<SubOrderRecord> orders =
                List.of(
                        new SubOrderRecord(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                v1,
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                1,
                                BigDecimal.valueOf(100),
                                BigDecimal.valueOf(100),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                SubOrderStatus.COMPLETED,
                                dt,
                                dt),
                        new SubOrderRecord(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                v1,
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                1,
                                BigDecimal.valueOf(100),
                                BigDecimal.valueOf(100),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                SubOrderStatus.COMPLETED,
                                dt,
                                dt),
                        new SubOrderRecord(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                v1,
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                1,
                                BigDecimal.valueOf(100),
                                BigDecimal.valueOf(100),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                SubOrderStatus.CANCELLED,
                                dt,
                                dt),
                        new SubOrderRecord(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                v1,
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                1,
                                BigDecimal.valueOf(100),
                                BigDecimal.valueOf(100),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                SubOrderStatus.CANCELLED,
                                dt,
                                dt));

        // v2 có slot đạt 98% công suất
        ServiceSlotRecord slot =
                new ServiceSlotRecord(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        v2,
                        LocalDate.of(2026, 10, 5),
                        LocalTime.of(14, 0),
                        LocalTime.of(16, 0),
                        100,
                        98,
                        SlotStatus.OPEN);

        Mockito.lenient()
                .when(reportDataPort.findSubOrders(eq(timeRange), eq(null)))
                .thenReturn(orders);
        ReportFinancialTestFixtures.paidOrders(
                reportDataPort, orders);
        Mockito.lenient()
                .when(reportDataPort.findRefunds(eq(timeRange), eq(null)))
                .thenReturn(Collections.emptyList());
        Mockito.lenient()
                .when(reportDataPort.findRefundsBySubOrderIds(any()))
                .thenReturn(Collections.emptyList());
        when(reportDataPort.findServiceSlots(eq(timeRange), eq(null))).thenReturn(List.of(slot));
        when(reportDataPort.findApprovedVendors()).thenReturn(List.of(rec1, rec2));

        DashboardMetrics metrics = useCase.execute(timeRange, null);

        assertThat(metrics.getAlerts()).isNotEmpty();
        assertThat(metrics.getAlerts())
                .extracting(VendorDiagnosisAlert::getIssueType)
                .contains(VendorIssueType.HIGH_CANCELLATION, VendorIssueType.OVERLOADED);
    }

    @Test
    @DisplayName("An toàn khi toàn sàn không có đơn nào (không lỗi chia cho 0)")
    void shouldHandleEmptySystemWithoutExceptions() {
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
        when(reportDataPort.findServiceSlots(eq(timeRange), any()))
                .thenReturn(Collections.emptyList());
        when(reportDataPort.findApprovedVendors()).thenReturn(Collections.emptyList());

        DashboardMetrics metrics = useCase.execute(timeRange, null);

        assertThat(metrics.getTotalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(metrics.getNetRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(metrics.getNewOrders()).isZero();
        assertThat(metrics.getCompletionRate()).isEqualTo(0.0);
        assertThat(metrics.getCancellationRate()).isEqualTo(0.0);
        assertThat(metrics.getAlerts()).isEmpty();
    }
}
