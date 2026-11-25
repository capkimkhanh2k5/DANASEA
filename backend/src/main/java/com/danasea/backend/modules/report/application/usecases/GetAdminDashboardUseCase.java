package com.danasea.backend.modules.report.application.usecases;

import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.report.application.ports.ReportDataPort;
import com.danasea.backend.modules.report.application.ports.ServiceSlotRecord;
import com.danasea.backend.modules.report.application.ports.SubOrderRecord;
import com.danasea.backend.modules.report.application.ports.VendorRecord;
import com.danasea.backend.modules.report.domain.enums.VendorAlertSeverity;
import com.danasea.backend.modules.report.domain.enums.VendorIssueType;
import com.danasea.backend.modules.report.domain.models.DashboardMetrics;
import com.danasea.backend.modules.report.domain.models.TimeRange;
import com.danasea.backend.modules.report.domain.models.VendorDiagnosisAlert;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * UseCase tổng hợp chỉ số hoạt động toàn sàn thời gian thực cho Admin (Admin Dashboard Metrics).
 *
 * <p>Tính toán: - Tổng doanh thu toàn sàn (GMV), Doanh thu thực nhận của sàn (Platform Commission).
 * - Số lượng đơn mới, đơn hoàn tất, đơn hủy. - Tỷ lệ hoàn tất và tỷ lệ hủy toàn hệ thống. - Phát
 * hiện và cảnh báo bất thường: tỷ lệ hủy cao bất thường toàn sàn hoặc theo từng vendor, đơn vị quá
 * tải công suất slot (>= 95%). - Hỗ trợ lọc theo vendorId nếu Admin muốn xem riêng một đơn vị cụ
 * thể.
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class GetAdminDashboardUseCase {

    private final ReportDataPort reportDataPort;

    public GetAdminDashboardUseCase(ReportDataPort reportDataPort) {
        this.reportDataPort =
                Objects.requireNonNull(reportDataPort, "reportDataPort cannot be null");
    }

    /**
     * Lấy chỉ số Dashboard của Admin trong khoảng thời gian TimeRange (có hỗ trợ lọc theo
     * vendorId).
     *
     * @param timeRange khoảng thời gian lọc (nếu null: mặc định từ đầu tháng hiện tại đến hôm nay)
     * @param vendorId ID của vendor (nếu null: lấy toàn bộ hệ thống)
     * @return DashboardMetrics tổng quan kèm danh sách cảnh báo bất thường
     */
    public DashboardMetrics execute(TimeRange timeRange, UUID vendorId) {
        TimeRange effectiveRange = timeRange != null ? timeRange : defaultTimeRange();

        // 1. Trích xuất dữ liệu vận hành
        List<SubOrderRecord> subOrders = reportDataPort.findSubOrders(effectiveRange, vendorId);
        List<ServiceSlotRecord> slots = reportDataPort.findServiceSlots(effectiveRange, vendorId);
        List<VendorRecord> vendors = reportDataPort.findApprovedVendors();

        Map<UUID, VendorRecord> vendorMap =
                vendors.stream()
                        .filter(v -> v.id() != null)
                        .collect(Collectors.toMap(VendorRecord::id, v -> v, (v1, v2) -> v1));

        Map<UUID, RefundReason> cancellationReasons =
                BookingCancellationClassifier.reasons(reportDataPort, subOrders);
        // 2. Thống kê số lượng đơn
        long newOrders = subOrders.size();
        long completedOrders =
                subOrders.stream().filter(s -> s.status() == SubOrderStatus.COMPLETED).count();
        long cancelledOrders =
                subOrders.stream()
                        .filter(
                                s ->
                                        BookingCancellationClassifier.isCancelled(
                                                s, cancellationReasons))
                        .count();

        List<ReportFinancialLedger.Entry> financialEntries =
                ReportFinancialLedger.load(reportDataPort, effectiveRange, vendorId);
        BigDecimal totalRevenue =
                financialEntries.stream()
                        .map(ReportFinancialLedger.Entry::gmv)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netRevenue =
                financialEntries.stream()
                        .map(e -> vendorId == null ? e.commission() : e.payout())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. Phát hiện cảnh báo bất thường
        List<VendorDiagnosisAlert> alerts = new ArrayList<>();

        // Cảnh báo hủy đơn toàn sàn
        if (newOrders > 0) {
            double platformCancellationRate = (double) cancelledOrders / newOrders * 100.0;
            if (platformCancellationRate > 20.0) {
                VendorAlertSeverity severity =
                        platformCancellationRate > 40.0
                                ? VendorAlertSeverity.CRITICAL
                                : VendorAlertSeverity.WARNING;
                alerts.add(
                        new VendorDiagnosisAlert(
                                vendorId,
                                vendorId != null
                                        ? vendorMap
                                                .getOrDefault(
                                                        vendorId,
                                                        new VendorRecord(
                                                                vendorId, null, "Vendor", null,
                                                                null, null, null))
                                                .businessName()
                                        : "Toàn sàn",
                                VendorIssueType.HIGH_CANCELLATION,
                                severity,
                                String.format(
                                        "Tỷ lệ hủy đơn cao bất thường: %.2f%%",
                                        platformCancellationRate),
                                platformCancellationRate));
            }
        }

        // Cảnh báo theo từng Vendor nếu xem toàn sàn
        if (vendorId == null) {
            Map<UUID, List<SubOrderRecord>> subOrdersByVendor =
                    subOrders.stream()
                            .filter(s -> s.vendorId() != null)
                            .collect(Collectors.groupingBy(SubOrderRecord::vendorId));

            for (Map.Entry<UUID, List<SubOrderRecord>> entry : subOrdersByVendor.entrySet()) {
                UUID vId = entry.getKey();
                List<SubOrderRecord> vOrders = entry.getValue();
                if (vOrders.size() >= 3) {
                    long vCancelled =
                            vOrders.stream()
                                    .filter(
                                            s ->
                                                    BookingCancellationClassifier.isCancelled(
                                                            s, cancellationReasons))
                                    .count();
                    double vRate = (double) vCancelled / vOrders.size() * 100.0;
                    if (vRate > 20.0) {
                        VendorRecord vRec = vendorMap.get(vId);
                        String name = vRec != null ? vRec.businessName() : "Đối tác " + vId;
                        VendorAlertSeverity severity =
                                vRate > 40.0
                                        ? VendorAlertSeverity.CRITICAL
                                        : VendorAlertSeverity.WARNING;
                        alerts.add(
                                new VendorDiagnosisAlert(
                                        vId,
                                        name,
                                        VendorIssueType.HIGH_CANCELLATION,
                                        severity,
                                        String.format(
                                                "Đối tác '%s' có tỷ lệ hủy đơn cao: %.2f%%",
                                                name, vRate),
                                        vRate));
                    }
                }
            }
        }

        // Cảnh báo quá tải công suất slot
        Map<UUID, List<ServiceSlotRecord>> slotsByVendor =
                slots.stream()
                        .filter(s -> s.vendorId() != null)
                        .collect(Collectors.groupingBy(ServiceSlotRecord::vendorId));

        for (Map.Entry<UUID, List<ServiceSlotRecord>> entry : slotsByVendor.entrySet()) {
            UUID vId = entry.getKey();
            List<ServiceSlotRecord> vSlots = entry.getValue();

            int totalCap =
                    vSlots.stream().mapToInt(ServiceSlotRecord::capacity).filter(c -> c > 0).sum();
            int totalBook =
                    vSlots.stream()
                            .mapToInt(ServiceSlotRecord::bookedCount)
                            .filter(b -> b > 0)
                            .sum();

            if (totalCap > 0) {
                double occRate = (double) totalBook / totalCap * 100.0;
                if (occRate >= 95.0) {
                    VendorRecord vRec = vendorMap.get(vId);
                    String name = vRec != null ? vRec.businessName() : "Đối tác " + vId;
                    alerts.add(
                            new VendorDiagnosisAlert(
                                    vId,
                                    name,
                                    VendorIssueType.OVERLOADED,
                                    VendorAlertSeverity.CRITICAL,
                                    String.format(
                                            "Đối tác '%s' đang quá tải công suất slot: %.2f%%",
                                            name, occRate),
                                    occRate));
                }
            }
        }

        // Phân bổ hạng huy hiệu đối tác & cảnh báo đối tác TOP_RATED có nguy cơ rớt hạng
        Map<String, Long> badgeDistribution = new LinkedHashMap<>();
        badgeDistribution.put(BadgeTier.TOP_RATED.name(), 0L);
        badgeDistribution.put(BadgeTier.VERIFIED.name(), 0L);
        badgeDistribution.put(BadgeTier.NONE.name(), 0L);

        BigDecimal atRiskThreshold = new BigDecimal("4.65");
        for (VendorRecord vRec : vendors) {
            if (vendorId != null && !vendorId.equals(vRec.id())) {
                continue;
            }
            BadgeTier tier = vRec.badgeTier() != null ? vRec.badgeTier() : BadgeTier.NONE;
            badgeDistribution.merge(tier.name(), 1L, Long::sum);

            if (tier == BadgeTier.TOP_RATED
                    && vRec.ratingAvg() != null
                    && vRec.ratingCount() != null
                    && vRec.ratingCount() >= 5
                    && vRec.ratingAvg().compareTo(atRiskThreshold) < 0) {
                String name = vRec.businessName() != null ? vRec.businessName() : "Đối tác " + vRec.id();
                alerts.add(
                        new VendorDiagnosisAlert(
                                vRec.id(),
                                name,
                                VendorIssueType.BADGE_AT_RISK,
                                VendorAlertSeverity.WARNING,
                                String.format(
                                        "Đối tác TOP_RATED '%s' có nguy cơ rớt hạng (điểm TB: %.2f/5.0)",
                                        name, vRec.ratingAvg().doubleValue()),
                                vRec.ratingAvg().doubleValue()));
            }
        }

        return DashboardMetrics.of(
                totalRevenue,
                netRevenue,
                newOrders,
                completedOrders,
                cancelledOrders,
                alerts,
                badgeDistribution);
    }

    public DashboardMetrics execute(TimeRange timeRange) {
        return execute(timeRange, null);
    }

    public DashboardMetrics execute() {
        return execute(defaultTimeRange(), null);
    }

    private TimeRange defaultTimeRange() {
        LocalDate today = LocalDate.now(TimeRange.VIETNAM_ZONE);
        LocalDate startOfMonth = today.withDayOfMonth(1);
        return TimeRange.of(startOfMonth, today);
    }
}
