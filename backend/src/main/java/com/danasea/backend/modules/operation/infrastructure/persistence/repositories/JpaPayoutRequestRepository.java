package com.danasea.backend.modules.operation.infrastructure.persistence.repositories;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.danasea.backend.modules.operation.domain.models.PayoutRequestStatus;
import com.danasea.backend.modules.operation.infrastructure.persistence.entities.PayoutRequestJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaPayoutRequestRepository extends JpaRepository<PayoutRequestJpaEntity, UUID>, JpaSpecificationExecutor<PayoutRequestJpaEntity> {

    Page<PayoutRequestJpaEntity> findByVendorId(UUID vendorId, Pageable pageable);

    Page<PayoutRequestJpaEntity> findByVendorIdAndStatus(UUID vendorId, PayoutRequestStatus status, Pageable pageable);

    List<PayoutRequestJpaEntity> findBySettlementId(UUID settlementId);

    List<PayoutRequestJpaEntity> findBySettlementIdAndStatusIn(UUID settlementId, Collection<PayoutRequestStatus> statuses);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PayoutRequestJpaEntity p WHERE p.settlementId = :settlementId AND p.status IN :statuses")
    BigDecimal sumAmountBySettlementIdAndStatusIn(@Param("settlementId") UUID settlementId, @Param("statuses") Collection<PayoutRequestStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PayoutRequestJpaEntity p WHERE p.id = :id")
    Optional<PayoutRequestJpaEntity> findByIdForUpdate(@Param("id") UUID id);
}
