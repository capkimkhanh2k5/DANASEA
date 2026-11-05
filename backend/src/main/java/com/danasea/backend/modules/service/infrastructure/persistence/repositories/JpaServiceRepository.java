package com.danasea.backend.modules.service.infrastructure.persistence.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import jakarta.persistence.LockModeType;

@Repository
public interface JpaServiceRepository extends JpaRepository<ServiceJpaEntity, UUID>, JpaSpecificationExecutor<ServiceJpaEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ServiceJpaEntity s WHERE s.id = :id")
    Optional<ServiceJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByCategoryIdAndStatus(UUID categoryId, ServiceStatus status);

    List<ServiceJpaEntity> findByVendorId(UUID vendorId);

    List<ServiceJpaEntity> findByVendorIdOrderByCreatedAtDesc(UUID vendorId);

    List<ServiceJpaEntity> findByStatus(ServiceStatus status);

    List<ServiceJpaEntity> findByStatusOrderByCreatedAtDesc(ServiceStatus status);

    boolean existsBySlug(String slug);

    Optional<ServiceJpaEntity> findBySlugAndStatus(String slug, ServiceStatus status);

    Optional<ServiceJpaEntity> findBySlug(String slug);

    @Modifying
    @Query("UPDATE ServiceJpaEntity s SET s.viewCount = COALESCE(s.viewCount, 0) + 1 WHERE s.id = :id AND s.status = :status")
    int incrementViewCount(@Param("id") UUID id, @Param("status") ServiceStatus status);

    Optional<ServiceJpaEntity> findByIdAndStatus(UUID id, ServiceStatus status);

    long countByVendorIdAndStatus(UUID vendorId, ServiceStatus status);
}
