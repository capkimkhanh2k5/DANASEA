package com.danasea.backend.modules.service.application.services;

import com.danasea.backend.modules.flashsale.application.FlashSaleService.PublicOffer;
import com.danasea.backend.modules.order.domain.models.DiscountType;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.DiscountCodeJpaEntity;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Composite ranking and effective pricing engine for public service discovery.
 *
 * <p>Recommended score formula (normalized to [0, 100]):
 * <pre>
 * Score(s) = 100 * (0.45 * S_quality + 0.15 * S_trust + 0.15 * S_pop + 0.25 * S_promo)
 * </pre>
 * <ul>
 *   <li>S_quality: Bayesian weighted rating with prior C=4.0 and confidence m=10.</li>
 *   <li>S_trust: Vendor badge tier (TOP_RATED=1.0, VERIFIED=0.6, NONE=0.0).</li>
 *   <li>S_pop: Log-saturated paid booking count ln(1+B)/ln(21).</li>
 *   <li>S_promo: Quality-gated stackable promotion boost min(1.0, S_flash + 0.35 * S_vendor_voucher).</li>
 * </ul>
 */
@Component
public class ServiceRankingEngine {

    static final double PRIOR_MEAN_C = 4.0;
    static final double PRIOR_COUNT_M = 10.0;
    static final double POPULARITY_REF_LOG = Math.log1p(20.0);

    static final double WEIGHT_QUALITY = 0.45;
    static final double WEIGHT_TRUST = 0.15;
    static final double WEIGHT_POPULARITY = 0.15;
    static final double WEIGHT_PROMO = 0.25;

    static final int QUALITY_GATE_MIN_REVIEWS = 5;
    static final double QUALITY_GATE_ZERO_THRESHOLD = 3.0;
    static final double QUALITY_GATE_DAMPED_THRESHOLD = 3.5;
    static final double QUALITY_GATE_DAMPED_FACTOR = 0.85;

    public BigDecimal effectivePrice(Service service, List<PublicOffer> serviceOffers) {
        BigDecimal basePrice = service != null && service.getPrice() != null ? service.getPrice() : BigDecimal.ZERO;
        if (serviceOffers == null || serviceOffers.isEmpty()) {
            return basePrice;
        }
        BigDecimal minPrice = basePrice;
        for (PublicOffer offer : serviceOffers) {
            if (offer != null && offer.salePrice() != null && offer.remainingQuota() > 0) {
                if (minPrice.compareTo(BigDecimal.ZERO) <= 0 || offer.salePrice().compareTo(minPrice) < 0) {
                    minPrice = offer.salePrice();
                }
            }
        }
        return minPrice;
    }

    public boolean isOnSale(
            Service service,
            List<PublicOffer> serviceOffers,
            List<DiscountCodeJpaEntity> serviceVouchers) {
        boolean hasFlashSale = serviceOffers != null && serviceOffers.stream()
                .anyMatch(o -> o != null && o.remainingQuota() > 0 && "AVAILABLE".equals(o.availability()));
        boolean hasVendorVoucher = serviceVouchers != null && !serviceVouchers.isEmpty();
        return hasFlashSale || hasVendorVoucher;
    }

    public List<DiscountCodeJpaEntity> matchingVendorVouchers(
            Service service,
            List<DiscountCodeJpaEntity> activeVendorVouchers) {
        if (service == null || service.getVendorId() == null || activeVendorVouchers == null || activeVendorVouchers.isEmpty()) {
            return List.of();
        }
        UUID vendorId = service.getVendorId();
        UUID serviceId = service.getId();
        return activeVendorVouchers.stream()
                .filter(d -> d != null
                        && vendorId.equals(d.getVendorId())
                        && (d.getServiceId() == null || d.getServiceId().equals(serviceId)))
                .toList();
    }

    public double computeRecommendedScore(
            Service service,
            BadgeTier badgeTier,
            long paidBookings,
            List<PublicOffer> serviceOffers,
            List<DiscountCodeJpaEntity> serviceVouchers) {
        double rating = service.getAvgRating() != null ? service.getAvgRating().doubleValue() : 0.0;
        int reviewCount = service.getRatingCount() != null ? Math.max(0, service.getRatingCount()) : 0;

        // 1. Quality (45%): Bayesian weighted rating
        double bayesianRating = (reviewCount * rating + PRIOR_COUNT_M * PRIOR_MEAN_C) / (reviewCount + PRIOR_COUNT_M);
        double qualityScore = clamp01((bayesianRating - 1.0) / 4.0);

        // 2. Trust (15%): Vendor badge tier
        double trustScore = switch (badgeTier != null ? badgeTier : BadgeTier.NONE) {
            case TOP_RATED -> 1.0;
            case VERIFIED -> 0.6;
            default -> 0.0;
        };

        // 3. Popularity (15%): Log-saturated paid booking count
        double popularityScore = clamp01(Math.log1p(Math.max(0L, paidBookings)) / POPULARITY_REF_LOG);

        // 4. Promotion (25%): Quality-gated stackable boost
        double qualityGate = resolveQualityGate(rating, reviewCount);
        double flashScore = computeFlashSaleScore(serviceOffers);
        BigDecimal effPrice = effectivePrice(service, serviceOffers);
        double vendorVoucherScore = computeVendorVoucherScore(effPrice, serviceVouchers);
        double promoScore = qualityGate * Math.min(1.0, flashScore + 0.35 * vendorVoucherScore);

        return 100.0 * (
                WEIGHT_QUALITY * qualityScore
                        + WEIGHT_TRUST * trustScore
                        + WEIGHT_POPULARITY * popularityScore
                        + WEIGHT_PROMO * promoScore
        );
    }

