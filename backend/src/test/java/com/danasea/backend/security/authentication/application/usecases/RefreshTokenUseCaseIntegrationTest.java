package com.danasea.backend.security.authentication.application.usecases;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.RefreshToken;
import com.danasea.backend.security.authentication.domain.exceptions.InvalidCredentialsException;
import com.danasea.backend.security.authentication.infrastructure.security.HashUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.danasea.backend.configs.AbstractExternalInfrastructureIntegrationTest;

@SpringBootTest
@ActiveProfiles("test")
class RefreshTokenUseCaseIntegrationTest extends AbstractExternalInfrastructureIntegrationTest {

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private LettuceBasedProxyManager<byte[]> proxyManager;

    @Autowired
    private RefreshTokenUseCase refreshTokenUseCase;

    @Autowired
    private AccountInternalApi accountInternalApi;

    @Test
    void execute_WhenTokenAlreadyRevoked_ShouldRevokeFamilyAndCommitDespiteException() {
        // Arrange
        UUID familyId = UUID.randomUUID();
        String rawToken = "raw-refresh-token-" + UUID.randomUUID();
        String tokenHash = HashUtils.sha256(rawToken);
        UUID userId = UUID.randomUUID();

        // Create the already revoked token that the user tries to use
        RefreshToken revokedToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .expiresAt(OffsetDateTime.now().plusDays(1))
                .revokedAt(OffsetDateTime.now().minusHours(1)) // Already revoked
                .build();
        accountInternalApi.saveRefreshToken(revokedToken);

        // Create a valid token in the same family to verify it gets revoked
        RefreshToken validTokenInFamily = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash(HashUtils.sha256("another-token"))
                .familyId(familyId)
                .expiresAt(OffsetDateTime.now().plusDays(1))
                .build();
        accountInternalApi.saveRefreshToken(validTokenInFamily);

        // Act & Assert
        assertThatThrownBy(() -> refreshTokenUseCase.execute(rawToken))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid refresh token");

        // The transaction should NOT be rolled back due to @Transactional(noRollbackFor = InvalidCredentialsException.class)
        // Check if the other token in the family got revoked
        Optional<RefreshToken> checkedToken = accountInternalApi.findRefreshTokenByHash(validTokenInFamily.getTokenHash());
        assertThat(checkedToken).isPresent();
        assertThat(checkedToken.get().getRevokedAt()).isNotNull(); // It should be revoked now
    }
}
