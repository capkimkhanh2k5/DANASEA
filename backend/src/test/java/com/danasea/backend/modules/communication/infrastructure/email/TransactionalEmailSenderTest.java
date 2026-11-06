package com.danasea.backend.modules.communication.infrastructure.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import com.danasea.backend.modules.communication.application.email.RenderedEmail;
import com.danasea.backend.modules.communication.domain.models.EmailDeliveryStatus;

import jakarta.mail.Message;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

@ExtendWith(MockitoExtension.class)
class TransactionalEmailSenderTest {

    private static final String FROM = "noreply@example.test";
    private static final String CUSTOMER = "customer@example.test";
    private static final String SUBJECT = "Vé chuyến đi";
    private static final String PLAIN_TEXT = "Chuyến đi của bạn bắt đầu lúc 08:00.";

    @Mock
    private JavaMailSender mailSender;

    @BeforeEach
    void createRealMimeMessages() {
        when(mailSender.createMimeMessage()).thenAnswer(call -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void liveMailSendsTextAndHtmlAlternativesWithDecodableInlinePngs() throws Exception {
        List<String> cids = List.of("danasea-logo", "danasea-icon-success", "danasea-icon-ocean",
                "danasea-icon-warning", "danasea-icon-danger", "danasea-icon-policy", "danasea-icon-info",
                "danasea-icon-phone", "danasea-icon-help");
        String images = cids.stream().map(cid -> "<img src=\"cid:" + cid + "\">").collect(Collectors.joining());
        String html = "<html><body><p>" + PLAIN_TEXT + "</p>" + images + "</body></html>";

        var result = smtpSender().send(new RenderedEmail(CUSTOMER, SUBJECT, PLAIN_TEXT, html));

        assertEquals(EmailDeliveryStatus.ACCEPTED, result.status());
        MimeMessage message = sentMessage();
        assertEquals(List.of(new InternetAddress(FROM)), List.of(message.getFrom()));
        assertEquals(List.of(new InternetAddress(CUSTOMER)), List.of(message.getAllRecipients()));
        assertEquals(SUBJECT, message.getSubject());

        assertTrue(message.isMimeType("multipart/mixed"));
        MimeMultipart mixed = assertInstanceOf(MimeMultipart.class, message.getContent());
        var relatedPart = mixed.getBodyPart(0);
        assertTrue(relatedPart.isMimeType("multipart/related"));
        MimeMultipart related = assertInstanceOf(MimeMultipart.class, relatedPart.getContent());
        var body = related.getBodyPart(0);
        assertTrue(body.isMimeType("multipart/alternative"));
        MimeMultipart alternatives = assertInstanceOf(MimeMultipart.class, body.getContent());
        assertTrue(alternatives.getBodyPart(0).isMimeType("text/plain"));
        assertEquals(PLAIN_TEXT, alternatives.getBodyPart(0).getContent());
        assertTrue(alternatives.getBodyPart(1).isMimeType("text/html"));
        assertEquals(html, alternatives.getBodyPart(1).getContent());

        Set<String> attachedCids = new HashSet<>();
        for (int i = 1; i < related.getCount(); i++) {
            MimeBodyPart inline = assertInstanceOf(MimeBodyPart.class, related.getBodyPart(i));
            assertEquals(Part.INLINE, inline.getDisposition());
            assertTrue(inline.isMimeType("image/png"));
            String contentId = inline.getContentID();
            assertTrue(attachedCids.add(contentId), "Duplicate inline Content-ID " + contentId);
            try (var stream = inline.getInputStream()) {
                assertNotNull(ImageIO.read(stream), "Undecodable PNG for " + contentId);
            }
        }
        assertEquals(cids.stream().map(cid -> "<" + cid + ">").collect(Collectors.toSet()), attachedCids);
    }

    @Test
    void captureMailRoutesOnlyToTheConfiguredSinkAndLabelsTheIntendedRecipient() throws Exception {
        var properties = new TransactionalEmailProperties();
        properties.setCaptureSinkEmail("capture@example.test");
        var sender = new CaptureTransactionalEmailSender(mailSender, properties);
        ReflectionTestUtils.setField(sender, "fromEmail", FROM);

        var result = sender.send(new RenderedEmail(CUSTOMER, SUBJECT, PLAIN_TEXT, null));

        assertEquals(EmailDeliveryStatus.ACCEPTED, result.status());
        MimeMessage message = sentMessage();
        assertEquals(List.of(new InternetAddress("capture@example.test")), List.of(message.getAllRecipients()));
        assertEquals(List.of(new InternetAddress("capture@example.test")), List.of(message.getRecipients(Message.RecipientType.TO)));
        assertEquals("[CAPTURE] " + SUBJECT, message.getSubject());
        assertTrue(message.isMimeType("text/plain"));
        assertTrue(message.getContent().toString().contains(PLAIN_TEXT));
        assertTrue(message.getContent().toString().contains("Intended recipient: " + CUSTOMER));
    }

    @ParameterizedTest
    @MethodSource("smtpFailures")
    void liveMailDistinguishesUncertainRetryableAndPermanentFailures(
            MailException failure, EmailDeliveryStatus status, String errorCode) {
        doThrow(failure).when(mailSender).send(any(MimeMessage.class));

        var result = smtpSender().send(new RenderedEmail(CUSTOMER, SUBJECT, PLAIN_TEXT, null));

        assertEquals(status, result.status());
        assertEquals(errorCode, result.errorCode());
        verify(mailSender).send(any(MimeMessage.class));
    }

    static Stream<Arguments> smtpFailures() {
        return Stream.of(
                Arguments.of(new MailSendException("SMTP failed", new SocketTimeoutException("read timed out")),
                        EmailDeliveryStatus.UNKNOWN, "SMTP_TIMEOUT_UNCERTAIN"),
                Arguments.of(new MailSendException("SMTP failed", new ConnectException("connection refused")),
                        EmailDeliveryStatus.RETRYABLE_REJECTION, "CONNECT_FAILED"),
                Arguments.of(new MailAuthenticationException("invalid SMTP credentials"),
                        EmailDeliveryStatus.PERMANENT_REJECTION, "AUTH_FAILURE"));
    }

    private SmtpTransactionalEmailSender smtpSender() {
        var sender = new SmtpTransactionalEmailSender(mailSender);
        ReflectionTestUtils.setField(sender, "fromEmail", FROM);
        return sender;
    }

    private MimeMessage sentMessage() throws Exception {
        var message = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(message.capture());
        MimeMessage sent = message.getValue();
        sent.saveChanges();
        return sent;
    }
}
