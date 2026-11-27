package com.danasea.backend.modules.communication.application.usecases;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.danasea.backend.configs.RabbitMQConfig;
import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.communication.application.dtos.NotificationCommand;
import com.danasea.backend.modules.communication.domain.events.NotificationEmailEvent;
import com.danasea.backend.modules.communication.domain.models.NotificationChannel;
import com.danasea.backend.modules.communication.domain.models.NotificationStatus;
import com.danasea.backend.modules.communication.infrastructure.persistence.entities.NotificationJpaEntity;
import com.danasea.backend.modules.communication.infrastructure.persistence.repositories.JpaNotificationRepository;
import com.danasea.backend.shared.i18n.LocalizedMessageService;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SendNotificationUseCase {

    private final JpaNotificationRepository notificationRepository;
    private final RabbitTemplate rabbitTemplate;
    private final AccountInternalApi accountInternalApi;
    private final LocalizedMessageService messages;

    @Transactional
    public NotificationJpaEntity execute(NotificationCommand command) {
        SupportedLanguage language = resolveLanguage(command.recipientId());
        String title = messages.get(command.title().key(), language, command.title().args());
        String body = messages.get(command.body().key(), language, command.body().args());
        return persist(command.recipientId(), command.type(), command.channel(), title, body,
                command.relatedEntityType(), command.relatedEntityId(), command.recipientEmail(), language);
    }

    @Transactional
    public NotificationJpaEntity execute(UUID userId,
                                         String type,
                                         NotificationChannel channel,
                                         String title,
                                         String body,
                                         String relatedEntityType,
                                         UUID relatedEntityId,
                                         String recipientEmail) {
        return persist(userId, type, channel, title, body, relatedEntityType,
                relatedEntityId, recipientEmail, resolveLanguage(userId));
    }

    private NotificationJpaEntity persist(UUID userId,
                                          String type,
                                          NotificationChannel channel,
                                          String title,
                                          String body,
                                          String relatedEntityType,
                                          UUID relatedEntityId,
                                          String recipientEmail,
                                          SupportedLanguage language) {
        NotificationJpaEntity entity = new NotificationJpaEntity();
        entity.setUserId(userId);
        entity.setType(type);
        entity.setChannel(channel != null ? channel : NotificationChannel.IN_APP);
        entity.setTitle(title);
        entity.setBody(body);
        entity.setLocale(language.code());
        entity.setRelatedEntityType(relatedEntityType);
        entity.setRelatedEntityId(relatedEntityId);
        entity.setStatus(NotificationStatus.PENDING);

        if (entity.getChannel() == NotificationChannel.IN_APP) {
            entity.setStatus(NotificationStatus.SENT);
            entity.setSentAt(OffsetDateTime.now());
        }

        NotificationJpaEntity saved = notificationRepository.save(entity);

        if (entity.getChannel() == NotificationChannel.EMAIL && recipientEmail != null && !recipientEmail.isBlank()) {
            try {
                NotificationEmailEvent event = new NotificationEmailEvent(
                        saved.getId(),
                        recipientEmail,
                        title,
                        body,
                        language.code()
                );
                rabbitTemplate.convertAndSend(RabbitMQConfig.NOTIFICATION_EMAIL_QUEUE, event);
                log.info("Queued notification email to {}: {}", recipientEmail, title);
            } catch (Exception e) {
                log.warn("Failed to queue notification email for notification id {}", saved.getId(), e);
            }
        }

        return saved;
    }

    @Transactional
    public Optional<NotificationJpaEntity> executeOnce(NotificationCommand command, String idempotencyKey) {
        if (command.channel() != NotificationChannel.IN_APP || command.recipientId() == null
                || idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 160) {
            throw new IllegalArgumentException("An in-app recipient and a valid idempotency key are required.");
        }
        SupportedLanguage language = resolveLanguage(command.recipientId());
        int inserted = notificationRepository.insertInAppOnce(UUID.randomUUID(), command.recipientId(), command.type(),
                messages.get(command.title().key(), language, command.title().args()),
                messages.get(command.body().key(), language, command.body().args()), language.code(),
                command.relatedEntityType(), command.relatedEntityId(), OffsetDateTime.now(), idempotencyKey);
        return inserted == 1 ? notificationRepository.findByIdempotencyKey(idempotencyKey) : Optional.empty();
    }

    private SupportedLanguage resolveLanguage(UUID userId) {
        if (userId == null || accountInternalApi == null) {
            return SupportedLanguage.VI;
        }
        return accountInternalApi.findUserById(userId)
                .flatMap(user -> SupportedLanguage.fromTag(user.getLocale()))
                .orElse(SupportedLanguage.VI);
    }
}
