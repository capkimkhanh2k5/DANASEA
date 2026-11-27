package com.danasea.backend.modules.ai.presentation.controllers;

import com.danasea.backend.modules.ai.application.ports.ConfirmationCardStorePort;
import com.danasea.backend.modules.ai.application.ports.RateLimiterPort;
import com.danasea.backend.modules.ai.application.usecases.ChatUseCase;
import com.danasea.backend.modules.ai.application.usecases.ConfirmBookingUseCase;
import com.danasea.backend.modules.ai.domain.models.AiMessageRole;
import com.danasea.backend.modules.ai.domain.models.ConfirmationCard;
import com.danasea.backend.modules.ai.domain.services.AssistantAuditLogService;
import com.danasea.backend.modules.ai.domain.services.ChatHistoryService;
import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiConversationJpaEntity;
import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiMessageJpaEntity;
import com.danasea.backend.modules.ai.infrastructure.persistence.repositories.JpaAiConversationRepository;
import com.danasea.backend.modules.ai.infrastructure.persistence.repositories.JpaAiMessageRepository;
import com.danasea.backend.modules.service.application.dtos.ServiceDetailResult;
import com.danasea.backend.modules.service.application.usecases.GetPublicServiceDetailUseCase;
import com.danasea.backend.shared.i18n.LocalizedMessageService;
import com.danasea.backend.shared.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Assistant Ownership HTTP Regression Tests (Account A/B)")
class AssistantOwnershipHttpRegressionTest {

    @Mock
    private JpaAiConversationRepository conversationRepository;

    @Mock
    private JpaAiMessageRepository messageRepository;

    @Mock
    private ConfirmationCardStorePort cardStorePort;

    @Mock
    private GetPublicServiceDetailUseCase getServiceDetailUseCase;

    @Mock
    private ChatUseCase chatUseCase;

    @Mock
    private AssistantAuditLogService auditLogService;

    @Mock
    private RateLimiterPort rateLimiter;

    private MockMvc mockMvc;
    private ChatHistoryService chatHistoryService;
    private ConfirmBookingUseCase confirmBookingUseCase;

    private final UUID userA = UUID.randomUUID();
    private final UUID userB = UUID.randomUUID();

    private final UUID convAId = UUID.randomUUID();
    private final UUID convBId = UUID.randomUUID();

    private AiConversationJpaEntity convA;
    private AiConversationJpaEntity convB;

