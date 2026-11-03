package com.danasea.backend.security.authentication.application.usecases;

import java.util.Locale;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.security.authentication.application.ports.PasswordHasher;
import com.danasea.backend.security.authentication.application.ports.TokenProvider;
import com.danasea.backend.security.authentication.application.ports.UserAccountPort;
import com.danasea.backend.security.authentication.application.results.LoginResult;
import com.danasea.backend.security.authentication.domain.events.UserRegisteredEvent;
import com.danasea.backend.security.authentication.domain.exceptions.EmailAlreadyUsedException;
import com.danasea.backend.security.authentication.domain.models.Authentication;

import lombok.RequiredArgsConstructor;

import java.util.UUID;
import java.time.OffsetDateTime;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.RefreshToken;
import com.danasea.backend.security.authentication.infrastructure.security.HashUtils;
import com.danasea.backend.security.authentication.infrastructure.security.JwtProperties;
import com.danasea.backend.shared.i18n.SupportedLanguage;

@RequiredArgsConstructor
public class RegisterUseCase {

    private final UserAccountPort userAccountPort;
    private final AccountInternalApi accountInternalApi;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final PasswordHasher passwordHasher;
    private final TokenProvider tokenProvider;
    private final JwtProperties jwtProperties;

    @Transactional
    public LoginResult execute(String email, String password) {
        return execute(email, password, SupportedLanguage.DEFAULT);
    }

    @Transactional
    public LoginResult execute(String email, String password, SupportedLanguage language) {
        String normalizedEmail = email.trim()
                .toLowerCase(Locale.ROOT);

        if (userAccountPort.existsByEmail(normalizedEmail)) {
            var existingOpt = userAccountPort.findByEmail(normalizedEmail);
            if (existingOpt.isPresent() && !existingOpt.get().emailVerified()) {
                Authentication existing = existingOpt.get();
                String hashedPassword = passwordHasher.hash(password);
                Authentication userToUpdate = new Authentication(
                        existing.id(),
                        normalizedEmail,
                        hashedPassword,
                        existing.role(),
                        true,
                        false,
                        (language == null ? SupportedLanguage.DEFAULT : language).code(),
                        existing.sessionVersion()
                );
                Authentication savedUser = userAccountPort.save(userToUpdate);
                String accessToken = tokenProvider.generateAccessToken(savedUser);
                String refreshToken = tokenProvider.generateRefreshToken(savedUser);

                RefreshToken refreshTokenModel = RefreshToken.builder()
                        .id(UUID.randomUUID())
                        .userId(savedUser.id())
                        .tokenHash(HashUtils.sha256(refreshToken))
                        .familyId(UUID.randomUUID())
                        .expiresAt(OffsetDateTime.now().plusDays(jwtProperties.refreshTokenDays()))
                        .build();
                accountInternalApi.saveRefreshToken(refreshTokenModel);

                return new LoginResult(
                        accessToken,
                        refreshToken,
                        savedUser.id(),
                        savedUser.email(),
                        savedUser.role());
            }
            throw new EmailAlreadyUsedException();
        }

        String hashedPassword = passwordHasher.hash(password);

        Authentication user = new Authentication(
                null,
                normalizedEmail,
                hashedPassword,
                "CUSTOMER",
                true, // enabled
                false, // emailVerified
                (language == null ? SupportedLanguage.DEFAULT : language).code()
        );

        Authentication savedUser = userAccountPort.save(user);

        String accessToken = tokenProvider.generateAccessToken(savedUser);
        String refreshToken = tokenProvider.generateRefreshToken(savedUser);

        RefreshToken refreshTokenModel = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(savedUser.id())
                .tokenHash(HashUtils.sha256(refreshToken))
                .familyId(UUID.randomUUID())
                .expiresAt(OffsetDateTime.now().plusDays(jwtProperties.refreshTokenDays()))
                .build();
        accountInternalApi.saveRefreshToken(refreshTokenModel);

        // Publish event
        applicationEventPublisher.publishEvent(new UserRegisteredEvent(
                UUID.randomUUID(),
                savedUser.id(),
                savedUser.email(),
                savedUser.role(),
                OffsetDateTime.now()));

        return new LoginResult(
                accessToken,
                refreshToken,
                savedUser.id(),
                savedUser.email(),
                savedUser.role());
    }
}
