package com.danasea.backend.modules.ai.application.usecases;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.danasea.backend.modules.ai.application.ports.ItineraryStorePort;
import com.danasea.backend.modules.ai.application.ports.TravelWeatherPort;
import com.danasea.backend.modules.ai.domain.exceptions.AiResourceNotFoundException;
import com.danasea.backend.modules.ai.domain.exceptions.AiStateConflictException;
import com.danasea.backend.modules.ai.domain.models.ItineraryPlan;
import com.danasea.backend.modules.ai.domain.models.ItineraryPlan.Alternative;
import com.danasea.backend.modules.ai.domain.models.ItineraryPlan.Item;
import com.danasea.backend.modules.ai.domain.models.ItineraryPlan.Preview;
import com.danasea.backend.modules.ai.domain.models.ItineraryPlan.Proposal;
import com.danasea.backend.modules.ai.domain.models.ItineraryPlan.Revision;
import com.danasea.backend.modules.ai.domain.models.ItineraryPlan.Saved;
import com.danasea.backend.modules.ai.domain.models.TravelContext;
import com.danasea.backend.modules.ai.domain.services.TravelQueryParser;
import com.danasea.backend.modules.service.application.api.AiCatalogReadApi.Option;
import com.danasea.backend.modules.service.application.api.AiCatalogReadApi.PublishedService;
import com.danasea.backend.modules.service.application.api.AiCatalogReadApi.Slot;
import com.danasea.backend.shared.i18n.SupportedLanguage;

import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@Service
@RequiredArgsConstructor
public class PlanItineraryUseCase {
    private static final int MAX_SERVICES = 15;
    private static final int MAX_CHOICES = 180;
    private static final int BEAM_WIDTH = 24;
    public record Replanned(Saved itinerary, List<UUID> removedSlotIds, List<UUID> addedSlotIds,
                            List<Item> changedItems, String trigger, boolean bookingChanged, Proposal proposal) {}
    private record Choice(PublishedService service, Option option, Slot slot, double relevance) {}
    private record Path(List<Item> items, BigDecimal total, double relevance) {}

    private final DiscoverServicesUseCase discovery;
    private final TravelWeatherPort weather;
    private final ItineraryStorePort store;

    public Saved create(UUID ownerId, TravelContext context, SupportedLanguage language) {
        requireOwner(ownerId);
        return store.create(ownerId, propose(context, Set.of(), Set.of(), language), null, null, language);
    }

    public Saved create(UUID ownerId, TravelContext context, SupportedLanguage language, String key) {
        if (key == null) return create(ownerId, context, language);
        requireOwner(ownerId);
        return store.create(ownerId, propose(context, Set.of(), Set.of(), language), key, context.toString(), language);
    }

    public Preview preview(UUID ownerId, TravelContext context, SupportedLanguage language) {
        requireOwner(ownerId);
        return store.savePreview(ownerId, alternatives(context, Set.of(), Set.of(), List.of(), language), language);
    }

    public Saved savePreview(UUID previewId, UUID ownerId, String alternativeId, String key) {
        requireOwner(ownerId);
        if (alternativeId == null || alternativeId.isBlank()) throw new IllegalArgumentException("An alternative is required");
        return store.savePreview(previewId, ownerId, alternativeId, key);
    }

    public Saved get(UUID id, UUID ownerId) {
        requireOwner(ownerId);
        return store.find(id, ownerId).orElseThrow(() -> new AiResourceNotFoundException("Itinerary not found"));
    }

    public List<Saved> list(UUID ownerId) { requireOwner(ownerId); return store.list(ownerId); }
    public List<Saved> list(UUID ownerId, int page, int size) {
        requireOwner(ownerId);
        if (page < 0 || page > 10000 || size < 1 || size > 50) throw new IllegalArgumentException("Invalid itinerary page");
        return store.list(ownerId, page, size);
    }

    public Saved accept(UUID id, UUID ownerId, long expectedVersion, SupportedLanguage language) {
        Saved saved = checked(id, ownerId, expectedVersion);
        if ("ARCHIVED".equals(saved.lifecycle())) throw new AiStateConflictException("An archived itinerary cannot be accepted");
        revalidate(saved.plan(), language);
        return store.transition(id, ownerId, expectedVersion, "ACCEPTED", "CUSTOMER_ACCEPTED");
    }

