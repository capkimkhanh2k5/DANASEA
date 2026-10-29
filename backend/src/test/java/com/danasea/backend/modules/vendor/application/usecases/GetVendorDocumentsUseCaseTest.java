package com.danasea.backend.modules.vendor.application.usecases;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.danasea.backend.modules.vendor.application.ports.DocumentStoragePort;
import com.danasea.backend.modules.vendor.domain.models.VendorDocument;
import com.danasea.backend.modules.vendor.infrastructure.mappers.VendorDocumentMapper;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorDocumentJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorDocumentRepository;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetVendorDocumentsUseCaseTest {

    @Mock
    private JpaVendorRepository jpaVendorRepository;

    @Mock
    private JpaVendorDocumentRepository jpaVendorDocumentRepository;

    @Mock
    private VendorDocumentMapper vendorDocumentMapper;

    @Mock
    private DocumentStoragePort documentStoragePort;

    @InjectMocks
    private GetVendorDocumentsUseCase useCase;

    private UUID userId;
    private UUID vendorId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        vendorId = UUID.randomUUID();
    }

    @Test
    void shouldReturnDocumentsWithSignedDeliveryUrls() {
        VendorJpaEntity vendor = new VendorJpaEntity();
        vendor.setId(vendorId);
        vendor.setUserId(userId);

        VendorDocumentJpaEntity entity = new VendorDocumentJpaEntity();
        String canonicalUrl = "https://res.cloudinary.com/xmxguqpi/image/authenticated/v1792170628/danasea_demo_data_package/vendor_documents/license.png";
        String signedUrl = "https://res.cloudinary.com/xmxguqpi/image/authenticated/s--SIG--/v1792170628/danasea_demo_data_package/vendor_documents/license.png";
        entity.setFileUrl(canonicalUrl);

        VendorDocument domainDoc = new VendorDocument();
        domainDoc.setFileUrl(canonicalUrl);

        when(jpaVendorRepository.findByUserId(userId)).thenReturn(Optional.of(vendor));
        when(jpaVendorDocumentRepository.findByVendorId(vendorId)).thenReturn(List.of(entity));
        when(vendorDocumentMapper.toDomain(entity)).thenReturn(domainDoc);
        when(documentStoragePort.generateDeliveryUrl(canonicalUrl)).thenReturn(signedUrl);

        List<VendorDocument> results = useCase.execute(userId);

        assertEquals(1, results.size());
        assertEquals(signedUrl, results.get(0).getFileUrl());
        verify(documentStoragePort).generateDeliveryUrl(canonicalUrl);
    }
}
