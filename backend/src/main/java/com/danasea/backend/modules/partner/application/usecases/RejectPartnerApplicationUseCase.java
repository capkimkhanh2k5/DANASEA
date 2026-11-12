package com.danasea.backend.modules.partner.application.usecases;

import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.partner.domain.exceptions.ApplicationNotFoundException;
import com.danasea.backend.modules.partner.domain.exceptions.ApplicationStatusConflictException;
import com.danasea.backend.modules.partner.domain.models.PartnerApplication;
import com.danasea.backend.modules.partner.domain.models.PartnerApplicationStatus;
import com.danasea.backend.modules.partner.infrastructure.mappers.PartnerApplicationMapper;
import com.danasea.backend.modules.partner.infrastructure.persistence.entities.VendorRoleApplicationJpaEntity;
import com.danasea.backend.modules.partner.infrastructure.persistence.repositories.JpaVendorRoleApplicationRepository;
import com.danasea.backend.modules.partner.presentation.dtos.PartnerApplicationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RejectPartnerApplicationUseCase {

    private final JpaVendorRoleApplicationRepository applicationRepository;
    private final AuditLogInternalApi auditLogInternalApi;
    private final PartnerApplicationMapper mapper;

    @Transactional
    public PartnerApplicationResponse execute(UUID applicationId, UUID adminId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason cannot be blank");
        }

        VendorRoleApplicationJpaEntity entity = applicationRepository.findByIdWithLock(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException("error.application_not_found"));

        if (entity.getStatus() != PartnerApplicationStatus.PENDING) {
            throw new ApplicationStatusConflictException("Only PENDING applications can be rejected");
        }

        PartnerApplication domain = mapper.toDomain(entity);
        domain.reject(adminId, reason.trim());

        VendorRoleApplicationJpaEntity updatedEntity = mapper.toEntity(domain);
        VendorRoleApplicationJpaEntity saved = applicationRepository.save(updatedEntity);

        if (auditLogInternalApi != null) {
            auditLogInternalApi.recordAuditLog(
                    adminId,
                    "PARTNER_APPLICATION_REJECTION",
                    "PARTNER_APPLICATION",
                    applicationId,
                    "Partner application rejected: " + reason.trim()
            );
        }

        return mapper.toResponse(mapper.toDomain(saved));
    }
}
