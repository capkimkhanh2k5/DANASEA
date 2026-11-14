package com.danasea.backend.modules.vendor.application.usecases;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.vendor.domain.exceptions.UserLockedException;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorAlreadyExistsException;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorNotFoundException;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.infrastructure.mappers.VendorMapper;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterVendorProfileUseCaseTest {

    @Mock
    private JpaVendorRepository jpaVendorRepository;

    @Mock
    private AccountInternalApi accountInternalApi;

    private VendorMapper vendorMapper;

    private RegisterVendorProfileUseCase registerVendorProfileUseCase;

    private UUID userId;
    private User testUser;
    private RegisterVendorProfileCommand command;

    @BeforeEach
    void setUp() {
        vendorMapper = new VendorMapper();
        registerVendorProfileUseCase = new RegisterVendorProfileUseCase(
                jpaVendorRepository,
                accountInternalApi,
                vendorMapper
        );

        userId = UUID.randomUUID();
        testUser = new User();
        testUser.setId(userId);
        testUser.setEmail("vendor@example.com");
        testUser.setRole(Role.VENDOR);
        testUser.setIsLocked(false);

        command = new RegisterVendorProfileCommand(
                "Fresh Seafood Co",
                "0123456789",
                "123 Vo Nguyen Giap, Da Nang",
                "9876543210",
                "Vietcombank",
                "NGUYEN VAN A"
        );
    }

    @Test
    @DisplayName("Should successfully register vendor profile and set status to PENDING for user with VENDOR role")
    void shouldRegisterVendorSuccessfully() {
        when(accountInternalApi.findUserById(userId)).thenReturn(Optional.of(testUser));
        when(jpaVendorRepository.existsByUserId(userId)).thenReturn(false);

        UUID generatedVendorId = UUID.randomUUID();
        when(jpaVendorRepository.save(any(VendorJpaEntity.class))).thenAnswer(invocation -> {
            VendorJpaEntity entity = invocation.getArgument(0);
            entity.setId(generatedVendorId);
            return entity;
        });

        Vendor result = registerVendorProfileUseCase.execute(userId, command);

        assertNotNull(result);
        assertEquals(generatedVendorId, result.getId());
        assertEquals(userId, result.getUserId());
        assertEquals("Fresh Seafood Co", result.getBusinessName());
        assertEquals("0123456789", result.getTaxCode());
        assertEquals("123 Vo Nguyen Giap, Da Nang", result.getAddress());
        assertEquals("9876543210", result.getBankAccountNumber());
        assertEquals("Vietcombank", result.getBankName());
        assertEquals("NGUYEN VAN A", result.getBankAccountHolder());
        assertEquals(VerificationStatus.PENDING, result.getVerificationStatus());
        assertEquals(BadgeTier.NONE, result.getBadgeTier());
        assertEquals(BigDecimal.ZERO, result.getRatingAvg());
        assertEquals(0, result.getRatingCount());

        ArgumentCaptor<VendorJpaEntity> entityCaptor = ArgumentCaptor.forClass(VendorJpaEntity.class);
        verify(jpaVendorRepository).save(entityCaptor.capture());
        VendorJpaEntity savedEntity = entityCaptor.getValue();
        assertEquals(userId, savedEntity.getUserId());
        assertEquals("Fresh Seafood Co", savedEntity.getBusinessName());
        assertEquals("0123456789", savedEntity.getTaxCode());
        assertEquals("123 Vo Nguyen Giap, Da Nang", savedEntity.getAddress());
        assertEquals("9876543210", savedEntity.getBankAccountNumber());
        assertEquals("Vietcombank", savedEntity.getBankName());
        assertEquals("NGUYEN VAN A", savedEntity.getBankAccountHolder());
        assertEquals(VerificationStatus.PENDING, savedEntity.getVerificationStatus());
        assertEquals(BadgeTier.NONE, savedEntity.getBadgeTier());
        assertEquals(BigDecimal.ZERO, savedEntity.getRatingAvg());
        assertEquals(0, savedEntity.getRatingCount());

        verify(accountInternalApi, never()).saveUser(any(User.class));
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when non-vendor user attempts direct profile registration")
    void shouldThrowAccessDeniedExceptionWhenUserIsNotVendor() {
        testUser.setRole(Role.CUSTOMER);
        when(accountInternalApi.findUserById(userId)).thenReturn(Optional.of(testUser));

        assertThrows(AccessDeniedException.class, () ->
                registerVendorProfileUseCase.execute(userId, command));

        verify(jpaVendorRepository, never()).save(any(VendorJpaEntity.class));
        verify(accountInternalApi, never()).saveUser(any(User.class));
    }

    @Test
    @DisplayName("Should throw VendorAlreadyExistsException when vendor profile already exists for userId")
    void shouldThrowVendorAlreadyExistsExceptionWhenVendorAlreadyExists() {
        when(accountInternalApi.findUserById(userId)).thenReturn(Optional.of(testUser));
        when(jpaVendorRepository.existsByUserId(userId)).thenReturn(true);

        assertThrows(VendorAlreadyExistsException.class, () ->
                registerVendorProfileUseCase.execute(userId, command));

        verify(jpaVendorRepository, never()).save(any(VendorJpaEntity.class));
        verify(accountInternalApi, never()).saveUser(any(User.class));
    }

    @Test
    @DisplayName("Should throw UserLockedException when user account is locked")
    void shouldThrowUserLockedExceptionWhenUserIsLocked() {
        testUser.setIsLocked(true);
        when(accountInternalApi.findUserById(userId)).thenReturn(Optional.of(testUser));

        assertThrows(UserLockedException.class, () ->
                registerVendorProfileUseCase.execute(userId, command));

        verify(jpaVendorRepository, never()).existsByUserId(any());
        verify(jpaVendorRepository, never()).save(any());
        verify(accountInternalApi, never()).saveUser(any());
    }

    @Test
    @DisplayName("Should throw VendorNotFoundException when user record is not found in account system")
    void shouldThrowVendorNotFoundExceptionWhenUserNotFound() {
        when(accountInternalApi.findUserById(userId)).thenReturn(Optional.empty());

        assertThrows(VendorNotFoundException.class, () ->
                registerVendorProfileUseCase.execute(userId, command));

        verify(jpaVendorRepository, never()).existsByUserId(any());
        verify(jpaVendorRepository, never()).save(any());
        verify(accountInternalApi, never()).saveUser(any());
    }

    @Test
    @DisplayName("Should allow registration when user isLocked is null (defaulting to not locked)")
    void shouldAllowRegistrationWhenIsLockedIsNull() {
        testUser.setIsLocked(null);
        when(accountInternalApi.findUserById(userId)).thenReturn(Optional.of(testUser));
        when(jpaVendorRepository.existsByUserId(userId)).thenReturn(false);
        when(jpaVendorRepository.save(any(VendorJpaEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Vendor result = registerVendorProfileUseCase.execute(userId, command);

        assertNotNull(result);
        assertEquals(VerificationStatus.PENDING, result.getVerificationStatus());
        verify(accountInternalApi, never()).saveUser(any(User.class));
    }
}
