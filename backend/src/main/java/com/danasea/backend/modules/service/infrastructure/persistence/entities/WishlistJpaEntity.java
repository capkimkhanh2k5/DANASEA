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
@Table(name = "wishlists")
public class WishlistJpaEntity extends BaseJpaEntity {

    private UUID userId;

    private UUID serviceId;

}
