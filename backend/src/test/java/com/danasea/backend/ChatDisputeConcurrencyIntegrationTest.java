package com.danasea.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.danasea.backend.modules.communication.application.dtos.CreateConversationRequest;
import com.danasea.backend.modules.communication.application.dtos.MessageResponse;
import com.danasea.backend.modules.communication.application.dtos.SendMessageRequest;
import com.danasea.backend.modules.communication.application.usecases.CreateOrGetConversationUseCase;
import com.danasea.backend.modules.communication.application.usecases.GetMessagesUseCase;
import com.danasea.backend.modules.communication.application.usecases.SendMessageUseCase;
import com.danasea.backend.modules.communication.domain.exceptions.UnauthorizedChatAccessException;
import com.danasea.backend.modules.communication.infrastructure.persistence.entities.ConversationJpaEntity;
import com.danasea.backend.modules.communication.infrastructure.persistence.repositories.JpaConversationRepository;
import com.danasea.backend.modules.communication.infrastructure.persistence.repositories.JpaMessageRepository;
import com.danasea.backend.modules.dispute.application.usecases.GetDisputesUseCase;
import com.danasea.backend.modules.dispute.application.usecases.ResolveDisputeUseCase;
import com.danasea.backend.modules.dispute.application.usecases.SubmitVendorDisputeResponseUseCase;
import com.danasea.backend.modules.dispute.domain.exceptions.DisputeAlreadyResolvedException;
import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.dispute.presentation.dtos.ResolveDisputeRequest;
import com.danasea.backend.modules.dispute.presentation.dtos.SubmitVendorResponseRequest;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;
import org.springframework.dao.DuplicateKeyException;

