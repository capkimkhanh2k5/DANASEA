package com.danasea.backend.modules.communication.presentation.controllers;

import com.danasea.backend.modules.communication.application.dtos.ConversationResponse;
import com.danasea.backend.modules.communication.application.dtos.CreateConversationRequest;
import com.danasea.backend.modules.communication.application.dtos.CreateOrGetConversationResult;
import com.danasea.backend.modules.communication.application.dtos.MessageResponse;
import com.danasea.backend.modules.communication.application.dtos.SendMessageRequest;
import com.danasea.backend.modules.communication.application.usecases.CreateOrGetConversationUseCase;
import com.danasea.backend.modules.communication.application.usecases.GetConversationsUseCase;
import com.danasea.backend.modules.communication.application.usecases.GetMessagesUseCase;
import com.danasea.backend.modules.communication.application.usecases.SendMessageUseCase;
import com.danasea.backend.modules.communication.domain.exceptions.ConversationNotFoundException;
import com.danasea.backend.modules.communication.domain.exceptions.UnauthorizedChatAccessException;
import com.danasea.backend.modules.communication.presentation.handlers.CommunicationExceptionHandler;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.Arrays;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationController MockMvc Tests")
class ConversationControllerTest {

    @Mock
    private CreateOrGetConversationUseCase createOrGetConversationUseCase;

    @Mock
    private GetConversationsUseCase getConversationsUseCase;

    @Mock
    private GetMessagesUseCase getMessagesUseCase;

