package com.danasea.backend.modules.partner.application.services;

import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingItemJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingItemRepository;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRescheduleProposalRepository;
import com.danasea.backend.modules.partner.domain.exceptions.CustomerHasActiveObligationsException;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerPartnerEligibilityService {

    private final JpaBookingRepository bookingRepository;
    private final JpaBookingItemRepository bookingItemRepository;
    private final JpaMasterOrderRepository masterOrderRepository;
    private final JpaSubOrderRepository subOrderRepository;
    private final JpaRefundRepository refundRepository;
    private final JpaSubOrderRescheduleProposalRepository rescheduleProposalRepository;
    private final JpaDisputeRepository disputeRepository;
    private final JpaServiceSlotRepository serviceSlotRepository;

    @Transactional(readOnly = true)
    public void validateCustomerEligibility(UUID customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID cannot be null");
        }

        // 1. Check active booking holds or pending payments
        if (bookingRepository.hasActiveBookings(customerId)) {
            log.warn("Customer {} has active booking holds or pending payments", customerId);
            throw new CustomerHasActiveObligationsException(
                    "Customer has active booking hold or pending payment");
        }

        // 2. Check active master orders (not COMPLETED or CANCELLED)
        if (masterOrderRepository.hasActiveMasterOrders(customerId)) {
            log.warn("Customer {} has active or incomplete master orders", customerId);
            throw new CustomerHasActiveObligationsException(
                    "Customer has orders currently being processed or pending payment");
        }

        // 3. Check active sub-orders (CONFIRMED, CHECKED_IN, IN_PROGRESS, PENDING)
        if (subOrderRepository.hasActiveSubOrders(customerId)) {
            log.warn("Customer {} has sub-orders not yet completed or terminal", customerId);
            throw new CustomerHasActiveObligationsException(
                    "Customer has experiences that are not completed (confirmed, checked-in, or in progress)");
        }

        // 4. Check unresolved refunds
        if (refundRepository.hasUnresolvedRefunds(customerId)) {
            log.warn("Customer {} has pending or unresolved refunds", customerId);
            throw new CustomerHasActiveObligationsException(
                    "Customer has refunds pending or unresolved");
        }

        // 5. Check pending reschedule proposals
        if (rescheduleProposalRepository.hasPendingRescheduleProposals(customerId)) {
            log.warn("Customer {} has pending reschedule proposals", customerId);
            throw new CustomerHasActiveObligationsException(
                    "Customer has pending reschedule requests or proposals");
        }

        // 6. Check active disputes
        if (disputeRepository.hasActiveDisputes(customerId)) {
            log.warn("Customer {} has active disputes under review", customerId);
            throw new CustomerHasActiveObligationsException(
                    "Customer has open complaints or disputes under review");
        }

        // 7. Check completed sub-orders still within the 7-day dispute window (matching CreateDisputeUseCase rule)
        List<SubOrderJpaEntity> completedSubOrders = subOrderRepository.findCompletedSubOrdersByCustomerId(customerId);
        LocalDate today = LocalDate.now();
        for (SubOrderJpaEntity subOrder : completedSubOrders) {
            LocalDate experienceDate = resolveExperienceDate(subOrder, today);
            if (!today.isAfter(experienceDate.plusDays(7))) {
                log.warn("Customer {} has completed sub-order {} still within 7-day dispute window (experienceDate: {})",
                        customerId, subOrder.getId(), experienceDate);
                throw new CustomerHasActiveObligationsException(
                        "Customer has completed orders still within the 7-day dispute period");
            }
        }
    }

    private LocalDate resolveExperienceDate(SubOrderJpaEntity subOrder, LocalDate today) {
        // Priority 1: ServiceSlot date (primary source of truth for tour schedule)
        if (subOrder.getSlotId() != null) {
            Optional<ServiceSlotJpaEntity> slotOpt = serviceSlotRepository.findById(subOrder.getSlotId());
            if (slotOpt.isPresent() && slotOpt.get().getDate() != null) {
                return slotOpt.get().getDate();
            }
        }
        // Priority 2: Physical check-in date (actual attendance)
        if (subOrder.getCheckedInAt() != null) {
            return subOrder.getCheckedInAt().toLocalDate();
        }
        // Priority 3: Scheduled booking item date (not null booking_date in booking_items)
        if (subOrder.getBookingItemId() != null) {
            Optional<BookingItemJpaEntity> itemOpt = bookingItemRepository.findById(subOrder.getBookingItemId());
            if (itemOpt.isPresent() && itemOpt.get().getBookingDate() != null) {
                return itemOpt.get().getBookingDate();
            }
        }
        // Priority 4: Completion timestamp (when sub-order became COMPLETED)
        if (subOrder.getUpdatedAt() != null) {
            return subOrder.getUpdatedAt().toLocalDate();
        }
        // Fail-safe fallback: creation date or today
        return subOrder.getCreatedAt() != null ? subOrder.getCreatedAt().toLocalDate() : today;
    }
}
