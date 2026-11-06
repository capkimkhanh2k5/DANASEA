package com.danasea.backend.modules.communication.infrastructure.listeners;

import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.communication.application.dtos.NotificationCommand;
import com.danasea.backend.modules.communication.application.email.EmailRequest;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailPort;
import com.danasea.backend.modules.communication.application.usecases.SendNotificationUseCase;
import com.danasea.backend.modules.communication.domain.models.NotificationChannel;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;
import com.danasea.backend.modules.order.domain.events.RefundCompletedEvent;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.shared.i18n.LocalizedMessageRef;

import lombok.RequiredArgsConstructor;

@Component("refundNotificationEventListener")
@RequiredArgsConstructor
public class RefundNotificationEventListener {
    private final SendNotificationUseCase notifications;

    @Autowired(required = false)
    private TransactionalEmailPort transactionalEmailPort;

    @EventListener
    @Transactional
    public void handleRefundCompleted(RefundCompletedEvent event) {
        if (event == null || event.refundId() == null || event.customerId() == null
                || event.status() != RefundStatus.PROCESSED) {
            return;
        }
        notifications.executeOnce(new NotificationCommand(event.customerId(), "REFUND_COMPLETED",
                NotificationChannel.IN_APP, LocalizedMessageRef.of("notification.refund.completed.title"),
                LocalizedMessageRef.of("notification.refund.completed.body", event.amount()),
                "REFUND", event.refundId(), null), "refund:" + event.refundId() + ":customer:" + event.customerId());

        if (transactionalEmailPort != null) {
            OffsetDateTime now = OffsetDateTime.now();
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.REFUND_PROCESSED,
                    "refund:" + event.refundId() + ":processed",
                    "USER:" + event.customerId(),
                    now,
                    now,
                    null,
                    1,
                    Map.of(
                            "refundId", event.refundId().toString(),
                            "amount", event.amount() != null ? event.amount().doubleValue() : 0.0
                    )
            ));
        }
    }
}