    public Saved archive(UUID id, UUID ownerId, long expectedVersion) {
        checked(id, ownerId, expectedVersion);
        return store.transition(id, ownerId, expectedVersion, "ARCHIVED", "CUSTOMER_ARCHIVED");
    }

    public List<Revision> revisions(UUID id, UUID ownerId) { get(id, ownerId); return store.revisions(id, ownerId); }
    public List<Proposal> proposals(UUID id, UUID ownerId) { get(id, ownerId); return store.proposals(id, ownerId); }

    public Saved acceptProposal(UUID id, UUID ownerId, UUID proposalId, long expectedVersion, SupportedLanguage language) {
        checked(id, ownerId, expectedVersion);
        Proposal proposal = store.findProposal(id, ownerId, proposalId)
                .orElseThrow(() -> new AiResourceNotFoundException("Itinerary proposal not found"));
        if (!"PENDING".equals(proposal.state()) || proposal.baseVersion() != expectedVersion) {
            throw new AiStateConflictException("This proposal is no longer current");
        }
        revalidate(proposal.plan(), language);
        return store.acceptProposal(id, ownerId, proposalId, expectedVersion);
    }

    public Proposal rejectProposal(UUID id, UUID ownerId, UUID proposalId, long expectedVersion) {
        checked(id, ownerId, expectedVersion);
        return store.rejectProposal(id, ownerId, proposalId, expectedVersion);
    }

    public Replanned replan(UUID id, UUID ownerId, long expectedVersion, TravelContext replacement,
                            Set<UUID> excludedServices, Set<UUID> excludedSlots, String ignoredClientTrigger,
                            SupportedLanguage language) {
        Saved old = checked(id, ownerId, expectedVersion);
        validateExclusions(excludedServices, excludedSlots);
        if ("ARCHIVED".equals(old.lifecycle())) throw new AiStateConflictException("An archived itinerary cannot be replanned");
        TravelContext requested = replacement == null ? old.plan().context() : replacement;
        List<Item> started = old.plan().items().stream().filter(item -> !item.start().isAfter(LocalDateTime.now(TravelContext.ZONE))).toList();
        ItineraryPlan next = alternatives(remainingContext(requested), excludedServices, excludedSlots, started, language).getFirst().plan();
        if (replacement == null) next = withContext(next, old.plan().context());
        Proposal proposal = store.propose(id, ownerId, expectedVersion, next, "CUSTOMER_REQUEST", null);
        return new Replanned(old, proposal.removedSlotIds(), proposal.addedSlotIds(), proposal.changedItems(),
                proposal.trigger(), false, proposal);
    }

    public void sourceChanged(UUID slotId, String sourceEventId, String trigger) {
        for (Saved saved : store.trackedForSlot(slotId)) {
            if (saved.plan().items().stream().noneMatch(item -> item.slotId().equals(slotId)
                    && item.start().isAfter(LocalDateTime.now(TravelContext.ZONE)))) continue;
            if (store.hasSourceEvent(saved.id(), sourceEventId)) continue;
            try {
                Saved stale = "STALE".equals(saved.lifecycle()) ? saved
                        : store.transition(saved.id(), saved.ownerId(), saved.version(), "STALE", trigger);
                Set<UUID> impactedSlots = new LinkedHashSet<>(Set.of(slotId));
                store.proposals(stale.id(), stale.ownerId()).stream()
                        .filter(proposal -> "PENDING".equals(proposal.state()) && proposal.sourceEventId() != null && proposal.baseVersion() == stale.version())
                        .forEach(proposal -> impactedSlots.addAll(proposal.removedSlotIds()));
                List<Item> unaffected = stale.plan().items().stream().filter(item -> !impactedSlots.contains(item.slotId())).toList();
                ItineraryPlan next = alternatives(remainingContext(stale.plan().context()), Set.of(), impactedSlots, unaffected,
                        SupportedLanguage.fromTag(stale.locale()).orElse(SupportedLanguage.VI)).getFirst().plan();
                next = withContext(next, stale.plan().context());
                store.propose(stale.id(), stale.ownerId(), stale.version(), next, trigger, sourceEventId);
            } catch (AiStateConflictException | ObjectOptimisticLockingFailureException ignored) {
                // A concurrent customer change is authoritative; the next monitoring pass retries.
            }
        }
    }

    public ItineraryPlan propose(TravelContext context, Set<UUID> excludedServices, Set<UUID> excludedSlots,
                                  SupportedLanguage language) {
        return alternatives(context, excludedServices, excludedSlots, List.of(), language).getFirst().plan();
    }

