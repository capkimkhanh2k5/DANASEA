package com.danasea.backend.security.authentication.application.usecases;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.audit.application.ports.AuditLogPort;
import com.danasea.backend.modules.account.domain.models.RefreshToken;
import org.springframework.context.ApplicationEventPublisher;

import com.danasea.backend.security.authentication.application.ports.PasswordHasher;
import com.danasea.backend.security.authentication.application.ports.TokenProvider;
import com.danasea.backend.security.authentication.application.ports.UserAccountPort;
import com.danasea.backend.security.authentication.application.results.LoginResult;
import com.danasea.backend.security.authentication.domain.events.UserRegisteredEvent;
import com.danasea.backend.security.authentication.domain.exceptions.EmailAlreadyUsedException;
import com.danasea.backend.security.authentication.domain.models.Authentication;
import com.danasea.backend.security.authentication.infrastructure.security.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegisterUseCaseTest {

    private UserAccountPort userAccountPort;
    private AccountInternalApi accountInternalApi;
    private PasswordHasher passwordHasher;
    private TokenProvider tokenProvider;
    private JwtProperties jwtProperties;
    private ApplicationEventPublisher eventPublisher;

    private RegisterUseCase registerUseCase;

    @BeforeEach
    void setUp() {
        userAccountPort = mock(UserAccountPort.class);
        accountInternalApi = mock(AccountInternalApi.class);
        passwordHasher = mock(PasswordHasher.class);
        tokenProvider = mock(TokenProvider.class);
        jwtProperties = new JwtProperties("secretsecretsecretsecretsecretsecret", 15, 7);
        eventPublisher = mock(ApplicationEventPublisher.class);

        registerUseCase = new RegisterUseCase(
                userAccountPort,
                accountInternalApi,
                eventPublisher,
                passwordHasher,
                tokenProvider,
                jwtProperties
        );
    }

    @Test
    void shouldRegisterSuccessfully() {
        String email = "test@example.com";
        String password = "Password1!";
        String hashedPassword = "hashedPassword1!";

        when(userAccountPort.existsByEmail(email)).thenReturn(false);
        when(passwordHasher.hash(password)).thenReturn(hashedPassword);

        Authentication savedUser = new Authentication(UUID.randomUUID(), email, hashedPassword, "CUSTOMER", true, true);
        when(userAccountPort.save(any(Authentication.class))).thenReturn(savedUser);

        when(tokenProvider.generateAccessToken(savedUser)).thenReturn("access-token");
        when(tokenProvider.generateRefreshToken(savedUser)).thenReturn("refresh-token");

        LoginResult result = registerUseCase.execute(email, password);

        assertNotNull(result);
        assertEquals("access-token", result.accessToken());
        assertEquals("refresh-token", result.refreshToken());
        assertEquals(savedUser.id(), result.userId());
        assertEquals(email, result.email());
        assertEquals("CUSTOMER", result.role());

        verify(accountInternalApi).saveRefreshToken(any(RefreshToken.class));
        
        ArgumentCaptor<UserRegisteredEvent> eventCaptor = ArgumentCaptor.forClass(UserRegisteredEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        UserRegisteredEvent publishedEvent = eventCaptor.getValue();
        assertNotNull(publishedEvent.eventId(), "Event ID must be generated for idempotency");
        assertEquals(savedUser.id(), publishedEvent.userId());
        assertEquals(email, publishedEvent.email());
        assertEquals("CUSTOMER", publishedEvent.role());
        assertNotNull(publishedEvent.registeredAt());
    }

    @Test
    void shouldThrowWhenEmailAlreadyUsed() {
        String email = "TEST@example.com";
        String normalizedEmail = "test@example.com";
        when(userAccountPort.existsByEmail(normalizedEmail)).thenReturn(true);

        assertThrows(EmailAlreadyUsedException.class, () -> registerUseCase.execute(email, "Password1!"));
    }

    @Test
    void shouldAllowReRegistrationWhenAccountExistsButNotVerified() {
        String email = "unverified@example.com";
        String normalizedEmail = "unverified@example.com";
        UUID userId = UUID.randomUUID();
        Authentication existing = new Authentication(userId, normalizedEmail, "old-hash", "CUSTOMER", true, false);

        when(userAccountPort.existsByEmail(normalizedEmail)).thenReturn(true);
        when(userAccountPort.findByEmail(normalizedEmail)).thenReturn(java.util.Optional.of(existing));
        when(passwordHasher.hash("NewPassword1!")).thenReturn("new-hash");

        Authentication updated = new Authentication(userId, normalizedEmail, "new-hash", "CUSTOMER", true, false);
        when(userAccountPort.save(any(Authentication.class))).thenReturn(updated);
        when(tokenProvider.generateAccessToken(updated)).thenReturn("new-access-token");
        when(tokenProvider.generateRefreshToken(updated)).thenReturn("new-refresh-token");

        LoginResult result = registerUseCase.execute(email, "NewPassword1!");

        assertNotNull(result);
        assertEquals("new-access-token", result.accessToken());
        assertEquals("new-refresh-token", result.refreshToken());
        assertEquals(userId, result.userId());
        assertEquals(email, result.email());
        verify(accountInternalApi).saveRefreshToken(any(RefreshToken.class));
    }
}
