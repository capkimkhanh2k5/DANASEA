package com.danasea.backend.modules.order.infrastructure.persistence.repositories;

import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderDisruptionJpaEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaSubOrderDisruptionRepository extends JpaRepository<SubOrderDisruptionJpaEntity, UUID> {
    Optional<SubOrderDisruptionJpaEntity> findBySubOrderIdAndSourceKey(UUID subOrderId, String sourceKey);
    Optional<SubOrderDisruptionJpaEntity> findBySubOrderIdAndStatus(UUID subOrderId, String status);
    List<SubOrderDisruptionJpaEntity> findBySubOrderIdInAndStatus(java.util.Collection<UUID> subOrderIds, String status);
    List<SubOrderDisruptionJpaEntity> findTop100ByStatusAndDecisionDeadlineLessThanEqualOrderByDecisionDeadlineAsc(
            String status, OffsetDateTime now);
}
