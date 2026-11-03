package com.danasea.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.communication.infrastructure.persistence.entities.ConversationJpaEntity;
import com.danasea.backend.modules.communication.infrastructure.persistence.entities.MessageJpaEntity;
import com.danasea.backend.modules.communication.infrastructure.persistence.repositories.JpaConversationRepository;
import com.danasea.backend.modules.communication.infrastructure.persistence.repositories.JpaMessageRepository;
import com.danasea.backend.modules.dispute.domain.models.Dispute;
import com.danasea.backend.modules.dispute.domain.models.DisputeReason;
import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.dispute.infrastructure.persistence.entities.DisputeJpaEntity;
import com.danasea.backend.modules.dispute.infrastructure.persistence.mappers.DisputeMapper;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;

import com.danasea.backend.configs.AbstractExternalInfrastructureIntegrationTest;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class Milestone1EmpiricalPersistenceChallengerTest extends AbstractExternalInfrastructureIntegrationTest {

    @MockitoBean
    private io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager<byte[]> proxyManager;

    @Autowired
    private JpaConversationRepository conversationRepository;

    @Autowired
    private JpaMessageRepository messageRepository;

    @Autowired
    private JpaDisputeRepository disputeRepository;

    @Autowired
    private DisputeMapper disputeMapper;

    @Nested
    @DisplayName("Challenger Tests for JpaConversationRepository")
    class ConversationRepositoryTests {

        private UUID customerId;
        private UUID vendorId1;
        private UUID vendorId2;
        private UUID masterOrderId;

        @BeforeEach
        void setUp() {
            customerId = UUID.randomUUID();
            vendorId1 = UUID.randomUUID();
            vendorId2 = UUID.randomUUID();
            masterOrderId = UUID.randomUUID();
        }

        @Test
        @DisplayName("TC-CONV-01: Query by CustomerId, VendorId, and MasterOrderId")
        void testFindByCustomerIdAndVendorIdAndMasterOrderId() {
            ConversationJpaEntity entity = ConversationJpaEntity.builder()
                    .customerId(customerId)
                    .vendorId(vendorId1)
                    .masterOrderId(masterOrderId)
                    .build();
            entity.setId(UUID.randomUUID());
            entity.markNew();
            conversationRepository.saveAndFlush(entity);

            Optional<ConversationJpaEntity> found = conversationRepository
                    .findByCustomerIdAndVendorIdAndMasterOrderId(customerId, vendorId1, masterOrderId);
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(entity.getId());

            // Mismatched parameters
            assertThat(conversationRepository.findByCustomerIdAndVendorIdAndMasterOrderId(UUID.randomUUID(), vendorId1, masterOrderId)).isEmpty();
            assertThat(conversationRepository.findByCustomerIdAndVendorIdAndMasterOrderId(customerId, UUID.randomUUID(), masterOrderId)).isEmpty();
            assertThat(conversationRepository.findByCustomerIdAndVendorIdAndMasterOrderId(customerId, vendorId1, UUID.randomUUID())).isEmpty();
        }

        @Test
        @DisplayName("TC-CONV-02: Stress-test findByMasterOrderId with multiple conversations (Multi-Vendor order)")
        void testFindByMasterOrderIdCollisionBehavior() {
            ConversationJpaEntity conv1 = ConversationJpaEntity.builder()
                    .customerId(customerId)
                    .vendorId(vendorId1)
                    .masterOrderId(masterOrderId)
                    .build();
            conv1.setId(UUID.randomUUID());
            conv1.markNew();
            conversationRepository.saveAndFlush(conv1);

            ConversationJpaEntity conv2 = ConversationJpaEntity.builder()
                    .customerId(customerId)
                    .vendorId(vendorId2)
                    .masterOrderId(masterOrderId)
                    .build();
            conv2.setId(UUID.randomUUID());
            conv2.markNew();
            conversationRepository.saveAndFlush(conv2);

            // CHALLENGE: If a master order has 2 vendors, calling findByMasterOrderId throws IncorrectResultSizeDataAccessException!
            assertThatThrownBy(() -> conversationRepository.findByMasterOrderId(masterOrderId))
                    .isInstanceOf(IncorrectResultSizeDataAccessException.class);
        }

        @Test
        @DisplayName("TC-CONV-03: Pagination queries by Customer, Vendor, and Or condition")
        void testPaginationQueries() {
            for (int i = 0; i < 5; i++) {
                ConversationJpaEntity conv = ConversationJpaEntity.builder()
                        .customerId(customerId)
                        .vendorId(UUID.randomUUID())
                        .masterOrderId(UUID.randomUUID())
                        .build();
                conv.setId(UUID.randomUUID());
                conv.markNew();
                conversationRepository.save(conv);
            }
            conversationRepository.flush();

            Page<ConversationJpaEntity> page = conversationRepository.findByCustomerId(customerId, PageRequest.of(0, 3));
            assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(5);
            assertThat(page.getContent()).hasSize(3);

            Page<ConversationJpaEntity> orPage = conversationRepository.findByCustomerIdOrVendorId(customerId, UUID.randomUUID(), PageRequest.of(0, 10));
            assertThat(orPage.getContent()).hasSizeGreaterThanOrEqualTo(5);
        }
    }

    @Nested
    @DisplayName("Challenger Tests for JpaMessageRepository")
    class MessageRepositoryTests {

        private UUID conversationId;
        private UUID senderId1;
        private UUID senderId2;

        @BeforeEach
        void setUp() {
            conversationId = UUID.randomUUID();
            senderId1 = UUID.randomUUID();
            senderId2 = UUID.randomUUID();
        }

        @Test
        @DisplayName("TC-MSG-01: Message polling with CreatedAt order and GreaterThan filter")
        void testMessageOrderingAndPolling() throws InterruptedException {
            OffsetDateTime t0 = OffsetDateTime.now().minusSeconds(10);

            MessageJpaEntity msg1 = MessageJpaEntity.builder()
                    .sequence(1L)
                    .conversationId(conversationId)
                    .senderId(senderId1)
                    .content("Message 1")
                    .isRead(false)
                    .build();
            msg1.setId(UUID.randomUUID());
            msg1.markNew();
            messageRepository.saveAndFlush(msg1);

            OffsetDateTime after = msg1.getCreatedAt();

            // Wait brief moment to guarantee distinct createdAt timestamp
            Thread.sleep(100);

            MessageJpaEntity msg2 = MessageJpaEntity.builder()
                    .sequence(2L)
                    .conversationId(conversationId)
                    .senderId(senderId2)
                    .content("Message 2")
                    .isRead(false)
                    .build();
            msg2.setId(UUID.randomUUID());
            msg2.markNew();
            messageRepository.saveAndFlush(msg2);

            List<MessageJpaEntity> allAsc = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
            assertThat(allAsc).hasSize(2);
            assertThat(allAsc.get(0).getContent()).isEqualTo("Message 1");
            assertThat(allAsc.get(1).getContent()).isEqualTo("Message 2");

            // Polling after t0
            List<MessageJpaEntity> polledAfterT0 = messageRepository
                    .findByConversationIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(conversationId, t0);
            assertThat(polledAfterT0).hasSize(2);

            // Latest message
            Optional<MessageJpaEntity> latest = messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(conversationId);
            assertThat(latest).isPresent();
            assertThat(latest.get().getContent()).isEqualTo("Message 2");
        }

        @Test
        @DisplayName("TC-MSG-02: countByConversationIdAndSenderIdNotAndIsReadFalse unread calculation")
        void testUnreadCountLogic() {
            // Message from sender 1, unread
            MessageJpaEntity m1 = MessageJpaEntity.builder()
                    .sequence(1L)
                    .conversationId(conversationId)
                    .senderId(senderId1)
                    .content("Unread 1")
                    .isRead(false)
                    .build();
            m1.setId(UUID.randomUUID());
            m1.markNew();
            messageRepository.save(m1);

            // Message from sender 1, read
            MessageJpaEntity m2 = MessageJpaEntity.builder()
                    .sequence(2L)
                    .conversationId(conversationId)
                    .senderId(senderId1)
                    .content("Read 1")
                    .isRead(true)
                    .build();
            m2.setId(UUID.randomUUID());
            m2.markNew();
            messageRepository.save(m2);

            // Message from sender 2 (the reader himself), unread
            MessageJpaEntity m3 = MessageJpaEntity.builder()
                    .sequence(3L)
                    .conversationId(conversationId)
                    .senderId(senderId2)
                    .content("Self message")
                    .isRead(false)
                    .build();
            m3.setId(UUID.randomUUID());
            m3.markNew();
            messageRepository.save(m3);

            messageRepository.flush();

            // When reader is senderId2: only m1 should be counted (senderId1 != senderId2 AND isRead == false)
            long unreadCountForReader2 = messageRepository
                    .countByConversationIdAndSenderIdNotAndIsReadFalse(conversationId, senderId2);
            assertThat(unreadCountForReader2).isEqualTo(1L);

            // When reader is senderId1: m3 should be counted (senderId2 != senderId1 AND isRead == false)
            long unreadCountForReader1 = messageRepository
                    .countByConversationIdAndSenderIdNotAndIsReadFalse(conversationId, senderId1);
            assertThat(unreadCountForReader1).isEqualTo(1L);

            // When reader is a third party: both m1 and m3 are unread
            UUID strangerId = UUID.randomUUID();
            long unreadForStranger = messageRepository
                    .countByConversationIdAndSenderIdNotAndIsReadFalse(conversationId, strangerId);
            assertThat(unreadForStranger).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("Challenger Tests for JpaDisputeRepository")
    class DisputeRepositoryTests {

        private UUID subOrderId1;
        private UUID subOrderId2;
        private UUID customerId;

        @BeforeEach
        void setUp() {
            subOrderId1 = UUID.randomUUID();
            subOrderId2 = UUID.randomUUID();
            customerId = UUID.randomUUID();
        }

        @Test
        @DisplayName("TC-DISP-01: Vendor Dispute Queries and Empty SubOrderIds Edge Case")
        void testDisputeSubOrderInQueries() {
            DisputeJpaEntity d1 = DisputeJpaEntity.builder()
                    .subOrderId(subOrderId1)
                    .customerId(customerId)
                    .reason(DisputeReason.SAFETY_CONCERN)
                    .description("Safety issue")
                    .status(DisputeStatus.OPEN)
                    .vendorResponse("Vendor reply here")
                    .vendorEvidenceUrls(List.of("https://cdn.example.com/proof1.png", "https://cdn.example.com/proof2.png"))
                    .vendorRespondedAt(OffsetDateTime.now())
                    .build();
            d1.setId(UUID.randomUUID());
            d1.markNew();
            disputeRepository.saveAndFlush(d1);

            // Query by subOrderIds
            Page<DisputeJpaEntity> foundPage = disputeRepository.findBySubOrderIdIn(List.of(subOrderId1, subOrderId2), PageRequest.of(0, 10));
            assertThat(foundPage.getTotalElements()).isEqualTo(1L);
            assertThat(foundPage.getContent().get(0).getVendorResponse()).isEqualTo("Vendor reply here");
            assertThat(foundPage.getContent().get(0).getVendorEvidenceUrls()).containsExactly("https://cdn.example.com/proof1.png", "https://cdn.example.com/proof2.png");

            // Query by status
            Page<DisputeJpaEntity> openPage = disputeRepository.findBySubOrderIdInAndStatus(List.of(subOrderId1), DisputeStatus.OPEN, PageRequest.of(0, 10));
            assertThat(openPage.getTotalElements()).isEqualTo(1L);

            Page<DisputeJpaEntity> reviewPage = disputeRepository.findBySubOrderIdInAndStatus(List.of(subOrderId1), DisputeStatus.UNDER_REVIEW, PageRequest.of(0, 10));
            assertThat(reviewPage.getTotalElements()).isZero();

            // Query by ID and subOrderIdIn (Vendor access control)
            Optional<DisputeJpaEntity> authorized = disputeRepository.findByIdAndSubOrderIdIn(d1.getId(), List.of(subOrderId1));
            assertThat(authorized).isPresent();

            Optional<DisputeJpaEntity> unauthorized = disputeRepository.findByIdAndSubOrderIdIn(d1.getId(), List.of(subOrderId2));
            assertThat(unauthorized).isEmpty();

            // CRITICAL EDGE CASE: Empty subOrderIds list
            // In Spring Data JPA, when a collection in 'In' is empty, does it fail or return empty?
            assertDoesNotThrow(() -> {
                Page<DisputeJpaEntity> emptyPage = disputeRepository.findBySubOrderIdIn(Collections.emptyList(), PageRequest.of(0, 10));
                assertThat(emptyPage.getTotalElements()).isZero();
            });

            assertDoesNotThrow(() -> {
                Optional<DisputeJpaEntity> emptyOpt = disputeRepository.findByIdAndSubOrderIdIn(d1.getId(), Collections.emptyList());
                assertThat(emptyOpt).isEmpty();
            });
        }

        @Test
        @DisplayName("TC-DISP-02: DisputeMapper bidirectional consistency with vendor response fields")
        void testDisputeMapperBidirectional() {
            OffsetDateTime now = OffsetDateTime.now();
            Dispute domain = Dispute.builder()
                    .id(UUID.randomUUID())
                    .subOrderId(subOrderId1)
                    .customerId(customerId)
                    .reason(DisputeReason.SERVICE_NOT_AS_DESCRIBED)
                    .description("Test description")
                    .status(DisputeStatus.OPEN)
                    .vendorResponse("Explanation from vendor")
                    .vendorEvidenceUrls(List.of("https://cdn.example.com/img1.jpg"))
                    .vendorRespondedAt(now)
                    .build();

            DisputeJpaEntity entity = disputeMapper.toEntity(domain);
            assertThat(entity.getVendorResponse()).isEqualTo("Explanation from vendor");
            assertThat(entity.getVendorEvidenceUrls()).containsExactly("https://cdn.example.com/img1.jpg");
            assertThat(entity.getVendorRespondedAt()).isEqualTo(now);

            Dispute roundTrip = disputeMapper.toDomain(entity);
            assertThat(roundTrip.getVendorResponse()).isEqualTo("Explanation from vendor");
            assertThat(roundTrip.getVendorEvidenceUrls()).containsExactly("https://cdn.example.com/img1.jpg");
            assertThat(roundTrip.getVendorRespondedAt()).isEqualTo(now);
        }
    }
}
