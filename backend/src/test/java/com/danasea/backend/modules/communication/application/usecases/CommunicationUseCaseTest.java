package com.danasea.backend.modules.communication.application.usecases;

import com.danasea.backend.modules.communication.application.dtos.CreateConversationRequest;
import com.danasea.backend.modules.communication.application.dtos.CreateOrGetConversationResult;
import com.danasea.backend.modules.communication.application.dtos.MessageResponse;
import com.danasea.backend.modules.communication.application.dtos.SendMessageRequest;
import com.danasea.backend.modules.communication.domain.exceptions.ConversationNotFoundException;
import com.danasea.backend.modules.communication.domain.exceptions.UnauthorizedChatAccessException;
import com.danasea.backend.modules.communication.infrastructure.persistence.entities.ConversationJpaEntity;
import com.danasea.backend.modules.communication.infrastructure.persistence.entities.MessageJpaEntity;
import com.danasea.backend.modules.communication.infrastructure.persistence.repositories.JpaConversationRepository;
import com.danasea.backend.modules.communication.infrastructure.persistence.repositories.JpaMessageRepository;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import com.danasea.backend.modules.communication.application.dtos.ConversationResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("Communication Module Use Cases Unit Tests")
class CommunicationUseCaseTest {

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

    private final UUID customerId = UUID.randomUUID();
    private final UUID vendorUserId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
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

    @Nested
    @DisplayName("CreateOrGetConversationUseCase Tests")
    class CreateOrGetConversationTests {

