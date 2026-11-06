package com.danasea.backend.modules.communication.infrastructure.jobs;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.communication.application.dtos.NotificationCommand;
import com.danasea.backend.modules.communication.application.email.EmailRequest;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailPort;
import com.danasea.backend.modules.communication.application.usecases.SendNotificationUseCase;
import com.danasea.backend.modules.communication.domain.models.NotificationChannel;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;
import com.danasea.backend.modules.communication.domain.ports.PushNotificationPort;
import com.danasea.backend.modules.communication.infrastructure.email.repositories.JpaTransactionalEmailDeliveryRepository;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.shared.i18n.LocalizedMessageRef;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component("tripReminderJob")
public class TripReminderJob {
    private final SendNotificationUseCase notifications;
    private final JpaSubOrderRepository subOrders;
    private final JpaMasterOrderRepository masterOrders;
    private final JpaServiceSlotRepository slots;
    private final PushNotificationPort push;
    private final TransactionalEmailPort emailPort;
    private final JpaTransactionalEmailDeliveryRepository emailDeliveryRepository;

    @Setter
    private Clock clock = Clock.system(Booking.VIETNAM_ZONE);

    public TripReminderJob(
            SendNotificationUseCase notifications,
            JpaSubOrderRepository subOrders,
            JpaMasterOrderRepository masterOrders,
            JpaServiceSlotRepository slots,
            PushNotificationPort push,
            @Autowired(required = false) TransactionalEmailPort emailPort,
            @Autowired(required = false) JpaTransactionalEmailDeliveryRepository emailDeliveryRepository,
            @Autowired(required = false) Clock clock
    ) {
        this.notifications = notifications;
        this.subOrders = subOrders;
        this.masterOrders = masterOrders;
        this.slots = slots;
        this.push = push;
        this.emailPort = emailPort;
        this.emailDeliveryRepository = emailDeliveryRepository;
        if (clock != null) {
            this.clock = clock;
        }
    }

