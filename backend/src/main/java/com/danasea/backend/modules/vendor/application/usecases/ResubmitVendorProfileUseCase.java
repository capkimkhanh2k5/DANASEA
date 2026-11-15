package com.danasea.backend.modules.vendor.application.usecases;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorDocumentsIncompleteException;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorNotFoundException;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorProfileIncompleteException;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorStatusConflictException;
import com.danasea.backend.modules.vendor.domain.models.DocStatus;
import com.danasea.backend.modules.vendor.domain.models.DocType;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.infrastructure.mappers.VendorMapper;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorDocumentJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorDocumentRepository;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ResubmitVendorProfileUseCase {

    private final JpaVendorRepository vendorRepository;
    private final JpaVendorDocumentRepository vendorDocumentRepository;
    private final VendorMapper vendorMapper;
    private final AuditLogInternalApi auditLogInternalApi;

    @Transactional
    public Vendor execute(UUID userId) {
        if (userId == null) {
            throw new VendorNotFoundException("User ID cannot be null");
        }

        VendorJpaEntity entity = vendorRepository.findByUserIdForUpdate(userId)
                .orElseThrow(VendorNotFoundException::new);

        VerificationStatus currentStatus = entity.getVerificationStatus();
        if (currentStatus == VerificationStatus.PENDING) {
            throw new VendorStatusConflictException("VENDOR_ALREADY_PENDING", "Vendor profile is currently PENDING review");
        }
        if (currentStatus == VerificationStatus.APPROVED) {
            throw new VendorStatusConflictException("VENDOR_ALREADY_APPROVED", "Vendor profile is already APPROVED");
        }
        if (currentStatus != VerificationStatus.REJECTED) {
            throw new VendorStatusConflictException("VENDOR_STATUS_CONFLICT", "Only REJECTED vendor profiles can be resubmitted");
        }

        validateProfileCompleteness(entity);
        validateDocuments(entity.getId());

        entity.setVerificationStatus(VerificationStatus.PENDING);
        entity.setRejectionReason(null);
        VendorJpaEntity savedEntity = vendorRepository.save(entity);

        auditLogInternalApi.recordAuditLog(
                userId,
                "VENDOR_RESUBMISSION",
                "VENDOR",
                entity.getId(),
                "Vendor resubmitted profile for review"
        );

        return vendorMapper.toDomain(savedEntity);
    }

    private void validateProfileCompleteness(VendorJpaEntity entity) {
        if (isBlank(entity.getBusinessName())
                || isBlank(entity.getTaxCode())
                || isBlank(entity.getAddress())
                || isBlank(entity.getBankAccountNumber())
                || isBlank(entity.getBankName())
                || isBlank(entity.getBankAccountHolder())) {
            throw new VendorProfileIncompleteException("Vendor profile is missing required business information");
        }
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    private void validateDocuments(UUID vendorId) {
        List<VendorDocumentJpaEntity> docs = vendorDocumentRepository.findByVendorId(vendorId);

        Map<DocType, VendorDocumentJpaEntity> latestDocs = docs.stream()
                .filter(d -> d.getDocType() != null)
                .collect(Collectors.toMap(
                        VendorDocumentJpaEntity::getDocType,
                        d -> d,
                        (d1, d2) -> {
                            if (d1.getCreatedAt() == null) return d2;
                            if (d2.getCreatedAt() == null) return d1;
                            int cmp = d1.getCreatedAt().compareTo(d2.getCreatedAt());
                            if (cmp != 0) return cmp > 0 ? d1 : d2;
                            return (d1.getId() != null && d2.getId() != null && d1.getId().compareTo(d2.getId()) > 0) ? d1 : d2;
                        }
                ));

        for (DocType requiredType : DocType.values()) {
            VendorDocumentJpaEntity latestDoc = latestDocs.get(requiredType);
            if (latestDoc == null) {
                throw new VendorDocumentsIncompleteException("Missing required document: " + requiredType);
            }
            if (latestDoc.getStatus() == DocStatus.REJECTED) {
                throw new VendorDocumentsIncompleteException("Document " + requiredType + " was rejected and must be replaced with a new document");
            }
        }
    }
}
