package com.danasea.backend.modules.service.application.usecases;

import java.util.List;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.service.application.dtos.ServiceResult;
import com.danasea.backend.modules.service.application.usecases.helpers.ServiceResultMapper;
import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.ServiceImage;
import com.danasea.backend.modules.service.domain.ports.AuditLogPort;
import com.danasea.backend.modules.service.domain.ports.ServiceImageRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ApproveServiceUseCase {

    private final ServiceRepositoryPort serviceRepository;
    private final ServiceImageRepositoryPort serviceImageRepository;
    private final AuditLogPort auditLogPort;
    private final ApproveSafetyDocumentUseCase approveSafetyDocumentUseCase;

    @Transactional
    public ServiceResult execute(UUID adminUserId, UUID serviceId) {
        Service service = serviceRepository.findByIdForUpdate(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException(serviceId));

        // Business logic guard: ensure safety documents are approved if required
        approveSafetyDocumentUseCase.canPublish(serviceId);

        service.approve();

        Service saved = serviceRepository.save(service);

        auditLogPort.recordAuditLog(
                adminUserId,
                "SERVICE_APPROVED",
                "SERVICE",
                saved.getId(),
                "Service approved by admin"
        );

        List<ServiceImage> images = serviceImageRepository.findByServiceId(saved.getId());
        return ServiceResultMapper.toResult(saved, images);
    }
}
