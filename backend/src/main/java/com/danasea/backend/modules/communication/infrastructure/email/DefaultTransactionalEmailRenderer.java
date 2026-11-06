package com.danasea.backend.modules.communication.infrastructure.email;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.danasea.backend.modules.communication.application.email.RenderedEmail;
import com.danasea.backend.modules.communication.application.email.ResolvedRecipient;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailRenderer;
import com.danasea.backend.modules.communication.infrastructure.email.entities.TransactionalEmailDeliveryJpaEntity;
import com.danasea.backend.modules.communication.infrastructure.email.renderers.BookingEmailRenderer;
import com.danasea.backend.modules.communication.infrastructure.email.renderers.CaseAndAccountEmailRenderer;
import com.danasea.backend.modules.communication.infrastructure.email.renderers.RefundVoucherEmailRenderer;
import com.danasea.backend.modules.communication.infrastructure.email.renderers.TransactionalEmailHtmlLayout;
import com.danasea.backend.modules.communication.infrastructure.email.renderers.TripChangeEmailRenderer;
import com.danasea.backend.modules.communication.infrastructure.email.renderers.TripReminderEmailRenderer;
import com.danasea.backend.modules.communication.infrastructure.email.renderers.TripReviewEmailRenderer;
import com.danasea.backend.shared.i18n.SupportedLanguage;

@Primary
@Component
public class DefaultTransactionalEmailRenderer implements TransactionalEmailRenderer {

    private final BookingEmailRenderer bookingEmailRenderer;
    private final TripReminderEmailRenderer tripReminderEmailRenderer;
    private final TripChangeEmailRenderer tripChangeEmailRenderer;
    private final RefundVoucherEmailRenderer refundVoucherEmailRenderer;
    private final CaseAndAccountEmailRenderer caseAndAccountEmailRenderer;
    private final TripReviewEmailRenderer tripReviewEmailRenderer;

    public DefaultTransactionalEmailRenderer(
            @Autowired(required = false) BookingEmailRenderer bookingEmailRenderer,
            @Autowired(required = false) TripReminderEmailRenderer tripReminderEmailRenderer,
            @Autowired(required = false) TripChangeEmailRenderer tripChangeEmailRenderer,
            @Autowired(required = false) RefundVoucherEmailRenderer refundVoucherEmailRenderer,
            @Autowired(required = false) CaseAndAccountEmailRenderer caseAndAccountEmailRenderer,
            @Autowired(required = false) TripReviewEmailRenderer tripReviewEmailRenderer
    ) {
        this.bookingEmailRenderer = bookingEmailRenderer;
        this.tripReminderEmailRenderer = tripReminderEmailRenderer;
        this.tripChangeEmailRenderer = tripChangeEmailRenderer;
        this.refundVoucherEmailRenderer = refundVoucherEmailRenderer;
        this.caseAndAccountEmailRenderer = caseAndAccountEmailRenderer;
        this.tripReviewEmailRenderer = tripReviewEmailRenderer;
    }

    @Override
    public RenderedEmail render(TransactionalEmailDeliveryJpaEntity entity, ResolvedRecipient recipient) {
        if (bookingEmailRenderer != null && bookingEmailRenderer.supports(entity.getType())) {
            return bookingEmailRenderer.render(entity, recipient);
        }
        if (tripReminderEmailRenderer != null && tripReminderEmailRenderer.supports(entity.getType())) {
            return tripReminderEmailRenderer.render(entity, recipient);
        }
        if (tripChangeEmailRenderer != null && tripChangeEmailRenderer.supports(entity.getType())) {
            return tripChangeEmailRenderer.render(entity, recipient);
        }
        if (refundVoucherEmailRenderer != null && refundVoucherEmailRenderer.supports(entity.getType())) {
            return refundVoucherEmailRenderer.render(entity, recipient);
        }
        if (caseAndAccountEmailRenderer != null && caseAndAccountEmailRenderer.supports(entity.getType())) {
            return caseAndAccountEmailRenderer.render(entity, recipient);
        }
        if (tripReviewEmailRenderer != null && tripReviewEmailRenderer.supports(entity.getType())) {
            return tripReviewEmailRenderer.render(entity, recipient);
        }

        String subject = "[DANASEA] Notification: " + entity.getType().name();
        String text = "Xin chào,\n\nBạn có thông báo mới từ DANASEA về giao dịch: " + entity.getBusinessKey()
                + "\nLoại thông báo: " + entity.getType().name()
                + "\nThời gian: " + entity.getOccurredAt()
                + "\n\nTrân trọng,\nĐội ngũ DANASEA";

        String html = TransactionalEmailHtmlLayout.fromPlainText(
                subject,
                entity.getType().name(),
                TransactionalEmailHtmlLayout.Theme.OCEAN,
                text,
                null,
                null,
                "1900-6868",
                SupportedLanguage.VI
        );

        return RenderedEmail.builder()
                .to(recipient.email())
                .subject(subject)
                .textBody(text)
                .htmlBody(html)
                .build();
    }
}
