package com.danasea.backend.modules.account.infrastructure.persistence.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;

import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "password_reset_tokens")
public class PasswordResetTokenJpaEntity extends BaseJpaEntity {

    private UUID userId;

    private String tokenHash;

    private OffsetDateTime expiresAt;

    private OffsetDateTime usedAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

}
