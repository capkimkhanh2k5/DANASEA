package com.danasea.backend.modules.ai.infrastructure.persistence.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiMarineBriefingJpaEntity;

@Repository
public interface JpaAiMarineBriefingRepository extends JpaRepository<AiMarineBriefingJpaEntity, UUID> {
    Optional<AiMarineBriefingJpaEntity> findBySubOrderIdAndCustomerId(UUID subOrderId, UUID customerId);
    List<AiMarineBriefingJpaEntity> findByMasterOrderId(UUID masterOrderId);
    List<AiMarineBriefingJpaEntity> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
    Optional<AiMarineBriefingJpaEntity> findFirstBySlotIdAndCustomerIdOrderByCreatedAtDesc(UUID slotId, UUID customerId);
}
