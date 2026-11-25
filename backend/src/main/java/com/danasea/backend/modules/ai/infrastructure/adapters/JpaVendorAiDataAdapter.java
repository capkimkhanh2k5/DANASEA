package com.danasea.backend.modules.ai.infrastructure.adapters;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort;
import com.danasea.backend.modules.operation.infrastructure.persistence.entities.ReviewJpaEntity;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaReviewRepository;
import com.danasea.backend.modules.report.application.usecases.GetVendorPerformanceReportUseCase;
import com.danasea.backend.modules.report.domain.models.TimeRange;
import com.danasea.backend.modules.report.domain.models.VendorPerformanceItem;
import com.danasea.backend.modules.service.domain.models.OptionStatus;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.CategoryJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaCategoryRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceOptionRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.Vendor;

@Component
public class JpaVendorAiDataAdapter implements VendorAiDataPort {

    private final VendorInternalApi vendorInternalApi;
    private final JpaServiceRepository serviceRepo;
    private final JpaCategoryRepository categoryRepo;
    private final JpaServiceSlotRepository slotRepo;
    private final JpaReviewRepository reviewRepo;
    private final GetVendorPerformanceReportUseCase vendorPerformanceReportUseCase;
    private final JpaServiceOptionRepository optionRepo;
    private final JdbcTemplate jdbc;

    public JpaVendorAiDataAdapter(
            VendorInternalApi vendorInternalApi,
            JpaServiceRepository serviceRepo,
            JpaCategoryRepository categoryRepo,
            JpaServiceSlotRepository slotRepo,
            JpaReviewRepository reviewRepo,
            GetVendorPerformanceReportUseCase vendorPerformanceReportUseCase
    ) {
        this(vendorInternalApi, serviceRepo, categoryRepo, slotRepo, reviewRepo, vendorPerformanceReportUseCase, null, null);
    }

