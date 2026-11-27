package com.danasea.backend.modules.weather.infrastructure.persistence.repositories;

import java.util.UUID;

import com.danasea.backend.modules.weather.infrastructure.persistence.entities.WeatherCacheJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JpaWeatherCacheRepository extends JpaRepository<WeatherCacheJpaEntity, UUID> {
    Optional<WeatherCacheJpaEntity> findFirstByLocationKeyOrderByFetchedAtDesc(String locationKey);
}
