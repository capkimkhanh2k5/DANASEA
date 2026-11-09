package com.danasea.backend.modules.ai.infrastructure.persistence.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiDisputeSummaryJpaEntity;

@Repository
public interface JpaAiDisputeSummaryRepository extends JpaRepository<AiDisputeSummaryJpaEntity, UUID> {
    Optional<AiDisputeSummaryJpaEntity> findByDisputeId(UUID disputeId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO ai_dispute_summaries (dispute_id, sub_order_id, requested_by_admin_id,
                recommended_resolution, recommended_refund_percent, summary_json, analysis_method)
            VALUES (:disputeId, :subOrderId, :adminId, :resolution, :refundPercent, :summaryJson, :analysisMethod)
            ON CONFLICT (dispute_id) DO UPDATE
            SET sub_order_id = EXCLUDED.sub_order_id, requested_by_admin_id = EXCLUDED.requested_by_admin_id,
                recommended_resolution = EXCLUDED.recommended_resolution,
                recommended_refund_percent = EXCLUDED.recommended_refund_percent,
                summary_json = EXCLUDED.summary_json, analysis_method = EXCLUDED.analysis_method, updated_at = now()
            """, nativeQuery = true)
    void upsert(@Param("disputeId") UUID disputeId, @Param("subOrderId") UUID subOrderId,
            @Param("adminId") UUID adminId, @Param("resolution") String resolution,
            @Param("refundPercent") Integer refundPercent, @Param("summaryJson") String summaryJson,
            @Param("analysisMethod") String analysisMethod);
}
