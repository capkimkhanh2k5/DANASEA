package com.danasea.backend.modules.order.infrastructure.persistence.repositories;

import java.time.OffsetDateTime;
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

import com.danasea.backend.modules.order.domain.models.PaymentAttemptState;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.specifications.AdminTransactionSpecifications;

import jakarta.persistence.LockModeType;

@Repository
public interface JpaPaymentRepository extends JpaRepository<PaymentJpaEntity, UUID>, JpaSpecificationExecutor<PaymentJpaEntity> {

    List<PaymentJpaEntity> findTop50ByProviderAndStatusAndCaptureRequestedAtIsNotNullOrderByCaptureRequestedAtAsc(
            PaymentProvider provider, PaymentStatus status);

    List<PaymentJpaEntity> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            PaymentStatus status, OffsetDateTime before);

    @Query("SELECT p FROM PaymentJpaEntity p WHERE p.status = :status AND p.provider IN :providers "
            + "AND (p.createdAt <= :threshold OR p.captureRequestedAt IS NOT NULL) "
            + "AND (p.reconciliationNextAttemptAt IS NULL OR p.reconciliationNextAttemptAt <= :now) "
            + "ORDER BY p.createdAt ASC, p.id ASC")
    List<PaymentJpaEntity> findReconciliationCandidates(
            @Param("status") PaymentStatus status, @Param("providers") List<PaymentProvider> providers,
            @Param("threshold") OffsetDateTime threshold, @Param("now") OffsetDateTime now, Pageable pageable);

    @Query("SELECT p FROM PaymentJpaEntity p, MasterOrderJpaEntity o WHERE p.masterOrderId = o.id "
            + "AND p.isActive = true AND p.status = :paymentStatus AND o.status = :orderStatus "
            + "AND o.paymentDeadline <= :now ORDER BY o.paymentDeadline, p.id")
    List<PaymentJpaEntity> findExpiredActiveAttempts(@Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("orderStatus") MasterOrderStatus orderStatus, @Param("now") OffsetDateTime now);

    Optional<PaymentJpaEntity> findByMasterOrderIdAndIdempotencyKey(UUID masterOrderId, String idempotencyKey);

    Optional<PaymentJpaEntity> findByProviderAndWebhookEventId(PaymentProvider provider, String webhookEventId);

    Optional<PaymentJpaEntity> findByProviderAndProviderTransactionId(PaymentProvider provider, String providerTransactionId);

    Optional<PaymentJpaEntity> findByProviderAndProviderOrderId(PaymentProvider provider, String providerOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentJpaEntity p WHERE p.provider = :provider AND p.providerOrderId = :providerOrderId")
    Optional<PaymentJpaEntity> findByProviderAndProviderOrderIdForUpdate(
            @Param("provider") PaymentProvider provider,
            @Param("providerOrderId") String providerOrderId);

    Optional<PaymentJpaEntity> findByProviderOrderId(String providerOrderId);

    Optional<PaymentJpaEntity> findByMasterOrderIdAndIsActiveTrue(UUID masterOrderId);

    @Query("SELECT COALESCE(MAX(p.attemptNumber), 0) FROM PaymentJpaEntity p WHERE p.masterOrderId = :masterOrderId")
    int findMaxAttemptNumberByMasterOrderId(@Param("masterOrderId") UUID masterOrderId);

    @Query("SELECT p FROM PaymentJpaEntity p WHERE p.masterOrderId = :masterOrderId AND p.attemptState IN :states")
    List<PaymentJpaEntity> findInFlightAttempts(
            @Param("masterOrderId") UUID masterOrderId,
            @Param("states") List<PaymentAttemptState> states);

    Optional<PaymentJpaEntity> findByPayosOrderCode(Long payosOrderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentJpaEntity p WHERE p.payosOrderCode = :code")
    Optional<PaymentJpaEntity> findByPayosOrderCodeForUpdate(@Param("code") Long payosOrderCode);

    @Query(value = "SELECT nextval('payos_order_code_seq')", nativeQuery = true)
    Long getNextPayOSOrderCode();

    List<PaymentJpaEntity> findByMasterOrderIdOrderByCreatedAtDesc(UUID masterOrderId);

    boolean existsByMasterOrderIdAndStatus(UUID masterOrderId, PaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentJpaEntity p WHERE p.id = :id")
    Optional<PaymentJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT p FROM PaymentJpaEntity p WHERE "
            + "(:status IS NULL OR p.status = :status) AND "
            + "(:provider IS NULL OR p.provider = :provider) AND "
            + "(:masterOrderId IS NULL OR p.masterOrderId = :masterOrderId)")
    Page<PaymentJpaEntity> findByFilters(
            @Param("status") PaymentStatus status,
            @Param("provider") PaymentProvider provider,
            @Param("masterOrderId") UUID masterOrderId,
            Pageable pageable);

    default Page<PaymentJpaEntity> findByAdvancedFilters(
            PaymentStatus status,
            PaymentProvider provider,
            UUID masterOrderId,
            UUID customerId,
            UUID vendorId,
            OffsetDateTime fromDate,
            OffsetDateTime toDate,
            Pageable pageable) {
        return findAll(AdminTransactionSpecifications.payments(status, provider, masterOrderId, customerId, vendorId, fromDate, toDate), pageable);
    }
}
