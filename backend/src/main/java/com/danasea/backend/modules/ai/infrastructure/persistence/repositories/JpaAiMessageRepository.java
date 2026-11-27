package com.danasea.backend.modules.ai.infrastructure.persistence.repositories;

import java.util.UUID;

import com.danasea.backend.modules.ai.domain.models.AiMessageRole;
import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiMessageJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;
import java.util.List;

@Repository
public interface JpaAiMessageRepository extends JpaRepository<AiMessageJpaEntity, UUID> {
    List<AiMessageJpaEntity> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);
    long countByConversationIdAndRole(UUID conversationId, AiMessageRole role);
}
