package com.danasea.backend.modules.ai.domain.services;

import com.danasea.backend.modules.ai.domain.models.AiMessageRole;
import com.danasea.backend.modules.ai.domain.exceptions.AiStateConflictException;
import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiConversationJpaEntity;
import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiMessageJpaEntity;
import com.danasea.backend.modules.ai.infrastructure.persistence.repositories.JpaAiConversationRepository;
import com.danasea.backend.modules.ai.infrastructure.persistence.repositories.JpaAiMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.danasea.backend.shared.i18n.SupportedLanguage;

@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private final JpaAiConversationRepository conversationRepository;
    private final JpaAiMessageRepository messageRepository;

    @Transactional
    public AiConversationJpaEntity getOrCreateConversation(UUID conversationId, UUID userId) {
        return getOrCreateConversation(conversationId, userId, SupportedLanguage.VI);
    }

    @Transactional
    public AiConversationJpaEntity getOrCreateConversation(
            UUID conversationId, UUID userId, SupportedLanguage language) {
        if (conversationId != null) {
            return conversationRepository.findById(conversationId)
                    .filter(conversation -> !conversation.isDeleted())
                    .map(conversation -> requireOwnership(conversation, userId))
                    .orElseGet(() -> createNewConversation(userId, language));
        }
        return createNewConversation(userId, language);
    }

    @Transactional(readOnly = true)
    public Optional<AiConversationJpaEntity> getConversationForUser(UUID conversationId, UUID userId) {
        return conversationRepository.findById(conversationId)
                .filter(conversation -> !conversation.isDeleted())
                .map(conversation -> requireOwnership(conversation, userId));
    }

    private AiConversationJpaEntity createNewConversation(UUID userId, SupportedLanguage language) {
        AiConversationJpaEntity conversation = new AiConversationJpaEntity();
        conversation.setUserId(userId);
        conversation.setLocale((language == null ? SupportedLanguage.VI : language).code());
        conversation.setStartedAt(OffsetDateTime.now());
        return conversationRepository.save(conversation);
    }

    @Transactional
    public AiMessageJpaEntity appendMessage(UUID conversationId, AiMessageRole role, String content, String toolCalls) {
        AiMessageJpaEntity message = new AiMessageJpaEntity();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setToolCalls(toolCalls);
        return messageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public List<AiMessageJpaEntity> getRecentMessages(UUID conversationId, int limit) {
        // Fetch the last N messages for the LLM Context window
        return messageRepository.findByConversationIdOrderByCreatedAtDesc(
                conversationId,
                PageRequest.of(0, limit, Sort.by("createdAt").descending())
        );
    }

    @Transactional(readOnly = true)
    public Optional<String> getStructuredContext(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .map(AiConversationJpaEntity::getStructuredContext);
    }

    @Transactional
    public UUID beginProcessing(UUID conversationId) {
        var conversation = conversationRepository.findForUpdate(conversationId).orElseThrow();
        if (conversation.getProcessingUntil() != null && conversation.getProcessingUntil().isAfter(OffsetDateTime.now()))
            throw new AiStateConflictException("This conversation is already processing another message");
        UUID token = UUID.randomUUID();
        conversation.setProcessingToken(token); conversation.setProcessingUntil(OffsetDateTime.now().plusMinutes(2));
        conversationRepository.save(conversation);
        return token;
    }

    @Transactional
    public void finishProcessing(UUID conversationId, UUID token) {
        if (token == null) return;
        conversationRepository.findForUpdate(conversationId).ifPresent(conversation -> {
            if (token.equals(conversation.getProcessingToken())) {
                conversation.setProcessingToken(null); conversation.setProcessingUntil(null);
                conversationRepository.save(conversation);
            }
        });
    }

    @Transactional
    public void saveStructuredContext(UUID conversationId, String json) {
        conversationRepository.findById(conversationId).ifPresent(conversation -> {
            conversation.setStructuredContext(json);
            conversationRepository.save(conversation);
        });
    }

    @Transactional
    public void saveResponsePayload(UUID messageId, String json) {
        if (messageId != null) messageRepository.findById(messageId).ifPresent(message -> {
            message.setResponsePayload(json);
            messageRepository.save(message);
        });
    }

    @Transactional(readOnly = true)
    public Optional<List<AiMessageJpaEntity>> getRecentMessagesForUser(
            UUID conversationId,
            UUID userId,
            int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        return getConversationForUser(conversationId, userId)
                .map(conversation -> getRecentMessages(conversation.getId(), boundedLimit));
    }

    @Transactional(readOnly = true)
    public List<AiConversationJpaEntity> listConversationsForUser(UUID userId, Boolean archived, int limit) {
        if (userId == null) return List.of();
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        var pageable = PageRequest.of(0, boundedLimit);
        if (archived != null) {
            return conversationRepository.findByUserIdAndArchived(userId, archived, pageable);
        }
        return conversationRepository.findByUserId(userId, pageable);
    }

    @Transactional
    public AiConversationJpaEntity updateConversationMeta(UUID conversationId, UUID userId, String title, Boolean pinned, Boolean archived) {
        AiConversationJpaEntity conv = conversationRepository.findById(conversationId)
                .filter(c -> !c.isDeleted())
                .map(c -> requireOwnership(c, userId))
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        if (title != null && !title.isBlank()) {
            conv.setTitle(title.trim().length() > 200 ? title.trim().substring(0, 200) : title.trim());
        }
        if (pinned != null) {
            conv.setPinned(pinned);
        }
        if (archived != null) {
            conv.setArchived(archived);
        }
        return conversationRepository.save(conv);
    }

    @Transactional
    public void softDeleteConversation(UUID conversationId, UUID userId) {
        conversationRepository.findById(conversationId).ifPresent(c -> {
            requireOwnership(c, userId);
            c.setDeleted(true);
            c.setDeletedAt(OffsetDateTime.now());
            conversationRepository.save(c);
        });
    }

    @Transactional
    public void autoGenerateTitleIfEmpty(UUID conversationId, String firstMessage) {
        if (conversationId == null || firstMessage == null || firstMessage.isBlank()) return;
        conversationRepository.findById(conversationId).ifPresent(conv -> {
            if (conv.getTitle() == null || conv.getTitle().isBlank()) {
                String clean = firstMessage.replaceAll("[\\r\\n]+", " ").trim();
                String title = clean.length() > 60 ? clean.substring(0, 57) + "..." : clean;
                conv.setTitle(title);
                conversationRepository.save(conv);
            }
        });
    }

    private AiConversationJpaEntity requireOwnership(AiConversationJpaEntity conversation, UUID userId) {
        if (userId == null || !userId.equals(conversation.getUserId())) {
            throw new AccessDeniedException("Conversation does not belong to the authenticated user");
        }
        return conversation;
    }
}
