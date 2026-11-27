package com.danasea.backend.modules.communication.presentation.controllers;

import com.danasea.backend.modules.communication.application.dtos.CreateConversationRequest;
import com.danasea.backend.modules.communication.application.dtos.MessageResponse;
import com.danasea.backend.modules.communication.application.dtos.SendMessageRequest;
import com.danasea.backend.modules.communication.application.usecases.CreateOrGetConversationUseCase;
import com.danasea.backend.modules.communication.application.usecases.GetConversationsUseCase;
import com.danasea.backend.modules.communication.application.usecases.GetMessagesUseCase;
import com.danasea.backend.modules.communication.application.usecases.MarkMessagesAsReadUseCase;
import com.danasea.backend.modules.communication.application.usecases.SendMessageUseCase;
import com.danasea.backend.modules.communication.domain.exceptions.UnauthorizedChatAccessException;
import com.danasea.backend.modules.communication.infrastructure.persistence.entities.ConversationJpaEntity;
import com.danasea.backend.modules.communication.infrastructure.persistence.entities.MessageJpaEntity;
import com.danasea.backend.modules.communication.infrastructure.persistence.repositories.JpaConversationRepository;
import com.danasea.backend.modules.communication.infrastructure.persistence.repositories.JpaMessageRepository;
import com.danasea.backend.modules.communication.presentation.handlers.CommunicationExceptionHandler;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.shared.i18n.LocalizedMessageService;
import com.danasea.backend.shared.presentation.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Milestone 2 Challenger Adversarial Test Suite")
class CommunicationChallengerTest {

    @Mock
    private JpaConversationRepository conversationRepository;
    @Mock
    private JpaMessageRepository messageRepository;
    @Mock
    private JpaMasterOrderRepository masterOrderRepository;
    @Mock
    private JpaSubOrderRepository subOrderRepository;
    @Mock
    private VendorInternalApi vendorInternalApi;

    private CreateOrGetConversationUseCase createOrGetConversationUseCase;
    private GetConversationsUseCase getConversationsUseCase;
    private MarkMessagesAsReadUseCase markMessagesAsReadUseCase;
    private GetMessagesUseCase getMessagesUseCase;
    private SendMessageUseCase sendMessageUseCase;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final UUID customerId = UUID.randomUUID();
    private final UUID vendorUserId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final UUID intruderUserId = UUID.randomUUID();
    private final UUID masterOrderId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    private MasterOrderJpaEntity masterOrder;
    private SubOrderJpaEntity subOrder;
    private ConversationJpaEntity conversation;

