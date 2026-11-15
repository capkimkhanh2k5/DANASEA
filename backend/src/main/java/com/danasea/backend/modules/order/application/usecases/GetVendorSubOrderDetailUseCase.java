package com.danasea.backend.modules.order.application.usecases;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingItemJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingItemRepository;
import com.danasea.backend.modules.order.domain.exceptions.SubOrderNotFoundException;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.order.presentation.dtos.VendorSubOrderDetailResponse;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceOptionJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceOptionRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.Vendor;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetVendorSubOrderDetailUseCase {

    private final JpaSubOrderRepository subOrderRepository;
    private final JpaMasterOrderRepository masterOrderRepository;
    private final VendorInternalApi vendorInternalApi;
    private final AccountInternalApi accountInternalApi;
    private final JpaServiceRepository serviceRepository;
    private final JpaServiceOptionRepository serviceOptionRepository;
    private final JpaBookingItemRepository bookingItemRepository;
    private final JpaServiceSlotRepository serviceSlotRepository;

    @Transactional(readOnly = true)
    public VendorSubOrderDetailResponse execute(UUID userId, UUID subOrderId) {
        if (userId == null || subOrderId == null) {
            throw new SubOrderNotFoundException("Sub-order not found");
        }

        Vendor vendor = vendorInternalApi.findByUserId(userId)
                .orElseThrow(() -> new SubOrderNotFoundException(subOrderId));

        SubOrderJpaEntity subOrder = subOrderRepository.findById(subOrderId)
                .orElseThrow(() -> new SubOrderNotFoundException(subOrderId));

        if (!vendor.getId().equals(subOrder.getVendorId())) {
            throw new SubOrderNotFoundException(subOrderId);
        }

        MasterOrderJpaEntity masterOrder = subOrder.getMasterOrderId() != null
                ? masterOrderRepository.findById(subOrder.getMasterOrderId()).orElse(null)
                : null;

        User customer = (masterOrder != null && masterOrder.getCustomerId() != null)
                ? accountInternalApi.findUserById(masterOrder.getCustomerId()).orElse(null)
                : null;

        ServiceJpaEntity service = subOrder.getServiceId() != null
                ? serviceRepository.findById(subOrder.getServiceId()).orElse(null)
                : null;

        BookingItemJpaEntity bookingItem = subOrder.getBookingItemId() != null
                ? bookingItemRepository.findById(subOrder.getBookingItemId()).orElse(null)
                : null;

        ServiceSlotJpaEntity slot = subOrder.getSlotId() != null
                ? serviceSlotRepository.findById(subOrder.getSlotId()).orElse(null)
                : null;

        UUID optionId = bookingItem != null ? bookingItem.getOptionId() : null;
        ServiceOptionJpaEntity option = optionId != null
                ? serviceOptionRepository.findById(optionId).orElse(null)
                : null;

        LocalDate bookingDate = bookingItem != null
                ? bookingItem.getBookingDate()
                : (slot != null ? slot.getDate() : null);

        LocalTime bookingTime = bookingItem != null
                ? bookingItem.getBookingTime()
                : (slot != null ? slot.getStartTime() : null);

        LocalTime endTime = slot != null ? slot.getEndTime() : null;

        return VendorSubOrderDetailResponse.builder()
                .id(subOrder.getId())
                .masterOrderId(subOrder.getMasterOrderId())
                .bookingItemId(subOrder.getBookingItemId())
                .status(subOrder.getStatus())
                .masterOrderStatus(masterOrder != null ? masterOrder.getStatus() : null)
                .createdAt(subOrder.getCreatedAt())
                .updatedAt(subOrder.getUpdatedAt())
                .serviceId(subOrder.getServiceId())
                .serviceName(service != null ? service.getName() : null)
                .optionId(optionId)
                .optionName(option != null ? option.getName() : null)
                .slotId(subOrder.getSlotId())
                .bookingDate(bookingDate)
                .bookingTime(bookingTime)
                .endTime(endTime)
                .customerId(customer != null ? customer.getId() : (masterOrder != null ? masterOrder.getCustomerId() : null))
                .customerName(customer != null ? customer.getFullName() : null)
                .customerPhone(customer != null ? customer.getPhone() : null)
                .customerEmail(customer != null ? customer.getEmail() : null)
                .quantity(subOrder.getQuantity())
                .unitPrice(subOrder.getUnitPrice())
                .subtotalAmount(subOrder.getSubtotalAmount())
                .discountAmount(subOrder.getDiscountAmount())
                .vendorDiscountAmount(subOrder.getVendorDiscountAmount())
                .platformDiscountAmount(subOrder.getPlatformDiscountAmount())
                .finalAmount(subOrder.getFinalAmount())
                .commissionRate(subOrder.getCommissionRate())
                .commissionAmount(subOrder.getCommissionAmount())
                .vendorPayoutAmount(subOrder.getVendorPayoutAmount())
                .checkedIn(subOrder.getCheckedInAt() != null)
                .checkedInAt(subOrder.getCheckedInAt())
                .waiverRequired(subOrder.getWaiverRequired())
                .waiverAccepted(subOrder.getWaiverAccepted())
                .waiverAcceptedAt(subOrder.getWaiverAcceptedAt())
                .rescheduledAt(subOrder.getRescheduledAt())
                .originalSlotId(subOrder.getOriginalSlotId())
                .cancellationReason(subOrder.getCancellationReason())
                .paymentStatus(masterOrder != null ? masterOrder.getPaymentStatus() : null)
                .build();
    }
}
