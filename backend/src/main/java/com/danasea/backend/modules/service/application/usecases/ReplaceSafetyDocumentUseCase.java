package com.danasea.backend.modules.service.application.usecases;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.danasea.backend.modules.service.application.ports.FileStoragePort;
import com.danasea.backend.modules.service.application.usecases.helpers.FileSignatureValidator;
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
public class ReplaceSafetyDocumentUseCase {

    private static final long MAX_DOCUMENT_BYTES = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );

    private final JpaServiceRepository serviceRepository;
    private final JpaServiceSafetyDocumentRepository safetyDocumentRepository;
    private final FileStoragePort fileStoragePort;

    @Transactional
    public ServiceSafetyDocumentJpaEntity execute(UUID serviceId, UUID documentId, UUID vendorId, MultipartFile file) {
        var serviceEntity = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException("Service not found: " + serviceId));

        if (!vendorId.equals(serviceEntity.getVendorId())) {
            throw new UnauthorizedServiceAccessException("Vendor does not own this service");
        }

        var document = safetyDocumentRepository.findByIdAndServiceId(documentId, serviceId)
                .orElseThrow(() -> new ServiceNotFoundException("Safety document not found: " + documentId));

        if (document.getStatus() == DocStatus.APPROVED) {
            throw new InvalidDocumentStateException("Cannot replace an APPROVED safety document");
        }
        if (document.getStatus() == DocStatus.SUPERSEDED || document.getStatus() == DocStatus.WITHDRAWN) {
            throw new InvalidDocumentStateException("Cannot replace a document in state " + document.getStatus());
        }

        byte[] fileBytes = FileSignatureValidator.readAndValidate(file, ALLOWED_CONTENT_TYPES, MAX_DOCUMENT_BYTES);

        String folderPath = "services/" + serviceId + "/documents";
        String uploadedUrl = fileStoragePort.uploadFile(fileBytes, file.getOriginalFilename(), folderPath);

        // Mark old document as SUPERSEDED (audit reason preserved)
        document.setStatus(DocStatus.SUPERSEDED);
        safetyDocumentRepository.save(document);

        // Create replacement document with PENDING status
        var replacement = new ServiceSafetyDocumentJpaEntity();
        replacement.setServiceId(serviceId);
        replacement.setFileUrl(uploadedUrl);
        replacement.setStatus(DocStatus.PENDING);
        replacement.setReviewedBy(null);
        replacement.setReviewedAt(null);
        replacement.setRejectionReason(null);

        return safetyDocumentRepository.save(replacement);
    }
}
