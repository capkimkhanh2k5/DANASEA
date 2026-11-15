package com.danasea.backend.modules.checkin.domain.exceptions;

import com.danasea.backend.modules.checkin.presentation.dtos.EligibleSubOrderCandidate;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class AmbiguousCheckinTargetException extends RuntimeException {

    private final UUID masterOrderId;
    private final List<EligibleSubOrderCandidate> candidates;

    public AmbiguousCheckinTargetException(String message) {
        super(message);
        this.masterOrderId = null;
        this.candidates = Collections.emptyList();
    }

    public AmbiguousCheckinTargetException(String message, UUID masterOrderId, List<EligibleSubOrderCandidate> candidates) {
        super(message);
        this.masterOrderId = masterOrderId;
        this.candidates = candidates != null ? List.copyOf(candidates) : Collections.emptyList();
    }

    public UUID getMasterOrderId() {
        return masterOrderId;
    }

    public List<EligibleSubOrderCandidate> getCandidates() {
        return candidates;
    }

    public List<UUID> getEligibleSubOrderIds() {
        return candidates.stream().map(EligibleSubOrderCandidate::subOrderId).toList();
    }
}
