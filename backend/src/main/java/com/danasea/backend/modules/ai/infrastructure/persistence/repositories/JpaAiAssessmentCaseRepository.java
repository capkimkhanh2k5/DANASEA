package com.danasea.backend.modules.ai.infrastructure.persistence.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiAssessmentCaseJpaEntity;

public interface JpaAiAssessmentCaseRepository extends JpaRepository<AiAssessmentCaseJpaEntity, UUID> {
    List<AiAssessmentCaseJpaEntity> findByStatusOrderByCreatedAtDesc(String status, Pageable page);
    long countByStatus(String status);
}
