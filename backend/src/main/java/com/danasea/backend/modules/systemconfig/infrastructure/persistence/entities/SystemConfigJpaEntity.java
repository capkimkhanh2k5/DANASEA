package com.danasea.backend.modules.systemconfig.infrastructure.persistence.entities;

import java.util.UUID;

import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "system_configs")
public class SystemConfigJpaEntity extends BaseJpaEntity {

    @Column(name = "\"key\"")
    private String key;

    @Column(name = "\"value\"")
    private String value;

    private String description;

    private UUID updatedBy;

}
