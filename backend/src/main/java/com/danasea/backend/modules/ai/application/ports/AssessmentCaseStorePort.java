package com.danasea.backend.modules.ai.application.ports;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.danasea.backend.modules.ai.domain.models.AssessmentCase;

public interface AssessmentCaseStorePort {
    AssessmentCase create(String kind, UUID sourceId, UUID actorId, String status, Map<String, Object> evidence, Map<String, Object> decisions);
    List<AssessmentCase> list(String status, int limit);
    AssessmentCase resolve(UUID id, UUID adminId, String resolution, String note);

    default Optional<AssessmentCase> findById(UUID id) {
        return Optional.empty();
    }

    default List<AssessmentCase> findBySourceId(UUID sourceId) {
        return List.of();
    }
}
