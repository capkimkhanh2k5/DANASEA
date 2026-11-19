package com.danasea.backend.modules.ai.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.ai.application.ports.AssessmentCaseStorePort;
import com.danasea.backend.modules.ai.domain.exceptions.AiResourceNotFoundException;
import com.danasea.backend.modules.ai.domain.exceptions.AiStateConflictException;
import com.danasea.backend.modules.ai.domain.models.AssessmentCase;
import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiAssessmentCaseJpaEntity;
import com.danasea.backend.modules.ai.infrastructure.persistence.repositories.JpaAiAssessmentCaseRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AssessmentCaseStoreAdapter implements AssessmentCaseStorePort {
    private final JpaAiAssessmentCaseRepository repository;
    private final ObjectMapper mapper;

    @Override
    @Transactional
    public AssessmentCase create(String kind, UUID sourceId, UUID actorId, String status, Map<String, Object> evidence, Map<String, Object> decisions) {
        if (actorId == null) throw new IllegalArgumentException("An authenticated actor is required");
        AiAssessmentCaseJpaEntity entity = new AiAssessmentCaseJpaEntity();
        entity.setKind(kind); entity.setSourceId(sourceId); entity.setRequestedBy(actorId); entity.setStatus(status);
        try {
            entity.setEvidenceJson(mapper.writeValueAsString(evidence)); entity.setDecisionJson(mapper.writeValueAsString(decisions));
        } catch (Exception exception) { throw new IllegalStateException("Cannot serialize assessment evidence", exception); }
        return toCase(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssessmentCase> list(String status, int limit) {
        if (status == null || !Set.of("NEEDS_REVIEW", "NO_RULE_SIGNAL", "REVIEWED", "DISMISSED").contains(status)) throw new IllegalArgumentException("Invalid assessment status");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Case limit must be between 1 and 100");
        return repository.findByStatusOrderByCreatedAtDesc(status, PageRequest.of(0, limit)).stream().map(this::toCase).toList();
    }

    @Override
    @Transactional
    public AssessmentCase resolve(UUID id, UUID adminId, String resolution, String note) {
        if (adminId == null || resolution == null || !Set.of("REVIEWED", "DISMISSED").contains(resolution) || note == null || note.isBlank() || note.length() > 1000) {
            throw new IllegalArgumentException("A review resolution and a note of 1 to 1000 characters are required");
        }
        var entity = repository.findById(id).orElseThrow(() -> new AiResourceNotFoundException("Assessment case not found"));
        if (entity.getResolvedBy() != null) throw new AiStateConflictException("Assessment case has already been resolved");
        entity.setStatus(resolution); entity.setResolvedBy(adminId); entity.setResolutionNote(note);
        return toCase(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AssessmentCase> findById(UUID id) {
        if (id == null) return Optional.empty();
        return repository.findById(id).map(this::toCase);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssessmentCase> findBySourceId(UUID sourceId) {
        if (sourceId == null) return List.of();
        return repository.findBySourceIdOrderByCreatedAtDesc(sourceId).stream().map(this::toCase).toList();
    }

    private AssessmentCase toCase(AiAssessmentCaseJpaEntity entity) {
        try {
            return new AssessmentCase(entity.getId(), entity.getKind(), entity.getSourceId(), entity.getRequestedBy(), entity.getStatus(),
                    mapper.readValue(entity.getEvidenceJson(), new TypeReference<Map<String, Object>>() {}),
                    mapper.readValue(entity.getDecisionJson(), new TypeReference<Map<String, Object>>() {}),
                    entity.getResolvedBy(), entity.getResolutionNote(), entity.getCreatedAt());
        } catch (Exception exception) { throw new IllegalStateException("Cannot read assessment case", exception); }
    }
}
