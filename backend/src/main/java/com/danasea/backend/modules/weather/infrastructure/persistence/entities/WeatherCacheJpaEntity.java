package com.danasea.backend.modules.weather.infrastructure.persistence.entities;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "weather_caches")
public class WeatherCacheJpaEntity extends BaseJpaEntity {

    private String locationKey;

    private BigDecimal windSpeedKmh;

    private BigDecimal waveHeightM;

    private BigDecimal precipitationMm;

    @Column(columnDefinition = "TEXT")
    private String rawPayload;

    private OffsetDateTime fetchedAt;

    private OffsetDateTime expiresAt;

}
