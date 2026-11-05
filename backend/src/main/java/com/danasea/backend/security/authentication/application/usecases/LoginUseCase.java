package com.danasea.backend.security.authentication.application.usecases;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.RefreshToken;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.security.authentication.application.ports.PasswordHasher;
import com.danasea.backend.security.authentication.application.ports.TokenProvider;
import com.danasea.backend.security.authentication.application.results.LoginResult;
import com.danasea.backend.security.authentication.domain.exceptions.EmailNotVerifiedException;
import com.danasea.backend.security.authentication.domain.exceptions.InvalidCredentialsException;
import com.danasea.backend.security.authentication.domain.models.Authentication;
import com.danasea.backend.security.authentication.infrastructure.security.HashUtils;
import com.danasea.backend.security.authentication.infrastructure.security.JwtProperties;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LoginUseCase {

    private final AccountInternalApi accountInternalApi;
    private final PasswordHasher passwordHasher;
    private final TokenProvider tokenProvider;
    private final JwtProperties jwtProperties;

    @Transactional
    public LoginResult execute(String email, String password) {
        String normalizedEmail = email.trim()
                .toLowerCase(Locale.ROOT);

        User account = accountInternalApi.findUserByEmailForUpdate(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);
        Authentication user = new Authentication(account.getId(), account.getEmail(), account.getPasswordHash(),
                account.getRole().name(), !Boolean.TRUE.equals(account.getIsLocked()),
                Boolean.TRUE.equals(account.getIsEmailVerified()), account.getLocale(), account.getSessionVersion());

        if (!user.enabled()
                || !passwordHasher.matches(
                        password,
                        user.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        if (!user.emailVerified()) {
            String pendingToken = tokenProvider.generateAccessToken(user);
            throw new EmailNotVerifiedException(user.email(), pendingToken);
        }

        String accessToken = tokenProvider.generateAccessToken(user);
        String refreshToken = tokenProvider.generateRefreshToken(user);

        RefreshToken refreshTokenModel = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(user.id())
                .tokenHash(HashUtils.sha256(refreshToken))
                .familyId(UUID.randomUUID())
                .expiresAt(OffsetDateTime.now().plusDays(jwtProperties.refreshTokenDays()))
                .build();
        accountInternalApi.saveRefreshToken(refreshTokenModel);

        return new LoginResult(
                accessToken,
                refreshToken,
                user.id(),
                user.email(),
                user.role());
    }

}
