package com.danasea.backend.modules.partner.application.usecases;

import com.danasea.backend.modules.partner.application.services.CustomerPartnerEligibilityService;
import com.danasea.backend.modules.partner.domain.exceptions.ApplicationNotFoundException;
import com.danasea.backend.modules.partner.domain.exceptions.ApplicationStatusConflictException;
import com.danasea.backend.modules.partner.domain.models.PartnerApplication;
import com.danasea.backend.modules.partner.domain.models.PartnerApplicationStatus;
import com.danasea.backend.modules.partner.infrastructure.mappers.PartnerApplicationMapper;
import com.danasea.backend.modules.partner.infrastructure.persistence.entities.VendorRoleApplicationJpaEntity;
import com.danasea.backend.modules.partner.infrastructure.persistence.repositories.JpaVendorRoleApplicationRepository;
import com.danasea.backend.modules.partner.presentation.dtos.PartnerApplicationResponse;
import com.danasea.backend.modules.partner.presentation.dtos.ResubmitPartnerApplicationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResubmitPartnerApplicationUseCase {

    private final JpaVendorRoleApplicationRepository applicationRepository;
    private final CustomerPartnerEligibilityService partnerEligibilityService;
    private final PartnerApplicationMapper mapper;

    @Transactional
    public PartnerApplicationResponse execute(UUID userId, ResubmitPartnerApplicationRequest request) {
        // Validate customer has no active obligations before allowing resubmit
        partnerEligibilityService.validateCustomerEligibility(userId);

        VendorRoleApplicationJpaEntity entity = applicationRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new ApplicationNotFoundException("error.application_not_found"));

        if (entity.getStatus() != PartnerApplicationStatus.REJECTED) {
            throw new ApplicationStatusConflictException("Only REJECTED applications can be resubmitted");
        }

        PartnerApplication domain = mapper.toDomain(entity);
        domain.resubmit(
                request.businessName(),
                request.taxCode(),
                request.address(),
                request.bankAccountNumber(),
                request.bankName(),
                request.bankAccountHolder()
        );

        VendorRoleApplicationJpaEntity updatedEntity = mapper.toEntity(domain);
        VendorRoleApplicationJpaEntity saved = applicationRepository.save(updatedEntity);
        return mapper.toResponse(mapper.toDomain(saved));
    }
}
