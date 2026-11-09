package com.danasea.backend.modules.ai.infrastructure.persistence.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiConversationJpaEntity;

import jakarta.persistence.LockModeType;

@Repository
public interface JpaAiConversationRepository extends JpaRepository<AiConversationJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AiConversationJpaEntity c where c.id = :id")
    Optional<AiConversationJpaEntity> findForUpdate(@Param("id") UUID id);

    @Query("SELECT c FROM AiConversationJpaEntity c WHERE c.userId = :userId AND c.isDeleted = false ORDER BY c.pinned DESC, c.updatedAt DESC")
    List<AiConversationJpaEntity> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT c FROM AiConversationJpaEntity c WHERE c.userId = :userId AND c.archived = :archived AND c.isDeleted = false ORDER BY c.pinned DESC, c.updatedAt DESC")
    List<AiConversationJpaEntity> findByUserIdAndArchived(@Param("userId") UUID userId, @Param("archived") boolean archived, Pageable pageable);
}
