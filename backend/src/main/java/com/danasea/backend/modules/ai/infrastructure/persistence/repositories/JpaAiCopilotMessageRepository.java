package com.danasea.backend.modules.ai.infrastructure.persistence.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiCopilotMessageJpaEntity;

@Repository
public interface JpaAiCopilotMessageRepository extends JpaRepository<AiCopilotMessageJpaEntity, UUID> {
    List<AiCopilotMessageJpaEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
    List<AiCopilotMessageJpaEntity> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);
}
