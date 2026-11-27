package com.danasea.backend.security.authentication.application.usecases;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.RefreshToken;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.security.authentication.application.ports.TokenProvider;
import com.danasea.backend.security.authentication.application.results.LoginResult;
import com.danasea.backend.security.authentication.domain.exceptions.InvalidCredentialsException;
import com.danasea.backend.security.authentication.domain.models.Authentication;
import com.danasea.backend.security.authentication.infrastructure.security.HashUtils;
import com.danasea.backend.security.authentication.infrastructure.security.JwtProperties;

class RefreshTokenUseCaseTest {

    private AccountInternalApi accountInternalApi;
    private TokenProvider tokenProvider;
    private JwtProperties jwtProperties;
    private RefreshTokenUseCase refreshTokenUseCase;

    @BeforeEach
    void setUp() {
        accountInternalApi = mock(AccountInternalApi.class);
        tokenProvider = mock(TokenProvider.class);
        jwtProperties = new JwtProperties("test-secret-with-at-least-32-bytes", 15, 7);
        refreshTokenUseCase = new RefreshTokenUseCase(accountInternalApi, tokenProvider, jwtProperties);
    }

    @Test
    void shouldRefreshTokenSuccessfully() {
        String rawToken = "raw-refresh-token";
        String tokenHash = HashUtils.sha256(rawToken);
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();

        RefreshToken refreshToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .expiresAt(OffsetDateTime.now().plusDays(1))
                .build();

        User user = new User();
        user.setId(userId);
        user.setEmail("test@example.com");
        user.setRole(Role.CUSTOMER);
        user.setIsLocked(false);
        user.setIsEmailVerified(true);

        when(accountInternalApi.findRefreshTokenUserIdByHash(tokenHash)).thenReturn(Optional.of(refreshToken.getUserId()));
        when(accountInternalApi.findRefreshTokenByHashForUpdate(tokenHash)).thenReturn(Optional.of(refreshToken));
        when(accountInternalApi.findUserByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(tokenProvider.generateAccessToken(any(Authentication.class))).thenReturn("new-access");
        when(tokenProvider.generateRefreshToken(any(Authentication.class))).thenReturn("new-refresh");

        LoginResult result = refreshTokenUseCase.execute(rawToken);

        assertNotNull(result);
        assertEquals("new-access", result.accessToken());
        assertEquals("new-refresh", result.refreshToken());

        // Verify the old token was revoked
        assertNotNull(refreshToken.getRevokedAt());
        verify(accountInternalApi).saveRefreshToken(refreshToken);

        // Verify a new token was saved
        verify(accountInternalApi, times(2)).saveRefreshToken(any(RefreshToken.class));
        assertNotNull(refreshToken.getReplacedById());
    }

    @Test
    void shouldRevokeFamilyIfTokenReused() {
        String rawToken = "raw-refresh-token";
        String tokenHash = HashUtils.sha256(rawToken);
        UUID familyId = UUID.randomUUID();

        RefreshToken refreshToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tokenHash(tokenHash)
                .familyId(familyId)
                .expiresAt(OffsetDateTime.now().plusDays(1))
                .revokedAt(OffsetDateTime.now().minusHours(1)) // Already revoked
                .build();

        when(accountInternalApi.findRefreshTokenUserIdByHash(tokenHash)).thenReturn(Optional.of(refreshToken.getUserId()));
        when(accountInternalApi.findRefreshTokenByHashForUpdate(tokenHash)).thenReturn(Optional.of(refreshToken));

        assertThrows(InvalidCredentialsException.class, () -> refreshTokenUseCase.execute(rawToken));
        verify(accountInternalApi).revokeRefreshTokenFamily(familyId);
    }
    @Test
    void shouldThrowIfUserEmailNotVerified() {
        String rawToken = "raw-refresh-token";
        String tokenHash = HashUtils.sha256(rawToken);
        UUID userId = UUID.randomUUID();

        RefreshToken refreshToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash(tokenHash)
                .expiresAt(OffsetDateTime.now().plusDays(1))
                .build();

        User user = new User();
        user.setId(userId);
        user.setIsLocked(false);
        user.setIsEmailVerified(false);

        when(accountInternalApi.findRefreshTokenUserIdByHash(tokenHash)).thenReturn(Optional.of(refreshToken.getUserId()));
        when(accountInternalApi.findRefreshTokenByHashForUpdate(tokenHash)).thenReturn(Optional.of(refreshToken));
        when(accountInternalApi.findUserByIdForUpdate(userId)).thenReturn(Optional.of(user));

        assertThrows(InvalidCredentialsException.class, () -> refreshTokenUseCase.execute(rawToken));
    }
}
