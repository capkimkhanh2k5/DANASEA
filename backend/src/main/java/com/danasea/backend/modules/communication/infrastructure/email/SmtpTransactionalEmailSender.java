package com.danasea.backend.modules.communication.infrastructure.email;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.danasea.backend.modules.communication.application.email.EmailSendResult;
import com.danasea.backend.modules.communication.application.email.RenderedEmail;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailSender;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component("smtpTransactionalEmailSender")
@RequiredArgsConstructor
public class SmtpTransactionalEmailSender implements TransactionalEmailSender {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@danasea.com}")
    private String fromEmail;

    @Override
    public EmailSendResult send(RenderedEmail email) {
        if (email == null) {
            return EmailSendResult.permanentRejection("INVALID_EMAIL", "Rendered email is null");
        }

        try {
            boolean hasHtml = email.htmlBody() != null && !email.htmlBody().isBlank();
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, hasHtml, StandardCharsets.UTF_8.name());
            helper.setFrom(fromEmail);
            helper.setTo(email.to());
            helper.setSubject(email.subject());

            if (hasHtml) {
                helper.setText(email.textBody() != null ? email.textBody() : "", email.htmlBody());
            } else {
                helper.setText(email.textBody() != null ? email.textBody() : "", false);
            }

            mailSender.send(mimeMessage);
            log.info("Transactional email successfully sent to {}", email.to());
            return EmailSendResult.accepted();
        } catch (MailAuthenticationException e) {
            log.error("Permanent authentication failure sending email to {}", email.to(), e);
            return EmailSendResult.permanentRejection("AUTH_FAILURE", e.getMessage());
        } catch (MailException | MessagingException e) {
            return classifyException(e, email.to());
        } catch (Exception e) {
            log.error("Unexpected error sending email to {}", email.to(), e);
            return EmailSendResult.unknown("UNEXPECTED_ERROR", e.getMessage());
        }
    }

    private EmailSendResult classifyException(Exception e, String recipient) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException) {
                log.error("Socket timeout sending email to {}: outcome uncertain", recipient, e);
                return EmailSendResult.unknown("SMTP_TIMEOUT_UNCERTAIN", cause.getMessage());
            }
            if (cause instanceof ConnectException) {
                log.warn("Connection refused/failed sending email to {}: retryable", recipient, e);
                return EmailSendResult.retryableRejection("CONNECT_FAILED", cause.getMessage());
            }
            cause = cause.getCause();
        }

        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (msg.contains("timeout") || msg.contains("timed out")) {
            log.error("Timeout occurred sending email to {}: outcome uncertain", recipient, e);
            return EmailSendResult.unknown("SMTP_TIMEOUT_UNCERTAIN", e.getMessage());
        }

        log.warn("Temporary SMTP error sending email to {}: retryable", recipient, e);
        return EmailSendResult.retryableRejection("SMTP_TEMPORARY_ERROR", e.getMessage());
    }
}
