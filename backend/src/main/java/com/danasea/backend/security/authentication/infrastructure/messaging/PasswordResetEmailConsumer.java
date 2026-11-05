package com.danasea.backend.security.authentication.infrastructure.messaging;

import com.danasea.backend.configs.RabbitMQConfig;
import com.danasea.backend.security.authentication.domain.events.PasswordResetRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.danasea.backend.shared.i18n.LocalizedMessageService;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PasswordResetEmailConsumer {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetEmailConsumer.class);

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    @Autowired
    private LocalizedMessageService messages = LocalizedMessageService.standalone();

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_PASSWORD_RESET_EMAIL_QUEUE)
    public void handlePasswordResetRequestedEvent(PasswordResetRequestedEvent event) {
        log.info("Received PasswordResetRequestedEvent for email: {}", event.email());
        if (mailProperties == null || mailProperties.getUsername() == null || mailProperties.getUsername().isBlank()) {
            log.error("Cannot send password reset email to {}: spring.mail.username is not configured", event.email());
            throw new AmqpRejectAndDontRequeueException("spring.mail.username is not configured");
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailProperties.getUsername().trim());
            message.setTo(event.email());
            SupportedLanguage language = SupportedLanguage.fromTag(event.locale()).orElse(SupportedLanguage.VI);
            message.setSubject(messages.get("email.password_reset.subject", language));
            message.setText(messages.get("email.password_reset.body", language, event.otpCode()));

            mailSender.send(message);
            log.info("Password reset OTP email sent successfully to: {}", event.email());
        } catch (MailException e) {
            log.error("Password reset OTP email send failed for: {}", event.email(), e);
            throw new AmqpRejectAndDontRequeueException("Password reset OTP email send failed", e);
        }
    }
}
