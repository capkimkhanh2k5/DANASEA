package com.danasea.backend.modules.communication.infrastructure.email;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.danasea.backend.modules.communication.application.email.EmailSendResult;
import com.danasea.backend.modules.communication.application.email.RenderedEmail;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailSender;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component("captureTransactionalEmailSender")
@RequiredArgsConstructor
public class CaptureTransactionalEmailSender implements TransactionalEmailSender {

    private final JavaMailSender mailSender;
    private final TransactionalEmailProperties properties;

    @Value("${spring.mail.username:noreply@danasea.com}")
    private String fromEmail;

    @Override
    public EmailSendResult send(RenderedEmail email) {
        if (email == null) {
            return EmailSendResult.permanentRejection("INVALID_EMAIL", "Rendered email is null");
        }

        log.info("[CAPTURE MODE] Capturing transactional email to {}: subject='{}'", email.to(), email.subject());

        try {
            boolean hasHtml = email.htmlBody() != null && !email.htmlBody().isBlank();
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, hasHtml, StandardCharsets.UTF_8.name());
            helper.setFrom(fromEmail);
            String sinkAddress = (properties != null && properties.getCaptureSinkEmail() != null && !properties.getCaptureSinkEmail().isBlank())
                    ? properties.getCaptureSinkEmail()
                    : email.to();
            helper.setTo(sinkAddress);
            helper.setSubject("[CAPTURE] " + email.subject());

            String textBody = (email.textBody() != null ? email.textBody() : "")
                    + "\n\n[DANASEA CAPTURE SINK - Intended recipient: " + email.to() + "]";

            if (hasHtml) {
                String htmlBody = email.htmlBody()
                        + "<hr><p style='color: gray; font-size: 12px;'>[DANASEA CAPTURE SINK - Intended recipient: "
                        + email.to() + "]</p>";
                helper.setText(textBody, htmlBody);
                EmailInlineAssetHelper.attachInlineAssets(helper, htmlBody);
            } else {
                helper.setText(textBody, false);
            }

            mailSender.send(mimeMessage);
            log.info("[CAPTURE MODE] Email successfully delivered to mail sink for intended recipient {}", email.to());
            return EmailSendResult.accepted();
        } catch (Exception e) {
            log.warn("[CAPTURE MODE] Failed to send email to mail sink: {}. Capturing in logs only.", e.getMessage());
            // In capture mode, if sink is down or not running, we still record as ACCEPTED to allow tests/local without sink
            return EmailSendResult.accepted();
        }
    }
}
