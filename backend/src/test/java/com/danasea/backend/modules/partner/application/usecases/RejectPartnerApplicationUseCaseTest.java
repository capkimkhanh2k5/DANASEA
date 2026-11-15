package com.danasea.backend.modules.partner.application.usecases;

import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.partner.domain.exceptions.ApplicationNotFoundException;
import com.danasea.backend.modules.partner.domain.exceptions.ApplicationStatusConflictException;
import com.danasea.backend.modules.partner.domain.models.PartnerApplicationStatus;
import com.danasea.backend.modules.partner.infrastructure.mappers.PartnerApplicationMapper;
import com.danasea.backend.modules.partner.infrastructure.persistence.entities.VendorRoleApplicationJpaEntity;
import com.danasea.backend.modules.partner.infrastructure.persistence.repositories.JpaVendorRoleApplicationRepository;
import com.danasea.backend.modules.partner.presentation.dtos.PartnerApplicationResponse;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RejectPartnerApplicationUseCaseTest {

    @Mock
    private JpaVendorRoleApplicationRepository applicationRepository;

    @Mock
    private AuditLogInternalApi auditLogInternalApi;

    private PartnerApplicationMapper mapper;
    private RejectPartnerApplicationUseCase useCase;

    private UUID applicationId;
    private UUID adminId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        mapper = new PartnerApplicationMapper();
        useCase = new RejectPartnerApplicationUseCase(applicationRepository, auditLogInternalApi, mapper);

        applicationId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Admin rejection sets status to REJECTED with rejectionReason and logs audit event")
    void execute_WhenPending_RejectsApplication() {
        VendorRoleApplicationJpaEntity entity = new VendorRoleApplicationJpaEntity();
        entity.setId(applicationId);
        entity.setUserId(userId);
        entity.setBusinessName("Danang Beach Tour Co.");
        entity.setStatus(PartnerApplicationStatus.PENDING);

        when(applicationRepository.findByIdWithLock(applicationId)).thenReturn(Optional.of(entity));
        when(applicationRepository.save(any(VendorRoleApplicationJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

        PartnerApplicationResponse response = useCase.execute(
                applicationId,
                adminId,
                "Tax code does not match official tax registry"
        );

        assertThat(response.getStatus()).isEqualTo(PartnerApplicationStatus.REJECTED);
        assertThat(response.getRejectionReason()).isEqualTo("Tax code does not match official tax registry");
        assertThat(response.getReviewedBy()).isEqualTo(adminId);
        assertThat(response.getReviewedAt()).isNotNull();

        verify(auditLogInternalApi).recordAuditLog(
                eq(adminId),
                eq("PARTNER_APPLICATION_REJECTION"),
                eq("PARTNER_APPLICATION"),
                eq(applicationId),
                contains("Tax code does not match")
        );
    }

    @Test
    @DisplayName("Throws IllegalArgumentException if rejection reason is blank")
    void execute_WhenBlankReason_ThrowsException() {
        assertThatThrownBy(() -> useCase.execute(applicationId, adminId, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rejection reason cannot be blank");

        verify(applicationRepository, never()).findByIdWithLock(any());
    }

    @Test
    @DisplayName("Throws 404 when application ID does not exist")
    void execute_WhenNotFound_ThrowsException() {
        when(applicationRepository.findByIdWithLock(applicationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(applicationId, adminId, "Valid reason"))
                .isInstanceOf(ApplicationNotFoundException.class)
                .hasMessage("error.application_not_found");
    }

    @Test
    @DisplayName("Throws conflict if application is not in PENDING status")
    void execute_WhenNotPending_ThrowsConflict() {
        VendorRoleApplicationJpaEntity entity = new VendorRoleApplicationJpaEntity();
        entity.setId(applicationId);
        entity.setStatus(PartnerApplicationStatus.REJECTED);

        when(applicationRepository.findByIdWithLock(applicationId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> useCase.execute(applicationId, adminId, "Valid reason"))
                .isInstanceOf(ApplicationStatusConflictException.class)
                .hasMessage("Only PENDING applications can be rejected");
    }
}
