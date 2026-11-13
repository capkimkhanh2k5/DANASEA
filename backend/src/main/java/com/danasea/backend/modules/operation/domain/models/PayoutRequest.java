package com.danasea.backend.modules.operation.domain.models;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.danasea.backend.shared.core.domain.models.BaseDomainModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PayoutRequest extends BaseDomainModel {
    private UUID vendorId;
    private UUID settlementId;
    private BigDecimal amount;
    private PayoutRequestStatus status;
    private UUID processedBy;
    private OffsetDateTime processedAt;
    private String rejectionReason;
    private String bankReference;
    private String adminNote;
    private String vendorNote;
}
