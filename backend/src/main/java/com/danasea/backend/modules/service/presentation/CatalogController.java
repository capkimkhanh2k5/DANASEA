package com.danasea.backend.modules.service.presentation;

import com.danasea.backend.modules.flashsale.application.FlashSaleService;
import com.danasea.backend.modules.flashsale.application.FlashSaleService.PublicOffer;
import com.danasea.backend.modules.service.application.dtos.SearchServicesCriteria;
import com.danasea.backend.modules.service.application.dtos.ServiceDetailResult;
import com.danasea.backend.modules.service.application.dtos.ServiceSummaryResult;
import com.danasea.backend.modules.service.application.usecases.GetPublicServiceDetailUseCase;
import com.danasea.backend.modules.service.application.usecases.SearchServicesUseCase;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.presentation.dtos.PageResponse;
import com.danasea.backend.modules.service.presentation.dtos.ServiceDetailResponse;
import com.danasea.backend.modules.service.presentation.dtos.ServiceSummaryResponse;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import com.danasea.backend.shared.i18n.LocaleContextFilter;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

@RestController
@RequestMapping({"/api/services", "/api/v1/catalog"})
@RequiredArgsConstructor
public class CatalogController {

    private static final String DEFAULT_PUBLIC_ORIGIN = "https://danasea.tailfc3563.ts.net";

    private FlashSaleService flashSale;

    @Autowired
    public void setFlashSale(FlashSaleService flashSale) {
        this.flashSale = flashSale;
    }

    private ServiceRepositoryPort serviceRepositoryPort;

    @Autowired
    public void setServiceRepositoryPort(ServiceRepositoryPort serviceRepositoryPort) {
        this.serviceRepositoryPort = serviceRepositoryPort;
    }

    @Value("${app.cors.allowed-origins:" + DEFAULT_PUBLIC_ORIGIN + "}")
    private String allowedOriginsConfig = DEFAULT_PUBLIC_ORIGIN;

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