        @Test
        @DisplayName("Order not found throws OrderNotFoundException")
        void orderNotFound_ThrowsException() {
            when(masterOrderRepository.findByIdForUpdate(masterOrderId)).thenReturn(Optional.empty());

            CreateConversationRequest request = new CreateConversationRequest(masterOrderId);

            assertThatThrownBy(() -> createOrGetConversationUseCase.execute(request, customerId))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("Order has no sub-orders throws IllegalArgumentException")
        void orderHasNoSubOrders_ThrowsException() {
            when(masterOrderRepository.findByIdForUpdate(masterOrderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of());

            CreateConversationRequest request = new CreateConversationRequest(masterOrderId);

            assertThatThrownBy(() -> createOrGetConversationUseCase.execute(request, customerId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Order has no sub-orders");
        }

        @Test
        @DisplayName("Customer creates conversation when 1 vendor exists without specifying vendorId -> automatically resolves")
        void customerCreates_SingleVendor_AutoResolved() {
            when(masterOrderRepository.findByIdForUpdate(masterOrderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of(subOrder));
            when(vendorInternalApi.findByUserId(customerId)).thenReturn(Optional.empty());
            when(conversationRepository.findByCustomerIdAndVendorIdAndMasterOrderId(customerId, vendorId, masterOrderId))
                    .thenReturn(Optional.empty());
            when(conversationRepository.saveAndFlush(any(ConversationJpaEntity.class))).thenReturn(conversation);

            CreateConversationRequest request = new CreateConversationRequest(masterOrderId);
            CreateOrGetConversationResult result = createOrGetConversationUseCase.execute(request, customerId);

            assertThat(result.isNew()).isTrue();
            assertThat(result.conversation().customerId()).isEqualTo(customerId);
            assertThat(result.conversation().vendorId()).isEqualTo(vendorId);
            verify(conversationRepository).saveAndFlush(any(ConversationJpaEntity.class));
        }

        @Test
        @DisplayName("Customer accesses existing conversation -> returns isNew = false")
        void customerAccesses_ExistingConversation() {
            when(masterOrderRepository.findByIdForUpdate(masterOrderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of(subOrder));
            when(vendorInternalApi.findByUserId(customerId)).thenReturn(Optional.empty());
            when(conversationRepository.findByCustomerIdAndVendorIdAndMasterOrderId(customerId, vendorId, masterOrderId))
                    .thenReturn(Optional.of(conversation));
            when(messageRepository.findFirstByConversationIdOrderBySequenceDesc(conversationId)).thenReturn(Optional.empty());
            when(messageRepository.countByConversationIdAndSenderIdNotAndIsReadFalse(conversationId, customerId)).thenReturn(0L);

            CreateConversationRequest request = new CreateConversationRequest(masterOrderId, vendorId);
            CreateOrGetConversationResult result = createOrGetConversationUseCase.execute(request, customerId);

            assertThat(result.isNew()).isFalse();
            assertThat(result.conversation().id()).isEqualTo(conversationId);
            verify(conversationRepository, never()).saveAndFlush(any(ConversationJpaEntity.class));
        }

        @Test
        @DisplayName("Customer specifies vendor not in order -> throws UnauthorizedChatAccessException")
        void customerSpecifiesInvalidVendor_ThrowsException() {
            UUID wrongVendorId = UUID.randomUUID();
            when(masterOrderRepository.findByIdForUpdate(masterOrderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of(subOrder));
            when(vendorInternalApi.findByUserId(customerId)).thenReturn(Optional.empty());

            CreateConversationRequest request = new CreateConversationRequest(masterOrderId, wrongVendorId);

            assertThatThrownBy(() -> createOrGetConversationUseCase.execute(request, customerId))
                    .isInstanceOf(UnauthorizedChatAccessException.class)
                    .hasMessageContaining("Specified vendor is not associated with this order");
        }

        @Test
        @DisplayName("Customer with multi-vendor order not specifying vendorId -> throws IllegalArgumentException")
        void customerMultiVendor_WithoutVendorId_ThrowsException() {
            SubOrderJpaEntity subOrder2 = new SubOrderJpaEntity();
            subOrder2.setId(UUID.randomUUID());
            subOrder2.setMasterOrderId(masterOrderId);
            subOrder2.setVendorId(UUID.randomUUID());

            when(masterOrderRepository.findByIdForUpdate(masterOrderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of(subOrder, subOrder2));
            when(vendorInternalApi.findByUserId(customerId)).thenReturn(Optional.empty());

            CreateConversationRequest request = new CreateConversationRequest(masterOrderId, null);

            assertThatThrownBy(() -> createOrGetConversationUseCase.execute(request, customerId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Multiple vendors found");
        }

        @Test
        @DisplayName("Vendor belonging to order creates or gets conversation -> succeeds")
        void vendorBelongingToOrder_Succeeds() {
            Vendor vendor = new Vendor();
            vendor.setId(vendorId);
            vendor.setUserId(vendorUserId);

            when(masterOrderRepository.findByIdForUpdate(masterOrderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of(subOrder));
            when(vendorInternalApi.findByUserId(vendorUserId)).thenReturn(Optional.of(vendor));
            when(conversationRepository.findByCustomerIdAndVendorIdAndMasterOrderId(customerId, vendorId, masterOrderId))
                    .thenReturn(Optional.of(conversation));
            when(messageRepository.findFirstByConversationIdOrderBySequenceDesc(conversationId)).thenReturn(Optional.empty());
            when(messageRepository.countByConversationIdAndSenderIdNotAndIsReadFalse(conversationId, vendorUserId)).thenReturn(0L);

            CreateConversationRequest request = new CreateConversationRequest(masterOrderId);
            CreateOrGetConversationResult result = createOrGetConversationUseCase.execute(request, vendorUserId);

            assertThat(result.isNew()).isFalse();
            assertThat(result.conversation().id()).isEqualTo(conversationId);
        }

        @Test
        @DisplayName("Vendor NOT belonging to order attempts to create conversation -> throws UnauthorizedChatAccessException")
        void vendorNotBelongingToOrder_ThrowsException() {
            UUID otherVendorId = UUID.randomUUID();
            Vendor otherVendor = new Vendor();
            otherVendor.setId(otherVendorId);
            otherVendor.setUserId(otherUserId);

            when(masterOrderRepository.findByIdForUpdate(masterOrderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of(subOrder));
            when(vendorInternalApi.findByUserId(otherUserId)).thenReturn(Optional.of(otherVendor));

            CreateConversationRequest request = new CreateConversationRequest(masterOrderId);

            assertThatThrownBy(() -> createOrGetConversationUseCase.execute(request, otherUserId))
                    .isInstanceOf(UnauthorizedChatAccessException.class)
                    .hasMessageContaining("Vendor does not belong to this order");
        }

        @Test
        @DisplayName("Unrelated user (neither customer nor vendor) -> throws UnauthorizedChatAccessException")
        void unrelatedUser_ThrowsException() {
            when(masterOrderRepository.findByIdForUpdate(masterOrderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of(subOrder));
            when(vendorInternalApi.findByUserId(otherUserId)).thenReturn(Optional.empty());

            CreateConversationRequest request = new CreateConversationRequest(masterOrderId);

            assertThatThrownBy(() -> createOrGetConversationUseCase.execute(request, otherUserId))
                    .isInstanceOf(UnauthorizedChatAccessException.class)
                    .hasMessageContaining("User is not authorized");
        }
    }

    @Nested
    @DisplayName("GetConversationsUseCase Tests")
    class GetConversationsTests {

        @Test
        @DisplayName("Customer gets conversations with unreadCount and lastMessage")
        void customerGetsConversations() {
            when(vendorInternalApi.findByUserId(customerId)).thenReturn(Optional.empty());
            when(conversationRepository.findByCustomerId(eq(customerId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(conversation)));

            MessageJpaEntity lastMsg = MessageJpaEntity.builder()
                    .conversationId(conversationId)
                    .senderId(vendorId)
                    .content("Chào bạn")
                    .isRead(false)
                    .build();
            lastMsg.setId(UUID.randomUUID());
            lastMsg.setCreatedAt(OffsetDateTime.now());

            when(messageRepository.findFirstByConversationIdOrderBySequenceDesc(conversationId))
                    .thenReturn(Optional.of(lastMsg));
            when(messageRepository.countByConversationIdAndSenderIdNotAndIsReadFalse(conversationId, customerId))
                    .thenReturn(1L);

            Page<ConversationResponse> result =
                    getConversationsUseCase.execute(customerId, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).lastMessage().content()).isEqualTo("Chào bạn");
            assertThat(result.getContent().get(0).unreadCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Vendor gets conversations through vendorId lookup")
        void vendorGetsConversations() {
            Vendor vendor = new Vendor();
            vendor.setId(vendorId);
            vendor.setUserId(vendorUserId);

            when(vendorInternalApi.findByUserId(vendorUserId)).thenReturn(Optional.of(vendor));
            when(conversationRepository.findByCustomerIdOrVendorId(eq(vendorUserId), eq(vendorId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(conversation)));
            when(messageRepository.findFirstByConversationIdOrderBySequenceDesc(conversationId)).thenReturn(Optional.empty());
            when(messageRepository.countByConversationIdAndSenderIdNotAndIsReadFalse(conversationId, vendorUserId)).thenReturn(0L);

            Page<ConversationResponse> result =
                    getConversationsUseCase.execute(vendorUserId, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).id()).isEqualTo(conversationId);
        }
    }

    @Nested
    @DisplayName("GetMessagesUseCase Tests")
    class GetMessagesTests {

        @Test
        @DisplayName("Participant fetches messages and counterparty unread messages are marked read")
        void participantFetchesMessages_MarksAsRead() {
            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
            when(vendorInternalApi.findByUserId(customerId)).thenReturn(Optional.empty());

            MessageJpaEntity msgFromVendor = MessageJpaEntity.builder()
                    .conversationId(conversationId)
                    .senderId(vendorId)
                    .content("Alo ban oi")
                    .isRead(false)
                    .build();
            msgFromVendor.setId(UUID.randomUUID());
            msgFromVendor.setCreatedAt(OffsetDateTime.now());

            when(messageRepository.findByConversationId(eq(conversationId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(msgFromVendor)));

            List<MessageResponse> responses = getMessagesUseCase.execute(conversationId, null, null, customerId);

            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).content()).isEqualTo("Alo ban oi");
            assertThat(responses.get(0).isRead()).isTrue();
            verify(messageRepository).markDeliveredMessagesAsRead(eq(conversationId), eq(customerId), anyCollection());
            verify(messageRepository, never()).markMessagesAsRead(any(), any());
        }

        @Test
        @DisplayName("Polling with after returns filtered messages")
        void pollingWithAfter_ReturnsFilteredMessages() {
            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
            when(vendorInternalApi.findByUserId(customerId)).thenReturn(Optional.empty());

            OffsetDateTime after = OffsetDateTime.now().minusMinutes(5);
            MessageJpaEntity msgNew = MessageJpaEntity.builder()
                    .conversationId(conversationId)
                    .senderId(customerId)
                    .content("Minh day")
                    .isRead(false)
                    .build();
            msgNew.setId(UUID.randomUUID());
            msgNew.setCreatedAt(OffsetDateTime.now());

            when(messageRepository.findByConversationIdAndCreatedAtGreaterThanOrderBySequenceAsc(eq(conversationId), eq(after), any(Pageable.class)))
                    .thenReturn(List.of(msgNew));

            List<MessageResponse> responses = getMessagesUseCase.execute(conversationId, after, null, customerId);

            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).content()).isEqualTo("Minh day");
        }

        @Test
        @DisplayName("Non-participant accessing messages throws UnauthorizedChatAccessException")
        void nonParticipant_ThrowsException() {
            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
            when(vendorInternalApi.findByUserId(otherUserId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getMessagesUseCase.execute(conversationId, null, null, otherUserId))
                    .isInstanceOf(UnauthorizedChatAccessException.class);
        }

        @Test
        @DisplayName("Conversation not found throws ConversationNotFoundException")
        void conversationNotFound_ThrowsException() {
            when(conversationRepository.findById(conversationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getMessagesUseCase.execute(conversationId, null, null, customerId))
                    .isInstanceOf(ConversationNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("SendMessageUseCase Tests")
    class SendMessageTests {

        @Test
        @DisplayName("Participant sends message successfully and updates conversation updatedAt")
        void participantSendsMessage_Success() {
            when(conversationRepository.findByIdForUpdate(conversationId)).thenReturn(Optional.of(conversation));
            when(vendorInternalApi.findByUserId(customerId)).thenReturn(Optional.empty());

            MessageJpaEntity savedMessage = MessageJpaEntity.builder()
                    .conversationId(conversationId)
                    .senderId(customerId)
                    .content("Xin chao")
                    .attachmentUrl("https://example.com/img.jpg")
                    .isRead(false)
                    .build();
            savedMessage.setId(UUID.randomUUID());
            savedMessage.setCreatedAt(OffsetDateTime.now());

            when(messageRepository.saveAndFlush(any(MessageJpaEntity.class))).thenReturn(savedMessage);

            SendMessageRequest request = new SendMessageRequest("Xin chao", "https://example.com/img.jpg");
            MessageResponse response = sendMessageUseCase.execute(conversationId, request, customerId);

            assertThat(response.content()).isEqualTo("Xin chao");
            assertThat(response.attachmentUrl()).isEqualTo("https://example.com/img.jpg");
            assertThat(response.isRead()).isFalse();
            verify(messageRepository).saveAndFlush(any(MessageJpaEntity.class));
            verify(conversationRepository).save(conversation);
        }

        @Test
        @DisplayName("Blank content throws IllegalArgumentException")
        void blankContent_ThrowsException() {
            SendMessageRequest request = new SendMessageRequest("   ", null);

            assertThatThrownBy(() -> sendMessageUseCase.execute(conversationId, request, customerId))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Non-participant sending message throws UnauthorizedChatAccessException")
        void nonParticipantSending_ThrowsException() {
            when(conversationRepository.findByIdForUpdate(conversationId)).thenReturn(Optional.of(conversation));
            when(vendorInternalApi.findByUserId(otherUserId)).thenReturn(Optional.empty());

            SendMessageRequest request = new SendMessageRequest("Tin nhan hack", null);

            assertThatThrownBy(() -> sendMessageUseCase.execute(conversationId, request, otherUserId))
                    .isInstanceOf(UnauthorizedChatAccessException.class);
        }
    }
}
