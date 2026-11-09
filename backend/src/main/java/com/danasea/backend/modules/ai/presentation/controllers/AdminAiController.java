package com.danasea.backend.modules.ai.presentation.controllers;

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

import com.danasea.backend.modules.ai.application.ports.AdminAiDataPort;
import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort;
import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort.CopilotConversation;
import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort.CopilotMessage;
import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort.DisputeSummary;
import com.danasea.backend.modules.ai.application.ports.AssessmentCaseStorePort;
import com.danasea.backend.modules.ai.application.usecases.AdminDisputeSummaryUseCase;
import com.danasea.backend.modules.ai.application.usecases.AdminDisputeSummaryUseCase.DisputeArbitrationResult;
import com.danasea.backend.modules.ai.application.usecases.AnalyzeTransactionRiskUseCase;
import com.danasea.backend.modules.ai.application.usecases.RoleCopilotUseCase;
import com.danasea.backend.modules.ai.application.usecases.RoleCopilotUseCase.CopilotChatResponse;
import com.danasea.backend.modules.ai.domain.models.AssessmentCase;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import com.danasea.backend.shared.i18n.SupportedLanguage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/admin/ai")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAiController {

    public record RiskRequest(UUID orderId, String complaint) {}
    public record ResolutionRequest(String resolution, String note) {}
    public record CopilotChatRequestDto(UUID conversationId, String message, String contextType, UUID contextId) {}
    public record UpdateConversationMetaDto(String title, Boolean pinned, Boolean archived) {}

    private final AnalyzeTransactionRiskUseCase risks;
    private final AssessmentCaseStorePort cases;
    private final AdminDisputeSummaryUseCase disputeSummaryUseCase;
    private final AdminAiDataPort adminAiData;
    private final RoleCopilotUseCase roleCopilotUseCase;
    private final AiExtendedInsightStorePort insightStore;

    @PostMapping("/risk-cases")
    public AssessmentCase risk(@RequestBody RiskRequest request) {
        return risks.execute(resolveUserId(), request.orderId(), request.complaint());
    }

    @GetMapping("/assessment-cases")
    public List<AssessmentCase> cases(
            @RequestParam(defaultValue = "NEEDS_REVIEW") String status,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return cases.list(status, limit);
    }

    @PostMapping("/assessment-cases/{id}/resolve")
    public AssessmentCase resolve(@PathVariable UUID id, @RequestBody ResolutionRequest request) {
        return cases.resolve(id, resolveUserId(), request.resolution(), request.note());
    }

    // ==================== DISPUTE AI ARBITRATION ====================

    @PostMapping("/disputes/{disputeId}/summarize")
    public DisputeArbitrationResult summarizeDispute(@PathVariable UUID disputeId) {
        UUID adminId = resolveUserId();
        return disputeSummaryUseCase.execute(adminId, disputeId, currentLanguage());
    }

    @GetMapping("/disputes/{disputeId}/summary")
    public ResponseEntity<DisputeSummary> getDisputeSummary(@PathVariable UUID disputeId) {
        return insightStore.getDisputeSummary(disputeId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== EXECUTIVE DIGEST ====================

    @GetMapping("/executive-digest")
    public AdminAiDataPort.PlatformDashboardMetrics getExecutiveDigest(
            @RequestParam(defaultValue = "THIS_MONTH", required = false) String period
    ) {
        return adminAiData.getPlatformDashboardMetrics();
    }

    @PostMapping("/executive-digest")
    public AdminAiDataPort.PlatformDashboardMetrics postExecutiveDigest(
            @RequestParam(defaultValue = "THIS_MONTH", required = false) String period
    ) {
        return adminAiData.getPlatformDashboardMetrics();
    }

    // ==================== ADMIN COPILOT CONVERSATION / THREAD LIFECYCLE ====================

    @PostMapping("/copilot/chat")
    public CopilotChatResponse chatWithCopilot(@RequestBody CopilotChatRequestDto request) {
        UUID adminId = resolveUserId();
        SupportedLanguage lang = currentLanguage();
        String msg = request != null ? request.message() : "";
        UUID convId = request != null ? request.conversationId() : null;
        String ctxType = request != null ? request.contextType() : null;
        UUID ctxId = request != null ? request.contextId() : null;

        return roleCopilotUseCase.processMessage(adminId, "ADMIN", null, convId, msg, ctxType, ctxId, lang);
    }

    @GetMapping("/copilot/conversations")
    public List<CopilotConversation> listConversations(
            @RequestParam(required = false) Boolean archived,
            @RequestParam(defaultValue = "20") int limit
    ) {
        UUID adminId = resolveUserId();
        return roleCopilotUseCase.listConversations(adminId, "ADMIN", archived, limit);
    }

    @GetMapping("/copilot/conversations/{id}/history")
    public List<CopilotMessage> getConversationHistory(@PathVariable UUID id) {
        UUID adminId = resolveUserId();
        return roleCopilotUseCase.getConversationHistory(id, adminId);
    }

    @PatchMapping("/copilot/conversations/{id}")
    public CopilotConversation updateConversationMeta(
            @PathVariable UUID id,
            @RequestBody UpdateConversationMetaDto dto
    ) {
        UUID adminId = resolveUserId();
        String title = dto != null ? dto.title() : null;
        Boolean pinned = dto != null ? dto.pinned() : null;
        Boolean archived = dto != null ? dto.archived() : null;
        return roleCopilotUseCase.updateConversationMeta(id, adminId, title, pinned, archived);
    }

    @DeleteMapping("/copilot/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable UUID id) {
        UUID adminId = resolveUserId();
        roleCopilotUseCase.deleteConversation(id, adminId);
        return ResponseEntity.noContent().build();
    }

    // ==================== HELPERS ====================

    private UUID resolveUserId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("Admin authentication required"));
    }

    private SupportedLanguage currentLanguage() {
        return SupportedLanguage.fromTag(LocaleContextHolder.getLocale().toLanguageTag())
                .orElse(SupportedLanguage.VI);
    }
}
