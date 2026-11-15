package com.danasea.backend.modules.systemconfig.infrastructure.persistence.repositories;

import java.util.Optional;
import java.util.UUID;

import com.danasea.backend.modules.systemconfig.infrastructure.persistence.entities.SystemConfigJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaSystemConfigRepository extends JpaRepository<SystemConfigJpaEntity, UUID> {
    Optional<SystemConfigJpaEntity> findByKey(String key);

    // A row lock cannot protect an absent key. Serialize writes through commit while allowing ordinary reads.
    @Modifying
    @Query(value = "LOCK TABLE system_configs IN SHARE ROW EXCLUSIVE MODE", nativeQuery = true)
    void lockWrites();
}

