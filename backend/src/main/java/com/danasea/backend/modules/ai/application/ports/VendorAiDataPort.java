package com.danasea.backend.modules.ai.application.ports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface VendorAiDataPort {

    record VendorProfile(
            UUID id,
            UUID userId,
            String businessName,
            BigDecimal ratingAvg,
            Integer ratingCount,
            String badgeTier
    ) {}

    record VendorServiceItem(
            UUID id,
            UUID vendorId,
            String title,
            String description,
            String categoryName,
            BigDecimal averageRating,
            Integer reviewCount
    ) {}

    record VendorServicePriceOption(
            UUID id,
            String name,
            String pricingUnit,
            BigDecimal price,
            Integer maxPaxPerPackage,
            String status
    ) {}

    record VendorServiceDetail(
            UUID id,
            UUID vendorId,
            String title,
            String categoryName,
            String status,
            BigDecimal basePrice,
            Integer durationMinutes,
            Integer capacityPerSlot,
            BigDecimal averageRating,
            Integer reviewCount,
            List<VendorServicePriceOption> options
    ) {}

    record VendorSlotItem(
            UUID slotId,
            UUID serviceId,
            String serviceTitle,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            int capacity,
            int bookedCount,
            String status
    ) {}

    record VendorReviewItem(
            UUID id,
            UUID serviceId,
            String serviceTitle,
            int rating,
            String comment,
            OffsetDateTime createdAt
    ) {}

    record VendorReviewDetail(
            UUID id,
            UUID serviceId,
            String serviceTitle,
            int rating,
            String comment,
            String vendorReply,
            OffsetDateTime createdAt
    ) {}

    record VendorOrderDetail(
            UUID id,
            UUID masterOrderId,
            UUID serviceId,
            String serviceTitle,
            LocalDate slotDate,
            LocalTime slotStartTime,
            LocalTime slotEndTime,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal subtotalAmount,
            BigDecimal discountAmount,
            BigDecimal finalAmount,
            BigDecimal commissionAmount,
            BigDecimal vendorPayoutAmount,
            String status,
            String paymentStatus,
            String cancellationReason,
            OffsetDateTime checkedInAt,
            OffsetDateTime createdAt
    ) {}

    record VendorDisputeDetail(
            UUID id,
            UUID subOrderId,
            UUID orderId,
            String serviceTitle,
            String reason,
            String description,
            String status,
            BigDecimal refundPercentage,
            BigDecimal orderFinalAmount,
            String vendorResponse,
            String adminNote,
            OffsetDateTime createdAt,
            OffsetDateTime resolvedAt
    ) {}

    record VendorRefundDetail(
            UUID id,
            UUID subOrderId,
            String serviceTitle,
            BigDecimal amount,
            BigDecimal refundPercentage,
            String reason,
            String status,
            String provider,
            OffsetDateTime createdAt,
            OffsetDateTime processedAt
    ) {}

    record VendorEquipmentDetail(
            UUID id,
            String name,
            String compatibilityGroup,
            int capacity,
            int upcomingMaintenanceCount,
            String nextMaintenanceSummary
    ) {}

    record VendorPayoutDetail(
            UUID id,
            UUID settlementId,
            BigDecimal amount,
            String status,
            String bankReference,
            String vendorNote,
            String adminNote,
            String rejectionReason,
            OffsetDateTime createdAt,
            OffsetDateTime processedAt
    ) {}

    record VendorPerformanceMetrics(
            BigDecimal gmv,
            BigDecimal netPayout,
            long totalBookings,
            long cancelledBookings,
            double cancellationRate,
            long weatherCancellations,
            long vendorFaultCancellations,
            double slotOccupancyRate,
            BigDecimal ratingAvg,
            int ratingCount,
            String badgeTier,
            List<String> diagnosticAlerts
    ) {}

    Optional<VendorProfile> findProfileByUserId(UUID userId);
    Optional<VendorProfile> findProfileByVendorId(UUID vendorId);
    List<VendorServiceItem> listServices(UUID vendorId);
    default List<VendorServiceItem> listAllOwnedServices(UUID vendorId) {
        return listServices(vendorId);
    }
    Map<String, Long> countServicesByStatus(UUID vendorId);
    Optional<VendorServiceItem> getService(UUID vendorId, UUID serviceId);
    default Optional<VendorServiceDetail> getServiceDetail(UUID vendorId, UUID serviceId) {
        return getService(vendorId, serviceId).map(s -> new VendorServiceDetail(
                s.id(), s.vendorId(), s.title(), s.categoryName(), "PUBLISHED",
                null, null, null, s.averageRating(), s.reviewCount(), List.of()
        ));
    }
    List<VendorSlotItem> listUpcomingSlots(UUID vendorId, UUID serviceId, int horizonDays);
    List<VendorReviewItem> listReviews(UUID vendorId, UUID serviceId, int limit);
    VendorPerformanceMetrics getPerformanceMetrics(UUID vendorId, LocalDate from, LocalDate to);

    default Optional<VendorOrderDetail> getOrderDetail(UUID vendorId, UUID orderOrSubOrderId) {
        return Optional.empty();
    }

    default Optional<VendorDisputeDetail> getDisputeDetail(UUID vendorId, UUID disputeId) {
        return Optional.empty();
    }

    default Optional<VendorRefundDetail> getRefundDetail(UUID vendorId, UUID refundId) {
        return Optional.empty();
    }

    default Optional<VendorEquipmentDetail> getEquipmentDetail(UUID vendorId, UUID equipmentId) {
        return Optional.empty();
    }

    default Optional<VendorPayoutDetail> getPayoutDetail(UUID vendorId, UUID payoutId) {
        return Optional.empty();
    }

    default Optional<VendorSlotItem> getSlotDetail(UUID vendorId, UUID slotId) {
        return Optional.empty();
    }

    default Optional<VendorReviewDetail> getReviewDetail(UUID vendorId, UUID reviewId) {
        return Optional.empty();
    }
}
