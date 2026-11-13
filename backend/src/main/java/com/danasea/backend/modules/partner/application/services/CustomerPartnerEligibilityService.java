package com.danasea.backend.modules.partner.application.services;

import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.dispute.application.services.DisputeWindowService;
import com.danasea.backend.modules.dispute.application.services.DisputeWindowService.DisputeWindow;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRescheduleProposalRepository;
import com.danasea.backend.modules.partner.domain.exceptions.CustomerHasActiveObligationsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerPartnerEligibilityService {

    private final JpaBookingRepository bookingRepository;
    private final JpaMasterOrderRepository masterOrderRepository;
    private final JpaSubOrderRepository subOrderRepository;
    private final JpaRefundRepository refundRepository;
    private final JpaSubOrderRescheduleProposalRepository rescheduleProposalRepository;
    private final JpaDisputeRepository disputeRepository;
    private final DisputeWindowService disputeWindowService;

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
        for (SubOrderJpaEntity subOrder : completedSubOrders) {
            DisputeWindow window = disputeWindowService.resolve(subOrder);
            if (!window.isExpired()) {
                log.warn("Customer {} has completed sub-order {} still within 7-day dispute window (experienceDate: {})",
                        customerId, subOrder.getId(), window.experienceDate());
                throw new CustomerHasActiveObligationsException(
                        "Customer has completed orders still within the 7-day dispute period");
            }
        }
    }
}
