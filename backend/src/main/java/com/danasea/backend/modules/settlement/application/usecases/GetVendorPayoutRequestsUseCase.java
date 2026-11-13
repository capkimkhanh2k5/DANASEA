package com.danasea.backend.modules.settlement.application.usecases;

import com.danasea.backend.modules.operation.domain.models.PayoutRequestStatus;
import com.danasea.backend.modules.operation.infrastructure.persistence.entities.PayoutRequestJpaEntity;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaPayoutRequestRepository;
import com.danasea.backend.modules.settlement.application.dtos.PayoutRequestResponse;
import com.danasea.backend.modules.settlement.domain.exceptions.UnauthorizedSettlementAccessException;
import com.danasea.backend.modules.settlement.infrastructure.persistence.mappers.PayoutRequestMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class GetVendorPayoutRequestsUseCase {

    private final JpaPayoutRequestRepository payoutRequestRepository;
    private final PayoutRequestMapper mapper;

    @Autowired
    public GetVendorPayoutRequestsUseCase(
            JpaPayoutRequestRepository payoutRequestRepository,
            PayoutRequestMapper mapper
    ) {
        this.payoutRequestRepository = payoutRequestRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Page<PayoutRequestResponse> execute(
            UUID vendorId,
            PayoutRequestStatus status,
            UUID settlementId,
            Pageable pageable
    ) {
        if (vendorId == null) {
            throw new UnauthorizedSettlementAccessException("Vendor ID is required");
        }

        Specification<PayoutRequestJpaEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("vendorId"), vendorId));

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (settlementId != null) {
                predicates.add(cb.equal(root.get("settlementId"), settlementId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<PayoutRequestJpaEntity> page = payoutRequestRepository.findAll(spec, pageable);
        return page.map(mapper::toResponse);
    }
}
