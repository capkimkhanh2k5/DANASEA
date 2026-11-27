package com.danasea.backend.modules.service.application.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.danasea.backend.modules.flashsale.application.FlashSaleService.PublicOffer;
import com.danasea.backend.modules.order.domain.models.DiscountScope;
import com.danasea.backend.modules.order.domain.models.DiscountSponsorType;
import com.danasea.backend.modules.order.domain.models.DiscountType;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.DiscountCodeJpaEntity;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServiceRankingEngineTest {

    private final ServiceRankingEngine engine = new ServiceRankingEngine();

    @Test
    void effectivePrice_returnsFlashSalePriceWhenAvailableAndLower() {
        UUID serviceId = UUID.randomUUID();
        Service svc = service(serviceId, UUID.randomUUID(), "650000", "3.35", 20);
        PublicOffer offer = offer(serviceId, "650000", "500000", "23.0769", 10);

        assertThat(engine.effectivePrice(svc, List.of(offer))).isEqualByComparingTo("500000");
        assertThat(engine.effectivePrice(svc, List.of())).isEqualByComparingTo("650000");
    }

    @Test
    void recommendedScore_boostsFlashSaleServiceOverMidTierWhileRespectingTopRatedExcellence() {
        UUID vendorTop = UUID.randomUUID();
        UUID vendorFlash = UUID.randomUUID();
        UUID vendorMid = UUID.randomUUID();

        Service excellent = service(UUID.randomUUID(), vendorTop, "500000", "5.00", 19);
        Service flashSaleSvc = service(UUID.randomUUID(), vendorFlash, "650000", "3.35", 20);
        Service midTier = service(UUID.randomUUID(), vendorMid, "550000", "4.32", 19);

        PublicOffer fsOffer = offer(flashSaleSvc.getId(), "650000", "500000", "23.0769", 20);
        DiscountCodeJpaEntity kajinVoucher = vendorVoucher(vendorFlash, null, DiscountType.FIXED, "20000", "250000", null);

        List<Service> list = new ArrayList<>(List.of(midTier, flashSaleSvc, excellent));
        list.sort(engine.recommendedComparator(
                Map.of(vendorTop, BadgeTier.TOP_RATED, vendorFlash, BadgeTier.VERIFIED, vendorMid, BadgeTier.VERIFIED),
                Map.of(excellent.getId(), 3L, flashSaleSvc.getId(), 2L, midTier.getId(), 2L),
                Map.of(flashSaleSvc.getId(), List.of(fsOffer)),
                List.of(kajinVoucher)
        ));

        assertThat(list).containsExactly(excellent, flashSaleSvc, midTier);
    }

    @Test
    void qualityGate_blocksPromoBoostWhenRatingBelowThreeWithAtLeastFiveReviews() {
        UUID vendorA = UUID.randomUUID();
        UUID vendorB = UUID.randomUUID();

        Service poorRated = service(UUID.randomUUID(), vendorA, "500000", "2.50", 10);
        Service normal = service(UUID.randomUUID(), vendorB, "500000", "3.80", 10);

        PublicOffer hugeFlashSale = offer(poorRated.getId(), "500000", "250000", "50.0", 20);
        double scorePoorWithPromo = engine.computeRecommendedScore(
                poorRated, BadgeTier.VERIFIED, 1, List.of(hugeFlashSale), List.of());
        double scorePoorWithoutPromo = engine.computeRecommendedScore(
                poorRated, BadgeTier.VERIFIED, 1, List.of(), List.of());
        double scoreNormal = engine.computeRecommendedScore(
                normal, BadgeTier.VERIFIED, 1, List.of(), List.of());

        assertThat(scorePoorWithPromo).isEqualTo(scorePoorWithoutPromo);
        assertThat(scoreNormal).isGreaterThan(scorePoorWithPromo);
    }

    @Test
    void stackablePromoScore_rewardsHavingBothFlashSaleAndVendorVoucherWithoutExceedingCap() {
        UUID vendorId = UUID.randomUUID();
        Service svc = service(UUID.randomUUID(), vendorId, "650000", "4.50", 15);
        PublicOffer fsOffer = offer(svc.getId(), "650000", "550000", "15.3846", 10);
        DiscountCodeJpaEntity voucher = vendorVoucher(vendorId, null, DiscountType.PERCENTAGE, "10", "300000", "80000");

        double flashOnly = engine.computeRecommendedScore(svc, BadgeTier.VERIFIED, 2, List.of(fsOffer), List.of());
        double stacked = engine.computeRecommendedScore(svc, BadgeTier.VERIFIED, 2, List.of(fsOffer), List.of(voucher));

        assertThat(stacked).isGreaterThan(flashOnly);
        assertThat(stacked).isLessThanOrEqualTo(100.0);
    }

    @Test
    void isOnSale_matchesFlashSaleOrActiveVendorVoucher() {
        UUID vendorId = UUID.randomUUID();
        Service svc = service(UUID.randomUUID(), vendorId, "650000", "4.50", 10);
        PublicOffer offer = offer(svc.getId(), "650000", "500000", "23.0769", 5);
        DiscountCodeJpaEntity voucher = vendorVoucher(vendorId, null, DiscountType.FIXED, "20000", "250000", null);

        assertThat(engine.isOnSale(svc, List.of(), List.of())).isFalse();
        assertThat(engine.isOnSale(svc, List.of(offer), List.of())).isTrue();
        assertThat(engine.isOnSale(svc, List.of(), List.of(voucher))).isTrue();
    }

    private Service service(UUID id, UUID vendorId, String price, String avgRating, int ratingCount) {
        Service s = Service.builder()
                .vendorId(vendorId)
                .name("Test Service")
                .price(new BigDecimal(price))
                .avgRating(new BigDecimal(avgRating))
                .ratingCount(ratingCount)
                .build();
        s.setId(id);
        s.setCreatedAt(OffsetDateTime.now());
        return s;
    }

    private PublicOffer offer(UUID serviceId, String original, String sale, String percent, int remainingQuota) {
        BigDecimal orig = new BigDecimal(original);
        BigDecimal salePrice = new BigDecimal(sale);
        return new PublicOffer(
                UUID.randomUUID(),
                serviceId,
                UUID.randomUUID(),
                "Test",
                null,
                orig,
                salePrice,
                orig.subtract(salePrice),
                new BigDecimal(percent),
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600),
                remainingQuota,
                remainingQuota > 0 ? "AVAILABLE" : "SOLD_OUT",
                "VND"
        );
    }

    private DiscountCodeJpaEntity vendorVoucher(
            UUID vendorId,
            UUID serviceId,
            DiscountType type,
            String value,
            String minOrder,
            String maxCap) {
        DiscountCodeJpaEntity e = new DiscountCodeJpaEntity();
        e.setId(UUID.randomUUID());
        e.setCode("SHOP10");
        e.setScope(DiscountScope.VENDOR);
        e.setSponsorType(DiscountSponsorType.VENDOR);
        e.setVendorId(vendorId);
        e.setServiceId(serviceId);
        e.setDiscountType(type);
        e.setDiscountValue(new BigDecimal(value));
        e.setMinOrderAmount(minOrder != null ? new BigDecimal(minOrder) : null);
        e.setMaxDiscountAmount(maxCap != null ? new BigDecimal(maxCap) : null);
        e.setIsActive(true);
        return e;
    }
}
