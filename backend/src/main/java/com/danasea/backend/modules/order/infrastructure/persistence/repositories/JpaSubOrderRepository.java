package com.danasea.backend.modules.order.infrastructure.persistence.repositories;

import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaSubOrderRepository extends JpaRepository<SubOrderJpaEntity, UUID> {
    @Query("SELECT s.masterOrderId FROM SubOrderJpaEntity s WHERE s.id = :id")
    Optional<UUID> findMasterOrderIdById(@Param("id") UUID id);

    List<SubOrderJpaEntity> findBySlotId(UUID slotId);
    List<SubOrderJpaEntity> findBySlotIdAndStatus(UUID slotId, SubOrderStatus status);
    List<SubOrderJpaEntity> findByMasterOrderId(UUID masterOrderId);
    List<SubOrderJpaEntity> findByMasterOrderIdIn(Collection<UUID> masterOrderIds);
    List<SubOrderJpaEntity> findByVendorId(UUID vendorId);
    Page<SubOrderJpaEntity> findByVendorId(UUID vendorId, Pageable pageable);
    List<SubOrderJpaEntity> findByVendorIdAndStatusIn(UUID vendorId, Collection<SubOrderStatus> statuses);
    List<SubOrderJpaEntity> findByVendorIdAndCreatedAtBetween(UUID vendorId, OffsetDateTime startDateTime, OffsetDateTime endDateTime);
    Optional<SubOrderJpaEntity> findByBookingItemId(UUID bookingItemId);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SubOrderJpaEntity s WHERE s.bookingItemId = :bookingItemId")
    Optional<SubOrderJpaEntity> findByBookingItemIdForUpdate(
            @Param("bookingItemId") UUID bookingItemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SubOrderJpaEntity s WHERE s.id = :id")
    Optional<SubOrderJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SubOrderJpaEntity s WHERE s.masterOrderId = :masterOrderId ORDER BY s.id ASC")
    List<SubOrderJpaEntity> findByMasterOrderIdForUpdate(@Param("masterOrderId") UUID masterOrderId);

    @Query("SELECT COUNT(s) > 0 FROM SubOrderJpaEntity s WHERE s.masterOrderId IN " +
           "(SELECT o.id FROM MasterOrderJpaEntity o WHERE o.customerId = :customerId) " +
           "AND s.status IN (com.danasea.backend.modules.order.domain.models.SubOrderStatus.PENDING, " +
           "com.danasea.backend.modules.order.domain.models.SubOrderStatus.CONFIRMED, " +
           "com.danasea.backend.modules.order.domain.models.SubOrderStatus.CHECKED_IN, " +
           "com.danasea.backend.modules.order.domain.models.SubOrderStatus.IN_PROGRESS)")
    boolean hasActiveSubOrders(@Param("customerId") UUID customerId);

    @Query("SELECT s FROM SubOrderJpaEntity s WHERE s.masterOrderId IN " +
           "(SELECT o.id FROM MasterOrderJpaEntity o WHERE o.customerId = :customerId) " +
           "AND s.status = com.danasea.backend.modules.order.domain.models.SubOrderStatus.COMPLETED")
    List<SubOrderJpaEntity> findCompletedSubOrdersByCustomerId(@Param("customerId") UUID customerId);
}
