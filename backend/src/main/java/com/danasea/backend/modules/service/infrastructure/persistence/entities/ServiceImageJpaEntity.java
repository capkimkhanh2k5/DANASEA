package com.danasea.backend.modules.service.infrastructure.persistence.entities;

import java.util.UUID;

import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "service_images")
public class ServiceImageJpaEntity extends BaseJpaEntity {

    private UUID serviceId;

    private String url;

    private Short sortOrder;

}
