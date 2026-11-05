package com.danasea.backend.modules.vendor.application.usecases;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorNotFoundException;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.infrastructure.mappers.VendorMapper;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import com.danasea.backend.modules.vendor.presentation.controllers.VendorProfileController;
import com.danasea.backend.modules.vendor.presentation.dtos.VendorProfileResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetVendorProfileUseCaseTest {

    @Mock
    private JpaVendorRepository vendorRepository;
    @Mock
    private AccountInternalApi accountInternalApi;
    private GetVendorProfileUseCase useCase;
    private VendorProfileController controller;

    @BeforeEach
    void setUp() {
        useCase = new GetVendorProfileUseCase(vendorRepository, new VendorMapper());
        controller = new VendorProfileController(mock(RegisterVendorProfileUseCase.class), useCase,
                mock(UpdateVendorProfileUseCase.class), mock(UploadVendorDocumentUseCase.class),
                mock(GetVendorDocumentsUseCase.class), accountInternalApi);
    }

    @Test
    void resolvesCurrentAccountAndMapsItsCompleteVendorProfile() {
        UUID userId = UUID.randomUUID();
        String email = "vendor@example.test";
        User user = new User();
        user.setId(userId);
        VendorJpaEntity stored = vendor(userId);
        when(accountInternalApi.findUserByEmail(email)).thenReturn(Optional.of(user));
        when(vendorRepository.findByUserId(userId)).thenReturn(Optional.of(stored));

        ResponseEntity<VendorProfileResponse> response = controller.getProfile(() -> email);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(new VendorProfileResponse(stored.getId(), userId,
                "Da Nang Sea Tours", "0401234567", "12 Vo Nguyen Giap, Da Nang", "0123456789",
                "Vietcombank", "DA NANG SEA TOURS", VerificationStatus.APPROVED, BadgeTier.TOP_RATED,
                new BigDecimal("4.75"), 12, stored.getCreatedAt(), stored.getUpdatedAt()));
        verify(accountInternalApi).findUserByEmail(email);
        verify(vendorRepository).findByUserId(userId);
        verifyNoMoreInteractions(accountInternalApi, vendorRepository);
    }

    @Test
    void retainsVerificationActorAndTimeInTheMappedDomainProfile() {
        UUID userId = UUID.randomUUID();
        VendorJpaEntity stored = vendor(userId);
        when(vendorRepository.findByUserId(userId)).thenReturn(Optional.of(stored));

        Vendor result = useCase.execute(userId);

        assertThat(result.getId()).isEqualTo(stored.getId());
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(result.getVerifiedBy()).isEqualTo(stored.getVerifiedBy());
        assertThat(result.getVerifiedAt()).isEqualTo(stored.getVerifiedAt());
        verify(vendorRepository).findByUserId(userId);
        verifyNoMoreInteractions(vendorRepository);
    }

    @Test
    void resolvesUuidPrincipalAndPreservesPendingProfileWithoutOptionalBankOrRatingData() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        VendorJpaEntity stored = new VendorJpaEntity();
        stored.setId(UUID.randomUUID());
        stored.setUserId(userId);
        stored.setBusinessName("New vendor");
        stored.setVerificationStatus(VerificationStatus.PENDING);
        when(accountInternalApi.findUserByEmail(userId.toString())).thenReturn(Optional.empty());
        when(accountInternalApi.findUserById(userId)).thenReturn(Optional.of(user));
        when(vendorRepository.findByUserId(userId)).thenReturn(Optional.of(stored));

        ResponseEntity<VendorProfileResponse> response = controller.getProfile(userId::toString);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(new VendorProfileResponse(stored.getId(), userId,
                "New vendor", null, null, null, null, null, VerificationStatus.PENDING,
                null, null, null, null, null));
        verify(vendorRepository).findByUserId(userId);
        verify(accountInternalApi).findUserByEmail(userId.toString());
        verify(accountInternalApi).findUserById(userId);
        verifyNoMoreInteractions(accountInternalApi, vendorRepository);
    }

    @Test
    void rejectsMissingVendorProfileForTheResolvedAccount() {
        UUID userId = UUID.randomUUID();
        when(vendorRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(userId))
                .isInstanceOf(VendorNotFoundException.class).hasMessage("Vendor profile not found");

        verify(vendorRepository).findByUserId(userId);
        verifyNoMoreInteractions(vendorRepository);
    }

    @Test
    void rejectsMissingAccountBeforeLoadingAnyVendorProfile() {
        String email = "missing@example.test";
        when(accountInternalApi.findUserByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getProfile(() -> email))
                .isInstanceOf(VendorNotFoundException.class).hasMessage("User not found: " + email);

        verifyNoInteractions(vendorRepository);
        verify(accountInternalApi).findUserByEmail(email);
        verifyNoMoreInteractions(accountInternalApi);
    }

    @Test
    void rejectsAbsentPrincipalBeforeAnyAccountOrVendorLookup() {
        assertThatThrownBy(() -> controller.getProfile(null))
                .isInstanceOf(AccessDeniedException.class).hasMessage("User is not authenticated");
        verifyNoInteractions(accountInternalApi, vendorRepository);
    }

    private VendorJpaEntity vendor(UUID userId) {
        VendorJpaEntity stored = new VendorJpaEntity();
        stored.setId(UUID.randomUUID());
        stored.setUserId(userId);
        stored.setBusinessName("Da Nang Sea Tours");
        stored.setTaxCode("0401234567");
        stored.setAddress("12 Vo Nguyen Giap, Da Nang");
        stored.setBankAccountNumber("0123456789");
        stored.setBankName("Vietcombank");
        stored.setBankAccountHolder("DA NANG SEA TOURS");
        stored.setVerificationStatus(VerificationStatus.APPROVED);
        stored.setVerifiedBy(UUID.randomUUID());
        stored.setVerifiedAt(OffsetDateTime.parse("2026-10-01T10:00:00+07:00"));
        stored.setBadgeTier(BadgeTier.TOP_RATED);
        stored.setRatingAvg(new BigDecimal("4.75"));
        stored.setRatingCount(12);
        stored.setCreatedAt(OffsetDateTime.parse("2026-09-01T10:00:00+07:00"));
        stored.setUpdatedAt(OffsetDateTime.parse("2026-10-02T10:00:00+07:00"));
        return stored;
    }
}
