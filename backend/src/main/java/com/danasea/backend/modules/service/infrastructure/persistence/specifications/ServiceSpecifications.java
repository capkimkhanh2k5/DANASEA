package com.danasea.backend.modules.service.infrastructure.persistence.specifications;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.service.application.dtos.SearchServicesCriteria;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

public final class ServiceSpecifications {

    private ServiceSpecifications() {
    }

    public static Specification<ServiceJpaEntity> orderByRecommended() {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                Subquery<Integer> badgeWeight = query.subquery(Integer.class);
                Root<VendorJpaEntity> vendor = badgeWeight.from(VendorJpaEntity.class);
                Expression<Integer> weightExpr = cb.<Integer>selectCase()
                        .when(cb.equal(vendor.get("badgeTier"), BadgeTier.TOP_RATED), 2)
                        .when(cb.equal(vendor.get("badgeTier"), BadgeTier.VERIFIED), 1)
                        .otherwise(0);
                badgeWeight.select(cb.max(weightExpr)).where(cb.equal(vendor.get("id"), root.get("vendorId")));
                query.orderBy(
                        cb.desc(cb.coalesce(badgeWeight, 0)),
                        cb.desc(cb.coalesce(root.get("avgRating"), BigDecimal.ZERO)),
                        cb.desc(cb.coalesce(root.get("ratingCount"), 0)),
                        cb.desc(root.get("createdAt")),
                        cb.asc(root.get("id"))
                );
            }
            return cb.conjunction();
        };
    }

    public static Specification<ServiceJpaEntity> hasVendorBadgeTier(BadgeTier badgeTier) {
        return (root, query, cb) -> {
            if (badgeTier == null) {
                return cb.conjunction();
            }
            Subquery<UUID> vendorSubquery = query.subquery(UUID.class);
            Root<VendorJpaEntity> vendorRoot = vendorSubquery.from(VendorJpaEntity.class);
            vendorSubquery.select(vendorRoot.get("id"))
                    .where(cb.equal(vendorRoot.get("badgeTier"), badgeTier));
            return root.get("vendorId").in(vendorSubquery);
        };
    }

    public static Specification<ServiceJpaEntity> orderByBookings() {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                Subquery<Long> bookings = query.subquery(Long.class);
                Root<SubOrderJpaEntity> subOrder = bookings.from(SubOrderJpaEntity.class);
                Root<MasterOrderJpaEntity> order = bookings.from(MasterOrderJpaEntity.class);
                bookings.select(cb.count(subOrder)).where(cb.equal(subOrder.get("serviceId"), root.get("id")),
                        cb.equal(subOrder.get("masterOrderId"), order.get("id")),
                        cb.equal(order.get("paymentStatus"), PaymentOrderStatus.PAID),
                        subOrder.get("status").in(SubOrderStatus.CONFIRMED, SubOrderStatus.CHECKED_IN,
                                SubOrderStatus.IN_PROGRESS, SubOrderStatus.COMPLETED));
                query.orderBy(cb.desc(bookings), cb.asc(root.get("id")));
            }
            return cb.conjunction();
        };
    }

    public static Specification<ServiceJpaEntity> isPublished() {
        return (root, query, cb) -> cb.equal(root.get("status"), ServiceStatus.PUBLISHED);
    }

    public static Specification<ServiceJpaEntity> hasCategory(UUID categoryId) {
        return (root, query, cb) -> {
            if (categoryId == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("categoryId"), categoryId);
        };
    }

    public static Specification<ServiceJpaEntity> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.trim().isEmpty()) {
                return cb.conjunction();
            }
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            Predicate nameMatch = cb.like(cb.lower(root.get("name")), pattern);
            Predicate nameEnMatch = cb.like(cb.lower(root.get("nameEn")), pattern);
            Predicate descMatch = cb.like(cb.lower(root.get("description")), pattern);
            return cb.or(nameMatch, nameEnMatch, descMatch);
        };
    }

    public static Specification<ServiceJpaEntity> inPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<ServiceJpaEntity> withinLocation(BigDecimal lat, BigDecimal lng, Double radiusKm) {
        return (root, query, cb) -> {
            if (lat == null || lng == null || radiusKm == null || radiusKm <= 0) {
                return cb.conjunction();
            }

            double latVal = lat.doubleValue();
            double lngVal = lng.doubleValue();
            double deltaLat = radiusKm / 111.0;
            double latRad = Math.toRadians(latVal);
            double cosLat = Math.cos(latRad);
            double deltaLng = (Math.abs(cosLat) > 0.0001) ? (radiusKm / (111.0 * Math.abs(cosLat))) : (radiusKm / 111.0);

            BigDecimal minLat = BigDecimal.valueOf(latVal - deltaLat);
            BigDecimal maxLat = BigDecimal.valueOf(latVal + deltaLat);
            BigDecimal minLng = BigDecimal.valueOf(lngVal - deltaLng);
            BigDecimal maxLng = BigDecimal.valueOf(lngVal + deltaLng);

            Predicate notNull = cb.and(root.get("latitude").isNotNull(), root.get("longitude").isNotNull());
            Predicate latBetween = cb.between(root.get("latitude"), minLat, maxLat);
            Predicate lngBetween = cb.between(root.get("longitude"), minLng, maxLng);

            return cb.and(notNull, latBetween, lngBetween);
        };
    }

    public static Specification<ServiceJpaEntity> hasVendor(UUID vendorId) {
        return (root, query, cb) -> {
            if (vendorId == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("vendorId"), vendorId);
        };
    }

    public static Specification<ServiceJpaEntity> hasMinRating(BigDecimal minRating) {
        return (root, query, cb) -> {
            if (minRating == null) {
                return cb.conjunction();
            }
            return cb.greaterThanOrEqualTo(root.get("avgRating"), minRating);
        };
    }

    public static Specification<ServiceJpaEntity> hasAvailableSlot(
            LocalDate date,
            LocalTime timeSlot,
            Integer guests
    ) {
        return (root, query, cb) -> {
            if (date == null && timeSlot == null && guests == null) {
                return cb.conjunction();
            }
            Subquery<UUID> slotSubquery = query.subquery(UUID.class);
            Root<ServiceSlotJpaEntity> slotRoot = slotSubquery.from(ServiceSlotJpaEntity.class);
            slotSubquery.select(slotRoot.get("serviceId"));
            List<Predicate> slotPreds = new ArrayList<>();
            slotPreds.add(cb.equal(slotRoot.get("status"), SlotStatus.OPEN));
            LocalDate today = LocalDate.now(Booking.VIETNAM_ZONE);
            LocalTime now = LocalTime.now(Booking.VIETNAM_ZONE);
            slotPreds.add(cb.or(cb.greaterThan(slotRoot.get("date"), today),
                    cb.and(cb.equal(slotRoot.get("date"), today), cb.greaterThan(slotRoot.get("startTime"), now))));
            if (date != null) {
                slotPreds.add(cb.equal(slotRoot.get("date"), date));
            }
            if (timeSlot != null) {
                slotPreds.add(cb.equal(slotRoot.get("startTime"), timeSlot));
            }
            slotSubquery.where(slotPreds.toArray(new Predicate[0]));
            return root.get("id").in(slotSubquery);
        };
    }

    public static Specification<ServiceJpaEntity> filter(
            UUID categoryId,
            String keyword,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            BigDecimal lat,
            BigDecimal lng,
            Double radiusKm
    ) {
        return filter(categoryId, keyword, minPrice, maxPrice, lat, lng, radiusKm, null, null, null, null, null);
    }

    public static Specification<ServiceJpaEntity> filter(SearchServicesCriteria criteria) {
        if (criteria == null) {
            return isPublished();
        }
        Specification<ServiceJpaEntity> base = filter(
                criteria.getCategoryId(),
                criteria.getKeyword(),
                criteria.getMinPrice(),
                criteria.getMaxPrice(),
                criteria.getLat(),
                criteria.getLng(),
                criteria.getRadiusKm(),
                criteria.getDate(),
                criteria.getTimeSlot(),
                criteria.getGuests(),
                criteria.getVendorId(),
                criteria.getMinRating()
        );
        return criteria.getBadgeTier() == null ? base : base.and(hasVendorBadgeTier(criteria.getBadgeTier()));
    }

    public static Specification<ServiceJpaEntity> filter(
            UUID categoryId,
            String keyword,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            BigDecimal lat,
            BigDecimal lng,
            Double radiusKm,
            LocalDate date,
            LocalTime timeSlot,
            Integer guests,
            UUID vendorId,
            BigDecimal minRating
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Mandatory status = PUBLISHED in all queries
            predicates.add(cb.equal(root.get("status"), ServiceStatus.PUBLISHED));

            // 2. categoryId
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("categoryId"), categoryId));
            }

            // 3. keyword case-insensitive match on name, nameEn, description
            if (keyword != null && !keyword.trim().isEmpty()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                Predicate nameMatch = cb.like(cb.lower(root.get("name")), pattern);
                Predicate nameEnMatch = cb.like(cb.lower(root.get("nameEn")), pattern);
                Predicate descMatch = cb.like(cb.lower(root.get("description")), pattern);
                predicates.add(cb.or(nameMatch, nameEnMatch, descMatch));
            }

            // 4. Price range
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }

            // 5. Location bounding box
            if (lat != null && lng != null && radiusKm != null && radiusKm > 0) {
                double latVal = lat.doubleValue();
                double lngVal = lng.doubleValue();
                double deltaLat = radiusKm / 111.0;
                double latRad = Math.toRadians(latVal);
                double cosLat = Math.cos(latRad);
                double deltaLng = (Math.abs(cosLat) > 0.0001) ? (radiusKm / (111.0 * Math.abs(cosLat))) : (radiusKm / 111.0);

                BigDecimal minLat = BigDecimal.valueOf(latVal - deltaLat);
                BigDecimal maxLat = BigDecimal.valueOf(latVal + deltaLat);
                BigDecimal minLng = BigDecimal.valueOf(lngVal - deltaLng);
                BigDecimal maxLng = BigDecimal.valueOf(lngVal + deltaLng);

                Predicate notNull = cb.and(root.get("latitude").isNotNull(), root.get("longitude").isNotNull());
                Predicate latBetween = cb.between(root.get("latitude"), minLat, maxLat);
                Predicate lngBetween = cb.between(root.get("longitude"), minLng, maxLng);
                predicates.add(cb.and(notNull, latBetween, lngBetween));
            }

            // 6. Vendor ID
            if (vendorId != null) {
                predicates.add(cb.equal(root.get("vendorId"), vendorId));
            }

            // 7. Minimum Rating
            if (minRating != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("avgRating"), minRating));
            }

            // 8. Slot criteria (date, timeSlot, guests)
            if (date != null || timeSlot != null || guests != null) {
                Subquery<UUID> slotSubquery = query.subquery(UUID.class);
                Root<ServiceSlotJpaEntity> slotRoot = slotSubquery.from(ServiceSlotJpaEntity.class);
                slotSubquery.select(slotRoot.get("serviceId"));
                List<Predicate> slotPreds = new ArrayList<>();
                slotPreds.add(cb.equal(slotRoot.get("status"), SlotStatus.OPEN));
                LocalDate today = LocalDate.now(Booking.VIETNAM_ZONE);
                LocalTime now = LocalTime.now(Booking.VIETNAM_ZONE);
                slotPreds.add(cb.or(cb.greaterThan(slotRoot.get("date"), today),
                        cb.and(cb.equal(slotRoot.get("date"), today), cb.greaterThan(slotRoot.get("startTime"), now))));
                if (date != null) {
                    slotPreds.add(cb.equal(slotRoot.get("date"), date));
                }
                if (timeSlot != null) {
                    slotPreds.add(cb.equal(slotRoot.get("startTime"), timeSlot));
                }
                slotSubquery.where(slotPreds.toArray(new Predicate[0]));
                predicates.add(root.get("id").in(slotSubquery));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
