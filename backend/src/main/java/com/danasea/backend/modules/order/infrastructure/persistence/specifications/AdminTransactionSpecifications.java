package com.danasea.backend.modules.order.infrastructure.persistence.specifications;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

public final class AdminTransactionSpecifications {
    private AdminTransactionSpecifications() { }

    public static Specification<MasterOrderJpaEntity> orders(MasterOrderStatus status, PaymentOrderStatus paymentStatus,
            UUID customerId, UUID vendorId, OffsetDateTime from, OffsetDateTime to) {
        return (root, query, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            equal(filters, cb, root.get("status"), status);
            equal(filters, cb, root.get("paymentStatus"), paymentStatus);
            equal(filters, cb, root.get("customerId"), customerId);
            dates(filters, cb, root.get("createdAt"), from, to);
            if (vendorId != null) {
                var exists = query.subquery(Integer.class);
                var sub = exists.from(SubOrderJpaEntity.class);
                exists.select(cb.literal(1)).where(cb.equal(sub.get("masterOrderId"), root.get("id")),
                        cb.equal(sub.get("vendorId"), vendorId));
                filters.add(cb.exists(exists));
            }
            return cb.and(filters.toArray(Predicate[]::new));
        };
    }

    public static Specification<PaymentJpaEntity> payments(PaymentStatus status, PaymentProvider provider,
            UUID orderId, UUID customerId, UUID vendorId, OffsetDateTime from, OffsetDateTime to) {
        return (root, query, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            equal(filters, cb, root.get("status"), status);
            equal(filters, cb, root.get("provider"), provider);
            equal(filters, cb, root.get("masterOrderId"), orderId);
            dates(filters, cb, root.get("createdAt"), from, to);
            if (customerId != null) {
                var exists = query.subquery(Integer.class);
                var order = exists.from(MasterOrderJpaEntity.class);
                exists.select(cb.literal(1)).where(cb.equal(order.get("id"), root.get("masterOrderId")),
                        cb.equal(order.get("customerId"), customerId));
                filters.add(cb.exists(exists));
            }
            if (vendorId != null) {
                var exists = query.subquery(Integer.class);
                var sub = exists.from(SubOrderJpaEntity.class);
                exists.select(cb.literal(1)).where(cb.equal(sub.get("masterOrderId"), root.get("masterOrderId")),
                        cb.equal(sub.get("vendorId"), vendorId));
                filters.add(cb.exists(exists));
            }
            return cb.and(filters.toArray(Predicate[]::new));
        };
    }

    public static Specification<RefundJpaEntity> refunds(RefundStatus status, RefundReason reason, UUID subOrderId,
            PaymentProvider provider, UUID vendorId, UUID customerId, UUID orderId, OffsetDateTime from, OffsetDateTime to) {
        return (root, query, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            equal(filters, cb, root.get("status"), status);
            equal(filters, cb, root.get("reason"), reason);
            equal(filters, cb, root.get("subOrderId"), subOrderId);
            equal(filters, cb, root.get("provider"), provider);
            dates(filters, cb, root.get("createdAt"), from, to);
            if (vendorId != null || customerId != null || orderId != null) {
                var exists = query.subquery(Integer.class);
                var sub = exists.from(SubOrderJpaEntity.class);
                List<Predicate> related = new ArrayList<>();
                related.add(cb.equal(sub.get("id"), root.get("subOrderId")));
                equal(related, cb, sub.get("vendorId"), vendorId);
                equal(related, cb, sub.get("masterOrderId"), orderId);
                if (customerId != null) {
                    var order = exists.from(MasterOrderJpaEntity.class);
                    related.add(cb.equal(order.get("id"), sub.get("masterOrderId")));
                    related.add(cb.equal(order.get("customerId"), customerId));
                }
                exists.select(cb.literal(1)).where(related.toArray(Predicate[]::new));
                if (vendorId == null) {
                    var paymentExists = query.subquery(Integer.class);
                    var payment = paymentExists.from(PaymentJpaEntity.class);
                    List<Predicate> paymentFilters = new ArrayList<>();
                    paymentFilters.add(cb.equal(payment.get("id"), root.get("paymentId")));
                    equal(paymentFilters, cb, payment.get("masterOrderId"), orderId);
                    if (customerId != null) {
                        var order = paymentExists.from(MasterOrderJpaEntity.class);
                        paymentFilters.add(cb.equal(order.get("id"), payment.get("masterOrderId")));
                        paymentFilters.add(cb.equal(order.get("customerId"), customerId));
                    }
                    paymentExists.select(cb.literal(1)).where(paymentFilters.toArray(Predicate[]::new));
                    filters.add(cb.or(cb.exists(exists), cb.and(cb.isNull(root.get("subOrderId")), cb.exists(paymentExists))));
                } else {
                    filters.add(cb.exists(exists));
                }
            }
            return cb.and(filters.toArray(Predicate[]::new));
        };
    }

    private static void equal(List<Predicate> filters, CriteriaBuilder cb, Path<?> path, Object value) {
        if (value != null) {
            filters.add(cb.equal(path, value));
        }
    }

    private static void dates(List<Predicate> filters, CriteriaBuilder cb, Path<OffsetDateTime> createdAt,
            OffsetDateTime from, OffsetDateTime to) {
        if (from != null) {
            filters.add(cb.greaterThanOrEqualTo(createdAt, from));
        }
        if (to != null) {
            filters.add(cb.lessThanOrEqualTo(createdAt, to));
        }
    }
}
