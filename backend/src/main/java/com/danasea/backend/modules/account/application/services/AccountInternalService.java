package com.danasea.backend.modules.account.application.services;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.Predicate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.PasswordResetToken;
import com.danasea.backend.modules.account.domain.models.RefreshToken;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.account.infrastructure.mappers.PasswordResetTokenMapper;
import com.danasea.backend.modules.account.infrastructure.mappers.RefreshTokenMapper;
import com.danasea.backend.modules.account.infrastructure.mappers.UserMapper;
import com.danasea.backend.modules.account.infrastructure.persistence.entities.PasswordResetTokenJpaEntity;
import com.danasea.backend.modules.account.infrastructure.persistence.entities.RefreshTokenJpaEntity;
import com.danasea.backend.modules.account.infrastructure.persistence.entities.UserJpaEntity;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaPasswordResetTokenRepository;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaRefreshTokenRepository;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;

@Service
@Transactional(readOnly = true)
public class AccountInternalService implements AccountInternalApi {

    private final JpaUserRepository userRepository;
    private final JpaRefreshTokenRepository refreshTokenRepository;
    private final JpaPasswordResetTokenRepository passwordResetTokenRepository;
    private final UserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final PasswordResetTokenMapper passwordResetTokenMapper;
    private final CacheManager cacheManager;

