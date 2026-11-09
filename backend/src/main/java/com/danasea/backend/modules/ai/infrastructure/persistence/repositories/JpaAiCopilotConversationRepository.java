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

import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiCopilotConversationJpaEntity;
import jakarta.persistence.LockModeType;

@Repository
public interface JpaAiCopilotConversationRepository extends JpaRepository<AiCopilotConversationJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM AiCopilotConversationJpaEntity c WHERE c.id = :id AND c.isDeleted = false")
    Optional<AiCopilotConversationJpaEntity> findForUpdate(@Param("id") UUID id);

    @Query("SELECT c FROM AiCopilotConversationJpaEntity c WHERE c.actorUserId = :userId AND c.actorRole = :role AND c.isDeleted = false ORDER BY c.pinned DESC, c.updatedAt DESC")
    List<AiCopilotConversationJpaEntity> findByActor(@Param("userId") UUID userId, @Param("role") String role, Pageable pageable);

    @Query("SELECT c FROM AiCopilotConversationJpaEntity c WHERE c.actorUserId = :userId AND c.actorRole = :role AND c.archived = :archived AND c.isDeleted = false ORDER BY c.pinned DESC, c.updatedAt DESC")
    List<AiCopilotConversationJpaEntity> findByActorAndArchived(@Param("userId") UUID userId, @Param("role") String role, @Param("archived") boolean archived, Pageable pageable);

    @Query("SELECT c FROM AiCopilotConversationJpaEntity c WHERE c.vendorId = :vendorId AND c.isDeleted = false ORDER BY c.pinned DESC, c.updatedAt DESC")
    List<AiCopilotConversationJpaEntity> findByVendorId(@Param("vendorId") UUID vendorId, Pageable pageable);
}