    @Scheduled(cron = "${app.scheduler.trip-reminder.cron:0 0 * * * *}", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void executeTripReminders() {
        scanAndSendReminders();
    }

    @Transactional
    public void scanAndSendReminders() {
        Instant nowInstant = clock.instant();
        ZonedDateTime nowZdt = nowInstant.atZone(Booking.VIETNAM_ZONE);
        LocalDateTime now = nowZdt.toLocalDateTime();
        LocalDateTime horizon = now.plusHours(24).plusMinutes(30);

        int page = 0;
        boolean hasNext;
        do {
            var batch = slots.findDeparturesInWindow(
                    now.toLocalDate(), now.toLocalTime(),
                    horizon.toLocalDate(), horizon.toLocalTime(),
                    PageRequest.of(page++, 200)
            );
            hasNext = batch.hasNext();

            for (var slot : batch) {
                if (slot.getStatus() == SlotStatus.CANCELLED) {
                    continue;
                }

                ZonedDateTime departureZdt = ZonedDateTime.of(slot.getDate(), slot.getStartTime(), Booking.VIETNAM_ZONE);
                Instant departureInstant = departureZdt.toInstant();
                Duration durationUntilDeparture = Duration.between(nowInstant, departureInstant);

                // Group valid sub-orders by customer
                Map<UUID, List<SubOrderJpaEntity>> customerSubOrders = new HashMap<>();

                for (var subOrder : subOrders.findBySlotId(slot.getId())) {
                    if (subOrder.getStatus() != SubOrderStatus.CONFIRMED) {
                        continue;
                    }
                    var order = masterOrders.findById(subOrder.getMasterOrderId()).orElse(null);
                    if (order == null || order.getCustomerId() == null || order.getPaymentStatus() != PaymentOrderStatus.PAID
                            || (order.getStatus() != MasterOrderStatus.PAID
                                && order.getStatus() != MasterOrderStatus.PARTIALLY_COMPLETED)) {
                        continue;
                    }

                    customerSubOrders.computeIfAbsent(order.getCustomerId(), k -> new ArrayList<>()).add(subOrder);

                    // Maintain existing in-app push notification
                    notifications.executeOnce(new NotificationCommand(order.getCustomerId(), "TRIP_REMINDER",
                            NotificationChannel.IN_APP, LocalizedMessageRef.of("notification.trip.reminder.title"),
                            LocalizedMessageRef.of("notification.trip.reminder.body", slot.getStartTime(), slot.getDate()),
                            "SERVICE_SLOT", slot.getId(), null),
                            "trip:" + slot.getId() + ":customer:" + order.getCustomerId()).ifPresent(notification -> {
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                try {
                                    push.sendPush(notification.getUserId(), notification.getTitle(), notification.getBody(),
                                            Map.of("type", "TRIP_REMINDER", "slotId", slot.getId().toString()));
                                } catch (Exception failure) {
                                    log.warn("Push delivery failed for notification {}", notification.getId(), failure);
                                }
                            }
                        });
                    });
                }

                // Enqueue TRIP_REMINDER_24H via Transactional Outbox
                if (emailPort != null) {
                    // Check late threshold at enqueue: if remaining <= 2 hours, skip reminder
                    if (durationUntilDeparture.toMinutes() <= 120 || durationUntilDeparture.isNegative()) {
                        log.info("Skipping enqueue of TRIP_REMINDER_24H for slot {}: departure in {} minutes is <= 2 hours",
                                slot.getId(), durationUntilDeparture.toMinutes());
                        continue;
                    }

                    for (Map.Entry<UUID, List<SubOrderJpaEntity>> entry : customerSubOrders.entrySet()) {
                        UUID customerId = entry.getKey();
                        String recipientKey = "USER:" + customerId;

                        // Check if booking confirmation was accepted within the last 2 hours
                        OffsetDateTime twoHoursAgo = OffsetDateTime.ofInstant(nowInstant.minus(Duration.ofHours(2)), ZoneOffset.UTC);
                        if (emailDeliveryRepository != null && emailDeliveryRepository.existsRecentlyAccepted(
                                TransactionalEmailType.BOOKING_CONFIRMED.name(),
                                recipientKey,
                                twoHoursAgo
                        )) {
                            log.info("Skipping enqueue of TRIP_REMINDER_24H for customer {} on slot {}: BOOKING_CONFIRMED was accepted within 2 hours",
                                    customerId, slot.getId());
                            continue;
                        }

                        String businessKey = "slot:" + slot.getId() + ":start:" + departureInstant.toString() + ":24h";
                        OffsetDateTime dueAt = OffsetDateTime.ofInstant(departureInstant.minus(Duration.ofHours(24)), ZoneOffset.UTC);
                        OffsetDateTime nowOdt = OffsetDateTime.ofInstant(nowInstant, ZoneOffset.UTC);
                        if (dueAt.isBefore(nowOdt)) {
                            dueAt = nowOdt; // late batch scan: enqueue to be sent immediately
                        }
                        OffsetDateTime expiresAt = OffsetDateTime.ofInstant(departureInstant.minus(Duration.ofHours(2)), ZoneOffset.UTC);

                        List<String> subOrderIds = entry.getValue().stream()
                                .map(so -> so.getId().toString())
                                .toList();

                        Map<String, Object> payload = Map.of(
                                "slotId", slot.getId().toString(),
                                "serviceId", slot.getServiceId().toString(),
                                "customerId", customerId.toString(),
                                "departureInstant", departureInstant.toString(),
                                "subOrderIds", subOrderIds
                        );

                        emailPort.enqueue(EmailRequest.builder()
                                .type(TransactionalEmailType.TRIP_REMINDER_24H)
                                .businessKey(businessKey)
                                .recipientKey(recipientKey)
                                .occurredAt(dueAt)
                                .dueAt(dueAt)
                                .expiresAt(expiresAt)
                                .payloadVersion(1)
                                .payload(payload)
                                .build());
                    }
                }
            }
        } while (hasNext);
    }
}
