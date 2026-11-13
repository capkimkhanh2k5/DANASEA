package com.danasea.backend.modules.order.infrastructure.persistence.entities;

import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "sub_orders")
public class SubOrderJpaEntity extends BaseJpaEntity {

    @Column(name = "booking_item_id")
    private UUID bookingItemId;

    private UUID masterOrderId;

    private UUID vendorId;

    private UUID serviceId;

    private UUID slotId;

    @Column(name = "actual_slot_id")
    private UUID actualSlotId;

    @Column(name = "is_late")
    private Boolean isLate = false;

    @Column(name = "late_checkin_reason")
    private String lateCheckinReason;

    private Integer quantity;

    private BigDecimal unitPrice;

    private BigDecimal subtotalAmount;

    @Column(name = "discount_amount")
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "vendor_discount_amount")
    private BigDecimal vendorDiscountAmount = BigDecimal.ZERO;

    @Column(name = "platform_discount_amount")
    private BigDecimal platformDiscountAmount = BigDecimal.ZERO;

    @Column(name = "commission_basis_amount")
    private BigDecimal commissionBasisAmount;

    @Column(name = "final_amount")
    private BigDecimal finalAmount;

    private BigDecimal commissionRate;

    private BigDecimal commissionAmount;

    private BigDecimal vendorPayoutAmount;

    @Enumerated(EnumType.STRING)
    private SubOrderStatus status;

    @Enumerated(EnumType.STRING)
    private RefundReason cancellationReason;

    @Column(name = "cancellation_policy_version", nullable = false, length = 40, columnDefinition = "varchar(40) default 'STANDARD_24H_V2'")
    private String cancellationPolicyVersion = "STANDARD_24H_V2";

    @Column(name = "waiver_required")
    private Boolean waiverRequired = false;

    @Column(name = "waiver_version")
    private Integer waiverVersion = 1;

    @Column(name = "waiver_content", columnDefinition = "TEXT")
    private String waiverContent;

    @Column(name = "waiver_content_en", columnDefinition = "TEXT")
    private String waiverContentEn;

    private Boolean waiverAccepted;

    private OffsetDateTime waiverAcceptedAt;

    @Column(name = "waiver_accepted_by")
    private UUID waiverAcceptedBy;

    @Column(name = "waiver_accepted_language", length = 10)
    private String waiverAcceptedLanguage;

    @Column(name = "waiver_accepted_content", columnDefinition = "TEXT")
    private String waiverAcceptedContent;

    private UUID qrSecret;

    private OffsetDateTime checkedInAt;

    @Column(name = "vendor_notified_at")
    private OffsetDateTime vendorNotifiedAt;

    @Column(name = "reschedule_version", nullable = false, columnDefinition = "bigint default 0")
    private Long rescheduleVersion = 0L;

    @Column(name = "rescheduled_at")
    private OffsetDateTime rescheduledAt;

    @Column(name = "original_slot_id")
    private UUID originalSlotId;

    public BigDecimal getFinalAmount() {
        if (finalAmount != null) {
            return finalAmount;
        }
        BigDecimal subtotal = subtotalAmount != null ? subtotalAmount : BigDecimal.ZERO;
        return subtotal.subtract(discountAmount != null ? discountAmount : BigDecimal.ZERO).max(BigDecimal.ZERO);
    }

    @PrePersist
    @PreUpdate
    private void initializeFinancialSnapshot() {
        if (finalAmount == null) {
            finalAmount = getFinalAmount();
        }
        if (commissionBasisAmount == null) {
            BigDecimal subtotal = subtotalAmount != null ? subtotalAmount : BigDecimal.ZERO;
            commissionBasisAmount = subtotal.subtract(vendorDiscountAmount != null ? vendorDiscountAmount : BigDecimal.ZERO)
                    .max(BigDecimal.ZERO);
        }
    }

}
