package com.danasea.backend.modules.settlement.application.usecases;

import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.operation.domain.models.PayoutRequestStatus;
import com.danasea.backend.modules.operation.infrastructure.persistence.entities.PayoutRequestJpaEntity;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaPayoutRequestRepository;
import com.danasea.backend.modules.settlement.application.dtos.PayoutRequestResponse;
import com.danasea.backend.modules.settlement.application.dtos.RejectPayoutRequest;
import com.danasea.backend.modules.settlement.domain.exceptions.InvalidPayoutRequestStateException;
import com.danasea.backend.modules.settlement.domain.exceptions.PayoutRequestNotFoundException;
import com.danasea.backend.modules.settlement.infrastructure.persistence.mappers.PayoutRequestMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class RejectPayoutRequestUseCase {

    private final JpaPayoutRequestRepository payoutRequestRepository;
    private final PayoutRequestMapper mapper;
    private final AuditLogInternalApi auditLogInternalApi;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public RejectPayoutRequestUseCase(
            JpaPayoutRequestRepository payoutRequestRepository,
            PayoutRequestMapper mapper,
            @Autowired(required = false) AuditLogInternalApi auditLogInternalApi
    ) {
        this.payoutRequestRepository = payoutRequestRepository;
        this.mapper = mapper;
        this.auditLogInternalApi = auditLogInternalApi;
    }

    public RejectPayoutRequestUseCase(
            JpaPayoutRequestRepository payoutRequestRepository,
            PayoutRequestMapper mapper
    ) {
        this(payoutRequestRepository, mapper, null);
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

        String previousStatus = payout.getStatus() != null ? payout.getStatus().name() : "UNKNOWN";
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
        log.info("Admin {} rejected payout request #{} (reason: {})",
                adminUserId, saved.getId(), saved.getRejectionReason());

        if (auditLogInternalApi != null) {
            String metadata = buildAuditMetadata(previousStatus, "REJECTED", saved, saved.getRejectionReason());
            auditLogInternalApi.recordTransactionalAuditLog(
                    adminUserId,
                    "PAYOUT_REQUEST_REJECTED",
                    "PAYOUT_REQUEST",
                    saved.getId(),
                    metadata
            );
        }

        return mapper.toResponse(saved);
    }

    private String buildAuditMetadata(String previousStatus, String newStatus, PayoutRequestJpaEntity payout, String reason) {
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("previousStatus", previousStatus);
            meta.put("newStatus", newStatus);
            meta.put("amount", payout.getAmount() != null ? payout.getAmount().doubleValue() : 0.0);
            meta.put("vendorId", payout.getVendorId() != null ? payout.getVendorId().toString() : null);
            meta.put("settlementId", payout.getSettlementId() != null ? payout.getSettlementId().toString() : null);
            if (reason != null && !reason.isBlank()) {
                meta.put("reason", reason);
            }
            if (payout.getAdminNote() != null && !payout.getAdminNote().isBlank()) {
                meta.put("note", payout.getAdminNote());
            }
            return objectMapper.writeValueAsString(meta);
        } catch (Exception ex) {
            log.warn("Could not serialize payout audit metadata as JSON: {}", ex.getMessage());
            return String.format("previousStatus=%s,newStatus=%s,amount=%s,reason=%s",
                    previousStatus, newStatus, payout.getAmount(), reason);
        }
    }
}
