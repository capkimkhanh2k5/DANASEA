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
import com.danasea.backend.modules.report.domain.enums.VendorAlertSeverity;
import com.danasea.backend.modules.report.domain.enums.VendorIssueType;
import com.danasea.backend.modules.report.domain.models.TimeRange;
import com.danasea.backend.modules.report.domain.models.VendorPerformanceItem;
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
import java.util.Optional;
import java.util.UUID;
import com.danasea.backend.modules.report.ReportFinancialTestFixtures;
import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetVendorPerformanceReportUseCase Performance & Diagnostics Tests")
class GetVendorPerformanceReportUseCaseTest {

    @Mock private ReportDataPort reportDataPort;

    private GetVendorPerformanceReportUseCase useCase;
    private final ZoneId zone = TimeRange.VIETNAM_ZONE;
    private TimeRange timeRange;

    @BeforeEach
    void setUp() {
        useCase = new GetVendorPerformanceReportUseCase(reportDataPort);
        timeRange = TimeRange.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7));
    }

    @Test
    @DisplayName(
            "Phân tích hiệu suất đối tác bình thường (HEALTHY) với tính toán lấp đầy slot và doanh"
                    + " thu")
    void shouldEvaluateHealthyVendorPerformance() {
        UUID vendorId = UUID.randomUUID();
        VendorRecord vendor =
                new VendorRecord(
                        vendorId,
                        UUID.randomUUID(),
                        "Dịch vụ Ca Nô Đà Nẵng",
                        VerificationStatus.APPROVED,
                        BigDecimal.valueOf(4.8),
                        20,
                        BadgeTier.TOP_RATED);

        OffsetDateTime dt = LocalDate.of(2026, 10, 2).atTime(10, 0).atZone(zone).toOffsetDateTime();
        SubOrderRecord o1 =
                new SubOrderRecord(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        vendorId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        5,
                        BigDecimal.valueOf(1_000_000),
                        BigDecimal.valueOf(5_000_000),
                        BigDecimal.valueOf(0.10),
                        BigDecimal.valueOf(500_000),
                        BigDecimal.valueOf(4_500_000),
                        SubOrderStatus.COMPLETED,
                        dt,
                        dt);

        ServiceSlotRecord slot =
                new ServiceSlotRecord(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        vendorId,
                        LocalDate.of(2026, 10, 2),
                        LocalTime.of(8, 0),
                        LocalTime.of(10, 0),
                        10,
                        8,
                        SlotStatus.OPEN);

        when(reportDataPort.findApprovedVendors()).thenReturn(List.of(vendor));
        Mockito.lenient()
                .when(reportDataPort.findSubOrders(eq(timeRange), eq(null)))
                .thenReturn(List.of(o1));
        ReportFinancialTestFixtures.paidOrders(
                reportDataPort, List.of(o1));
        Mockito.lenient()
                .when(reportDataPort.findRefunds(eq(timeRange), eq(null)))
                .thenReturn(Collections.emptyList());
        Mockito.lenient()
                .when(reportDataPort.findRefundsBySubOrderIds(any()))
                .thenReturn(Collections.emptyList());
        when(reportDataPort.findServiceSlots(eq(timeRange), eq(null))).thenReturn(List.of(slot));
        when(reportDataPort.findReviews(eq(timeRange), eq(null)))
                .thenReturn(Collections.emptyList());

        List<VendorPerformanceItem> results = useCase.execute(timeRange, null);

        assertThat(results).hasSize(1);
        VendorPerformanceItem item = results.getFirst();
        assertThat(item.getVendorId()).isEqualTo(vendorId);
        assertThat(item.getBusinessName()).isEqualTo("Dịch vụ Ca Nô Đà Nẵng");
        assertThat(item.getRevenue()).isEqualByComparingTo("5000000");
        assertThat(item.getNetPayout()).isEqualByComparingTo("4500000");
        assertThat(item.getTotalOrders()).isEqualTo(1);
        assertThat(item.getCompletedOrders()).isEqualTo(1);
        assertThat(item.getCancelledOrders()).isZero();
        assertThat(item.getCancellationRate()).isEqualTo(0.0);
        // Slot occupancy: 8 / 10 = 80.0%
        assertThat(item.getSlotOccupancyRate()).isEqualTo(80.0);
        assertThat(item.getSeverity()).isEqualTo(VendorAlertSeverity.HEALTHY);
        assertThat(item.getIssues()).isEmpty();
    }

    @Test
    @DisplayName("Chẩn đoán phát hiện cảnh báo quá tải OVERLOADED khi tỷ lệ lấp đầy >= 95%")
    void shouldDetectOverloadedSlotAlert() {
        UUID vendorId = UUID.randomUUID();
        VendorRecord vendor =
                new VendorRecord(
                        vendorId,
                        UUID.randomUUID(),
                        "Lặn Biển Sơn Trà",
                        VerificationStatus.APPROVED,
                        BigDecimal.valueOf(4.5),
                        10,
                        BadgeTier.VERIFIED);

        ServiceSlotRecord slot =
                new ServiceSlotRecord(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        vendorId,
                        LocalDate.of(2026, 10, 3),
                        LocalTime.of(8, 0),
                        LocalTime.of(10, 0),
                        100,
                        96,
                        SlotStatus.OPEN);

        when(reportDataPort.findVendorById(vendorId)).thenReturn(Optional.of(vendor));
        Mockito.lenient()
                .when(reportDataPort.findSubOrders(eq(timeRange), eq(vendorId)))
                .thenReturn(Collections.emptyList());
        ReportFinancialTestFixtures.paidOrders(
                reportDataPort, Collections.emptyList());
        Mockito.lenient()
                .when(reportDataPort.findRefunds(eq(timeRange), eq(vendorId)))
                .thenReturn(Collections.emptyList());
        Mockito.lenient()
                .when(reportDataPort.findRefundsBySubOrderIds(any()))
                .thenReturn(Collections.emptyList());
        when(reportDataPort.findServiceSlots(eq(timeRange), eq(vendorId)))
                .thenReturn(List.of(slot));
        when(reportDataPort.findReviews(eq(timeRange), eq(vendorId)))
                .thenReturn(Collections.emptyList());

        List<VendorPerformanceItem> results = useCase.execute(timeRange, vendorId);

        assertThat(results).hasSize(1);
        VendorPerformanceItem item = results.getFirst();
        assertThat(item.getSlotOccupancyRate()).isEqualTo(96.0);
        assertThat(item.getIssues()).contains(VendorIssueType.OVERLOADED);
        assertThat(item.getSeverity()).isEqualTo(VendorAlertSeverity.WARNING);
    }

    @Test
    @DisplayName("Chẩn đoán phát hiện CRITICAL khi tỷ lệ hủy > 40%")
    void shouldDetectCriticalHighCancellation() {
        UUID vendorId = UUID.randomUUID();
        VendorRecord vendor =
                new VendorRecord(
                        vendorId,
                        UUID.randomUUID(),
                        "Tour Câu Mực",
                        VerificationStatus.APPROVED,
                        BigDecimal.valueOf(4.0),
                        10,
                        BadgeTier.NONE);

        OffsetDateTime dt = LocalDate.of(2026, 10, 4).atTime(18, 0).atZone(zone).toOffsetDateTime();
        SubOrderRecord o1 =
                new SubOrderRecord(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        vendorId,
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
                        dt);
        SubOrderRecord o2 =
                new SubOrderRecord(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        vendorId,
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
                        dt);

        when(reportDataPort.findVendorById(vendorId)).thenReturn(Optional.of(vendor));
        Mockito.lenient()
                .when(reportDataPort.findSubOrders(eq(timeRange), eq(vendorId)))
                .thenReturn(List.of(o1, o2));
        ReportFinancialTestFixtures.paidOrders(
                reportDataPort, List.of(o1, o2));
        Mockito.lenient()
                .when(reportDataPort.findRefunds(eq(timeRange), eq(vendorId)))
                .thenReturn(Collections.emptyList());
        Mockito.lenient()
                .when(reportDataPort.findRefundsBySubOrderIds(any()))
                .thenReturn(Collections.emptyList());
        when(reportDataPort.findServiceSlots(eq(timeRange), eq(vendorId)))
                .thenReturn(Collections.emptyList());
        when(reportDataPort.findReviews(eq(timeRange), eq(vendorId)))
                .thenReturn(Collections.emptyList());

        List<VendorPerformanceItem> results = useCase.execute(timeRange, vendorId);

        assertThat(results).hasSize(1);
        VendorPerformanceItem item = results.getFirst();
        // Cancellation Rate: 1 / 2 = 50.0% (> 40% -> CRITICAL)
        assertThat(item.getCancellationRate()).isEqualTo(50.0);
        assertThat(item.getIssues()).contains(VendorIssueType.HIGH_CANCELLATION);
        assertThat(item.getSeverity()).isEqualTo(VendorAlertSeverity.CRITICAL);
    }

    @Test
    @DisplayName("An toàn không chia cho 0 khi công suất slot = 0")
    void shouldHandleZeroSlotCapacitySafely() {
        UUID vendorId = UUID.randomUUID();
        VendorRecord vendor =
                new VendorRecord(
                        vendorId,
                        UUID.randomUUID(),
                        "Vendor Mới",
                        VerificationStatus.APPROVED,
                        BigDecimal.ZERO,
                        0,
                        BadgeTier.NONE);

        when(reportDataPort.findVendorById(vendorId)).thenReturn(Optional.of(vendor));
        Mockito.lenient()
                .when(reportDataPort.findSubOrders(eq(timeRange), eq(vendorId)))
                .thenReturn(Collections.emptyList());
        ReportFinancialTestFixtures.paidOrders(
                reportDataPort, Collections.emptyList());
        Mockito.lenient()
                .when(reportDataPort.findRefunds(eq(timeRange), eq(vendorId)))
                .thenReturn(Collections.emptyList());
        Mockito.lenient()
                .when(reportDataPort.findRefundsBySubOrderIds(any()))
                .thenReturn(Collections.emptyList());
        when(reportDataPort.findServiceSlots(eq(timeRange), eq(vendorId)))
                .thenReturn(Collections.emptyList());
        when(reportDataPort.findReviews(eq(timeRange), eq(vendorId)))
                .thenReturn(Collections.emptyList());

        List<VendorPerformanceItem> results = useCase.execute(timeRange, vendorId);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getSlotOccupancyRate()).isEqualTo(0.0);
    }
}
