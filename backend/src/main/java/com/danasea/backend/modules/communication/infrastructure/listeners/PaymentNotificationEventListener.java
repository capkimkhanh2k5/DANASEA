package com.danasea.backend.modules.communication.infrastructure.listeners;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.communication.application.dtos.NotificationCommand;
import com.danasea.backend.modules.communication.application.usecases.SendNotificationUseCase;
import com.danasea.backend.modules.communication.domain.models.NotificationChannel;
import com.danasea.backend.modules.order.domain.events.PaymentSuccessEvent;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import com.danasea.backend.shared.i18n.LocalizedMessageRef;

import lombok.RequiredArgsConstructor;

@Component("paymentNotificationEventListener")
@RequiredArgsConstructor
public class PaymentNotificationEventListener {
    private final SendNotificationUseCase notifications;
    private final JpaMasterOrderRepository masterOrders;
    private final JpaSubOrderRepository subOrders;
    private final JpaVendorRepository vendors;

    @EventListener
    @Transactional
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }
        var order = masterOrders.findById(event.orderId()).orElse(null);
        if (order == null || order.getPaymentStatus() != PaymentOrderStatus.PAID) {
            return;
        }
        UUID customerId = order.getCustomerId();
        notifications.executeOnce(new NotificationCommand(customerId, "PAYMENT_SUCCESS", NotificationChannel.IN_APP,
                LocalizedMessageRef.of("notification.payment.success.title"),
                LocalizedMessageRef.of("notification.payment.success.body"), "ORDER", order.getId(), null),
                "payment:" + order.getId() + ":customer:" + customerId);

        Set<UUID> notifiedVendors = new HashSet<>();
        for (var subOrder : subOrders.findByMasterOrderId(order.getId())) {
            if (subOrder.getStatus() != SubOrderStatus.CONFIRMED) continue;
            UUID vendorId = subOrder.getVendorId();
            if (vendorId == null || !notifiedVendors.add(vendorId)) {
                continue;
            }
            vendors.findById(vendorId).filter(vendor -> vendor.getUserId() != null).ifPresent(vendor ->
                    notifications.executeOnce(new NotificationCommand(vendor.getUserId(), "NEW_BOOKING_ORDER",
                            NotificationChannel.IN_APP, LocalizedMessageRef.of("notification.booking.new.title"),
                            LocalizedMessageRef.of("notification.booking.new.body"), "SUB_ORDER", subOrder.getId(), null),
                            "payment:" + order.getId() + ":vendor:" + vendorId));
        }
    }
}
