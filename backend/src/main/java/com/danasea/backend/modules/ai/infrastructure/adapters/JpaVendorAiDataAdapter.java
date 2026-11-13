package com.danasea.backend.modules.ai.infrastructure.adapters;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort;
import com.danasea.backend.modules.operation.infrastructure.persistence.entities.ReviewJpaEntity;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaReviewRepository;
import com.danasea.backend.modules.report.application.usecases.GetVendorPerformanceReportUseCase;
import com.danasea.backend.modules.report.domain.models.TimeRange;
import com.danasea.backend.modules.report.domain.models.VendorPerformanceItem;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.CategoryJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaCategoryRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.Vendor;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JpaVendorAiDataAdapter implements VendorAiDataPort {

    private final VendorInternalApi vendorInternalApi;
    private final JpaServiceRepository serviceRepo;
    private final JpaCategoryRepository categoryRepo;
    private final JpaServiceSlotRepository slotRepo;
    private final JpaReviewRepository reviewRepo;
    private final GetVendorPerformanceReportUseCase vendorPerformanceReportUseCase;

    @Override
    @Transactional(readOnly = true)
    public Optional<VendorProfile> findProfileByUserId(UUID userId) {
        return vendorInternalApi.findByUserId(userId).map(this::toProfile);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VendorProfile> findProfileByVendorId(UUID vendorId) {
        return vendorInternalApi.findById(vendorId).map(this::toProfile);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorServiceItem> listServices(UUID vendorId) {
        return serviceRepo.findByVendorIdOrderByCreatedAtDesc(vendorId).stream()
                .filter(service -> service.getStatus() == ServiceStatus.PUBLISHED)
                .map(this::toServiceItem)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> countServicesByStatus(UUID vendorId) {
        return serviceRepo.countServicesByStatusForVendor(vendorId).stream()
                .collect(Collectors.toMap(row -> row.getStatus().name(), JpaServiceRepository.StatusCount::getTotal));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VendorServiceItem> getService(UUID vendorId, UUID serviceId) {
        return findOwnedService(vendorId, serviceId).map(this::toServiceItem);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorSlotItem> listUpcomingSlots(UUID vendorId, UUID serviceId, int horizonDays) {
        LocalDate today = LocalDate.now(TimeRange.VIETNAM_ZONE);
        LocalDate toDate = today.plusDays(Math.max(1, Math.min(horizonDays, 30)));
        List<VendorSlotItem> result = new ArrayList<>();
        List<ServiceJpaEntity> services = serviceId == null
                ? serviceRepo.findByVendorId(vendorId)
                : findOwnedService(vendorId, serviceId).stream().toList();

        for (ServiceJpaEntity service : services) {
            var slots = slotRepo.findByServiceIdAndDateBetweenOrderByDateAscStartTimeAsc(service.getId(), today, toDate);
            for (var slot : slots) {
                if (slot.getStatus() != SlotStatus.CANCELLED) {
                    result.add(new VendorSlotItem(
                            slot.getId(), service.getId(), service.getName(), slot.getDate(), slot.getStartTime(), slot.getEndTime(),
                            slot.getCapacity(), slot.getBookedCount(), slot.getStatus().name()
                    ));
                }
            }
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorReviewItem> listReviews(UUID vendorId, UUID serviceId, int limit) {
        int bounded = Math.max(1, Math.min(limit, 100));
        var pageable = PageRequest.of(0, bounded);
        var page = serviceId == null
                ? reviewRepo.findByVendorIdAndIsVisibleTrue(vendorId, pageable)
                : reviewRepo.findByVendorIdAndServiceIdAndIsVisibleTrue(vendorId, serviceId, pageable);
        List<ReviewJpaEntity> reviews = page.getContent();
        var serviceIds = reviews.stream().map(ReviewJpaEntity::getServiceId).distinct().toList();
        Map<UUID, ServiceJpaEntity> services = serviceRepo.findAllById(serviceIds).stream()
                .collect(Collectors.toMap(ServiceJpaEntity::getId, Function.identity()));

        return reviews.stream().map(review -> new VendorReviewItem(
                review.getId(), review.getServiceId(),
                Optional.ofNullable(services.get(review.getServiceId())).map(ServiceJpaEntity::getName).orElse("Trải nghiệm biển"),
                review.getRating(), review.getComment(), review.getCreatedAt()
        )).toList();
    }

    private Optional<ServiceJpaEntity> findOwnedService(UUID vendorId, UUID serviceId) {
        return serviceRepo.findById(serviceId).filter(service -> vendorId.equals(service.getVendorId()));
    }

    private VendorServiceItem toServiceItem(ServiceJpaEntity service) {
        String categoryName = Optional.ofNullable(service.getCategoryId())
                .flatMap(categoryRepo::findById)
                .map(CategoryJpaEntity::getName)
                .orElse("Du lịch biển");
        return new VendorServiceItem(
                service.getId(), service.getVendorId(), service.getName(), service.getDescription(),
                categoryName, service.getAvgRating() != null ? service.getAvgRating() : BigDecimal.ZERO,
                service.getRatingCount() != null ? service.getRatingCount() : 0
        );
    }

    @Override
    @Transactional(readOnly = true)
    public VendorPerformanceMetrics getPerformanceMetrics(UUID vendorId, LocalDate from, LocalDate to) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now(TimeRange.VIETNAM_ZONE).minusDays(30);
        LocalDate effectiveTo = to != null ? to : LocalDate.now(TimeRange.VIETNAM_ZONE);
        TimeRange range = TimeRange.of(effectiveFrom, effectiveTo);

        List<VendorPerformanceItem> items = vendorPerformanceReportUseCase.execute(range, vendorId);
        if (items.isEmpty()) {
            return new VendorPerformanceMetrics(
                    BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0.0, 0, 0, 0.0,
                    BigDecimal.ZERO, 0, "NONE", List.of()
            );
        }

        VendorPerformanceItem item = items.get(0);
        List<String> alerts = item.getIssues().stream().map(Enum::name).toList();
        String badge = vendorInternalApi.findById(vendorId)
                .map(Vendor::getBadgeTier).map(BadgeTier::name).orElse("NONE");

        return new VendorPerformanceMetrics(
                item.getRevenue(),
                item.getNetPayout(),
                item.getTotalOrders(),
                item.getCancelledOrders(),
                item.getCancellationRate(),
                0L, // weather cancellations aggregated inside booking breakdown
                0L,
                item.getSlotOccupancyRate(),
                item.getRating(),
                item.getRatingCount(),
                badge,
                alerts
        );
    }

    private VendorProfile toProfile(Vendor v) {
        return new VendorProfile(
                v.getId(), v.getUserId(), v.getBusinessName(),
                v.getRatingAvg() != null ? v.getRatingAvg() : BigDecimal.ZERO,
                v.getRatingCount() != null ? v.getRatingCount() : 0,
                v.getBadgeTier() != null ? v.getBadgeTier().name() : "NONE"
        );
    }
}
