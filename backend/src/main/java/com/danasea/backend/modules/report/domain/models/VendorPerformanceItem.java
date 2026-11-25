package com.danasea.backend.modules.report.domain.models;

import com.danasea.backend.modules.report.domain.enums.VendorAlertSeverity;
import com.danasea.backend.modules.report.domain.enums.VendorIssueType;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Chỉ số hiệu suất và chẩn đoán hoạt động của một Vendor trong khoảng thời gian phân tích. */
public final class VendorPerformanceItem {

    private static final BigDecimal TOP_RATED_AT_RISK_THRESHOLD = new BigDecimal("4.65");

    private final UUID vendorId;
    private final String businessName;
    private final BadgeTier badgeTier;
    private final BigDecimal revenue;
    private final BigDecimal netPayout;
    private final long totalOrders;
    private final long completedOrders;
    private final long cancelledOrders;
    private final double cancellationRate;
    private final double slotOccupancyRate;
    private final BigDecimal rating;
    private final int ratingCount;
    private final VendorAlertSeverity severity;
    private final List<VendorIssueType> issues;

    public VendorPerformanceItem(
            UUID vendorId,
            String businessName,
            BigDecimal revenue,
            BigDecimal netPayout,
            long totalOrders,
            long completedOrders,
            long cancelledOrders,
            double cancellationRate,
            double slotOccupancyRate,
            BigDecimal rating,
            int ratingCount,
            VendorAlertSeverity severity,
            List<VendorIssueType> issues) {
        this(
                vendorId,
                businessName,
                BadgeTier.NONE,
                revenue,
                netPayout,
                totalOrders,
                completedOrders,
                cancelledOrders,
                cancellationRate,
                slotOccupancyRate,
                rating,
                ratingCount,
                severity,
                issues);
    }

    public VendorPerformanceItem(
            UUID vendorId,
            String businessName,
            BadgeTier badgeTier,
            BigDecimal revenue,
            BigDecimal netPayout,
            long totalOrders,
            long completedOrders,
            long cancelledOrders,
            double cancellationRate,
            double slotOccupancyRate,
            BigDecimal rating,
            int ratingCount,
            VendorAlertSeverity severity,
            List<VendorIssueType> issues) {
        this.vendorId = Objects.requireNonNull(vendorId, "vendorId cannot be null");
        this.businessName = businessName != null ? businessName : "";
        this.badgeTier = badgeTier != null ? badgeTier : BadgeTier.NONE;
        this.revenue = revenue != null ? revenue : BigDecimal.ZERO;
        this.netPayout = netPayout != null ? netPayout : BigDecimal.ZERO;
        this.totalOrders = totalOrders;
        this.completedOrders = completedOrders;
        this.cancelledOrders = cancelledOrders;
        this.cancellationRate = cancellationRate;
        this.slotOccupancyRate = slotOccupancyRate;
        this.rating = rating != null ? rating : BigDecimal.ZERO;
        this.ratingCount = ratingCount;
        this.severity = severity != null ? severity : VendorAlertSeverity.HEALTHY;
        this.issues =
                issues != null
                        ? Collections.unmodifiableList(new ArrayList<>(issues))
                        : Collections.emptyList();
    }

    /**
     * Chẩn đoán tự động và tính toán các chỉ số của Vendor với bảo vệ chia cho 0.
     *
     * @param vendorId ID của Vendor
     * @param businessName Tên kinh doanh của Vendor
     * @param revenue Tổng doanh thu
     * @param netPayout Tiền thực nhận của Vendor
     * @param totalOrders Tổng số đơn
     * @param completedOrders Số đơn hoàn tất
     * @param cancelledOrders Số đơn hủy
     * @param totalBooked Tổng số suất đã đặt của các slot
     * @param totalCapacity Tổng công suất các slot (nếu = 0 thì tỷ lệ lấp đầy = 0.0)
     * @param rating Điểm đánh giá trung bình
     * @param ratingCount Số lượt đánh giá
     * @return Đối tượng VendorPerformanceItem hoàn chỉnh
     */
    public static VendorPerformanceItem evaluate(
            UUID vendorId,
            String businessName,
            BigDecimal revenue,
            BigDecimal netPayout,
            long totalOrders,
            long completedOrders,
            long cancelledOrders,
            int totalBooked,
            int totalCapacity,
            BigDecimal rating,
            int ratingCount) {
        return evaluate(
                vendorId,
                businessName,
                BadgeTier.NONE,
                revenue,
                netPayout,
                totalOrders,
                completedOrders,
                cancelledOrders,
                totalBooked,
                totalCapacity,
                rating,
                ratingCount);
    }

