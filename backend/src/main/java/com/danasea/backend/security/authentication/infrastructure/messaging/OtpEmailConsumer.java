package com.danasea.backend.security.authentication.infrastructure.messaging;

import com.danasea.backend.configs.RabbitMQConfig;
import com.danasea.backend.security.authentication.domain.events.OtpEmailRequestedEvent;
import lombok.extern.slf4j.Slf4j;

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
public class OtpEmailConsumer {

    private static final Logger log = LoggerFactory.getLogger(OtpEmailConsumer.class);

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    @Autowired
    private LocalizedMessageService messages = LocalizedMessageService.standalone();

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_OTP_EMAIL_QUEUE)
    public void handleOtpEmailRequestedEvent(OtpEmailRequestedEvent event) {
        log.info("Received OtpEmailRequestedEvent for email: {}. OTP code is: {}", event.email(), event.otpCode());
        if (mailProperties == null || mailProperties.getUsername() == null || mailProperties.getUsername().isBlank()) {
            log.error("Cannot send OTP email to {}: spring.mail.username is not configured", event.email());
            throw new AmqpRejectAndDontRequeueException("spring.mail.username is not configured");
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailProperties.getUsername().trim());
            message.setTo(event.email());
            SupportedLanguage language = SupportedLanguage.fromTag(event.locale()).orElse(SupportedLanguage.VI);
            message.setSubject(messages.get("email.otp.subject", language));
            message.setText(messages.get("email.otp.body", language, event.otpCode()));

            mailSender.send(message);
            log.info("OTP email sent successfully to: {}", event.email());
        } catch (MailException e) {
            log.error("OTP email send failed for: {}", event.email(), e);
            throw new AmqpRejectAndDontRequeueException("OTP email send failed", e);
        }
    }
}
