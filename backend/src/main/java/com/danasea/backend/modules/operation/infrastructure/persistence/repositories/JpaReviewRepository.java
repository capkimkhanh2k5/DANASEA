package com.danasea.backend.modules.operation.infrastructure.persistence.repositories;

import java.util.Optional;
import java.util.UUID;

import com.danasea.backend.modules.operation.infrastructure.persistence.entities.ReviewJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaReviewRepository extends JpaRepository<ReviewJpaEntity, UUID>, JpaSpecificationExecutor<ReviewJpaEntity> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReviewJpaEntity r where r.id = :id")
    Optional<ReviewJpaEntity> findByIdForUpdate(UUID id);

    @Query("select r.id from ReviewJpaEntity r where r.subOrderId = :subOrderId")
    Optional<UUID> findReviewIdBySubOrderId(UUID subOrderId);

    Optional<ReviewJpaEntity> findBySubOrderId(UUID subOrderId);

    boolean existsBySubOrderId(UUID subOrderId);

    Page<ReviewJpaEntity> findByServiceIdAndIsVisibleTrue(UUID serviceId, Pageable pageable);

    Page<ReviewJpaEntity> findByVendorId(UUID vendorId, Pageable pageable);

    Page<ReviewJpaEntity> findByVendorIdAndIsVisibleTrue(UUID vendorId, Pageable pageable);

    Page<ReviewJpaEntity> findByVendorIdAndServiceIdAndIsVisibleTrue(UUID vendorId, UUID serviceId, Pageable pageable);

    @Query("SELECT COUNT(r) FROM ReviewJpaEntity r WHERE r.serviceId = :serviceId AND r.isVisible = true")
    long countByServiceIdAndIsVisibleTrue(@Param("serviceId") UUID serviceId);

    @Query("SELECT AVG(r.rating) FROM ReviewJpaEntity r WHERE r.serviceId = :serviceId AND r.isVisible = true")
    Double getAvgRatingByServiceId(@Param("serviceId") UUID serviceId);

    @Query("SELECT COUNT(r) FROM ReviewJpaEntity r WHERE r.vendorId = :vendorId AND r.isVisible = true")
    long countByVendorIdAndIsVisibleTrue(@Param("vendorId") UUID vendorId);

    @Query("SELECT AVG(r.rating) FROM ReviewJpaEntity r WHERE r.vendorId = :vendorId AND r.isVisible = true")
    Double getAvgRatingByVendorId(@Param("vendorId") UUID vendorId);
}
