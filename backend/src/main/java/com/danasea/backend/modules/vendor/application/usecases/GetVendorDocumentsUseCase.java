package com.danasea.backend.modules.vendor.application.usecases;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.vendor.application.ports.DocumentStoragePort;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorNotFoundException;
import com.danasea.backend.modules.vendor.domain.models.VendorDocument;
import com.danasea.backend.modules.vendor.infrastructure.mappers.VendorDocumentMapper;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorDocumentRepository;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetVendorDocumentsUseCase {

    private final JpaVendorRepository jpaVendorRepository;
    private final JpaVendorDocumentRepository jpaVendorDocumentRepository;
    private final VendorDocumentMapper vendorDocumentMapper;
    private final DocumentStoragePort documentStoragePort;

    @Transactional(readOnly = true)
    public List<VendorDocument> execute(UUID userId) {
        VendorJpaEntity vendor = jpaVendorRepository.findByUserId(userId)
                .orElseThrow(VendorNotFoundException::new);

        return jpaVendorDocumentRepository.findByVendorId(vendor.getId())
                .stream()
                .map(entity -> {
                    VendorDocument doc = vendorDocumentMapper.toDomain(entity);
                    if (doc != null && doc.getFileUrl() != null) {
                        doc.setFileUrl(documentStoragePort.generateDeliveryUrl(doc.getFileUrl()));
                    }
                    return doc;
                })
                .toList();
    }
}
