package com.danasea.backend.modules.vendor.application.usecases;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResubmitVendorProfileUseCaseTest {

    @Mock
    private JpaVendorRepository vendorRepository;

    @Mock
    private JpaVendorDocumentRepository vendorDocumentRepository;

    @Mock
    private VendorMapper vendorMapper;

    @Mock
    private AuditLogInternalApi auditLogInternalApi;

    @InjectMocks
    private ResubmitVendorProfileUseCase useCase;

    private UUID userId;
    private UUID vendorId;
    private VendorJpaEntity rejectedVendorEntity;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        vendorId = UUID.randomUUID();

        rejectedVendorEntity = new VendorJpaEntity();
        rejectedVendorEntity.setId(vendorId);
        rejectedVendorEntity.setUserId(userId);
        rejectedVendorEntity.setBusinessName("Da Nang Sea Tours");
        rejectedVendorEntity.setTaxCode("0401234567");
        rejectedVendorEntity.setAddress("12 Vo Nguyen Giap, Da Nang");
        rejectedVendorEntity.setBankAccountNumber("0123456789");
        rejectedVendorEntity.setBankName("Vietcombank");
        rejectedVendorEntity.setBankAccountHolder("DA NANG SEA TOURS");
        rejectedVendorEntity.setVerificationStatus(VerificationStatus.REJECTED);
        rejectedVendorEntity.setRejectionReason("Business license was blurry");
    }

    private VendorDocumentJpaEntity createDoc(DocType type, DocStatus status, OffsetDateTime createdAt) {
        VendorDocumentJpaEntity doc = new VendorDocumentJpaEntity();
        doc.setId(UUID.randomUUID());
        doc.setVendorId(vendorId);
        doc.setDocType(type);
        doc.setStatus(status);
        doc.setFileUrl("https://cloudinary.com/doc.pdf");
        doc.setCreatedAt(createdAt);
        return doc;
    }

    @Test
    @DisplayName("execute successfully transitions REJECTED to PENDING, clears rejectionReason, and audits")
    void execute_WhenValid_Success() {
        OffsetDateTime now = OffsetDateTime.now();
        VendorDocumentJpaEntity licenseDoc = createDoc(DocType.BUSINESS_LICENSE, DocStatus.APPROVED, now.minusDays(2));
        VendorDocumentJpaEntity safetyDoc = createDoc(DocType.SAFETY_CERT, DocStatus.PENDING, now.minusHours(1));

        when(vendorRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(rejectedVendorEntity));
        when(vendorDocumentRepository.findByVendorId(vendorId)).thenReturn(List.of(licenseDoc, safetyDoc));
        when(vendorRepository.save(rejectedVendorEntity)).thenReturn(rejectedVendorEntity);

        Vendor domainVendor = new Vendor();
        domainVendor.setId(vendorId);
        domainVendor.setVerificationStatus(VerificationStatus.PENDING);
        domainVendor.setRejectionReason(null);
        when(vendorMapper.toDomain(rejectedVendorEntity)).thenReturn(domainVendor);

        Vendor result = useCase.execute(userId);

        assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(result.getRejectionReason()).isNull();
        assertThat(rejectedVendorEntity.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(rejectedVendorEntity.getRejectionReason()).isNull();

        verify(auditLogInternalApi).recordAuditLog(
                eq(userId),
                eq("VENDOR_RESUBMISSION"),
                eq("VENDOR"),
                eq(vendorId),
                eq("Vendor resubmitted profile for review")
        );
    }

    @Test
    @DisplayName("execute throws VendorNotFoundException when vendor profile does not exist")
    void execute_WhenVendorNotFound_ThrowsException() {
        when(vendorRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(userId))
                .isInstanceOf(VendorNotFoundException.class);

        verify(vendorRepository, never()).save(any());
        verify(auditLogInternalApi, never()).recordAuditLog(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("execute throws VendorStatusConflictException when vendor is currently PENDING")
    void execute_WhenAlreadyPending_ThrowsConflict() {
        rejectedVendorEntity.setVerificationStatus(VerificationStatus.PENDING);
        when(vendorRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(rejectedVendorEntity));

        assertThatThrownBy(() -> useCase.execute(userId))
                .isInstanceOf(VendorStatusConflictException.class)
                .hasMessageContaining("PENDING");

        verify(vendorRepository, never()).save(any());
    }

    @Test
    @DisplayName("execute throws VendorStatusConflictException when vendor is already APPROVED")
    void execute_WhenAlreadyApproved_ThrowsConflict() {
        rejectedVendorEntity.setVerificationStatus(VerificationStatus.APPROVED);
        when(vendorRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(rejectedVendorEntity));

        assertThatThrownBy(() -> useCase.execute(userId))
                .isInstanceOf(VendorStatusConflictException.class)
                .hasMessageContaining("APPROVED");

        verify(vendorRepository, never()).save(any());
    }

    @Test
    @DisplayName("execute throws VendorProfileIncompleteException when required business info is missing")
    void execute_WhenBusinessInfoIncomplete_ThrowsException() {
        rejectedVendorEntity.setTaxCode(null);
        when(vendorRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(rejectedVendorEntity));

        assertThatThrownBy(() -> useCase.execute(userId))
                .isInstanceOf(VendorProfileIncompleteException.class);

        verify(vendorRepository, never()).save(any());
    }

    @Test
    @DisplayName("execute throws VendorDocumentsIncompleteException when a required document type is missing")
    void execute_WhenRequiredDocumentMissing_ThrowsException() {
        OffsetDateTime now = OffsetDateTime.now();
        VendorDocumentJpaEntity licenseDoc = createDoc(DocType.BUSINESS_LICENSE, DocStatus.APPROVED, now);

        when(vendorRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(rejectedVendorEntity));
        when(vendorDocumentRepository.findByVendorId(vendorId)).thenReturn(List.of(licenseDoc));

        assertThatThrownBy(() -> useCase.execute(userId))
                .isInstanceOf(VendorDocumentsIncompleteException.class)
                .hasMessageContaining("SAFETY_CERT");

        verify(vendorRepository, never()).save(any());
    }

    @Test
    @DisplayName("execute throws VendorDocumentsIncompleteException when latest document is REJECTED")
    void execute_WhenLatestDocumentIsRejected_ThrowsException() {
        OffsetDateTime now = OffsetDateTime.now();
        VendorDocumentJpaEntity licenseDoc = createDoc(DocType.BUSINESS_LICENSE, DocStatus.APPROVED, now);
        VendorDocumentJpaEntity rejectedSafetyDoc = createDoc(DocType.SAFETY_CERT, DocStatus.REJECTED, now);

        when(vendorRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(rejectedVendorEntity));
        when(vendorDocumentRepository.findByVendorId(vendorId)).thenReturn(List.of(licenseDoc, rejectedSafetyDoc));

        assertThatThrownBy(() -> useCase.execute(userId))
                .isInstanceOf(VendorDocumentsIncompleteException.class)
                .hasMessageContaining("rejected");

        verify(vendorRepository, never()).save(any());
    }

    @Test
    @DisplayName("execute succeeds when a new document replaced the old REJECTED document")
    void execute_WhenNewDocumentReplacedRejectedDocument_Success() {
        OffsetDateTime now = OffsetDateTime.now();
        VendorDocumentJpaEntity licenseDoc = createDoc(DocType.BUSINESS_LICENSE, DocStatus.APPROVED, now.minusDays(2));
        VendorDocumentJpaEntity oldRejectedDoc = createDoc(DocType.SAFETY_CERT, DocStatus.REJECTED, now.minusDays(1));
        VendorDocumentJpaEntity newPendingDoc = createDoc(DocType.SAFETY_CERT, DocStatus.PENDING, now);

        when(vendorRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(rejectedVendorEntity));
        when(vendorDocumentRepository.findByVendorId(vendorId)).thenReturn(List.of(licenseDoc, oldRejectedDoc, newPendingDoc));
        when(vendorRepository.save(rejectedVendorEntity)).thenReturn(rejectedVendorEntity);

        Vendor domainVendor = new Vendor();
        domainVendor.setId(vendorId);
        domainVendor.setVerificationStatus(VerificationStatus.PENDING);
        domainVendor.setRejectionReason(null);
        when(vendorMapper.toDomain(rejectedVendorEntity)).thenReturn(domainVendor);

        Vendor result = useCase.execute(userId);

        assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(result.getRejectionReason()).isNull();
        verify(vendorRepository).save(rejectedVendorEntity);
    }
}
