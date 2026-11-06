package com.danasea.backend.modules.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.danasea.backend.modules.communication.application.email.EmailSendResult;
import com.danasea.backend.modules.communication.application.email.RenderedEmail;
import com.danasea.backend.modules.communication.application.email.ResolvedRecipient;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailRecipientResolver;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailRenderer;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailSender;
import com.danasea.backend.modules.communication.domain.models.EmailDeliveryMode;
import com.danasea.backend.modules.communication.domain.models.EmailDeliveryStatus;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailFamily;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;
import com.danasea.backend.modules.communication.infrastructure.email.TransactionalEmailClaimService;
import com.danasea.backend.modules.communication.infrastructure.email.TransactionalEmailProperties;
import com.danasea.backend.modules.communication.infrastructure.email.TransactionalEmailWorker;
import com.danasea.backend.modules.communication.infrastructure.email.entities.TransactionalEmailDeliveryJpaEntity;
import com.danasea.backend.modules.communication.infrastructure.email.repositories.JpaTransactionalEmailDeliveryRepository;

@ExtendWith(MockitoExtension.class)
class TransactionalEmailWorkerTest {

    @Mock
    private JpaTransactionalEmailDeliveryRepository repository;

    @Mock
    private TransactionalEmailClaimService claimService;

    @Mock
    private TransactionalEmailRecipientResolver recipientResolver;

    @Mock
    private TransactionalEmailRenderer renderer;

    @Mock
    private TransactionalEmailSender smtpSender;

    @Mock
    private TransactionalEmailSender captureSender;

    private TransactionalEmailProperties properties;
    private TransactionalEmailWorker worker;

    @BeforeEach
    void setUp() {
        properties = new TransactionalEmailProperties();
        properties.setMode("live");
        properties.setEnabledFamilies("BOOKING,TRIP,DISRUPTION,REFUND");
        properties.getWorker().setEnabled(true);
        properties.getWorker().setMaxAttempts(4);
        properties.getWorker().setLeaseDurationSeconds(120);

        worker = new TransactionalEmailWorker(
                repository,
                claimService,
                recipientResolver,
                renderer,
                smtpSender,
                captureSender,
                properties
        );
    }

    private TransactionalEmailDeliveryJpaEntity createEntity(
            TransactionalEmailType type,
            String recipientKey,
            OffsetDateTime expiresAt
    ) {
        return TransactionalEmailDeliveryJpaEntity.builder()
                .id(UUID.randomUUID())
                .type(type)
                .family(type.getFamily())
                .businessKey("order:" + UUID.randomUUID())
                .recipientKey(recipientKey)
                .mode(EmailDeliveryMode.LIVE)
                .status(EmailDeliveryStatus.PENDING)
                .occurredAt(OffsetDateTime.now().minusMinutes(5))
                .dueAt(OffsetDateTime.now().minusMinutes(5))
                .expiresAt(expiresAt)
                .attemptCount(1) // incremented upon claim
                .nextAttemptAt(OffsetDateTime.now().minusMinutes(5))
                .payload("{}")
                .build();
    }

    @Test
    @DisplayName("Unverified email recipient is marked as SKIPPED / UNVERIFIED_EMAIL and never retried")
    void unverifiedEmailRecipientMarkedSkipped() {
        UUID id = UUID.randomUUID();
        String recipientKey = "USER:" + UUID.randomUUID();
        TransactionalEmailDeliveryJpaEntity entity = createEntity(TransactionalEmailType.BOOKING_CONFIRMED, recipientKey, null);
        entity.setId(id);

        when(claimService.claimForDelivery(any(), any(), any(), any())).thenReturn(Optional.of(entity));
        when(recipientResolver.resolve(recipientKey)).thenReturn(Optional.of(
                new ResolvedRecipient("unverified@example.com", "vi", false, false)
        ));

        worker.processIndividualEmail(id, EmailDeliveryMode.LIVE);

        verify(claimService).recordFinalOutcome(
                org.mockito.ArgumentMatchers.eq(id),
                any(),
                org.mockito.ArgumentMatchers.argThat(result ->
                        result.status() == EmailDeliveryStatus.SKIPPED
                                && "UNVERIFIED_EMAIL".equals(result.errorCode())
                ),
                any()
        );
        verify(smtpSender, never()).send(any());
    }