    @BeforeEach
    void setUp() {
        createOrGetConversationUseCase = new CreateOrGetConversationUseCase(
                conversationRepository,
                messageRepository,
                masterOrderRepository,
                subOrderRepository,
                vendorInternalApi
        );

        getConversationsUseCase = new GetConversationsUseCase(
                conversationRepository,
                messageRepository,
                vendorInternalApi
        );

        markMessagesAsReadUseCase = new MarkMessagesAsReadUseCase(
                conversationRepository,
                messageRepository,
                vendorInternalApi
        );

        getMessagesUseCase = new GetMessagesUseCase(
                conversationRepository,
                messageRepository,
                vendorInternalApi,
                markMessagesAsReadUseCase
        );

        sendMessageUseCase = new SendMessageUseCase(
                conversationRepository,
                messageRepository,
                vendorInternalApi
        );

        ConversationController controller = new ConversationController(
                createOrGetConversationUseCase,
                getConversationsUseCase,
                getMessagesUseCase,
                sendMessageUseCase
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(
                        new CommunicationExceptionHandler(),
                        new GlobalExceptionHandler(LocalizedMessageService.standalone())
                )
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        masterOrder = new MasterOrderJpaEntity();
        masterOrder.setId(masterOrderId);
        masterOrder.setCustomerId(customerId);

        subOrder = new SubOrderJpaEntity();
        subOrder.setId(UUID.randomUUID());
        subOrder.setMasterOrderId(masterOrderId);
        subOrder.setVendorId(vendorId);

        conversation = ConversationJpaEntity.builder()
                .customerId(customerId)
                .vendorId(vendorId)
                .masterOrderId(masterOrderId)
                .build();
        conversation.setId(conversationId);
        conversation.setCreatedAt(OffsetDateTime.now());
        conversation.setUpdatedAt(OffsetDateTime.now());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockSecurityUser(UUID userId, String role) {
        var authorities = List.of(new SimpleGrantedAuthority(role));
        var auth = new UsernamePasswordAuthenticationToken(userId.toString(), "credentials", authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    // =========================================================================
    // SECTION 1: REST POLLING ADVERSARIAL CHALLENGES
    // =========================================================================
    @Nested
    @DisplayName("Challenge 1: REST Polling Integrity & Ordering")
    class RestPollingChallenges {

        @Test
        void sequencePollingReturnsSequenceAndRejectsInvalidParameters() throws Exception {
            mockSecurityUser(customerId, "ROLE_CUSTOMER");
            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
            when(vendorInternalApi.findByUserId(customerId)).thenReturn(Optional.empty());
            MessageJpaEntity message = MessageJpaEntity.builder().conversationId(conversationId)
                    .senderId(vendorUserId).content("Next message").sequence(2L).isRead(false).build();
            message.setId(UUID.randomUUID()); message.setCreatedAt(OffsetDateTime.now());
            when(messageRepository.findByConversationIdAndSequenceGreaterThanOrderBySequenceAsc(
                    eq(conversationId), eq(1L), any(Pageable.class))).thenReturn(List.of(message));
            mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                            .param("afterSequence", "1").param("size", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].sequence").value(2))
                    .andExpect(jsonPath("$[0].isRead").value(true));
            mockMvc.perform(get("/api/conversations/" + conversationId + "/messages").param("afterSequence", "-1"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                            .param("afterSequence", "1").param("after", "2026-10-09T10:00:00Z"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                            .param("afterSequence", "1").param("size", "101"))
                    .andExpect(status().isBadRequest());
        }


        @Test
        @DisplayName("Adversarial: Polling with after timestamp strictly excludes earlier and equal messages (no duplicates)")
        void polling_StrictlyNewer_NoDuplicates() {
            OffsetDateTime t1 = OffsetDateTime.parse("2026-10-09T10:01:00Z");
            OffsetDateTime t2 = OffsetDateTime.parse("2026-10-09T10:02:00Z");
            OffsetDateTime t3 = OffsetDateTime.parse("2026-10-09T10:03:00Z");

            MessageJpaEntity m2 = MessageJpaEntity.builder()
                    .conversationId(conversationId).senderId(vendorId).content("Msg 2").isRead(false).build();
            m2.setId(UUID.randomUUID());
            m2.setCreatedAt(t2);

            MessageJpaEntity m3 = MessageJpaEntity.builder()
                    .conversationId(conversationId).senderId(vendorId).content("Msg 3").isRead(false).build();
            m3.setId(UUID.randomUUID());
            m3.setCreatedAt(t3);

            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
            when(vendorInternalApi.findByUserId(customerId)).thenReturn(Optional.empty());

            when(messageRepository.findByConversationIdAndCreatedAtGreaterThanOrderBySequenceAsc(eq(conversationId), eq(t1), any(Pageable.class)))
                    .thenReturn(List.of(m2, m3));

            List<MessageResponse> responses = getMessagesUseCase.execute(conversationId, t1, null, customerId);

            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).content()).isEqualTo("Msg 2");
            assertThat(responses.get(1).content()).isEqualTo("Msg 3");
            assertThat(responses.get(0).createdAt()).isBefore(responses.get(1).createdAt());

            verify(messageRepository).findByConversationIdAndCreatedAtGreaterThanOrderBySequenceAsc(eq(conversationId), eq(t1), any(Pageable.class));
            verify(messageRepository, never()).findByConversationIdOrderByCreatedAtAsc(conversationId);
        }

        @Test
        @DisplayName("Adversarial: Polling checkpoint at latest message timestamp returns empty list without error")
        void polling_CheckpointAtLatestMessage_ReturnsEmpty() {
            OffsetDateTime latestTime = OffsetDateTime.parse("2026-10-09T10:05:00Z");

            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
            when(vendorInternalApi.findByUserId(customerId)).thenReturn(Optional.empty());
            when(messageRepository.findByConversationIdAndCreatedAtGreaterThanOrderBySequenceAsc(eq(conversationId), eq(latestTime), any(Pageable.class)))
                    .thenReturn(Collections.emptyList());

            List<MessageResponse> responses = getMessagesUseCase.execute(conversationId, latestTime, null, customerId);

            assertThat(responses).isEmpty();
        }

        @Test
        @DisplayName("Adversarial: MockMvc REST polling with ISO-8601 offset format parses correctly")
        void polling_IsoFormat_ParsesCorrectly() throws Exception {
            mockSecurityUser(customerId, "ROLE_CUSTOMER");

            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
            when(vendorInternalApi.findByUserId(customerId)).thenReturn(Optional.empty());

            MessageJpaEntity newMsg = MessageJpaEntity.builder()
                    .conversationId(conversationId).senderId(vendorId).content("Tin sau polling").isRead(false).build();
            newMsg.setId(UUID.randomUUID());
            newMsg.setCreatedAt(OffsetDateTime.parse("2026-10-09T10:01:00+07:00"));

            when(messageRepository.findByConversationIdAndCreatedAtGreaterThanOrderBySequenceAsc(eq(conversationId), any(OffsetDateTime.class), any(Pageable.class)))
                    .thenReturn(List.of(newMsg));

            mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                            .param("after", "2026-10-09T10:00:00+07:00"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].content").value("Tin sau polling"));
        }
    }

    // =========================================================================
    // SECTION 2: READ RECEIPTS (isRead) ADVERSARIAL CHALLENGES
    // =========================================================================
    @Nested
    @DisplayName("Challenge 2: Read Receipt (isRead) & Unread Counts")
    class ReadReceiptChallenges {

        @Test
        @DisplayName("Adversarial: Counterparty message is marked read in DB and returned as isRead=true for recipient")
        void recipientFetches_IncomingMessagesMarkedRead() {
            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
            when(vendorInternalApi.findByUserId(customerId)).thenReturn(Optional.empty());

            MessageJpaEntity vendorMsg = MessageJpaEntity.builder()
                    .conversationId(conversationId).senderId(vendorId).content("Xin chào khách").isRead(false).build();
            vendorMsg.setId(UUID.randomUUID());
            vendorMsg.setCreatedAt(OffsetDateTime.now());

            when(messageRepository.findByConversationId(eq(conversationId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(vendorMsg)));

            List<MessageResponse> responses = getMessagesUseCase.execute(conversationId, null, null, customerId);

            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).isRead()).isTrue();
            verify(messageRepository).markDeliveredMessagesAsRead(eq(conversationId), eq(customerId), anyCollection());
            verify(messageRepository, never()).markMessagesAsRead(any(), any());
        }

        @Test
        @DisplayName("Adversarial: Sender fetching messages sees unread message as isRead=false until counterparty reads")
        void senderFetches_OwnMessageRemainsUnreadUntilCounterpartyReads() {
            Vendor vendor = new Vendor();
            vendor.setId(vendorId);
            vendor.setUserId(vendorUserId);

            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
            when(vendorInternalApi.findByUserId(vendorUserId)).thenReturn(Optional.of(vendor));

            MessageJpaEntity vendorOwnMsg = MessageJpaEntity.builder()
                    .conversationId(conversationId).senderId(vendorUserId).content("Tin của vendor").isRead(false).build();
            vendorOwnMsg.setId(UUID.randomUUID());
            vendorOwnMsg.setCreatedAt(OffsetDateTime.now());

            when(messageRepository.findByConversationId(eq(conversationId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(vendorOwnMsg)));

            List<MessageResponse> responses = getMessagesUseCase.execute(conversationId, null, null, vendorUserId);

            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).isRead()).isFalse();
            verify(messageRepository).markDeliveredMessagesAsRead(eq(conversationId), eq(vendorUserId), anyCollection());
        }

        @Test
        @DisplayName("Adversarial: When counterparty reads message, sender subsequently sees isRead=true")
        void counterpartyReads_SenderSubsequentlySeesRead() {
            Vendor vendor = new Vendor();
            vendor.setId(vendorId);
            vendor.setUserId(vendorUserId);

            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
            when(vendorInternalApi.findByUserId(vendorUserId)).thenReturn(Optional.of(vendor));

            MessageJpaEntity vendorMsgNowRead = MessageJpaEntity.builder()
                    .conversationId(conversationId).senderId(vendorUserId).content("Tin của vendor đã được đọc").isRead(true).build();
            vendorMsgNowRead.setId(UUID.randomUUID());
            vendorMsgNowRead.setCreatedAt(OffsetDateTime.now());

            when(messageRepository.findByConversationId(eq(conversationId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(vendorMsgNowRead)));

            List<MessageResponse> responses = getMessagesUseCase.execute(conversationId, null, null, vendorUserId);

            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).isRead()).isTrue();
        }

        @Test
        @DisplayName("Adversarial: Unread count only increments for counterparty, never for sender")
        void unreadCount_OnlyCountsCounterpartyMessages() {
            when(vendorInternalApi.findByUserId(customerId)).thenReturn(Optional.empty());
            when(conversationRepository.findByCustomerId(eq(customerId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(conversation)));

            when(messageRepository.countByConversationIdAndSenderIdNotAndIsReadFalse(conversationId, customerId))
                    .thenReturn(2L);

            var page = getConversationsUseCase.execute(customerId, PageRequest.of(0, 10));

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).unreadCount()).isEqualTo(2L);
            verify(messageRepository).countByConversationIdAndSenderIdNotAndIsReadFalse(conversationId, customerId);
        }
    }

