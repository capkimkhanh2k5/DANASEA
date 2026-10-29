package com.danasea.backend.modules.vendor.application.usecases;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.danasea.backend.modules.vendor.application.ports.DocumentStoragePort;
import com.danasea.backend.modules.vendor.domain.exceptions.InvalidDocTypeException;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorNotFoundException;
import com.danasea.backend.modules.vendor.domain.models.DocStatus;
import com.danasea.backend.modules.vendor.domain.models.DocType;
import com.danasea.backend.modules.vendor.domain.models.VendorDocument;
import com.danasea.backend.modules.vendor.infrastructure.mappers.VendorDocumentMapper;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorDocumentJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorDocumentRepository;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import com.danasea.backend.modules.service.application.usecases.helpers.FileSignatureValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UploadVendorDocumentUseCase {

    private static final long MAX_DOCUMENT_BYTES = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );

    private final JpaVendorRepository jpaVendorRepository;
    private final JpaVendorDocumentRepository jpaVendorDocumentRepository;
    private final DocumentStoragePort documentStoragePort;
    private final VendorDocumentMapper vendorDocumentMapper;

    @Transactional
    public VendorDocument execute(UUID userId, MultipartFile file, String docTypeStr) {
        DocType docType = parseDocType(docTypeStr);
        return execute(userId, file, docType);
    }

    @Transactional
    public VendorDocument execute(UUID userId, MultipartFile file, DocType docType) {
        if (docType == null) {
            throw new InvalidDocTypeException("Document type cannot be null");
        }

        FileSignatureValidator.readAndValidate(file, ALLOWED_CONTENT_TYPES, MAX_DOCUMENT_BYTES);

        VendorJpaEntity vendor = jpaVendorRepository.findByUserId(userId)
                .orElseThrow(VendorNotFoundException::new);

        String fileUrl = documentStoragePort.uploadDocument(file, "vendor_documents");

        VendorDocumentJpaEntity entity = new VendorDocumentJpaEntity();
        entity.setVendorId(vendor.getId());
        entity.setDocType(docType);
        entity.setFileUrl(fileUrl);
        entity.setStatus(DocStatus.PENDING);
        entity.setReviewedBy(null);
        entity.setReviewedAt(null);

        VendorDocumentJpaEntity saved = jpaVendorDocumentRepository.save(entity);
        VendorDocument doc = vendorDocumentMapper.toDomain(saved);
        if (doc != null && doc.getFileUrl() != null) {
            doc.setFileUrl(documentStoragePort.generateDeliveryUrl(doc.getFileUrl()));
        }
        return doc;
    }

    private DocType parseDocType(String docTypeStr) {
        if (docTypeStr == null || docTypeStr.isBlank()) {
            throw new InvalidDocTypeException("Document type is required");
        }
        try {
            return DocType.valueOf(docTypeStr.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidDocTypeException("Invalid document type: " + docTypeStr);
        }
    }
}
