package com.danasea.backend.modules.service.application.usecases;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.service.domain.exceptions.InvalidDocumentStateException;
import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.UnauthorizedServiceAccessException;
import com.danasea.backend.modules.service.domain.models.DocStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSafetyDocumentJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSafetyDocumentRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WithdrawSafetyDocumentUseCase {

    private final JpaServiceRepository serviceRepository;
    private final JpaServiceSafetyDocumentRepository safetyDocumentRepository;

    @Transactional
    public ServiceSafetyDocumentJpaEntity execute(UUID serviceId, UUID documentId, UUID vendorId) {
        var serviceEntity = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException("Service not found: " + serviceId));

        if (!vendorId.equals(serviceEntity.getVendorId())) {
            throw new UnauthorizedServiceAccessException("Vendor does not own this service");
        }

        var document = safetyDocumentRepository.findByIdAndServiceId(documentId, serviceId)
                .orElseThrow(() -> new ServiceNotFoundException("Safety document not found: " + documentId));

        if (document.getStatus() == DocStatus.APPROVED) {
            throw new InvalidDocumentStateException("Cannot withdraw an APPROVED safety document");
        }
        if (document.getStatus() == DocStatus.WITHDRAWN || document.getStatus() == DocStatus.SUPERSEDED) {
            throw new InvalidDocumentStateException("Document is already " + document.getStatus());
        }

        document.setStatus(DocStatus.WITHDRAWN);
        return safetyDocumentRepository.save(document);
    }
}
