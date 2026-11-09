package com.danasea.backend.modules.ai.infrastructure.adapters;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaReviewRepository;
import com.danasea.backend.modules.report.application.usecases.GetVendorPerformanceReportUseCase;
import com.danasea.backend.modules.report.domain.models.TimeRange;
import com.danasea.backend.modules.report.domain.models.VendorPerformanceItem;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.Vendor;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JpaVendorAiDataAdapter implements VendorAiDataPort {

    private final VendorInternalApi vendorInternalApi;
    private final JpaServiceRepository serviceRepo;
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
                .filter(s -> s.getStatus() == ServiceStatus.APPROVED || s.getStatus() == ServiceStatus.ACTIVE)
                .map(s -> new VendorServiceItem(
                        s.getId(), s.getVendorId(), s.getTitle(), s.getDescription(),
                        s.getCategory() != null ? s.getCategory().getName() : "Du lịch biển",
                        s.getRatingAvg() != null ? s.getRatingAvg() : BigDecimal.ZERO,
                        s.getRatingCount() != null ? s.getRatingCount() : 0
                )).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VendorServiceItem> getService(UUID vendorId, UUID serviceId) {
        return serviceRepo.findById(serviceId)
                .filter(s -> s.getVendorId().equals(vendorId))
                .map(s -> new VendorServiceItem(
                        s.getId(), s.getVendorId(), s.getTitle(), s.getDescription(),
                        s.getCategory() != null ? s.getCategory().getName() : "Du lịch biển",
                        s.getRatingAvg() != null ? s.getRatingAvg() : BigDecimal.ZERO,
                        s.getRatingCount() != null ? s.getRatingCount() : 0
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorSlotItem> listUpcomingSlots(UUID vendorId, UUID serviceId, int horizonDays) {
        LocalDate today = LocalDate.now();
        LocalDate toDate = today.plusDays(Math.max(1, Math.min(horizonDays, 30)));
        List<VendorSlotItem> result = new ArrayList<>();
        List<UUID> targetServiceIds = serviceId != null
                ? List.of(serviceId)
                : serviceRepo.findByVendorId(vendorId).stream().map(ServiceJpaEntity::getId).toList();

        for (UUID sId : targetServiceIds) {
            var service = serviceRepo.findById(sId).orElse(null);
            String title = service != null ? service.getTitle() : "Dịch vụ biển";
            var slots = slotRepo.findByServiceIdAndDateBetweenOrderByDateAscStartTimeAsc(sId, today, toDate);
            for (var slot : slots) {
                if (slot.getStatus() != SlotStatus.CANCELLED) {
                    result.add(new VendorSlotItem(
                            slot.getId(), sId, title, slot.getDate(), slot.getStartTime(), slot.getEndTime(),
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
        var page = serviceId != null
                ? reviewRepo.findByServiceIdAndIsVisibleTrue(serviceId, pageable)
                : reviewRepo.findByVendorIdAndIsVisibleTrue(vendorId, pageable);

        return page.getContent().stream().map(r -> new VendorReviewItem(
                r.getId(), r.getServiceId(),
                r.getService() != null ? r.getService().getTitle() : "Trải nghiệm biển",
                r.getRating(), r.getComment(), r.getCreatedAt()
        )).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VendorPerformanceMetrics getPerformanceMetrics(UUID vendorId, LocalDate from, LocalDate to) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        TimeRange range = TimeRange.of(effectiveFrom, effectiveTo);

        List<VendorPerformanceItem> items = vendorPerformanceReportUseCase.execute(range, vendorId);
        if (items.isEmpty()) {
            return new VendorPerformanceMetrics(
                    BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0.0, 0, 0, 0.0,
                    BigDecimal.ZERO, 0, "NONE", List.of()
            );
        }

        VendorPerformanceItem item = items.get(0);
        List<String> alerts = item.getIssues() != null
                ? item.getIssues().stream().map(Enum::name).toList() : List.of();

        String badge = "STANDARD";
        if (item.getRating() != null && item.getRating().doubleValue() >= 4.8) {
            badge = "GOLD";
        } else if (item.getRating() != null && item.getRating().doubleValue() >= 4.5) {
            badge = "SILVER";
        }

        return new VendorPerformanceMetrics(
                item.getRevenue() != null ? item.getRevenue() : BigDecimal.ZERO,
                item.getNetPayout() != null ? item.getNetPayout() : BigDecimal.ZERO,
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
