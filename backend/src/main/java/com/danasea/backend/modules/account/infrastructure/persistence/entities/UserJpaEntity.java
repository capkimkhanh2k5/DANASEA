package com.danasea.backend.modules.account.infrastructure.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;

import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "users")
public class UserJpaEntity extends BaseJpaEntity {

    private String email;

    private String phone;

    private String passwordHash;

    private String fullName;

    @Enumerated(EnumType.STRING)
    private Role role;

    private String avatarUrl;

    private Boolean isEmailVerified;

    private Boolean isLocked;

    @Column(name = "session_version", nullable = false, columnDefinition = "bigint default 0")
    private long sessionVersion = 0L;

    @Column(nullable = false, length = 2)
    private String locale = "vi";

}
