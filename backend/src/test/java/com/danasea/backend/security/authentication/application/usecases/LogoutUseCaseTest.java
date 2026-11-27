package com.danasea.backend.security.authentication.application.usecases;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.RefreshToken;
import com.danasea.backend.security.authentication.infrastructure.security.HashUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LogoutUseCaseTest {

    private AccountInternalApi accountInternalApi;
    private LogoutUseCase logoutUseCase;

    @BeforeEach
    void setUp() {
        accountInternalApi = mock(AccountInternalApi.class);
        logoutUseCase = new LogoutUseCase(accountInternalApi);
    }

    @Test
    void shouldRevokeTokenSuccessfully() {
        String rawToken = "raw-token";
        String tokenHash = HashUtils.sha256(rawToken);

        RefreshToken token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .tokenHash(tokenHash)
                .build();

        when(accountInternalApi.findRefreshTokenByHash(tokenHash)).thenReturn(Optional.of(token));

        logoutUseCase.execute(rawToken);

        assertNotNull(token.getRevokedAt());
        verify(accountInternalApi).saveRefreshToken(token);
    }

    @Test
    void shouldNotRevokeAlreadyRevokedToken() {
        String rawToken = "raw-token";
        String tokenHash = HashUtils.sha256(rawToken);

        RefreshToken token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .tokenHash(tokenHash)
                .revokedAt(OffsetDateTime.now())
                .build();

        when(accountInternalApi.findRefreshTokenByHash(tokenHash)).thenReturn(Optional.of(token));

        logoutUseCase.execute(rawToken);

        verify(accountInternalApi, never()).saveRefreshToken(any());
    }

    @Test
    void shouldDoNothingWhenTokenIsNull() {
        logoutUseCase.execute(null);
        verify(accountInternalApi, never()).findRefreshTokenByHash(anyString());
    }
}