    public Comparator<Service> recommendedComparator(
            Map<UUID, BadgeTier> badgeTiers,
            Map<UUID, Long> paidBookingsByService,
            Map<UUID, List<PublicOffer>> offersByService,
            List<DiscountCodeJpaEntity> activeVendorVouchers) {
        return (a, b) -> {
            double scoreA = computeRecommendedScore(
                    a,
                    badgeTiers.getOrDefault(a.getVendorId(), BadgeTier.NONE),
                    paidBookingsByService.getOrDefault(a.getId(), 0L),
                    offersByService.getOrDefault(a.getId(), List.of()),
                    matchingVendorVouchers(a, activeVendorVouchers)
            );
            double scoreB = computeRecommendedScore(
                    b,
                    badgeTiers.getOrDefault(b.getVendorId(), BadgeTier.NONE),
                    paidBookingsByService.getOrDefault(b.getId(), 0L),
                    offersByService.getOrDefault(b.getId(), List.of()),
                    matchingVendorVouchers(b, activeVendorVouchers)
            );
            int byScore = Double.compare(scoreB, scoreA);
            if (byScore != 0) {
                return byScore;
            }
            OffsetDateTime createdA = a.getCreatedAt();
            OffsetDateTime createdB = b.getCreatedAt();
            if (createdA != null && createdB != null) {
                int byCreated = createdB.compareTo(createdA);
                if (byCreated != 0) {
                    return byCreated;
                }
            } else if (createdA != null || createdB != null) {
                return createdB != null ? 1 : -1;
            }
            if (a.getId() != null && b.getId() != null) {
                return a.getId().compareTo(b.getId());
            }
            return 0;
        };
    }

    private double resolveQualityGate(double rating, int reviewCount) {
        if (reviewCount >= QUALITY_GATE_MIN_REVIEWS && rating < QUALITY_GATE_ZERO_THRESHOLD) {
            return 0.0;
        }
        if (reviewCount >= QUALITY_GATE_MIN_REVIEWS && rating < QUALITY_GATE_DAMPED_THRESHOLD) {
            return QUALITY_GATE_DAMPED_FACTOR;
        }
        return 1.0;
    }

    private double computeFlashSaleScore(List<PublicOffer> offers) {
        if (offers == null || offers.isEmpty()) {
            return 0.0;
        }
        double bestDepth = 0.0;
        boolean hasAvailable = false;
        for (PublicOffer offer : offers) {
            if (offer == null || offer.remainingQuota() <= 0 || !"AVAILABLE".equals(offer.availability())) {
                continue;
            }
            hasAvailable = true;
            double depth = 0.0;
            if (offer.actualDiscountPercent() != null) {
                depth = offer.actualDiscountPercent().doubleValue() / 100.0;
            } else if (offer.originalPrice() != null && offer.originalPrice().signum() > 0 && offer.actualDiscountAmount() != null) {
                depth = offer.actualDiscountAmount()
                        .divide(offer.originalPrice(), 4, RoundingMode.HALF_UP)
                        .doubleValue();
            }
            if (depth > bestDepth) {
                bestDepth = depth;
            }
        }
        if (!hasAvailable) {
            return 0.0;
        }
        return Math.min(1.0, 0.40 + 0.60 * Math.min(1.0, Math.max(0.0, bestDepth) / 0.30));
    }

    private double computeVendorVoucherScore(BigDecimal effectivePrice, List<DiscountCodeJpaEntity> vouchers) {
        if (effectivePrice == null || effectivePrice.signum() <= 0 || vouchers == null || vouchers.isEmpty()) {
            return 0.0;
        }
        double priceVal = effectivePrice.doubleValue();
        double bestScore = 0.0;
        for (DiscountCodeJpaEntity voucher : vouchers) {
            if (voucher == null || voucher.getDiscountValue() == null || voucher.getDiscountValue().signum() <= 0) {
                continue;
            }
            double discountAmount;
            if (voucher.getDiscountType() == DiscountType.PERCENTAGE) {
                discountAmount = priceVal * (voucher.getDiscountValue().doubleValue() / 100.0);
                if (voucher.getMaxDiscountAmount() != null && voucher.getMaxDiscountAmount().signum() > 0) {
                    discountAmount = Math.min(discountAmount, voucher.getMaxDiscountAmount().doubleValue());
                }
            } else {
                discountAmount = voucher.getDiscountValue().doubleValue();
            }
            double effPct = Math.min(1.0, Math.max(0.0, discountAmount / priceVal));
            double minOrder = voucher.getMinOrderAmount() != null ? voucher.getMinOrderAmount().doubleValue() : 0.0;
            double minOrderFactor = (minOrder <= 0.0 || priceVal >= minOrder)
                    ? 1.0
                    : 0.5 * (priceVal / minOrder);
            double score = 0.55 * Math.min(1.0, effPct / 0.25) * minOrderFactor;
            if (score > bestScore) {
                bestScore = score;
            }
        }
        return clamp01(bestScore);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
