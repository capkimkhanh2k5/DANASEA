package com.danasea.backend.security.authentication.application.usecases;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.RefreshToken;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.audit.application.ports.AuditLogPort;
import com.danasea.backend.security.authentication.application.ports.PasswordHasher;
import com.danasea.backend.security.authentication.application.ports.TokenProvider;
import com.danasea.backend.security.authentication.application.results.LoginResult;
import com.danasea.backend.security.authentication.domain.exceptions.EmailNotVerifiedException;
import com.danasea.backend.security.authentication.domain.exceptions.InvalidCredentialsException;
import com.danasea.backend.security.authentication.domain.models.Authentication;
import com.danasea.backend.security.authentication.infrastructure.security.JwtProperties;

class LoginUseCaseTest {

    private AccountInternalApi accountInternalApi;
    private PasswordHasher passwordHasher;
    private TokenProvider tokenProvider;
    private JwtProperties jwtProperties;

    private LoginUseCase loginUseCase;

    @BeforeEach
    void setUp() {
        accountInternalApi = mock(AccountInternalApi.class);
        passwordHasher = mock(PasswordHasher.class);
        tokenProvider = mock(TokenProvider.class);
        jwtProperties = new JwtProperties("secretsecretsecretsecretsecretsecret", 15, 7);

        loginUseCase = new LoginUseCase(
                accountInternalApi,
                passwordHasher,
                tokenProvider,
                jwtProperties
        );
    }

    @Test
    void shouldLoginSuccessfully() {
        String email = "TEST@example.com";
        String normalizedEmail = "test@example.com";
        String password = "Password1!";
        String hashedPassword = "hashedPassword1!";

        Authentication user = new Authentication(UUID.randomUUID(), normalizedEmail, hashedPassword, "CUSTOMER", true, true);
        
        when(accountInternalApi.findUserByEmailForUpdate(normalizedEmail)).thenReturn(Optional.of(toUser(user)));
        when(passwordHasher.matches(password, hashedPassword)).thenReturn(true);
        when(tokenProvider.generateAccessToken(user)).thenReturn("access-token");
        when(tokenProvider.generateRefreshToken(user)).thenReturn("refresh-token");

        LoginResult result = loginUseCase.execute(email, password);

        assertNotNull(result);
        assertEquals("access-token", result.accessToken());
        assertEquals("refresh-token", result.refreshToken());
        
        verify(accountInternalApi).saveRefreshToken(any(RefreshToken.class));
    }

    @Test
    void shouldThrowWhenUserNotFound() {
        when(accountInternalApi.findUserByEmailForUpdate(anyString())).thenReturn(Optional.empty());
        assertThrows(InvalidCredentialsException.class, () -> loginUseCase.execute("test@example.com", "Password1!"));
    }

    @Test
    void shouldThrowWhenPasswordMismatches() {
        Authentication user = new Authentication(UUID.randomUUID(), "test@example.com", "hash", "CUSTOMER", true, true);
        when(accountInternalApi.findUserByEmailForUpdate("test@example.com")).thenReturn(Optional.of(toUser(user)));
        when(passwordHasher.matches("wrong", "hash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> loginUseCase.execute("test@example.com", "wrong"));
    }

    @Test
    void shouldThrowWhenUserIsLocked() {
        Authentication user = new Authentication(UUID.randomUUID(), "test@example.com", "hash", "CUSTOMER", false, true);
        when(accountInternalApi.findUserByEmailForUpdate("test@example.com")).thenReturn(Optional.of(toUser(user)));

        assertThrows(InvalidCredentialsException.class, () -> loginUseCase.execute("test@example.com", "password"));
    }

    @Test
    void shouldThrowWhenEmailIsNotVerified() {
        Authentication user = new Authentication(UUID.randomUUID(), "test@example.com", "hash", "CUSTOMER", true, false);
        when(accountInternalApi.findUserByEmailForUpdate("test@example.com")).thenReturn(Optional.of(toUser(user)));

        assertThrows(InvalidCredentialsException.class, () -> loginUseCase.execute("test@example.com", "password"));
    }

    @Test
    void shouldThrowEmailNotVerifiedExceptionWhenCredentialsMatchButEmailNotVerified() {
        Authentication user = new Authentication(UUID.randomUUID(), "test@example.com", "hash", "CUSTOMER", true, false);
        when(accountInternalApi.findUserByEmailForUpdate("test@example.com")).thenReturn(Optional.of(toUser(user)));
        when(passwordHasher.matches("correct-password", "hash")).thenReturn(true);
        when(tokenProvider.generateAccessToken(any(Authentication.class))).thenReturn("pending-token");

        EmailNotVerifiedException ex = assertThrows(EmailNotVerifiedException.class,
                () -> loginUseCase.execute("test@example.com", "correct-password"));
        assertEquals("test@example.com", ex.getEmail());
        assertEquals("pending-token", ex.getAccessToken());
    }
    private User toUser(Authentication auth) {
        var user = new User();
        user.setId(auth.id());user.setEmail(auth.email());user.setPasswordHash(auth.passwordHash());
        user.setRole(Role.valueOf(auth.role()));
        user.setIsLocked(!auth.enabled());user.setIsEmailVerified(auth.emailVerified());
        user.setLocale(auth.locale());user.setSessionVersion(auth.sessionVersion());
        return user;
    }
}
