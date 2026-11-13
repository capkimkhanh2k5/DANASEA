package com.danasea.backend.modules.order.infrastructure.persistence.repositories;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
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

import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.specifications.AdminTransactionSpecifications;

import jakarta.persistence.LockModeType;

@Repository
public interface JpaRefundRepository extends JpaRepository<RefundJpaEntity, UUID>, JpaSpecificationExecutor<RefundJpaEntity> {
    Optional<RefundJpaEntity> findByProviderAndProviderRefundId(PaymentProvider provider, String providerRefundId);
    Optional<RefundJpaEntity> findByProviderRefundId(String providerRefundId);
    Optional<RefundJpaEntity> findByGatewayRequestId(String gatewayRequestId);
    List<RefundJpaEntity> findByPaymentId(UUID paymentId);
    boolean existsByPaymentIdAndSubOrderIdIsNullAndReason(UUID paymentId, RefundReason reason);

    List<RefundJpaEntity> findBySubOrderId(UUID subOrderId);
    List<RefundJpaEntity> findBySubOrderIdIn(Collection<UUID> subOrderIds);
    List<RefundJpaEntity> findBySubOrderIdInOrderByCreatedAtDesc(Collection<UUID> subOrderIds);
    Optional<RefundJpaEntity> findBySubOrderIdAndIdempotencyKey(UUID subOrderId, String idempotencyKey);
    Optional<RefundJpaEntity> findByProviderAndWebhookEventId(PaymentProvider provider, String webhookEventId);
    List<RefundJpaEntity> findBySubOrderIdInAndStatus(
            Collection<UUID> subOrderIds, RefundStatus status);

    @Query("""
            SELECT r FROM RefundJpaEntity r
            WHERE r.status = :status AND r.retryCount < :maxRetryCount
              AND (r.lastError IS NULL OR r.lastError <> :manualReviewMarker OR r.providerAmount IS NULL)
            ORDER BY r.createdAt ASC
            """)
    List<RefundJpaEntity> findForAutomaticProcessing(@Param("status") RefundStatus status,
            @Param("maxRetryCount") int maxRetryCount, @Param("manualReviewMarker") String manualReviewMarker);

    List<RefundJpaEntity> findByStatusAndCreatedAtLessThanOrderByCreatedAtAsc(RefundStatus status, OffsetDateTime threshold);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefundJpaEntity r WHERE r.id = :id")
    Optional<RefundJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT r FROM RefundJpaEntity r WHERE "
            + "(:status IS NULL OR r.status = :status) AND "
            + "(:reason IS NULL OR r.reason = :reason) AND "
            + "(:subOrderId IS NULL OR r.subOrderId = :subOrderId)")
    Page<RefundJpaEntity> findByFilters(
            @Param("status") RefundStatus status,
            @Param("reason") RefundReason reason,
            @Param("subOrderId") UUID subOrderId,
            Pageable pageable);

    default Page<RefundJpaEntity> findByAdvancedFilters(
            RefundStatus status,
            RefundReason reason,
            UUID subOrderId,
            PaymentProvider provider,
            UUID vendorId,
            UUID customerId,
            UUID orderId,
            OffsetDateTime fromDate,
            OffsetDateTime toDate,
            Pageable pageable) {
        return findAll(AdminTransactionSpecifications.refunds(status, reason, subOrderId, provider, vendorId, customerId, orderId, fromDate, toDate), pageable);
    }

    @Query("SELECT COUNT(r) > 0 FROM RefundJpaEntity r WHERE " +
           "(r.requestedBy = :customerId OR r.subOrderId IN " +
           "(SELECT s.id FROM SubOrderJpaEntity s WHERE s.masterOrderId IN " +
           "(SELECT o.id FROM MasterOrderJpaEntity o WHERE o.customerId = :customerId))) " +
           "AND r.status IN (com.danasea.backend.modules.order.domain.models.RefundStatus.PENDING, " +
           "com.danasea.backend.modules.order.domain.models.RefundStatus.FAILED)")
    boolean hasUnresolvedRefunds(@Param("customerId") UUID customerId);
}
