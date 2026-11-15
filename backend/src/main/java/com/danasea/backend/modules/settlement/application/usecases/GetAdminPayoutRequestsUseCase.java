package com.danasea.backend.modules.settlement.application.usecases;

import com.danasea.backend.modules.operation.domain.models.PayoutRequestStatus;
import com.danasea.backend.modules.operation.infrastructure.persistence.entities.PayoutRequestJpaEntity;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaPayoutRequestRepository;
import com.danasea.backend.modules.settlement.application.dtos.PayoutRequestResponse;
import com.danasea.backend.modules.settlement.infrastructure.persistence.mappers.PayoutRequestMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class GetAdminPayoutRequestsUseCase {

    private final JpaPayoutRequestRepository payoutRequestRepository;
    private final PayoutRequestMapper mapper;

    @Autowired
    public GetAdminPayoutRequestsUseCase(
            JpaPayoutRequestRepository payoutRequestRepository,
            PayoutRequestMapper mapper
    ) {
        this.payoutRequestRepository = payoutRequestRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Page<PayoutRequestResponse> execute(
            UUID vendorId,
            UUID settlementId,
            PayoutRequestStatus status,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    ) {
        Specification<PayoutRequestJpaEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (vendorId != null) {
                predicates.add(cb.equal(root.get("vendorId"), vendorId));
            }
            if (settlementId != null) {
                predicates.add(cb.equal(root.get("settlementId"), settlementId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                OffsetDateTime fromDateTime = from.atStartOfDay().atOffset(ZoneOffset.UTC);
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDateTime));
            }
            if (to != null) {
                OffsetDateTime toDateTime = to.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDateTime));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<PayoutRequestJpaEntity> page = payoutRequestRepository.findAll(spec, pageable);
        return page.map(mapper::toResponse);
    }
}
