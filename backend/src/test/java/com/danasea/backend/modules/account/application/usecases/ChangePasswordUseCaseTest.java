package com.danasea.backend.modules.account.application.usecases;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;

import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.security.authentication.application.ports.PasswordHasher;
import com.danasea.backend.security.authentication.domain.exceptions.InvalidCredentialsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChangePasswordUseCaseTest {

    private AccountInternalApi accountInternalApi;
    private PasswordHasher passwordHasher;
    private AuditLogInternalApi auditLogInternalApi;
    private ChangePasswordUseCase changePasswordUseCase;

    @BeforeEach
    void setUp() {
        accountInternalApi = mock(AccountInternalApi.class);
        passwordHasher = mock(PasswordHasher.class);
        auditLogInternalApi = mock(AuditLogInternalApi.class);
        changePasswordUseCase = new ChangePasswordUseCase(accountInternalApi, passwordHasher, auditLogInternalApi);
    }

    @Test
    void execute_Success() {
        String email = "test@example.com";
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail(email);
        user.setPasswordHash("hashed_old_password");

        when(accountInternalApi.findUserByEmail(email)).thenReturn(Optional.of(user));
        when(passwordHasher.matches("oldPassword", "hashed_old_password")).thenReturn(true);
        when(passwordHasher.hash("NewPassword123!")).thenReturn("hashed_new_password");

        changePasswordUseCase.execute(email, "oldPassword", "NewPassword123!");

        assertEquals("hashed_new_password", user.getPasswordHash());
        verify(accountInternalApi, times(1)).saveUser(user);
        verify(accountInternalApi, times(1)).revokeAllRefreshTokensByUserId(userId);
        verify(auditLogInternalApi, times(1)).recordAuditLog(
                eq(userId),
                eq("PASSWORD_CHANGED"),
                eq("USER"),
                eq(userId),
                eq("User changed their own password")
        );
    }

    @Test
    void execute_WrongCurrentPassword_ThrowsException() {
        String email = "test@example.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("hashed_old_password");

        when(accountInternalApi.findUserByEmail(email)).thenReturn(Optional.of(user));
        when(passwordHasher.matches("wrongPassword", "hashed_old_password")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> 
            changePasswordUseCase.execute(email, "wrongPassword", "NewPassword123!")
        );

        verify(accountInternalApi, never()).saveUser(any());
        verify(accountInternalApi, never()).revokeAllRefreshTokensByUserId(any());
        verify(auditLogInternalApi, never()).recordAuditLog(any(), any(), any(), any(), any());
    }

    @Test
    void execute_NewPasswordSameAsOld_ThrowsException() {
        String email = "test@example.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("hashed_old_password");

        when(accountInternalApi.findUserByEmail(email)).thenReturn(Optional.of(user));
        when(passwordHasher.matches("oldPassword", "hashed_old_password")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> 
            changePasswordUseCase.execute(email, "oldPassword", "oldPassword")
        );

        verify(accountInternalApi, never()).saveUser(any());
        verify(accountInternalApi, never()).revokeAllRefreshTokensByUserId(any());
        verify(auditLogInternalApi, never()).recordAuditLog(any(), any(), any(), any(), any());
    }
}
