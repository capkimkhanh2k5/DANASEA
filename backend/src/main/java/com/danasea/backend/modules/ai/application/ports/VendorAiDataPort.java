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
}
