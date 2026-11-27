package com.danasea.backend.modules.order.infrastructure.persistence.repositories;

import com.danasea.backend.modules.order.domain.models.DiscountScope;
import com.danasea.backend.modules.order.domain.models.DiscountSponsorType;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.DiscountCodeJpaEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaDiscountCodeRepository extends JpaRepository<DiscountCodeJpaEntity, UUID>, JpaSpecificationExecutor<DiscountCodeJpaEntity> {

    @Query("""
            SELECT d FROM DiscountCodeJpaEntity d
            WHERE d.scope = :scope
              AND d.sponsorType = :sponsorType
              AND d.isActive = true
              AND d.recipientCustomerId IS NULL
              AND (d.validFrom IS NULL OR d.validFrom <= :now)
              AND (d.validTo IS NULL OR d.validTo > :now)
              AND (d.maxUses IS NULL OR COALESCE(d.usedCount, 0) < d.maxUses)
            ORDER BY d.discountValue DESC, d.createdAt DESC
            """)
    List<DiscountCodeJpaEntity> findActivePublicDiscounts(
            @Param("scope") DiscountScope scope,
            @Param("sponsorType") DiscountSponsorType sponsorType,
            @Param("now") OffsetDateTime now);

    default List<DiscountCodeJpaEntity> findActivePublicVendorDiscounts(OffsetDateTime now) {
        return findActivePublicDiscounts(DiscountScope.VENDOR, DiscountSponsorType.VENDOR, now);
    }

    Optional<DiscountCodeJpaEntity> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    Page<DiscountCodeJpaEntity> findByVendorId(UUID vendorId, Pageable pageable);

    Page<DiscountCodeJpaEntity> findByRecipientCustomerId(UUID customerId, Pageable pageable);

    Optional<DiscountCodeJpaEntity> findByReplacementSubOrderId(UUID subOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DiscountCodeJpaEntity d WHERE UPPER(d.code) = UPPER(:code)")
    Optional<DiscountCodeJpaEntity> findByCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DiscountCodeJpaEntity d WHERE d.id = :id")
    Optional<DiscountCodeJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query(value = "UPDATE discount_codes SET used_count = used_count + 1, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = :id AND (max_uses IS NULL OR used_count < max_uses)", nativeQuery = true)
    int incrementUsedCount(@Param("id") UUID id);

    @Modifying
    @Query(value = "UPDATE discount_codes SET used_count = CASE WHEN used_count > 0 THEN used_count - 1 ELSE 0 END, "
            + "updated_at = CURRENT_TIMESTAMP WHERE id = :id", nativeQuery = true)
    int decrementUsedCount(@Param("id") UUID id);
}
