package com.danasea.backend.modules.report.application.usecases;

import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.report.application.ports.ReportDataPort;
import com.danasea.backend.modules.report.application.ports.ReviewRecord;
import com.danasea.backend.modules.report.application.ports.ServiceSlotRecord;
import com.danasea.backend.modules.report.application.ports.SubOrderRecord;
import com.danasea.backend.modules.report.application.ports.VendorRecord;
import com.danasea.backend.modules.report.domain.models.TimeRange;
import com.danasea.backend.modules.report.domain.models.VendorPerformanceItem;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * UseCase phân tích chi tiết hiệu quả hoạt động theo từng đối tác cung cấp dịch vụ (Vendor
 * Performance & Diagnostics).
 *
 * <p>Tính toán: - Doanh thu (GMV), tiền thực nhận (Net Vendor Payout). - Số lượng đơn hàng, số đơn
 * hoàn thành, số đơn hủy, tỷ lệ hủy. - Tỷ lệ lấp đầy chỗ (slot occupancy rate = SUM(booked) /
 * SUM(capacity) * 100%). - Điểm đánh giá chất lượng (Rating từ reviews thực tế hoặc hồ sơ đối tác).
 * - Cơ chế chẩn đoán thông minh (HEALTHY, WARNING, CRITICAL) với các cảnh báo (HIGH_CANCELLATION,
 * LOW_RATING, OVERLOADED, UNDERPERFORMING).
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class GetVendorPerformanceReportUseCase {

    private final ReportDataPort reportDataPort;

    public GetVendorPerformanceReportUseCase(ReportDataPort reportDataPort) {
        this.reportDataPort =
                Objects.requireNonNull(reportDataPort, "reportDataPort cannot be null");
    }

    /**
     * Phân tích hiệu suất đối tác trong khoảng thời gian TimeRange.
     *
     * @param timeRange khoảng thời gian lọc (chuẩn UTC+7)
     * @param vendorId ID của vendor cụ thể (nếu null: toàn bộ các vendor đã được duyệt - APPROVED)
     * @return danh sách VendorPerformanceItem kèm chẩn đoán thông minh
     */
    public List<VendorPerformanceItem> execute(TimeRange timeRange, UUID vendorId) {
        Objects.requireNonNull(timeRange, "timeRange cannot be null");

        // 1. Xác định danh sách Vendor cần đánh giá
        List<VendorRecord> vendors;
        if (vendorId != null) {
            Optional<VendorRecord> vendorOpt = reportDataPort.findVendorById(vendorId);
            if (vendorOpt.isEmpty()) {
                return Collections.emptyList();
            }
            vendors = List.of(vendorOpt.get());
        } else {
            vendors = reportDataPort.findApprovedVendors();
        }

        if (vendors.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Trích xuất dữ liệu vận hành từ cơ sở dữ liệu
        List<SubOrderRecord> subOrders = reportDataPort.findSubOrders(timeRange, vendorId);
        List<ReportFinancialLedger.Entry> financialEntries =
                ReportFinancialLedger.load(reportDataPort, timeRange, vendorId);
        List<ServiceSlotRecord> slots = reportDataPort.findServiceSlots(timeRange, vendorId);
        List<ReviewRecord> reviews = reportDataPort.findReviews(timeRange, vendorId);

        Map<UUID, RefundReason> cancellationReasons =
                BookingCancellationClassifier.reasons(reportDataPort, subOrders);
        // 3. Phân loại dữ liệu theo Vendor ID
        Map<UUID, List<SubOrderRecord>> subOrdersByVendor =
                subOrders.stream()
                        .filter(s -> s.vendorId() != null)
                        .collect(Collectors.groupingBy(SubOrderRecord::vendorId));

        Map<UUID, List<ServiceSlotRecord>> slotsByVendor =
                slots.stream()
                        .filter(s -> s.vendorId() != null)
                        .collect(Collectors.groupingBy(ServiceSlotRecord::vendorId));

        Map<UUID, List<ReviewRecord>> reviewsByVendor =
                reviews.stream()
                        .filter(r -> r.vendorId() != null)
                        .collect(Collectors.groupingBy(ReviewRecord::vendorId));

        // 4. Tính toán chỉ số và đánh giá chẩn đoán cho từng Vendor
        List<VendorPerformanceItem> results = new ArrayList<>(vendors.size());
        for (VendorRecord vendor : vendors) {
            UUID currentVendorId = vendor.id();
            List<SubOrderRecord> vOrders =
                    subOrdersByVendor.getOrDefault(currentVendorId, Collections.emptyList());

            long totalOrders = vOrders.size();
            long completedOrders =
                    vOrders.stream().filter(s -> s.status() == SubOrderStatus.COMPLETED).count();
            long cancelledOrders =
                    vOrders.stream()
                            .filter(
                                    s ->
                                            BookingCancellationClassifier.isCancelled(
                                                    s, cancellationReasons))
                            .count();

            List<ReportFinancialLedger.Entry> vendorEntries =
                    financialEntries.stream()
                            .filter(e -> currentVendorId.equals(e.vendorId()))
                            .toList();
            BigDecimal revenue =
                    vendorEntries.stream()
                            .map(ReportFinancialLedger.Entry::gmv)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal netPayout =
                    vendorEntries.stream()
                            .map(ReportFinancialLedger.Entry::payout)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Tính toán công suất slot và tỷ lệ lấp đầy
            List<ServiceSlotRecord> vSlots =
                    slotsByVendor.getOrDefault(currentVendorId, Collections.emptyList());
            int totalCapacity =
                    vSlots.stream().mapToInt(ServiceSlotRecord::capacity).filter(c -> c > 0).sum();
            int totalBooked =
                    vSlots.stream()
                            .mapToInt(ServiceSlotRecord::bookedCount)
                            .filter(b -> b > 0)
                            .sum();

            // Tính điểm đánh giá
            List<ReviewRecord> vReviews =
                    reviewsByVendor.getOrDefault(currentVendorId, Collections.emptyList());
            BigDecimal rating;
            int ratingCount;
            if (!vReviews.isEmpty()) {
                ratingCount = vReviews.size();
                double avg = vReviews.stream().mapToInt(ReviewRecord::rating).average().orElse(0.0);
                rating = BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
            } else {
                ratingCount = vendor.ratingCount() != null ? vendor.ratingCount() : 0;
                rating = vendor.ratingAvg() != null ? vendor.ratingAvg() : BigDecimal.ZERO;
            }

            // Đánh giá tự động qua domain model logic
            VendorPerformanceItem item =
                    VendorPerformanceItem.evaluate(
                            currentVendorId,
                            vendor.businessName(),
                            vendor.badgeTier(),
                            revenue,
                            netPayout,
                            totalOrders,
                            completedOrders,
                            cancelledOrders,
                            totalBooked,
                            totalCapacity,
                            rating,
                            ratingCount);

            results.add(item);
        }

        return results;
    }

    public List<VendorPerformanceItem> execute(TimeRange timeRange) {
        return execute(timeRange, null);
    }
}
