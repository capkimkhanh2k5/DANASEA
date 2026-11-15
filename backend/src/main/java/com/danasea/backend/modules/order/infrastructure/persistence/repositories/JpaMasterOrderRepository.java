package com.danasea.backend.modules.order.infrastructure.persistence.repositories;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.specifications.AdminTransactionSpecifications;

import jakarta.persistence.LockModeType;

@Repository
public interface JpaMasterOrderRepository extends JpaRepository<MasterOrderJpaEntity, UUID>, JpaSpecificationExecutor<MasterOrderJpaEntity> {

    Optional<MasterOrderJpaEntity> findByBookingId(UUID bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM MasterOrderJpaEntity o WHERE o.bookingId = :bookingId")
    Optional<MasterOrderJpaEntity> findByBookingIdForUpdate(@Param("bookingId") UUID bookingId);

    Optional<MasterOrderJpaEntity> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);

    Page<MasterOrderJpaEntity> findByCustomerId(UUID customerId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM MasterOrderJpaEntity o WHERE o.id = :id")
    Optional<MasterOrderJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT COUNT(o) > 0 FROM MasterOrderJpaEntity o WHERE o.customerId = :customerId " +
           "AND o.status IN (com.danasea.backend.modules.order.domain.models.MasterOrderStatus.PENDING_PAYMENT, " +
           "com.danasea.backend.modules.order.domain.models.MasterOrderStatus.PAID, " +
           "com.danasea.backend.modules.order.domain.models.MasterOrderStatus.PARTIALLY_COMPLETED)")
    boolean hasActiveMasterOrders(@Param("customerId") UUID customerId);

    default Page<MasterOrderJpaEntity> findByAdminFilters(
            MasterOrderStatus status,
            PaymentOrderStatus paymentStatus,
            UUID customerId,
            UUID vendorId,
            OffsetDateTime fromDate,
            OffsetDateTime toDate,
            Pageable pageable) {
        return findAll(AdminTransactionSpecifications.orders(status, paymentStatus, customerId, vendorId, fromDate, toDate), pageable);
    }
}
