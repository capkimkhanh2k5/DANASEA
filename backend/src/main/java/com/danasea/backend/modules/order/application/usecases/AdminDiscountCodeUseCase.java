package com.danasea.backend.modules.order.application.usecases;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.order.application.DiscountCodeRules;
import com.danasea.backend.modules.order.domain.exceptions.InvalidDiscountException;
import com.danasea.backend.modules.order.domain.models.DiscountScope;
import com.danasea.backend.modules.order.domain.models.DiscountSponsorType;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.DiscountCodeJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaDiscountCodeRepository;
import com.danasea.backend.modules.order.presentation.dtos.CreateDiscountCodeRequest;
import com.danasea.backend.modules.order.presentation.dtos.DiscountCodeResponse;
import com.danasea.backend.modules.order.presentation.dtos.UpdateDiscountCodeRequest;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;

@Service
public class AdminDiscountCodeUseCase {

    private final JpaDiscountCodeRepository discountCodeRepository;
    private final JpaServiceRepository serviceRepository;

    @Autowired
    public AdminDiscountCodeUseCase(JpaDiscountCodeRepository discountCodeRepository, JpaServiceRepository serviceRepository) {
        this.discountCodeRepository = discountCodeRepository;
        this.serviceRepository = serviceRepository;
    }

    public AdminDiscountCodeUseCase(JpaDiscountCodeRepository discountCodeRepository) {
        this(discountCodeRepository, null);
    }

    @Transactional(readOnly = true)
    public Page<DiscountCodeResponse> getDiscountCodes(
            String code,
            DiscountScope scope,
            UUID vendorId,
            Boolean isActive,
            Pageable pageable
    ) {
        Specification<DiscountCodeJpaEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (code != null && !code.isBlank()) {
                predicates.add(cb.like(cb.upper(root.get("code")), "%" + code.trim().toUpperCase(Locale.ROOT) + "%"));
            }
            if (scope != null) {
                predicates.add(cb.equal(root.get("scope"), scope));
            }
            if (vendorId != null) {
                predicates.add(cb.equal(root.get("vendorId"), vendorId));
            }
            if (isActive != null) {
                predicates.add(cb.equal(root.get("isActive"), isActive));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return discountCodeRepository.findAll(spec, pageable).map(DiscountCodeResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public DiscountCodeResponse getDiscountCodeById(UUID id) {
        DiscountCodeJpaEntity entity = discountCodeRepository.findById(id)
                .orElseThrow(() -> new InvalidDiscountException("DISCOUNT_NOT_FOUND", "Discount code was not found: " + id));
        return DiscountCodeResponse.fromEntity(entity);
    }

    @Transactional
    public DiscountCodeResponse createDiscountCode(CreateDiscountCodeRequest request) {
        String cleanCode = request.code().trim().toUpperCase(Locale.ROOT);
        if (discountCodeRepository.existsByCodeIgnoreCase(cleanCode)) {
            throw new InvalidDiscountException("CODE_ALREADY_EXISTS", "Discount code already exists: " + cleanCode);
        }

        if (request.scope() == DiscountScope.VENDOR && request.vendorId() == null) {
            throw new InvalidDiscountException("VENDOR_ID_REQUIRED", "Vendor-scoped discounts require a vendor ID.");
        }

        DiscountSponsorType sponsor = request.sponsorType();
        if (sponsor == null) {
            sponsor = request.scope() == DiscountScope.VENDOR ? DiscountSponsorType.VENDOR : DiscountSponsorType.PLATFORM;
        }

        DiscountCodeJpaEntity entity = new DiscountCodeJpaEntity();
        entity.setCode(cleanCode);
        entity.setScope(request.scope());
        entity.setSponsorType(sponsor);
        entity.setVendorId(request.vendorId());
        entity.setServiceId(request.serviceId());
        entity.setDiscountType(request.discountType());
        entity.setDiscountValue(request.discountValue());
        entity.setMinOrderAmount(request.minOrderAmount());
        entity.setMaxDiscountAmount(request.maxDiscountAmount());
        entity.setMaxUses(request.maxUses());
        entity.setUsedCount(0);
        entity.setMaxUsesPerUser(request.maxUsesPerUser());
        entity.setValidFrom(request.validFrom());
        entity.setValidTo(request.validTo());
        entity.setIsActive(request.isActive() != null ? request.isActive() : true);

        validateConfiguration(entity);
        DiscountCodeJpaEntity saved = discountCodeRepository.save(entity);
        return DiscountCodeResponse.fromEntity(saved);
    }

    @Transactional
    public DiscountCodeResponse updateDiscountCode(UUID id, UpdateDiscountCodeRequest request) {
        DiscountCodeJpaEntity entity = discountCodeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new InvalidDiscountException("DISCOUNT_NOT_FOUND", "Discount code was not found: " + id));

        if (entity.getCompensationDisruptionId() != null) {
            throw new InvalidDiscountException("INVALID_DISCOUNT", "Vendor-fault compensation vouchers cannot be edited.");
        }

        if (request.discountValue() != null) {
            entity.setDiscountValue(request.discountValue());
        }
        if (request.minOrderAmount() != null) {
            entity.setMinOrderAmount(request.minOrderAmount());
        }
        if (request.maxDiscountAmount() != null) {
            entity.setMaxDiscountAmount(request.maxDiscountAmount());
        }
        if (request.maxUses() != null) {
            if (entity.getUsedCount() != null && request.maxUses() < entity.getUsedCount()) {
                throw new InvalidDiscountException("MAX_USES_LESS_THAN_USED", "Maximum uses cannot be less than reserved or consumed uses.");
            }
            entity.setMaxUses(request.maxUses());
        }
        if (request.maxUsesPerUser() != null) {
            entity.setMaxUsesPerUser(request.maxUsesPerUser());
        }
        if (request.validFrom() != null) {
            entity.setValidFrom(request.validFrom());
        }
        if (request.validTo() != null) {
            entity.setValidTo(request.validTo());
        }
        if (request.isActive() != null) {
            entity.setIsActive(request.isActive());
        }

        validateConfiguration(entity);
        DiscountCodeJpaEntity saved = discountCodeRepository.save(entity);
        return DiscountCodeResponse.fromEntity(saved);
    }
    private void validateConfiguration(DiscountCodeJpaEntity entity) {
        DiscountCodeRules.validate(entity);
        if (entity.getServiceId() != null) {
            if (serviceRepository == null) {
                throw new IllegalStateException("Service ownership lookup is not configured.");
            }
            var service = serviceRepository.findById(entity.getServiceId())
                    .orElseThrow(() -> new InvalidDiscountException("DISCOUNT_SERVICE_INVALID", "Discount service was not found."));
            if (entity.getScope() == DiscountScope.VENDOR && !entity.getVendorId().equals(service.getVendorId())) {
                throw new InvalidDiscountException("DISCOUNT_SERVICE_INVALID", "Discount service does not belong to the vendor.");
            }
        }
    }

}
