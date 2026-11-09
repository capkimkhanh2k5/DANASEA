package com.danasea.backend.modules.ai.infrastructure.persistence.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiCustomerSupportRequestJpaEntity;

public interface JpaAiCustomerSupportRequestRepository extends JpaRepository<AiCustomerSupportRequestJpaEntity, UUID> {
    Optional<AiCustomerSupportRequestJpaEntity> findByIdAndOwnerId(UUID id, UUID ownerId);
    Optional<AiCustomerSupportRequestJpaEntity> findByOwnerIdAndIdempotencyKey(UUID ownerId, String key);
    List<AiCustomerSupportRequestJpaEntity> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);
    List<AiCustomerSupportRequestJpaEntity> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);
    long countByStatus(String status);
}
