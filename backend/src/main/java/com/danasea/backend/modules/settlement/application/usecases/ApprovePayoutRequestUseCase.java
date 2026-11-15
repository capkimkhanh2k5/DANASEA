package com.danasea.backend.modules.settlement.application.usecases;

import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.operation.domain.models.PayoutRequestStatus;
import com.danasea.backend.modules.operation.infrastructure.persistence.entities.PayoutRequestJpaEntity;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaPayoutRequestRepository;
import com.danasea.backend.modules.settlement.application.dtos.ApprovePayoutRequest;
import com.danasea.backend.modules.settlement.application.dtos.PayoutRequestResponse;
import com.danasea.backend.modules.settlement.domain.exceptions.InvalidPayoutRequestStateException;
import com.danasea.backend.modules.settlement.domain.exceptions.PayoutAmountExceedsBalanceException;
import com.danasea.backend.modules.settlement.domain.exceptions.PayoutRequestNotFoundException;
import com.danasea.backend.modules.settlement.domain.exceptions.SettlementNotFoundException;
import com.danasea.backend.modules.settlement.infrastructure.persistence.entities.SettlementJpaEntity;
import com.danasea.backend.modules.settlement.infrastructure.persistence.mappers.PayoutRequestMapper;
import com.danasea.backend.modules.settlement.infrastructure.persistence.repositories.JpaSettlementRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class ApprovePayoutRequestUseCase {

    private final JpaPayoutRequestRepository payoutRequestRepository;
    private final JpaSettlementRepository settlementRepository;
    private final PayoutRequestMapper mapper;
    private final AuditLogInternalApi auditLogInternalApi;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public ApprovePayoutRequestUseCase(
            JpaPayoutRequestRepository payoutRequestRepository,
            JpaSettlementRepository settlementRepository,
            PayoutRequestMapper mapper,
            @Autowired(required = false) AuditLogInternalApi auditLogInternalApi
    ) {
        this.payoutRequestRepository = payoutRequestRepository;
        this.settlementRepository = settlementRepository;
        this.mapper = mapper;
        this.auditLogInternalApi = auditLogInternalApi;
    }

    public ApprovePayoutRequestUseCase(
            JpaPayoutRequestRepository payoutRequestRepository,
            JpaSettlementRepository settlementRepository,
            PayoutRequestMapper mapper
    ) {
        this(payoutRequestRepository, settlementRepository, mapper, null);
    }

    @Transactional
    public PayoutRequestResponse execute(UUID payoutRequestId, UUID adminUserId, ApprovePayoutRequest request) {
        if (payoutRequestId == null) {
            throw new IllegalArgumentException("Payout request ID is required");
        }

        // 1. Lock PayoutRequest
        PayoutRequestJpaEntity payout = payoutRequestRepository.findByIdForUpdate(payoutRequestId)
                .orElseThrow(() -> new PayoutRequestNotFoundException("Payout request not found with ID: " + payoutRequestId));

        // 2. Validate current state: Only REQUESTED can be approved
        if (payout.getStatus() != PayoutRequestStatus.REQUESTED) {
            throw new InvalidPayoutRequestStateException(
                    "Chỉ có thể duyệt yêu cầu chi trả đang ở trạng thái REQUESTED. Trạng thái hiện tại: " + payout.getStatus());
        }

        // 3. Lock Settlement and re-verify financial invariant
        SettlementJpaEntity settlement = settlementRepository.findByIdForUpdate(payout.getSettlementId())
                .orElseThrow(() -> new SettlementNotFoundException("Settlement not found with ID: " + payout.getSettlementId()));

        BigDecimal committedExcludingCurrent = payoutRequestRepository.sumAmountBySettlementIdAndStatusIn(
                settlement.getId(),
                List.of(PayoutRequestStatus.APPROVED, PayoutRequestStatus.PAID)
        );
        if (committedExcludingCurrent == null) {
            committedExcludingCurrent = BigDecimal.ZERO;
        }

        BigDecimal remainingBalance = settlement.getNetPayableAmount().subtract(committedExcludingCurrent);
        if (payout.getAmount().compareTo(remainingBalance) > 0) {
            throw new PayoutAmountExceedsBalanceException(
                    String.format("Không thể duyệt: Số tiền yêu cầu (%s đ) vượt quá số dư khả dụng còn lại (%s đ)",
                            payout.getAmount(), remainingBalance));
        }

        // 4. Update status to APPROVED
        OffsetDateTime now = OffsetDateTime.now();
        payout.setStatus(PayoutRequestStatus.APPROVED);
        payout.setProcessedBy(adminUserId);
        payout.setProcessedAt(now);
        payout.setUpdatedAt(now);

        if (request != null && request.adminNote() != null && !request.adminNote().isBlank()) {
            payout.setAdminNote(request.adminNote().trim());
        }

        PayoutRequestJpaEntity saved = payoutRequestRepository.save(payout);
        log.info("Admin {} approved payout request #{} ({} VND) for vendor {}",
                adminUserId, saved.getId(), saved.getAmount(), saved.getVendorId());

        if (auditLogInternalApi != null) {
            String metadata = buildAuditMetadata("REQUESTED", "APPROVED", saved, null);
            auditLogInternalApi.recordTransactionalAuditLog(
                    adminUserId,
                    "PAYOUT_REQUEST_APPROVED",
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
            if (payout.getBankReference() != null && !payout.getBankReference().isBlank()) {
                meta.put("bankRef", payout.getBankReference());
            }
            if (payout.getAdminNote() != null && !payout.getAdminNote().isBlank()) {
                meta.put("note", payout.getAdminNote());
            }
            return objectMapper.writeValueAsString(meta);
        } catch (Exception ex) {
            log.warn("Could not serialize payout audit metadata as JSON: {}", ex.getMessage());
            return String.format("previousStatus=%s,newStatus=%s,amount=%s,vendorId=%s",
                    previousStatus, newStatus, payout.getAmount(), payout.getVendorId());
        }
    }
}
