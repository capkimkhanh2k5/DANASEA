package com.danasea.backend.security.authentication.application.usecases;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.RefreshToken;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.security.authentication.application.ports.GoogleTokenVerifierPort;
import com.danasea.backend.security.authentication.application.ports.TokenProvider;
import com.danasea.backend.security.authentication.application.results.LoginResult;
import com.danasea.backend.security.authentication.domain.exceptions.InvalidCredentialsException;
import com.danasea.backend.security.authentication.domain.models.Authentication;
import com.danasea.backend.security.authentication.domain.models.GoogleUserInfo;
import com.danasea.backend.security.authentication.infrastructure.security.JwtProperties;

class GoogleOAuth2LoginUseCaseTest {

    private GoogleTokenVerifierPort tokenVerifier;
    private AccountInternalApi accountInternalApi;
    private TokenProvider tokenProvider;
    private JwtProperties jwtProperties;

    private GoogleOAuth2LoginUseCase googleOAuth2LoginUseCase;

    @BeforeEach
    void setUp() {
        tokenVerifier = mock(GoogleTokenVerifierPort.class);
        accountInternalApi = mock(AccountInternalApi.class);
        tokenProvider = mock(TokenProvider.class);
        jwtProperties = new JwtProperties("testsecretkeytestsecretkeytestsecretkey", 15, 7);

        googleOAuth2LoginUseCase = new GoogleOAuth2LoginUseCase(
                tokenVerifier,
                accountInternalApi,
                tokenProvider,
                jwtProperties
        );
    }

    @Test
    void shouldLoginExistingUserSuccessfully() {
        String idToken = "valid-google-id-token";
        GoogleUserInfo googleUser = new GoogleUserInfo(
                "google-123",
                "USER@EXAMPLE.COM",
                true,
                "User Example",
                "https://example.com/avatar.jpg"
        );

        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("user@example.com");
        existingUser.setFullName("User Example");
        existingUser.setAvatarUrl("https://example.com/avatar.jpg");
        existingUser.setRole(Role.CUSTOMER);
        existingUser.setIsLocked(false);
        existingUser.setIsEmailVerified(true);
        existingUser.setLocale("vi");

        when(tokenVerifier.verify(idToken)).thenReturn(googleUser);
        when(accountInternalApi.findUserByEmailForUpdate("user@example.com")).thenReturn(Optional.of(existingUser));
        when(tokenProvider.generateAccessToken(any(Authentication.class))).thenReturn("access-token-123");
        when(tokenProvider.generateRefreshToken(any(Authentication.class))).thenReturn("refresh-token-456");

        LoginResult result = googleOAuth2LoginUseCase.execute(idToken);

        assertNotNull(result);
        assertEquals("access-token-123", result.accessToken());
        assertEquals("refresh-token-456", result.refreshToken());
        assertEquals("user@example.com", result.email());
        assertEquals("CUSTOMER", result.role());

        verify(accountInternalApi).saveRefreshToken(any(RefreshToken.class));
        verify(accountInternalApi, never()).saveUser(any(User.class));
    }

    @Test
    void shouldAutoRegisterNewUserSuccessfully() {
        String idToken = "new-user-id-token";
        GoogleUserInfo googleUser = new GoogleUserInfo(
                "google-456",
                "newuser@example.com",
                true,
                "New User",
                "https://example.com/new-avatar.jpg"
        );

        when(tokenVerifier.verify(idToken)).thenReturn(googleUser);
        when(accountInternalApi.findUserByEmailForUpdate("newuser@example.com")).thenReturn(Optional.empty());

        UUID generatedId = UUID.randomUUID();
        when(accountInternalApi.saveUser(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(generatedId);
            return u;
        });

        when(tokenProvider.generateAccessToken(any(Authentication.class))).thenReturn("new-access-token");
        when(tokenProvider.generateRefreshToken(any(Authentication.class))).thenReturn("new-refresh-token");

        LoginResult result = googleOAuth2LoginUseCase.execute(idToken);

        assertNotNull(result);
        assertEquals("new-access-token", result.accessToken());
        assertEquals("new-refresh-token", result.refreshToken());
        assertEquals("newuser@example.com", result.email());
        assertEquals("CUSTOMER", result.role());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(accountInternalApi).saveUser(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("newuser@example.com", savedUser.getEmail());
        assertEquals("New User", savedUser.getFullName());
        assertEquals("https://example.com/new-avatar.jpg", savedUser.getAvatarUrl());
        assertEquals(Role.CUSTOMER, savedUser.getRole());
        assertTrue(savedUser.getIsEmailVerified());
        assertFalse(savedUser.getIsLocked());

        verify(accountInternalApi).saveRefreshToken(any(RefreshToken.class));
    }

    @Test
    void shouldThrowWhenIdTokenIsBlankOrNull() {
        assertThrows(InvalidCredentialsException.class, () -> googleOAuth2LoginUseCase.execute(null));
        assertThrows(InvalidCredentialsException.class, () -> googleOAuth2LoginUseCase.execute("   "));
        verifyNoInteractions(tokenVerifier);
    }

    @Test
    void shouldThrowWhenAccountIsLocked() {
        String idToken = "locked-user-token";
        GoogleUserInfo googleUser = new GoogleUserInfo(
                "google-789",
                "locked@example.com",
                true,
                "Locked User",
                null
        );

        User lockedUser = new User();
        lockedUser.setId(UUID.randomUUID());
        lockedUser.setEmail("locked@example.com");
        lockedUser.setIsLocked(true);

        when(tokenVerifier.verify(idToken)).thenReturn(googleUser);
        when(accountInternalApi.findUserByEmailForUpdate("locked@example.com")).thenReturn(Optional.of(lockedUser));

        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                () -> googleOAuth2LoginUseCase.execute(idToken));
        assertEquals("Account is locked", ex.getMessage());

        verify(tokenProvider, never()).generateAccessToken(any());
        verify(accountInternalApi, never()).saveRefreshToken(any());
    }

    @Test
    void shouldUpdateAvatarAndNameIfMissingForExistingUser() {
        String idToken = "existing-user-token";
        GoogleUserInfo googleUser = new GoogleUserInfo(
                "google-999",
                "existing@example.com",
                true,
                "Updated Name",
                "https://example.com/updated-avatar.png"
        );

        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("existing@example.com");
        existingUser.setFullName(null);
        existingUser.setAvatarUrl(null);
        existingUser.setIsEmailVerified(false);
        existingUser.setIsLocked(false);
        existingUser.setRole(Role.CUSTOMER);

        when(tokenVerifier.verify(idToken)).thenReturn(googleUser);
        when(accountInternalApi.findUserByEmailForUpdate("existing@example.com")).thenReturn(Optional.of(existingUser));
        when(accountInternalApi.saveUser(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenProvider.generateAccessToken(any(Authentication.class))).thenReturn("access");
        when(tokenProvider.generateRefreshToken(any(Authentication.class))).thenReturn("refresh");

        LoginResult result = googleOAuth2LoginUseCase.execute(idToken);
        assertNotNull(result);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(accountInternalApi).saveUser(captor.capture());
        User updated = captor.getValue();
        assertEquals("Updated Name", updated.getFullName());
        assertEquals("https://example.com/updated-avatar.png", updated.getAvatarUrl());
        assertTrue(updated.getIsEmailVerified());
    }
}
