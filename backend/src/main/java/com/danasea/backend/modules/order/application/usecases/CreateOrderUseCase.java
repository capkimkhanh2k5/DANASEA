package com.danasea.backend.modules.order.application.usecases;

import com.danasea.backend.modules.order.application.dtos.CreateOrderCommand;
import com.danasea.backend.modules.order.application.dtos.MasterOrderDetailResult;
import com.danasea.backend.modules.order.domain.exceptions.BookingNotEligibleForOrderException;
import com.danasea.backend.modules.order.domain.exceptions.InvalidDiscountException;
import com.danasea.backend.modules.order.domain.exceptions.InvalidOrderStateException;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.exceptions.UnauthorizedOrderAccessException;
import com.danasea.backend.modules.order.domain.models.DiscountCode;
import com.danasea.backend.modules.order.domain.models.MasterOrder;
import com.danasea.backend.modules.order.domain.models.SubOrder;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.domain.ports.BookingLookupPort;
import com.danasea.backend.modules.order.domain.ports.BookingOrderView;
import com.danasea.backend.modules.order.domain.ports.BookingStatusUpdatePort;
import com.danasea.backend.modules.order.domain.ports.CommissionPolicyPort;
import com.danasea.backend.modules.order.domain.ports.MasterOrderRepositoryPort;
import com.danasea.backend.modules.order.domain.ports.OrderEventPublisherPort;
import com.danasea.backend.modules.order.domain.ports.ServiceWaiverLookupPort;
import com.danasea.backend.modules.order.domain.ports.SubOrderRepositoryPort;
import com.danasea.backend.modules.order.domain.services.DiscountAllocationEngine;
import com.danasea.backend.modules.order.domain.services.DiscountAllocationEngine.AllocationResult;
import com.danasea.backend.modules.order.domain.services.DiscountAllocationEngine.CandidateItem;
import com.danasea.backend.modules.order.domain.services.DiscountAllocationEngine.ItemAllocation;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.DiscountCodeJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.DiscountRedemptionJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.mappers.DiscountCodeMapper;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaDiscountCodeRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaDiscountRedemptionRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CreateOrderUseCase {
    private com.danasea.backend.modules.booking.domain.ports.FlashSaleBookingPort flashSale;
    @Autowired
    public void setFlashSale(com.danasea.backend.modules.booking.domain.ports.FlashSaleBookingPort flashSale) { this.flashSale=flashSale; }

    private final MasterOrderRepositoryPort masterOrderRepository;
    private final SubOrderRepositoryPort subOrderRepository;
    private final BookingLookupPort bookingLookupPort;
    private final BookingStatusUpdatePort bookingStatusUpdatePort;
    private final CommissionPolicyPort commissionPolicyPort;
    private final OrderEventPublisherPort orderEventPublisherPort;
    private final JpaDiscountCodeRepository discountCodeRepository;
    private final JpaDiscountRedemptionRepository discountRedemptionRepository;
    private final DiscountAllocationEngine discountAllocationEngine;
    private final DiscountCodeMapper discountCodeMapper;
    private final ServiceWaiverLookupPort serviceWaiverLookupPort;

    @Autowired
    public CreateOrderUseCase(
            MasterOrderRepositoryPort masterOrderRepository,
            SubOrderRepositoryPort subOrderRepository,
            BookingLookupPort bookingLookupPort,
            BookingStatusUpdatePort bookingStatusUpdatePort,
            CommissionPolicyPort commissionPolicyPort,
            OrderEventPublisherPort orderEventPublisherPort,
            @Autowired(required = false) JpaDiscountCodeRepository discountCodeRepository,
            @Autowired(required = false) JpaDiscountRedemptionRepository discountRedemptionRepository,
            @Autowired(required = false) DiscountAllocationEngine discountAllocationEngine,
            @Autowired(required = false) DiscountCodeMapper discountCodeMapper,
            @Autowired(required = false) ServiceWaiverLookupPort serviceWaiverLookupPort) {
        this.masterOrderRepository = masterOrderRepository;
        this.subOrderRepository = subOrderRepository;
        this.bookingLookupPort = bookingLookupPort;
        this.bookingStatusUpdatePort = bookingStatusUpdatePort;
        this.commissionPolicyPort = commissionPolicyPort;
        this.orderEventPublisherPort = orderEventPublisherPort;
        this.discountCodeRepository = discountCodeRepository;
        this.discountRedemptionRepository = discountRedemptionRepository;
        this.discountAllocationEngine = discountAllocationEngine;
        this.discountCodeMapper = discountCodeMapper;
        this.serviceWaiverLookupPort = serviceWaiverLookupPort;
    }

    public CreateOrderUseCase(
            MasterOrderRepositoryPort masterOrderRepository,
            SubOrderRepositoryPort subOrderRepository,
            BookingLookupPort bookingLookupPort,
            BookingStatusUpdatePort bookingStatusUpdatePort,
            CommissionPolicyPort commissionPolicyPort,
            OrderEventPublisherPort orderEventPublisherPort,
            JpaDiscountCodeRepository discountCodeRepository,
            JpaDiscountRedemptionRepository discountRedemptionRepository,
            DiscountAllocationEngine discountAllocationEngine,
            DiscountCodeMapper discountCodeMapper) {
        this(masterOrderRepository, subOrderRepository, bookingLookupPort, bookingStatusUpdatePort,
                commissionPolicyPort, orderEventPublisherPort, discountCodeRepository,
                discountRedemptionRepository, discountAllocationEngine, discountCodeMapper, null);
    }

    public CreateOrderUseCase(
            MasterOrderRepositoryPort masterOrderRepository,
            SubOrderRepositoryPort subOrderRepository,
            BookingLookupPort bookingLookupPort,
            BookingStatusUpdatePort bookingStatusUpdatePort,
            CommissionPolicyPort commissionPolicyPort,
            OrderEventPublisherPort orderEventPublisherPort) {
        this(masterOrderRepository, subOrderRepository, bookingLookupPort, bookingStatusUpdatePort,
                commissionPolicyPort, orderEventPublisherPort, null, null, null, null);
    }

    @Transactional
    public MasterOrderDetailResult execute(CreateOrderCommand command) {
        if (command == null || command.customerId() == null || command.bookingId() == null) {
            throw new IllegalArgumentException("Customer ID and booking ID are required.");
        }
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required.");
        }

        // 1. Kiểm tra Idempotency theo bookingId
        Optional<MasterOrder> existingByBooking = masterOrderRepository.findByBookingId(command.bookingId());
        if (existingByBooking.isPresent()) {
            MasterOrder order = existingByBooking.get();
            if (!order.getCustomerId().equals(command.customerId())) {
                throw new UnauthorizedOrderAccessException(command.bookingId(), command.customerId());
            }
            order.setSubOrders(subOrderRepository.findByMasterOrderId(order.getId()));
            return MasterOrderDetailResult.fromDomain(order);
        }

        // 2. Kiểm tra Idempotency theo (customerId, idempotencyKey)
        Optional<MasterOrder> existingByKey = masterOrderRepository.findByCustomerIdAndIdempotencyKey(
                command.customerId(), command.idempotencyKey());
        if (existingByKey.isPresent()) {
            MasterOrder order = existingByKey.get();
            if (!order.getCustomerId().equals(command.customerId())) {
                throw new UnauthorizedOrderAccessException(command.bookingId(), command.customerId());
            }
            if (!command.bookingId().equals(order.getBookingId())) {
                throw new InvalidOrderStateException("The idempotency key was already used for another booking.");
            }
            order.setSubOrders(subOrderRepository.findByMasterOrderId(order.getId()));
            return MasterOrderDetailResult.fromDomain(order);
        }

        // 3. Tra cứu thông tin Booking qua Port
        BookingOrderView booking = bookingLookupPort.findBookingForOrderForUpdate(command.bookingId())
                .orElseThrow(() -> new OrderNotFoundException("Booking not found with id: " + command.bookingId()));

        // Another request may have created the order while this request waited for the booking lock.
        Optional<MasterOrder> concurrentOrder = masterOrderRepository.findByBookingId(command.bookingId());
        if (concurrentOrder.isPresent()) {
            MasterOrder existing = concurrentOrder.get();
            if (!existing.getCustomerId().equals(command.customerId())) {
                throw new UnauthorizedOrderAccessException(command.bookingId(), command.customerId());
            }
            existing.setSubOrders(subOrderRepository.findByMasterOrderId(existing.getId()));
            return MasterOrderDetailResult.fromDomain(existing);
        }

        // 4. Kiểm tra quyền sở hữu (chặn IDOR 403)
        if (!booking.customerId().equals(command.customerId())) {
            throw new UnauthorizedOrderAccessException(command.bookingId(), command.customerId());
        }

        // 5. Kiểm tra trạng thái Booking: chỉ cho phép từ trạng thái HOLD
        if (!"HOLD".equalsIgnoreCase(booking.status())) {
            throw new BookingNotEligibleForOrderException(
                    command.bookingId(), "Booking is not in HOLD status (current: " + booking.status() + ")");
        }

        // 6. Kiểm tra TTL giữ chỗ
        OffsetDateTime now = OffsetDateTime.now();
        if (booking.holdExpiresAt() != null && now.isAfter(booking.holdExpiresAt())) {
            throw new BookingNotEligibleForOrderException(
                    command.bookingId(), "Booking hold expired at " + booking.holdExpiresAt());
        }

        // 7. Kiểm tra danh sách Booking Items
        if (booking.items() == null || booking.items().isEmpty()) {
            throw new BookingNotEligibleForOrderException(command.bookingId(), "Booking contains no items.");
        }

        if(flashSale!=null && command.discountCode()!=null && !command.discountCode().isBlank() && flashSale.hasAllocation(command.bookingId()))
            throw new com.danasea.backend.modules.flashsale.domain.FlashSaleException("FLASH_SALE_VOUCHER_STACKING_NOT_ALLOWED");

        // 8. Đánh giá Voucher / Discount Code (nếu có yêu cầu áp dụng)
        DiscountCodeJpaEntity appliedCodeEntity = null;
        AllocationResult allocationResult = null;

        if (command.discountCode() != null && !command.discountCode().isBlank()) {
            if (discountCodeRepository == null || discountAllocationEngine == null || discountCodeMapper == null) {
                throw new InvalidOrderStateException("Discount processing is not configured.");
            }

            String cleanCode = command.discountCode().trim().toUpperCase(Locale.ROOT);
            appliedCodeEntity = discountCodeRepository.findByCodeForUpdate(cleanCode)
                    .orElseThrow(() -> new InvalidDiscountException("DISCOUNT_NOT_FOUND", "Discount code was not found."));

            DiscountCode discountCode = discountCodeMapper.toDomain(appliedCodeEntity);

            long userUsageCount = 0;
            if (discountRedemptionRepository != null) {
                userUsageCount = discountRedemptionRepository.countByDiscountCodeIdAndCustomerId(appliedCodeEntity.getId(), command.customerId());
            }

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

            allocationResult = discountAllocationEngine.evaluateAndAllocate(
                    discountCode,
                    candidateItems,
                    now,
                    userUsageCount,
                    command.customerId()
            );

            if (!allocationResult.valid()) {
                throw new InvalidDiscountException(allocationResult.errorCode(), allocationResult.message());
            }

            if (allocationResult.finalPayableAmount().signum() <= 0) {
                throw new InvalidDiscountException("DISCOUNT_ZERO_PAYABLE_UNSUPPORTED",
                        "Discount must leave a positive payable amount.");
            }

            // The voucher lock serializes global and per-customer reservations.
            int updated = discountCodeRepository.incrementUsedCount(appliedCodeEntity.getId());
            if (updated == 0) {
                throw new InvalidDiscountException("DISCOUNT_QUOTA_EXCEEDED", "Discount code usage quota is exhausted.");
            }
        }

        // 9. Khởi tạo MasterOrder
        MasterOrder order = MasterOrder.createFromBooking(
                command.customerId(),
                command.bookingId(),
                allocationResult != null ? allocationResult.finalPayableAmount() : booking.totalAmount(),
                booking.holdExpiresAt(),
                command.idempotencyKey()
        );
        if (allocationResult != null && appliedCodeEntity != null) {
            order.setDiscountCodeId(appliedCodeEntity.getId());
            order.setDiscountAmount(allocationResult.totalDiscountAmount());
        }
        order = masterOrderRepository.save(order);

        // 10. Tách Sub-Orders theo quy tắc 1 SubOrder tương ứng với 1 BookingItem và phân bổ discount
        Map<UUID, ItemAllocation> allocMap = allocationResult != null
                ? allocationResult.itemAllocations().stream().collect(Collectors.toMap(ItemAllocation::itemId, Function.identity()))
                : Collections.emptyMap();

        List<SubOrder> subOrders = new ArrayList<>();
        for (BookingOrderView.BookingItemOrderView item : booking.items()) {
            var promotion=flashSale!=null?flashSale.snapshot(item.bookingItemId()).orElse(null):null;
            BigDecimal rate = promotion!=null?promotion.commissionRate():commissionPolicyPort != null ? commissionPolicyPort.getCommissionRate(item.vendorId()) : null;
            if (rate == null) {
                throw new com.danasea.backend.modules.flashsale.domain.FlashSaleException("COMMISSION_POLICY_MISSING");
            }
            BigDecimal subtotal = item.price().multiply(BigDecimal.valueOf(item.quantity()));

            SubOrder subOrder = new SubOrder();
            subOrder.setBookingItemId(item.bookingItemId());
            subOrder.setMasterOrderId(order.getId());
            subOrder.setVendorId(item.vendorId());
            subOrder.setServiceId(item.serviceId());
            subOrder.setSlotId(item.slotId());
            subOrder.setQuantity(item.quantity());
            subOrder.setUnitPrice(item.price());
            subOrder.setSubtotalAmount(subtotal);
            subOrder.setCommissionRate(rate);

            ItemAllocation alloc = allocMap.get(item.bookingItemId());
            if (alloc != null && alloc.eligible()) {
                subOrder.applyDiscount(alloc.vendorDiscountAmount(), alloc.platformDiscountAmount());
            } else {
                BigDecimal commission = subtotal.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                BigDecimal vendorPayout = subtotal.subtract(commission);

                subOrder.setCommissionBasisAmount(subtotal);
                subOrder.setCommissionAmount(commission);
                subOrder.setVendorPayoutAmount(vendorPayout);
                subOrder.setDiscountAmount(BigDecimal.ZERO);
                subOrder.setVendorDiscountAmount(BigDecimal.ZERO);
                subOrder.setPlatformDiscountAmount(BigDecimal.ZERO);
                subOrder.setFinalAmount(subtotal);
            }

            if(promotion!=null) {
                subOrder.setUnitPrice(promotion.originalUnitPrice());
                subOrder.setSubtotalAmount(promotion.originalAmount());
                subOrder.setCommissionBasisAmount(promotion.originalAmount());
                subOrder.setCommissionRate(promotion.commissionRate());
                subOrder.setCommissionAmount(promotion.commissionAmount());
                subOrder.setVendorPayoutAmount(promotion.vendorReceivable());
                subOrder.setVendorDiscountAmount(BigDecimal.ZERO);
                subOrder.setPlatformDiscountAmount(promotion.subsidyAmount());
                subOrder.setDiscountAmount(promotion.subsidyAmount());
                subOrder.setFinalAmount(promotion.customerPayable());
            }
            subOrder.setStatus(SubOrderStatus.PENDING);
            subOrder.setWaiverAccepted(false);

            if (serviceWaiverLookupPort != null) {
                serviceWaiverLookupPort.findWaiverSnapshot(item.serviceId()).ifPresent(snapshot -> {
                    subOrder.setWaiverRequired(snapshot.waiverRequired());
                    subOrder.setWaiverVersion(snapshot.waiverVersion());
                    subOrder.setWaiverContent(snapshot.waiverContent());
                    subOrder.setWaiverContentEn(snapshot.waiverContentEn());
                });
            }

            subOrders.add(subOrder);
        }
        subOrders = subOrderRepository.saveAll(subOrders);
        order.setSubOrders(subOrders);
        if(flashSale!=null && flashSale.hasAllocation(command.bookingId())) {
            order.setDiscountAmount(subOrders.stream().map(SubOrder::getPlatformDiscountAmount).reduce(BigDecimal.ZERO,BigDecimal::add));
            order=masterOrderRepository.save(order);
        }

        // 11. Ghi nhận Discount Redemption
        if (allocationResult != null && appliedCodeEntity != null && discountRedemptionRepository != null) {
            DiscountRedemptionJpaEntity redemption = new DiscountRedemptionJpaEntity();
            redemption.setId(UUID.randomUUID());
            redemption.setDiscountCodeId(appliedCodeEntity.getId());
            redemption.setMasterOrderId(order.getId());
            redemption.setCustomerId(command.customerId());
            redemption.setAmountDeducted(allocationResult.totalDiscountAmount());
            discountRedemptionRepository.save(redemption);
        }

        // 12. Chuyển trạng thái Booking sang PENDING_PAYMENT (trong cùng Transaction)
        bookingStatusUpdatePort.updateStatusToPendingPayment(command.bookingId());

        // 13. Bắn sự kiện Order Created
        orderEventPublisherPort.publishOrderCreatedEvent(order);

        return MasterOrderDetailResult.fromDomain(order);
    }
}
