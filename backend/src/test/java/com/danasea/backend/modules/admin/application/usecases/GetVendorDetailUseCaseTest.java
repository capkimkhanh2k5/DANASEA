package com.danasea.backend.modules.admin.application.usecases;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.danasea.backend.modules.admin.presentation.dtos.AdminVendorResponse;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.application.ports.DocumentStoragePort;
import com.danasea.backend.modules.vendor.application.usecases.GetVendorDocumentsUseCase;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorNotFoundException;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.DocStatus;
import com.danasea.backend.modules.vendor.domain.models.DocType;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.infrastructure.mappers.VendorDocumentMapper;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorDocumentJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorDocumentRepository;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import com.danasea.backend.modules.vendor.presentation.dtos.VendorDocumentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetVendorDetailUseCaseTest {

    @Mock
    private VendorInternalApi vendorInternalApi;
    @Mock
    private JpaVendorRepository vendorRepository;
    @Mock
    private JpaVendorDocumentRepository documentRepository;
    @Mock
    private DocumentStoragePort documentStoragePort;

    private GetVendorDetailUseCase useCase;

    @BeforeEach
    void setUp() {
        GetVendorDocumentsUseCase documentsUseCase = new GetVendorDocumentsUseCase(
                vendorRepository, documentRepository, new VendorDocumentMapper(), documentStoragePort);
        useCase = new GetVendorDetailUseCase(vendorInternalApi, documentsUseCase);
    }

    @Test
    void returnsCompleteVendorDetailWithReviewedDocumentsAndDeliveryUrl() {
        Vendor vendor = vendor();
        VendorJpaEntity storedVendor = new VendorJpaEntity();
        storedVendor.setId(vendor.getId());
        storedVendor.setUserId(vendor.getUserId());
        VendorDocumentJpaEntity document = new VendorDocumentJpaEntity();
        document.setId(UUID.randomUUID());
        document.setVendorId(vendor.getId());
        document.setDocType(DocType.BUSINESS_LICENSE);
        document.setFileUrl("https://storage.example/license.pdf");
        document.setStatus(DocStatus.APPROVED);
        document.setReviewedBy(vendor.getVerifiedBy());
        document.setReviewedAt(vendor.getVerifiedAt());
        document.setCreatedAt(vendor.getCreatedAt());
        document.setUpdatedAt(vendor.getUpdatedAt());
        when(vendorInternalApi.findById(vendor.getId())).thenReturn(Optional.of(vendor));
        when(vendorRepository.findByUserId(vendor.getUserId())).thenReturn(Optional.of(storedVendor));
        when(documentRepository.findByVendorId(vendor.getId())).thenReturn(List.of(document));
        when(documentStoragePort.generateDeliveryUrl(document.getFileUrl()))
                .thenReturn("https://delivery.example/signed-license.pdf");

        AdminVendorResponse response = useCase.execute(vendor.getId());

        assertThat(response).usingRecursiveComparison().ignoringFields("documents").isEqualTo(vendor);
        assertThat(response.getDocuments()).containsExactly(new VendorDocumentResponse(
                document.getId(), vendor.getId(), DocType.BUSINESS_LICENSE,
                "https://delivery.example/signed-license.pdf", DocStatus.APPROVED,
                document.getReviewedBy(), document.getReviewedAt(), document.getCreatedAt(), document.getUpdatedAt()));
        verify(vendorRepository).findByUserId(vendor.getUserId());
        verify(documentRepository).findByVendorId(vendor.getId());
    }

    @Test
    void returnsEmptyDocumentListWhenVendorHasNoDocuments() {
        Vendor vendor = vendor();
        VendorJpaEntity storedVendor = new VendorJpaEntity();
        storedVendor.setId(vendor.getId());
        when(vendorInternalApi.findById(vendor.getId())).thenReturn(Optional.of(vendor));
        when(vendorRepository.findByUserId(vendor.getUserId())).thenReturn(Optional.of(storedVendor));
        when(documentRepository.findByVendorId(vendor.getId())).thenReturn(List.of());

        AdminVendorResponse response = useCase.execute(vendor.getId());

        assertThat(response.getId()).isEqualTo(vendor.getId());
        assertThat(response.getDocuments()).isEmpty();
        verifyNoInteractions(documentStoragePort);
    }

    @Test
    void rejectsMissingVendorBeforeLoadingDocuments() {
        UUID vendorId = UUID.randomUUID();
        when(vendorInternalApi.findById(vendorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(vendorId))
                .isInstanceOf(VendorNotFoundException.class)
                .hasMessage("Vendor not found with id: " + vendorId);

        verifyNoInteractions(vendorRepository, documentRepository, documentStoragePort);
    }

    private Vendor vendor() {
        Vendor vendor = new Vendor();
        vendor.setId(UUID.randomUUID());
        vendor.setUserId(UUID.randomUUID());
        vendor.setBusinessName("Da Nang Sea Tours");
        vendor.setTaxCode("0401234567");
        vendor.setAddress("12 Vo Nguyen Giap, Da Nang");
        vendor.setBankAccountNumber("0123456789");
        vendor.setBankName("Vietcombank");
        vendor.setBankAccountHolder("DA NANG SEA TOURS");
        vendor.setVerificationStatus(VerificationStatus.APPROVED);
        vendor.setVerifiedBy(UUID.randomUUID());
        vendor.setVerifiedAt(OffsetDateTime.parse("2026-10-01T10:00:00+07:00"));
        vendor.setRatingAvg(new BigDecimal("4.75"));
        vendor.setRatingCount(12);
        vendor.setBadgeTier(BadgeTier.TOP_RATED);
        vendor.setCreatedAt(OffsetDateTime.parse("2026-09-01T10:00:00+07:00"));
        vendor.setUpdatedAt(OffsetDateTime.parse("2026-10-02T10:00:00+07:00"));
        return vendor;
    }
}
