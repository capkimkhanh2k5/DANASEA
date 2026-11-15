package com.danasea.backend.modules.account.application.api;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.danasea.backend.modules.account.domain.models.PasswordResetToken;
import com.danasea.backend.modules.account.domain.models.RefreshToken;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.domain.models.User;

public interface AccountInternalApi {
    Optional<User> findUserByEmail(String email);

    Optional<User> findUserById(UUID id);

    Map<UUID, User> findUsersByIds(Collection<UUID> ids);

    boolean existsByEmail(String email);

    User saveUser(User user);

    RefreshToken saveRefreshToken(RefreshToken token);

    Optional<RefreshToken> findRefreshTokenByHash(String tokenHash);

    Optional<UUID> findRefreshTokenUserIdByHash(String tokenHash);

    Optional<RefreshToken> findRefreshTokenByHashForUpdate(String tokenHash);

    void revokeRefreshTokenFamily(UUID familyId);

    void revokeAllTokensByUserId(UUID userId);

    void revokeAllRefreshTokensByUserId(UUID userId);

    Page<User> findUsers(Pageable pageable, Role role, Boolean isLocked, String search);

    Optional<User> findUserByEmailUncached(String email);

    Optional<User> findUserByEmailForUpdate(String email);

    Optional<User> findUserByIdForUpdate(UUID userId);

    void recordFailedPasswordResetAttempt(UUID tokenId);

    void createPasswordResetToken(UUID userId, String tokenHash, OffsetDateTime expiresAt);

    Optional<PasswordResetToken> findLatestActivePasswordResetToken(UUID userId);

    void invalidatePasswordResetTokens(UUID userId);

    void markPasswordResetTokenUsed(UUID tokenId);
}