    @Test
    @DisplayName("Locked account: regular email is skipped, critical disruption/cancellation email is allowed")
    void lockedAccountSkipsRegularEmailButAllowsCriticalEmail() {
        String recipientKey = "USER:" + UUID.randomUUID();
        ResolvedRecipient lockedUser = new ResolvedRecipient("locked@example.com", "vi", true, true);
        when(recipientResolver.resolve(recipientKey)).thenReturn(Optional.of(lockedUser));

        // 1. Regular booking confirmed email -> SKIPPED / ACCOUNT_LOCKED
        UUID bookingEmailId = UUID.randomUUID();
        TransactionalEmailDeliveryJpaEntity bookingEntity = createEntity(TransactionalEmailType.BOOKING_CONFIRMED, recipientKey, null);
        bookingEntity.setId(bookingEmailId);
        when(claimService.claimForDelivery(org.mockito.ArgumentMatchers.eq(bookingEmailId), any(), any(), any()))
                .thenReturn(Optional.of(bookingEntity));

        worker.processIndividualEmail(bookingEmailId, EmailDeliveryMode.LIVE);

        verify(claimService).recordFinalOutcome(
                org.mockito.ArgumentMatchers.eq(bookingEmailId),
                any(),
                org.mockito.ArgumentMatchers.argThat(result ->
                        result.status() == EmailDeliveryStatus.SKIPPED
                                && "ACCOUNT_LOCKED".equals(result.errorCode())
                ),
                any()
        );

        // 2. Critical cancellation email -> ALLOWED & SENT
        UUID cancelEmailId = UUID.randomUUID();
        TransactionalEmailDeliveryJpaEntity cancelEntity = createEntity(TransactionalEmailType.TRIP_CANCELLED, recipientKey, null);
        cancelEntity.setId(cancelEmailId);
        when(claimService.claimForDelivery(org.mockito.ArgumentMatchers.eq(cancelEmailId), any(), any(), any()))
                .thenReturn(Optional.of(cancelEntity));
        when(renderer.render(any(), any())).thenReturn(
                RenderedEmail.builder().to("locked@example.com").subject("Hủy chuyến").textBody("Body").build()
        );
        when(smtpSender.send(any())).thenReturn(EmailSendResult.accepted());

        worker.processIndividualEmail(cancelEmailId, EmailDeliveryMode.LIVE);

        verify(smtpSender).send(any());
        verify(claimService).recordFinalOutcome(
                org.mockito.ArgumentMatchers.eq(cancelEmailId),
                any(),
                org.mockito.ArgumentMatchers.argThat(result -> result.status() == EmailDeliveryStatus.ACCEPTED),
                any()
        );
    }

    @Test
    @DisplayName("SMTP temporary error schedules retry with exponential backoff")
    void temporarySmtpErrorSchedulesRetry() {
        UUID id = UUID.randomUUID();
        String recipientKey = "USER:" + UUID.randomUUID();
        TransactionalEmailDeliveryJpaEntity entity = createEntity(TransactionalEmailType.BOOKING_CONFIRMED, recipientKey, null);
        entity.setId(id);
        entity.setAttemptCount(1);

        when(claimService.claimForDelivery(any(), any(), any(), any())).thenReturn(Optional.of(entity));
        when(recipientResolver.resolve(recipientKey)).thenReturn(Optional.of(
                new ResolvedRecipient("valid@example.com", "vi", true, false)
        ));
        when(renderer.render(any(), any())).thenReturn(
                RenderedEmail.builder().to("valid@example.com").subject("Booking").textBody("Body").build()
        );
        when(smtpSender.send(any())).thenReturn(
                EmailSendResult.retryableRejection("CONNECT_FAILED", "Connection refused")
        );

        worker.processIndividualEmail(id, EmailDeliveryMode.LIVE);

        verify(claimService).recordRetryableFailure(
                org.mockito.ArgumentMatchers.eq(id),
                any(),
                org.mockito.ArgumentMatchers.eq("CONNECT_FAILED"),
                org.mockito.ArgumentMatchers.eq("Connection refused"),
                any(), // nextAttemptAt scheduled
                org.mockito.ArgumentMatchers.eq(false),
                any()
        );
    }

    @Test
    @DisplayName("SMTP timeout uncertain result transitions to UNKNOWN without automatic retry")
    void smtpTimeoutUncertainTransitionsToUnknown() {
        UUID id = UUID.randomUUID();
        String recipientKey = "USER:" + UUID.randomUUID();
        TransactionalEmailDeliveryJpaEntity entity = createEntity(TransactionalEmailType.BOOKING_CONFIRMED, recipientKey, null);
        entity.setId(id);

        when(claimService.claimForDelivery(any(), any(), any(), any())).thenReturn(Optional.of(entity));
        when(recipientResolver.resolve(recipientKey)).thenReturn(Optional.of(
                new ResolvedRecipient("valid@example.com", "vi", true, false)
        ));
        when(renderer.render(any(), any())).thenReturn(
                RenderedEmail.builder().to("valid@example.com").subject("Booking").textBody("Body").build()
        );
        when(smtpSender.send(any())).thenReturn(
                EmailSendResult.unknown("SMTP_TIMEOUT_UNCERTAIN", "Socket timeout")
        );

        worker.processIndividualEmail(id, EmailDeliveryMode.LIVE);

        verify(claimService).recordFinalOutcome(
                org.mockito.ArgumentMatchers.eq(id),
                any(),
                org.mockito.ArgumentMatchers.argThat(result ->
                        result.status() == EmailDeliveryStatus.UNKNOWN
                                && "SMTP_TIMEOUT_UNCERTAIN".equals(result.errorCode())
                ),
                any()
        );
    }

    @Test
    @DisplayName("Expired lease reclamation sets status to UNKNOWN")
    void expiredLeaseReclamationSetsStatusToUnknown() {
        when(repository.markExpiredLeasesAsUnknown(any())).thenReturn(3);

        worker.runWorkerCycle();

        verify(repository).markExpiredLeasesAsUnknown(any());
    }
}
