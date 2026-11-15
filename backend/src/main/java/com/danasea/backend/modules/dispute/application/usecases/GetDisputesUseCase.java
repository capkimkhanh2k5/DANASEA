package com.danasea.backend.modules.dispute.application.usecases;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.dispute.domain.models.DisputeReason;
import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.dispute.infrastructure.persistence.entities.DisputeJpaEntity;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.dispute.presentation.dtos.DisputeResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort.Order;

@Service
public class GetDisputesUseCase {

    private final JpaDisputeRepository disputeRepository;
    private final AccountInternalApi accountInternalApi;

    public GetDisputesUseCase(JpaDisputeRepository disputeRepository,
                             @Autowired(required = false) AccountInternalApi accountInternalApi) {
        this.disputeRepository = disputeRepository;
        this.accountInternalApi = accountInternalApi;
    }

    public GetDisputesUseCase(JpaDisputeRepository disputeRepository) {
        this(disputeRepository, null);
    }

    @Transactional(readOnly = true)
    public Page<DisputeResponse> execute(DisputeStatus status, DisputeReason reason, Pageable pageable) {
        if (pageable == null || pageable.getPageNumber() < 0
                || pageable.getPageSize() < 1 || pageable.getPageSize() > 100) {
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        }
        for (Order order : pageable.getSort()) {
            if (!Set.of("createdAt", "updatedAt", "status", "reason", "resolvedAt")
                    .contains(order.getProperty())) {
                throw new IllegalArgumentException("Unsupported sort field: " + order.getProperty());
            }
        }
        Page<DisputeJpaEntity> pageResult;
        if (status != null && reason != null) {
            pageResult = disputeRepository.findByStatusAndReason(status, reason, pageable);
        } else if (status != null) {
            pageResult = disputeRepository.findByStatus(status, pageable);
        } else if (reason != null) {
            pageResult = disputeRepository.findByReason(reason, pageable);
        } else {
            pageResult = disputeRepository.findAll(pageable);
        }

        Map<UUID, User> usersMap = Map.of();
        if (accountInternalApi != null) {
            Set<UUID> resolverIds = pageResult.getContent().stream()
                    .map(DisputeJpaEntity::getResolvedBy)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!resolverIds.isEmpty()) {
                usersMap = accountInternalApi.findUsersByIds(resolverIds);
            }
        }
        final Map<UUID, User> finalUsersMap = usersMap;

        return pageResult.map(entity -> {
            String resolvedByName = null;
            if (entity.getResolvedBy() != null && finalUsersMap.containsKey(entity.getResolvedBy())) {
                resolvedByName = finalUsersMap.get(entity.getResolvedBy()).getFullName();
            }
            return DisputeResponse.fromEntity(entity, resolvedByName);
        });
    }

}
