package com.danasea.backend.modules.settlement.application.usecases;

import com.danasea.backend.modules.operation.domain.models.PayoutRequestStatus;
import com.danasea.backend.modules.operation.infrastructure.persistence.entities.PayoutRequestJpaEntity;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaPayoutRequestRepository;
import com.danasea.backend.modules.settlement.application.dtos.MarkPaidPayoutRequest;
import com.danasea.backend.modules.settlement.application.dtos.PayoutRequestResponse;
import com.danasea.backend.modules.settlement.domain.exceptions.InvalidPayoutRequestStateException;
import com.danasea.backend.modules.settlement.domain.exceptions.PayoutRequestNotFoundException;
import com.danasea.backend.modules.settlement.domain.exceptions.SettlementNotFoundException;
import com.danasea.backend.modules.settlement.domain.models.SettlementStatus;
import com.danasea.backend.modules.settlement.infrastructure.persistence.entities.SettlementJpaEntity;
import com.danasea.backend.modules.settlement.infrastructure.persistence.mappers.PayoutRequestMapper;
import com.danasea.backend.modules.settlement.infrastructure.persistence.repositories.JpaSettlementRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class MarkPayoutPaidUseCase {

    private final JpaPayoutRequestRepository payoutRequestRepository;
    private final JpaSettlementRepository settlementRepository;
    private final PayoutRequestMapper mapper;

    @Autowired
    public MarkPayoutPaidUseCase(
            JpaPayoutRequestRepository payoutRequestRepository,
            JpaSettlementRepository settlementRepository,
            PayoutRequestMapper mapper
    ) {
        this.payoutRequestRepository = payoutRequestRepository;
        this.settlementRepository = settlementRepository;
        this.mapper = mapper;
    }

    @Transactional
    public PayoutRequestResponse execute(UUID payoutRequestId, UUID adminUserId, MarkPaidPayoutRequest request) {
        if (payoutRequestId == null) {
            throw new IllegalArgumentException("Payout request ID is required");
        }

        // 1. Lock PayoutRequest
        PayoutRequestJpaEntity payout = payoutRequestRepository.findByIdForUpdate(payoutRequestId)
                .orElseThrow(() -> new PayoutRequestNotFoundException("Payout request not found with ID: " + payoutRequestId));

        if (payout.getStatus() != PayoutRequestStatus.APPROVED) {
            throw new InvalidPayoutRequestStateException(
                    "Chỉ có thể ghi nhận chi trả cho yêu cầu đã được APPROVED. Trạng thái hiện tại: " + payout.getStatus());
        }

        // 2. Cập nhật trạng thái PayoutRequest sang PAID
        OffsetDateTime now = OffsetDateTime.now();
        payout.setStatus(PayoutRequestStatus.PAID);
        payout.setProcessedBy(adminUserId);
        payout.setProcessedAt(now);
        payout.setUpdatedAt(now);

        if (request != null) {
            if (request.bankReference() != null && !request.bankReference().isBlank()) {
                payout.setBankReference(request.bankReference().trim());
            }
            if (request.adminNote() != null && !request.adminNote().isBlank()) {
                payout.setAdminNote(request.adminNote().trim());
            }
        }

        PayoutRequestJpaEntity saved = payoutRequestRepository.saveAndFlush(payout);
        log.info("Admin {} đã xác nhận chi trả PAID cho PayoutRequest #{} (Số tiền: {} đ, Mã tham chiếu ngân hàng: {})",
                adminUserId, saved.getId(), saved.getAmount(), saved.getBankReference());

        // 3. Kiểm tra Settlement: Khi toàn bộ số tiền của kỳ đối soát đã được giải ngân, chuyển Settlement sang PAID
        SettlementJpaEntity settlement = settlementRepository.findByIdForUpdate(payout.getSettlementId())
                .orElseThrow(() -> new SettlementNotFoundException("Settlement not found with ID: " + payout.getSettlementId()));

        BigDecimal totalPaid = payoutRequestRepository.sumAmountBySettlementIdAndStatusIn(
                settlement.getId(),
                List.of(PayoutRequestStatus.PAID)
        );

        if (totalPaid != null && totalPaid.compareTo(settlement.getNetPayableAmount()) >= 0) {
            settlement.setStatus(SettlementStatus.PAID);
            settlement.setUpdatedAt(now);
            settlementRepository.save(settlement);
            log.info("Kỳ đối soát #{} đã hoàn tất toàn bộ nghĩa vụ chi trả (Tổng đã chi: {} / Cần chi: {}) -> Đã chuyển sang PAID.",
                    settlement.getId(), totalPaid, settlement.getNetPayableAmount());
        }

        return mapper.toResponse(saved);
    }
}
