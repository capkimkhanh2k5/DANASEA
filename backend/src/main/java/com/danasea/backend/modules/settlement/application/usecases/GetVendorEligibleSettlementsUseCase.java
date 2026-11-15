package com.danasea.backend.modules.settlement.application.usecases;

import com.danasea.backend.modules.operation.domain.models.PayoutRequestStatus;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaPayoutRequestRepository;
import com.danasea.backend.modules.settlement.application.dtos.EligibleSettlementPayoutResponse;
import com.danasea.backend.modules.settlement.domain.exceptions.UnauthorizedSettlementAccessException;
import com.danasea.backend.modules.settlement.domain.models.SettlementStatus;
import com.danasea.backend.modules.settlement.infrastructure.persistence.entities.SettlementJpaEntity;
import com.danasea.backend.modules.settlement.infrastructure.persistence.repositories.JpaSettlementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class GetVendorEligibleSettlementsUseCase {

    private final JpaSettlementRepository settlementRepository;
    private final JpaPayoutRequestRepository payoutRequestRepository;

    @Autowired
    public GetVendorEligibleSettlementsUseCase(
            JpaSettlementRepository settlementRepository,
            JpaPayoutRequestRepository payoutRequestRepository
    ) {
        this.settlementRepository = settlementRepository;
        this.payoutRequestRepository = payoutRequestRepository;
    }

    @Transactional(readOnly = true)
    public List<EligibleSettlementPayoutResponse> execute(UUID vendorId) {
        if (vendorId == null) {
            throw new UnauthorizedSettlementAccessException("Vendor ID is required");
        }

        // Lấy tất cả kỳ đối soát FINALIZED của vendor
        var page = settlementRepository.findByVendorIdAndStatus(
                vendorId,
                SettlementStatus.FINALIZED,
                PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        List<EligibleSettlementPayoutResponse> result = new ArrayList<>();
        for (SettlementJpaEntity settlement : page.getContent()) {
            BigDecimal requestedOrApproved = payoutRequestRepository.sumAmountBySettlementIdAndStatusIn(
                    settlement.getId(),
                    List.of(PayoutRequestStatus.REQUESTED, PayoutRequestStatus.APPROVED)
            );
            if (requestedOrApproved == null) requestedOrApproved = BigDecimal.ZERO;

            BigDecimal paid = payoutRequestRepository.sumAmountBySettlementIdAndStatusIn(
                    settlement.getId(),
                    List.of(PayoutRequestStatus.PAID)
            );
            if (paid == null) paid = BigDecimal.ZERO;

            BigDecimal totalCommitted = requestedOrApproved.add(paid);
            BigDecimal remaining = settlement.getNetPayableAmount().subtract(totalCommitted);

            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                result.add(EligibleSettlementPayoutResponse.builder()
                        .settlementId(settlement.getId())
                        .periodStart(settlement.getPeriodStart())
                        .periodEnd(settlement.getPeriodEnd())
                        .grossAmount(settlement.getGrossAmount())
                        .commissionAmount(settlement.getCommissionAmount())
                        .netPayableAmount(settlement.getNetPayableAmount())
                        .alreadyRequestedAmount(requestedOrApproved)
                        .alreadyPaidAmount(paid)
                        .remainingEligibleBalance(remaining)
                        .status(settlement.getStatus())
                        .build());
            }
        }

        return result;
    }
}
