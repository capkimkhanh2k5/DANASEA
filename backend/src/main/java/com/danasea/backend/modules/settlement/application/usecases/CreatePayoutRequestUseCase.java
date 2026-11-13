package com.danasea.backend.modules.settlement.application.usecases;

import com.danasea.backend.modules.operation.domain.models.PayoutRequestStatus;
import com.danasea.backend.modules.operation.infrastructure.persistence.entities.PayoutRequestJpaEntity;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaPayoutRequestRepository;
import com.danasea.backend.modules.settlement.application.dtos.CreatePayoutRequestRequest;
import com.danasea.backend.modules.settlement.application.dtos.PayoutRequestResponse;
import com.danasea.backend.modules.settlement.domain.exceptions.InvalidOrderAmountException;
import com.danasea.backend.modules.settlement.domain.exceptions.PayoutAmountExceedsBalanceException;
import com.danasea.backend.modules.settlement.domain.exceptions.SettlementNotFoundException;
import com.danasea.backend.modules.settlement.domain.exceptions.SettlementNotFinalizedException;
import com.danasea.backend.modules.settlement.domain.exceptions.UnauthorizedSettlementAccessException;
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
public class CreatePayoutRequestUseCase {

    private final JpaPayoutRequestRepository payoutRequestRepository;
    private final JpaSettlementRepository settlementRepository;
    private final PayoutRequestMapper mapper;

    @Autowired
    public CreatePayoutRequestUseCase(
            JpaPayoutRequestRepository payoutRequestRepository,
            JpaSettlementRepository settlementRepository,
            PayoutRequestMapper mapper
    ) {
        this.payoutRequestRepository = payoutRequestRepository;
        this.settlementRepository = settlementRepository;
        this.mapper = mapper;
    }

    @Transactional
    public PayoutRequestResponse execute(UUID vendorId, CreatePayoutRequestRequest request) {
        if (vendorId == null) {
            throw new UnauthorizedSettlementAccessException("Vendor ID is required");
        }
        if (request == null || request.settlementId() == null) {
            throw new IllegalArgumentException("Settlement ID is required");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderAmountException("Payout amount must be greater than 0");
        }

        // 1. Khoá bi quan Settlement để bảo đảm tính nguyên tử và chống race condition
        SettlementJpaEntity settlement = settlementRepository.findByIdForUpdate(request.settlementId())
                .orElseThrow(() -> new SettlementNotFoundException("Settlement not found with ID: " + request.settlementId()));

        // 2. Kiểm tra tenant isolation: Kỳ đối soát phải thuộc đúng vendor yêu cầu
        if (!vendorId.equals(settlement.getVendorId())) {
            log.warn("Vendor {} cố tình yêu cầu rút tiền từ kỳ đối soát {} của Vendor {}",
                    vendorId, settlement.getId(), settlement.getVendorId());
            throw new UnauthorizedSettlementAccessException("Vendor does not own this settlement period");
        }

        // 3. Kiểm tra trạng thái kỳ đối soát: Chỉ cho phép yêu cầu khi đã FINALIZED
        if (settlement.getStatus() != SettlementStatus.FINALIZED) {
            throw new SettlementNotFinalizedException(
                    "Chỉ được tạo yêu cầu rút tiền cho kỳ đối soát đã chốt sổ (FINALIZED). Trạng thái hiện tại: " + settlement.getStatus());
        }

        // 4. Tính toán số tiền đã cam kết (REQUESTED, APPROVED, PAID)
        BigDecimal alreadyCommitted = payoutRequestRepository.sumAmountBySettlementIdAndStatusIn(
                settlement.getId(),
                List.of(PayoutRequestStatus.REQUESTED, PayoutRequestStatus.APPROVED, PayoutRequestStatus.PAID)
        );
        if (alreadyCommitted == null) {
            alreadyCommitted = BigDecimal.ZERO;
        }

        BigDecimal remainingBalance = settlement.getNetPayableAmount().subtract(alreadyCommitted);

        // 5. Kiểm tra hạn mức khả dụng
        if (request.amount().compareTo(remainingBalance) > 0) {
            throw new PayoutAmountExceedsBalanceException(
                    String.format("Số tiền yêu cầu (%s đ) vượt quá số dư khả dụng còn lại của kỳ đối soát (Khả dụng: %s đ)",
                            request.amount(), remainingBalance));
        }

        // 6. Tạo và lưu PayoutRequest
        OffsetDateTime now = OffsetDateTime.now();
        String vNote = (request.note() != null && !request.note().isBlank()) ? request.note().trim() : null;
        PayoutRequestJpaEntity entity = PayoutRequestJpaEntity.builder()
                .vendorId(vendorId)
                .settlementId(settlement.getId())
                .amount(request.amount())
                .status(PayoutRequestStatus.REQUESTED)
                .vendorNote(vNote)
                .adminNote(null)
                .rejectionReason(null)
                .bankReference(null)
                .build();
        entity.setId(UUID.randomUUID());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        PayoutRequestJpaEntity saved = payoutRequestRepository.save(entity);
        log.info("Vendor {} tạo PayoutRequest #{} thành công: {} đ cho Settlement #{}",
                vendorId, saved.getId(), saved.getAmount(), settlement.getId());

        return mapper.toResponse(saved);
    }
}
