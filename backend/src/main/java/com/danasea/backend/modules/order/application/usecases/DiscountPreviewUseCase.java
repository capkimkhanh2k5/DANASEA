package com.danasea.backend.modules.order.application.usecases;

import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.exceptions.UnauthorizedOrderAccessException;
import com.danasea.backend.modules.order.domain.models.DiscountCode;
import com.danasea.backend.modules.order.domain.ports.BookingLookupPort;
import com.danasea.backend.modules.order.domain.ports.BookingOrderView;
import com.danasea.backend.modules.order.domain.ports.CommissionPolicyPort;
import com.danasea.backend.modules.order.domain.services.DiscountAllocationEngine;
import com.danasea.backend.modules.order.domain.services.DiscountAllocationEngine.AllocationResult;
import com.danasea.backend.modules.order.domain.services.DiscountAllocationEngine.CandidateItem;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.DiscountCodeJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.mappers.DiscountCodeMapper;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaDiscountCodeRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaDiscountRedemptionRepository;
import com.danasea.backend.modules.order.presentation.dtos.DiscountPreviewRequest;
import com.danasea.backend.modules.order.presentation.dtos.DiscountPreviewResponse;
import com.danasea.backend.modules.order.presentation.dtos.ItemDiscountPreviewResponse;
import com.danasea.backend.shared.i18n.LocalizedMessageService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class DiscountPreviewUseCase {
    private com.danasea.backend.modules.booking.domain.ports.FlashSaleBookingPort flashSale;
    @org.springframework.beans.factory.annotation.Autowired
    public void setFlashSale(com.danasea.backend.modules.booking.domain.ports.FlashSaleBookingPort flashSale) { this.flashSale=flashSale; }

    private final BookingLookupPort bookingLookupPort;
    private LocalizedMessageService messages = LocalizedMessageService.standalone();

    @Autowired
    void setMessages(LocalizedMessageService messages) {
        this.messages = messages;
    }
    private final JpaDiscountCodeRepository discountCodeRepository;
    private final JpaDiscountRedemptionRepository discountRedemptionRepository;
    private final CommissionPolicyPort commissionPolicyPort;
    private final DiscountAllocationEngine discountAllocationEngine;
    private final DiscountCodeMapper discountCodeMapper;

    public DiscountPreviewUseCase(
            BookingLookupPort bookingLookupPort,
            JpaDiscountCodeRepository discountCodeRepository,
            JpaDiscountRedemptionRepository discountRedemptionRepository,
            CommissionPolicyPort commissionPolicyPort,
            DiscountAllocationEngine discountAllocationEngine,
            DiscountCodeMapper discountCodeMapper
    ) {
        this.bookingLookupPort = bookingLookupPort;
        this.discountCodeRepository = discountCodeRepository;
        this.discountRedemptionRepository = discountRedemptionRepository;
        this.commissionPolicyPort = commissionPolicyPort;
        this.discountAllocationEngine = discountAllocationEngine;
        this.discountCodeMapper = discountCodeMapper;
    }

    @Transactional(readOnly = true)
    public DiscountPreviewResponse execute(UUID customerId, DiscountPreviewRequest request) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID is required.");
        }
        if (request == null || request.bookingId() == null) {
            throw new IllegalArgumentException("Booking ID is required.");
        }
        if (request.code() == null || request.code().isBlank()) {
            return DiscountPreviewResponse.invalid("EMPTY_CODE", messages.get("error.empty_code"), "");
        }

        // 1. Kiểm tra Booking
        BookingOrderView booking = bookingLookupPort.findBookingForOrder(request.bookingId())
                .orElseThrow(() -> new OrderNotFoundException("Booking not found with id: " + request.bookingId()));

        if (!booking.customerId().equals(customerId)) {
            throw new UnauthorizedOrderAccessException(request.bookingId(), customerId);
        }

        if (!"HOLD".equalsIgnoreCase(booking.status())
                || (booking.holdExpiresAt() != null && !booking.holdExpiresAt().isAfter(OffsetDateTime.now()))) {
            return DiscountPreviewResponse.invalid("BOOKING_NOT_ELIGIBLE_FOR_ORDER",
                    messages.get("error.booking_not_eligible_for_order"), request.code());
        }

        if (booking.items() == null || booking.items().isEmpty()) {
            return DiscountPreviewResponse.invalid("EMPTY_ITEMS", messages.get("error.empty_items"), request.code());
        }

        if(flashSale!=null && flashSale.hasAllocation(request.bookingId()))
            throw new com.danasea.backend.modules.flashsale.domain.FlashSaleException("FLASH_SALE_VOUCHER_STACKING_NOT_ALLOWED");

        // 2. Tìm Discount Code
        String cleanCode = request.code().trim().toUpperCase(Locale.ROOT);
        Optional<DiscountCodeJpaEntity> codeOpt = discountCodeRepository.findByCodeIgnoreCase(cleanCode);
        if (codeOpt.isEmpty()) {
            return DiscountPreviewResponse.invalid("DISCOUNT_NOT_FOUND", messages.get("error.discount_not_found"), cleanCode);
        }

        DiscountCodeJpaEntity codeEntity = codeOpt.get();
        DiscountCode discountCode = discountCodeMapper.toDomain(codeEntity);

        // 3. Đếm số lần user đã sử dụng mã này
        long userUsageCount = discountRedemptionRepository.countByDiscountCodeIdAndCustomerId(codeEntity.getId(), customerId);

        // 4. Chuẩn bị danh sách CandidateItem
        List<CandidateItem> candidateItems = new ArrayList<>();
        for (BookingOrderView.BookingItemOrderView item : booking.items()) {
            BigDecimal rate = commissionPolicyPort != null ? commissionPolicyPort.getCommissionRate(item.vendorId()) : null;
            if (rate == null) {
                throw new com.danasea.backend.modules.flashsale.domain.FlashSaleException("COMMISSION_POLICY_MISSING");
            }
            BigDecimal subtotal = item.price().multiply(BigDecimal.valueOf(item.quantity()));
            candidateItems.add(new CandidateItem(
                    item.bookingItemId(),
                    item.vendorId(),
                    item.serviceId(),
                    subtotal,
                    rate
            ));
        }

        // 5. Phân bổ giảm giá qua Allocation Engine
        AllocationResult result = discountAllocationEngine.evaluateAndAllocate(
                discountCode,
                candidateItems,
                OffsetDateTime.now(),
                userUsageCount,
                customerId
        );

        if (!result.valid()) {
            return DiscountPreviewResponse.invalid(result.errorCode(),
                    messages.get("error." + result.errorCode().toLowerCase(Locale.ROOT)), cleanCode);
        }

        if (result.finalPayableAmount().signum() <= 0) {
            return DiscountPreviewResponse.invalid("DISCOUNT_ZERO_PAYABLE_UNSUPPORTED",
                    messages.get("error.discount_zero_payable_unsupported"), cleanCode);
        }

        List<ItemDiscountPreviewResponse> itemBreakdown = result.itemAllocations().stream()
                .map(alloc -> new ItemDiscountPreviewResponse(
                        alloc.itemId(),
                        alloc.vendorId(),
                        alloc.serviceId(),
                        alloc.originalSubtotal(),
                        alloc.eligible(),
                        alloc.vendorDiscountAmount(),
                        alloc.platformDiscountAmount(),
                        alloc.totalDiscountAmount(),
                        alloc.finalSubtotal()
                ))
                .toList();

        BigDecimal originalTotal = candidateItems.stream()
                .map(CandidateItem::subtotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DiscountPreviewResponse(
                true,
                null,
                messages.get("discount.applied"),
                cleanCode,
                discountCode.getScope(),
                discountCode.getSponsorType(),
                originalTotal,
                result.totalDiscountAmount(),
                result.finalPayableAmount(),
                itemBreakdown
        );
    }
}
