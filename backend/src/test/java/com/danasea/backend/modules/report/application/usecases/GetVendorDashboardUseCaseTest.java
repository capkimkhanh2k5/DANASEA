package com.danasea.backend.modules.report.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.report.application.ports.RefundRecord;
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
import java.util.Optional;
import java.util.UUID;
import com.danasea.backend.modules.report.ReportFinancialTestFixtures;
import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetVendorDashboardUseCase Real-time Metrics & Isolation Tests")
class GetVendorDashboardUseCaseTest {

    @Mock private ReportDataPort reportDataPort;

    private GetVendorDashboardUseCase useCase;
    private final ZoneId zone = TimeRange.VIETNAM_ZONE;
    private TimeRange timeRange;
    private UUID vendorId;

    @BeforeEach
    void setUp() {
        useCase = new GetVendorDashboardUseCase(reportDataPort);
        timeRange = TimeRange.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8));
        vendorId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Tổng hợp chỉ số hoạt động thời gian thực của Vendor kèm tính toán Net Payout")
    void shouldAggregateVendorMetricsAccurately() {
        OffsetDateTime dt = LocalDate.of(2026, 10, 3).atTime(10, 0).atZone(zone).toOffsetDateTime();
        VendorRecord vendor =
                new VendorRecord(
                        vendorId,
                        UUID.randomUUID(),
                        "Nhà xe Đà Nẵng",
                        VerificationStatus.APPROVED,
                        BigDecimal.valueOf(4.5),
                        15,
                        BadgeTier.TOP_RATED);

        // Đơn 1: GMV 1,000,000, hoa hồng 100,000, hoàn tiền 0 -> thực nhận 900,000
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
        // Đơn 2: GMV 500,000, hủy đơn có hoàn tiền 500,000
        SubOrderRecord o2 =
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

        RefundRecord refund =
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

        when(reportDataPort.findVendorById(vendorId)).thenReturn(Optional.of(vendor));
        Mockito.lenient()
                .when(reportDataPort.findSubOrders(eq(timeRange), eq(vendorId)))
                .thenReturn(List.of(o1, o2));
        ReportFinancialTestFixtures.paidOrders(
                reportDataPort, List.of(o1, o2));
        Mockito.lenient()
                .when(reportDataPort.findRefunds(eq(timeRange), eq(vendorId)))
                .thenReturn(List.of(refund));
        Mockito.lenient()
                .when(reportDataPort.findRefundsBySubOrderIds(any()))
                .thenReturn(List.of(refund));
        when(reportDataPort.findServiceSlots(eq(timeRange), eq(vendorId)))
                .thenReturn(Collections.emptyList());

        DashboardMetrics metrics = useCase.execute(vendorId, timeRange);

        // GMV = 1.5M
        assertThat(metrics.getTotalRevenue()).isEqualByComparingTo("1500000");
        // Net vendor payout = GMV (1.5M) - Commission (100k) - Refund (500k) = 900,000
        assertThat(metrics.getNetRevenue()).isEqualByComparingTo("900000");
        assertThat(metrics.getNewOrders()).isEqualTo(2);
        assertThat(metrics.getCompletedOrders()).isEqualTo(1);
        assertThat(metrics.getCancelledOrders()).isEqualTo(1);
        assertThat(metrics.getCompletionRate()).isEqualTo(50.0);
        assertThat(metrics.getCancellationRate()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("Kích hoạt cảnh báo quá tải slot khi occupancy rate >= 95%")
    void shouldTriggerOverloadedAlertForVendor() {
        VendorRecord vendor =
                new VendorRecord(
                        vendorId,
                        UUID.randomUUID(),
                        "Lặn San Hô",
                        VerificationStatus.APPROVED,
                        BigDecimal.valueOf(4.7),
                        20,
                        BadgeTier.TOP_RATED);
        ServiceSlotRecord slot =
                new ServiceSlotRecord(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        vendorId,
                        LocalDate.of(2026, 10, 4),
                        LocalTime.of(8, 0),
                        LocalTime.of(10, 0),
                        50,
                        49,
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

        DashboardMetrics metrics = useCase.execute(vendorId, timeRange);

        assertThat(metrics.getAlerts()).isNotEmpty();
        assertThat(metrics.getAlerts())
                .extracting(VendorDiagnosisAlert::getIssueType)
                .contains(VendorIssueType.OVERLOADED);
    }

    @Test
    @DisplayName("Ném NullPointerException nếu vendorId là null")
    void shouldThrowExceptionWhenVendorIdIsNull() {
        assertThatThrownBy(() -> useCase.execute(null, timeRange))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("vendorId cannot be null");
    }
}
