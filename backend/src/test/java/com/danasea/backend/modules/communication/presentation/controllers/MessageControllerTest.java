package com.danasea.backend.modules.communication.presentation.controllers;

import com.danasea.backend.modules.communication.application.dtos.MessageResponse;
import com.danasea.backend.modules.communication.application.dtos.SendMessageRequest;
import com.danasea.backend.modules.communication.application.usecases.CreateOrGetConversationUseCase;
import com.danasea.backend.modules.communication.application.usecases.GetConversationsUseCase;
import com.danasea.backend.modules.communication.application.usecases.GetMessagesUseCase;
import com.danasea.backend.modules.communication.application.usecases.SendMessageUseCase;
import com.danasea.backend.modules.communication.presentation.handlers.CommunicationExceptionHandler;
import com.danasea.backend.shared.i18n.LocalizedMessageService;
import com.danasea.backend.shared.presentation.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("Message Controller & REST Polling Specialized Tests")
class MessageControllerTest {

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

    private final UUID customerId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

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

    private void mockSecurityUser(UUID userId, String role) {
        var authorities = List.of(new SimpleGrantedAuthority(role));
        var auth = new UsernamePasswordAuthenticationToken(userId.toString(), "credentials", authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    @Test
    @DisplayName("Polling with after returns empty list when no new messages arrived")
    void polling_NoNewMessages_ReturnsEmptyList() throws Exception {
        mockSecurityUser(customerId, "ROLE_CUSTOMER");
        OffsetDateTime checkpoint = OffsetDateTime.now();

        when(getMessagesUseCase.execute(eq(conversationId), any(OffsetDateTime.class), isNull(), any(Pageable.class), eq(customerId)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                        .param("after", checkpoint.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Polling with after returns multiple messages ordered chronologically")
    void polling_MultipleNewMessages_ReturnsChronologicalOrder() throws Exception {
        mockSecurityUser(customerId, "ROLE_CUSTOMER");
        OffsetDateTime checkpoint = OffsetDateTime.now().minusMinutes(5);
        OffsetDateTime t1 = OffsetDateTime.now().minusMinutes(3);
        OffsetDateTime t2 = OffsetDateTime.now().minusMinutes(1);

        MessageResponse m1 = new MessageResponse(UUID.randomUUID(), conversationId, vendorId, "Msg 1", null, true, t1);
        MessageResponse m2 = new MessageResponse(UUID.randomUUID(), conversationId, vendorId, "Msg 2", null, true, t2);

        when(getMessagesUseCase.execute(eq(conversationId), any(OffsetDateTime.class), isNull(), any(Pageable.class), eq(customerId)))
                .thenReturn(List.of(m1, m2));

        mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                        .param("after", checkpoint.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value("Msg 1"))
                .andExpect(jsonPath("$[1].content").value("Msg 2"));
    }

    @Test
    @DisplayName("Vendor sends message with attachment URL successfully")
    void vendor_SendMessage_WithAttachment_Returns201Created() throws Exception {
        mockSecurityUser(vendorId, "ROLE_VENDOR");
        SendMessageRequest request = new SendMessageRequest("Vui lòng xem ảnh tour", "https://storage.danasea.com/tour.jpg");

        MessageResponse response = new MessageResponse(
                UUID.randomUUID(), conversationId, vendorId, "Vui lòng xem ảnh tour",
                "https://storage.danasea.com/tour.jpg", false, OffsetDateTime.now()
        );

        when(sendMessageUseCase.execute(eq(conversationId), eq(request), eq(vendorId)))
                .thenReturn(response);

        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Vui lòng xem ảnh tour"))
                .andExpect(jsonPath("$.attachmentUrl").value("https://storage.danasea.com/tour.jpg"))
                .andExpect(jsonPath("$.senderId").value(vendorId.toString()));

        verify(sendMessageUseCase).execute(eq(conversationId), eq(request), eq(vendorId));
    }

    @Test
    @DisplayName("Reject message with content exceeding 5000 characters -> 400 Bad Request")
    void sendMessage_ExceedingLength_Returns400() throws Exception {
        mockSecurityUser(customerId, "ROLE_CUSTOMER");
        String oversized = "a".repeat(5001);
        SendMessageRequest request = new SendMessageRequest(oversized, null);

        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