    public List<Alternative> alternatives(TravelContext context, Set<UUID> excludedServices, Set<UUID> excludedSlots,
                                         List<Item> preserved, SupportedLanguage language) {
        context.validateCurrentDates();
        validateExclusions(excludedServices, excludedSlots);
        var found = discovery.execute(context, "RECOMMENDATION", language);
        Set<String> unresolved = new LinkedHashSet<>();
        unresolved.add("TRANSFER_ESTIMATE_HAVERSINE_25_KMH_PLUS_15_MINUTES_NOT_A_ROUTE");
        unresolved.add("MEALS_AND_TRANSFER_COSTS_NOT_INCLUDED");
        found.requiredInputs().forEach(input -> unresolved.add("REQUIRED_INPUT:" + input));
        unresolved.addAll(found.limitations());
        List<Choice> choices = choices(found.candidates(), excludedServices, excludedSlots);
        if (choices.size() >= MAX_CHOICES) unresolved.add("BOUNDED_SLOT_COVERAGE");
        Map<UUID, TravelWeatherPort.Assessment> weatherCache = new HashMap<>();
        List<Path> frontier = new ArrayList<>();
        BigDecimal fixedCost = preserved.stream().map(Item::partyTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        frontier.add(new Path(List.copyOf(preserved), fixedCost, 0));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        for (Choice choice : choices) {
            if (System.nanoTime() >= deadline) { unresolved.add("PLANNING_DEADLINE_PARTIAL_COVERAGE"); break; }
            List<Path> expanded = new ArrayList<>(frontier);
            for (Path path : frontier) {
                Path added = extend(path, choice, context, found.effectiveTotalBudget(), unresolved, weatherCache);
                if (added != null) expanded.add(added);
            }
            frontier = trim(expanded, BEAM_WIDTH);
        }
        List<Path> selected = distinctAlternatives(frontier);
        List<Alternative> result = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            Path path = selected.get(index);
            Set<String> constraints = new LinkedHashSet<>(unresolved);
            if (path.items().isEmpty()) constraints.add("NO_FEASIBLE_CURRENT_SLOTS");
            if (!preserved.isEmpty()) constraints.add("UNAFFECTED_ITEMS_PRESERVED_REVALIDATE_BEFORE_ACCEPTANCE");
            if (path.items().stream().anyMatch(Item::provisionalWeather)) constraints.add("WEATHER_REVALIDATION_REQUIRED");
            String status = !found.requiredInputs().isEmpty() ? "NEEDS_INPUT" : path.items().isEmpty() ? "NEEDS_REVIEW"
                    : path.items().stream().anyMatch(item -> "WAITING_FOR_FORECAST".equals(item.weatherStatus())) ? "WAITING_FOR_FORECAST"
                    : path.items().stream().anyMatch(Item::provisionalWeather) ? "PROVISIONAL" : "PROPOSED";
            ItineraryPlan plan = new ItineraryPlan(status, context, path.total(), path.items(), List.copyOf(constraints),
                    "BOUNDED_BEAM_SEARCH_PER_DAY_NO_GLOBAL_OPTIMALITY_GUARANTEE", false);
            result.add(new Alternative("alternative-" + (index + 1), plan,
                    List.of("ACTIVITIES:" + path.items().size(), "SERVICE_COST_VND:" + path.total().toPlainString(),
                            "ESTIMATED_TRANSFER_MINUTES:" + path.items().stream().mapToInt(Item::estimatedTravelMinutes).sum())));
        }
        return List.copyOf(result);
    }

    private List<Choice> choices(List<DiscoverServicesUseCase.Candidate> candidates,
                                 Set<UUID> excludedServices, Set<UUID> excludedSlots) {
        return candidates.stream().limit(MAX_SERVICES).filter(candidate -> !excludedServices.contains(candidate.service().id()))
                .flatMap(candidate -> candidate.service().options().stream().flatMap(option -> option.slots().stream()
                        .filter(slot -> !excludedSlots.contains(slot.id()))
                        .map(slot -> new Choice(candidate.service(), option, slot, candidate.score()))))
                .sorted(Comparator.comparing((Choice choice) -> LocalDateTime.of(choice.slot().date(), choice.slot().start()))
                        .thenComparing(choice -> choice.option().partyTotal()).thenComparing(choice -> choice.service().id()))
                .limit(MAX_CHOICES).toList();
    }

