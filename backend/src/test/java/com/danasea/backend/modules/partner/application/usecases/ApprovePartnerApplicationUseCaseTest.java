package com.danasea.backend.modules.partner.application.usecases;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.partner.application.services.CustomerPartnerEligibilityService;
import com.danasea.backend.modules.partner.domain.exceptions.ApplicationNotFoundException;
import com.danasea.backend.modules.partner.domain.exceptions.ApplicationStatusConflictException;
import com.danasea.backend.modules.partner.domain.exceptions.CustomerHasActiveObligationsException;
import com.danasea.backend.modules.partner.domain.models.PartnerApplicationStatus;
import com.danasea.backend.modules.partner.infrastructure.mappers.PartnerApplicationMapper;
import com.danasea.backend.modules.partner.infrastructure.persistence.entities.VendorRoleApplicationJpaEntity;
import com.danasea.backend.modules.partner.infrastructure.persistence.repositories.JpaVendorRoleApplicationRepository;
import com.danasea.backend.modules.partner.presentation.dtos.PartnerApplicationResponse;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class ApprovePartnerApplicationUseCaseTest {

    @Mock
    private JpaVendorRoleApplicationRepository applicationRepository;

    @Mock
    private JpaVendorRepository vendorRepository;

    @Mock
    private CustomerPartnerEligibilityService partnerEligibilityService;

    @Mock
    private AccountInternalApi accountInternalApi;

    @Mock
    private AuditLogInternalApi auditLogInternalApi;

    private PartnerApplicationMapper mapper;
    private ApprovePartnerApplicationUseCase useCase;

    private UUID applicationId;
    private UUID adminId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        mapper = new PartnerApplicationMapper();
        useCase = new ApprovePartnerApplicationUseCase(
                applicationRepository,
                vendorRepository,
                partnerEligibilityService,
                accountInternalApi,
                auditLogInternalApi,
                mapper
        );

        applicationId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Admin approval transitions application to APPROVED, sets users.role = VENDOR, and initializes Vendor with PENDING verification")
    void execute_WhenPending_PromotesRoleAndCreatesVendor() {
        VendorRoleApplicationJpaEntity entity = new VendorRoleApplicationJpaEntity();
        entity.setId(applicationId);
        entity.setUserId(userId);
        entity.setBusinessName("Danang Beach Tour Co.");
        entity.setTaxCode("0401234567");
        entity.setAddress("123 Vo Nguyen Giap");
        entity.setBankAccountNumber("1234567890");
        entity.setBankName("Vietcombank");
        entity.setBankAccountHolder("NGUYEN VAN A");
        entity.setStatus(PartnerApplicationStatus.PENDING);

        when(applicationRepository.findByIdWithLock(applicationId)).thenReturn(Optional.of(entity));
        when(vendorRepository.existsByUserId(userId)).thenReturn(false);

        User customer = new User();
        customer.setId(userId);
        customer.setEmail("partner@danang.vn");
        customer.setRole(Role.CUSTOMER);
        customer.setSessionVersion(0L);

        when(accountInternalApi.findUserByIdForUpdate(userId)).thenReturn(Optional.of(customer));
        when(applicationRepository.save(any(VendorRoleApplicationJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

        PartnerApplicationResponse response = useCase.execute(applicationId, adminId);

        // 1. Application check
        assertThat(response.getStatus()).isEqualTo(PartnerApplicationStatus.APPROVED);
        assertThat(response.getReviewedBy()).isEqualTo(adminId);
        assertThat(response.getReviewedAt()).isNotNull();

        // 2. User role promotion check and sessionVersion increment
        assertThat(customer.getRole()).isEqualTo(Role.VENDOR);
        assertThat(customer.getSessionVersion()).isEqualTo(1L);
        verify(accountInternalApi).saveUser(customer);
        verify(accountInternalApi).revokeAllRefreshTokensByUserId(userId);

        // 3. Vendor entity creation check
        ArgumentCaptor<VendorJpaEntity> vendorCaptor = ArgumentCaptor.forClass(VendorJpaEntity.class);
        verify(vendorRepository).save(vendorCaptor.capture());
        VendorJpaEntity savedVendor = vendorCaptor.getValue();
        assertThat(savedVendor.getUserId()).isEqualTo(userId);
        assertThat(savedVendor.getBusinessName()).isEqualTo("Danang Beach Tour Co.");
        assertThat(savedVendor.getTaxCode()).isEqualTo("0401234567");
        assertThat(savedVendor.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(savedVendor.getBadgeTier()).isEqualTo(BadgeTier.NONE);

        // 4. Audit log check
        verify(auditLogInternalApi).recordAuditLog(
                eq(adminId),
                eq("PARTNER_APPLICATION_APPROVAL"),
                eq("PARTNER_APPLICATION"),
                eq(applicationId),
                contains(userId.toString())
        );
    }

    @Test
    @DisplayName("Throws 404 when application ID not found")
    void execute_WhenNotFound_ThrowsException() {
        when(applicationRepository.findByIdWithLock(applicationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(applicationId, adminId))
                .isInstanceOf(ApplicationNotFoundException.class)
                .hasMessage("error.application_not_found");

        verify(accountInternalApi, never()).saveUser(any());
        verify(vendorRepository, never()).save(any());
    }

    @Test
    @DisplayName("Throws conflict when application is not in PENDING status")
    void execute_WhenNotPending_ThrowsConflict() {
        VendorRoleApplicationJpaEntity entity = new VendorRoleApplicationJpaEntity();
        entity.setId(applicationId);
        entity.setStatus(PartnerApplicationStatus.APPROVED);

        when(applicationRepository.findByIdWithLock(applicationId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> useCase.execute(applicationId, adminId))
                .isInstanceOf(ApplicationStatusConflictException.class)
                .hasMessage("Only PENDING applications can be approved");

        verify(accountInternalApi, never()).saveUser(any());
        verify(vendorRepository, never()).save(any());
    }

    @Test
    @DisplayName("Throws conflict if Vendor profile already exists for user")
    void execute_WhenVendorExists_ThrowsConflict() {
        VendorRoleApplicationJpaEntity entity = new VendorRoleApplicationJpaEntity();
        entity.setId(applicationId);
        entity.setUserId(userId);
        entity.setStatus(PartnerApplicationStatus.PENDING);

        when(applicationRepository.findByIdWithLock(applicationId)).thenReturn(Optional.of(entity));
        when(vendorRepository.existsByUserId(userId)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(applicationId, adminId))
                .isInstanceOf(ApplicationStatusConflictException.class)
                .hasMessage("A Vendor profile already exists for this user");

        verify(accountInternalApi, never()).saveUser(any());
    }

    @Test
    @DisplayName("Fails atomically if vendor creation fails during approval")
    void execute_WhenVendorSaveFails_ThrowsExceptionAndAborts() {
        VendorRoleApplicationJpaEntity entity = new VendorRoleApplicationJpaEntity();
        entity.setId(applicationId);
        entity.setUserId(userId);
        entity.setBusinessName("Danang Beach Tour Co.");
        entity.setTaxCode("0401234567");
        entity.setAddress("123 Vo Nguyen Giap");
        entity.setBankAccountNumber("1234567890");
        entity.setBankName("Vietcombank");
        entity.setBankAccountHolder("NGUYEN VAN A");
        entity.setStatus(PartnerApplicationStatus.PENDING);

        when(applicationRepository.findByIdWithLock(applicationId)).thenReturn(Optional.of(entity));
        when(vendorRepository.existsByUserId(userId)).thenReturn(false);

        User customer = new User();
        customer.setId(userId);
        customer.setEmail("partner@danang.vn");
        customer.setRole(Role.CUSTOMER);

        when(accountInternalApi.findUserByIdForUpdate(userId)).thenReturn(Optional.of(customer));
        when(applicationRepository.save(any(VendorRoleApplicationJpaEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(vendorRepository.save(any(VendorJpaEntity.class))).thenThrow(new RuntimeException("Database error during vendor persistence"));

        assertThatThrownBy(() -> useCase.execute(applicationId, adminId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database error during vendor persistence");

        verify(auditLogInternalApi, never()).recordAuditLog(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Admin approval aborts and keeps application PENDING when customer has active obligations")
    void execute_WhenCustomerHasActiveObligations_AbortsAndKeepsPending() {
        VendorRoleApplicationJpaEntity entity = new VendorRoleApplicationJpaEntity();
        entity.setId(applicationId);
        entity.setUserId(userId);
        entity.setStatus(PartnerApplicationStatus.PENDING);

        when(applicationRepository.findByIdWithLock(applicationId)).thenReturn(Optional.of(entity));

        User customer = new User();
        customer.setId(userId);
        customer.setRole(Role.CUSTOMER);
        when(accountInternalApi.findUserByIdForUpdate(userId)).thenReturn(Optional.of(customer));
        when(vendorRepository.existsByUserId(userId)).thenReturn(false);

        doThrow(new CustomerHasActiveObligationsException("Customer has active booking hold or pending payment"))
                .when(partnerEligibilityService).validateCustomerEligibility(userId);

        assertThatThrownBy(() -> useCase.execute(applicationId, adminId))
                .isInstanceOf(CustomerHasActiveObligationsException.class)
                .hasMessageContaining("active booking hold or pending payment");

        // Verifications: Role is NOT promoted, vendor is NOT created, application is NOT approved
        assertThat(entity.getStatus()).isEqualTo(PartnerApplicationStatus.PENDING);
        verify(accountInternalApi, never()).saveUser(any());
        verify(vendorRepository, never()).save(any());
        verify(auditLogInternalApi, never()).recordAuditLog(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Admin approval aborts when user is not a CUSTOMER")
    void execute_WhenUserNotCustomer_ThrowsConflict() {
        VendorRoleApplicationJpaEntity entity = new VendorRoleApplicationJpaEntity();
        entity.setId(applicationId);
        entity.setUserId(userId);
        entity.setStatus(PartnerApplicationStatus.PENDING);

        when(applicationRepository.findByIdWithLock(applicationId)).thenReturn(Optional.of(entity));

        User user = new User();
        user.setId(userId);
        user.setRole(Role.VENDOR);
        when(accountInternalApi.findUserByIdForUpdate(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> useCase.execute(applicationId, adminId))
                .isInstanceOf(ApplicationStatusConflictException.class)
                .hasMessage("User is not a CUSTOMER");

        verify(partnerEligibilityService, never()).validateCustomerEligibility(any());
        verify(accountInternalApi, never()).saveUser(any());
    }
}
