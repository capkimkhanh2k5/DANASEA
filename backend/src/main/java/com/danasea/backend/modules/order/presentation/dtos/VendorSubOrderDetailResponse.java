package com.danasea.backend.modules.order.presentation.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorSubOrderDetailResponse {

    // A. Order Info
    private UUID id;
    private UUID masterOrderId;
    private UUID bookingItemId;
    private SubOrderStatus status;
    private MasterOrderStatus masterOrderStatus;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // B. Service & Slot Info
    private UUID serviceId;
    private String serviceName;
    private UUID optionId;
    private String optionName;
    private UUID slotId;
    private LocalDate bookingDate;
    private LocalTime bookingTime;
    private LocalTime endTime;

    // C. Customer Info
    private UUID customerId;
    private String customerName;
    private String customerPhone;
    private String customerEmail;

    // D. Financials
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal vendorDiscountAmount;
    private BigDecimal platformDiscountAmount;
    private BigDecimal finalAmount;
    private BigDecimal commissionRate;
    private BigDecimal commissionAmount;
    private BigDecimal vendorPayoutAmount;

    // E. Operations & Check-in
    private boolean checkedIn;
    private OffsetDateTime checkedInAt;
    private Boolean waiverRequired;
    private Boolean waiverAccepted;
    private OffsetDateTime waiverAcceptedAt;
    private OffsetDateTime rescheduledAt;
    private UUID originalSlotId;
    private RefundReason cancellationReason;

    // F. Payment
    private PaymentOrderStatus paymentStatus;
}
