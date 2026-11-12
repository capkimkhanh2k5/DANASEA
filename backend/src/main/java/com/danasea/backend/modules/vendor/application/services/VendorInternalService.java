package com.danasea.backend.modules.vendor.application.services;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.infrastructure.mappers.VendorMapper;
import com.danasea.backend.modules.vendor.infrastructure.mappers.VendorDocumentMapper;
import com.danasea.backend.modules.vendor.domain.models.VendorDocument;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorDocumentRepository;
import com.danasea.backend.modules.vendor.domain.models.DocStatus;
import com.danasea.backend.modules.vendor.domain.models.DocType;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VendorInternalService implements VendorInternalApi {

    private final JpaVendorRepository vendorRepository;
    private final JpaVendorDocumentRepository vendorDocumentRepository;
    private final VendorMapper vendorMapper;
    private final VendorDocumentMapper vendorDocumentMapper;

    @Override
    public Optional<VendorDocument> findDocumentForReview(UUID vendorId, UUID documentId) {
        return vendorDocumentRepository.findForReview(vendorId, documentId)
                .map(vendorDocumentMapper::toDomain);
    }

    @Override
    public VendorDocument saveDocument(VendorDocument document) {
        return vendorDocumentMapper.toDomain(
                vendorDocumentRepository.saveAndFlush(vendorDocumentMapper.toEntity(document)));
    }

    @Override
    public Optional<Vendor> findByUserId(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return vendorRepository.findByUserId(userId)
                .map(vendorMapper::toDomain);
    }

    @Override
    public Optional<Vendor> findById(UUID vendorId) {
        if (vendorId == null) {
            return Optional.empty();
        }
        return vendorRepository.findById(vendorId)
                .map(vendorMapper::toDomain);
    }

    @Override
    public boolean isVendorApproved(UUID vendorId) {
        if (vendorId == null) {
            return false;
        }
        return vendorRepository.findById(vendorId)
                .map(v -> VerificationStatus.APPROVED.equals(v.getVerificationStatus()))
                .orElse(false);
    }

    @Override
    public Vendor saveVendor(Vendor vendor) {
        VendorJpaEntity entity = vendorMapper.toEntity(vendor);
        entity = vendorRepository.save(entity);
        return vendorMapper.toDomain(entity);
    }

    @Override
    public Page<Vendor> getVendors(VerificationStatus status, Pageable pageable) {
        if (status == null) {
            return vendorRepository.findAll(pageable).map(vendorMapper::toDomain);
        }
        return vendorRepository.findByVerificationStatus(status, pageable).map(vendorMapper::toDomain);
    }

    @Override
    public Set<DocType> getApprovedDocumentTypes(UUID vendorId) {
        if (vendorId == null) {
            return Set.of();
        }
        List<com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorDocumentJpaEntity> docs =
                vendorDocumentRepository.findByVendorId(vendorId);
        if (docs == null || docs.isEmpty()) {
            return Set.of();
        }
        Map<DocType, com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorDocumentJpaEntity> latestByDocType =
                docs.stream()
                    .filter(d -> d.getDocType() != null && d.getCreatedAt() != null)
                    .collect(Collectors.toMap(
                            com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorDocumentJpaEntity::getDocType,
                            d -> d,
                            (d1, d2) -> d1.getCreatedAt().isAfter(d2.getCreatedAt()) ? d1 : d2
                    ));

        return latestByDocType.entrySet().stream()
                .filter(e -> e.getValue().getStatus() == DocStatus.APPROVED)
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }
}
