package com.danasea.backend.modules.partner.application.usecases;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.partner.application.services.CustomerPartnerEligibilityService;
import com.danasea.backend.modules.partner.domain.exceptions.ApplicationNotFoundException;
import com.danasea.backend.modules.partner.domain.exceptions.ApplicationStatusConflictException;
import com.danasea.backend.modules.partner.domain.models.PartnerApplication;
import com.danasea.backend.modules.partner.domain.models.PartnerApplicationStatus;
import com.danasea.backend.modules.partner.infrastructure.mappers.PartnerApplicationMapper;
import com.danasea.backend.modules.partner.infrastructure.persistence.entities.VendorRoleApplicationJpaEntity;
import com.danasea.backend.modules.partner.infrastructure.persistence.repositories.JpaVendorRoleApplicationRepository;
import com.danasea.backend.modules.partner.presentation.dtos.PartnerApplicationResponse;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApprovePartnerApplicationUseCase {

    private final JpaVendorRoleApplicationRepository applicationRepository;
    private final JpaVendorRepository vendorRepository;
    private final CustomerPartnerEligibilityService partnerEligibilityService;
    private final AccountInternalApi accountInternalApi;
    private final AuditLogInternalApi auditLogInternalApi;
    private final PartnerApplicationMapper mapper;

    @Transactional
    public PartnerApplicationResponse execute(UUID applicationId, UUID adminId) {
        // 1. Pessimistic Lock on Application
        VendorRoleApplicationJpaEntity entity = applicationRepository.findByIdWithLock(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException("error.application_not_found"));

        if (entity.getStatus() != PartnerApplicationStatus.PENDING) {
            throw new ApplicationStatusConflictException("Only PENDING applications can be approved");
        }

        UUID userId = entity.getUserId();

        // 2. Prevent duplicate Vendor creation for the same user
        if (vendorRepository.existsByUserId(userId)) {
            throw new ApplicationStatusConflictException("A Vendor profile already exists for this user");
        }

        // 3. Pessimistic Lock on User Account to serialize with concurrent Booking/Order attempts
        User user = accountInternalApi.findUserByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        if (user.getRole() != Role.CUSTOMER) {
            throw new ApplicationStatusConflictException("User is not a CUSTOMER");
        }

        // 4. Re-verify customer has no active obligations before role transition
        partnerEligibilityService.validateCustomerEligibility(userId);

        // 5. Update application status
        PartnerApplication domain = mapper.toDomain(entity);
        domain.approve(adminId);
        VendorRoleApplicationJpaEntity updatedEntity = mapper.toEntity(domain);
        VendorRoleApplicationJpaEntity savedApp = applicationRepository.save(updatedEntity);

        // 6. Update User role to VENDOR, increment sessionVersion to invalidate stale JWTs, revoke refresh tokens
        user.setRole(Role.VENDOR);
        user.setSessionVersion(user.getSessionVersion() + 1);
        accountInternalApi.saveUser(user);
        accountInternalApi.revokeAllRefreshTokensByUserId(userId);

        // 6. Create new Vendor entity with verificationStatus = PENDING (Quy trình 2)
        VendorJpaEntity vendor = new VendorJpaEntity();
        vendor.setUserId(userId);
        vendor.setBusinessName(domain.getBusinessName());
        vendor.setTaxCode(domain.getTaxCode());
        vendor.setAddress(domain.getAddress());
        vendor.setBankAccountNumber(domain.getBankAccountNumber());
        vendor.setBankName(domain.getBankName());
        vendor.setBankAccountHolder(domain.getBankAccountHolder());
        vendor.setVerificationStatus(VerificationStatus.PENDING);
        vendor.setBadgeTier(BadgeTier.NONE);
        vendor.setRatingAvg(BigDecimal.ZERO);
        vendor.setRatingCount(0);
        vendorRepository.save(vendor);

        // 6. Record Audit Log
        if (auditLogInternalApi != null) {
            auditLogInternalApi.recordAuditLog(
                    adminId,
                    "PARTNER_APPLICATION_APPROVAL",
                    "PARTNER_APPLICATION",
                    applicationId,
                    "Partner application approved and ROLE_VENDOR granted for user " + userId
            );
        }

        return mapper.toResponse(mapper.toDomain(savedApp));
    }
}
