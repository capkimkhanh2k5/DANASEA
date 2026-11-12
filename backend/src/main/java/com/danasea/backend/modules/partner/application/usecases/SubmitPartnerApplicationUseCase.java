package com.danasea.backend.modules.partner.application.usecases;

import com.danasea.backend.modules.partner.application.services.CustomerPartnerEligibilityService;
import com.danasea.backend.modules.partner.domain.exceptions.ApplicationStatusConflictException;
import com.danasea.backend.modules.partner.domain.models.PartnerApplicationStatus;
import com.danasea.backend.modules.partner.infrastructure.mappers.PartnerApplicationMapper;
import com.danasea.backend.modules.partner.infrastructure.persistence.entities.VendorRoleApplicationJpaEntity;
import com.danasea.backend.modules.partner.infrastructure.persistence.repositories.JpaVendorRoleApplicationRepository;
import com.danasea.backend.modules.partner.presentation.dtos.PartnerApplicationResponse;
import com.danasea.backend.modules.partner.presentation.dtos.SubmitPartnerApplicationRequest;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubmitPartnerApplicationUseCase {

    private final JpaVendorRoleApplicationRepository applicationRepository;
    private final JpaVendorRepository vendorRepository;
    private final CustomerPartnerEligibilityService partnerEligibilityService;
    private final PartnerApplicationMapper mapper;

    @Transactional
    public PartnerApplicationResponse execute(UUID userId, SubmitPartnerApplicationRequest request) {
        if (vendorRepository.existsByUserId(userId)) {
            throw new ApplicationStatusConflictException("User already has an existing Vendor profile");
        }

        // Validate customer has no active obligations before allowing application
        partnerEligibilityService.validateCustomerEligibility(userId);

        Optional<VendorRoleApplicationJpaEntity> existing = applicationRepository.findByUserIdWithLock(userId);
        if (existing.isPresent()) {
            VendorRoleApplicationJpaEntity app = existing.get();
            if (app.getStatus() == PartnerApplicationStatus.PENDING) {
                throw new ApplicationStatusConflictException("error.application_already_pending");
            }
            if (app.getStatus() == PartnerApplicationStatus.APPROVED) {
                throw new ApplicationStatusConflictException("error.application_already_approved");
            }
            if (app.getStatus() == PartnerApplicationStatus.REJECTED) {
                throw new ApplicationStatusConflictException("error.application_already_rejected");
            }
            throw new ApplicationStatusConflictException("error.application_status_conflict");
        }

        VendorRoleApplicationJpaEntity newApp = new VendorRoleApplicationJpaEntity();
        newApp.setUserId(userId);
        newApp.setBusinessName(request.businessName());
        newApp.setTaxCode(request.taxCode());
        newApp.setAddress(request.address());
        newApp.setBankAccountNumber(request.bankAccountNumber());
        newApp.setBankName(request.bankName());
        newApp.setBankAccountHolder(request.bankAccountHolder());
        newApp.setStatus(PartnerApplicationStatus.PENDING);

        try {
            VendorRoleApplicationJpaEntity saved = applicationRepository.saveAndFlush(newApp);
            return mapper.toResponse(mapper.toDomain(saved));
        } catch (DataIntegrityViolationException ex) {
            throw new ApplicationStatusConflictException("error.application_already_pending");
        }
    }
}