/** PostgreSQL and transactional use cases are real; spies only coordinate competing threads. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChatDisputeConcurrencyIntegrationTest extends BaseSecurityIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private CreateOrGetConversationUseCase create;
    @Autowired private SendMessageUseCase send;
    @Autowired private GetMessagesUseCase get;
    @Autowired private SubmitVendorDisputeResponseUseCase reply;
    @Autowired private ResolveDisputeUseCase resolve;
    @Autowired private GetDisputesUseCase adminList;
    @Autowired private JpaMessageRepository messages;
    @MockitoSpyBean private JpaConversationRepository conversations;
    @MockitoSpyBean private JpaSubOrderRepository subOrders;
    @MockitoBean private VendorInternalApi vendors;

    private UUID customer, vendorUser, vendorId, order, subOrder, admin;

    @BeforeEach
    void seed() {
        customer = UUID.randomUUID(); vendorUser = UUID.randomUUID(); vendorId = UUID.randomUUID();
        order = UUID.randomUUID(); subOrder = UUID.randomUUID(); admin = UUID.randomUUID();
        Vendor vendor = new Vendor(); vendor.setId(vendorId); vendor.setUserId(vendorUser);
        when(vendors.findByUserId(any())).thenReturn(Optional.empty());
        when(vendors.findByUserId(vendorUser)).thenReturn(Optional.of(vendor));
        jdbc.update("INSERT INTO users(id,email,password_hash,full_name,role,locale,created_at,updated_at,session_version) "
                + "VALUES (?,?,'hash','Customer','CUSTOMER','vi',NOW(),NOW(),0)", customer, customer + "@example.com");
        jdbc.update("INSERT INTO users(id,email,password_hash,full_name,role,locale,created_at,updated_at,session_version) "
                + "VALUES (?,?,'hash','Admin','ADMIN','vi',NOW(),NOW(),0)", admin, admin + "@example.com");
        jdbc.update("INSERT INTO master_orders(id,customer_id,status,total_amount,created_at,updated_at) "
                + "VALUES (?,?,'PAID',100000,NOW(),NOW())", order, customer);
        jdbc.update("INSERT INTO sub_orders(id,master_order_id,vendor_id,status,final_amount,created_at,updated_at,reschedule_version) "
                + "VALUES (?,?,?,'COMPLETED',100000,NOW(),NOW(),0)", subOrder, order, vendorId);
    }

    @AfterEach
    void cleanFixture() {
        jdbc.update("DELETE FROM refunds WHERE sub_order_id=?", subOrder);
        jdbc.update("DELETE FROM disputes WHERE sub_order_id=?", subOrder);
        jdbc.update("DELETE FROM messages WHERE conversation_id IN (SELECT id FROM conversations WHERE master_order_id=?)", order);
        jdbc.update("DELETE FROM conversations WHERE master_order_id=?", order);
        jdbc.update("DELETE FROM sub_orders WHERE id=?", subOrder);
        jdbc.update("DELETE FROM master_orders WHERE id=?", order);
        jdbc.update("DELETE FROM users WHERE id IN (?,?)", customer, admin);
    }

    private UUID createConversation() {
        return create.execute(new CreateConversationRequest(order, vendorId), customer).conversation().id();
    }

    private UUID createDispute() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO disputes(id,sub_order_id,customer_id,reason,status,created_at,updated_at) "
                + "VALUES (?,?,?,'SAFETY_CONCERN','OPEN',NOW(),NOW())", id, subOrder, customer);
        return id;
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(15, TimeUnit.SECONDS), "Timing gate timed out");
    }

    @Test
    void concurrentCreateReturnsOneConversation() throws Exception {
        CountDownLatch firstLookedUp = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(call -> {
            var found = conversations.findAll().stream().filter(c -> order.equals(c.getMasterOrderId())
                    && vendorId.equals(c.getVendorId()) && customer.equals(c.getCustomerId())).findFirst();
            if (Thread.currentThread().getName().equals("first-creator")) {
                firstLookedUp.countDown(); await(release);
            }
            return found;
        }).when(conversations).findByCustomerIdAndVendorIdAndMasterOrderId(customer, vendorId, order);
        ExecutorService firstPool = Executors.newSingleThreadExecutor(r -> new Thread(r, "first-creator"));
        ExecutorService secondPool = Executors.newSingleThreadExecutor();
        try {
            Future<UUID> first = firstPool.submit(this::createConversation);
            await(firstLookedUp);
            CountDownLatch secondStarted = new CountDownLatch(1);
            Future<UUID> second = secondPool.submit(() -> { secondStarted.countDown(); return createConversation(); });
            await(secondStarted);
            assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));
            release.countDown();
            assertEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM conversations WHERE master_order_id=?", Integer.class, order));
        } finally { release.countDown(); firstPool.shutdownNow(); secondPool.shutdownNow(); }
    }

    @Test
    void databaseRejectsDuplicateConversation() {
        createConversation();
        assertThrows(DuplicateKeyException.class, () -> jdbc.update(
                "INSERT INTO conversations(id,customer_id,vendor_id,master_order_id,created_at,updated_at,last_message_sequence) VALUES (?,?,?,?,NOW(),NOW(),0)",
                UUID.randomUUID(), customer, vendorId, order));
    }

    @Test
    void vendorReplyAndAdminResolutionShareLock() throws Exception {
        UUID id = createDispute();
        CountDownLatch loaded = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(call -> {
            var found = subOrders.findAll().stream().filter(s -> subOrder.equals(s.getId())).findFirst();
            if (Thread.currentThread().getName().equals("vendor-response")) { loaded.countDown(); await(release); }
            return found;
        }).when(subOrders).findById(subOrder);
        ExecutorService vendorPool = Executors.newSingleThreadExecutor(r -> new Thread(r, "vendor-response"));
        ExecutorService adminPool = Executors.newSingleThreadExecutor();
        try {
            Future<?> vendorResponse = vendorPool.submit(() -> reply.execute(id,
                    new SubmitVendorResponseRequest("Vendor explanation", List.of()), vendorUser));
            await(loaded);
            Future<?> adminDecision = adminPool.submit(() -> resolve.execute(id,
                    new ResolveDisputeRequest(DisputeStatus.RESOLVED_REFUND, null, "Approved"), admin));
            assertThrows(TimeoutException.class, () -> adminDecision.get(200, TimeUnit.MILLISECONDS));
            release.countDown(); vendorResponse.get(15, TimeUnit.SECONDS); adminDecision.get(15, TimeUnit.SECONDS);
            assertEquals("RESOLVED_REFUND", jdbc.queryForObject("SELECT status FROM disputes WHERE id=?", String.class, id));
            assertEquals(admin, jdbc.queryForObject("SELECT resolved_by FROM disputes WHERE id=?", UUID.class, id));
            assertEquals("Vendor explanation", jdbc.queryForObject("SELECT vendor_response FROM disputes WHERE id=?", String.class, id));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM refunds WHERE sub_order_id=? AND status='PENDING'", Integer.class, subOrder));
        } finally { release.countDown(); vendorPool.shutdownNow(); adminPool.shutdownNow(); }
    }

    @Test
    void responseWaitingForAdminCommitIsRejected() throws Exception {
        UUID id = createDispute();
        CountDownLatch loaded = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(call -> {
            var found = subOrders.findAll().stream().filter(s -> subOrder.equals(s.getId())).findFirst();
            if (Thread.currentThread().getName().equals("admin-resolution")) { loaded.countDown(); await(release); }
            return found;
        }).when(subOrders).findById(subOrder);
        ExecutorService adminPool = Executors.newSingleThreadExecutor(r -> new Thread(r, "admin-resolution"));
        ExecutorService vendorPool = Executors.newSingleThreadExecutor();
        try {
            Future<?> decision = adminPool.submit(() -> resolve.execute(id,
                    new ResolveDisputeRequest(DisputeStatus.RESOLVED_REFUND, null, "Approved"), admin));
            await(loaded);
            Future<?> response = vendorPool.submit(() -> reply.execute(id,
                    new SubmitVendorResponseRequest("Too late", List.of()), vendorUser));
            assertThrows(TimeoutException.class, () -> response.get(200, TimeUnit.MILLISECONDS));
            release.countDown(); decision.get(15, TimeUnit.SECONDS);
            ExecutionException failure = assertThrows(ExecutionException.class, () -> response.get(15, TimeUnit.SECONDS));
            assertInstanceOf(DisputeAlreadyResolvedException.class, failure.getCause());
            assertEquals("RESOLVED_REFUND", jdbc.queryForObject("SELECT status FROM disputes WHERE id=?", String.class, id));
        } finally { release.countDown(); adminPool.shutdownNow(); vendorPool.shutdownNow(); }
    }

    @Test
    void readingOneMessageLeavesOtherMessagesUnread() {
        UUID id = createConversation();
        for (int i = 0; i < 3; i++) send.execute(id, new SendMessageRequest("Incoming " + i, null), vendorUser);
        var first = get.execute(id, null, 0L, PageRequest.of(0, 1), customer);
        assertEquals(1, first.size()); assertTrue(first.get(0).isRead());
        assertEquals(2, messages.countByConversationIdAndSenderIdNotAndIsReadFalse(id, customer));
        var next = get.execute(id, null, first.get(0).sequence(), PageRequest.of(0, 1), customer);
        assertEquals(1, next.size()); assertEquals(2L, next.get(0).sequence());
        assertEquals(1, messages.countByConversationIdAndSenderIdNotAndIsReadFalse(id, customer));
    }

    @Test
    void newResponsesIncludePersistedTimestamps() {
        var conversation = create.execute(new CreateConversationRequest(order, vendorId), customer).conversation();
        assertNotNull(conversation.createdAt()); assertNotNull(conversation.updatedAt());
        var response = send.execute(conversation.id(), new SendMessageRequest("Hello", null), customer);
        assertNotNull(response.createdAt()); assertEquals(1L, response.sequence());
        assertEquals(response.createdAt().toInstant(), jdbc.queryForObject("SELECT created_at FROM messages WHERE id=?",
                OffsetDateTime.class, response.id()).toInstant());
    }

    @Test
    void pendingSendBlocksNextSequenceUntilCommit() throws Exception {
        UUID id = createConversation();
        CountDownLatch inserted = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(call -> {
            if (Thread.currentThread().getName().equals("slow-sender")) { inserted.countDown(); await(release); }
            return call.getArgument(0);
        }).when(conversations).save(any(ConversationJpaEntity.class));
        ExecutorService slowPool = Executors.newSingleThreadExecutor(r -> new Thread(r, "slow-sender"));
        ExecutorService fastPool = Executors.newSingleThreadExecutor();
        try {
            Future<MessageResponse> slow = slowPool.submit(() -> send.execute(id, new SendMessageRequest("First", null), vendorUser));
            await(inserted);
            Future<MessageResponse> fast = fastPool.submit(() -> send.execute(id, new SendMessageRequest("Second", null), vendorUser));
            assertThrows(TimeoutException.class, () -> fast.get(200, TimeUnit.MILLISECONDS));
            assertTrue(get.execute(id, null, 0L, PageRequest.of(0, 50), customer).isEmpty());
            release.countDown();
            assertEquals(1L, slow.get(15, TimeUnit.SECONDS).sequence()); assertEquals(2L, fast.get(15, TimeUnit.SECONDS).sequence());
            var firstPoll = get.execute(id, null, 0L, PageRequest.of(0, 1), customer);
            var nextPoll = get.execute(id, null, firstPoll.get(0).sequence(), PageRequest.of(0, 1), customer);
            assertEquals("First", firstPoll.get(0).content()); assertEquals("Second", nextPoll.get(0).content());
            assertTrue(get.execute(id, null, nextPoll.get(0).sequence(), PageRequest.of(0, 50), customer).isEmpty());
        } finally { release.countDown(); slowPool.shutdownNow(); fastPool.shutdownNow(); }
    }

    @Test
    void rolledBackSendDoesNotConsumeSequenceOrLeaveMessage() {
        UUID id = createConversation();
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            send.execute(id, new SendMessageRequest("Rolled back", null), customer);
            throw new IllegalStateException("Force rollback");
        }));
        assertEquals(0L, jdbc.queryForObject("SELECT last_message_sequence FROM conversations WHERE id=?", Long.class, id));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM messages WHERE conversation_id=?", Integer.class, id));
        assertEquals(1L, send.execute(id, new SendMessageRequest("Committed", null), customer).sequence());
    }

    @Test
    void sequenceCursorAndLegacyCheckpointSurviveClockRegression() {
        UUID id = createConversation();
        var first = send.execute(id, new SendMessageRequest("First", null), vendorUser);
        var second = send.execute(id, new SendMessageRequest("Second", null), vendorUser);
        jdbc.update("UPDATE messages SET created_at=? WHERE id=?", first.createdAt().minusMinutes(1), second.id());
        assertEquals(second.id(), get.execute(id, null, first.sequence(), PageRequest.of(0, 1), customer).get(0).id());
        assertEquals(second.id(), get.execute(id, first.createdAt(), PageRequest.of(0, 1), customer).get(0).id());
        jdbc.update("UPDATE messages SET created_at=? WHERE id=?", first.createdAt(), second.id());
        assertEquals(second.id(), get.execute(id, first.createdAt(), PageRequest.of(0, 1), customer).get(0).id());
    }

    @Test
    void pollingIsBoundedAndRetainsParticipantChecks() {
        UUID id = createConversation();
        for (int i = 0; i < 3; i++) send.execute(id, new SendMessageRequest("Message " + i, null), vendorUser);
        assertEquals(1, get.execute(id, OffsetDateTime.now().minusHours(1), PageRequest.of(0, 1), customer).size());
        assertThrows(UnauthorizedChatAccessException.class, () -> get.execute(id, null, 0L, PageRequest.of(0, 1), UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> get.execute(id, null, -1L, PageRequest.of(0, 1), customer));
        assertThrows(IllegalArgumentException.class, () -> get.execute(id, OffsetDateTime.now(), 0L, PageRequest.of(0, 1), customer));
        assertThrows(IllegalArgumentException.class, () -> get.execute(id, null, 0L, PageRequest.of(0, 101), customer));
    }

    @Test
    void adminResponsesPreserveVendorExplanationAndEvidence() {
        UUID id = createDispute();
        reply.execute(id, new SubmitVendorResponseRequest("Vendor explanation", List.of("https://example.com/proof.png")), vendorUser);
        var response = resolve.execute(id, new ResolveDisputeRequest(DisputeStatus.RESOLVED_REJECTED, null, "Rejected"), admin);
        assertEquals("Vendor explanation", response.vendorResponse());
        assertEquals(List.of("https://example.com/proof.png"), response.vendorEvidenceUrls());
        assertNotNull(response.vendorRespondedAt());
        var listed = adminList.execute(null, null, PageRequest.of(0, 100)).stream().filter(d -> id.equals(d.id())).findFirst().orElseThrow();
        assertEquals(response.vendorResponse(), listed.vendorResponse());
        assertEquals(response.vendorEvidenceUrls(), listed.vendorEvidenceUrls());
    }
}
