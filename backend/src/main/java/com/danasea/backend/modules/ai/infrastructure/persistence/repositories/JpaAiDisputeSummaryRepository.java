package com.danasea.backend.modules.ai.infrastructure.persistence.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiDisputeSummaryJpaEntity;

@Repository
public interface JpaAiDisputeSummaryRepository extends JpaRepository<AiDisputeSummaryJpaEntity, UUID> {
    Optional<AiDisputeSummaryJpaEntity> findByDisputeId(UUID disputeId);
}