    // =========================================================================
    // SECTION 3: RBAC & 403 FORBIDDEN ADVERSARIAL CHALLENGES
    // =========================================================================
    @Nested
    @DisplayName("Challenge 3: Strict RBAC & 403 Forbidden Authorization")
    class RbacForbiddenChallenges {

        @Test
        @DisplayName("Adversarial: Intruder attempting to initiate chat on someone else's order -> 403 Forbidden")
        void intruderInitiatesChat_Returns403() throws Exception {
            mockSecurityUser(intruderUserId, "ROLE_CUSTOMER");

            when(masterOrderRepository.findByIdForUpdate(masterOrderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of(subOrder));
            when(vendorInternalApi.findByUserId(intruderUserId)).thenReturn(Optional.empty());

            CreateConversationRequest request = new CreateConversationRequest(masterOrderId, vendorId);

            mockMvc.perform(post("/api/conversations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("Adversarial: Unrelated vendor attempting to initiate chat on order they do not serve -> 403 Forbidden")
        void unrelatedVendorInitiatesChat_Returns403() throws Exception {
            mockSecurityUser(intruderUserId, "ROLE_VENDOR");

            UUID intruderVendorId = UUID.randomUUID();
            Vendor intruderVendor = new Vendor();
            intruderVendor.setId(intruderVendorId);
            intruderVendor.setUserId(intruderUserId);

            when(masterOrderRepository.findByIdForUpdate(masterOrderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of(subOrder));
            when(vendorInternalApi.findByUserId(intruderUserId)).thenReturn(Optional.of(intruderVendor));

            CreateConversationRequest request = new CreateConversationRequest(masterOrderId, intruderVendorId);

            mockMvc.perform(post("/api/conversations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("Adversarial: Intruder attempting to read messages of other participants -> 403 Forbidden")
        void intruderReadsMessages_Returns403() throws Exception {
            mockSecurityUser(intruderUserId, "ROLE_CUSTOMER");

            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
            when(vendorInternalApi.findByUserId(intruderUserId)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/conversations/" + conversationId + "/messages"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("Adversarial: Intruder attempting to inject message into other participants' chat -> 403 Forbidden")
        void intruderInjectsMessage_Returns403() throws Exception {
            mockSecurityUser(intruderUserId, "ROLE_CUSTOMER");

            when(conversationRepository.findByIdForUpdate(conversationId)).thenReturn(Optional.of(conversation));
            when(vendorInternalApi.findByUserId(intruderUserId)).thenReturn(Optional.empty());

            SendMessageRequest request = new SendMessageRequest("Tin nhan gia mao", null);

            mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("Adversarial: Multi-vendor order isolation - Vendor B cannot read or post to Vendor A's conversation")
        void multiVendor_VendorBCannotAccessVendorAConversation() {
            UUID vendorBUserId = UUID.randomUUID();
            UUID vendorBId = UUID.randomUUID();
            Vendor vendorB = new Vendor();
            vendorB.setId(vendorBId);
            vendorB.setUserId(vendorBUserId);

            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
            when(conversationRepository.findByIdForUpdate(conversationId)).thenReturn(Optional.of(conversation));
            when(vendorInternalApi.findByUserId(vendorBUserId)).thenReturn(Optional.of(vendorB));

            assertThatThrownBy(() -> getMessagesUseCase.execute(conversationId, null, null, vendorBUserId))
                    .isInstanceOf(UnauthorizedChatAccessException.class);

            SendMessageRequest request = new SendMessageRequest("Lẻn vào chat của Vendor A", null);
            assertThatThrownBy(() -> sendMessageUseCase.execute(conversationId, request, vendorBUserId))
                    .isInstanceOf(UnauthorizedChatAccessException.class);
        }

        @Test
        @DisplayName("Adversarial: Unauthenticated request to any chat endpoint rejected with 403 Forbidden")
        void unauthenticatedRequests_Return403() throws Exception {
            mockMvc.perform(get("/api/conversations"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

            mockMvc.perform(get("/api/conversations/" + conversationId + "/messages"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

            mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"test\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    // =========================================================================
    // SECTION 4: DATA INTEGRITY & BOUNDARY ATTACKS
    // =========================================================================
    @Nested
    @DisplayName("Challenge 4: Data Integrity, Boundaries & Error Responses")
    class BoundaryChallenges {

        @Test
        @DisplayName("Adversarial: Message content at exact boundary of 5000 characters succeeds")
        void messageContent_At5000Boundary_Succeeds() throws Exception {
            mockSecurityUser(customerId, "ROLE_CUSTOMER");

            when(conversationRepository.findByIdForUpdate(conversationId)).thenReturn(Optional.of(conversation));
            when(vendorInternalApi.findByUserId(customerId)).thenReturn(Optional.empty());

            String maxContent = "X".repeat(5000);
            MessageJpaEntity savedMsg = MessageJpaEntity.builder()
                    .conversationId(conversationId).senderId(customerId).content(maxContent).isRead(false).build();
            savedMsg.setId(UUID.randomUUID());
            savedMsg.setCreatedAt(OffsetDateTime.now());

            when(messageRepository.saveAndFlush(any(MessageJpaEntity.class))).thenReturn(savedMsg);

            SendMessageRequest request = new SendMessageRequest(maxContent, null);

            mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.content").value(maxContent));
        }

        @Test
        @DisplayName("Adversarial: Message content exceeding 5000 characters rejected with 400 Bad Request")
        void messageContent_Exceeding5000_Rejected() throws Exception {
            mockSecurityUser(customerId, "ROLE_CUSTOMER");

            String overflowContent = "X".repeat(5001);
            SendMessageRequest request = new SendMessageRequest(overflowContent, null);

            mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("Adversarial: Message sending updates conversation updatedAt timestamp")
        void sendingMessage_UpdatesConversationUpdatedAt() {
            OffsetDateTime oldUpdatedAt = OffsetDateTime.now().minusHours(2);
            conversation.setUpdatedAt(oldUpdatedAt);

            when(conversationRepository.findByIdForUpdate(conversationId)).thenReturn(Optional.of(conversation));
            when(vendorInternalApi.findByUserId(customerId)).thenReturn(Optional.empty());

            MessageJpaEntity savedMsg = MessageJpaEntity.builder()
                    .conversationId(conversationId).senderId(customerId).content("Xin chao").isRead(false).build();
            savedMsg.setId(UUID.randomUUID());
            savedMsg.setCreatedAt(OffsetDateTime.now());

            when(messageRepository.saveAndFlush(any(MessageJpaEntity.class))).thenReturn(savedMsg);

            SendMessageRequest request = new SendMessageRequest("Xin chao", null);
            sendMessageUseCase.execute(conversationId, request, customerId);

            assertThat(conversation.getUpdatedAt()).isAfter(oldUpdatedAt);
            verify(conversationRepository).save(conversation);
        }

        @Test
        @DisplayName("Adversarial: Multi-vendor order without specified vendorId rejected with 400 Bad Request")
        void multiVendorOrder_WithoutVendorId_Rejected() throws Exception {
            mockSecurityUser(customerId, "ROLE_CUSTOMER");

            SubOrderJpaEntity subOrder2 = new SubOrderJpaEntity();
            subOrder2.setId(UUID.randomUUID());
            subOrder2.setMasterOrderId(masterOrderId);
            subOrder2.setVendorId(UUID.randomUUID());

            when(masterOrderRepository.findByIdForUpdate(masterOrderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of(subOrder, subOrder2));
            when(vendorInternalApi.findByUserId(customerId)).thenReturn(Optional.empty());

            CreateConversationRequest request = new CreateConversationRequest(masterOrderId, null);

            mockMvc.perform(post("/api/conversations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
    }
}
