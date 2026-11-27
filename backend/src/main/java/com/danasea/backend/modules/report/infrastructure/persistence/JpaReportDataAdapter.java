package com.danasea.backend.modules.report.infrastructure.persistence;

import com.danasea.backend.modules.operation.infrastructure.persistence.entities.ReviewJpaEntity;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.report.application.ports.MasterOrderRecord;
import com.danasea.backend.modules.report.application.ports.PaymentRecord;
import com.danasea.backend.modules.report.application.ports.RefundRecord;
import com.danasea.backend.modules.report.application.ports.ReportDataPort;
import com.danasea.backend.modules.report.application.ports.ReviewRecord;
import com.danasea.backend.modules.report.application.ports.ServiceSlotRecord;
import com.danasea.backend.modules.report.application.ports.SubOrderRecord;
import com.danasea.backend.modules.report.application.ports.VendorRecord;
import com.danasea.backend.modules.report.domain.models.TimeRange;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter triển khai ReportDataPort sử dụng JPA EntityManager, hỗ trợ đầy đủ các truy vấn
 * DB-agnostic (chạy được trên cả H2 in-memory và PostgreSQL).
 */
@Repository
@Transactional(readOnly = true)
public class JpaReportDataAdapter implements ReportDataPort {

    @PersistenceContext private EntityManager entityManager;

    public JpaReportDataAdapter() {}

    public JpaReportDataAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<SubOrderRecord> findSubOrders(TimeRange timeRange, UUID vendorId) {
        Objects.requireNonNull(timeRange, "timeRange cannot be null");

        String jpql;
        if (vendorId != null) {
            jpql =
                    "SELECT s FROM SubOrderJpaEntity s "
                            + "WHERE s.createdAt >= :start AND s.createdAt < :end "
                            + "AND s.vendorId = :vendorId "
                            + "ORDER BY s.createdAt ASC";
        } else {
            jpql =
                    "SELECT s FROM SubOrderJpaEntity s "
                            + "WHERE s.createdAt >= :start AND s.createdAt < :end "
                            + "ORDER BY s.createdAt ASC";
        }

        TypedQuery<SubOrderJpaEntity> query =
                entityManager
                        .createQuery(jpql, SubOrderJpaEntity.class)
                        .setParameter("start", timeRange.getStartDateTime())
                        .setParameter("end", timeRange.getEndExclusiveDateTime());

        if (vendorId != null) {
            query.setParameter("vendorId", vendorId);
        }

        return query.getResultList().stream().map(this::toSubOrderRecord).toList();
    }

