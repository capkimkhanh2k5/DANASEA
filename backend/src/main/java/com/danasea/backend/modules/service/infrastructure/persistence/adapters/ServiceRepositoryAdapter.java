package com.danasea.backend.modules.service.infrastructure.persistence.adapters;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.danasea.backend.modules.flashsale.application.FlashSaleService;
import com.danasea.backend.modules.flashsale.application.FlashSaleService.PublicOffer;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.DiscountCodeJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaDiscountCodeRepository;
import com.danasea.backend.modules.service.application.dtos.SearchServicesCriteria;
import com.danasea.backend.modules.service.application.services.ServiceDiscoveryAvailability;
import com.danasea.backend.modules.service.application.services.ServiceRankingEngine;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.mappers.ServiceMapper;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.specifications.ServiceSpecifications;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ServiceRepositoryAdapter implements ServiceRepositoryPort {

    private final JpaServiceRepository jpaServiceRepository;
    private final ServiceMapper serviceMapper;
    private final ServiceDiscoveryAvailability availability;

    private FlashSaleService flashSaleService;
    private JpaDiscountCodeRepository discountCodeRepository;
    private VendorPort vendorPort;
    private JdbcTemplate jdbcTemplate;
    private ServiceRankingEngine rankingEngine = new ServiceRankingEngine();

    @Autowired(required = false)
    public void setFlashSaleService(FlashSaleService flashSaleService) {
        this.flashSaleService = flashSaleService;
    }

    @Autowired(required = false)
    public void setDiscountCodeRepository(JpaDiscountCodeRepository discountCodeRepository) {
        this.discountCodeRepository = discountCodeRepository;
    }

    @Autowired(required = false)
    public void setVendorPort(VendorPort vendorPort) {
        this.vendorPort = vendorPort;
    }

    @Autowired(required = false)
    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Autowired(required = false)
    public void setRankingEngine(ServiceRankingEngine rankingEngine) {
        if (rankingEngine != null) {
            this.rankingEngine = rankingEngine;
        }
    }

    @Override
    @Transactional
    public Optional<Service> findByIdForUpdate(UUID id) {
        return jpaServiceRepository.findByIdForUpdate(id).map(serviceMapper::toDomain);
    }

    @Override
    @Transactional
    public Service save(Service service) {
        ServiceJpaEntity entity = serviceMapper.toEntity(service);
        ServiceJpaEntity saved = jpaServiceRepository.save(entity);
        return serviceMapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Service> findById(UUID id) {
        return jpaServiceRepository.findById(id).map(serviceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Service> findByVendorId(UUID vendorId) {
        List<ServiceJpaEntity> entities = jpaServiceRepository.findByVendorIdOrderByCreatedAtDesc(vendorId);
        return serviceMapper.toDomainList(entities);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Service> findByStatus(ServiceStatus status) {
        List<ServiceJpaEntity> entities = jpaServiceRepository.findByStatusOrderByCreatedAtDesc(status);
        return serviceMapper.toDomainList(entities);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Service> findAll() {
        return serviceMapper.toDomainList(jpaServiceRepository.findAll());
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        jpaServiceRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(UUID id) {
        return jpaServiceRepository.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Service> findPublishedById(UUID id) {
        return jpaServiceRepository.findByIdAndStatus(id, ServiceStatus.PUBLISHED)
                .map(serviceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Service> findPublishedBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return jpaServiceRepository.findBySlugAndStatus(slug.trim(), ServiceStatus.PUBLISHED)
                .map(serviceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Service> findBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return jpaServiceRepository.findBySlug(slug.trim())
                .map(serviceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return false;
        }
        return jpaServiceRepository.existsBySlug(slug.trim());
    }

    @Override
    @Transactional
    public int incrementViewCount(UUID id, ServiceStatus status) {
        return jpaServiceRepository.incrementViewCount(id, status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Service> searchPublishedServices(
            UUID categoryId,
            String keyword,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            BigDecimal lat,
            BigDecimal lng,
            Double radiusKm,
            int page,
            int size
    ) {
        SearchServicesCriteria criteria = SearchServicesCriteria.builder()
                .categoryId(categoryId)
                .keyword(keyword)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .lat(lat)
                .lng(lng)
                .radiusKm(radiusKm)
                .page(page)
                .size(size)
                .build();
        return searchPublishedServices(criteria);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPublishedServices(
            UUID categoryId,
            String keyword,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            BigDecimal lat,
            BigDecimal lng,
            Double radiusKm
    ) {
        SearchServicesCriteria criteria = SearchServicesCriteria.builder()
                .categoryId(categoryId)
                .keyword(keyword)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .lat(lat)
                .lng(lng)
                .radiusKm(radiusKm)
                .build();
        return countPublishedServices(criteria);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Service> searchPublishedServices(SearchServicesCriteria criteria) {
        SearchServicesCriteria filter = criteria == null ? SearchServicesCriteria.builder().build() : criteria;
        int page = Math.max(0, filter.getPage());
        int size = filter.getSize() <= 0 ? 20 : filter.getSize();

        if (shouldUsePromotionAwarePipeline(filter)) {
            List<Service> filtered = loadFilteredPromotionAwareServices(filter);
            sortPromotionAware(filtered, filter);
            int fromIndex = (int) Math.min((long) page * size, filtered.size());
            int toIndex = Math.min(fromIndex + size, filtered.size());
            return filtered.subList(fromIndex, toIndex);
        }

        Specification<ServiceJpaEntity> spec = specification(filter);
        Sort sort = resolveSort(filter.getSortBy());
        if (sort.isSorted()) {
            sort = sort.and(Sort.by("id"));
        }
        if (!hasSlotFilter(filter)) {
            return jpaServiceRepository.findAll(spec, PageRequest.of(page, size, sort))
                    .map(serviceMapper::toDomain).getContent();
        }
        List<Service> result = new ArrayList<>();
        long skip = (long) page * size;
        int batch = 0;
        boolean hasNext;
        do {
            var candidates = jpaServiceRepository.findAll(spec, PageRequest.of(batch++, 200, sort));
            hasNext = candidates.hasNext();
            for (var candidate : candidates) {
                if (!availability.matches(candidate.getId(), filter)) {
                    continue;
                }
                if (skip > 0) {
                    skip--;
                } else if (result.size() < size) {
                    result.add(serviceMapper.toDomain(candidate));
                }
            }
        } while (hasNext && result.size() < size);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public long countPublishedServices(SearchServicesCriteria criteria) {
        SearchServicesCriteria filter = criteria == null ? SearchServicesCriteria.builder().build() : criteria;
        if (shouldUsePromotionAwarePipeline(filter)) {
            return loadFilteredPromotionAwareServices(filter).size();
        }
        Specification<ServiceJpaEntity> spec = ServiceSpecifications.filter(filter);
        if (!hasSlotFilter(filter)) {
            return jpaServiceRepository.count(spec);
        }
        long count = 0;
        int batch = 0;
        boolean hasNext;
        do {
            var candidates = jpaServiceRepository.findAll(spec, PageRequest.of(batch++, 200, Sort.by("id")));
            hasNext = candidates.hasNext();
            count += candidates.stream().filter(candidate -> availability.matches(candidate.getId(), filter)).count();
        } while (hasNext);
        return count;
    }

    private boolean isRecommendedSort(String sortBy) {
        return sortBy == null || sortBy.isBlank() || sortBy.trim().equalsIgnoreCase("recommended");
    }

    private boolean isPriceSort(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return false;
        }
        String normalized = sortBy.trim().toLowerCase();
        return "price_asc".equals(normalized) || "price_desc".equals(normalized);
    }

    private boolean shouldUsePromotionAwarePipeline(SearchServicesCriteria filter) {
        boolean hasPromotionPorts = flashSaleService != null || discountCodeRepository != null || vendorPort != null;
        if (!hasPromotionPorts && !Boolean.TRUE.equals(filter.getOnSale())) {
            return false;
        }
        if (isRecommendedSort(filter.getSortBy()) || Boolean.TRUE.equals(filter.getOnSale())) {
            return true;
        }
        boolean usesPrice = filter.getMinPrice() != null || filter.getMaxPrice() != null || isPriceSort(filter.getSortBy());
        return usesPrice && flashSaleService != null && !activeFlashSaleOffers().isEmpty();
    }

    private List<PublicOffer> activeFlashSaleOffers() {
        if (flashSaleService == null) {
            return List.of();
        }
        List<PublicOffer> offers = flashSaleService.active();
        return offers != null ? offers : List.of();
    }

    private List<DiscountCodeJpaEntity> activeVendorVouchers() {
        if (discountCodeRepository == null) {
            return List.of();
        }
        List<DiscountCodeJpaEntity> vouchers = discountCodeRepository.findActivePublicVendorDiscounts(OffsetDateTime.now());
        return vouchers != null ? vouchers : List.of();
    }

    private List<Service> loadFilteredPromotionAwareServices(SearchServicesCriteria filter) {
        // Defer price range filtering so active Flash Sale effectivePrice can satisfy minPrice/maxPrice.
        SearchServicesCriteria sqlCriteria = SearchServicesCriteria.builder()
                .categoryId(filter.getCategoryId())
                .keyword(filter.getKeyword())
                .lat(filter.getLat())
                .lng(filter.getLng())
                .radiusKm(filter.getRadiusKm())
                .date(filter.getDate())
                .timeSlot(filter.getTimeSlot())
                .guests(filter.getGuests())
                .vendorId(filter.getVendorId())
                .badgeTier(filter.getBadgeTier())
                .minRating(filter.getMinRating())
                .build();

        Specification<ServiceJpaEntity> spec = ServiceSpecifications.filter(sqlCriteria);
        List<Service> rawServices = serviceMapper.toDomainList(jpaServiceRepository.findAll(spec));
        if (rawServices.isEmpty()) {
            return new ArrayList<>();
        }

        Map<UUID, List<PublicOffer>> offersByService = activeFlashSaleOffers().stream()
                .filter(o -> o != null && o.serviceId() != null)
                .collect(Collectors.groupingBy(PublicOffer::serviceId));
        List<DiscountCodeJpaEntity> vendorVouchers = Boolean.TRUE.equals(filter.getOnSale())
                ? activeVendorVouchers()
                : List.of();
        boolean checkSlotAvailability = hasSlotFilter(filter);

        List<Service> matched = new ArrayList<>();
        for (Service service : rawServices) {
            if (checkSlotAvailability && !availability.matches(service.getId(), filter)) {
                continue;
            }
            List<PublicOffer> serviceOffers = offersByService.getOrDefault(service.getId(), List.of());
            BigDecimal effPrice = rankingEngine.effectivePrice(service, serviceOffers);
            if (filter.getMinPrice() != null && effPrice.compareTo(filter.getMinPrice()) < 0) {
                continue;
            }
            if (filter.getMaxPrice() != null && effPrice.compareTo(filter.getMaxPrice()) > 0) {
                continue;
            }
            if (Boolean.TRUE.equals(filter.getOnSale())) {
                List<DiscountCodeJpaEntity> serviceVouchers = rankingEngine.matchingVendorVouchers(service, vendorVouchers);
                if (!rankingEngine.isOnSale(service, serviceOffers, serviceVouchers)) {
                    continue;
                }
            }
            matched.add(service);
        }
        return matched;
    }

    private void sortPromotionAware(List<Service> services, SearchServicesCriteria filter) {
        if (services.size() <= 1) {
            return;
        }
        Map<UUID, List<PublicOffer>> offersByService = activeFlashSaleOffers().stream()
                .filter(o -> o != null && o.serviceId() != null)
                .collect(Collectors.groupingBy(PublicOffer::serviceId));

        String sortBy = filter.getSortBy();
        if (isRecommendedSort(sortBy)) {
            Map<UUID, BadgeTier> badgeTiers = resolveVendorBadgeTiers(services);
            Map<UUID, Long> paidBookings = loadPaidBookingCounts(services);
            List<DiscountCodeJpaEntity> vendorVouchers = activeVendorVouchers();
            services.sort(rankingEngine.recommendedComparator(badgeTiers, paidBookings, offersByService, vendorVouchers));
            return;
        }

        String normalized = sortBy.trim().toLowerCase();
        Comparator<Service> comparator = switch (normalized) {
            case "price_asc" -> Comparator.comparing((Service s) -> rankingEngine.effectivePrice(s, offersByService.getOrDefault(s.getId(), List.of())));
            case "price_desc" -> Comparator.comparing((Service s) -> rankingEngine.effectivePrice(s, offersByService.getOrDefault(s.getId(), List.of()))).reversed();
            case "rating_desc", "rating" -> Comparator.comparing(
                    (Service s) -> s.getAvgRating() != null ? s.getAvgRating() : BigDecimal.ZERO,
                    Comparator.reverseOrder()
            );
            case "views_desc", "views", "popularity" -> Comparator.comparingInt(
                    (Service s) -> s.getViewCount() != null ? s.getViewCount() : 0
            ).reversed();
            case "bookings_desc", "bookings" -> {
                Map<UUID, Long> paidBookings = loadPaidBookingCounts(services);
                yield Comparator.comparingLong((Service s) -> paidBookings.getOrDefault(s.getId(), 0L)).reversed();
            }
            case "createdat_asc", "created_asc" -> Comparator.comparing(
                    Service::getCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            default -> Comparator.comparing(
                    Service::getCreatedAt,
                    Comparator.nullsLast(Comparator.reverseOrder())
            );
        };
        services.sort(comparator.thenComparing(Service::getId, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    private Map<UUID, BadgeTier> resolveVendorBadgeTiers(List<Service> services) {
        if (vendorPort == null || services.isEmpty()) {
            return Map.of();
        }
        Set<UUID> vendorIds = services.stream()
                .map(Service::getVendorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (vendorIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, BadgeTier> result = vendorPort.findBadgeTiersByVendorIds(vendorIds);
        return result != null ? result : Map.of();
    }

    private Map<UUID, Long> loadPaidBookingCounts(List<Service> services) {
        if (jdbcTemplate == null || services.isEmpty()) {
            return Map.of();
        }
        List<UUID> serviceIds = services.stream()
                .map(Service::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (serviceIds.isEmpty()) {
            return Map.of();
        }
        try {
            String placeholders = serviceIds.stream().map(ignored -> "?").collect(Collectors.joining(","));
            String sql = "SELECT so.service_id, COUNT(*) AS paid_count "
                    + "FROM sub_orders so "
                    + "JOIN master_orders mo ON mo.id = so.master_order_id "
                    + "WHERE mo.payment_status = 'PAID' "
                    + "AND so.status IN ('CONFIRMED', 'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED') "
                    + "AND so.service_id IN (" + placeholders + ") "
                    + "GROUP BY so.service_id";
            Map<UUID, Long> counts = new HashMap<>();
            jdbcTemplate.query(sql, rs -> {
                UUID serviceId = rs.getObject("service_id", UUID.class);
                long cnt = rs.getLong("paid_count");
                if (serviceId != null) {
                    counts.put(serviceId, cnt);
                }
            }, serviceIds.toArray());
            return counts;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private boolean hasSlotFilter(SearchServicesCriteria criteria) {
        return criteria.getDate() != null || criteria.getTimeSlot() != null || criteria.getGuests() != null;
    }

    private Specification<ServiceJpaEntity> specification(SearchServicesCriteria criteria) {
        Specification<ServiceJpaEntity> spec = ServiceSpecifications.filter(criteria);
        String sort = criteria.getSortBy();
        if (sort == null || sort.isBlank() || sort.trim().equalsIgnoreCase("recommended")) {
            return spec.and(ServiceSpecifications.orderByRecommended());
        }
        if (sort.trim().equalsIgnoreCase("bookings") || sort.trim().equalsIgnoreCase("bookings_desc")) {
            return spec.and(ServiceSpecifications.orderByBookings());
        }
        return spec;
    }

    private Sort resolveSort(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return Sort.unsorted();
        }
        String normalized = sortBy.trim().toLowerCase();
        return switch (normalized) {
            case "recommended" -> Sort.unsorted();
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "price");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "price");
            case "rating_desc", "rating" -> Sort.by(Sort.Direction.DESC, "avgRating");
            case "views_desc", "views", "popularity" -> Sort.by(Sort.Direction.DESC, "viewCount");
            case "bookings_desc", "bookings" -> Sort.unsorted();
            case "createdat_asc", "created_asc" -> Sort.by(Sort.Direction.ASC, "createdAt");
            case "newest", "createdat_desc", "created_desc" -> Sort.by(Sort.Direction.DESC, "createdAt");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }
}
