package com.danasea.backend.modules.order.infrastructure.persistence.repositories;

import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderRescheduleProposalJpaEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaSubOrderRescheduleProposalRepository
        extends JpaRepository<SubOrderRescheduleProposalJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM SubOrderRescheduleProposalJpaEntity e WHERE e.id = :id")
    Optional<SubOrderRescheduleProposalJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    List<SubOrderRescheduleProposalJpaEntity>
            findTop50ByNotificationSentAtIsNullOrderByCreatedAtAsc();

    List<SubOrderRescheduleProposalJpaEntity> findBySubOrderIdAndStatus(
            UUID subOrderId, String status);

    List<SubOrderRescheduleProposalJpaEntity> findBySubOrderIdOrderByCreatedAtDesc(UUID subOrderId);

    Optional<SubOrderRescheduleProposalJpaEntity> findByIdAndSubOrderId(UUID id, UUID subOrderId);

    List<SubOrderRescheduleProposalJpaEntity> findBySubOrderIdAndStatusAndExpiresAtAfter(
            UUID subOrderId, String status, OffsetDateTime now);

    @Query("SELECT COUNT(p) > 0 FROM SubOrderRescheduleProposalJpaEntity p WHERE " +
           "p.subOrderId IN (SELECT s.id FROM SubOrderJpaEntity s WHERE s.masterOrderId IN " +
           "(SELECT o.id FROM MasterOrderJpaEntity o WHERE o.customerId = :customerId)) " +
           "AND p.status = 'PENDING'")
    boolean hasPendingRescheduleProposals(@Param("customerId") UUID customerId);
}
