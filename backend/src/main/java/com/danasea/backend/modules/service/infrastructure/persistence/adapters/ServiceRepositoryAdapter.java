package com.danasea.backend.modules.service.infrastructure.persistence.adapters;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.danasea.backend.modules.service.application.dtos.SearchServicesCriteria;
import com.danasea.backend.modules.service.application.services.ServiceDiscoveryAvailability;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.mappers.ServiceMapper;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.specifications.ServiceSpecifications;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ServiceRepositoryAdapter implements ServiceRepositoryPort {

    private final JpaServiceRepository jpaServiceRepository;
    private final ServiceMapper serviceMapper;
    private final ServiceDiscoveryAvailability availability;

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

    private boolean hasSlotFilter(SearchServicesCriteria criteria) {
        return criteria.getDate() != null || criteria.getTimeSlot() != null || criteria.getGuests() != null;
    }

    private Specification<ServiceJpaEntity> specification(SearchServicesCriteria criteria) {
        Specification<ServiceJpaEntity> spec = ServiceSpecifications.filter(criteria);
        String sort = criteria.getSortBy();
        return sort != null && (sort.equalsIgnoreCase("bookings") || sort.equalsIgnoreCase("bookings_desc"))
                ? spec.and(ServiceSpecifications.orderByBookings()) : spec;
    }

    private Sort resolveSort(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String normalized = sortBy.trim().toLowerCase();
        return switch (normalized) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "price");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "price");
            case "rating_desc", "rating" -> Sort.by(Sort.Direction.DESC, "avgRating");
            case "views_desc", "views", "popularity" -> Sort.by(Sort.Direction.DESC, "viewCount");
            case "bookings_desc", "bookings" -> Sort.unsorted();
            case "createdat_asc", "created_asc" -> Sort.by(Sort.Direction.ASC, "createdAt");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }
}
