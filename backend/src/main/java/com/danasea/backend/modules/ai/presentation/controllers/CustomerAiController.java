package com.danasea.backend.modules.ai.presentation.controllers;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.danasea.backend.modules.ai.application.dtos.TravelRequest;
import com.danasea.backend.modules.ai.application.usecases.CompareServicesUseCase;
import com.danasea.backend.modules.ai.application.usecases.CustomerMarineBriefingUseCase;
import com.danasea.backend.modules.ai.application.usecases.CustomerSupportUseCase;
import com.danasea.backend.modules.ai.application.usecases.DiscoverServicesUseCase;
import com.danasea.backend.modules.ai.application.usecases.PlanItineraryUseCase;
import com.danasea.backend.modules.ai.application.usecases.ReviewSummaryUseCase;
import com.danasea.backend.modules.ai.application.usecases.WeatherAwareUseCase;
import com.danasea.backend.modules.ai.domain.models.ItineraryPlan.Saved;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import com.danasea.backend.shared.i18n.SupportedLanguage;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ai")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class CustomerAiController {
    public record ComparisonRequest(List<UUID> serviceIds, TravelRequest context) {}
    public record WeatherRequest(UUID serviceId, UUID optionId, UUID slotId, TravelRequest context) {}
    public record SupportRequest(UUID orderId, String message, Boolean includeCancellationPreview) {}
    public record ReplanRequest(Long expectedVersion, TravelRequest context, Set<UUID> excludedServiceIds,
                                Set<UUID> excludedSlotIds, String trigger) {}

    private final DiscoverServicesUseCase discovery;
    private final CompareServicesUseCase comparison;
    private final PlanItineraryUseCase itineraries;
    private final ReviewSummaryUseCase reviews;
    private final CustomerSupportUseCase support;
    private final WeatherAwareUseCase weather;
    private final CustomerMarineBriefingUseCase marineBriefingUseCase;

    @PostMapping("/search")
    public DiscoverServicesUseCase.Result search(@RequestBody TravelRequest request) { return discovery.execute(request.context(), "SEARCH", language()); }

    @PostMapping("/recommendations")
    public DiscoverServicesUseCase.Result recommendations(@RequestBody TravelRequest request) { return discovery.execute(request.context(), "RECOMMENDATION", language(), userId()); }

    @PostMapping("/nearby")
    public DiscoverServicesUseCase.Result nearby(@RequestBody TravelRequest request) { return discovery.execute(request.context(), "NEARBY", language()); }

    @PostMapping("/services/compare")
    public CompareServicesUseCase.Result compare(@RequestBody ComparisonRequest request) {
        return comparison.execute(request.serviceIds(), requiredContext(request.context()).context(), language());
    }

    @PostMapping("/weather")
    public WeatherAwareUseCase.Result weather(@RequestBody WeatherRequest request) {
        return weather.execute(request.serviceId(), request.optionId(), request.slotId(), requiredContext(request.context()).context(), language());
    }

    @GetMapping("/review-summaries/{serviceId}")
    public ReviewSummaryUseCase.Result reviews(@PathVariable UUID serviceId) { return reviews.execute(serviceId, language()); }

    @GetMapping("/orders/{orderId}/marine-briefing")
    public CustomerMarineBriefingUseCase.MarineBriefingOverview marineBriefing(@PathVariable UUID orderId) {
        return marineBriefingUseCase.getMarineBriefing(userId(), orderId, language());
    }

    @PostMapping("/support")
    public CustomerSupportUseCase.Result support(@RequestBody SupportRequest request) {
        return support.execute(userId(), request.orderId(), request.message(), Boolean.TRUE.equals(request.includeCancellationPreview()));
    }

    @PostMapping("/itineraries")
    public Saved plan(@RequestBody TravelRequest request, @RequestHeader(value = "Idempotency-Key", required = false) String key) { return itineraries.create(userId(), request.context(), language(), key); }

    @GetMapping("/itineraries")
    public List<Saved> plans() { return itineraries.list(userId()); }

    @GetMapping("/itineraries/{id}")
    public Saved plan(@PathVariable UUID id) { return itineraries.get(id, userId()); }

    @PostMapping("/itineraries/{id}/replan")
    public PlanItineraryUseCase.Replanned replan(@PathVariable UUID id, @RequestBody ReplanRequest request) {
        if (request.expectedVersion() == null || request.expectedVersion() < 0) throw new IllegalArgumentException("expectedVersion is required");
        return itineraries.replan(id, userId(), request.expectedVersion(), request.context() == null ? null : request.context().context(),
                request.excludedServiceIds() == null ? Set.of() : request.excludedServiceIds(),
                request.excludedSlotIds() == null ? Set.of() : request.excludedSlotIds(),
                request.trigger() == null ? "CUSTOMER_REQUEST" : request.trigger(), language());
    }

    private TravelRequest requiredContext(TravelRequest context) {
        if (context == null) throw new IllegalArgumentException("context is required");
        return context;
    }

    private UUID userId() { return SecurityUtils.getCurrentUserId().orElseThrow(() -> new AccessDeniedException("Authentication required")); }
    private SupportedLanguage language() { return SupportedLanguage.fromTag(LocaleContextHolder.getLocale().toLanguageTag()).orElse(SupportedLanguage.VI); }
}