    private Path extend(Path path, Choice choice, TravelContext context, BigDecimal budget,
                        Set<String> unresolved, Map<UUID, TravelWeatherPort.Assessment> weatherCache) {
        LocalDate date = choice.slot().date();
        if (path.items().stream().anyMatch(item -> item.serviceId().equals(choice.service().id()) && item.start().toLocalDate().equals(date))) return null;
        if (path.items().stream().filter(item -> item.start().toLocalDate().equals(date)).count() >= context.maxActivities()) return null;
        BigDecimal total = path.total().add(choice.option().partyTotal());
        if (budget != null && total.compareTo(budget) > 0) return null;
        LocalDateTime start = LocalDateTime.of(date, choice.slot().start());
        LocalDateTime end = LocalDateTime.of(date, DiscoverServicesUseCase.endTime(choice.slot().end(), choice.slot().start(), choice.service().durationMinutes()));
        if (!start.isAfter(LocalDateTime.now(TravelContext.ZONE))) return null;
        if (start.toLocalTime().isBefore(context.dayStart()) || end.toLocalTime().isAfter(context.dayEnd()) || !end.isAfter(start)) return null;
        Item before = path.items().stream().filter(item -> item.start().toLocalDate().equals(date) && !item.end().isAfter(start))
                .max(Comparator.comparing(Item::end)).orElse(null);
        Item after = path.items().stream().filter(item -> item.start().toLocalDate().equals(date) && !item.start().isBefore(end))
                .min(Comparator.comparing(Item::start)).orElse(null);
        if (path.items().stream().anyMatch(item -> start.isBefore(item.end()) && end.isAfter(item.start()))) return null;
        int transit = estimate(before == null ? context.latitude() : before.latitude(),
                before == null ? context.longitude() : before.longitude(), choice.service().latitude(), choice.service().longitude());
        if (transit < 0 && before != null) { unresolved.add("MISSING_TRANSFER_COORDINATES"); return null; }
        if (before != null && start.isBefore(before.end().plusMinutes(transit))) return null;
        if (before == null && context.latitude() != null && start.isBefore(LocalDateTime.of(date, context.dayStart()).plusMinutes(Math.max(0, transit)))) return null;
        if (after != null) {
            int nextTransit = estimate(choice.service().latitude(), choice.service().longitude(), after.latitude(), after.longitude());
            if (nextTransit < 0 || end.plusMinutes(nextTransit).isAfter(after.start())) return null;
        }
        long sensitiveChecks = weatherCache.values().stream().filter(value -> value != null && !"NOT_REQUIRED".equals(value.status())).count();
        if (choice.service().weatherSensitive() && !weatherCache.containsKey(choice.slot().id()) && sensitiveChecks >= 20) {
            unresolved.add("WEATHER_CHECK_LIMIT_PARTIAL_COVERAGE");
            return null;
        }
        var assessment = weatherCache.computeIfAbsent(choice.slot().id(), ignored -> weather.assess(choice.service(), choice.slot()));
        boolean waitingForForecast = assessment != null && "WAITING_FOR_FORECAST".equals(assessment.status());
        if (assessment == null || (!assessment.acceptable() && !waitingForForecast)) {
            unresolved.add(assessment == null ? "WEATHER_UNKNOWN" : "WEATHER_" + assessment.status());
                return null;
        }
        if (waitingForForecast) unresolved.add("WAITING_FOR_FORECAST_NO_SAFETY_AUTHORIZATION");
        List<Item> items = new ArrayList<>(path.items());
        items.add(new Item(choice.service().id(), choice.option().id(), choice.slot().id(), choice.service().name(), start, end,
                choice.option().quantity(), choice.option().unitPrice(), choice.option().partyTotal(), choice.service().latitude(),
                choice.service().longitude(), Math.max(0, transit), assessment.status(), assessment.provisional() || waitingForForecast, assessment.message()));
        items.sort(Comparator.comparing(Item::start));
        return new Path(List.copyOf(items), total, path.relevance() + choice.relevance());
    }

    private int estimate(Double fromLat, Double fromLon, Double toLat, Double toLon) {
        if (fromLat == null || fromLon == null || toLat == null || toLon == null) return -1;
        return 15 + (int) Math.ceil(TravelQueryParser.distanceKm(fromLat, fromLon, toLat, toLon) / 25 * 60);
    }