    @BeforeEach
    void setUp() {
        chatHistoryService = new ChatHistoryService(conversationRepository, messageRepository);
        confirmBookingUseCase = new ConfirmBookingUseCase(cardStorePort, getServiceDetailUseCase);

        AssistantController controller = new AssistantController(
                chatHistoryService,
                chatUseCase,
                confirmBookingUseCase,
                auditLogService,
                conversationRepository,
                rateLimiter,
                LocalizedMessageService.standalone()
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(LocalizedMessageService.standalone()))
                .build();

        convA = new AiConversationJpaEntity();
        convA.setId(convAId);
        convA.setUserId(userA);
        convA.setLocale("vi");
        convA.setStartedAt(OffsetDateTime.now());

        convB = new AiConversationJpaEntity();
        convB.setId(convBId);
        convB.setUserId(userB);
        convB.setLocale("vi");
        convB.setStartedAt(OffsetDateTime.now());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UUID userId) {
        var auth = new UsernamePasswordAuthenticationToken(
                userId.toString(),
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    // =========================================================================
    // 1. GET /api/assistant/conversations/{id}
    // =========================================================================

    @Test
    @DisplayName("GET /conversations/{id}: Account A reads own conversation -> 200 OK")
    void getConversation_OwnerA_Returns200() throws Exception {
        authenticateAs(userA);
        when(conversationRepository.findById(convAId)).thenReturn(Optional.of(convA));

        mockMvc.perform(get("/api/assistant/conversations/{id}", convAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(convAId.toString()))
                .andExpect(jsonPath("$.userId").value(userA.toString()))
                .andExpect(jsonPath("$.locale").value("vi"));
    }

    @Test
    @DisplayName("GET /conversations/{id}: Account B attempts to read Account A's conversation -> 403 Forbidden")
    void getConversation_AttackerB_Returns403() throws Exception {
        authenticateAs(userB);
        when(conversationRepository.findById(convAId)).thenReturn(Optional.of(convA));

        mockMvc.perform(get("/api/assistant/conversations/{id}", convAId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("GET /conversations/{id}: Non-existent conversation -> 404 Not Found")
    void getConversation_NonExistent_Returns404() throws Exception {
        authenticateAs(userA);
        UUID randomId = UUID.randomUUID();
        when(conversationRepository.findById(randomId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/assistant/conversations/{id}", randomId))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // 2. GET /api/assistant/conversations/{id}/history
    // =========================================================================

    @Test
    @DisplayName("GET /conversations/{id}/history: Account A reads own message history -> 200 OK")
    void getConversationHistory_OwnerA_Returns200() throws Exception {
        authenticateAs(userA);
        when(conversationRepository.findById(convAId)).thenReturn(Optional.of(convA));

        AiMessageJpaEntity msg = new AiMessageJpaEntity();
        msg.setId(UUID.randomUUID());
        msg.setConversationId(convAId);
        msg.setRole(AiMessageRole.USER);
        msg.setContent("Tôi muốn đặt tour Sơn Trà");

        when(messageRepository.findByConversationIdOrderByCreatedAtDesc(eq(convAId), any(PageRequest.class)))
                .thenReturn(List.of(msg));

        mockMvc.perform(get("/api/assistant/conversations/{id}/history", convAId).param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].conversationId").value(convAId.toString()))
                .andExpect(jsonPath("$[0].content").value("Tôi muốn đặt tour Sơn Trà"));
    }

    @Test
    @DisplayName("GET /conversations/{id}/history: Account B attempts to read Account A's message history -> 403 Forbidden")
    void getConversationHistory_AttackerB_Returns403() throws Exception {
        authenticateAs(userB);
        when(conversationRepository.findById(convAId)).thenReturn(Optional.of(convA));

        mockMvc.perform(get("/api/assistant/conversations/{id}/history", convAId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(messageRepository, never()).findByConversationIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    @DisplayName("GET /conversations/{id}/history: Non-existent conversation -> 404 Not Found")
    void getConversationHistory_NonExistent_Returns404() throws Exception {
        authenticateAs(userA);
        UUID randomId = UUID.randomUUID();
        when(conversationRepository.findById(randomId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/assistant/conversations/{id}/history", randomId))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // 3. POST /api/assistant/conversations/{id}/confirm
    // =========================================================================

    @Test
    @DisplayName("POST /conversations/{id}/confirm: Account A confirms own booking card in own conversation -> 200 OK")
    void confirmBooking_OwnerA_Returns200() throws Exception {
        authenticateAs(userA);
        when(conversationRepository.findById(convAId)).thenReturn(Optional.of(convA));

        String cardId = "card-user-a-123";
        UUID serviceId = UUID.randomUUID();
        ConfirmationCard card = ConfirmationCard.builder()
                .id(cardId)
                .conversationId(convAId)
                .serviceId(serviceId)
                .price(new BigDecimal("500000.00"))
                .date("2026-10-10T09:00:00")
                .quantity(1)
                .status(ConfirmationCard.STATUS_PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        when(cardStorePort.findById(cardId)).thenReturn(Optional.of(card));

        ServiceDetailResult serviceDetail = ServiceDetailResult.builder()
                .price(new BigDecimal("500000.00"))
                .availableSlots(List.of("2026-10-10T09:00:00"))
                .build();
        when(getServiceDetailUseCase.execute(eq(serviceId), eq(userA), anyString()))
                .thenReturn(serviceDetail);

        String jsonPayload = """
                {
                    "cardId": "%s"
                }
                """.formatted(cardId);

        mockMvc.perform(post("/api/assistant/conversations/{id}/confirm", convAId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.cardId").value(cardId));

        verify(auditLogService).logToolCall(eq(convAId), eq(userA), eq("confirm_booking"), anyString(), anyString());
    }

    @Test
    @DisplayName("POST /conversations/{id}/confirm: Account B attempts to confirm Account A's conversation -> 403 Forbidden")
    void confirmBooking_AttackerB_WrongConversation_Returns403() throws Exception {
        authenticateAs(userB);
        when(conversationRepository.findById(convAId)).thenReturn(Optional.of(convA));

        String jsonPayload = """
                {
                    "cardId": "card-user-a-123"
                }
                """;

        mockMvc.perform(post("/api/assistant/conversations/{id}/confirm", convAId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(cardStorePort, never()).findById(any());
        verify(getServiceDetailUseCase, never()).execute(any(), any(), any());
    }

    @Test
    @DisplayName("POST /conversations/{id}/confirm: Account B uses Card belonging to Conv A in Conv B -> 403 Forbidden (Card mismatch)")
    void confirmBooking_CrossConversationCardHijacking_Returns403() throws Exception {
        authenticateAs(userB);
        when(conversationRepository.findById(convBId)).thenReturn(Optional.of(convB));

        String cardAId = "card-from-conv-a";
        ConfirmationCard cardFromConvA = ConfirmationCard.builder()
                .id(cardAId)
                .conversationId(convAId) // thuộc conversation A!
                .serviceId(UUID.randomUUID())
                .price(new BigDecimal("500000.00"))
                .date("2026-10-10T09:00:00")
                .quantity(1)
                .status(ConfirmationCard.STATUS_PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        when(cardStorePort.findById(cardAId)).thenReturn(Optional.of(cardFromConvA));

        String jsonPayload = """
                {
                    "cardId": "%s"
                }
                """.formatted(cardAId);

        mockMvc.perform(post("/api/assistant/conversations/{id}/confirm", convBId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // Không bao giờ gọi re-validation hay hold vì đã bị chặn do mismatch conversation
        verify(getServiceDetailUseCase, never()).execute(any(), any(), any());
    }

    @Test
    @DisplayName("POST /conversations/{id}/confirm: Non-existent conversation -> 404 Not Found")
    void confirmBooking_NonExistentConversation_Returns404() throws Exception {
        authenticateAs(userA);
        UUID randomId = UUID.randomUUID();
        when(conversationRepository.findById(randomId)).thenReturn(Optional.empty());

        String jsonPayload = """
                {
                    "cardId": "any-card"
                }
                """;

        mockMvc.perform(post("/api/assistant/conversations/{id}/confirm", randomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isNotFound());

        verify(cardStorePort, never()).findById(any());
    }
    @Test
    void malformedTypedConversationIdReturns400BeforeChatOrRateLimit() throws Exception {
        mockMvc.perform(post("/api/assistant/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\":\"invalid-uuid\",\"message\":\"Hello\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        verifyNoInteractions(chatUseCase, rateLimiter);
    }

}
