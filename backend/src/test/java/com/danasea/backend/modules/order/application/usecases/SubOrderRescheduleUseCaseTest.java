package com.danasea.backend.modules.order.application.usecases;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.danasea.backend.modules.audit.application.services.AuditLogInternalService;
import com.danasea.backend.modules.booking.domain.models.*;
import com.danasea.backend.modules.booking.domain.ports.*;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.*;
import com.danasea.backend.modules.booking.infrastructure.persistence.mappers.BookingMapper;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.*;
import com.danasea.backend.modules.checkin.infrastructure.persistence.repositories.JpaCheckinTokenRepository;
import com.danasea.backend.modules.communication.application.usecases.SendNotificationUseCase;
import com.danasea.backend.modules.order.application.services.DisruptionDecisionService;
import com.danasea.backend.modules.order.application.services.RescheduleSupport;
import com.danasea.backend.modules.order.domain.models.*;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.*;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderDisruptionJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.*;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderDisruptionRepository;
import com.danasea.backend.modules.order.presentation.dtos.*;
import com.danasea.backend.modules.service.application.services.SlotAvailabilityCalculator;
import com.danasea.backend.modules.service.domain.models.*;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.*;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.*;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.modules.weather.infrastructure.persistence.entities.SafetyRuleEvaluationJpaEntity;
import com.danasea.backend.modules.weather.infrastructure.persistence.repositories.JpaSafetyRuleEvaluationRepository;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class SubOrderRescheduleUseCaseTest {
    final DisruptionDecisionService decisions =
            mock(DisruptionDecisionService.class);
    final JpaSubOrderDisruptionRepository disruptions =
            mock(JpaSubOrderDisruptionRepository.class);
    final JpaSubOrderRepository subs = mock(JpaSubOrderRepository.class);
    final JpaMasterOrderRepository orders = mock(JpaMasterOrderRepository.class);
    final JpaBookingItemRepository items = mock(JpaBookingItemRepository.class);
    final JpaBookingItemAllocationRepository allocations =
            mock(JpaBookingItemAllocationRepository.class);
    final JpaServiceSlotRepository slots = mock(JpaServiceSlotRepository.class);
    final JpaServiceSlotUnitRepository units = mock(JpaServiceSlotUnitRepository.class);
    final JpaServiceOptionRepository serviceOptions = mock(JpaServiceOptionRepository.class);
    final JpaServiceRepository services = mock(JpaServiceRepository.class);
    final JpaSubOrderRescheduleProposalRepository proposals =
            mock(JpaSubOrderRescheduleProposalRepository.class);
    final JpaSubOrderRescheduleHistoryRepository histories =
            mock(JpaSubOrderRescheduleHistoryRepository.class);
    final JpaSafetyRuleEvaluationRepository evaluations =
            mock(JpaSafetyRuleEvaluationRepository.class);
    final JpaCheckinTokenRepository tokens = mock(JpaCheckinTokenRepository.class);
    final AuditLogInternalService audits = mock(AuditLogInternalService.class);
    final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    final InventoryLockPort inventory = mock(InventoryLockPort.class);
    final ServiceSlotPort capacity = mock(ServiceSlotPort.class);
    final RescheduleSupport support = mock(RescheduleSupport.class);
    final VendorInternalApi vendors = mock(VendorInternalApi.class);
    final SendNotificationUseCase notifications = mock(SendNotificationUseCase.class);
    final SlotAvailabilityCalculator availability = mock(SlotAvailabilityCalculator.class);
    final UUID user = UUID.randomUUID(),
            subId = UUID.randomUUID(),
            orderId = UUID.randomUUID(),
            vendorId = UUID.randomUUID(),
            serviceId = UUID.randomUUID();
    final UUID oldId = UUID.randomUUID(), newId = UUID.randomUUID(), itemId = UUID.randomUUID();
    SubOrderJpaEntity sub;
    MasterOrderJpaEntity order;
    BookingItemJpaEntity item;
    ServiceSlotJpaEntity oldSlot, target;
    ConfirmSubOrderRescheduleUseCase confirm;
    GetRescheduleOptionsUseCase getOptions;
    CreateRescheduleProposalUseCase create;

    @BeforeEach
    void setup() {
        var pendingDecision = new SubOrderDisruptionJpaEntity();
        pendingDecision.setStatus("AWAITING_DECISION");
        when(decisions.record(any(), any(), any(), any(), any())).thenReturn(pendingDecision);

        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                user.toString(),
                                "n/a",
                                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
        order = new MasterOrderJpaEntity();
        order.setId(orderId);
        order.setCustomerId(user);
        order.setStatus(MasterOrderStatus.PAID);
        order.setPaymentStatus(PaymentOrderStatus.PAID);
        sub = new SubOrderJpaEntity();
        sub.setId(subId);
        sub.setMasterOrderId(orderId);
        sub.setBookingItemId(itemId);
        sub.setVendorId(vendorId);
        sub.setServiceId(serviceId);
        sub.setSlotId(oldId);
        sub.setQuantity(4);
        sub.setStatus(SubOrderStatus.CONFIRMED);
        sub.setRescheduleVersion(0L);
        oldSlot = slot(oldId, 10, 4);
        target = slot(newId, 10, 3);
        item =
                BookingItemJpaEntity.builder()
                        .slotId(oldId)
                        .serviceId(serviceId)
                        .vendorId(vendorId)
                        .quantity(4)
                        .pricingUnit("PER_PERSON")
                        .bookingDate(oldSlot.getDate())
                        .bookingTime(oldSlot.getStartTime())
                        .price(BigDecimal.TEN)
                        .build();
        item.setId(itemId);
        when(support.lock(subId)).thenReturn(new RescheduleSupport.LockedOrder(order, sub));
        when(subs.findById(subId)).thenReturn(Optional.of(sub));
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(slots.findByIdForUpdate(oldId)).thenReturn(Optional.of(oldSlot));
        when(slots.findByIdForUpdate(newId)).thenReturn(Optional.of(target));
        when(slots.findById(oldId)).thenReturn(Optional.of(oldSlot));
        when(slots.findById(newId)).thenReturn(Optional.of(target));
        when(items.findById(itemId)).thenReturn(Optional.of(item));
        when(capacity.findSlotDetails(newId))
                .thenAnswer(
                        i ->
                                Optional.of(
                                        SlotValidationDetails.builder()
                                                .slotId(newId)
                                                .vendorId(vendorId)
                                                .serviceId(serviceId)
                                                .servicePublished(true)
                                                .capacity(target.getCapacity())
                                                .bookedCount(target.getBookedCount())
                                                .units(List.of())
                                                .build()));
        when(slots.decrementBookedCount(eq(oldId), anyInt())).thenReturn(1);
        var alert = new SafetyRuleEvaluationJpaEntity();
        alert.setIsSafe(false);
        alert.setAlertLevel("RED");
        alert.setStatus("AWAITING_ADMIN_RESOLUTION");
        when(evaluations.findTopBySlotIdOrderByEvaluatedAtDesc(oldId))
                .thenReturn(Optional.of(alert));
        confirm =
                new ConfirmSubOrderRescheduleUseCase(
                        subs,
                        items,
                        slots,
                        units,
                        proposals,
                        histories,
                        evaluations,
                        tokens,
                        audits,
                        events,
                        inventory,
                        capacity,
                        support,
                        new BookingMapper(), decisions, disruptions);
        getOptions =
                new GetRescheduleOptionsUseCase(
                        subs, orders, slots, services, items, proposals, evaluations, availability, disruptions);
        create =
                new CreateRescheduleProposalUseCase(
                        slots, proposals, vendors, audits, support, events, capacity, decisions);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.clearSynchronization();
    }

    ServiceSlotJpaEntity slot(UUID id, int cap, int booked) {
        var s = new ServiceSlotJpaEntity();
        s.setId(id);
        s.setServiceId(serviceId);
        s.setDate(LocalDate.now(Booking.VIETNAM_ZONE).plusDays(2));
        s.setStartTime(LocalTime.NOON);
        s.setEndTime(LocalTime.of(14, 0));
        s.setCapacity(cap);
        s.setBookedCount(booked);
        s.setInventoryType(InventoryType.PERSON_LIMIT);
        s.setStatus(SlotStatus.OPEN);
        return s;
    }

    ConfirmRescheduleRequest request() {
        return new ConfirmRescheduleRequest(newId, null, 0L);
    }

    @Test
    void movesInventoryVersionAndQr() {
        var previousQr = sub.getQrSecret();
        var result = confirm.execute(subId, request(), "reschedule-1");
        assertEquals(1L, result.rescheduleVersion());
        assertEquals(newId, sub.getSlotId());
        assertNotEquals(previousQr, sub.getQrSecret());
        verify(capacity).commitCapacityBatch(anyList());
        verify(slots).decrementBookedCount(oldId, 4);
        verify(tokens).deleteBySubOrderIdAndUsedAtIsNull(subId);
        verify(inventory).releaseHolds(any(), anyList());
        assertEquals(BigDecimal.ZERO, result.additionalAmountCharged());
    }

    @Test
    void passesRemainingCapacityToRedis() {
        confirm.execute(subId, request(), "reschedule-1");
        var c = ArgumentCaptor.forClass(List.class);
        verify(inventory).acquireHolds(any(), c.capture(), any());
        assertEquals(7, ((InventoryLockItem) c.getValue().getFirst()).getMaxCapacity());
    }

    @Test
    void usesRedisAllocationWithoutReplanning() {
        target.setInventoryType(InventoryType.SHARED_CAPACITY_UNITS);
        oldSlot.setInventoryType(InventoryType.SHARED_CAPACITY_UNITS);
        item.addAllocation(
                BookingItemAllocationJpaEntity.builder()
                        .slotId(oldId)
                        .unitNumber(1)
                        .allocatedSeats(4)
                        .isPrivateLock(false)
                        .build());
        when(units.decrementUnitBookedCount(oldId, 1, 4)).thenReturn(1);
        doAnswer(
                        inv -> {
                            List<InventoryLockItem> holds = inv.getArgument(1);
                            holds.getFirst()
                                    .setAllocations(
                                            List.of(
                                                    BookingItemAllocation.builder()
                                                            .slotId(newId)
                                                            .unitNumber(2)
                                                            .allocatedSeats(4)
                                                            .isPrivateLock(false)
                                                            .build()));
                            return null;
                        })
                .when(inventory)
                .acquireHolds(any(), anyList(), any());
        confirm.execute(subId, request(), "reschedule-1");
        assertEquals(2, item.getAllocations().getFirst().getUnitNumber());
        var c = ArgumentCaptor.forClass(List.class);
        verify(capacity).commitCapacityBatch(c.capture());
        assertEquals(
                2,
                ((BookingItem) c.getValue().getFirst())
                        .getAllocations()
                        .getFirst()
                        .getUnitNumber());
        verifyNoInteractions(allocations);
    }

    @Test
    void releasesHoldAfterCommitOnly() {
        TransactionSynchronizationManager.initSynchronization();
        confirm.execute(subId, request(), "reschedule-1");
        verify(inventory, never()).releaseHolds(any(), anyList());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        verify(inventory).releaseHolds(any(), anyList());
    }

    @Test
    void releasesHoldOnRollback() {
        TransactionSynchronizationManager.initSynchronization();
        doThrow(new IllegalStateException("DB failed"))
                .when(capacity)
                .commitCapacityBatch(anyList());
        assertThrows(
                IllegalStateException.class,
                () -> confirm.execute(subId, request(), "reschedule-1"));
        verify(inventory, never()).releaseHolds(any(), anyList());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(inventory).releaseHolds(any(), anyList());
    }

    @Test
    void keepsPackageSnapshotAndSplitConsent() {
        item.setPricingUnit("PER_PACKAGE");
        item.setQuantity(1);
        item.setMaxPaxPerPackage(10);
        item.setParticipantsCount(8);
        item.setAllowSplit(true);
        target.setInventoryType(InventoryType.SHARED_CAPACITY_UNITS);
        confirm.execute(subId, request(), "reschedule-1");
        var c = ArgumentCaptor.forClass(List.class);
        verify(inventory).acquireHolds(any(), c.capture(), any());
        var hold = (InventoryLockItem) c.getValue().getFirst();
        assertEquals(10, hold.getPaxPerPackage());
        assertTrue(hold.isAllowSplit());
        assertEquals(OptionType.PRIVATE, hold.getOptionType());
        verifyNoInteractions(serviceOptions);
    }

    @Test
    void changedVersionConflicts() {
        sub.setRescheduleVersion(1L);
        assertThrows(
                ResponseStatusException.class,
                () -> confirm.execute(subId, request(), "reschedule-1"));
        verifyNoInteractions(inventory);
    }

    @Test
    void checkedInOrderRejected() {
        sub.setCheckedInAt(OffsetDateTime.now());
        assertThrows(
                ResponseStatusException.class,
                () -> confirm.execute(subId, request(), "reschedule-1"));
    }

    @Test
    void unpaidOrderRejected() {
        order.setPaymentStatus(PaymentOrderStatus.UNPAID);
        assertThrows(
                ResponseStatusException.class,
                () -> confirm.execute(subId, request(), "reschedule-1"));
    }

    @Test
    void sameSlotRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        confirm.execute(
                                subId,
                                new ConfirmRescheduleRequest(oldId, null, 0L),
                                "reschedule-1"));
    }

    @Test
    void unsafeTargetRejected() {
        var a = new SafetyRuleEvaluationJpaEntity();
        a.setIsSafe(false);
        when(evaluations.findTopBySlotIdOrderByEvaluatedAtDesc(newId)).thenReturn(Optional.of(a));
        assertThrows(
                ResponseStatusException.class,
                () -> confirm.execute(subId, request(), "reschedule-1"));
    }

    @Test
    void differentServiceRejected() {
        target.setServiceId(UUID.randomUUID());
        assertThrows(
                ResponseStatusException.class,
                () -> confirm.execute(subId, request(), "reschedule-1"));
    }

    @Test
    void missingKeyRejected() {
        assertThrows(IllegalArgumentException.class, () -> confirm.execute(subId, request(), null));
    }

    @Test
    void ownershipCheckedBeforeIdempotencyReplay() {
        order.setCustomerId(UUID.randomUUID());
        assertThrows(
                AccessDeniedException.class,
                () -> confirm.execute(subId, request(), "reschedule-1"));
        verifyNoInteractions(histories);
    }

    @Test
    void replayUsesCommittedVersionEvenAfterLaterMove() {
        var h =
                SubOrderRescheduleHistoryJpaEntity.builder()
                        .subOrderId(subId)
                        .fromSlotId(oldId)
                        .toSlotId(newId)
                        .rescheduleVersion(1L)
                        .payloadHash(DigestUtils.sha256Hex(newId + ":null:0"))
                        .build();
        when(histories.findBySubOrderIdAndIdempotencyKey(subId, "reschedule-1"))
                .thenReturn(Optional.of(h));
        sub.setRescheduleVersion(8L);
        assertEquals(1L, confirm.execute(subId, request(), "reschedule-1").rescheduleVersion());
        verifyNoInteractions(inventory);
    }

    @Test
    void replayChangedPayloadRejected() {
        when(histories.findBySubOrderIdAndIdempotencyKey(subId, "reschedule-1"))
                .thenReturn(
                        Optional.of(
                                SubOrderRescheduleHistoryJpaEntity.builder()
                                        .payloadHash("other")
                                        .build()));
        assertThrows(
                ResponseStatusException.class,
                () -> confirm.execute(subId, request(), "reschedule-1"));
    }

    @Test
    void optionsUseAvailabilityIncludingHolds() {
        var service = new ServiceJpaEntity();
        service.setId(serviceId);
        service.setStatus(ServiceStatus.PUBLISHED);
        service.setName("Test");
        when(services.findById(serviceId)).thenReturn(Optional.of(service));
        when(slots.findByServiceIdAndDateBetweenOrderByDateAscStartTimeAsc(
                        eq(serviceId), any(), any()))
                .thenReturn(List.of(target));
        when(availability.evaluate(any(), any()))
                .thenReturn(new SlotAvailabilityCalculator.Availability(2, 2));
        var result = getOptions.execute(subId);
        assertEquals(2, result.options().getFirst().availableCapacity());
        assertFalse(result.options().getFirst().canAccommodate());
    }

    @Test
    void optionsReflectDisruptionEligibilityReason() {
        var service = new ServiceJpaEntity();
        service.setId(serviceId);
        service.setStatus(ServiceStatus.PUBLISHED);
        service.setName("Test");
        when(services.findById(serviceId)).thenReturn(Optional.of(service));
        when(slots.findByServiceIdAndDateBetweenOrderByDateAscStartTimeAsc(
                        eq(serviceId), any(), any()))
                .thenReturn(List.of(target));
        when(availability.evaluate(any(), any()))
                .thenReturn(new SlotAvailabilityCalculator.Availability(10, 10));
        when(evaluations.findTopBySlotIdOrderByEvaluatedAtDesc(any())).thenReturn(Optional.empty());
        var disruption = new SubOrderDisruptionJpaEntity();
        disruption.setReason(RefundReason.VENDOR_FAULT);
        disruption.setDecisionDeadline(OffsetDateTime.now().plusHours(20));
        when(disruptions.findBySubOrderIdAndStatus(eq(subId), eq(DisruptionDecisionService.AWAITING)))
                .thenReturn(Optional.of(disruption));
        var result = getOptions.execute(subId);
        assertTrue(result.rescheduleAllowed());
        assertEquals("VENDOR_FAULT", result.eligibilityReason());
        assertEquals(RefundReason.VENDOR_FAULT, result.disruptionReason());
        assertTrue(result.fullRefundAvailable());
    }

    void vendor() {
        var v = new Vendor();
        v.setId(vendorId);
        when(vendors.findByUserId(user)).thenReturn(Optional.of(v));
        when(proposals.save(any()))
                .thenAnswer(
                        i -> {
                            var p = (SubOrderRescheduleProposalJpaEntity) i.getArgument(0);
                            p.setId(UUID.randomUUID());
                            return p;
                        });
    }

    @Test
    void proposalDoesNotReserveInventoryAndDefersNotification() {
        vendor();
        var result =
                create.execute(
                        subId,
                        new CreateRescheduleProposalRequest(
                                newId, "Maintenance", "OPERATIONAL", null));
        assertEquals("PENDING", result.status());
        verifyNoInteractions(inventory, notifications);
        verify(events).publishEvent(any(Object.class));
    }

    @Test
    void proposalExpiryForNearDepartureRemainsFuture() {
        vendor();
        var nearFuture = LocalDateTime.now(Booking.VIETNAM_ZONE).plusMinutes(30);
        target.setDate(nearFuture.toLocalDate());
        target.setStartTime(nearFuture.toLocalTime());
        var result =
                create.execute(
                        subId,
                        new CreateRescheduleProposalRequest(newId, "Weather", "WEATHER", null));
        assertTrue(result.expiresAt().isAfter(OffsetDateTime.now()));
    }

    @Test
    void invalidReasonRejected() {
        vendor();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        create.execute(
                                subId,
                                new CreateRescheduleProposalRequest(
                                        newId, "Maintenance", "INVALID", null)));
    }

    @Test
    void wrongVendorRejected() {
        vendor();
        sub.setVendorId(UUID.randomUUID());
        assertThrows(
                AccessDeniedException.class,
                () ->
                        create.execute(
                                subId,
                                new CreateRescheduleProposalRequest(
                                        newId, "Maintenance", "OPERATIONAL", null)));
    }
}