    @Autowired
    public AccountInternalService(
            JpaUserRepository userRepository,
            JpaRefreshTokenRepository refreshTokenRepository,
            JpaPasswordResetTokenRepository passwordResetTokenRepository,
            UserMapper userMapper,
            RefreshTokenMapper refreshTokenMapper,
            PasswordResetTokenMapper passwordResetTokenMapper,
            CacheManager cacheManager) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.userMapper = userMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.passwordResetTokenMapper = passwordResetTokenMapper;
        this.cacheManager = cacheManager;
    }

    public AccountInternalService(
            JpaUserRepository userRepository,
            JpaRefreshTokenRepository refreshTokenRepository,
            UserMapper userMapper,
            RefreshTokenMapper refreshTokenMapper,
            CacheManager cacheManager) {
        this(userRepository, refreshTokenRepository, null, userMapper, refreshTokenMapper, new PasswordResetTokenMapper(), cacheManager);
    }

    private String normalizeEmail(String email) {
        return email != null ? email.trim().toLowerCase(Locale.ROOT) : null;
    }

    @Override
    @Cacheable(value = "usersByEmail", key = "#email?.trim()?.toLowerCase()", condition = "#email != null && !#email.isBlank()", unless = "#result == null")
    public Optional<User> findUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(normalizeEmail(email)).map(userMapper::toDomain);
    }

    @Override
    @Cacheable(value = "usersById", key = "#id", condition = "#id != null", unless = "#result == null")
    public Optional<User> findUserById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return userRepository.findById(id).map(userMapper::toDomain);
    }

    @Override
    public Map<UUID, User> findUsersByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return new java.util.HashMap<>();
        }
        Set<UUID> nonNullIds = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (nonNullIds.isEmpty()) {
            return new java.util.HashMap<>();
        }
        List<UserJpaEntity> entities = userRepository.findAllById(nonNullIds);
        Map<UUID, User> map = new java.util.HashMap<>();
        for (UserJpaEntity entity : entities) {
            User user = userMapper.toDomain(entity);
            if (user != null && user.getId() != null) {
                map.put(user.getId(), user);
            }
        }
        return map;
    }

    @Override
    public boolean existsByEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return userRepository.existsByEmail(normalizeEmail(email));
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "usersByEmail", key = "#user?.email?.trim()?.toLowerCase()", condition = "#user != null && #user.email != null"),
        @CacheEvict(value = "usersById", key = "#user?.id", condition = "#user != null && #user.id != null"),
        @CacheEvict(value = "usersByEmail", key = "#result?.email?.trim()?.toLowerCase()", condition = "#result != null && #result.email != null"),
        @CacheEvict(value = "usersById", key = "#result?.id", condition = "#result != null && #result.id != null")
    })
    public User saveUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (user.getEmail() != null) {
            user.setEmail(normalizeEmail(user.getEmail()));
        }

        UserJpaEntity current = user.getId() != null
                ? userRepository.findByIdForUpdate(user.getId()).orElse(null) : null;
        String previousEmail = current != null ? normalizeEmail(current.getEmail()) : null;
        UserJpaEntity entity = userMapper.toEntity(user);
        if (current != null) {
            entity.setSessionVersion(Math.max(current.getSessionVersion(), user.getSessionVersion()));
            if (user.getSessionVersion() < current.getSessionVersion()) {
                entity.setPasswordHash(current.getPasswordHash());
            }
        }
        UserJpaEntity saved = userRepository.saveAndFlush(entity);

        if (cacheManager != null) {
            Cache byEmail = cacheManager.getCache("usersByEmail");
            if (byEmail != null) {
                if (user.getEmail() != null) {
                    byEmail.evict(normalizeEmail(user.getEmail()));
                }
                if (saved.getEmail() != null) {
                    byEmail.evict(normalizeEmail(saved.getEmail()));
                }
                if (previousEmail != null && !previousEmail.equals(user.getEmail())) {
                    byEmail.evict(previousEmail);
                }
            }
            Cache byId = cacheManager.getCache("usersById");
            if (byId != null) {
                if (user.getId() != null) {
                    byId.evict(user.getId());
                }
                if (saved.getId() != null) {
                    byId.evict(saved.getId());
                }
            }
        }

        return userMapper.toDomain(saved);
    }

    @Override
    @Transactional
    public RefreshToken saveRefreshToken(RefreshToken token) {
        RefreshTokenJpaEntity entity = refreshTokenMapper.toEntity(token);
        RefreshTokenJpaEntity saved = refreshTokenRepository.save(entity);
        return refreshTokenMapper.toDomain(saved);
    }

    @Override
    public Optional<RefreshToken> findRefreshTokenByHash(String tokenHash) {
        return refreshTokenRepository.findByTokenHash(tokenHash)
                .map(refreshTokenMapper::toDomain);
    }

    @Override
    public Optional<UUID> findRefreshTokenUserIdByHash(String tokenHash) {
        return refreshTokenRepository.findUserIdByTokenHash(tokenHash);
    }

    @Override
    public Optional<RefreshToken> findRefreshTokenByHashForUpdate(String tokenHash) {
        return refreshTokenRepository.findForUpdateByTokenHash(tokenHash)
                .map(refreshTokenMapper::toDomain);
    }

    @Override
    @Transactional
    public void revokeRefreshTokenFamily(UUID familyId) {
        List<RefreshTokenJpaEntity> tokens = refreshTokenRepository.findAllByFamilyId(familyId);
        OffsetDateTime now = OffsetDateTime.now();
        tokens.forEach(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(now);
            }
        });
        refreshTokenRepository.saveAll(tokens);
    }

    @Override
    @Transactional
    public void revokeAllRefreshTokensByUserId(UUID userId) {
        List<RefreshTokenJpaEntity> tokens = refreshTokenRepository.findAllByUserId(userId);
        OffsetDateTime now = OffsetDateTime.now();
        boolean hasUnrevoked = false;
        for (RefreshTokenJpaEntity token : tokens) {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(now);
                hasUnrevoked = true;
            }
        }
        if (hasUnrevoked) {
            refreshTokenRepository.saveAll(tokens);
        }
    }

    @Override
    @Transactional
    public void revokeAllTokensByUserId(UUID userId) {
        revokeAllRefreshTokensByUserId(userId);
    }

    @Override
    public Page<User> findUsers(Pageable pageable, Role role, Boolean isLocked, String search) {
        Specification<UserJpaEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (isLocked != null) {
                predicates.add(cb.equal(root.get("isLocked"), isLocked));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Predicate emailMatch = cb.like(cb.lower(root.get("email")), pattern);
                Predicate nameMatch = cb.like(cb.lower(root.get("fullName")), pattern);
                predicates.add(cb.or(emailMatch, nameMatch));
            }
            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<UserJpaEntity> entities = userRepository.findAll(spec, pageable);
        return entities.map(userMapper::toDomain);
    }

    @Override
    public Optional<User> findUserByEmailUncached(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(normalizeEmail(email)).map(userMapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<User> findUserByEmailForUpdate(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmailForUpdate(normalizeEmail(email)).map(userMapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<User> findUserByIdForUpdate(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findByIdForUpdate(userId).map(userMapper::toDomain);
    }

    @Override
    @Transactional
    public void recordFailedPasswordResetAttempt(UUID tokenId) {
        PasswordResetTokenJpaEntity token = passwordResetTokenRepository.findById(tokenId).orElseThrow();
        token.setFailedAttempts(Math.min(5, token.getFailedAttempts() + 1));
        if (token.getFailedAttempts() >= 5) {
            token.setUsedAt(OffsetDateTime.now());
        }
        passwordResetTokenRepository.save(token);
    }

    @Override
    @Transactional
    public void createPasswordResetToken(UUID userId, String tokenHash, OffsetDateTime expiresAt) {
        PasswordResetTokenJpaEntity entity = new PasswordResetTokenJpaEntity();
        entity.setUserId(userId);
        entity.setTokenHash(tokenHash);
        entity.setExpiresAt(expiresAt);
        entity.setUsedAt(null);
        passwordResetTokenRepository.save(entity);
    }

    @Override
    public Optional<PasswordResetToken> findLatestActivePasswordResetToken(UUID userId) {
        return passwordResetTokenRepository
                .findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(userId)
                .filter(token -> token.getExpiresAt() != null && token.getExpiresAt().isAfter(OffsetDateTime.now()))
                .map(passwordResetTokenMapper::toDomain);
    }

    @Override
    @Transactional
    public void invalidatePasswordResetTokens(UUID userId) {
        List<PasswordResetTokenJpaEntity> tokens = passwordResetTokenRepository.findAllByUserIdAndUsedAtIsNull(userId);
        OffsetDateTime now = OffsetDateTime.now();
        tokens.forEach(token -> token.setUsedAt(now));
        passwordResetTokenRepository.saveAll(tokens);
    }

    @Override
    @Transactional
    public void markPasswordResetTokenUsed(UUID tokenId) {
        passwordResetTokenRepository.findById(tokenId).ifPresent(token -> {
            token.setUsedAt(OffsetDateTime.now());
            passwordResetTokenRepository.save(token);
        });
    }

}
