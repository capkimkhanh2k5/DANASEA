package com.danasea.backend.modules.ai.infrastructure.persistence.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiMarineBriefingJpaEntity;

@Repository
public interface JpaAiMarineBriefingRepository extends JpaRepository<AiMarineBriefingJpaEntity, UUID> {
    Optional<AiMarineBriefingJpaEntity> findBySubOrderIdAndCustomerId(UUID subOrderId, UUID customerId);
    List<AiMarineBriefingJpaEntity> findByMasterOrderId(UUID masterOrderId);
    List<AiMarineBriefingJpaEntity> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
    Optional<AiMarineBriefingJpaEntity> findFirstBySlotIdAndCustomerIdOrderByCreatedAtDesc(UUID slotId, UUID customerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO ai_marine_briefings (master_order_id, sub_order_id, customer_id, slot_id, weather_status, briefing_json)
            VALUES (:orderId, :subOrderId, :customerId, :slotId, :weatherStatus, :briefingJson)
            ON CONFLICT (sub_order_id, customer_id) DO UPDATE
            SET master_order_id = EXCLUDED.master_order_id, slot_id = EXCLUDED.slot_id,
                weather_status = EXCLUDED.weather_status, briefing_json = EXCLUDED.briefing_json, updated_at = now()
            """, nativeQuery = true)
    void upsert(@Param("orderId") UUID orderId, @Param("subOrderId") UUID subOrderId,
            @Param("customerId") UUID customerId, @Param("slotId") UUID slotId,
            @Param("weatherStatus") String weatherStatus, @Param("briefingJson") String briefingJson);
}
