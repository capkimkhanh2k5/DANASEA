package com.danasea.backend.modules.dispute.application.usecases;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.dispute.domain.exceptions.DisputeNotFoundException;
import com.danasea.backend.modules.dispute.infrastructure.persistence.entities.DisputeJpaEntity;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.dispute.presentation.dtos.DisputeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
public class GetAdminDisputeDetailUseCase {

    private final JpaDisputeRepository disputeRepository;
    private final AccountInternalApi accountInternalApi;

    @Autowired
    public GetAdminDisputeDetailUseCase(JpaDisputeRepository disputeRepository,
                                        @Autowired(required = false) AccountInternalApi accountInternalApi) {
        this.disputeRepository = disputeRepository;
        this.accountInternalApi = accountInternalApi;
    }

    public GetAdminDisputeDetailUseCase(JpaDisputeRepository disputeRepository) {
        this(disputeRepository, null);
    }

    @Transactional(readOnly = true)
    public DisputeResponse execute(UUID disputeId) {
        if (disputeId == null) {
            throw new IllegalArgumentException("Dispute ID cannot be null");
        }

        DisputeJpaEntity dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new DisputeNotFoundException("Dispute not found with id: " + disputeId));

        log.info("Admin retrieved dispute dossier for id: {}", disputeId);

        String resolvedByName = null;
        if (dispute.getResolvedBy() != null && accountInternalApi != null) {
            resolvedByName = accountInternalApi.findUserById(dispute.getResolvedBy())
                    .map(User::getFullName)
                    .orElse(null);
        }

        return DisputeResponse.fromEntity(dispute, resolvedByName);
    }
}
