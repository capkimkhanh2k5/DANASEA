package com.danasea.backend.modules.settlement.application.usecases;

import com.danasea.backend.modules.order.application.services.VendorFaultCompensationService;
import com.danasea.backend.modules.settlement.application.dtos.SettlementResponse;
import com.danasea.backend.modules.settlement.domain.exceptions.SettlementAlreadyFinalizedException;
import com.danasea.backend.modules.settlement.domain.exceptions.SettlementNotFoundException;
import com.danasea.backend.modules.settlement.domain.models.SettlementStatus;
import com.danasea.backend.modules.settlement.infrastructure.persistence.entities.SettlementJpaEntity;
import com.danasea.backend.modules.settlement.infrastructure.persistence.mappers.SettlementMapper;
import com.danasea.backend.modules.settlement.infrastructure.persistence.repositories.JpaSettlementRepository;
import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
public class FinalizeSettlementUseCase {

    private final JpaSettlementRepository settlementRepository;
    private final SettlementMapper mapper;
    private final AuditLogInternalApi auditLogInternalApi;
    private final VendorFaultCompensationService compensation;

    public FinalizeSettlementUseCase(
            JpaSettlementRepository settlementRepository,
            SettlementMapper mapper,
            AuditLogInternalApi auditLogInternalApi,
            VendorFaultCompensationService compensation) {
        this.settlementRepository = settlementRepository;
        this.mapper = mapper;
        this.auditLogInternalApi = auditLogInternalApi;
        this.compensation = compensation;
    }

    @Transactional
    public SettlementResponse execute(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Settlement ID is required.");
        }

        SettlementJpaEntity settlement = settlementRepository.findByIdForUpdate(id)
                .or(() -> settlementRepository.findById(id))
                .orElseThrow(() -> new SettlementNotFoundException("Settlement not found with ID: " + id));

        if (settlement.getStatus() == SettlementStatus.FINALIZED || settlement.getStatus() == SettlementStatus.PAID) {
            throw new SettlementAlreadyFinalizedException(
                    "Settlement is already FINALIZED or PAID and cannot be finalized again.");
        }

        var deducted = compensation.recoverAtFinalization(
                settlement.getVendorId(), settlement.getId(), settlement.getNetPayableAmount());
        if (deducted.signum() > 0) {
            settlement.setVendorFaultCompensationDeductedAmount(deducted);
            settlement.setNetPayableAmount(settlement.getNetPayableAmount().subtract(deducted));
        }
        settlement.setStatus(SettlementStatus.FINALIZED);
        settlement.setUpdatedAt(OffsetDateTime.now());
        SettlementJpaEntity saved = settlementRepository.save(settlement);

        auditLogInternalApi.recordAuditLog(
                SecurityUtils.getCurrentUserId().orElse(null),
                "FINALIZE_SETTLEMENT",
                "SETTLEMENT",
                id,
                "status=FINALIZED,vendorId=" + saved.getVendorId());

        log.info("Settlement {} finalized successfully", id);
        return mapper.toResponse(saved);
    }
}