    @Override
    public List<SubOrderRecord> findSubOrdersByMasterOrderIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return entityManager
                .createQuery(
                        "SELECT s FROM SubOrderJpaEntity s WHERE s.masterOrderId IN :ids",
                        SubOrderJpaEntity.class)
                .setParameter("ids", ids)
                .getResultList()
                .stream()
                .map(this::toSubOrderRecord)
                .toList();
    }

    @Override
    public List<SubOrderRecord> findSubOrdersByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return entityManager
                .createQuery(
                        "SELECT s FROM SubOrderJpaEntity s WHERE s.id IN :ids",
                        SubOrderJpaEntity.class)
                .setParameter("ids", ids)
                .getResultList()
                .stream()
                .map(this::toSubOrderRecord)
                .toList();
    }

    @Override
    public List<MasterOrderRecord> findMasterOrders(TimeRange timeRange) {
        Objects.requireNonNull(timeRange, "timeRange cannot be null");

        String jpql =
                "SELECT m FROM MasterOrderJpaEntity m "
                        + "WHERE m.createdAt >= :start AND m.createdAt < :end "
                        + "ORDER BY m.createdAt ASC";

        return entityManager
                .createQuery(jpql, MasterOrderJpaEntity.class)
                .setParameter("start", timeRange.getStartDateTime())
                .setParameter("end", timeRange.getEndExclusiveDateTime())
                .getResultList()
                .stream()
                .map(this::toMasterOrderRecord)
                .toList();
    }

    @Override
    public List<MasterOrderRecord> findMasterOrdersByIds(Collection<UUID> masterOrderIds) {
        if (masterOrderIds == null || masterOrderIds.isEmpty()) {
            return Collections.emptyList();
        }

        String jpql = "SELECT m FROM MasterOrderJpaEntity m WHERE m.id IN :ids";
        return entityManager
                .createQuery(jpql, MasterOrderJpaEntity.class)
                .setParameter("ids", masterOrderIds)
                .getResultList()
                .stream()
                .map(this::toMasterOrderRecord)
                .toList();
    }

    @Override
    public List<PaymentRecord> findPayments(TimeRange timeRange) {
        Objects.requireNonNull(timeRange, "timeRange cannot be null");

        String jpql =
                "SELECT p FROM PaymentJpaEntity p WHERE p.status IN"
                    + " (com.danasea.backend.modules.order.domain.models.PaymentStatus.SUCCESS,"
                    + " com.danasea.backend.modules.order.domain.models.PaymentStatus.REFUNDED) AND"
                    + " p.paidAt >= :start AND p.paidAt < :end ORDER BY p.paidAt ASC";

        return entityManager
                .createQuery(jpql, PaymentJpaEntity.class)
                .setParameter("start", timeRange.getStartDateTime())
                .setParameter("end", timeRange.getEndExclusiveDateTime())
                .getResultList()
                .stream()
                .map(this::toPaymentRecord)
                .toList();
    }

    @Override
    public List<PaymentRecord> findPaymentsByMasterOrderIds(Collection<UUID> masterOrderIds) {
        if (masterOrderIds == null || masterOrderIds.isEmpty()) {
            return Collections.emptyList();
        }

        String jpql = "SELECT p FROM PaymentJpaEntity p WHERE p.masterOrderId IN :ids";
        return entityManager
                .createQuery(jpql, PaymentJpaEntity.class)
                .setParameter("ids", masterOrderIds)
                .getResultList()
                .stream()
                .map(this::toPaymentRecord)
                .toList();
    }

    @Override
    public List<RefundRecord> findRefunds(TimeRange timeRange, UUID vendorId) {
        Objects.requireNonNull(timeRange, "timeRange cannot be null");

        String jpql;
        if (vendorId != null) {
            jpql =
                    "SELECT r FROM RefundJpaEntity r, SubOrderJpaEntity s WHERE r.status ="
                        + " com.danasea.backend.modules.order.domain.models.RefundStatus.PROCESSED"
                        + " AND r.subOrderId = s.id AND r.processedAt >= :start AND r.processedAt <"
                        + " :end AND s.vendorId = :vendorId ORDER BY r.processedAt ASC";
        } else {
            jpql =
                    "SELECT r FROM RefundJpaEntity r WHERE r.status ="
                        + " com.danasea.backend.modules.order.domain.models.RefundStatus.PROCESSED"
                        + " AND r.processedAt >= :start AND r.processedAt < :end ORDER BY"
                        + " r.processedAt ASC";
        }

        TypedQuery<RefundJpaEntity> query =
                entityManager
                        .createQuery(jpql, RefundJpaEntity.class)
                        .setParameter("start", timeRange.getStartDateTime())
                        .setParameter("end", timeRange.getEndExclusiveDateTime());

        if (vendorId != null) {
            query.setParameter("vendorId", vendorId);
        }

        return query.getResultList().stream().map(this::toRefundRecord).toList();
    }

    @Override
    public List<RefundRecord> findRefundsBySubOrderIds(Collection<UUID> subOrderIds) {
        if (subOrderIds == null || subOrderIds.isEmpty()) {
            return Collections.emptyList();
        }

        String jpql = "SELECT r FROM RefundJpaEntity r WHERE r.subOrderId IN :subOrderIds";
        return entityManager
                .createQuery(jpql, RefundJpaEntity.class)
                .setParameter("subOrderIds", subOrderIds)
                .getResultList()
                .stream()
                .map(this::toRefundRecord)
                .toList();
    }

    @Override
    public Map<RefundReason, Long> countRefundsByReason(TimeRange timeRange, UUID vendorId) {
        Objects.requireNonNull(timeRange, "timeRange cannot be null");

        String jpql;
        if (vendorId != null) {
            jpql =
                    "SELECT r.reason, COUNT(r) FROM RefundJpaEntity r, SubOrderJpaEntity s "
                            + "WHERE r.subOrderId = s.id "
                            + "AND r.createdAt >= :start AND r.createdAt < :end "
                            + "AND s.vendorId = :vendorId "
                            + "GROUP BY r.reason";
        } else {
            jpql =
                    "SELECT r.reason, COUNT(r) FROM RefundJpaEntity r "
                            + "WHERE r.createdAt >= :start AND r.createdAt < :end "
                            + "GROUP BY r.reason";
        }

        TypedQuery<Object[]> query =
                entityManager
                        .createQuery(jpql, Object[].class)
                        .setParameter("start", timeRange.getStartDateTime())
                        .setParameter("end", timeRange.getEndExclusiveDateTime());

        if (vendorId != null) {
            query.setParameter("vendorId", vendorId);
        }

        Map<RefundReason, Long> resultMap = new EnumMap<>(RefundReason.class);
        for (Object[] row : query.getResultList()) {
            RefundReason reason = (RefundReason) row[0];
            Long count = (Long) row[1];
            if (reason != null && count != null) {
                resultMap.put(reason, count);
            }
        }

        return resultMap;
    }

    @Override
    public List<ServiceSlotRecord> findServiceSlots(TimeRange timeRange, UUID vendorId) {
        Objects.requireNonNull(timeRange, "timeRange cannot be null");

        String jpql;
        if (vendorId != null) {
            jpql =
                    "SELECT s, srv.vendorId FROM ServiceSlotJpaEntity s, ServiceJpaEntity srv "
                            + "WHERE s.serviceId = srv.id "
                            + "AND s.date >= :fromDate AND s.date <= :toDate "
                            + "AND srv.vendorId = :vendorId "
                            + "ORDER BY s.date ASC, s.startTime ASC";
        } else {
            jpql =
                    "SELECT s, srv.vendorId FROM ServiceSlotJpaEntity s, ServiceJpaEntity srv "
                            + "WHERE s.serviceId = srv.id "
                            + "AND s.date >= :fromDate AND s.date <= :toDate "
                            + "ORDER BY s.date ASC, s.startTime ASC";
        }

        TypedQuery<Object[]> query =
                entityManager
                        .createQuery(jpql, Object[].class)
                        .setParameter("fromDate", timeRange.getFrom())
                        .setParameter("toDate", timeRange.getTo());

        if (vendorId != null) {
            query.setParameter("vendorId", vendorId);
        }

        return query.getResultList().stream()
                .map(
                        row -> {
                            ServiceSlotJpaEntity slot = (ServiceSlotJpaEntity) row[0];
                            UUID slotVendorId = (UUID) row[1];
                            return new ServiceSlotRecord(
                                    slot.getId(),
                                    slot.getServiceId(),
                                    slotVendorId,
                                    slot.getDate(),
                                    slot.getStartTime(),
                                    slot.getEndTime(),
                                    slot.getCapacity(),
                                    slot.getBookedCount(),
                                    slot.getStatus());
                        })
                .toList();
    }

    @Override
    public List<VendorRecord> findApprovedVendors() {
        String jpql =
                "SELECT v FROM VendorJpaEntity v "
                        + "WHERE v.verificationStatus = :status "
                        + "ORDER BY v.businessName ASC";

        return entityManager
                .createQuery(jpql, VendorJpaEntity.class)
                .setParameter("status", VerificationStatus.APPROVED)
                .getResultList()
                .stream()
                .map(this::toVendorRecord)
                .toList();
    }

    @Override
    public Optional<VendorRecord> findVendorById(UUID vendorId) {
        if (vendorId == null) {
            return Optional.empty();
        }
        VendorJpaEntity entity = entityManager.find(VendorJpaEntity.class, vendorId);
        return Optional.ofNullable(entity).map(this::toVendorRecord);
    }

    @Override
    public Optional<VendorRecord> findVendorByUserId(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        String jpql = "SELECT v FROM VendorJpaEntity v WHERE v.userId = :userId";
        List<VendorJpaEntity> list =
                entityManager
                        .createQuery(jpql, VendorJpaEntity.class)
                        .setParameter("userId", userId)
                        .setMaxResults(1)
                        .getResultList();

        return list.isEmpty() ? Optional.empty() : Optional.of(toVendorRecord(list.getFirst()));
    }

    @Override
    public List<ReviewRecord> findReviews(TimeRange timeRange, UUID vendorId) {
        Objects.requireNonNull(timeRange, "timeRange cannot be null");

        String jpql;
        if (vendorId != null) {
            jpql =
                    "SELECT r FROM ReviewJpaEntity r "
                            + "WHERE r.createdAt >= :start AND r.createdAt < :end "
                            + "AND r.vendorId = :vendorId "
                            + "ORDER BY r.createdAt ASC";
        } else {
            jpql =
                    "SELECT r FROM ReviewJpaEntity r "
                            + "WHERE r.createdAt >= :start AND r.createdAt < :end "
                            + "ORDER BY r.createdAt ASC";
        }

        TypedQuery<ReviewJpaEntity> query =
                entityManager
                        .createQuery(jpql, ReviewJpaEntity.class)
                        .setParameter("start", timeRange.getStartDateTime())
                        .setParameter("end", timeRange.getEndExclusiveDateTime());

        if (vendorId != null) {
            query.setParameter("vendorId", vendorId);
        }

        return query.getResultList().stream().map(this::toReviewRecord).toList();
    }

    private SubOrderRecord toSubOrderRecord(SubOrderJpaEntity s) {
        return new SubOrderRecord(
                s.getId(),
                s.getMasterOrderId(),
                s.getVendorId(),
                s.getServiceId(),
                s.getSlotId(),
                s.getQuantity(),
                s.getUnitPrice(),
                s.getSubtotalAmount(),
                s.getCommissionRate(),
                s.getCommissionAmount(),
                s.getVendorPayoutAmount(),
                s.getStatus(),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                s.getCancellationReason(),
                s.getFinalAmount(),
                s.getPlatformDiscountAmount(),
                s.getCommissionBasisAmount(),
                s.getVendorDiscountAmount());
    }

    private MasterOrderRecord toMasterOrderRecord(MasterOrderJpaEntity m) {
        return new MasterOrderRecord(
                m.getId(),
                m.getBookingId(),
                m.getCustomerId(),
                m.getStatus(),
                m.getTotalAmount(),
                m.getDiscountAmount(),
                m.getDiscountCodeId(),
                m.getPaymentStatus(),
                m.getCreatedAt(),
                m.getUpdatedAt());
    }

    private PaymentRecord toPaymentRecord(PaymentJpaEntity p) {
        return new PaymentRecord(
                p.getId(),
                p.getMasterOrderId(),
                p.getAmount(),
                p.getStatus(),
                p.getProvider(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                p.getPaidAt());
    }

    private RefundRecord toRefundRecord(RefundJpaEntity r) {
        return new RefundRecord(
                r.getId(),
                r.getSubOrderId(),
                r.getPaymentId(),
                r.getAmount(),
                r.getRefundPercentage(),
                r.getReason(),
                r.getStatus(),
                r.getProcessedAt(),
                r.getCreatedAt());
    }

    private VendorRecord toVendorRecord(VendorJpaEntity v) {
        return new VendorRecord(
                v.getId(),
                v.getUserId(),
                v.getBusinessName(),
                v.getVerificationStatus(),
                v.getRatingAvg(),
                v.getRatingCount(),
                v.getBadgeTier());
    }

    private ReviewRecord toReviewRecord(ReviewJpaEntity r) {
        return new ReviewRecord(
                r.getId(),
                r.getSubOrderId(),
                r.getVendorId(),
                r.getServiceId(),
                r.getRating(),
                r.getCreatedAt());
    }
}
