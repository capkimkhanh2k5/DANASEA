package com.danasea.backend.modules.partner.application.services;

import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingItemJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingItemRepository;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRescheduleProposalRepository;
import com.danasea.backend.modules.partner.domain.exceptions.CustomerHasActiveObligationsException;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerPartnerEligibilityServiceTest {

    @Mock
    private JpaBookingRepository bookingRepository;

    @Mock
    private JpaBookingItemRepository bookingItemRepository;

    @Mock
    private JpaMasterOrderRepository masterOrderRepository;

    @Mock
    private JpaSubOrderRepository subOrderRepository;

    @Mock
    private JpaRefundRepository refundRepository;

    @Mock
    private JpaSubOrderRescheduleProposalRepository rescheduleProposalRepository;

    @Mock
    private JpaDisputeRepository disputeRepository;

    @Mock
    private JpaServiceSlotRepository serviceSlotRepository;

    private CustomerPartnerEligibilityService service;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        service = new CustomerPartnerEligibilityService(
                bookingRepository,
                bookingItemRepository,
                masterOrderRepository,
                subOrderRepository,
                refundRepository,
                rescheduleProposalRepository,
                disputeRepository,
                serviceSlotRepository
        );
        customerId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Validation succeeds when customer has no active obligations")
    void validateCustomerEligibility_WhenClean_Succeeds() {
        when(bookingRepository.hasActiveBookings(customerId)).thenReturn(false);
        when(masterOrderRepository.hasActiveMasterOrders(customerId)).thenReturn(false);
        when(subOrderRepository.hasActiveSubOrders(customerId)).thenReturn(false);
        when(refundRepository.hasUnresolvedRefunds(customerId)).thenReturn(false);
        when(rescheduleProposalRepository.hasPendingRescheduleProposals(customerId)).thenReturn(false);
        when(disputeRepository.hasActiveDisputes(customerId)).thenReturn(false);
        when(subOrderRepository.findCompletedSubOrdersByCustomerId(customerId)).thenReturn(List.of());

        assertThatCode(() -> service.validateCustomerEligibility(customerId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Throws exception when customer has active booking holds or pending payments")
    void validateCustomerEligibility_WhenActiveBooking_ThrowsException() {
        when(bookingRepository.hasActiveBookings(customerId)).thenReturn(true);

        assertThatThrownBy(() -> service.validateCustomerEligibility(customerId))
                .isInstanceOf(CustomerHasActiveObligationsException.class)
                .hasMessageContaining("active booking hold or pending payment");
    }

    @Test
    @DisplayName("Throws exception when customer has active master orders")
    void validateCustomerEligibility_WhenActiveMasterOrder_ThrowsException() {
        when(bookingRepository.hasActiveBookings(customerId)).thenReturn(false);
        when(masterOrderRepository.hasActiveMasterOrders(customerId)).thenReturn(true);

        assertThatThrownBy(() -> service.validateCustomerEligibility(customerId))
                .isInstanceOf(CustomerHasActiveObligationsException.class)
                .hasMessageContaining("orders currently being processed");
    }

    @Test
    @DisplayName("Throws exception when customer has active or incomplete sub-orders")
    void validateCustomerEligibility_WhenActiveSubOrder_ThrowsException() {
        when(bookingRepository.hasActiveBookings(customerId)).thenReturn(false);
        when(masterOrderRepository.hasActiveMasterOrders(customerId)).thenReturn(false);
        when(subOrderRepository.hasActiveSubOrders(customerId)).thenReturn(true);

        assertThatThrownBy(() -> service.validateCustomerEligibility(customerId))
                .isInstanceOf(CustomerHasActiveObligationsException.class)
                .hasMessageContaining("experiences that are not completed");
    }

    @Test
    @DisplayName("Throws exception when customer has unresolved refunds")
    void validateCustomerEligibility_WhenUnresolvedRefund_ThrowsException() {
        when(bookingRepository.hasActiveBookings(customerId)).thenReturn(false);
        when(masterOrderRepository.hasActiveMasterOrders(customerId)).thenReturn(false);
        when(subOrderRepository.hasActiveSubOrders(customerId)).thenReturn(false);
        when(refundRepository.hasUnresolvedRefunds(customerId)).thenReturn(true);

        assertThatThrownBy(() -> service.validateCustomerEligibility(customerId))
                .isInstanceOf(CustomerHasActiveObligationsException.class)
                .hasMessageContaining("refunds pending or unresolved");
    }

    @Test
    @DisplayName("Throws exception when customer has pending reschedule proposals")
    void validateCustomerEligibility_WhenPendingReschedule_ThrowsException() {
        when(bookingRepository.hasActiveBookings(customerId)).thenReturn(false);
        when(masterOrderRepository.hasActiveMasterOrders(customerId)).thenReturn(false);
        when(subOrderRepository.hasActiveSubOrders(customerId)).thenReturn(false);
        when(refundRepository.hasUnresolvedRefunds(customerId)).thenReturn(false);
        when(rescheduleProposalRepository.hasPendingRescheduleProposals(customerId)).thenReturn(true);

        assertThatThrownBy(() -> service.validateCustomerEligibility(customerId))
                .isInstanceOf(CustomerHasActiveObligationsException.class)
                .hasMessageContaining("pending reschedule requests or proposals");
    }

    @Test
    @DisplayName("Throws exception when customer has active disputes under review")
    void validateCustomerEligibility_WhenActiveDispute_ThrowsException() {
        when(bookingRepository.hasActiveBookings(customerId)).thenReturn(false);
        when(masterOrderRepository.hasActiveMasterOrders(customerId)).thenReturn(false);
        when(subOrderRepository.hasActiveSubOrders(customerId)).thenReturn(false);
        when(refundRepository.hasUnresolvedRefunds(customerId)).thenReturn(false);
        when(rescheduleProposalRepository.hasPendingRescheduleProposals(customerId)).thenReturn(false);
        when(disputeRepository.hasActiveDisputes(customerId)).thenReturn(true);

        assertThatThrownBy(() -> service.validateCustomerEligibility(customerId))
                .isInstanceOf(CustomerHasActiveObligationsException.class)
                .hasMessageContaining("open complaints or disputes under review");
    }

    @Test
    @DisplayName("Throws exception when customer has completed sub-order within 7-day dispute window")
    void validateCustomerEligibility_WhenCompletedOrderWithin7Days_ThrowsException() {
        when(bookingRepository.hasActiveBookings(customerId)).thenReturn(false);
        when(masterOrderRepository.hasActiveMasterOrders(customerId)).thenReturn(false);
        when(subOrderRepository.hasActiveSubOrders(customerId)).thenReturn(false);
        when(refundRepository.hasUnresolvedRefunds(customerId)).thenReturn(false);
        when(rescheduleProposalRepository.hasPendingRescheduleProposals(customerId)).thenReturn(false);
        when(disputeRepository.hasActiveDisputes(customerId)).thenReturn(false);

        UUID slotId = UUID.randomUUID();
        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(UUID.randomUUID());
        subOrder.setStatus(SubOrderStatus.COMPLETED);
        subOrder.setSlotId(slotId);

        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(LocalDate.now().minusDays(3)); // 3 days ago -> still within 7 days

        when(subOrderRepository.findCompletedSubOrdersByCustomerId(customerId)).thenReturn(List.of(subOrder));
        when(serviceSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.validateCustomerEligibility(customerId))
                .isInstanceOf(CustomerHasActiveObligationsException.class)
                .hasMessageContaining("7-day dispute period");
    }

    @Test
    @DisplayName("Validation succeeds when completed sub-order is older than 7 days")
    void validateCustomerEligibility_WhenCompletedOrderOlderThan7Days_Succeeds() {
        when(bookingRepository.hasActiveBookings(customerId)).thenReturn(false);
        when(masterOrderRepository.hasActiveMasterOrders(customerId)).thenReturn(false);
        when(subOrderRepository.hasActiveSubOrders(customerId)).thenReturn(false);
        when(refundRepository.hasUnresolvedRefunds(customerId)).thenReturn(false);
        when(rescheduleProposalRepository.hasPendingRescheduleProposals(customerId)).thenReturn(false);
        when(disputeRepository.hasActiveDisputes(customerId)).thenReturn(false);

        UUID slotId = UUID.randomUUID();
        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(UUID.randomUUID());
        subOrder.setStatus(SubOrderStatus.COMPLETED);
        subOrder.setSlotId(slotId);

        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(LocalDate.now().minusDays(8)); // 8 days ago -> expired dispute window

        when(subOrderRepository.findCompletedSubOrdersByCustomerId(customerId)).thenReturn(List.of(subOrder));
        when(serviceSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        assertThatCode(() -> service.validateCustomerEligibility(customerId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Throws exception when completed sub-order has no slot but createdAt is within 7 days")
    void validateCustomerEligibility_WhenNoSlotAndCreatedAtWithin7Days_ThrowsException() {
        when(bookingRepository.hasActiveBookings(customerId)).thenReturn(false);
        when(masterOrderRepository.hasActiveMasterOrders(customerId)).thenReturn(false);
        when(subOrderRepository.hasActiveSubOrders(customerId)).thenReturn(false);
        when(refundRepository.hasUnresolvedRefunds(customerId)).thenReturn(false);
        when(rescheduleProposalRepository.hasPendingRescheduleProposals(customerId)).thenReturn(false);
        when(disputeRepository.hasActiveDisputes(customerId)).thenReturn(false);

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(UUID.randomUUID());
        subOrder.setStatus(SubOrderStatus.COMPLETED);
        subOrder.setSlotId(null);
        subOrder.setCreatedAt(OffsetDateTime.now().minusDays(2)); // created 2 days ago

        when(subOrderRepository.findCompletedSubOrdersByCustomerId(customerId)).thenReturn(List.of(subOrder));

        assertThatThrownBy(() -> service.validateCustomerEligibility(customerId))
                .isInstanceOf(CustomerHasActiveObligationsException.class)
                .hasMessageContaining("7-day dispute period");
    }

    @Test
    @DisplayName("Throws exception when no slot, but checkedInAt is within 7 days")
    void validateCustomerEligibility_WhenNoSlot_CheckedInAtWithin7Days_ThrowsException() {
        when(bookingRepository.hasActiveBookings(customerId)).thenReturn(false);
        when(masterOrderRepository.hasActiveMasterOrders(customerId)).thenReturn(false);
        when(subOrderRepository.hasActiveSubOrders(customerId)).thenReturn(false);
        when(refundRepository.hasUnresolvedRefunds(customerId)).thenReturn(false);
        when(rescheduleProposalRepository.hasPendingRescheduleProposals(customerId)).thenReturn(false);
        when(disputeRepository.hasActiveDisputes(customerId)).thenReturn(false);

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(UUID.randomUUID());
        subOrder.setStatus(SubOrderStatus.COMPLETED);
        subOrder.setSlotId(null);
        subOrder.setCheckedInAt(OffsetDateTime.now().minusDays(3)); // checked in 3 days ago

        when(subOrderRepository.findCompletedSubOrdersByCustomerId(customerId)).thenReturn(List.of(subOrder));

        assertThatThrownBy(() -> service.validateCustomerEligibility(customerId))
                .isInstanceOf(CustomerHasActiveObligationsException.class)
                .hasMessageContaining("7-day dispute period");
    }

    @Test
    @DisplayName("Succeeds when no slot, but checkedInAt is older than 7 days")
    void validateCustomerEligibility_WhenNoSlot_CheckedInAtOlderThan7Days_Succeeds() {
        when(bookingRepository.hasActiveBookings(customerId)).thenReturn(false);
        when(masterOrderRepository.hasActiveMasterOrders(customerId)).thenReturn(false);
        when(subOrderRepository.hasActiveSubOrders(customerId)).thenReturn(false);
        when(refundRepository.hasUnresolvedRefunds(customerId)).thenReturn(false);
        when(rescheduleProposalRepository.hasPendingRescheduleProposals(customerId)).thenReturn(false);
        when(disputeRepository.hasActiveDisputes(customerId)).thenReturn(false);

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(UUID.randomUUID());
        subOrder.setStatus(SubOrderStatus.COMPLETED);
        subOrder.setSlotId(null);
        subOrder.setCheckedInAt(OffsetDateTime.now().minusDays(10)); // checked in 10 days ago

        when(subOrderRepository.findCompletedSubOrdersByCustomerId(customerId)).thenReturn(List.of(subOrder));

        assertThatCode(() -> service.validateCustomerEligibility(customerId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Throws exception when no slot and no checkin, but bookingItem bookingDate is within 7 days")
    void validateCustomerEligibility_WhenNoSlot_BookingDateWithin7Days_ThrowsException() {
        when(bookingRepository.hasActiveBookings(customerId)).thenReturn(false);
        when(masterOrderRepository.hasActiveMasterOrders(customerId)).thenReturn(false);
        when(subOrderRepository.hasActiveSubOrders(customerId)).thenReturn(false);
        when(refundRepository.hasUnresolvedRefunds(customerId)).thenReturn(false);
        when(rescheduleProposalRepository.hasPendingRescheduleProposals(customerId)).thenReturn(false);
        when(disputeRepository.hasActiveDisputes(customerId)).thenReturn(false);

        UUID itemId = UUID.randomUUID();
        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(UUID.randomUUID());
        subOrder.setStatus(SubOrderStatus.COMPLETED);
        subOrder.setSlotId(null);
        subOrder.setBookingItemId(itemId);

        BookingItemJpaEntity item = new BookingItemJpaEntity();
        item.setId(itemId);
        item.setBookingDate(LocalDate.now().minusDays(4));

        when(subOrderRepository.findCompletedSubOrdersByCustomerId(customerId)).thenReturn(List.of(subOrder));
        when(bookingItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.validateCustomerEligibility(customerId))
                .isInstanceOf(CustomerHasActiveObligationsException.class)
                .hasMessageContaining("7-day dispute period");
    }

    @Test
    @DisplayName("Succeeds when customer has full history (completed tours older than 7 days, resolved dispute, resolved refund)")
    void validateCustomerEligibility_WhenFullHistory_CompletedAndResolved_Succeeds() {
        // Customer has booking that is CONFIRMED (not active hold), MasterOrder COMPLETED, SubOrder COMPLETED > 7 days
        when(bookingRepository.hasActiveBookings(customerId)).thenReturn(false);
        when(masterOrderRepository.hasActiveMasterOrders(customerId)).thenReturn(false);
        when(subOrderRepository.hasActiveSubOrders(customerId)).thenReturn(false);
        when(refundRepository.hasUnresolvedRefunds(customerId)).thenReturn(false); // e.g. refunds were SUCCESS
        when(rescheduleProposalRepository.hasPendingRescheduleProposals(customerId)).thenReturn(false);
        when(disputeRepository.hasActiveDisputes(customerId)).thenReturn(false); // e.g. dispute was RESOLVED_REFUND

        UUID slotId = UUID.randomUUID();
        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(UUID.randomUUID());
        subOrder.setStatus(SubOrderStatus.COMPLETED);
        subOrder.setSlotId(slotId);

        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(LocalDate.now().minusDays(15)); // completed 15 days ago

        when(subOrderRepository.findCompletedSubOrdersByCustomerId(customerId)).thenReturn(List.of(subOrder));
        when(serviceSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        assertThatCode(() -> service.validateCustomerEligibility(customerId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Throws IllegalArgumentException when customerId is null")
    void validateCustomerEligibility_WhenNullCustomerId_ThrowsException() {
        assertThatThrownBy(() -> service.validateCustomerEligibility(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
