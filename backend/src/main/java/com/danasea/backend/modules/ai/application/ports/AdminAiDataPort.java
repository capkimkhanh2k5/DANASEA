package com.danasea.backend.modules.ai.application.ports;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    PlatformDashboardMetrics getPlatformDashboardMetrics();

    List<RevenueTimeSeriesItem> getRevenueTimeSeries(LocalDate from, LocalDate to, String groupBy);

    BookingCancellationBreakdown getBookingCancellationBreakdown(LocalDate from, LocalDate to, String groupBy);

    List<VendorDiagnosticItem> getVendorDiagnostics(LocalDate from, LocalDate to, UUID vendorId);

    Optional<DisputeFullEvidence> getDisputeFullEvidence(UUID disputeId);

    QueueOverview getQueueOverview();
}
