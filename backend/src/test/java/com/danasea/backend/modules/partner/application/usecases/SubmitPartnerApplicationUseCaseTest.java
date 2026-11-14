package com.danasea.backend.modules.partner.application.usecases;

import com.danasea.backend.modules.partner.application.services.CustomerPartnerEligibilityService;
import com.danasea.backend.modules.partner.domain.exceptions.ApplicationStatusConflictException;
import com.danasea.backend.modules.partner.domain.exceptions.CustomerHasActiveObligationsException;
import com.danasea.backend.modules.partner.domain.models.PartnerApplicationStatus;
import com.danasea.backend.modules.partner.infrastructure.mappers.PartnerApplicationMapper;
import com.danasea.backend.modules.partner.infrastructure.persistence.entities.VendorRoleApplicationJpaEntity;
import com.danasea.backend.modules.partner.infrastructure.persistence.repositories.JpaVendorRoleApplicationRepository;
import com.danasea.backend.modules.partner.presentation.dtos.PartnerApplicationResponse;
import com.danasea.backend.modules.partner.presentation.dtos.SubmitPartnerApplicationRequest;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmitPartnerApplicationUseCaseTest {

    @Mock
    private JpaVendorRoleApplicationRepository applicationRepository;

    @Mock
    private JpaVendorRepository vendorRepository;

    @Mock
    private CustomerPartnerEligibilityService partnerEligibilityService;

    private PartnerApplicationMapper mapper;
    private SubmitPartnerApplicationUseCase useCase;

    private UUID userId;
    private SubmitPartnerApplicationRequest request;

    @BeforeEach
    void setUp() {
        mapper = new PartnerApplicationMapper();
        useCase = new SubmitPartnerApplicationUseCase(applicationRepository, vendorRepository, partnerEligibilityService, mapper);

        userId = UUID.randomUUID();
        request = new SubmitPartnerApplicationRequest(
                "Danang Beach Tour Co.",
                "0401234567",
                "123 Vo Nguyen Giap, Da Nang",
                "1234567890",
                "Vietcombank",
                "NGUYEN VAN A"
        );
    }

    @Test
    @DisplayName("Successfully submit initial application when user has no existing records")
    void execute_WhenFirstTime_CreatesApplicationPending() {
        when(vendorRepository.existsByUserId(userId)).thenReturn(false);
        when(applicationRepository.findByUserIdWithLock(userId)).thenReturn(Optional.empty());

        when(applicationRepository.saveAndFlush(any(VendorRoleApplicationJpaEntity.class))).thenAnswer(invocation -> {
            VendorRoleApplicationJpaEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        PartnerApplicationResponse response = useCase.execute(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getBusinessName()).isEqualTo("Danang Beach Tour Co.");
        assertThat(response.getTaxCode()).isEqualTo("0401234567");
        assertThat(response.getStatus()).isEqualTo(PartnerApplicationStatus.PENDING);
        assertThat(response.getRejectionReason()).isNull();

        verify(applicationRepository).saveAndFlush(any(VendorRoleApplicationJpaEntity.class));
    }

    @Test
    @DisplayName("Throws conflict when concurrent submissions collide on unique user_id constraint")
    void execute_WhenConcurrentInsert_ThrowsConflict() {
        when(vendorRepository.existsByUserId(userId)).thenReturn(false);
        when(applicationRepository.findByUserIdWithLock(userId)).thenReturn(Optional.empty());
        when(applicationRepository.saveAndFlush(any(VendorRoleApplicationJpaEntity.class)))
                .thenThrow(new DataIntegrityViolationException("Unique constraint violation: uk_partner_app_user"));

        assertThatThrownBy(() -> useCase.execute(userId, request))
                .isInstanceOf(ApplicationStatusConflictException.class)
                .hasMessage("error.application_already_pending");

        verify(applicationRepository).saveAndFlush(any(VendorRoleApplicationJpaEntity.class));
    }

    @Test
    @DisplayName("Throws conflict if user is already an existing Vendor")
    void execute_WhenUserAlreadyVendor_ThrowsConflict() {
        when(vendorRepository.existsByUserId(userId)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(userId, request))
                .isInstanceOf(ApplicationStatusConflictException.class)
                .hasMessageContaining("User already has an existing Vendor profile");

        verify(applicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Throws conflict if user already has an application in PENDING status")
    void execute_WhenAlreadyPending_ThrowsConflict() {
        when(vendorRepository.existsByUserId(userId)).thenReturn(false);

        VendorRoleApplicationJpaEntity existing = new VendorRoleApplicationJpaEntity();
        existing.setId(UUID.randomUUID());
        existing.setUserId(userId);
        existing.setStatus(PartnerApplicationStatus.PENDING);

        when(applicationRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(userId, request))
                .isInstanceOf(ApplicationStatusConflictException.class)
                .hasMessage("error.application_already_pending");

        verify(applicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Throws conflict if user already has an application in APPROVED status")
    void execute_WhenAlreadyApproved_ThrowsConflict() {
        when(vendorRepository.existsByUserId(userId)).thenReturn(false);

        VendorRoleApplicationJpaEntity existing = new VendorRoleApplicationJpaEntity();
        existing.setId(UUID.randomUUID());
        existing.setUserId(userId);
        existing.setStatus(PartnerApplicationStatus.APPROVED);

        when(applicationRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(userId, request))
                .isInstanceOf(ApplicationStatusConflictException.class)
                .hasMessage("error.application_already_approved");

        verify(applicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Throws conflict if user already has a REJECTED application (must use /resubmit endpoint)")
    void execute_WhenExistingRejected_ThrowsConflict() {
        when(vendorRepository.existsByUserId(userId)).thenReturn(false);

        VendorRoleApplicationJpaEntity existing = new VendorRoleApplicationJpaEntity();
        existing.setId(UUID.randomUUID());
        existing.setUserId(userId);
        existing.setStatus(PartnerApplicationStatus.REJECTED);
        existing.setRejectionReason("Tax code mismatch");

        when(applicationRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(userId, request))
                .isInstanceOf(ApplicationStatusConflictException.class)
                .hasMessage("error.application_already_rejected");

        verify(applicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Throws exception when customer has active obligations (bookings, orders, refunds, disputes)")
    void execute_WhenCustomerHasActiveObligations_ThrowsException() {
        when(vendorRepository.existsByUserId(userId)).thenReturn(false);
        doThrow(new CustomerHasActiveObligationsException("Customer has active booking hold or pending payment"))
                .when(partnerEligibilityService).validateCustomerEligibility(userId);

        assertThatThrownBy(() -> useCase.execute(userId, request))
                .isInstanceOf(CustomerHasActiveObligationsException.class)
                .hasMessageContaining("active booking hold or pending payment");

        verify(applicationRepository, never()).save(any());
        verify(applicationRepository, never()).saveAndFlush(any());
    }
}
