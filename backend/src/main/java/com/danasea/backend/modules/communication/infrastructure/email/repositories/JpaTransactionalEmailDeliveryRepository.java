package com.danasea.backend.modules.communication.infrastructure.email.repositories;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.communication.domain.models.EmailDeliveryMode;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;
import com.danasea.backend.modules.communication.infrastructure.email.entities.TransactionalEmailDeliveryJpaEntity;

@Repository
public interface JpaTransactionalEmailDeliveryRepository
        extends JpaRepository<TransactionalEmailDeliveryJpaEntity, UUID> {

    @Query(value = """
            SELECT id FROM transactional_email_delivery
            WHERE mode = :mode
              AND status IN ('PENDING', 'RETRYABLE_REJECTION')
              AND next_attempt_at <= :now
              AND (leased_until IS NULL OR leased_until < :now)
            ORDER BY next_attempt_at ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findDueEmailIds(
            @Param("mode") String mode,
            @Param("now") OffsetDateTime now,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT * FROM transactional_email_delivery
            WHERE id = :id
              AND status IN ('PENDING', 'RETRYABLE_REJECTION')
              AND (leased_until IS NULL OR leased_until < :now)
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<TransactionalEmailDeliveryJpaEntity> findByIdAndLockForClaim(
            @Param("id") UUID id,
            @Param("now") OffsetDateTime now
    );

    @Transactional
    @Modifying
    @Query(value = """
            UPDATE transactional_email_delivery
            SET status = 'UNKNOWN',
                last_error_code = 'LEASE_EXPIRED',
                last_error_message = 'Lease expired without completion confirmation',
                leased_until = NULL,
                updated_at = :now
            WHERE leased_until IS NOT NULL
              AND leased_until < :now
              AND status IN ('PENDING', 'RETRYABLE_REJECTION')
            """, nativeQuery = true)
    int markExpiredLeasesAsUnknown(@Param("now") OffsetDateTime now);

    @Modifying
    @Query(value = """
            INSERT INTO transactional_email_delivery (
                id, type, business_key, recipient_key, family, mode, status,
                payload_version, payload, occurred_at, due_at, expires_at,
                attempt_count, next_attempt_at, created_at, updated_at
            ) VALUES (
                :id, :type, :businessKey, :recipientKey, :family, :mode, 'PENDING',
                :payloadVersion, CAST(:payload AS jsonb), :occurredAt, :dueAt, :expiresAt,
                0, :dueAt, :now, :now
            ) ON CONFLICT (type, business_key, recipient_key, mode) DO NOTHING
            """, nativeQuery = true)
    int insertOnConflictDoNothing(
            @Param("id") UUID id,
            @Param("type") String type,
            @Param("businessKey") String businessKey,
            @Param("recipientKey") String recipientKey,
            @Param("family") String family,
            @Param("mode") String mode,
            @Param("payloadVersion") int payloadVersion,
            @Param("payload") String payload,
            @Param("occurredAt") OffsetDateTime occurredAt,
            @Param("dueAt") OffsetDateTime dueAt,
            @Param("expiresAt") OffsetDateTime expiresAt,
            @Param("now") OffsetDateTime now
    );

    Optional<TransactionalEmailDeliveryJpaEntity> findByTypeAndBusinessKeyAndRecipientKeyAndMode(
            TransactionalEmailType type,
            String businessKey,
            String recipientKey,
            EmailDeliveryMode mode
    );

    List<TransactionalEmailDeliveryJpaEntity> findByTypeAndRecipientKey(
            TransactionalEmailType type,
            String recipientKey
    );

    @Query(value = """
            SELECT COUNT(*) > 0 FROM transactional_email_delivery
            WHERE type = :type
              AND recipient_key = :recipientKey
              AND status = 'ACCEPTED'
              AND accepted_at >= :since
            """, nativeQuery = true)
    boolean existsRecentlyAccepted(
            @Param("type") String type,
            @Param("recipientKey") String recipientKey,
            @Param("since") OffsetDateTime since
    );
}
