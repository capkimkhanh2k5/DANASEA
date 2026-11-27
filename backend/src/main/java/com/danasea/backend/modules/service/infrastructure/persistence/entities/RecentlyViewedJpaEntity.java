package com.danasea.backend.modules.service.infrastructure.persistence.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "recently_vieweds")
public class RecentlyViewedJpaEntity extends BaseJpaEntity {

    private UUID userId;

    private String sessionId;

    private UUID serviceId;

    private OffsetDateTime viewedAt;

}
