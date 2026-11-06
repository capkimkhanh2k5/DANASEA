package com.danasea.backend.modules.communication.infrastructure.listeners;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.communication.application.dtos.NotificationCommand;
import com.danasea.backend.modules.communication.application.email.EmailRequest;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailPort;
import com.danasea.backend.modules.communication.application.usecases.SendNotificationUseCase;
import com.danasea.backend.modules.communication.domain.models.NotificationChannel;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;
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
    private final TransactionalEmailPort transactionalEmailPort;

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
        OffsetDateTime now = OffsetDateTime.now();

        notifications.executeOnce(new NotificationCommand(customerId, "PAYMENT_SUCCESS", NotificationChannel.IN_APP,
                LocalizedMessageRef.of("notification.payment.success.title"),
                LocalizedMessageRef.of("notification.payment.success.body"), "ORDER", order.getId(), null),
                "payment:" + order.getId() + ":customer:" + customerId);

        // Transactional outbox for customer confirmation
        transactionalEmailPort.enqueue(EmailRequest.builder()
                .type(TransactionalEmailType.BOOKING_CONFIRMED)
                .businessKey("order:" + order.getId())
                .recipientKey("USER:" + customerId)
                .occurredAt(now)
                .dueAt(now)
                .expiresAt(now.plusHours(24))
                .payloadVersion(1)
                .payload(Map.of("orderId", order.getId().toString()))
                .build());

        Set<UUID> notifiedVendors = new HashSet<>();
        for (var subOrder : subOrders.findByMasterOrderId(order.getId())) {
            if (subOrder.getStatus() != SubOrderStatus.CONFIRMED) continue;
            UUID vendorId = subOrder.getVendorId();
            if (vendorId == null || !notifiedVendors.add(vendorId)) {
                continue;
            }
            vendors.findById(vendorId).filter(vendor -> vendor.getUserId() != null).ifPresent(vendor -> {
                notifications.executeOnce(new NotificationCommand(vendor.getUserId(), "NEW_BOOKING_ORDER",
                        NotificationChannel.IN_APP, LocalizedMessageRef.of("notification.booking.new.title"),
                        LocalizedMessageRef.of("notification.booking.new.body"), "SUB_ORDER", subOrder.getId(), null),
                        "payment:" + order.getId() + ":vendor:" + vendorId);

                // Transactional outbox for vendor confirmation
                transactionalEmailPort.enqueue(EmailRequest.builder()
                        .type(TransactionalEmailType.VENDOR_BOOKING_CONFIRMED)
                        .businessKey("order:" + order.getId() + ":vendor:" + vendorId)
                        .recipientKey("USER:" + vendor.getUserId())
                        .occurredAt(now)
                        .dueAt(now)
                        .expiresAt(now.plusHours(24))
                        .payloadVersion(1)
                        .payload(Map.of("orderId", order.getId().toString(), "vendorId", vendorId.toString()))
                        .build());
            });
        }
    }
}
