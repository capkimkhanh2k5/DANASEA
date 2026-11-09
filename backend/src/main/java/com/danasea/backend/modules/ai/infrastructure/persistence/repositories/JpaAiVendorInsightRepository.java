package com.danasea.backend.modules.ai.infrastructure.persistence.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiVendorInsightJpaEntity;

@Repository
public interface JpaAiVendorInsightRepository extends JpaRepository<AiVendorInsightJpaEntity, UUID> {

    List<AiVendorInsightJpaEntity> findByVendorIdAndInsightTypeOrderByCreatedAtDesc(
            UUID vendorId, String insightType, Pageable pageable);

    Optional<AiVendorInsightJpaEntity> findFirstByVendorIdAndServiceIdAndInsightTypeOrderByCreatedAtDesc(
            UUID vendorId, UUID serviceId, String insightType);

    Optional<AiVendorInsightJpaEntity> findFirstByVendorIdAndInsightTypeAndSourceFingerprint(
            UUID vendorId, String insightType, String sourceFingerprint);
}
