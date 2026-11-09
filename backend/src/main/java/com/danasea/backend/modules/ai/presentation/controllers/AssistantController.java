package com.danasea.backend.modules.ai.presentation.controllers;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.danasea.backend.modules.ai.application.ports.RateLimiterPort;
import com.danasea.backend.modules.ai.application.usecases.ChatUseCase;
import com.danasea.backend.modules.ai.application.usecases.ChatIdempotencyService;
import com.danasea.backend.modules.ai.application.usecases.ConfirmBookingUseCase;
import com.danasea.backend.modules.ai.domain.TrustTier;
import com.danasea.backend.modules.ai.domain.exceptions.AiConversationLocaleMismatchException;
import com.danasea.backend.modules.ai.domain.services.AssistantAuditLogService;
import com.danasea.backend.modules.ai.domain.services.ChatHistoryService;
import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiConversationJpaEntity;
import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiMessageJpaEntity;
import com.danasea.backend.modules.ai.infrastructure.persistence.repositories.JpaAiConversationRepository;
import com.danasea.backend.modules.ai.presentation.dtos.ChatRequest;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import com.danasea.backend.shared.i18n.LocalizedMessageService;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.danasea.backend.shared.presentation.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private static final String DEFAULT_CLIENT_IP = "127.0.0.1";
    private static final String DEFAULT_SESSION_PREFIX = "session-";

    private final ChatHistoryService chatHistoryService;
    private final ChatUseCase chatUseCase;
    private final ConfirmBookingUseCase confirmBookingUseCase;
    private final AssistantAuditLogService auditLogService;
    private final JpaAiConversationRepository conversationRepository;
    private final RateLimiterPort rateLimiter;
    private final LocalizedMessageService messages;
    private ChatIdempotencyService chatIdempotency;

    @Autowired
    public void setChatIdempotencyService(ChatIdempotencyService service) { this.chatIdempotency = service; }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@Valid @RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        Optional<UUID> currentUserIdOpt = SecurityUtils.getCurrentUserId();
        UUID userId = currentUserIdOpt.orElse(null);
        String userIdStr = userId != null ? userId.toString() : null;
        TrustTier tier = userId != null ? TrustTier.VERIFIED : TrustTier.UNVERIFIED;
        SupportedLanguage language = SupportedLanguage
                .fromTag(LocaleContextHolder.getLocale().toLanguageTag())
                .orElse(SupportedLanguage.VI);

        // Enforce rate limiting prior to calling chatUseCase
        if (!rateLimiter.isAllowed(userIdStr, clientIp, tier)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ErrorResponse("RATE_LIMIT_EXCEEDED", messages.get("ai.rate_limit", language)));
        }

        String key = httpRequest == null ? null : httpRequest.getHeader("Idempotency-Key");
        ChatIdempotencyService.Claim claim = key == null || chatIdempotency == null ? null
                : chatIdempotency.begin(userId, key, request.conversationId(), request.message(), language);
        if (claim != null && claim.replay()) return ResponseEntity.ok(claim.response());
        try {
            AiConversationJpaEntity conversation = chatHistoryService
                    .getOrCreateConversation(request.conversationId(), userId, language);
            SupportedLanguage conversationLanguage = SupportedLanguage
                    .fromTag(conversation.getLocale()).orElse(SupportedLanguage.VI);
            if (conversationLanguage != language) throw new AiConversationLocaleMismatchException();
            UUID conversationId = conversation.getId();
            chatHistoryService.autoGenerateTitleIfEmpty(conversationId, request.message());
            if (chatIdempotency != null) chatIdempotency.bind(claim, conversationId);
            var response = chatUseCase.processMessage(conversationId, request.message(), conversationLanguage, userId);
            auditLogService.logChatAction(conversationId, userId, request.message(), response.getContent(), response.getKeyMasked());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "success"); body.put("conversationId", conversationId.toString());
            body.put("message", response.getContent() == null ? "" : response.getContent());
            body.put("responseStatus", response.getResponseStatus()); body.put("cards", response.getCards());
            body.put("sources", response.getCards().stream().flatMap(card -> card.sources().stream()).toList());
            body.put("actions", response.getCards().stream().flatMap(card -> card.actions().stream()).toList());
            body.put("requiredInputs", response.getRequiredInputs()); body.put("context", response.getConversationContext());
            body.put("generatedTextVerified", false);
            if (chatIdempotency != null) chatIdempotency.complete(claim, body);
            return ResponseEntity.ok(body);
        } catch (RuntimeException exception) {
            if (chatIdempotency != null) chatIdempotency.fail(claim);
            throw exception;
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return DEFAULT_CLIENT_IP;
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : DEFAULT_CLIENT_IP;
    }

    @PostMapping("/conversations/{id}/confirm")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> confirmBooking(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> request) {
        String cardId = (String) request.get("cardId");

        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new IllegalStateException("User not authenticated"));
        var conversationOpt = chatHistoryService.getConversationForUser(id, userId);
        if (conversationOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String sessionId = request.getOrDefault("sessionId", DEFAULT_SESSION_PREFIX + userId).toString();

        Map<String, Object> result = confirmBookingUseCase.execute(cardId, userId, sessionId, id);
        
        // Log confirmation action as tool call/chat action
        auditLogService.logToolCall(id, userId, "confirm_booking",
                "cardId=" + cardId,
                result.toString());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/conversations/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AiConversationJpaEntity> getConversation(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new IllegalStateException("User not authenticated"));
        return chatHistoryService.getConversationForUser(id, userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/conversations/{id}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AiMessageJpaEntity>> getConversationHistory(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "20") int limit) {
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new IllegalStateException("User not authenticated"));
        return chatHistoryService.getRecentMessagesForUser(id, userId, limit)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    public record UpdateConversationMetaRequest(String title, Boolean pinned, Boolean archived) {}

    @GetMapping("/conversations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AiConversationJpaEntity>> listConversations(
            @RequestParam(required = false) Boolean archived,
            @RequestParam(defaultValue = "30") int limit) {
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new IllegalStateException("User not authenticated"));
        return ResponseEntity.ok(chatHistoryService.listConversationsForUser(userId, archived, limit));
    }

    @PatchMapping("/conversations/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AiConversationJpaEntity> updateConversationMeta(
            @PathVariable UUID id,
            @RequestBody UpdateConversationMetaRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new IllegalStateException("User not authenticated"));
        String title = request != null ? request.title() : null;
        Boolean pinned = request != null ? request.pinned() : null;
        Boolean archived = request != null ? request.archived() : null;
        return ResponseEntity.ok(chatHistoryService.updateConversationMeta(id, userId, title, pinned, archived));
    }

    @DeleteMapping("/conversations/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteConversation(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new IllegalStateException("User not authenticated"));
        chatHistoryService.softDeleteConversation(id, userId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> invalidChatBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_INPUT", messages.get("error.invalid_input")));
    }

}
