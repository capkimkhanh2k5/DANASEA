package com.danasea.backend.modules.settlement.infrastructure.persistence.repositories;

import com.danasea.backend.modules.settlement.infrastructure.persistence.entities.SettlementLineItemJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository("settlementJpaSettlementLineItemRepository")
public interface JpaSettlementLineItemRepository extends JpaRepository<SettlementLineItemJpaEntity, UUID> {

    List<SettlementLineItemJpaEntity> findBySettlementId(UUID settlementId);

    @Query("SELECT sli.subOrderId FROM SettlementLineItemJpaEntity sli, SettlementJpaEntity s " +
           "WHERE sli.settlementId = s.id AND s.vendorId = :vendorId " +
           "AND s.status IN (com.danasea.backend.modules.settlement.domain.models.SettlementStatus.FINALIZED, " +
           "com.danasea.backend.modules.settlement.domain.models.SettlementStatus.PAID) " +
           "AND (sli.excludedReason IS NULL OR sli.excludedReason = com.danasea.backend.modules.settlement.domain.models.LineItemExclusionReason.NONE " +
           "OR sli.excludedReason = com.danasea.backend.modules.settlement.domain.models.LineItemExclusionReason.FULL_REFUND)")
    java.util.Set<UUID> findSettledSubOrderIdsByVendorId(@Param("vendorId") UUID vendorId);

    @Query("SELECT sli.subOrderId FROM SettlementLineItemJpaEntity sli, SettlementJpaEntity s " +
           "WHERE sli.settlementId = s.id AND s.vendorId = :vendorId " +
           "AND sli.excludedReason IN (com.danasea.backend.modules.settlement.domain.models.LineItemExclusionReason.DISPUTE_WINDOW_OPEN, " +
           "com.danasea.backend.modules.settlement.domain.models.LineItemExclusionReason.ACTIVE_DISPUTE, " +
           "com.danasea.backend.modules.settlement.domain.models.LineItemExclusionReason.PENDING_REFUND, " +
           "com.danasea.backend.modules.settlement.domain.models.LineItemExclusionReason.ACTIVE_DISRUPTION)")
    java.util.Set<UUID> findWithheldSubOrderIdsByVendorId(@Param("vendorId") UUID vendorId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM SettlementLineItemJpaEntity e WHERE e.settlementId = :settlementId")
    void deleteBySettlementId(@Param("settlementId") UUID settlementId);
}