    @Mock
    private SendMessageUseCase sendMessageUseCase;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final UUID currentUserId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID masterOrderId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
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
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockSecurityUser(UUID userId, String... roles) {
        var authorities = Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        var auth = new UsernamePasswordAuthenticationToken(userId.toString(), "credentials", authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    @Nested
    @DisplayName("POST /api/conversations")
    class CreateOrGetConversationEndpoints {

        @Test
        @DisplayName("When conversation is newly created -> returns 201 Created")
        void createConversation_WhenNew_Returns201Created() throws Exception {
            mockSecurityUser(currentUserId, "ROLE_CUSTOMER");

            CreateConversationRequest request = new CreateConversationRequest(masterOrderId, vendorId);
            ConversationResponse convResponse = new ConversationResponse(
                    conversationId, masterOrderId, currentUserId, vendorId,
                    OffsetDateTime.now(), OffsetDateTime.now(), null, 0L
            );
            CreateOrGetConversationResult result = new CreateOrGetConversationResult(convResponse, true);

            when(createOrGetConversationUseCase.execute(eq(request), eq(currentUserId))).thenReturn(result);

            mockMvc.perform(post("/api/conversations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(conversationId.toString()))
                    .andExpect(jsonPath("$.masterOrderId").value(masterOrderId.toString()))
                    .andExpect(jsonPath("$.customerId").value(currentUserId.toString()))
                    .andExpect(jsonPath("$.vendorId").value(vendorId.toString()))
                    .andExpect(jsonPath("$.unreadCount").value(0));

            verify(createOrGetConversationUseCase).execute(eq(request), eq(currentUserId));
        }

        @Test
        @DisplayName("When conversation already exists -> returns 200 OK")
        void createConversation_WhenExisting_Returns200Ok() throws Exception {
            mockSecurityUser(currentUserId, "ROLE_CUSTOMER");

            CreateConversationRequest request = new CreateConversationRequest(masterOrderId, vendorId);
            ConversationResponse convResponse = new ConversationResponse(
                    conversationId, masterOrderId, currentUserId, vendorId,
                    OffsetDateTime.now().minusDays(1), OffsetDateTime.now(), null, 2L
            );
            CreateOrGetConversationResult result = new CreateOrGetConversationResult(convResponse, false);

            when(createOrGetConversationUseCase.execute(eq(request), eq(currentUserId))).thenReturn(result);

            mockMvc.perform(post("/api/conversations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(conversationId.toString()))
                    .andExpect(jsonPath("$.unreadCount").value(2));
        }

        @Test
        @DisplayName("When request is missing masterOrderId -> returns 400 Bad Request")
        void createConversation_MissingMasterOrderId_Returns400() throws Exception {
            mockSecurityUser(currentUserId, "ROLE_CUSTOMER");

            String invalidJson = "{\"vendorId\": \"" + vendorId + "\"}";

            mockMvc.perform(post("/api/conversations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("When order not found -> returns 404 Not Found")
        void createConversation_OrderNotFound_Returns404() throws Exception {
            mockSecurityUser(currentUserId, "ROLE_CUSTOMER");

            CreateConversationRequest request = new CreateConversationRequest(masterOrderId, vendorId);
            when(createOrGetConversationUseCase.execute(eq(request), eq(currentUserId)))
                    .thenThrow(new OrderNotFoundException(masterOrderId));

            mockMvc.perform(post("/api/conversations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        }

        @Test
        @DisplayName("When unauthorized user attempts to start chat -> returns 403 Forbidden")
        void createConversation_Unauthorized_Returns403() throws Exception {
            mockSecurityUser(currentUserId, "ROLE_CUSTOMER");

            CreateConversationRequest request = new CreateConversationRequest(masterOrderId, vendorId);
            when(createOrGetConversationUseCase.execute(eq(request), eq(currentUserId)))
                    .thenThrow(new UnauthorizedChatAccessException("Not authorized for this order"));

            mockMvc.perform(post("/api/conversations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("When unauthenticated -> returns 403 Forbidden")
        void createConversation_Unauthenticated_Returns403() throws Exception {
            CreateConversationRequest request = new CreateConversationRequest(masterOrderId, vendorId);

            mockMvc.perform(post("/api/conversations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Nested
    @DisplayName("GET /api/conversations")
    class GetConversationsEndpoints {

        @Test
        @DisplayName("When authenticated -> returns 200 OK with paginated conversations")
        void getConversations_Authenticated_Returns200Ok() throws Exception {
            mockSecurityUser(currentUserId, "ROLE_CUSTOMER");

            MessageResponse lastMessage = new MessageResponse(
                    UUID.randomUUID(), conversationId, vendorId, "Xin chao quy khach", null, true, OffsetDateTime.now()
            );
            ConversationResponse conv = new ConversationResponse(
                    conversationId, masterOrderId, currentUserId, vendorId,
                    OffsetDateTime.now().minusHours(1), OffsetDateTime.now(), lastMessage, 1L
            );

            when(getConversationsUseCase.execute(eq(currentUserId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(conv), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/conversations?page=0&size=20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(conversationId.toString()))
                    .andExpect(jsonPath("$.content[0].lastMessage.content").value("Xin chao quy khach"))
                    .andExpect(jsonPath("$.content[0].unreadCount").value(1))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("When unauthenticated -> returns 403 Forbidden")
        void getConversations_Unauthenticated_Returns403() throws Exception {
            mockMvc.perform(get("/api/conversations"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Nested
    @DisplayName("GET /api/conversations/{id}/messages")
    class GetMessagesEndpoints {

        @Test
        @DisplayName("When fetching messages without after -> returns 200 OK with messages list")
        void getMessages_WithoutAfter_Returns200Ok() throws Exception {
            mockSecurityUser(currentUserId, "ROLE_CUSTOMER");

            MessageResponse msg1 = new MessageResponse(
                    UUID.randomUUID(), conversationId, currentUserId, "Tin nhắn 1", null, true, OffsetDateTime.now().minusMinutes(5)
            );
            MessageResponse msg2 = new MessageResponse(
                    UUID.randomUUID(), conversationId, vendorId, "Tin nhắn 2", null, true, OffsetDateTime.now().minusMinutes(2)
            );

            when(getMessagesUseCase.execute(eq(conversationId), any(), isNull(), any(), eq(currentUserId)))
                    .thenReturn(List.of(msg1, msg2));

            mockMvc.perform(get("/api/conversations/" + conversationId + "/messages"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].content").value("Tin nhắn 1"))
                    .andExpect(jsonPath("$[1].content").value("Tin nhắn 2"));
        }

        @Test
        @DisplayName("When REST polling with after parameter -> returns 200 OK with newer messages")
        void getMessages_WithAfterPolling_Returns200Ok() throws Exception {
            mockSecurityUser(currentUserId, "ROLE_CUSTOMER");

            OffsetDateTime after = OffsetDateTime.now().minusMinutes(3);
            MessageResponse msg2 = new MessageResponse(
                    UUID.randomUUID(), conversationId, vendorId, "Tin nhắn mới nhất", null, true, OffsetDateTime.now()
            );

            when(getMessagesUseCase.execute(eq(conversationId), any(OffsetDateTime.class), isNull(), any(), eq(currentUserId)))
                    .thenReturn(List.of(msg2));

            mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                            .param("after", after.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].content").value("Tin nhắn mới nhất"));
        }

        @Test
        @DisplayName("When conversation not found -> returns 404 Not Found")
        void getMessages_ConversationNotFound_Returns404() throws Exception {
            mockSecurityUser(currentUserId, "ROLE_CUSTOMER");

            when(getMessagesUseCase.execute(eq(conversationId), any(), isNull(), any(), eq(currentUserId)))
                    .thenThrow(new ConversationNotFoundException(conversationId));

            mockMvc.perform(get("/api/conversations/" + conversationId + "/messages"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
        }

        @Test
        @DisplayName("When non-participant accesses messages -> returns 403 Forbidden")
        void getMessages_UnauthorizedParticipant_Returns403() throws Exception {
            mockSecurityUser(currentUserId, "ROLE_CUSTOMER");

            when(getMessagesUseCase.execute(eq(conversationId), any(), isNull(), any(), eq(currentUserId)))
                    .thenThrow(new UnauthorizedChatAccessException("Not a participant"));

            mockMvc.perform(get("/api/conversations/" + conversationId + "/messages"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Nested
    @DisplayName("POST /api/conversations/{id}/messages")
    class SendMessageEndpoints {

        @Test
        @DisplayName("When valid message sent -> returns 201 Created with MessageResponse")
        void sendMessage_Valid_Returns201Created() throws Exception {
            mockSecurityUser(currentUserId, "ROLE_CUSTOMER");

            SendMessageRequest request = new SendMessageRequest("Xin chào đối tác", "https://example.com/img.png");
            MessageResponse messageResponse = new MessageResponse(
                    UUID.randomUUID(), conversationId, currentUserId, "Xin chào đối tác",
                    "https://example.com/img.png", false, OffsetDateTime.now()
            );

            when(sendMessageUseCase.execute(eq(conversationId), eq(request), eq(currentUserId)))
                    .thenReturn(messageResponse);

            mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.conversationId").value(conversationId.toString()))
                    .andExpect(jsonPath("$.senderId").value(currentUserId.toString()))
                    .andExpect(jsonPath("$.content").value("Xin chào đối tác"))
                    .andExpect(jsonPath("$.attachmentUrl").value("https://example.com/img.png"))
                    .andExpect(jsonPath("$.isRead").value(false));

            verify(sendMessageUseCase).execute(eq(conversationId), eq(request), eq(currentUserId));
        }

        @Test
        @DisplayName("When blank message content -> returns 400 Bad Request")
        void sendMessage_BlankContent_Returns400() throws Exception {
            mockSecurityUser(currentUserId, "ROLE_CUSTOMER");

            SendMessageRequest request = new SendMessageRequest("   ", null);

            mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("When conversation not found -> returns 404 Not Found")
        void sendMessage_ConversationNotFound_Returns404() throws Exception {
            mockSecurityUser(currentUserId, "ROLE_CUSTOMER");

            SendMessageRequest request = new SendMessageRequest("Xin chào", null);
            when(sendMessageUseCase.execute(eq(conversationId), eq(request), eq(currentUserId)))
                    .thenThrow(new ConversationNotFoundException(conversationId));

            mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
        }

        @Test
        @DisplayName("When non-participant attempts to send message -> returns 403 Forbidden")
        void sendMessage_UnauthorizedSender_Returns403() throws Exception {
            mockSecurityUser(currentUserId, "ROLE_CUSTOMER");

            SendMessageRequest request = new SendMessageRequest("Xin chào", null);
            when(sendMessageUseCase.execute(eq(conversationId), eq(request), eq(currentUserId)))
                    .thenThrow(new UnauthorizedChatAccessException("Not allowed to send"));

            mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }
}
