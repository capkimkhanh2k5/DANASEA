package com.danasea.backend.modules.admin.application.usecases;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.admin.presentation.dtos.AdminVendorResponse;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetVendorsUseCaseCoverageTest {

    @Mock
    private VendorInternalApi vendorInternalApi;

    @Mock
    private AccountInternalApi accountInternalApi;

    private GetVendorsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetVendorsUseCase(vendorInternalApi, accountInternalApi);
    }

    @Test
    @DisplayName("Should use 1-argument constructor and leave verifiedByName null when accountInternalApi is null")
    void execute_WithOneArgumentConstructor_AccountApiNull() {
        GetVendorsUseCase oneArgUseCase = new GetVendorsUseCase(vendorInternalApi);
        PageRequest pageable = PageRequest.of(0, 10);
        UUID verifierId = UUID.randomUUID();
        Vendor vendor = createVendor(UUID.randomUUID(), verifierId);

        when(vendorInternalApi.getVendors(VerificationStatus.APPROVED, pageable))
                .thenReturn(new PageImpl<>(List.of(vendor), pageable, 1));

        Page<AdminVendorResponse> result = oneArgUseCase.execute(VerificationStatus.APPROVED, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getVerifiedBy()).isEqualTo(verifierId);
        assertThat(result.getContent().get(0).getVerifiedByName()).isNull();
    }

    @Test
    @DisplayName("Should resolve verifiedByName for vendors with verifiers and handle vendors with null verifier")
    void execute_WithAccountApi_ResolvesAllVerifiersAndHandlesNullVerifier() {
        PageRequest pageable = PageRequest.of(0, 10);
        UUID verifier1 = UUID.randomUUID();
        UUID verifier2 = UUID.randomUUID();

        Vendor vendor1 = createVendor(UUID.randomUUID(), verifier1);
        Vendor vendor2 = createVendor(UUID.randomUUID(), verifier2);
        Vendor vendor3 = createVendor(UUID.randomUUID(), null); // null verifiedBy covers Objects::nonNull false branch

        User user1 = new User();
        user1.setId(verifier1);
        user1.setFullName("Verifier One");

        User user2 = new User();
        user2.setId(verifier2);
        user2.setFullName("Verifier Two");

        when(vendorInternalApi.getVendors(VerificationStatus.APPROVED, pageable))
                .thenReturn(new PageImpl<>(List.of(vendor1, vendor2, vendor3), pageable, 3));
        when(accountInternalApi.findUsersByIds(Set.of(verifier1, verifier2)))
                .thenReturn(Map.of(verifier1, user1, verifier2, user2));

        Page<AdminVendorResponse> result = useCase.execute(VerificationStatus.APPROVED, pageable);

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).getVerifiedByName()).isEqualTo("Verifier One");
        assertThat(result.getContent().get(1).getVerifiedByName()).isEqualTo("Verifier Two");
        assertThat(result.getContent().get(2).getVerifiedByName()).isNull();

        verify(accountInternalApi).findUsersByIds(Set.of(verifier1, verifier2));
    }

    @Test
    @DisplayName("Should leave verifiedByName null when verifier ID is not found in accountInternalApi map")
    void execute_WithAccountApi_WhenVerifierNotInMap_LeavesNameNull() {
        PageRequest pageable = PageRequest.of(0, 10);
        UUID verifierId = UUID.randomUUID();
        Vendor vendor = createVendor(UUID.randomUUID(), verifierId);

        when(vendorInternalApi.getVendors(VerificationStatus.APPROVED, pageable))
                .thenReturn(new PageImpl<>(List.of(vendor), pageable, 1));
        when(accountInternalApi.findUsersByIds(Set.of(verifierId)))
                .thenReturn(Map.of()); // Not found

        Page<AdminVendorResponse> result = useCase.execute(VerificationStatus.APPROVED, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getVerifiedBy()).isEqualTo(verifierId);
        assertThat(result.getContent().get(0).getVerifiedByName()).isNull();
    }

    @Test
    @DisplayName("Should skip accountInternalApi call when all vendors have null verifiedBy")
    void execute_WithAccountApi_WhenAllVendorsHaveNullVerifier_SkipsAccountApiCall() {
        PageRequest pageable = PageRequest.of(0, 10);
        Vendor vendor1 = createVendor(UUID.randomUUID(), null);
        Vendor vendor2 = createVendor(UUID.randomUUID(), null);

        when(vendorInternalApi.getVendors(VerificationStatus.PENDING, pageable))
                .thenReturn(new PageImpl<>(List.of(vendor1, vendor2), pageable, 2));

        Page<AdminVendorResponse> result = useCase.execute(VerificationStatus.PENDING, pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getVerifiedByName()).isNull();
        assertThat(result.getContent().get(1).getVerifiedByName()).isNull();

        verifyNoInteractions(accountInternalApi);
    }

    @Test
    @DisplayName("Should preserve empty page and skip accountInternalApi call when page is empty")
    void execute_WithAccountApi_WhenPageIsEmpty_ReturnsEmptyPageAndSkipsAccountApi() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(vendorInternalApi.getVendors(VerificationStatus.REJECTED, pageable))
                .thenReturn(Page.empty(pageable));

        Page<AdminVendorResponse> result = useCase.execute(VerificationStatus.REJECTED, pageable);

        assertThat(result.getContent()).isEmpty();
        verifyNoInteractions(accountInternalApi);
    }

    private Vendor createVendor(UUID vendorId, UUID verifiedBy) {
        Vendor vendor = new Vendor();
        vendor.setId(vendorId);
        vendor.setUserId(UUID.randomUUID());
        vendor.setBusinessName("Vendor " + vendorId.toString().substring(0, 8));
        vendor.setTaxCode("0401234567");
        vendor.setAddress("12 Vo Nguyen Giap, Da Nang");
        vendor.setBankAccountNumber("0123456789");
        vendor.setBankName("Vietcombank");
        vendor.setBankAccountHolder("VENDOR HOLDER");
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
}
