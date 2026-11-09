package com.danasea.backend.modules.ai.presentation.controllers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort;
import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort.CopilotConversation;
import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort.CopilotMessage;
import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort.VendorInsight;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort;
import com.danasea.backend.modules.ai.application.usecases.RoleCopilotUseCase;
import com.danasea.backend.modules.ai.application.usecases.RoleCopilotUseCase.CopilotChatResponse;
import com.danasea.backend.modules.ai.application.usecases.VendorListingOptimizerUseCase;
import com.danasea.backend.modules.ai.application.usecases.VendorListingOptimizerUseCase.OptimizeRequest;
import com.danasea.backend.modules.ai.application.usecases.VendorListingOptimizerUseCase.OptimizeResult;
import com.danasea.backend.modules.ai.application.usecases.VendorReviewCoachingUseCase;
import com.danasea.backend.modules.ai.application.usecases.VendorReviewCoachingUseCase.CoachingResult;
import com.danasea.backend.modules.ai.application.usecases.VendorReviewCoachingUseCase.ReplyDraftResult;
import com.danasea.backend.modules.ai.application.usecases.VendorSlotAdvisorUseCase;
import com.danasea.backend.modules.ai.application.usecases.VendorSlotAdvisorUseCase.SlotAdvisorResult;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import com.danasea.backend.shared.i18n.SupportedLanguage;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/vendor/ai")
@PreAuthorize("hasRole('VENDOR')")
@RequiredArgsConstructor
public class VendorAiController {

    public record ReviewCoachingRequestDto(UUID serviceId, Integer periodDays) {}
    public record ReplyDraftRequestDto(String tone, String customPoints) {}
    public record SlotAdvisorRequestDto(UUID serviceId, Integer horizonDays) {}
    public record CopilotChatRequestDto(UUID conversationId, String message, String contextType, UUID contextId) {}
    public record UpdateConversationMetaDto(String title, Boolean pinned, Boolean archived) {}

    private final VendorAiDataPort vendorDataPort;
    private final VendorReviewCoachingUseCase reviewCoachingUseCase;
    private final VendorListingOptimizerUseCase listingOptimizerUseCase;
    private final VendorSlotAdvisorUseCase slotAdvisorUseCase;
    private final RoleCopilotUseCase roleCopilotUseCase;
    private final AiExtendedInsightStorePort insightStore;

    @PostMapping("/review-coaching")
    public CoachingResult reviewCoaching(@RequestBody(required = false) ReviewCoachingRequestDto req) {
        UUID userId = resolveUserId();
        UUID serviceId = req != null ? req.serviceId() : null;
        int period = req != null && req.periodDays() != null ? req.periodDays() : 90;
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(period);
        return reviewCoachingUseCase.execute(userId, serviceId, from, to, currentLanguage());
    }

    @GetMapping("/review-coaching/history")
    public List<VendorInsight> reviewCoachingHistory(@RequestParam(defaultValue = "10") int limit) {
        UUID vendorId = resolveVendorId();
        return insightStore.listVendorInsights(vendorId, "REVIEW_COACHING", limit);
    }

    @PostMapping("/reviews/{reviewId}/reply-draft")
    public ReplyDraftResult draftReply(
            @PathVariable UUID reviewId,
            @RequestBody(required = false) ReplyDraftRequestDto request
    ) {
        UUID userId = resolveUserId();
        String tone = request != null ? request.tone() : "PROFESSIONAL";
        String customPoints = request != null ? request.customPoints() : null;
        return reviewCoachingUseCase.draftReply(userId, reviewId, tone, customPoints, currentLanguage());
    }

    @PostMapping("/listings/optimize")
    public OptimizeResult optimizeListing(@Valid @RequestBody OptimizeRequest request) {
        UUID userId = resolveUserId();
        return listingOptimizerUseCase.execute(userId, request, currentLanguage());
    }

    @PostMapping("/slots/advisor")
    public SlotAdvisorResult slotAdvisor(@RequestBody(required = false) SlotAdvisorRequestDto request) {
        UUID userId = resolveUserId();
        UUID serviceId = request != null ? request.serviceId() : null;
        int horizon = request != null && request.horizonDays() != null ? request.horizonDays() : 7;
        return slotAdvisorUseCase.execute(userId, serviceId, horizon, currentLanguage());
    }

    // ==================== COPILOT CONVERSATION / THREAD LIFECYCLE ====================

    @PostMapping("/copilot/chat")
    public CopilotChatResponse chatWithCopilot(@RequestBody CopilotChatRequestDto request) {
        UUID userId = resolveUserId();
        UUID vendorId = resolveVendorId();
        SupportedLanguage lang = currentLanguage();
        String msg = request != null ? request.message() : "";
        UUID convId = request != null ? request.conversationId() : null;
        String ctxType = request != null ? request.contextType() : null;
        UUID ctxId = request != null ? request.contextId() : null;

        return roleCopilotUseCase.processMessage(userId, "VENDOR", vendorId, convId, msg, ctxType, ctxId, lang);
    }

    @GetMapping("/copilot/conversations")
    public List<CopilotConversation> listConversations(
            @RequestParam(required = false) Boolean archived,
            @RequestParam(defaultValue = "20") int limit
    ) {
        UUID userId = resolveUserId();
        return roleCopilotUseCase.listConversations(userId, "VENDOR", resolveVendorId(), archived, limit);
    }

    @GetMapping("/copilot/conversations/{id}/history")
    public List<CopilotMessage> getConversationHistory(@PathVariable UUID id) {
        UUID userId = resolveUserId();
        return roleCopilotUseCase.getConversationHistory(id, userId, "VENDOR", resolveVendorId());
    }

    @PatchMapping("/copilot/conversations/{id}")
    public CopilotConversation updateConversationMeta(
            @PathVariable UUID id,
            @RequestBody UpdateConversationMetaDto dto
    ) {
        UUID userId = resolveUserId();
        String title = dto != null ? dto.title() : null;
        Boolean pinned = dto != null ? dto.pinned() : null;
        Boolean archived = dto != null ? dto.archived() : null;
        return roleCopilotUseCase.updateConversationMeta(id, userId, "VENDOR", resolveVendorId(), title, pinned, archived);
    }

    @DeleteMapping("/copilot/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable UUID id) {
        UUID userId = resolveUserId();
        roleCopilotUseCase.deleteConversation(id, userId, "VENDOR", resolveVendorId());
        return ResponseEntity.noContent().build();
    }

    // ==================== HELPERS ====================

    private UUID resolveUserId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User not authenticated"));
    }

    private UUID resolveVendorId() {
        UUID userId = resolveUserId();
        return vendorDataPort.findProfileByUserId(userId)
                .map(VendorAiDataPort.VendorProfile::id)
                .orElseThrow(() -> new AccessDeniedException("Current user is not linked to any active vendor account"));
    }

    private SupportedLanguage currentLanguage() {
        return SupportedLanguage.fromTag(LocaleContextHolder.getLocale().toLanguageTag())
                .orElse(SupportedLanguage.VI);
    }
}