    private List<Path> trim(List<Path> paths, int limit) {
        Set<String> seen = new HashSet<>();
        return paths.stream().sorted(Comparator.comparingInt((Path path) -> path.items().size()).reversed()
                        .thenComparing(Path::total).thenComparing(Comparator.comparingDouble(Path::relevance).reversed())
                        .thenComparing(this::fingerprint))
                .filter(path -> seen.add(fingerprint(path))).limit(limit).toList();
    }

    private List<Path> distinctAlternatives(List<Path> paths) {
        List<Path> choices = new ArrayList<>(trim(paths, BEAM_WIDTH));
        List<Path> selected = new ArrayList<>();
        selected.add(choices.getFirst());
        choices.removeFirst();
        choices.stream().filter(path -> !path.items().isEmpty()).sorted(Comparator.comparingDouble(Path::relevance).reversed()
                .thenComparing(Path::total)).limit(2).forEach(selected::add);
        return selected;
    }

    private String fingerprint(Path path) { return path.items().stream().map(item -> item.slotId() + ":" + item.optionId()).collect(Collectors.joining("|")); }

    private void revalidate(ItineraryPlan plan, SupportedLanguage language) {
        if (plan.items().isEmpty()) throw new AiStateConflictException("An empty itinerary cannot be accepted");
        TravelContext remaining = remainingContext(plan.context());
        for (Item item : plan.items()) {
            if (!item.start().isAfter(LocalDateTime.now(TravelContext.ZONE))) continue;
            PublishedService service = discovery.detail(item.serviceId(), remaining, language);
            Option option = service.options().stream().filter(value -> value.id().equals(item.optionId())).findFirst()
                    .orElseThrow(() -> new AiStateConflictException("An itinerary option is no longer available"));
            Slot slot = option.slots().stream().filter(value -> value.id().equals(item.slotId())).findFirst()
                    .orElseThrow(() -> new AiStateConflictException("An itinerary slot is no longer available"));
            if (option.partyTotal().compareTo(item.partyTotal()) != 0 || option.unitPrice().compareTo(item.unitPrice()) != 0
                    || option.quantity() != item.quantity() || !LocalDateTime.of(slot.date(), slot.start()).equals(item.start())
                    || !LocalDateTime.of(slot.date(), DiscoverServicesUseCase.endTime(slot.end(), slot.start(), service.durationMinutes())).equals(item.end())
                    || !Objects.equals(service.latitude(), item.latitude()) || !Objects.equals(service.longitude(), item.longitude())) {
                throw new AiStateConflictException("An itinerary quote has changed; preview a new proposal");
            }
            var assessment = weather.assess(service, slot);
            if (assessment == null || !assessment.acceptable()) throw new AiStateConflictException("Itinerary weather must be revalidated");
        }
    }

    private TravelContext remainingContext(TravelContext context) {
        LocalDate today = LocalDate.now(TravelContext.ZONE);
        if (context.to().isBefore(today)) throw new AiStateConflictException("This itinerary has ended; create a new travel preview");
        if (!context.from().isBefore(today)) return context;
        return new TravelContext(context.query(), context.categoryId(), context.totalBudget(), context.partySize(), today,
                context.to(), context.dayStart(), context.dayEnd(), context.latitude(), context.longitude(), context.radiusKm(),
                context.interests(), context.limit(), context.maxActivities(), context.weatherSafeOnly(), context.criteria());
    }

    private ItineraryPlan withContext(ItineraryPlan plan, TravelContext context) {
        return new ItineraryPlan(plan.status(), context, plan.totalPrice(), plan.items(), plan.unresolvedConstraints(), plan.planningMethod(), false);
    }

    private Saved checked(UUID id, UUID ownerId, long expectedVersion) {
        Saved saved = get(id, ownerId);
        if (expectedVersion < 0 || saved.version() != expectedVersion) throw new AiStateConflictException("Itinerary version has changed");
        return saved;
    }

    private void validateExclusions(Set<UUID> services, Set<UUID> slots) {
        if (services == null || slots == null || services.stream().anyMatch(Objects::isNull) || slots.stream().anyMatch(Objects::isNull)
                || services.size() > 15 || slots.size() > 75) throw new IllegalArgumentException("Invalid replanning exclusions");
    }

    private void requireOwner(UUID ownerId) {
        if (ownerId == null) throw new IllegalArgumentException("An authenticated owner is required");
    }
}
