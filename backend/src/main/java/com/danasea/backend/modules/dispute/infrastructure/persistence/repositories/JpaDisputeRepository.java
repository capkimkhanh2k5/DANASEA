package com.danasea.backend.modules.dispute.infrastructure.persistence.repositories;

import com.danasea.backend.modules.dispute.domain.models.DisputeReason;
import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.dispute.infrastructure.persistence.entities.DisputeJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;

@Repository("disputeJpaDisputeRepository")
public interface JpaDisputeRepository extends JpaRepository<DisputeJpaEntity, UUID>, JpaSpecificationExecutor<DisputeJpaEntity> {

    boolean existsBySubOrderIdAndStatusIn(UUID subOrderId, Collection<DisputeStatus> statuses);

    Optional<DisputeJpaEntity> findBySubOrderIdAndStatusIn(UUID subOrderId, Collection<DisputeStatus> statuses);

    List<DisputeJpaEntity> findBySubOrderId(UUID subOrderId);

    List<DisputeJpaEntity> findByOrderId(UUID orderId);

    Page<DisputeJpaEntity> findByCustomerId(UUID customerId, Pageable pageable);

    Optional<DisputeJpaEntity> findByIdAndCustomerId(UUID id, UUID customerId);

    Page<DisputeJpaEntity> findBySubOrderIdIn(Collection<UUID> subOrderIds, Pageable pageable);

    Page<DisputeJpaEntity> findBySubOrderIdInAndStatus(Collection<UUID> subOrderIds, DisputeStatus status, Pageable pageable);

    Optional<DisputeJpaEntity> findByIdAndSubOrderIdIn(UUID id, Collection<UUID> subOrderIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DisputeEntity d WHERE d.id = :id")
    Optional<DisputeJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    List<DisputeJpaEntity> findBySubOrderIdInAndStatusIn(Collection<UUID> subOrderIds, Collection<DisputeStatus> statuses);

    Page<DisputeJpaEntity> findByStatusAndReason(DisputeStatus status, DisputeReason reason, Pageable pageable);

    Page<DisputeJpaEntity> findByStatus(DisputeStatus status, Pageable pageable);

    long countByStatus(DisputeStatus status);

    Page<DisputeJpaEntity> findByReason(DisputeReason reason, Pageable pageable);

    @Query("SELECT d FROM DisputeEntity d WHERE " +
           "(:status IS NULL OR d.status = :status) AND " +
           "(:reason IS NULL OR d.reason = :reason)")
    Page<DisputeJpaEntity> findFiltered(
            @Param("status") DisputeStatus status,
            @Param("reason") DisputeReason reason,
            Pageable pageable
    );
}
