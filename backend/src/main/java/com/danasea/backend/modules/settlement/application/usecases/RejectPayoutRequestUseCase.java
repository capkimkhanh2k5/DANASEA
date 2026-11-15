package com.danasea.backend.modules.settlement.application.usecases;

import com.danasea.backend.modules.operation.domain.models.PayoutRequestStatus;
import com.danasea.backend.modules.operation.infrastructure.persistence.entities.PayoutRequestJpaEntity;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaPayoutRequestRepository;
import com.danasea.backend.modules.settlement.application.dtos.PayoutRequestResponse;
import com.danasea.backend.modules.settlement.application.dtos.RejectPayoutRequest;
import com.danasea.backend.modules.settlement.domain.exceptions.InvalidPayoutRequestStateException;
import com.danasea.backend.modules.settlement.domain.exceptions.PayoutRequestNotFoundException;
import com.danasea.backend.modules.settlement.infrastructure.persistence.mappers.PayoutRequestMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
public class RejectPayoutRequestUseCase {

    private final JpaPayoutRequestRepository payoutRequestRepository;
    private final PayoutRequestMapper mapper;

    @Autowired
    public RejectPayoutRequestUseCase(
            JpaPayoutRequestRepository payoutRequestRepository,
            PayoutRequestMapper mapper
    ) {
        this.payoutRequestRepository = payoutRequestRepository;
        this.mapper = mapper;
    }

    @Transactional
    public PayoutRequestResponse execute(UUID payoutRequestId, UUID adminUserId, RejectPayoutRequest request) {
        if (payoutRequestId == null) {
            throw new IllegalArgumentException("Payout request ID is required");
        }
        if (request == null || request.rejectionReason() == null || request.rejectionReason().isBlank()) {
            throw new IllegalArgumentException("Lý do từ chối là bắt buộc");
        }

        PayoutRequestJpaEntity payout = payoutRequestRepository.findByIdForUpdate(payoutRequestId)
                .orElseThrow(() -> new PayoutRequestNotFoundException("Payout request not found with ID: " + payoutRequestId));

        if (payout.getStatus() == PayoutRequestStatus.PAID) {
            throw new InvalidPayoutRequestStateException("Không thể từ chối yêu cầu chi trả đã hoàn tất (PAID)");
        }
        if (payout.getStatus() == PayoutRequestStatus.REJECTED) {
            throw new InvalidPayoutRequestStateException("Yêu cầu chi trả này đã bị từ chối trước đó");
        }

        OffsetDateTime now = OffsetDateTime.now();
        payout.setStatus(PayoutRequestStatus.REJECTED);
        payout.setRejectionReason(request.rejectionReason().trim());
        payout.setProcessedBy(adminUserId);
        payout.setProcessedAt(now);
        payout.setUpdatedAt(now);

        if (request.adminNote() != null && !request.adminNote().isBlank()) {
            payout.setAdminNote(request.adminNote().trim());
        }

        PayoutRequestJpaEntity saved = payoutRequestRepository.save(payout);
        log.info("Admin {} đã từ chối PayoutRequest #{} (Lý do: {})",
                adminUserId, saved.getId(), saved.getRejectionReason());

        return mapper.toResponse(saved);
    }
}
