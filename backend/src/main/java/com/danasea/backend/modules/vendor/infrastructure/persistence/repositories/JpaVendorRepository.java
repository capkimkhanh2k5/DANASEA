package com.danasea.backend.modules.vendor.infrastructure.persistence.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;

@Repository
public interface JpaVendorRepository extends JpaRepository<VendorJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VendorJpaEntity v where v.id = :id")
    Optional<VendorJpaEntity> findByIdForUpdate(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VendorJpaEntity v where v.userId = :userId")
    Optional<VendorJpaEntity> findByUserIdForUpdate(UUID userId);

    Optional<VendorJpaEntity> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    Page<VendorJpaEntity> findByVerificationStatus(VerificationStatus verificationStatus, Pageable pageable);
}
