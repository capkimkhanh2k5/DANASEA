package com.danasea.backend.modules.ai.application.ports;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiExtendedInsightStorePort {

    record CopilotConversation(
            UUID id,
            UUID actorUserId,
            String actorRole,
            UUID vendorId,
            String language,
            String title,
            boolean pinned,
            boolean archived,
            String contextType,
            UUID contextId,
            long version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    record CopilotMessage(
            UUID id,
            UUID conversationId,
            String role,
            String content,
            String artifactsJson,
            String toolsUsed,
            boolean verified,
            OffsetDateTime createdAt
    ) {}

    record VendorInsight(
            UUID id,
            UUID vendorId,
            UUID serviceId,
            String insightType,
            String sourceFingerprint,
            String payloadJson,
            String analysisMethod,
            OffsetDateTime createdAt
    ) {}

    record DisputeSummary(
            UUID id,
            UUID disputeId,
            UUID subOrderId,
            UUID requestedByAdminId,
            String recommendedResolution,
            Integer recommendedRefundPercent,
            String summaryJson,
            String analysisMethod,
            OffsetDateTime createdAt
    ) {}

    record MarineBriefing(
            UUID id,
            UUID masterOrderId,
            UUID subOrderId,
            UUID customerId,
            UUID slotId,
            String weatherStatus,
            String briefingJson,
            OffsetDateTime notifiedAt,
            OffsetDateTime createdAt
    ) {}

    // Copilot Conversation methods
    CopilotConversation createCopilotConversation(
            UUID actorUserId, String actorRole, UUID vendorId, String language,
            String title, String contextType, UUID contextId);

    Optional<CopilotConversation> getCopilotConversation(UUID id);

    List<CopilotConversation> listCopilotConversations(
            UUID actorUserId, String actorRole, UUID vendorId, Boolean archived, int limit);

    CopilotConversation updateCopilotConversationMeta(
            UUID id, UUID actorUserId, String title, Boolean pinned, Boolean archived);

    void softDeleteCopilotConversation(UUID id, UUID actorUserId);

    void autoGenerateCopilotTitleIfEmpty(UUID id, String firstMessage);

    // Copilot Message methods
    CopilotMessage appendCopilotMessage(
            UUID conversationId, String role, String content, String artifactsJson, String toolsUsed, boolean verified);

    List<CopilotMessage> listCopilotMessages(UUID conversationId);

    // Vendor Insight methods
    VendorInsight saveVendorInsight(
            UUID vendorId, UUID serviceId, String insightType, String sourceFingerprint, String payloadJson, String analysisMethod);

    List<VendorInsight> listVendorInsights(UUID vendorId, String insightType, int limit);

    Optional<VendorInsight> getLatestVendorInsight(UUID vendorId, UUID serviceId, String insightType);

    // Dispute Summary methods
    DisputeSummary saveDisputeSummary(
            UUID disputeId, UUID subOrderId, UUID requestedByAdminId, String recommendedResolution,
            Integer recommendedRefundPercent, String summaryJson, String analysisMethod);

    Optional<DisputeSummary> getDisputeSummary(UUID disputeId);

    // Marine Briefing methods
    MarineBriefing saveMarineBriefing(
            UUID masterOrderId, UUID subOrderId, UUID customerId, UUID slotId,
            String weatherStatus, String briefingJson);

    Optional<MarineBriefing> getMarineBriefing(UUID subOrderId, UUID customerId);

    List<MarineBriefing> listMarineBriefingsByMasterOrder(UUID masterOrderId);

    void markBriefingNotified(UUID briefingId, OffsetDateTime notifiedAt);
}
