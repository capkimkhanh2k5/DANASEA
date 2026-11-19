package com.danasea.backend.modules.ai.presentation.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.danasea.backend.modules.ai.application.dtos.TravelRequest;
import com.danasea.backend.modules.ai.application.usecases.PlanItineraryUseCase;
import com.danasea.backend.modules.ai.domain.models.ItineraryPlan.Preview;
import com.danasea.backend.modules.ai.domain.models.ItineraryPlan.Proposal;
import com.danasea.backend.modules.ai.domain.models.ItineraryPlan.Revision;
import com.danasea.backend.modules.ai.domain.models.ItineraryPlan.Saved;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import com.danasea.backend.shared.i18n.SupportedLanguage;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ai/itineraries")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class ItineraryLifecycleController {
    public record SavePreviewRequest(String alternativeId) {}
    public record VersionRequest(Long expectedVersion) {}
    private final PlanItineraryUseCase itineraries;

    @PostMapping("/preview")
    public Preview preview(@RequestBody TravelRequest request) { return itineraries.preview(userId(), request.context(), language()); }

    @PostMapping("/previews/{previewId}/save")
    public Saved save(@PathVariable UUID previewId, @RequestBody SavePreviewRequest request,
                      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return itineraries.savePreview(previewId, userId(), request.alternativeId(), key);
    }

    @GetMapping("/page")
    public List<Saved> page(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return itineraries.list(userId(), page, size);
    }

    @PostMapping("/{id}/accept")
    public Saved accept(@PathVariable UUID id, @RequestBody VersionRequest request) {
        return itineraries.accept(id, userId(), version(request), language());
    }

    @PostMapping("/{id}/archive")
    public Saved archive(@PathVariable UUID id, @RequestBody VersionRequest request) {
        return itineraries.archive(id, userId(), version(request));
    }

    @GetMapping("/{id}/proposals")
    public List<Proposal> proposals(@PathVariable UUID id) { return itineraries.proposals(id, userId()); }

    @PostMapping("/{id}/proposals/{proposalId}/accept")
    public Saved acceptProposal(@PathVariable UUID id, @PathVariable UUID proposalId, @RequestBody VersionRequest request) {
        return itineraries.acceptProposal(id, userId(), proposalId, version(request), language());
    }

    @PostMapping("/{id}/proposals/{proposalId}/reject")
    public Proposal rejectProposal(@PathVariable UUID id, @PathVariable UUID proposalId, @RequestBody VersionRequest request) {
        return itineraries.rejectProposal(id, userId(), proposalId, version(request));
    }

    @GetMapping("/{id}/revisions")
    public List<Revision> revisions(@PathVariable UUID id) { return itineraries.revisions(id, userId()); }

    @PostMapping("/{id}/revisions/{revisionVersion}/restore")
    public Proposal restore(@PathVariable UUID id, @PathVariable long revisionVersion, @RequestBody VersionRequest request) {
        return itineraries.restoreFromRevision(id, userId(), version(request), revisionVersion, language());
    }

    private long version(VersionRequest request) {
        if (request.expectedVersion() == null || request.expectedVersion() < 0) throw new IllegalArgumentException("expectedVersion is required");
        return request.expectedVersion();
    }
    private UUID userId() { return SecurityUtils.getCurrentUserId().orElseThrow(() -> new AccessDeniedException("Authentication required")); }
    private SupportedLanguage language() { return SupportedLanguage.fromTag(LocaleContextHolder.getLocale().toLanguageTag()).orElse(SupportedLanguage.VI); }
}
