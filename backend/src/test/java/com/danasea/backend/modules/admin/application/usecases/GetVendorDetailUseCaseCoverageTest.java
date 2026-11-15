package com.danasea.backend.modules.admin.application.usecases;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.admin.presentation.dtos.AdminVendorResponse;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.application.usecases.GetVendorDocumentsUseCase;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.DocStatus;
import com.danasea.backend.modules.vendor.domain.models.DocType;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.modules.vendor.domain.models.VendorDocument;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetVendorDetailUseCaseCoverageTest {

    @Mock
    private VendorInternalApi vendorInternalApi;

    @Mock
    private GetVendorDocumentsUseCase getVendorDocumentsUseCase;

    @Mock
    private AccountInternalApi accountInternalApi;

    private GetVendorDetailUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetVendorDetailUseCase(vendorInternalApi, getVendorDocumentsUseCase, accountInternalApi);
    }

    @Test
    @DisplayName("Should resolve verifiedByName and reviewedByName when AccountInternalApi has all users")
    void execute_WithAccountApi_ResolvesVerifierAndReviewersSuccessfully() {
        UUID vendorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID verifierId = UUID.randomUUID();
        UUID reviewer1Id = UUID.randomUUID();
        UUID reviewer2Id = UUID.randomUUID();

        Vendor vendor = createVendor(vendorId, userId, verifierId);
        VendorDocument doc1 = createDocument(vendorId, UUID.randomUUID(), DocType.BUSINESS_LICENSE, reviewer1Id);
        VendorDocument doc2 = createDocument(vendorId, UUID.randomUUID(), DocType.SAFETY_CERT, reviewer2Id);

        User verifierUser = new User();
        verifierUser.setId(verifierId);
        verifierUser.setFullName("Nguyen Van Verifier");

        User reviewer1 = new User();
        reviewer1.setId(reviewer1Id);
        reviewer1.setFullName("Tran Thi Reviewer 1");

        User reviewer2 = new User();
        reviewer2.setId(reviewer2Id);
        reviewer2.setFullName("Le Van Reviewer 2");

        when(vendorInternalApi.findById(vendorId)).thenReturn(Optional.of(vendor));
        when(getVendorDocumentsUseCase.execute(userId)).thenReturn(List.of(doc1, doc2));
        when(accountInternalApi.findUsersByIds(Set.of(verifierId, reviewer1Id, reviewer2Id)))
                .thenReturn(Map.of(
                        verifierId, verifierUser,
                        reviewer1Id, reviewer1,
                        reviewer2Id, reviewer2
                ));

        AdminVendorResponse response = useCase.execute(vendorId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(vendorId);
        assertThat(response.getVerifiedBy()).isEqualTo(verifierId);
        assertThat(response.getVerifiedByName()).isEqualTo("Nguyen Van Verifier");

        assertThat(response.getDocuments()).hasSize(2);
        assertThat(response.getDocuments().get(0).reviewedBy()).isEqualTo(reviewer1Id);
        assertThat(response.getDocuments().get(0).reviewedByName()).isEqualTo("Tran Thi Reviewer 1");
        assertThat(response.getDocuments().get(1).reviewedBy()).isEqualTo(reviewer2Id);
        assertThat(response.getDocuments().get(1).reviewedByName()).isEqualTo("Le Van Reviewer 2");

        verify(accountInternalApi).findUsersByIds(Set.of(verifierId, reviewer1Id, reviewer2Id));
    }

    @Test
    @DisplayName("Should leave names null when IDs exist but are not found in accountInternalApi map")
    void execute_WithAccountApi_WhenUsersNotFoundInAccountMap_LeavesNamesNull() {
        UUID vendorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID verifierId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        Vendor vendor = createVendor(vendorId, userId, verifierId);
        VendorDocument doc = createDocument(vendorId, UUID.randomUUID(), DocType.BUSINESS_LICENSE, reviewerId);

        when(vendorInternalApi.findById(vendorId)).thenReturn(Optional.of(vendor));
        when(getVendorDocumentsUseCase.execute(userId)).thenReturn(List.of(doc));
        when(accountInternalApi.findUsersByIds(Set.of(verifierId, reviewerId)))
                .thenReturn(Map.of()); // Not found

        AdminVendorResponse response = useCase.execute(vendorId);

        assertThat(response.getVerifiedBy()).isEqualTo(verifierId);
        assertThat(response.getVerifiedByName()).isNull();
        assertThat(response.getDocuments()).hasSize(1);
        assertThat(response.getDocuments().get(0).reviewedBy()).isEqualTo(reviewerId);
        assertThat(response.getDocuments().get(0).reviewedByName()).isNull();
    }

    @Test
    @DisplayName("Should not call accountInternalApi when vendor has no verifier and documents have no reviewers")
    void execute_WithAccountApi_WhenVendorAndDocumentsHaveNoReviewers_SkipsAccountApiCall() {
        UUID vendorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Vendor vendor = createVendor(vendorId, userId, null);
        VendorDocument doc = createDocument(vendorId, UUID.randomUUID(), DocType.BUSINESS_LICENSE, null);

        when(vendorInternalApi.findById(vendorId)).thenReturn(Optional.of(vendor));
        when(getVendorDocumentsUseCase.execute(userId)).thenReturn(List.of(doc));

        AdminVendorResponse response = useCase.execute(vendorId);

        assertThat(response.getVerifiedBy()).isNull();
        assertThat(response.getVerifiedByName()).isNull();
        assertThat(response.getDocuments()).hasSize(1);
        assertThat(response.getDocuments().get(0).reviewedBy()).isNull();
        assertThat(response.getDocuments().get(0).reviewedByName()).isNull();

        verifyNoInteractions(accountInternalApi);
    }

    @Test
    @DisplayName("Should resolve verifier even when document list is empty")
    void execute_WithAccountApi_WhenDocumentsEmpty_StillResolvesVerifier() {
        UUID vendorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID verifierId = UUID.randomUUID();

        Vendor vendor = createVendor(vendorId, userId, verifierId);

        User verifierUser = new User();
        verifierUser.setId(verifierId);
        verifierUser.setFullName("Nguyen Van Verifier");

        when(vendorInternalApi.findById(vendorId)).thenReturn(Optional.of(vendor));
        when(getVendorDocumentsUseCase.execute(userId)).thenReturn(Collections.emptyList());
        when(accountInternalApi.findUsersByIds(Set.of(verifierId)))
                .thenReturn(Map.of(verifierId, verifierUser));

        AdminVendorResponse response = useCase.execute(vendorId);

        assertThat(response.getDocuments()).isEmpty();
        assertThat(response.getVerifiedByName()).isEqualTo("Nguyen Van Verifier");
    }

    @Test
    @DisplayName("Should resolve reviewer when vendor has no verifier and documents have mixed reviewers")
    void execute_WithAccountApi_WhenVendorHasNoVerifier_ResolvesDocumentReviewers() {
        UUID vendorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        Vendor vendor = createVendor(vendorId, userId, null);
        VendorDocument docWithReviewer = createDocument(vendorId, UUID.randomUUID(), DocType.BUSINESS_LICENSE, reviewerId);
        VendorDocument docWithoutReviewer = createDocument(vendorId, UUID.randomUUID(), DocType.SAFETY_CERT, null);

        User reviewerUser = new User();
        reviewerUser.setId(reviewerId);
        reviewerUser.setFullName("Reviewer Solo");

        when(vendorInternalApi.findById(vendorId)).thenReturn(Optional.of(vendor));
        when(getVendorDocumentsUseCase.execute(userId)).thenReturn(List.of(docWithReviewer, docWithoutReviewer));
        when(accountInternalApi.findUsersByIds(Set.of(reviewerId)))
                .thenReturn(Map.of(reviewerId, reviewerUser));

        AdminVendorResponse response = useCase.execute(vendorId);

        assertThat(response.getVerifiedByName()).isNull();
        assertThat(response.getDocuments()).hasSize(2);
        assertThat(response.getDocuments().get(0).reviewedByName()).isEqualTo("Reviewer Solo");
        assertThat(response.getDocuments().get(1).reviewedByName()).isNull();
    }

    @Test
    @DisplayName("Should work with 2-argument constructor when accountInternalApi is null")
    void execute_WithTwoArgumentConstructor_AccountApiIsNull_LeavesNamesNull() {
        GetVendorDetailUseCase twoArgUseCase = new GetVendorDetailUseCase(vendorInternalApi, getVendorDocumentsUseCase);

        UUID vendorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID verifierId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        Vendor vendor = createVendor(vendorId, userId, verifierId);
        VendorDocument doc = createDocument(vendorId, UUID.randomUUID(), DocType.BUSINESS_LICENSE, reviewerId);

        when(vendorInternalApi.findById(vendorId)).thenReturn(Optional.of(vendor));
        when(getVendorDocumentsUseCase.execute(userId)).thenReturn(List.of(doc));

        AdminVendorResponse response = twoArgUseCase.execute(vendorId);

        assertThat(response.getVerifiedBy()).isEqualTo(verifierId);
        assertThat(response.getVerifiedByName()).isNull();
        assertThat(response.getDocuments().get(0).reviewedBy()).isEqualTo(reviewerId);
        assertThat(response.getDocuments().get(0).reviewedByName()).isNull();
    }

    private Vendor createVendor(UUID vendorId, UUID userId, UUID verifiedBy) {
        Vendor vendor = new Vendor();
        vendor.setId(vendorId);
        vendor.setUserId(userId);
        vendor.setBusinessName("Da Nang Sea Tours");
        vendor.setTaxCode("0401234567");
        vendor.setAddress("12 Vo Nguyen Giap, Da Nang");
        vendor.setBankAccountNumber("0123456789");
        vendor.setBankName("Vietcombank");
        vendor.setBankAccountHolder("DA NANG SEA TOURS");
        vendor.setVerificationStatus(VerificationStatus.APPROVED);
        vendor.setVerifiedBy(verifiedBy);
        vendor.setVerifiedAt(verifiedBy != null ? OffsetDateTime.parse("2026-10-01T10:00:00+07:00") : null);
        vendor.setRatingAvg(new BigDecimal("4.75"));
        vendor.setRatingCount(12);
        vendor.setBadgeTier(BadgeTier.TOP_RATED);
        vendor.setCreatedAt(OffsetDateTime.parse("2026-09-01T10:00:00+07:00"));
        vendor.setUpdatedAt(OffsetDateTime.parse("2026-10-02T10:00:00+07:00"));
        return vendor;
    }

    private VendorDocument createDocument(UUID vendorId, UUID docId, DocType docType, UUID reviewerId) {
        VendorDocument doc = new VendorDocument();
        doc.setId(docId);
        doc.setVendorId(vendorId);
        doc.setDocType(docType);
        doc.setFileUrl("https://storage.example/" + docId + ".pdf");
        doc.setStatus(reviewerId != null ? DocStatus.APPROVED : DocStatus.PENDING);
        doc.setReviewedBy(reviewerId);
        doc.setReviewedAt(reviewerId != null ? OffsetDateTime.parse("2026-10-01T10:00:00+07:00") : null);
        doc.setCreatedAt(OffsetDateTime.parse("2026-09-01T10:00:00+07:00"));
        doc.setUpdatedAt(OffsetDateTime.parse("2026-10-02T10:00:00+07:00"));
        return doc;
    }
}
