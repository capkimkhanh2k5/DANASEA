package com.danasea.backend.modules.partner.application.usecases;

import com.danasea.backend.modules.partner.application.services.CustomerPartnerEligibilityService;
import com.danasea.backend.modules.partner.domain.exceptions.ApplicationNotFoundException;
import com.danasea.backend.modules.partner.domain.exceptions.ApplicationStatusConflictException;
import com.danasea.backend.modules.partner.domain.exceptions.CustomerHasActiveObligationsException;
import com.danasea.backend.modules.partner.domain.models.PartnerApplicationStatus;
import com.danasea.backend.modules.partner.infrastructure.mappers.PartnerApplicationMapper;
import com.danasea.backend.modules.partner.infrastructure.persistence.entities.VendorRoleApplicationJpaEntity;
import com.danasea.backend.modules.partner.infrastructure.persistence.repositories.JpaVendorRoleApplicationRepository;
import com.danasea.backend.modules.partner.presentation.dtos.PartnerApplicationResponse;
import com.danasea.backend.modules.partner.presentation.dtos.ResubmitPartnerApplicationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResubmitPartnerApplicationUseCaseTest {

    @Mock
    private JpaVendorRoleApplicationRepository applicationRepository;

    @Mock
    private CustomerPartnerEligibilityService partnerEligibilityService;

    private PartnerApplicationMapper mapper;
    private ResubmitPartnerApplicationUseCase useCase;

    private UUID userId;
    private ResubmitPartnerApplicationRequest request;

    @BeforeEach
    void setUp() {
        mapper = new PartnerApplicationMapper();
        useCase = new ResubmitPartnerApplicationUseCase(applicationRepository, partnerEligibilityService, mapper);

        userId = UUID.randomUUID();
        request = new ResubmitPartnerApplicationRequest(
                "Updated Danang Tours",
                "0409999999",
                "456 Tran Phu, Da Nang",
                "9876543210",
                "BIDV",
                "NGUYEN VAN B"
        );
    }

    @Test
    @DisplayName("Resubmitting a REJECTED application updates details, clears rejection reason, and sets status to PENDING")
    void execute_WhenRejected_ResubmitsToPending() {
        VendorRoleApplicationJpaEntity entity = new VendorRoleApplicationJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setBusinessName("Old Name");
        entity.setStatus(PartnerApplicationStatus.REJECTED);
        entity.setRejectionReason("Invalid address");
        entity.setReviewedBy(UUID.randomUUID());

        when(applicationRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(entity));
        when(applicationRepository.save(any(VendorRoleApplicationJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

        PartnerApplicationResponse response = useCase.execute(userId, request);

        assertThat(response.getStatus()).isEqualTo(PartnerApplicationStatus.PENDING);
        assertThat(response.getRejectionReason()).isNull();
        assertThat(response.getReviewedBy()).isNull();
        assertThat(response.getBusinessName()).isEqualTo("Updated Danang Tours");
        assertThat(response.getTaxCode()).isEqualTo("0409999999");
        assertThat(response.getBankName()).isEqualTo("BIDV");

        verify(applicationRepository).save(any(VendorRoleApplicationJpaEntity.class));
    }

    @Test
    @DisplayName("Throws 404 when no application exists for user")
    void execute_WhenNotFound_ThrowsException() {
        when(applicationRepository.findByUserIdWithLock(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(userId, request))
                .isInstanceOf(ApplicationNotFoundException.class)
                .hasMessage("error.application_not_found");
    }

    @Test
    @DisplayName("Throws conflict if application is still PENDING")
    void execute_WhenPending_ThrowsConflict() {
        VendorRoleApplicationJpaEntity entity = new VendorRoleApplicationJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setStatus(PartnerApplicationStatus.PENDING);

        when(applicationRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> useCase.execute(userId, request))
                .isInstanceOf(ApplicationStatusConflictException.class)
                .hasMessage("Only REJECTED applications can be resubmitted");

        verify(applicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Throws conflict if application is already APPROVED")
    void execute_WhenApproved_ThrowsConflict() {
        VendorRoleApplicationJpaEntity entity = new VendorRoleApplicationJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setStatus(PartnerApplicationStatus.APPROVED);

        when(applicationRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> useCase.execute(userId, request))
                .isInstanceOf(ApplicationStatusConflictException.class)
                .hasMessage("Only REJECTED applications can be resubmitted");

        verify(applicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Throws exception when customer has active obligations during resubmission")
    void execute_WhenCustomerHasActiveObligations_ThrowsException() {
        doThrow(new CustomerHasActiveObligationsException("Customer has active booking hold or pending payment"))
                .when(partnerEligibilityService).validateCustomerEligibility(userId);

        assertThatThrownBy(() -> useCase.execute(userId, request))
                .isInstanceOf(CustomerHasActiveObligationsException.class)
                .hasMessageContaining("active booking hold or pending payment");

        verify(applicationRepository, never()).findByUserIdWithLock(any());
        verify(applicationRepository, never()).save(any());
    }
}
