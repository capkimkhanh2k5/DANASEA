package com.danasea.backend.modules.booking.infrastructure.persistence.repositories;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import com.danasea.backend.modules.booking.domain.models.BookingStatus;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingJpaEntity;

@Repository
public interface JpaBookingRepository extends JpaRepository<BookingJpaEntity, UUID> {

    Optional<BookingJpaEntity> findByIdAndCustomerId(UUID id, UUID customerId);

    @Query("SELECT DISTINCT b FROM BookingJpaEntity b LEFT JOIN FETCH b.items WHERE b.id = :id")
    Optional<BookingJpaEntity> findByIdWithItems(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT DISTINCT b FROM BookingJpaEntity b LEFT JOIN FETCH b.items WHERE b.id = :id")
    Optional<BookingJpaEntity> findByIdWithItemsForUpdate(@Param("id") UUID id);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE BookingItemJpaEntity i SET i.capacityReleased = true WHERE i.id IN :itemIds")
    int markCapacityNotCommitted(@Param("itemIds") Collection<UUID> itemIds);

    @EntityGraph(attributePaths = {"items"})
    @Query("SELECT b FROM BookingJpaEntity b WHERE b.customerId = :customerId AND (:status IS NULL OR b.status = :status)")
    Page<BookingJpaEntity> findByCustomerIdAndStatus(
            @Param("customerId") UUID customerId,
            @Param("status") BookingStatus status,
            Pageable pageable
    );

    @Query("SELECT b FROM BookingJpaEntity b WHERE b.status IN :statuses AND b.holdExpiresAt < :threshold")
    List<BookingJpaEntity> findExpiredHolds(
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("threshold") OffsetDateTime threshold
    );

    default List<BookingJpaEntity> findExpiredHolds(OffsetDateTime threshold) {
        return findExpiredHolds(List.of(BookingStatus.HOLD, BookingStatus.PENDING_PAYMENT), threshold);
    }
}