        List<PublicOffer> offers = flashSale != null ? flashSale.active() : List.of();
        List<ServiceSummaryResponse> responses = results.stream().map(r -> ServiceSummaryResponse.builder()
                .flashSales(offers.stream().filter(o -> o.serviceId().equals(r.getId())).toList())
                .id(r.getId())
                .name(r.getName())
                .slug(r.getSlug())
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

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> generateSitemap(HttpServletRequest request) {
        String baseUrl = resolvePublicBaseUrl(request);
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // Public static routes
        sb.append("  <url><loc>").append(baseUrl).append("/</loc><changefreq>daily</changefreq><priority>1.0</priority></url>\n");
        sb.append("  <url><loc>").append(baseUrl).append("/explore</loc><changefreq>daily</changefreq><priority>0.9</priority></url>\n");
        sb.append("  <url><loc>").append(baseUrl).append("/search</loc><changefreq>daily</changefreq><priority>0.8</priority></url>\n");

        if (serviceRepositoryPort != null) {
            List<Service> services = serviceRepositoryPort.findByStatus(ServiceStatus.PUBLISHED);
            for (Service svc : services) {
                String slugOrId = (svc.getSlug() != null && !svc.getSlug().isBlank()) ? svc.getSlug() : svc.getId().toString();
                String lastmod = svc.getUpdatedAt() != null ? svc.getUpdatedAt().toLocalDate().toString() : LocalDate.now().toString();
                sb.append("  <url>\n");
                sb.append("    <loc>").append(baseUrl).append("/tour/").append(slugOrId).append("</loc>\n");
                sb.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
                sb.append("    <changefreq>weekly</changefreq>\n");
                sb.append("    <priority>0.9</priority>\n");
                sb.append("  </url>\n");
            }
        }

        sb.append("</urlset>");
        return ResponseEntity.ok(sb.toString());
    }

    @GetMapping(value = "/og/{idOrSlug}", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> getOpenGraphPreview(
            @PathVariable("idOrSlug") String idOrSlug,
            @RequestParam(value = "lang", required = false) String lang,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
            HttpServletRequest request
    ) {
        SupportedLanguage activeLang = resolveLanguage(lang, acceptLanguage, request);
        boolean isEnglish = activeLang == SupportedLanguage.EN;
        String baseUrl = resolvePublicBaseUrl(request);
        Locale previousLocale = LocaleContextHolder.getLocale();
        try {
            LocaleContextHolder.setLocale(activeLang.locale());
            ServiceDetailResult result = getServiceDetailUseCase.readOnlySnapshotByIdOrSlug(idOrSlug);
            String titleSuffix = isEnglish
                    ? " | DANASEA - Da Nang Marine Experiences"
                    : " | DANASEA - Trải nghiệm biển Đà Nẵng";
            String defaultDesc = isEnglish
                    ? "Book marine tourism and water sports experiences in Da Nang with DANASEA."
                    : "Đặt vé trải nghiệm du lịch biển tại Đà Nẵng cùng DANASEA.";
            String htmlLang = isEnglish ? "en" : "vi";
            String ogLocale = isEnglish ? "en_US" : "vi_VN";
            String redirectLabel = isEnglish ? "Redirecting to " : "Đang chuyển hướng đến ";

            String title = HtmlUtils.htmlEscape(result.getName() + titleSuffix);
            String descText = result.getDescription() != null && !result.getDescription().isBlank()
                    ? result.getDescription()
                    : defaultDesc;
            if (descText.length() > 200) {
                descText = descText.substring(0, 197) + "...";
            }
            String desc = HtmlUtils.htmlEscape(descText);
            String imageUrl = (result.getImageUrls() != null && !result.getImageUrls().isEmpty())
                    ? result.getImageUrls().get(0)
                    : baseUrl + "/logo192.png";
            String targetSlug = result.getSlug() != null && !result.getSlug().isBlank()
                    ? result.getSlug()
                    : result.getId().toString();
            String canonicalUrl = baseUrl + "/tour/" + targetSlug;
            String price = result.getPrice() != null ? result.getPrice().toPlainString() : "0";

            String html = String.format("""
                <!DOCTYPE html>
                <html lang="%s">
                <head>
                    <meta charset="UTF-8">
                    <title>%s</title>
                    <meta name="description" content="%s">
                    <link rel="canonical" href="%s">

                    <!-- Open Graph / Facebook / Zalo -->
                    <meta property="og:type" content="product">
                    <meta property="og:site_name" content="DANASEA">
                    <meta property="og:locale" content="%s">
                    <meta property="og:title" content="%s">
                    <meta property="og:description" content="%s">
                    <meta property="og:image" content="%s">
                    <meta property="og:url" content="%s">
                    <meta property="product:price:amount" content="%s">
                    <meta property="product:price:currency" content="VND">

                    <!-- Twitter Card -->
                    <meta name="twitter:card" content="summary_large_image">
                    <meta name="twitter:title" content="%s">
                    <meta name="twitter:description" content="%s">
                    <meta name="twitter:image" content="%s">

                    <!-- Automatic redirect for human visitors -->
                    <meta http-equiv="refresh" content="0; url=/tour/%s">
                    <script>window.location.replace("/tour/%s");</script>
                </head>
                <body>
                    <p>%s<a href="/tour/%s">%s</a>...</p>
                </body>
                </html>
                """,
                htmlLang,
                title, desc, canonicalUrl,
                ogLocale, title, desc, imageUrl, canonicalUrl, price,
                title, desc, imageUrl,
                targetSlug, targetSlug, redirectLabel, targetSlug, title
            );
            return ResponseEntity.ok(html);
        } catch (Exception ex) {
            String notFoundText = isEnglish ? "Service not found" : "Dịch vụ không tồn tại";
            String htmlLang = isEnglish ? "en" : "vi";
            return ResponseEntity.status(404).body(
                    "<!DOCTYPE html><html lang=\"" + htmlLang + "\"><head><title>DANASEA</title></head><body>" + notFoundText + "</body></html>"
            );
        } finally {
            LocaleContextHolder.setLocale(previousLocale);
        }
    }

    private SupportedLanguage resolveLanguage(String langParam, String acceptLanguageHeader, HttpServletRequest request) {
        if (langParam != null && !langParam.isBlank()) {
            return SupportedLanguage.fromTag(langParam).orElse(SupportedLanguage.DEFAULT);
        }
        if (acceptLanguageHeader != null && !acceptLanguageHeader.isBlank()) {
            return SupportedLanguage.fromAcceptLanguage(acceptLanguageHeader).orElse(SupportedLanguage.DEFAULT);
        }
        if (request != null && request.getAttribute(LocaleContextFilter.REQUEST_LANGUAGE_ATTRIBUTE) instanceof SupportedLanguage attrLang) {
            return attrLang;
        }
        return SupportedLanguage.DEFAULT;
    }

    private String resolvePublicBaseUrl(HttpServletRequest request) {
        if (request != null) {
            String forwardedHost = request.getHeader("X-Forwarded-Host");
            if (forwardedHost != null && !forwardedHost.isBlank()) {
                String host = forwardedHost.split(",")[0].trim();
                if (!isLocalOrInternalHost(host)) {
                    String proto = request.getHeader("X-Forwarded-Proto");
                    String scheme = (proto != null && !proto.isBlank()) ? proto.split(",")[0].trim() : "https";
                    return stripTrailingSlash(scheme + "://" + host);
                }
            }
            String serverName = request.getServerName();
            if (serverName != null && !isLocalOrInternalHost(serverName)) {
                String scheme = request.getScheme() != null ? request.getScheme() : "https";
                int port = request.getServerPort();
                boolean standardPort = port <= 0 || port == 80 || port == 443;
                return stripTrailingSlash(scheme + "://" + serverName + (standardPort ? "" : ":" + port));
            }
        }
        if (allowedOriginsConfig != null && !allowedOriginsConfig.isBlank()) {
            return Arrays.stream(allowedOriginsConfig.split(","))
                    .map(String::trim)
                    .filter(origin -> !origin.isEmpty() && !isLocalOrInternalHost(origin))
                    .map(this::stripTrailingSlash)
                    .findFirst()
                    .orElse(DEFAULT_PUBLIC_ORIGIN);
        }
        return DEFAULT_PUBLIC_ORIGIN;
    }

    private boolean isLocalOrInternalHost(String hostOrOrigin) {
        String lower = hostOrOrigin.toLowerCase(Locale.ROOT);
        return lower.contains("localhost")
                || lower.contains("127.0.0.1")
                || lower.contains("0.0.0.0")
                || lower.equals("backend");
    }

    private String stripTrailingSlash(String url) {
        return url.replaceAll("/+$", "");
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceDetailResponse> getServiceDetail(
            @PathVariable("id") UUID id,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId
    ) {
        UUID userId = SecurityUtils.getCurrentUserId().orElse(null);
        ServiceDetailResult result = getServiceDetailUseCase.execute(id, userId, sessionId);
        return ResponseEntity.ok(toServiceDetailResponse(result));
    }

    @GetMapping("/by-slug/{slug}")
    public ResponseEntity<ServiceDetailResponse> getServiceDetailBySlug(
            @PathVariable("slug") String slug,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId
    ) {
        UUID userId = SecurityUtils.getCurrentUserId().orElse(null);
        ServiceDetailResult result = getServiceDetailUseCase.executeByIdOrSlug(slug, userId, sessionId);
        return ResponseEntity.ok(toServiceDetailResponse(result));
    }

    private ServiceDetailResponse toServiceDetailResponse(ServiceDetailResult result) {
        final UUID serviceId = result.getId();

        return ServiceDetailResponse.builder()
                .flashSales(flashSale != null ? flashSale.active().stream().filter(o -> o.serviceId().equals(serviceId)).toList() : List.of())
                .id(result.getId())
                .name(result.getName())
                .slug(result.getSlug())
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
