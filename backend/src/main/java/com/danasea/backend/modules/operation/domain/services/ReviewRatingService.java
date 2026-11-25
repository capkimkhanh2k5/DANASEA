package com.danasea.backend.modules.operation.domain.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaReviewRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorNotFoundException;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewRatingService {

    public static final int VERIFIED_MIN_REVIEWS = 1;
    public static final BigDecimal VERIFIED_MIN_RATING = new BigDecimal("3.50");
    public static final int TOP_RATED_MIN_REVIEWS = 5;
    public static final BigDecimal TOP_RATED_MIN_RATING = new BigDecimal("4.50");

    private final JpaReviewRepository reviewRepository;
    private final JpaServiceRepository serviceRepository;
    private final JpaVendorRepository vendorRepository;

    @Transactional
    public void lockVendor(UUID vendorId) {
        vendorRepository.findByIdForUpdate(vendorId)
                .orElseThrow(VendorNotFoundException::new);
    }

    @Transactional
    public void recalculateRatings(UUID serviceId, UUID vendorId) {
        if (vendorId != null) {
            lockVendor(vendorId);
        }
        if (serviceId != null) {
            recalculateServiceRating(serviceId);
        }
        if (vendorId != null) {
            recalculateVendorRating(vendorId);
        }
    }

    private void recalculateServiceRating(UUID serviceId) {
        ServiceJpaEntity serviceEntity = serviceRepository.findByIdForUpdate(serviceId).orElse(null);
        if (serviceEntity == null) {
            log.warn("Service not found for rating recalculation: {}", serviceId);
            return;
        }

        long count = reviewRepository.countByServiceIdAndIsVisibleTrue(serviceId);
        Double avg = reviewRepository.getAvgRatingByServiceId(serviceId);
        BigDecimal avgRating = (avg != null && count > 0)
                ? BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        serviceEntity.setAvgRating(avgRating);
        serviceEntity.setRatingCount((int) count);
        serviceRepository.save(serviceEntity);
        log.info("Recalculated Service [{}] - avgRating: {}, ratingCount: {}", serviceId, avgRating, count);
    }

    private void recalculateVendorRating(UUID vendorId) {
        VendorJpaEntity vendorEntity = vendorRepository.findByIdForUpdate(vendorId).orElse(null);
        if (vendorEntity == null) {
            log.warn("Vendor not found for rating recalculation: {}", vendorId);
            return;
        }

        long count = reviewRepository.countByVendorIdAndIsVisibleTrue(vendorId);
        Double avg = reviewRepository.getAvgRatingByVendorId(vendorId);
        BigDecimal ratingAvg = (avg != null && count > 0)
                ? BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BadgeTier badgeTier = deriveBadgeTier(count, ratingAvg);

        vendorEntity.setRatingAvg(ratingAvg);
        vendorEntity.setRatingCount((int) count);
        vendorEntity.setBadgeTier(badgeTier);
        vendorRepository.save(vendorEntity);
        log.info("Recalculated Vendor [{}] - ratingAvg: {}, ratingCount: {}, badgeTier: {}", vendorId, ratingAvg, count, badgeTier);
    }

    public static BadgeTier deriveBadgeTier(long visibleReviewCount, BigDecimal avgRating) {
        BigDecimal rating = avgRating != null ? avgRating : BigDecimal.ZERO;
        if (visibleReviewCount >= TOP_RATED_MIN_REVIEWS && rating.compareTo(TOP_RATED_MIN_RATING) >= 0) {
            return BadgeTier.TOP_RATED;
        }
        if (visibleReviewCount >= VERIFIED_MIN_REVIEWS && rating.compareTo(VERIFIED_MIN_RATING) >= 0) {
            return BadgeTier.VERIFIED;
        }
        return BadgeTier.NONE;
    }
}
