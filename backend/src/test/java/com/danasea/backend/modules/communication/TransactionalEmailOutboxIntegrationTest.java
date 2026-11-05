package com.danasea.backend.modules.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.danasea.backend.modules.communication.application.email.EmailRequest;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailPort;
import com.danasea.backend.modules.communication.domain.models.EmailDeliveryMode;
import com.danasea.backend.modules.communication.domain.models.EmailDeliveryStatus;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;
import com.danasea.backend.modules.communication.infrastructure.email.TransactionalEmailProperties;
import com.danasea.backend.modules.communication.infrastructure.email.entities.TransactionalEmailDeliveryJpaEntity;
import com.danasea.backend.modules.communication.infrastructure.email.repositories.JpaTransactionalEmailDeliveryRepository;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;

@SpringBootTest
class TransactionalEmailOutboxIntegrationTest extends BaseSecurityIntegrationTest {

    @Autowired
    private TransactionalEmailPort emailPort;

    @Autowired
    private JpaTransactionalEmailDeliveryRepository repository;

    @Autowired
    private TransactionalEmailProperties properties;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @DynamicPropertySource
    static void configureEmailProperties(DynamicPropertyRegistry registry) {
        registry.add("app.transactional-email.mode", () -> "capture");
        registry.add("app.transactional-email.worker.enabled", () -> "false");
    }

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        repository.deleteAll();
    }

    @Test
    @DisplayName("Rollback in business transaction leaves no transactional email records in database")
    void rollbackInBusinessTransactionLeavesNoRecords() {
        String businessKey = "order:" + UUID.randomUUID();
        String recipientKey = "USER:" + UUID.randomUUID();

        try {
            transactionTemplate.execute(status -> {
                emailPort.enqueue(EmailRequest.builder()
                        .type(TransactionalEmailType.BOOKING_CONFIRMED)
                        .businessKey(businessKey)
                        .recipientKey(recipientKey)
                        .occurredAt(OffsetDateTime.now())
                        .payload(Map.of("orderId", businessKey))
                        .build());
                status.setRollbackOnly();
                return null;
            });
        } catch (Exception ignored) {
        }

        var list = repository.findByTypeAndBusinessKeyAndRecipientKeyAndMode(
                TransactionalEmailType.BOOKING_CONFIRMED,
                businessKey,
                recipientKey,
                EmailDeliveryMode.CAPTURE
        );
        assertTrue(list.isEmpty(), "Rolled back transaction must not persist outbox records");
    }

    @Test
    @DisplayName("Two identical enqueue calls produce only one outbox record due to unique idempotency constraint")
    void duplicateInsertProducesSingleRecord() {
        String businessKey = "order:" + UUID.randomUUID();
        String recipientKey = "USER:" + UUID.randomUUID();

        EmailRequest request = EmailRequest.builder()
                .type(TransactionalEmailType.BOOKING_CONFIRMED)
                .businessKey(businessKey)
                .recipientKey(recipientKey)
                .occurredAt(OffsetDateTime.now())
                .payload(Map.of("orderId", businessKey))
                .build();

        transactionTemplate.execute(status -> {
            emailPort.enqueue(request);
            emailPort.enqueue(request);
            return null;
        });

        var entityOpt = repository.findByTypeAndBusinessKeyAndRecipientKeyAndMode(
                TransactionalEmailType.BOOKING_CONFIRMED,
                businessKey,
                recipientKey,
                EmailDeliveryMode.CAPTURE
        );

        assertTrue(entityOpt.isPresent(), "Record should exist");
        TransactionalEmailDeliveryJpaEntity entity = entityOpt.get();
        assertEquals(EmailDeliveryStatus.PENDING, entity.getStatus());
        assertEquals(0, entity.getAttemptCount());
        assertNotNull(entity.getId());
    }
}