    public static VendorPerformanceItem evaluate(
            UUID vendorId,
            String businessName,
            BadgeTier badgeTier,
            BigDecimal revenue,
            BigDecimal netPayout,
            long totalOrders,
            long completedOrders,
            long cancelledOrders,
            int totalBooked,
            int totalCapacity,
            BigDecimal rating,
            int ratingCount) {
        BadgeTier effectiveTier = badgeTier != null ? badgeTier : BadgeTier.NONE;
        double cancellationRate = 0.0;
        if (totalOrders > 0) {
            cancellationRate =
                    BigDecimal.valueOf((double) cancelledOrders / totalOrders * 100.0)
                            .setScale(2, RoundingMode.HALF_UP)
                            .doubleValue();
        }

        double slotOccupancyRate = 0.0;
        if (totalCapacity > 0) {
            slotOccupancyRate =
                    BigDecimal.valueOf((double) totalBooked / totalCapacity * 100.0)
                            .setScale(2, RoundingMode.HALF_UP)
                            .doubleValue();
        }

        List<VendorIssueType> detectedIssues = new ArrayList<>();
        if (cancellationRate > 20.0) {
            detectedIssues.add(VendorIssueType.HIGH_CANCELLATION);
        }
        if (rating != null && rating.compareTo(BigDecimal.valueOf(3.5)) < 0 && ratingCount >= 3) {
            detectedIssues.add(VendorIssueType.LOW_RATING);
        }
        if (slotOccupancyRate >= 95.0) {
            detectedIssues.add(VendorIssueType.OVERLOADED);
        }
        if (slotOccupancyRate < 20.0 && totalOrders < 5) {
            detectedIssues.add(VendorIssueType.UNDERPERFORMING);
        }
        if (effectiveTier == BadgeTier.TOP_RATED
                && ((rating != null
                                && ratingCount >= 5
                                && rating.compareTo(TOP_RATED_AT_RISK_THRESHOLD) < 0)
                        || cancellationRate > 15.0)) {
            detectedIssues.add(VendorIssueType.BADGE_AT_RISK);
        }

        VendorAlertSeverity severity;
        if (cancellationRate > 40.0
                || (rating != null
                        && rating.compareTo(BigDecimal.valueOf(2.5)) < 0
                        && ratingCount >= 5)) {
            severity = VendorAlertSeverity.CRITICAL;
        } else if (!detectedIssues.isEmpty()) {
            severity = VendorAlertSeverity.WARNING;
        } else {
            severity = VendorAlertSeverity.HEALTHY;
        }

        return new VendorPerformanceItem(
                vendorId,
                businessName,
                effectiveTier,
                revenue,
                netPayout,
                totalOrders,
                completedOrders,
                cancelledOrders,
                cancellationRate,
                slotOccupancyRate,
                rating,
                ratingCount,
                severity,
                detectedIssues);
    }

    public UUID getVendorId() {
        return vendorId;
    }

    public String getBusinessName() {
        return businessName;
    }

    public BadgeTier getBadgeTier() {
        return badgeTier;
    }

    public BigDecimal getRevenue() {
        return revenue;
    }

    public BigDecimal getNetPayout() {
        return netPayout;
    }

    public long getTotalOrders() {
        return totalOrders;
    }

    public long getCompletedOrders() {
        return completedOrders;
    }

    public long getCancelledOrders() {
        return cancelledOrders;
    }

    public double getCancellationRate() {
        return cancellationRate;
    }

    public double getSlotOccupancyRate() {
        return slotOccupancyRate;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public int getRatingCount() {
        return ratingCount;
    }

    public VendorAlertSeverity getSeverity() {
        return severity;
    }

    public List<VendorIssueType> getIssues() {
        return issues;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VendorPerformanceItem that = (VendorPerformanceItem) o;
        return totalOrders == that.totalOrders
                && completedOrders == that.completedOrders
                && cancelledOrders == that.cancelledOrders
                && Double.compare(that.cancellationRate, cancellationRate) == 0
                && Double.compare(that.slotOccupancyRate, slotOccupancyRate) == 0
                && ratingCount == that.ratingCount
                && Objects.equals(vendorId, that.vendorId)
                && Objects.equals(businessName, that.businessName)
                && Objects.equals(revenue, that.revenue)
                && Objects.equals(netPayout, that.netPayout)
                && Objects.equals(rating, that.rating)
                && severity == that.severity
                && Objects.equals(issues, that.issues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                vendorId,
                businessName,
                revenue,
                netPayout,
                totalOrders,
                completedOrders,
                cancelledOrders,
                cancellationRate,
                slotOccupancyRate,
                rating,
                ratingCount,
                severity,
                issues);
    }

    @Override
    public String toString() {
        return "VendorPerformanceItem{"
                + "vendorId="
                + vendorId
                + ", businessName='"
                + businessName
                + '\''
                + ", revenue="
                + revenue
                + ", netPayout="
                + netPayout
                + ", totalOrders="
                + totalOrders
                + ", completedOrders="
                + completedOrders
                + ", cancelledOrders="
                + cancelledOrders
                + ", cancellationRate="
                + cancellationRate
                + ", slotOccupancyRate="
                + slotOccupancyRate
                + ", rating="
                + rating
                + ", ratingCount="
                + ratingCount
                + ", severity="
                + severity
                + ", issues="
                + issues
                + '}';
    }
}
