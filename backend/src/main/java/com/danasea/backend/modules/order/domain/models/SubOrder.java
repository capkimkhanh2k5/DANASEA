package com.danasea.backend.modules.order.domain.models;


import com.danasea.backend.modules.order.domain.exceptions.InvalidOrderStateException;
import com.danasea.backend.shared.core.domain.models.BaseDomainModel;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SubOrder extends BaseDomainModel {

    private UUID bookingItemId;
    private UUID masterOrderId;
    private String subOrderCode;
    private UUID vendorId;
    private UUID serviceId;
    private UUID slotId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount = BigDecimal.ZERO;
    private BigDecimal vendorDiscountAmount = BigDecimal.ZERO;
    private BigDecimal platformDiscountAmount = BigDecimal.ZERO;
    private BigDecimal commissionBasisAmount;
    private BigDecimal finalAmount;
    private BigDecimal commissionRate;
    private BigDecimal commissionAmount;
    private BigDecimal vendorPayoutAmount;
    private SubOrderStatus status;

    private RefundReason cancellationReason;
    private String cancellationPolicyVersion = "STANDARD_24H_V2";
    private Boolean waiverRequired;
    private Integer waiverVersion;
    private String waiverContent;
    private String waiverContentEn;
    private Boolean waiverAccepted;
    private OffsetDateTime waiverAcceptedAt;
    private UUID waiverAcceptedBy;
    private String waiverAcceptedLanguage;
    private String waiverAcceptedContent;
    private UUID qrSecret;
    private OffsetDateTime checkedInAt;
    private OffsetDateTime vendorNotifiedAt;
    private Long rescheduleVersion = 0L;
    private OffsetDateTime rescheduledAt;
    private UUID originalSlotId;

    public SubOrder() {
        super();
        this.status = SubOrderStatus.PENDING;
        this.waiverRequired = false;
        this.waiverVersion = 1;
        this.waiverAccepted = false;
        this.discountAmount = BigDecimal.ZERO;
        this.vendorDiscountAmount = BigDecimal.ZERO;
        this.platformDiscountAmount = BigDecimal.ZERO;
    }

    public BigDecimal getFinalAmount() {
        if (finalAmount != null) {
            return finalAmount;
        }
        if (subtotalAmount != null) {
            BigDecimal disc = discountAmount != null ? discountAmount : BigDecimal.ZERO;
            return subtotalAmount.subtract(disc).max(BigDecimal.ZERO);
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal getCommissionBasisAmount() {
        if (commissionBasisAmount != null) {
            return commissionBasisAmount;
        }
        if (subtotalAmount != null) {
            BigDecimal vd = vendorDiscountAmount != null ? vendorDiscountAmount : BigDecimal.ZERO;
            return subtotalAmount.subtract(vd).max(BigDecimal.ZERO);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Áp dụng discount và cập nhật lại cơ sở tính hoa hồng, hoa hồng, vendor payout và final amount.
     * Quy tắc:
     * - Voucher vendor: vendor chịu giảm giá -> cơ sở hoa hồng = subtotal - vendorDiscount.
     * - Voucher sàn: sàn trợ giá -> cơ sở hoa hồng = subtotal, vendor nhận đủ (subtotal - commission).
     * - Final amount (khách trả) = subtotal - (vendorDiscount + platformDiscount).
     */
    public void applyDiscount(BigDecimal vendorDiscount, BigDecimal platformDiscount) {
        this.vendorDiscountAmount = vendorDiscount != null ? vendorDiscount : BigDecimal.ZERO;
        this.platformDiscountAmount = platformDiscount != null ? platformDiscount : BigDecimal.ZERO;
        this.discountAmount = this.vendorDiscountAmount.add(this.platformDiscountAmount);

        BigDecimal subtotal = this.subtotalAmount != null ? this.subtotalAmount : BigDecimal.ZERO;
        this.finalAmount = subtotal.subtract(this.discountAmount).max(BigDecimal.ZERO);

        this.commissionBasisAmount = subtotal.subtract(this.vendorDiscountAmount).max(BigDecimal.ZERO);

        BigDecimal rate = this.commissionRate != null ? this.commissionRate : BigDecimal.ZERO;
        this.commissionAmount = this.commissionBasisAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        this.vendorPayoutAmount = this.commissionBasisAmount.subtract(this.commissionAmount);
    }

    /**
     * Tự bảo vệ tính đúng đắn khi chuyển trạng thái của SubOrder.
     */
    public void assertValidTransition(SubOrderStatus newStatus) {
        if (this.status == newStatus) {
            return;
        }
        if (this.status != null && this.status.isTerminal()) {
            throw new InvalidOrderStateException(
                    "Cannot transition sub-order from terminal state " + this.status + " to " + newStatus);
        }
        if (this.status == SubOrderStatus.PENDING) {
            if (newStatus != SubOrderStatus.CONFIRMED
                    && newStatus != SubOrderStatus.CANCELLED
                    && newStatus != SubOrderStatus.REJECTED) {
                throw new InvalidOrderStateException(
                        "SubOrder in PENDING can only transition to CONFIRMED, CANCELLED, or REJECTED; requested: " + newStatus);
            }
        }
    }

    public void markConfirmed() {
        assertValidTransition(SubOrderStatus.CONFIRMED);
        this.status = SubOrderStatus.CONFIRMED;
    }

    public void cancel() {
        assertValidTransition(SubOrderStatus.CANCELLED);
        this.status = SubOrderStatus.CANCELLED;
    }

    public void markVendorNotified(OffsetDateTime timestamp) {
        this.vendorNotifiedAt = timestamp;
    }
}