    @Autowired
    public JpaVendorAiDataAdapter(
            VendorInternalApi vendorInternalApi,
            JpaServiceRepository serviceRepo,
            JpaCategoryRepository categoryRepo,
            JpaServiceSlotRepository slotRepo,
            JpaReviewRepository reviewRepo,
            GetVendorPerformanceReportUseCase vendorPerformanceReportUseCase,
            @Autowired(required = false) JpaServiceOptionRepository optionRepo,
            @Autowired(required = false) JdbcTemplate jdbc
    ) {
        this.vendorInternalApi = vendorInternalApi;
        this.serviceRepo = serviceRepo;
        this.categoryRepo = categoryRepo;
        this.slotRepo = slotRepo;
        this.reviewRepo = reviewRepo;
        this.vendorPerformanceReportUseCase = vendorPerformanceReportUseCase;
        this.optionRepo = optionRepo;
        this.jdbc = jdbc;
    }

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
    public List<VendorServiceItem> listAllOwnedServices(UUID vendorId) {
        return serviceRepo.findByVendorIdOrderByCreatedAtDesc(vendorId).stream()
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
    public Optional<VendorServiceDetail> getServiceDetail(UUID vendorId, UUID serviceId) {
        return findOwnedService(vendorId, serviceId).map(service -> {
            String categoryName = Optional.ofNullable(service.getCategoryId())
                    .flatMap(categoryRepo::findById)
                    .map(CategoryJpaEntity::getName)
                    .orElse("Du lịch biển");
            List<VendorServicePriceOption> options = optionRepo == null
                    ? List.of()
                    : optionRepo.findByServiceIdAndStatusOrderByCreatedAtAsc(service.getId(), OptionStatus.ACTIVE)
                            .stream()
                            .map(opt -> new VendorServicePriceOption(
                                    opt.getId(),
                                    opt.getName(),
                                    opt.getPricingUnit() != null ? opt.getPricingUnit().name() : "PER_PERSON",
                                    opt.getPrice(),
                                    opt.getMaxPaxPerPackage(),
                                    opt.getStatus() != null ? opt.getStatus().name() : "ACTIVE"
                            ))
                            .toList();
            return new VendorServiceDetail(
                    service.getId(),
                    service.getVendorId(),
                    service.getName(),
                    categoryName,
                    service.getStatus() != null ? service.getStatus().name() : "PUBLISHED",
                    service.getPrice(),
                    service.getDurationMinutes(),
                    service.getCapacityPerSlot(),
                    service.getAvgRating() != null ? service.getAvgRating() : BigDecimal.ZERO,
                    service.getRatingCount() != null ? service.getRatingCount() : 0,
                    options
            );
        });
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

    @Override
    @Transactional(readOnly = true)
    public Optional<VendorOrderDetail> getOrderDetail(UUID vendorId, UUID orderOrSubOrderId) {
        if (jdbc == null || vendorId == null || orderOrSubOrderId == null) return Optional.empty();
        return jdbc.query("""
                SELECT so.id, so.master_order_id, so.service_id, s.name AS service_title,
                       sl.date AS slot_date, sl.start_time AS slot_start, sl.end_time AS slot_end,
                       so.quantity, so.unit_price, so.subtotal_amount, so.discount_amount,
                       COALESCE(so.final_amount, GREATEST(COALESCE(so.subtotal_amount, 0) - COALESCE(so.discount_amount, 0), 0)) AS final_amount,
                       so.commission_amount, so.vendor_payout_amount,
                       so.status, mo.payment_status, so.cancellation_reason, so.checked_in_at, so.created_at
                FROM sub_orders so
                LEFT JOIN services s ON s.id = so.service_id
                LEFT JOIN master_orders mo ON mo.id = so.master_order_id
                LEFT JOIN service_slots sl ON sl.id = COALESCE(so.actual_slot_id, so.slot_id)
                WHERE so.vendor_id = ? AND (so.id = ? OR so.master_order_id = ?)
                ORDER BY CASE WHEN so.id = ? THEN 0 ELSE 1 END, so.created_at DESC
                LIMIT 1
                """, (rs, i) -> new VendorOrderDetail(
                rs.getObject("id", UUID.class),
                rs.getObject("master_order_id", UUID.class),
                rs.getObject("service_id", UUID.class),
                rs.getString("service_title"),
                rs.getObject("slot_date", LocalDate.class),
                rs.getObject("slot_start", LocalTime.class),
                rs.getObject("slot_end", LocalTime.class),
                (Integer) rs.getObject("quantity"),
                rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("subtotal_amount"),
                rs.getBigDecimal("discount_amount"),
                rs.getBigDecimal("final_amount"),
                rs.getBigDecimal("commission_amount"),
                rs.getBigDecimal("vendor_payout_amount"),
                rs.getString("status"),
                rs.getString("payment_status"),
                rs.getString("cancellation_reason"),
                rs.getObject("checked_in_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class)
        ), vendorId, orderOrSubOrderId, orderOrSubOrderId, orderOrSubOrderId).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VendorDisputeDetail> getDisputeDetail(UUID vendorId, UUID disputeId) {
        if (jdbc == null || vendorId == null || disputeId == null) return Optional.empty();
        return jdbc.query("""
                SELECT d.id, d.sub_order_id, d.order_id, s.name AS service_title,
                       d.reason, d.description, d.status, d.refund_percentage,
                       COALESCE(so.final_amount, so.subtotal_amount) AS order_final_amount,
                       d.vendor_response, d.admin_note, d.created_at, d.resolved_at
                FROM disputes d
                JOIN sub_orders so ON so.id = d.sub_order_id
                LEFT JOIN services s ON s.id = so.service_id
                WHERE d.id = ? AND so.vendor_id = ?
                """, (rs, i) -> new VendorDisputeDetail(
                rs.getObject("id", UUID.class),
                rs.getObject("sub_order_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("service_title"),
                rs.getString("reason"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getBigDecimal("refund_percentage"),
                rs.getBigDecimal("order_final_amount"),
                rs.getString("vendor_response"),
                rs.getString("admin_note"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("resolved_at", OffsetDateTime.class)
        ), disputeId, vendorId).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VendorRefundDetail> getRefundDetail(UUID vendorId, UUID refundId) {
        if (jdbc == null || vendorId == null || refundId == null) return Optional.empty();
        return jdbc.query("""
                SELECT r.id, r.sub_order_id, s.name AS service_title,
                       r.amount, r.refund_percentage, r.reason, r.status, r.provider,
                       r.created_at, r.processed_at
                FROM refunds r
                JOIN sub_orders so ON so.id = r.sub_order_id
                LEFT JOIN services s ON s.id = so.service_id
                WHERE r.id = ? AND so.vendor_id = ?
                """, (rs, i) -> new VendorRefundDetail(
                rs.getObject("id", UUID.class),
                rs.getObject("sub_order_id", UUID.class),
                rs.getString("service_title"),
                rs.getBigDecimal("amount"),
                rs.getBigDecimal("refund_percentage"),
                rs.getString("reason"),
                rs.getString("status"),
                rs.getString("provider"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("processed_at", OffsetDateTime.class)
        ), refundId, vendorId).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorRefundDetail> listRefunds(UUID vendorId, int limit) {
        if (jdbc == null || vendorId == null) return List.of();
        int bounded = Math.max(1, Math.min(limit, 100));
        return jdbc.query("""
                SELECT r.id, r.sub_order_id, s.name AS service_title,
                       r.amount, r.refund_percentage, r.reason, r.status, r.provider,
                       r.created_at, r.processed_at
                FROM refunds r
                JOIN sub_orders so ON so.id = r.sub_order_id
                LEFT JOIN services s ON s.id = so.service_id
                WHERE so.vendor_id = ?
                ORDER BY r.created_at DESC
                LIMIT ?
                """, (rs, i) -> new VendorRefundDetail(
                rs.getObject("id", UUID.class),
                rs.getObject("sub_order_id", UUID.class),
                rs.getString("service_title"),
                rs.getBigDecimal("amount"),
                rs.getBigDecimal("refund_percentage"),
                rs.getString("reason"),
                rs.getString("status"),
                rs.getString("provider"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("processed_at", OffsetDateTime.class)
        ), vendorId, bounded);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VendorEquipmentDetail> getEquipmentDetail(UUID vendorId, UUID equipmentId) {
        if (jdbc == null || vendorId == null || equipmentId == null) return Optional.empty();
        var equipment = jdbc.query("""
                SELECT id, name, compatibility_group, capacity
                FROM vendor_equipment
                WHERE id = ? AND vendor_id = ?
                """, (rs, i) -> new Object[] {
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("compatibility_group"),
                rs.getInt("capacity")
        }, equipmentId, vendorId).stream().findFirst();
        if (equipment.isEmpty()) return Optional.empty();

        var maintenances = jdbc.query("""
                SELECT starts_at, ends_at, urgent, reason
                FROM equipment_maintenance
                WHERE equipment_id = ? AND ends_at >= CURRENT_TIMESTAMP
                ORDER BY starts_at ASC
                """, (rs, i) -> {
            LocalDateTime start = rs.getObject("starts_at", LocalDateTime.class);
            LocalDateTime end = rs.getObject("ends_at", LocalDateTime.class);
            boolean urgent = rs.getBoolean("urgent");
            String reason = rs.getString("reason");
            return start + ".." + end + (urgent ? " [URGENT]" : "")
                    + (reason != null && !reason.isBlank() ? " (" + reason + ")" : "");
        }, equipmentId);

        Object[] row = equipment.get();
        return Optional.of(new VendorEquipmentDetail(
                (UUID) row[0],
                (String) row[1],
                (String) row[2],
                (Integer) row[3],
                maintenances.size(),
                maintenances.isEmpty() ? null : maintenances.getFirst()
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VendorPayoutDetail> getPayoutDetail(UUID vendorId, UUID payoutId) {
        if (jdbc == null || vendorId == null || payoutId == null) return Optional.empty();
        return jdbc.query("""
                SELECT id, settlement_id, amount, status, bank_reference,
                       vendor_note, admin_note, rejection_reason, created_at, processed_at
                FROM payout_requests
                WHERE id = ? AND vendor_id = ?
                """, (rs, i) -> new VendorPayoutDetail(
                rs.getObject("id", UUID.class),
                rs.getObject("settlement_id", UUID.class),
                rs.getBigDecimal("amount"),
                rs.getString("status"),
                rs.getString("bank_reference"),
                rs.getString("vendor_note"),
                rs.getString("admin_note"),
                rs.getString("rejection_reason"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("processed_at", OffsetDateTime.class)
        ), payoutId, vendorId).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VendorSlotItem> getSlotDetail(UUID vendorId, UUID slotId) {
        if (jdbc == null || vendorId == null || slotId == null) return Optional.empty();
        return jdbc.query("""
                SELECT sl.id, sl.service_id, s.name AS service_title,
                       sl.date, sl.start_time, sl.end_time, sl.capacity, sl.booked_count, sl.status
                FROM service_slots sl
                JOIN services s ON s.id = sl.service_id
                WHERE sl.id = ? AND s.vendor_id = ?
                """, (rs, i) -> new VendorSlotItem(
                rs.getObject("id", UUID.class),
                rs.getObject("service_id", UUID.class),
                rs.getString("service_title"),
                rs.getObject("date", LocalDate.class),
                rs.getObject("start_time", LocalTime.class),
                rs.getObject("end_time", LocalTime.class),
                rs.getInt("capacity"),
                rs.getInt("booked_count"),
                rs.getString("status")
        ), slotId, vendorId).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VendorReviewDetail> getReviewDetail(UUID vendorId, UUID reviewId) {
        if (jdbc == null || vendorId == null || reviewId == null) return Optional.empty();
        return jdbc.query("""
                SELECT r.id, r.service_id, s.name AS service_title,
                       r.rating, r.comment, r.vendor_reply, r.created_at
                FROM reviews r
                LEFT JOIN services s ON s.id = r.service_id
                WHERE r.id = ? AND r.vendor_id = ?
                """, (rs, i) -> new VendorReviewDetail(
                rs.getObject("id", UUID.class),
                rs.getObject("service_id", UUID.class),
                rs.getString("service_title"),
                rs.getInt("rating"),
                rs.getString("comment"),
                rs.getString("vendor_reply"),
                rs.getObject("created_at", OffsetDateTime.class)
        ), reviewId, vendorId).stream().findFirst();
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
