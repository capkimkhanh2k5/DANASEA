package com.danasea.backend.modules.service.presentation;

import com.danasea.backend.modules.service.application.dtos.SearchServicesCriteria;
import com.danasea.backend.modules.service.application.dtos.ServiceDetailResult;
import com.danasea.backend.modules.service.application.dtos.ServiceSummaryResult;
import com.danasea.backend.modules.service.application.usecases.GetPublicServiceDetailUseCase;
import com.danasea.backend.modules.service.application.usecases.SearchServicesUseCase;
import com.danasea.backend.modules.service.presentation.dtos.PageResponse;
import com.danasea.backend.modules.service.presentation.dtos.ServiceDetailResponse;
import com.danasea.backend.modules.service.presentation.dtos.ServiceSummaryResponse;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/services", "/api/v1/catalog"})
@RequiredArgsConstructor
public class CatalogController {
    private com.danasea.backend.modules.flashsale.application.FlashSaleService flashSale;
    @org.springframework.beans.factory.annotation.Autowired
    public void setFlashSale(com.danasea.backend.modules.flashsale.application.FlashSaleService flashSale) { this.flashSale=flashSale; }

    private final SearchServicesUseCase searchServicesUseCase;
    private final GetPublicServiceDetailUseCase getServiceDetailUseCase;

    @GetMapping
    public ResponseEntity<PageResponse<ServiceSummaryResponse>> searchServices(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime timeSlot,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTime,
            @RequestParam(required = false) Integer guests,
            @RequestParam(required = false) Integer participantCount,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (dateTime != null) {
            if (date == null) {
                date = dateTime.toLocalDate();
            }
            if (timeSlot == null) {
                timeSlot = dateTime.toLocalTime();
            }
        }
        if (guests == null && participantCount != null) {
            guests = participantCount;
        }

        validateSearchParameters(keyword, minPrice, maxPrice, lat, lng, radiusKm, guests, minRating, page, size);
        SearchServicesCriteria criteria = SearchServicesCriteria.builder()
                .categoryId(categoryId)
                .keyword(keyword)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .lat(lat)
                .lng(lng)
                .radiusKm(radiusKm)
                .date(date)
                .timeSlot(timeSlot)
                .guests(guests)
                .vendorId(vendorId)
                .minRating(minRating)
                .sortBy(sortBy)
                .page(page)
                .size(size)
                .build();

        List<ServiceSummaryResult> results = searchServicesUseCase.execute(criteria);
        long total = searchServicesUseCase.count(criteria);

        var offers=flashSale!=null?flashSale.active():java.util.List.<com.danasea.backend.modules.flashsale.application.FlashSaleService.PublicOffer>of();
        List<ServiceSummaryResponse> responses = results.stream().map(r -> ServiceSummaryResponse.builder()
                .flashSales(offers.stream().filter(o->o.serviceId().equals(r.getId())).toList())
                .id(r.getId())
                .name(r.getName())
                .shortDescription(r.getShortDescription())
                .price(r.getPrice())
                .promotionalPrice(r.getPromotionalPrice())
                .address(r.getAddress())
                .averageRating(r.getAverageRating())
                .reviewCount(r.getReviewCount())
                .viewCount(r.getViewCount())
                .primaryImageUrl(r.getPrimaryImageUrl())
                .build()
        ).collect(Collectors.toList());

        return ResponseEntity.ok(new PageResponse<>(responses, page, size, total));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceDetailResponse> getServiceDetail(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId
    ) {
        UUID userId = SecurityUtils.getCurrentUserId().orElse(null);
        ServiceDetailResult result = getServiceDetailUseCase.execute(id, userId, sessionId);

        ServiceDetailResponse response = ServiceDetailResponse.builder()
                .flashSales(flashSale!=null?flashSale.active().stream().filter(o->o.serviceId().equals(id)).toList():java.util.List.of())
                .id(result.getId())
                .name(result.getName())
                .description(result.getDescription())
                .price(result.getPrice())
                .promotionalPrice(result.getPromotionalPrice())
                .address(result.getAddress())
                .latitude(result.getLatitude())
                .longitude(result.getLongitude())
                .averageRating(result.getAverageRating())
                .reviewCount(result.getReviewCount())
                .viewCount(result.getViewCount())
                .categoryId(result.getCategoryId())
                .categoryName(result.getCategoryName())
                .imageUrls(result.getImageUrls())
                .availableSlots(result.getAvailableSlots())
                .options(result.getOptions())
                .vendorId(result.getVendorId())
                .businessName(result.getBusinessName())
                .badgeTier(result.getBadgeTier())
                .duration(result.getDuration())
                .capacity(result.getCapacity())
                .participantConditions(result.getParticipantConditions())
                .waiverRequired(result.getWaiverRequired())
                .waiverVersion(result.getWaiverVersion())
                .waiverContent(result.getWaiverContent())
                .waiverLanguage(result.getWaiverLanguage())
                .waiverFallbackUsed(result.getWaiverFallbackUsed())
                .refundPolicy(result.getRefundPolicy())
                .cancellationPolicy(result.getCancellationPolicy())
                .safetyRules(result.getSafetyRules())
                .slots(result.getSlots())
                .build();

        return ResponseEntity.ok(response);
    }

    private void validateSearchParameters(
            String keyword,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            BigDecimal lat,
            BigDecimal lng,
            Double radiusKm,
            Integer guests,
            BigDecimal minRating,
            int page,
            int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        if (keyword != null && keyword.length() > 100) {
            throw new IllegalArgumentException("keyword must not exceed 100 characters");
        }
        if (minPrice != null && minPrice.signum() < 0
                || maxPrice != null && maxPrice.signum() < 0) {
            throw new IllegalArgumentException("price filters must not be negative");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("minPrice must not exceed maxPrice");
        }
        if ((lat == null) != (lng == null)) {
            throw new IllegalArgumentException("lat and lng must be provided together");
        }
        if (lat != null && (lat.compareTo(BigDecimal.valueOf(-90)) < 0
                || lat.compareTo(BigDecimal.valueOf(90)) > 0)) {
            throw new IllegalArgumentException("lat must be between -90 and 90");
        }
        if (lng != null && (lng.compareTo(BigDecimal.valueOf(-180)) < 0
                || lng.compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw new IllegalArgumentException("lng must be between -180 and 180");
        }
        if (radiusKm != null && (lat == null || radiusKm <= 0 || radiusKm > 200)) {
            throw new IllegalArgumentException("radiusKm requires coordinates and must be between 0 and 200");
        }
        if (minRating != null && (minRating.compareTo(BigDecimal.ZERO) < 0
                || minRating.compareTo(BigDecimal.valueOf(5.0)) > 0)) {
            throw new IllegalArgumentException("minRating must be between 0.0 and 5.0");
        }
        if (guests != null && guests <= 0) {
            throw new IllegalArgumentException("guests must be greater than 0");
        }
    }
}
