package com.danasea.backend.modules.ai.application.ports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface AdminAiDataPort {

    record PlatformDashboardMetrics(
            BigDecimal totalGmv,
            BigDecimal netPayout,
            BigDecimal platformCommission,
            long totalBookings,
            long activeServices,
            long activeVendors,
            long criticalVendorsCount,
            List<String> operationalAlerts
    ) {}

    record RevenueTimeSeriesItem(
            String period,
            BigDecimal gmv,
            BigDecimal collectedCash,
            BigDecimal refunds,
            BigDecimal commission,
            BigDecimal netVendorPayout
    ) {}

    record BookingCancellationBreakdown(
            long totalBookings,
            long completedBookings,
            long totalCancelled,
            long weatherCancellations,
            long vendorFaultCancellations,
            long customerCancellations,
            long adminOverrideCancellations,
            long disputeCancellations,
            Map<String, Long> cancellationsByReason
    ) {}

    record VendorDiagnosticItem(
            UUID vendorId,
            String businessName,
            BigDecimal gmv,
            long totalBookings,
            double cancellationRate,
            double slotOccupancyRate,
            BigDecimal ratingAvg,
            int ratingCount,
            String severity, // HEALTHY, WARNING, CRITICAL
            List<String> issueTypes
    ) {}

    record DisputeFullEvidence(
            UUID disputeId,
            UUID orderId,
            UUID subOrderId,
            UUID customerId,
            String customerName,
            String disputeReason,
            String description,
            List<String> customerEvidenceUrls,
            String status,
            OffsetDateTime disputeCreatedAt,
            // SubOrder & Service facts
            UUID serviceId,
            String serviceTitle,
            UUID vendorId,
            String vendorBusinessName,
            BigDecimal subtotalAmount,
            Integer quantity,
            UUID slotId,
            LocalDate slotDate,
            String slotTimeRange,
            String slotStatus,
            // Checkin verification
            boolean checkinUsed,
            OffsetDateTime checkinUsedAt,
            // Vendor defense
            String vendorResponse,
            List<String> vendorEvidenceUrls,
            OffsetDateTime vendorRespondedAt
    ) {}

    record QueueOverview(
            long pendingRiskCasesCount,
            long pendingSupportRequestsCount,
            long openDisputesCount
    ) {}

    record AdminOrderDetail(
            UUID subOrderId,
            UUID masterOrderId,
            UUID vendorId,
            String vendorBusinessName,
            UUID serviceId,
            String serviceTitle,
            String customerName,
            LocalDate slotDate,
            LocalTime slotStartTime,
            LocalTime slotEndTime,
            Integer quantity,
            BigDecimal finalAmount,
            BigDecimal commissionAmount,
            BigDecimal vendorPayoutAmount,
            String status,
            String paymentStatus,
            String cancellationReason,
            OffsetDateTime checkedInAt,
            OffsetDateTime createdAt
    ) {}

    record AdminServiceDetail(
            UUID id,
            UUID vendorId,
            String vendorBusinessName,
            String title,
            String categoryName,
            String status,
            BigDecimal basePrice,
            Integer durationMinutes,
            Integer capacityPerSlot,
            BigDecimal averageRating,
            Integer reviewCount
    ) {}

    record AdminVendorDetail(
            UUID id,
            String businessName,
            String status,
            String badgeTier,
            BigDecimal ratingAvg,
            Integer ratingCount,
            long publishedServicesCount
    ) {}

    PlatformDashboardMetrics getPlatformDashboardMetrics();

    List<RevenueTimeSeriesItem> getRevenueTimeSeries(LocalDate from, LocalDate to, String groupBy);

    BookingCancellationBreakdown getBookingCancellationBreakdown(LocalDate from, LocalDate to, String groupBy);

    List<VendorDiagnosticItem> getVendorDiagnostics(LocalDate from, LocalDate to, UUID vendorId);

    Optional<DisputeFullEvidence> getDisputeFullEvidence(UUID disputeId);

    QueueOverview getQueueOverview();

    default Optional<AdminOrderDetail> getOrderDetail(UUID orderOrSubOrderId) {
        return Optional.empty();
    }

    default Optional<AdminServiceDetail> getServiceDetail(UUID serviceId) {
        return Optional.empty();
    }

    default Optional<AdminVendorDetail> getVendorDetail(UUID vendorId) {
        return Optional.empty();
    }
}
