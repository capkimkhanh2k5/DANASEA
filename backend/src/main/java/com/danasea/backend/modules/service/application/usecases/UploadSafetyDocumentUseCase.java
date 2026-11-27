package com.danasea.backend.modules.service.application.usecases;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.danasea.backend.modules.service.application.ports.FileStoragePort;
import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.UnauthorizedServiceAccessException;
import com.danasea.backend.modules.service.domain.models.DocStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSafetyDocumentJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSafetyDocumentRepository;
import lombok.RequiredArgsConstructor;
import com.danasea.backend.modules.service.application.usecases.helpers.FileSignatureValidator;

@Service
@RequiredArgsConstructor
public class UploadSafetyDocumentUseCase {

    private static final long MAX_DOCUMENT_BYTES = 20L * 1024 * 1024;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );

    private final JpaServiceRepository serviceRepository;
    private final JpaServiceSafetyDocumentRepository safetyDocumentRepository;
    private final FileStoragePort fileStoragePort;

    public ServiceSafetyDocumentJpaEntity execute(UUID serviceId, UUID vendorId, MultipartFile file) {
        // 1. Validate service exists
        var serviceEntity = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException("Service not found: " + serviceId));

        // 2. Validate ownership (IDOR protection)
        if (!vendorId.equals(serviceEntity.getVendorId())) {
            throw new UnauthorizedServiceAccessException("Vendor does not own this service");
        }

        // 3. Validate file type — safety documents can be PDF or images
        byte[] fileBytes = FileSignatureValidator.readAndValidate(
                file, ALLOWED_CONTENT_TYPES, MAX_DOCUMENT_BYTES);

        // 4. Upload to Cloudinary
        String folderPath = "services/" + serviceId + "/documents";
        String uploadedUrl;
        uploadedUrl = fileStoragePort.uploadFile(fileBytes, file.getOriginalFilename(), folderPath);

        // 5. Save with PENDING status, no audit trail yet
        var documentEntity = new ServiceSafetyDocumentJpaEntity();
        documentEntity.setServiceId(serviceId);
        documentEntity.setFileUrl(uploadedUrl);
        documentEntity.setStatus(DocStatus.PENDING);
        documentEntity.setReviewedBy(null);
        documentEntity.setReviewedAt(null);
        documentEntity.setRejectionReason(null);

        return safetyDocumentRepository.save(documentEntity);
    }
}
