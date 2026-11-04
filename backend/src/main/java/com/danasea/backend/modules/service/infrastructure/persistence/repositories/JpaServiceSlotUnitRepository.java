package com.danasea.backend.modules.service.infrastructure.persistence.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotUnitJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaServiceSlotUnitRepository extends JpaRepository<ServiceSlotUnitJpaEntity, UUID> {

    List<ServiceSlotUnitJpaEntity> findBySlotIdOrderByUnitNumberAsc(UUID slotId);

    List<ServiceSlotUnitJpaEntity> findBySlotIdInOrderBySlotIdAscUnitNumberAsc(List<UUID> slotIds);

    Optional<ServiceSlotUnitJpaEntity> findBySlotIdAndUnitNumber(UUID slotId, Integer unitNumber);

    void deleteBySlotId(UUID slotId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ServiceSlotUnitJpaEntity u SET u.bookedCount = u.bookedCount + :quantity WHERE u.slotId = :slotId AND u.unitNumber = :unitNumber AND u.equipmentBlocked = false AND (u.capacity - u.bookedCount) >= :quantity")
    int incrementUnitBookedCount(@Param("slotId") UUID slotId, @Param("unitNumber") Integer unitNumber, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ServiceSlotUnitJpaEntity u SET u.bookedCount = GREATEST(0, u.bookedCount - :quantity) WHERE u.slotId = :slotId AND u.unitNumber = :unitNumber")
    int decrementUnitBookedCount(@Param("slotId") UUID slotId, @Param("unitNumber") Integer unitNumber, @Param("quantity") int quantity);
}
