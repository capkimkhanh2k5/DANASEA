package com.danasea.backend.modules.order.application.usecases;

import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.booking.domain.exceptions.InsufficientInventoryException;
import com.danasea.backend.modules.booking.domain.models.InventoryLockItem;
import com.danasea.backend.modules.booking.domain.ports.InventoryLockPort;
import com.danasea.backend.modules.booking.domain.ports.ServiceSlotPort;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingItemAllocationJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.mappers.BookingMapper;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingItemRepository;
import com.danasea.backend.modules.checkin.infrastructure.persistence.repositories.JpaCheckinTokenRepository;
import com.danasea.backend.modules.communication.application.email.EmailRequest;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailPort;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;
import com.danasea.backend.modules.order.application.services.DisruptionDecisionService;
import com.danasea.backend.modules.order.application.services.RescheduleSupport;
import com.danasea.backend.modules.order.domain.events.SubOrderRescheduledEvent;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderRescheduleHistoryJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderRescheduleProposalJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderDisruptionRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRescheduleHistoryRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRescheduleProposalRepository;
import com.danasea.backend.modules.order.presentation.dtos.ConfirmRescheduleRequest;
import com.danasea.backend.modules.order.presentation.dtos.SubOrderRescheduleResponse;
import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.domain.models.OptionType;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotUnitRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.modules.weather.infrastructure.persistence.repositories.JpaSafetyRuleEvaluationRepository;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import com.danasea.backend.shared.i18n.LocalizedMessageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmSubOrderRescheduleUseCase {

    private final JpaSubOrderRepository subOrderRepository;
    private final JpaBookingItemRepository bookingItemRepository;
    private final JpaServiceSlotRepository slotRepository;
    private final JpaServiceSlotUnitRepository slotUnitRepository;
    private final JpaSubOrderRescheduleProposalRepository proposalRepository;
    private final JpaSubOrderRescheduleHistoryRepository historyRepository;
    private final JpaSafetyRuleEvaluationRepository evaluationRepository;
    private final JpaCheckinTokenRepository checkinTokenRepository;
    private final AuditLogInternalApi auditLogInternalService;
    private final ApplicationEventPublisher eventPublisher;

    private final InventoryLockPort inventoryLockPort;
    private final ServiceSlotPort serviceSlotPort;
    private final RescheduleSupport support;
    private final BookingMapper bookingMapper;
    private final DisruptionDecisionService decisions;
    private final JpaSubOrderDisruptionRepository disruptions;

    @Autowired(required = false)
    private TransactionalEmailPort transactionalEmailPort;

    @Autowired(required = false)
    private VendorInternalApi vendorInternalApi;

    @Transactional(timeout = 30)
    public SubOrderRescheduleResponse execute(
            UUID subOrderId, ConfirmRescheduleRequest request, String key) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,100}")) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must contain 8 to 100 valid characters.");
        }
        UUID userId =
                SecurityUtils.getCurrentUserId()
                        .orElseThrow(() -> new AccessDeniedException("User is not authenticated."));
        var locked = support.lock(subOrderId);
        var master = locked.master();
        var sub = locked.sub();
        RescheduleSupport.owner(master, userId);
        String hash =
                DigestUtils.sha256Hex(
                        request.targetSlotId()
                                + ":"
                                + request.proposalId()
                                + ":"
                                + request.expectedVersion());
        var previous = historyRepository.findBySubOrderIdAndIdempotencyKey(subOrderId, key);
        if (previous.isPresent()) {
            if (!hash.equals(previous.get().getPayloadHash())) {
                throw RescheduleSupport.conflict(
                        "Idempotency-Key was already used for a different request.");
            }
            return response(previous.get());
        }
        RescheduleSupport.eligible(master, sub);
        decisions.assertNoRefund(subOrderId);
        var disruption =
                disruptions.findBySubOrderIdAndStatus(
                        subOrderId, DisruptionDecisionService.AWAITING);

        if (!Objects.equals(sub.getRescheduleVersion(), request.expectedVersion())) {
            throw RescheduleSupport.conflict("Order version has changed. Refresh before retrying.");
        }
        if (Objects.equals(sub.getSlotId(), request.targetSlotId())) {
            throw new IllegalArgumentException("Target slot must differ from the current slot.");
        }
        serviceSlotPort.lockSlotsForUpdate(
                Stream.of(sub.getSlotId(), request.targetSlotId()).sorted().toList());
        var oldSlot =
                slotRepository
                        .findByIdForUpdate(sub.getSlotId())
                        .orElseThrow(() -> RescheduleSupport.conflict("Current slot not found."));
        var target =
                slotRepository
                        .findByIdForUpdate(request.targetSlotId())
                        .orElseThrow(() -> RescheduleSupport.conflict("Target slot not found."));
        if (!RescheduleSupport.upcoming(oldSlot)
                || !RescheduleSupport.upcoming(target)
                || target.getStatus() != SlotStatus.OPEN
                || !Objects.equals(target.getServiceId(), sub.getServiceId())) {
            throw RescheduleSupport.conflict(
                    "Target must be an upcoming open slot of the same service.");
        }
        var details =
                serviceSlotPort
                        .findSlotDetails(target.getId())
                        .orElseThrow(() -> RescheduleSupport.conflict("Target slot unavailable."));
        if (!details.isServicePublished()
                || !Objects.equals(details.getVendorId(), sub.getVendorId())) {
            throw RescheduleSupport.conflict("Service is unavailable for rescheduling.");
        }
        var targetAlert =
                evaluationRepository.findTopBySlotIdOrderByEvaluatedAtDesc(target.getId());
        if (targetAlert
                .filter(a -> Boolean.FALSE.equals(a.getIsSafe()) || "RED".equals(a.getAlertLevel()))
                .isPresent()) {
            throw RescheduleSupport.conflict("Target slot has an unsafe weather assessment.");
        }
        var decisionDeadline =
                disruption
                        .map(d -> d.getDecisionDeadline())
                        .orElseGet(
                                () ->
                                        oldSlot.getDate()
                                                .atTime(oldSlot.getStartTime())
                                                .atOffset(ZoneOffset.ofHours(7))
                                                .minusHours(12));
        SubOrderRescheduleProposalJpaEntity proposal = null;
        if (request.proposalId() != null) {
            proposal =
                    proposalRepository
                            .findByIdAndSubOrderId(request.proposalId(), subOrderId)
                            .orElseThrow(
                                    () ->
                                            RescheduleSupport.conflict(
                                                    "Reschedule proposal not found."));
        } else if (disruption.isEmpty()
                && !evaluationRepository
                        .findTopBySlotIdOrderByEvaluatedAtDesc(oldSlot.getId())
                        .map(RescheduleSupport::weatherAlert)
                        .orElse(false)) {
            proposal =
                    proposalRepository
                            .findBySubOrderIdAndStatusAndExpiresAtAfter(
                                    subOrderId, "PENDING", OffsetDateTime.now())
                            .stream()
                            .filter(p -> p.getProposedSlotId().equals(target.getId()))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            RescheduleSupport.conflict(
                                                    "No active weather alert or vendor proposal"
                                                            + " permits this change."));
        }
        if (proposal != null
                && (!"PENDING".equals(proposal.getStatus())
                        || !proposal.getExpiresAt().isAfter(OffsetDateTime.now())
                        || !target.getId().equals(proposal.getProposedSlotId())
                        || !sub.getVendorId().equals(proposal.getVendorId()))) {
            throw RescheduleSupport.conflict(
                    "Reschedule proposal is expired or does not match the target.");
        }
        boolean unsafeWeather =
                evaluationRepository
                        .findTopBySlotIdOrderByEvaluatedAtDesc(oldSlot.getId())
                        .filter(
                                a ->
                                        RescheduleSupport.weatherAlert(a)
                                                && (Boolean.FALSE.equals(a.getIsSafe())
                                                        || "RED".equals(a.getAlertLevel())))
                        .isPresent();
        if ((disruption.isPresent() || proposal != null || unsafeWeather)
                && !decisionDeadline.isAfter(OffsetDateTime.now()))
            throw RescheduleSupport.conflict(
                    "The disruption decision deadline has passed; a full refund is due.");
        var item =
                bookingItemRepository
                        .findById(sub.getBookingItemId())
                        .orElseThrow(() -> RescheduleSupport.conflict("Booking item not found."));
        boolean privatePackage = "PER_PACKAGE".equals(item.getPricingUnit());
        if (privatePackage && target.getInventoryType() != InventoryType.SHARED_CAPACITY_UNITS) {
            throw RescheduleSupport.conflict("Private packages require whole capacity units.");
        }
        var hold =
                InventoryLockItem.builder()
                        .slotId(target.getId())
                        .quantity(item.getQuantity())
                        .maxCapacity(details.getAvailableCapacity())
                        .inventoryType(target.getInventoryType())
                        .optionType(privatePackage ? OptionType.PRIVATE : OptionType.SHARED)
                        .paxPerPackage(RescheduleSupport.maxPax(item))
                        .allowSplit(item.isAllowSplit())
                        .units(
                                details.getUnits().stream()
                                        .map(
                                                u ->
                                                        new InventoryLockItem.UnitLockInfo(
                                                                u.getUnitNumber(),
                                                                u.getCapacity(),
                                                                u.getBookedCount() == null
                                                                        ? 0
                                                                        : u.getBookedCount()))
                                        .toList())
                        .build();
        UUID holdId = UUID.randomUUID();
        try {
            inventoryLockPort.acquireHolds(holdId, List.of(hold), Duration.ofMinutes(2));
        } catch (InsufficientInventoryException ex) {
            throw RescheduleSupport.conflict("Target slot has insufficient remaining capacity.");
        }
        boolean synchronizedTransaction =
                TransactionSynchronizationManager.isSynchronizationActive();
        if (synchronizedTransaction) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            releaseHold(holdId, hold);
                        }
                    });
        }
        try {
            var newItem = bookingMapper.toDomainItem(item);
            newItem.setSlotId(target.getId());
            newItem.setAllocations(hold.getAllocations());
            serviceSlotPort.commitCapacityBatch(List.of(newItem));
            int oldSeats = item.getQuantity();
            if (oldSlot.getInventoryType() == InventoryType.SHARED_CAPACITY_UNITS) {
                oldSeats = 0;
                for (var allocation : item.getAllocations()) {
                    if (slotUnitRepository.decrementUnitBookedCount(
                                    oldSlot.getId(),
                                    allocation.getUnitNumber(),
                                    allocation.getAllocatedSeats())
                            != 1) {
                        throw RescheduleSupport.conflict("Original unit inventory changed.");
                    }
                    oldSeats += allocation.getAllocatedSeats();
                }
            }
            if (oldSeats <= 0
                    || slotRepository.decrementBookedCount(oldSlot.getId(), oldSeats) != 1) {
                throw RescheduleSupport.conflict("Original slot inventory changed.");
            }
            item.setSlotId(target.getId());
            item.setBookingDate(target.getDate());
            item.setBookingTime(target.getStartTime());
            item.getAllocations().clear();
            for (var allocation : hold.getAllocations()) {
                item.addAllocation(
                        BookingItemAllocationJpaEntity.builder()
                                .slotId(target.getId())
                                .unitNumber(allocation.getUnitNumber())
                                .allocatedSeats(allocation.getAllocatedSeats())
                                .isPrivateLock(allocation.getIsPrivateLock())
                                .build());
            }
            bookingItemRepository.save(item);
            if (sub.getOriginalSlotId() == null) sub.setOriginalSlotId(oldSlot.getId());
            sub.setSlotId(target.getId());
            sub.setRescheduleVersion(sub.getRescheduleVersion() + 1);
            sub.setRescheduledAt(OffsetDateTime.now());
            sub.setQrSecret(UUID.randomUUID());
            subOrderRepository.save(sub);
            decisions.completeReschedule(subOrderId);
            if (proposal != null) {
                proposal.setStatus("ACCEPTED");
                proposalRepository.save(proposal);
            }
            for (var pending :
                    proposalRepository.findBySubOrderIdAndStatus(subOrderId, "PENDING")) {
                pending.setStatus("SUPERSEDED");
                proposalRepository.save(pending);
            }
            checkinTokenRepository.deleteBySubOrderIdAndUsedAtIsNull(subOrderId);
            var history =
                    SubOrderRescheduleHistoryJpaEntity.builder()
                            .subOrderId(subOrderId)
                            .bookingItemId(item.getId())
                            .proposalId(proposal == null ? null : proposal.getId())
                            .fromSlotId(oldSlot.getId())
                            .toSlotId(target.getId())
                            .fromBookingDate(oldSlot.getDate())
                            .fromBookingTime(oldSlot.getStartTime())
                            .toBookingDate(target.getDate())
                            .toBookingTime(target.getStartTime())
                            .performedBy(userId)
                            .reasonType(
                                    disruption
                                            .map(d -> d.getReason().name())
                                            .orElse(
                                                    proposal == null
                                                            ? "WEATHER_ALERT"
                                                            : "VENDOR_PROPOSAL"))
                            .reason(
                                    disruption
                                            .map(d -> d.getDetails())
                                            .orElse(
                                                    proposal == null
                                                            ? "Weather disruption"
                                                            : proposal.getReason()))
                            .idempotencyKey(key)
                            .payloadHash(hash)
                            .rescheduleVersion(sub.getRescheduleVersion())
                            .build();
            history.setId(UUID.randomUUID());
            history.setCreatedAt(sub.getRescheduledAt());
            history.markNew();
            historyRepository.saveAndFlush(history);
            auditLogInternalService.recordTransactionalAuditLog(
                    userId,
                    "RESCHEDULE_SUB_ORDER",
                    "SUB_ORDER",
                    subOrderId,
                    "{\"fromSlotId\":\""
                            + oldSlot.getId()
                            + "\",\"toSlotId\":\""
                            + target.getId()
                            + "\",\"version\":"
                            + sub.getRescheduleVersion()
                            + "}");

            if (transactionalEmailPort != null) {
                OffsetDateTime emailNow = OffsetDateTime.now(ZoneOffset.ofHours(7));
                transactionalEmailPort.enqueue(new EmailRequest(
                        TransactionalEmailType.TRIP_RESCHEDULED,
                        "reschedule:" + history.getId(),
                        "USER:" + master.getCustomerId(),
                        emailNow,
                        emailNow,
                        emailNow.plusDays(7),
                        1,
                        Map.of(
                                "historyId", history.getId().toString(),
                                "subOrderId", subOrderId.toString(),
                                "serviceName", "Dịch vụ Danasea",
                                "newDate", target.getDate().toString(),
                                "newTime", target.getStartTime().toString()
                        )
                ));
                if (vendorInternalApi != null && sub.getVendorId() != null) {
                    UUID vendorOwnerUserId = vendorInternalApi.findById(sub.getVendorId())
                            .map(Vendor::getUserId)
                            .orElse(null);
                    if (vendorOwnerUserId != null) {
                        transactionalEmailPort.enqueue(new EmailRequest(
                                TransactionalEmailType.TRIP_RESCHEDULED,
                                "reschedule:" + history.getId(),
                                "USER:" + vendorOwnerUserId,
                                emailNow,
                                emailNow,
                                emailNow.plusDays(7),
                                1,
                                Map.of(
                                        "historyId", history.getId().toString(),
                                        "subOrderId", subOrderId.toString(),
                                        "serviceName", "Dịch vụ Danasea",
                                        "newDate", target.getDate().toString(),
                                        "newTime", target.getStartTime().toString(),
                                        "isVendor", true
                                )
                        ));
                    }
                }
            }

            eventPublisher.publishEvent(
                    new SubOrderRescheduledEvent(
                            history.getId(),
                            subOrderId,
                            master.getId(),
                            master.getCustomerId(),
                            sub.getVendorId(),
                            oldSlot.getId(),
                            target.getId(),
                            target.getDate(),
                            target.getStartTime(),
                            history.getReasonType(),
                            history.getReason(),
                            sub.getRescheduledAt()));
            return response(history);
        } finally {
            if (!synchronizedTransaction) releaseHold(holdId, hold);
        }
    }

    private SubOrderRescheduleResponse response(SubOrderRescheduleHistoryJpaEntity history) {
        return new SubOrderRescheduleResponse(
                history.getSubOrderId(),
                history.getBookingItemId(),
                history.getFromSlotId(),
                history.getToSlotId(),
                history.getToBookingDate(),
                history.getToBookingTime(),
                history.getRescheduleVersion(),
                BigDecimal.ZERO,
                "RESCHEDULED",
                history.getCreatedAt() == null
                        ? null
                        : history.getCreatedAt()
                                .withOffsetSameInstant(ZoneOffset.UTC)
                                .truncatedTo(ChronoUnit.MICROS),
                LocalizedMessageService.standalone().get("reschedule.completed"));
    }

    private void releaseHold(UUID id, InventoryLockItem hold) {
        try {
            inventoryLockPort.releaseHolds(id, List.of(hold));
        } catch (Exception ex) {
            log.warn("Temporary reschedule hold {} will expire: {}", id, ex.getMessage());
        }
    }
}
