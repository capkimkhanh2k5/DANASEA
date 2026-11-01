package com.danasea.backend.modules.service.application.usecases;
import com.danasea.backend.modules.service.application.dtos.CreateServiceCommand;
import com.danasea.backend.modules.service.application.dtos.ServiceResult;
import com.danasea.backend.modules.service.application.usecases.helpers.ServiceResultMapper;
import com.danasea.backend.modules.service.domain.exceptions.CategoryInactiveException;
import com.danasea.backend.modules.service.domain.exceptions.CategoryNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.VendorNotApprovedException;
import com.danasea.backend.modules.service.domain.exceptions.WaiverContentRequiredException;
import com.danasea.backend.modules.service.domain.models.Category;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.ServiceImage;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.domain.ports.CategoryRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceImageRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.shared.utils.SlugUtils;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateServiceUseCase {

    private final ServiceRepositoryPort serviceRepository;
    private final CategoryRepositoryPort categoryRepository;
    private final ServiceImageRepositoryPort serviceImageRepository;
    private final VendorPort vendorPort;

    public ServiceResult execute(CreateServiceCommand cmd) {
        // 1. Find vendor by userId and validate APPROVED status
        Vendor vendor = vendorPort.findByUserId(cmd.userId())
                .orElseThrow(() -> new VendorNotApprovedException("Vendor not found for user: " + cmd.userId()));

        if (!VerificationStatus.APPROVED.equals(vendor.getVerificationStatus())) {
            throw new VendorNotApprovedException(vendor.getId());
        }

        // 2. Validate category exists and is active
        Category category = categoryRepository.findById(cmd.categoryId())
                .orElseThrow(() -> new CategoryNotFoundException(cmd.categoryId()));

        if (!category.isActive()) {
            throw new CategoryInactiveException(cmd.categoryId());
        }

        boolean waiverReq = Boolean.TRUE.equals(cmd.waiverRequired());
        String wc = cmd.waiverContent() != null ? cmd.waiverContent().trim() : null;
        String wce = cmd.waiverContentEn() != null ? cmd.waiverContentEn().trim() : null;
        if (waiverReq && (wc == null || wc.isBlank()) && (wce == null || wce.isBlank())) {
            throw new WaiverContentRequiredException();
        }

        // Generate unique SEO slug
        String slug = SlugUtils.resolveUniqueSlug(cmd.name(), serviceRepository::existsBySlug);

        // 3. Build service domain object
        Service service = Service.builder()
                .vendorId(vendor.getId())
                .categoryId(cmd.categoryId())
                .name(cmd.name())
                .nameEn(cmd.nameEn())
                .slug(slug)
                .description(cmd.description())
                .descriptionEn(cmd.descriptionEn())
                .price(cmd.price())
                .durationMinutes(cmd.durationMinutes())
                .capacityPerSlot(cmd.capacityPerSlot())
                .locationName(cmd.locationName())
                .address(cmd.address())
                .latitude(cmd.latitude())
                .longitude(cmd.longitude())
                .waiverContent(wc)
                .waiverContentEn(wce)
                .waiverRequired(waiverReq)
                .waiverVersion(1)
                .weatherSensitive(cmd.weatherSensitive())
                .minWindKmh(cmd.minWindKmh())
                .maxWaveM(cmd.maxWaveM())
                .status(ServiceStatus.DRAFT)
                .viewCount(0)
                .avgRating(null)
                .ratingCount(0)
                .build();

        // 4. Validate weather requirements
        service.validateWeatherRequirements();

        // 5. Persist
        Service saved = serviceRepository.save(service);

        List<ServiceImage> images =
                serviceImageRepository.findByServiceId(saved.getId());

        return ServiceResultMapper.toResult(saved, images);
    }
}
