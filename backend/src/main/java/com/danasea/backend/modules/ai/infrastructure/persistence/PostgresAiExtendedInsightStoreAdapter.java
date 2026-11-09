package com.danasea.backend.modules.ai.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort;
import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiCopilotConversationJpaEntity;
import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiCopilotMessageJpaEntity;
import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiDisputeSummaryJpaEntity;
import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiMarineBriefingJpaEntity;
import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiVendorInsightJpaEntity;
import com.danasea.backend.modules.ai.infrastructure.persistence.repositories.JpaAiCopilotConversationRepository;
import com.danasea.backend.modules.ai.infrastructure.persistence.repositories.JpaAiCopilotMessageRepository;
import com.danasea.backend.modules.ai.infrastructure.persistence.repositories.JpaAiDisputeSummaryRepository;
import com.danasea.backend.modules.ai.infrastructure.persistence.repositories.JpaAiMarineBriefingRepository;
import com.danasea.backend.modules.ai.infrastructure.persistence.repositories.JpaAiVendorInsightRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PostgresAiExtendedInsightStoreAdapter implements AiExtendedInsightStorePort {

    private final JpaAiCopilotConversationRepository conversationRepo;
    private final JpaAiCopilotMessageRepository messageRepo;
    private final JpaAiVendorInsightRepository vendorInsightRepo;
    private final JpaAiDisputeSummaryRepository disputeSummaryRepo;
    private final JpaAiMarineBriefingRepository marineBriefingRepo;

    @Override
    @Transactional
    public CopilotConversation createCopilotConversation(
            UUID actorUserId, String actorRole, UUID vendorId, String language,
            String title, String contextType, UUID contextId) {
        AiCopilotConversationJpaEntity entity = new AiCopilotConversationJpaEntity();
        entity.setActorUserId(actorUserId);
        entity.setActorRole(actorRole);
        entity.setVendorId(vendorId);
        entity.setLanguage(language != null ? language : "vi");
        entity.setTitle(title);
        entity.setContextType(contextType);
        entity.setContextId(contextId);
        entity.setPinned(false);
        entity.setArchived(false);
        entity.setDeleted(false);
        var saved = conversationRepo.save(entity);
        return toRecord(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CopilotConversation> getCopilotConversation(UUID id) {
        return conversationRepo.findById(id)
                .filter(c -> !c.isDeleted())
                .map(this::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CopilotConversation> listCopilotConversations(
            UUID actorUserId, String actorRole, Boolean archived, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        var pageable = PageRequest.of(0, boundedLimit);
        List<AiCopilotConversationJpaEntity> list = archived == null
                ? conversationRepo.findByActor(actorUserId, actorRole, pageable)
                : conversationRepo.findByActorAndArchived(actorUserId, actorRole, archived, pageable);
        return list.stream().map(this::toRecord).toList();
    }

    @Override
    @Transactional
    public CopilotConversation updateCopilotConversationMeta(
            UUID id, UUID actorUserId, String title, Boolean pinned, Boolean archived) {
        var conv = conversationRepo.findById(id)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        if (!conv.getActorUserId().equals(actorUserId)) {
            throw new AccessDeniedException("Conversation does not belong to the user");
        }
        if (title != null && !title.isBlank()) {
            conv.setTitle(title.trim().length() > 200 ? title.trim().substring(0, 200) : title.trim());
        }
        if (pinned != null) {
            conv.setPinned(pinned);
        }
        if (archived != null) {
            conv.setArchived(archived);
        }
        return toRecord(conversationRepo.save(conv));
    }

    @Override
    @Transactional
    public void softDeleteCopilotConversation(UUID id, UUID actorUserId) {
        conversationRepo.findById(id).ifPresent(c -> {
            if (!c.getActorUserId().equals(actorUserId)) {
                throw new AccessDeniedException("Conversation does not belong to the user");
            }
            c.setDeleted(true);
            c.setDeletedAt(OffsetDateTime.now());
            conversationRepo.save(c);
        });
    }

    @Override
    @Transactional
    public void autoGenerateCopilotTitleIfEmpty(UUID id, String firstMessage) {
        if (id == null || firstMessage == null || firstMessage.isBlank()) return;
        conversationRepo.findById(id).ifPresent(conv -> {
            if (conv.getTitle() == null || conv.getTitle().isBlank()) {
                String clean = firstMessage.replaceAll("[\\r\\n]+", " ").trim();
                String title = clean.length() > 60 ? clean.substring(0, 57) + "..." : clean;
                conv.setTitle(title);
                conversationRepo.save(conv);
            }
        });
    }

    @Override
    @Transactional
    public CopilotMessage appendCopilotMessage(
            UUID conversationId, String role, String content, String artifactsJson, String toolsUsed, boolean verified) {
        AiCopilotMessageJpaEntity entity = new AiCopilotMessageJpaEntity();
        entity.setConversationId(conversationId);
        entity.setRole(role);
        entity.setContent(content);
        entity.setArtifactsJson(artifactsJson);
        entity.setToolsUsed(toolsUsed);
        entity.setVerified(verified);
        var saved = messageRepo.save(entity);
        return toRecord(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CopilotMessage> listCopilotMessages(UUID conversationId) {
        return messageRepo.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream().map(this::toRecord).toList();
    }

    @Override
    @Transactional
    public VendorInsight saveVendorInsight(
            UUID vendorId, UUID serviceId, String insightType, String sourceFingerprint, String payloadJson, String analysisMethod) {
        AiVendorInsightJpaEntity entity = new AiVendorInsightJpaEntity();
        entity.setVendorId(vendorId);
        entity.setServiceId(serviceId);
        entity.setInsightType(insightType);
        entity.setSourceFingerprint(sourceFingerprint);
        entity.setPayloadJson(payloadJson);
        entity.setAnalysisMethod(analysisMethod != null ? analysisMethod : "HYBRID_DUAL_BRAIN");
        var saved = vendorInsightRepo.save(entity);
        return toRecord(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorInsight> listVendorInsights(UUID vendorId, String insightType, int limit) {
        int bounded = Math.max(1, Math.min(limit, 50));
        return vendorInsightRepo.findByVendorIdAndInsightTypeOrderByCreatedAtDesc(
                vendorId, insightType, PageRequest.of(0, bounded))
                .stream().map(this::toRecord).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VendorInsight> getLatestVendorInsight(UUID vendorId, UUID serviceId, String insightType) {
        if (serviceId != null) {
            return vendorInsightRepo.findFirstByVendorIdAndServiceIdAndInsightTypeOrderByCreatedAtDesc(
                    vendorId, serviceId, insightType).map(this::toRecord);
        }
        var list = vendorInsightRepo.findByVendorIdAndInsightTypeOrderByCreatedAtDesc(
                vendorId, insightType, PageRequest.of(0, 1));
        return list.isEmpty() ? Optional.empty() : Optional.of(toRecord(list.get(0)));
    }

    @Override
    @Transactional
    public DisputeSummary saveDisputeSummary(
            UUID disputeId, UUID subOrderId, UUID requestedByAdminId, String recommendedResolution,
            Integer recommendedRefundPercent, String summaryJson, String analysisMethod) {
        var existing = disputeSummaryRepo.findByDisputeId(disputeId);
        AiDisputeSummaryJpaEntity entity = existing.orElseGet(AiDisputeSummaryJpaEntity::new);
        entity.setDisputeId(disputeId);
        entity.setSubOrderId(subOrderId);
        entity.setRequestedByAdminId(requestedByAdminId);
        entity.setRecommendedResolution(recommendedResolution);
        entity.setRecommendedRefundPercent(recommendedRefundPercent);
        entity.setSummaryJson(summaryJson);
        entity.setAnalysisMethod(analysisMethod != null ? analysisMethod : "HYBRID_DUAL_BRAIN");
        var saved = disputeSummaryRepo.save(entity);
        return toRecord(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DisputeSummary> getDisputeSummary(UUID disputeId) {
        return disputeSummaryRepo.findByDisputeId(disputeId).map(this::toRecord);
    }

    @Override
    @Transactional
    public MarineBriefing saveMarineBriefing(
            UUID masterOrderId, UUID subOrderId, UUID customerId, UUID slotId,
            String weatherStatus, String briefingJson) {
        var existing = marineBriefingRepo.findBySubOrderIdAndCustomerId(subOrderId, customerId);
        AiMarineBriefingJpaEntity entity = existing.orElseGet(AiMarineBriefingJpaEntity::new);
        entity.setMasterOrderId(masterOrderId);
        entity.setSubOrderId(subOrderId);
        entity.setCustomerId(customerId);
        entity.setSlotId(slotId);
        entity.setWeatherStatus(weatherStatus != null ? weatherStatus : "UNKNOWN");
        entity.setBriefingJson(briefingJson);
        var saved = marineBriefingRepo.save(entity);
        return toRecord(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MarineBriefing> getMarineBriefing(UUID subOrderId, UUID customerId) {
        return marineBriefingRepo.findBySubOrderIdAndCustomerId(subOrderId, customerId).map(this::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MarineBriefing> listMarineBriefingsByMasterOrder(UUID masterOrderId) {
        return marineBriefingRepo.findByMasterOrderId(masterOrderId).stream().map(this::toRecord).toList();
    }

    @Override
    @Transactional
    public void markBriefingNotified(UUID briefingId, OffsetDateTime notifiedAt) {
        marineBriefingRepo.findById(briefingId).ifPresent(b -> {
            b.setNotifiedAt(notifiedAt != null ? notifiedAt : OffsetDateTime.now());
            marineBriefingRepo.save(b);
        });
    }

    private CopilotConversation toRecord(AiCopilotConversationJpaEntity e) {
        return new CopilotConversation(
                e.getId(), e.getActorUserId(), e.getActorRole(), e.getVendorId(),
                e.getLanguage(), e.getTitle(), e.isPinned(), e.isArchived(),
                e.getContextType(), e.getContextId(), e.getVersion(),
                e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    private CopilotMessage toRecord(AiCopilotMessageJpaEntity e) {
        return new CopilotMessage(
                e.getId(), e.getConversationId(), e.getRole(), e.getContent(),
                e.getArtifactsJson(), e.getToolsUsed(), e.isVerified(), e.getCreatedAt()
        );
    }

    private VendorInsight toRecord(AiVendorInsightJpaEntity e) {
        return new VendorInsight(
                e.getId(), e.getVendorId(), e.getServiceId(), e.getInsightType(),
                e.getSourceFingerprint(), e.getPayloadJson(), e.getAnalysisMethod(), e.getCreatedAt()
        );
    }

    private DisputeSummary toRecord(AiDisputeSummaryJpaEntity e) {
        return new DisputeSummary(
                e.getId(), e.getDisputeId(), e.getSubOrderId(), e.getRequestedByAdminId(),
                e.getRecommendedResolution(), e.getRecommendedRefundPercent(),
                e.getSummaryJson(), e.getAnalysisMethod(), e.getCreatedAt()
        );
    }

    private MarineBriefing toRecord(AiMarineBriefingJpaEntity e) {
        return new MarineBriefing(
                e.getId(), e.getMasterOrderId(), e.getSubOrderId(), e.getCustomerId(),
                e.getSlotId(), e.getWeatherStatus(), e.getBriefingJson(),
                e.getNotifiedAt(), e.getCreatedAt()
        );
    }
}
