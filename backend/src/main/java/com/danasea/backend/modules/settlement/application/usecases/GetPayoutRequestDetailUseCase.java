package com.danasea.backend.modules.settlement.application.usecases;

import com.danasea.backend.modules.operation.infrastructure.persistence.entities.PayoutRequestJpaEntity;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaPayoutRequestRepository;
import com.danasea.backend.modules.settlement.application.dtos.PayoutRequestDetailResponse;
import com.danasea.backend.modules.settlement.domain.exceptions.PayoutRequestNotFoundException;
import com.danasea.backend.modules.settlement.domain.exceptions.UnauthorizedSettlementAccessException;
import com.danasea.backend.modules.settlement.infrastructure.persistence.entities.SettlementJpaEntity;
import com.danasea.backend.modules.settlement.infrastructure.persistence.mappers.PayoutRequestMapper;
import com.danasea.backend.modules.settlement.infrastructure.persistence.repositories.JpaSettlementRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetPayoutRequestDetailUseCase {

    private final JpaPayoutRequestRepository payoutRequestRepository;
    private final JpaSettlementRepository settlementRepository;
    private final VendorInternalApi vendorInternalApi;
    private final PayoutRequestMapper mapper;

    @Autowired
    public GetPayoutRequestDetailUseCase(
            JpaPayoutRequestRepository payoutRequestRepository,
            JpaSettlementRepository settlementRepository,
            VendorInternalApi vendorInternalApi,
            PayoutRequestMapper mapper
    ) {
        this.payoutRequestRepository = payoutRequestRepository;
        this.settlementRepository = settlementRepository;
        this.vendorInternalApi = vendorInternalApi;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PayoutRequestDetailResponse execute(UUID payoutRequestId, UUID requestingVendorId) {
        if (payoutRequestId == null) {
            throw new IllegalArgumentException("Payout request ID is required");
        }

        PayoutRequestJpaEntity payout = payoutRequestRepository.findById(payoutRequestId)
                .orElseThrow(() -> new PayoutRequestNotFoundException("Payout request not found with ID: " + payoutRequestId));

        if (requestingVendorId != null && !payout.getVendorId().equals(requestingVendorId)) {
            throw new UnauthorizedSettlementAccessException("Vendor does not own this payout request");
        }

        SettlementJpaEntity settlement = settlementRepository.findById(payout.getSettlementId()).orElse(null);
        Vendor vendor = vendorInternalApi.findById(payout.getVendorId()).orElse(null);

        return mapper.toDetailResponse(payout, vendor, settlement);
    }
}
