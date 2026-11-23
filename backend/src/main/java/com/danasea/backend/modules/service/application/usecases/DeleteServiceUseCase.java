package com.danasea.backend.modules.service.application.usecases;

import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.service.domain.exceptions.InvalidServiceStateException;
import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.VendorNotApprovedException;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.ports.ServiceImageRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceSlotRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSafetyDocumentRepository;
import com.danasea.backend.modules.vendor.domain.models.Vendor;

public class DeleteServiceUseCase {

    private final ServiceRepositoryPort serviceRepository;
    private final ServiceImageRepositoryPort serviceImageRepository;
    private final VendorPort vendorPort;
    private final ServiceSlotRepositoryPort serviceSlotRepository;
    private final JpaServiceSafetyDocumentRepository safetyDocumentRepository;

    public DeleteServiceUseCase(
            ServiceRepositoryPort serviceRepository,
            ServiceImageRepositoryPort serviceImageRepository,
            VendorPort vendorPort,
            ServiceSlotRepositoryPort serviceSlotRepository,
            JpaServiceSafetyDocumentRepository safetyDocumentRepository) {
        this.serviceRepository = serviceRepository;
        this.serviceImageRepository = serviceImageRepository;
        this.vendorPort = vendorPort;
        this.serviceSlotRepository = serviceSlotRepository;
        this.safetyDocumentRepository = safetyDocumentRepository;
    }

    public DeleteServiceUseCase(
            ServiceRepositoryPort serviceRepository,
            ServiceImageRepositoryPort serviceImageRepository,
            VendorPort vendorPort) {
        this(serviceRepository, serviceImageRepository, vendorPort, null, null);
    }

    @Transactional
    public void execute(UUID userId, UUID serviceId) {
        Vendor vendor = vendorPort.findByUserId(userId)
                .orElseThrow(() -> new VendorNotApprovedException("Vendor not found for user: " + userId));

        Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException(serviceId));

        service.validateOwnership(vendor.getId());
        service.validateDeletable();

        if (service.isRejected()) {
            if (serviceSlotRepository != null && serviceSlotRepository.hasAnyBookingForService(serviceId)) {
                throw new InvalidServiceStateException("Cannot delete rejected service with existing booking history.");
            }
            if (serviceSlotRepository != null) {
                serviceSlotRepository.deleteByServiceId(serviceId);
            }
        }

        if (safetyDocumentRepository != null) {
            safetyDocumentRepository.deleteByServiceId(serviceId);
        }
        serviceImageRepository.deleteByServiceId(serviceId);
        serviceRepository.deleteById(serviceId);
    }
}
