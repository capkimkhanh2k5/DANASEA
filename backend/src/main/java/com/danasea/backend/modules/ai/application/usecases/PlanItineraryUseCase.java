package com.danasea.backend.modules.ai.application.usecases;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    public record PlanningDiagnostics(long planningDurationMs, int candidatesFound, int choicesTotal,
                                      int choicesEvaluated, Map<String, Integer> rejectionCounts) {}
    private record Choice(PublishedService service, Option option, Slot slot, double relevance) {}
    private record Path(List<Item> items, BigDecimal total, double relevance, Set<String> coveredAreas, int preferredHits) {}

    private final DiscoverServicesUseCase discovery;
    private final TravelWeatherPort weather;
    private final ItineraryStorePort store;
    private final ThreadLocal<PlanningDiagnostics> lastDiagnostics = new ThreadLocal<>();

    public PlanningDiagnostics lastDiagnostics() { return lastDiagnostics.get(); }
    public void clearLastDiagnostics() { lastDiagnostics.remove(); }

    public static PlanningDiagnostics extractDiagnostics(List<Alternative> alternatives) {
        if (alternatives == null || alternatives.isEmpty() || alternatives.getFirst().tradeOffs() == null) {
            return new PlanningDiagnostics(0L, 0, 0, 0, Map.of());
        }
        long duration = 0L;
        int evaluated = 0;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String entry : alternatives.getFirst().tradeOffs()) {
            if (entry.startsWith("PLANNING_DURATION_MS:")) {
                try { duration = Long.parseLong(entry.substring("PLANNING_DURATION_MS:".length())); } catch (NumberFormatException ignored) {}
            } else if (entry.startsWith("CHOICES_EVALUATED:")) {
                try { evaluated = Integer.parseInt(entry.substring("CHOICES_EVALUATED:".length())); } catch (NumberFormatException ignored) {}
            } else if (entry.startsWith("REJECTION_COUNTS:")) {
                String raw = entry.substring("REJECTION_COUNTS:".length());
                if (!raw.isBlank() && !"NONE".equals(raw)) {
                    for (String pair : raw.split(",")) {
                        int eq = pair.indexOf('=');
                        if (eq > 0) {
                            try { counts.put(pair.substring(0, eq), Integer.parseInt(pair.substring(eq + 1))); }
                            catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        }
        return new PlanningDiagnostics(duration, 0, evaluated, evaluated, Map.copyOf(counts));
    }

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
        LocalDateTime now = LocalDateTime.now(TravelContext.ZONE);
        boolean preserveUnaffected = replacement == null && (!excludedServices.isEmpty() || !excludedSlots.isEmpty());
        List<Item> preserved = old.plan().items().stream()
                .filter(item -> !item.start().isAfter(now)
                        || (preserveUnaffected && !excludedServices.contains(item.serviceId()) && !excludedSlots.contains(item.slotId())))
                .toList();
        ItineraryPlan next = alternatives(remainingContext(requested), excludedServices, excludedSlots, preserved, language).getFirst().plan();
        if (replacement == null) next = withContext(next, old.plan().context());
        Proposal proposal = store.propose(id, ownerId, expectedVersion, next, "CUSTOMER_REQUEST", null);
        return new Replanned(old, proposal.removedSlotIds(), proposal.addedSlotIds(), proposal.changedItems(),
                proposal.trigger(), false, proposal);
    }

    public Replanned replanItemToWindow(UUID id, UUID ownerId, long expectedVersion, int targetItemIndex,
                                        LocalTime targetDayStart, LocalTime targetDayEnd, TravelContext replacement,
                                        Set<UUID> excludedServices, Set<UUID> excludedSlots, SupportedLanguage language) {
        Saved old = checked(id, ownerId, expectedVersion);
        validateExclusions(excludedServices, excludedSlots);
        if ("ARCHIVED".equals(old.lifecycle())) throw new AiStateConflictException("An archived itinerary cannot be replanned");
        List<Item> existingItems = old.plan().items();
        if (targetItemIndex < 1 || targetItemIndex > existingItems.size()) {
            throw new IllegalArgumentException("targetItemIndex is out of range for this itinerary");
        }
        Item targetItem = existingItems.get(targetItemIndex - 1);
        LocalDateTime now = LocalDateTime.now(TravelContext.ZONE);
        if (!targetItem.start().isAfter(now)) {
            throw new AiStateConflictException("Cannot move an activity that has already started");
        }
        TravelContext base = replacement == null ? old.plan().context() : replacement;
        LocalTime windowStart = targetDayStart != null ? targetDayStart : base.dayStart();
        LocalTime windowEnd = targetDayEnd != null ? targetDayEnd : base.dayEnd();
        if (!windowStart.isBefore(windowEnd)) {
            throw new IllegalArgumentException("Invalid target time window");
        }
        List<Item> preserved = new ArrayList<>();
        for (int index = 0; index < existingItems.size(); index++) {
            if (index == targetItemIndex - 1) continue;
            Item item = existingItems.get(index);
            if (!item.start().isAfter(now) || (!excludedServices.contains(item.serviceId()) && !excludedSlots.contains(item.slotId()))) {
                preserved.add(item);
            }
        }
        LocalDate targetDate = targetItem.start().toLocalDate();
        TravelContext remaining = remainingContext(base);
        LocalDate searchFrom = replacement == null && !targetDate.isBefore(remaining.from()) && !targetDate.isAfter(remaining.to())
                ? targetDate : remaining.from();
        LocalDate searchTo = replacement == null && !targetDate.isBefore(remaining.from()) && !targetDate.isAfter(remaining.to())
                ? targetDate : remaining.to();
        int preservedOnTargetDate = (int) preserved.stream().filter(item -> item.start().toLocalDate().equals(searchFrom)).count();
        int maxOnTargetDate = Math.min(5, Math.max(1, preservedOnTargetDate + 1));
        TravelContext moveContext = new TravelContext(base.query(), base.categoryId(), base.totalBudget(), base.partySize(),
                searchFrom, searchTo, windowStart, windowEnd, base.latitude(), base.longitude(), base.radiusKm(),
                base.interests(), base.limit(), maxOnTargetDate, base.weatherSafeOnly(), base.criteria());
        Set<UUID> effectiveExcludedSlots = new LinkedHashSet<>(excludedSlots);
        effectiveExcludedSlots.add(targetItem.slotId());
        Set<UUID> preferredServices = excludedServices.contains(targetItem.serviceId()) ? Set.of() : Set.of(targetItem.serviceId());
        ItineraryPlan next = alternativesWithPreferences(moveContext, excludedServices, effectiveExcludedSlots, preserved,
                language, preferredServices, Set.of(), Set.of()).getFirst().plan();
        if (next.items().size() <= preserved.size()) {
            Set<String> constraints = new LinkedHashSet<>(next.unresolvedConstraints());
            constraints.add("NO_FEASIBLE_SLOTS_IN_TARGET_WINDOW");
            next = new ItineraryPlan("NEEDS_REVIEW", next.context(), next.totalPrice(), next.items(),
                    List.copyOf(constraints), next.planningMethod(), false);
        }
        LocalTime expandedStart = base.dayStart().isBefore(windowStart) ? base.dayStart() : windowStart;
        LocalTime expandedEnd = base.dayEnd().isAfter(windowEnd) ? base.dayEnd() : windowEnd;
        TravelContext proposalContext = new TravelContext(base.query(), base.categoryId(), base.totalBudget(), base.partySize(),
                base.from(), base.to(), expandedStart, expandedEnd, base.latitude(), base.longitude(), base.radiusKm(),
                base.interests(), base.limit(), base.maxActivities(), base.weatherSafeOnly(), base.criteria());
        next = withContext(next, proposalContext);
        Proposal proposal = store.propose(id, ownerId, expectedVersion, next, "CUSTOMER_REQUEST", null);
        return new Replanned(old, proposal.removedSlotIds(), proposal.addedSlotIds(), proposal.changedItems(),
                proposal.trigger(), false, proposal);
    }

    public Proposal restoreFromRevision(UUID id, UUID ownerId, long expectedVersion, long targetRevisionVersion,
                                        SupportedLanguage language) {
        Saved current = checked(id, ownerId, expectedVersion);
        if ("ARCHIVED".equals(current.lifecycle())) throw new AiStateConflictException("An archived itinerary cannot be restored");
        if (targetRevisionVersion < 0) throw new IllegalArgumentException("targetRevisionVersion must be non-negative");
        Revision target = store.revisions(id, ownerId).stream()
                .filter(revision -> revision.version() == targetRevisionVersion)
                .findFirst()
                .orElseThrow(() -> new AiResourceNotFoundException("Itinerary revision not found"));
        TravelContext restoredContext = remainingContext(target.plan().context());
        restoredContext.validateCurrentDates();
        LocalDateTime now = LocalDateTime.now(TravelContext.ZONE);
        List<Item> started = current.plan().items().stream().filter(item -> !item.start().isAfter(now)).toList();
        List<Item> targetFuture = target.plan().items().stream().filter(item -> item.start().isAfter(now)).toList();
        Set<UUID> targetServices = targetFuture.stream().map(Item::serviceId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> targetSlots = targetFuture.stream().map(Item::slotId).collect(Collectors.toCollection(LinkedHashSet::new));
        ItineraryPlan next = null;
        if (!targetServices.isEmpty()) {
            ItineraryPlan exact = alternativesWithPreferences(restoredContext, Set.of(), Set.of(), started, language,
                    targetServices, targetSlots, targetServices).getFirst().plan();
            if (exact.items().size() >= started.size() + targetFuture.size()) {
                next = exact;
            }
        }
        if (next == null) {
            next = alternativesWithPreferences(restoredContext, Set.of(), Set.of(), started, language,
                    targetServices, targetSlots, Set.of()).getFirst().plan();
        }
        Set<String> constraints = new LinkedHashSet<>(next.unresolvedConstraints());
        constraints.add("RESTORED_FROM_REVISION:" + targetRevisionVersion);
        constraints.add("REVALIDATE_BEFORE_ACCEPTANCE");
        next = new ItineraryPlan(next.status(), target.plan().context(), next.totalPrice(), next.items(),
                List.copyOf(constraints), next.planningMethod(), false);
        return store.propose(id, ownerId, expectedVersion, next, "CUSTOMER_RESTORE_REVISION", null);
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
        List<Alternative> result = alternativesWithPreferences(context, excludedServices, excludedSlots, preserved, language, Set.of(), Set.of(), Set.of());
        LocalDate today = LocalDate.now(TravelContext.ZONE);
        if (preserved.isEmpty()
                && context.from().equals(today)
                && context.to().equals(today)
                && !TravelQueryParser.mentionsToday(context.query())
                && (result.isEmpty() || result.getFirst().plan().items().isEmpty())) {
            LocalDate tomorrow = today.plusDays(1);
            TravelContext nextDay = new TravelContext(
                    context.query(), context.categoryId(), context.totalBudget(), context.partySize(),
                    tomorrow, tomorrow, context.dayStart(), context.dayEnd(),
                    context.latitude(), context.longitude(), context.radiusKm(),
                    context.interests(), context.limit(), context.maxActivities(), context.weatherSafeOnly(), context.criteria());
            List<Alternative> fallback = alternativesWithPreferences(nextDay, excludedServices, excludedSlots, preserved, language, Set.of(), Set.of(), Set.of());
            if (!fallback.isEmpty() && !fallback.getFirst().plan().items().isEmpty()) {
                return fallback;
            }
        }
        return result;
    }

    private List<Alternative> alternativesWithPreferences(TravelContext context, Set<UUID> excludedServices, Set<UUID> excludedSlots,
                                                          List<Item> preserved, SupportedLanguage language,
                                                          Set<UUID> preferredServices, Set<UUID> preferredSlots, Set<UUID> restrictToServices) {
        long startNanos = System.nanoTime();
        context.validateCurrentDates();
        validateExclusions(excludedServices, excludedSlots);
        var found = discovery.execute(context, "RECOMMENDATION", language);
        Set<String> unresolved = new LinkedHashSet<>();
        unresolved.add("TRANSFER_ESTIMATE_HAVERSINE_25_KMH_PLUS_15_MINUTES_NOT_A_ROUTE");
        unresolved.add("MEALS_AND_TRANSFER_COSTS_NOT_INCLUDED");
        found.requiredInputs().forEach(input -> unresolved.add("REQUIRED_INPUT:" + input));
        unresolved.addAll(found.limitations());
        List<Choice> choices = choices(found.candidates(), excludedServices, excludedSlots, restrictToServices);
        if (choices.size() >= MAX_CHOICES) unresolved.add("BOUNDED_SLOT_COVERAGE");
        List<String> requestedAreas = TravelQueryParser.coastalAreas(context.query());
        Map<UUID, TravelWeatherPort.Assessment> weatherCache = new HashMap<>();
        Map<String, Integer> rejectionCounts = new LinkedHashMap<>();
        List<Path> frontier = new ArrayList<>();
        BigDecimal fixedCost = preserved.stream().map(Item::partyTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        frontier.add(new Path(List.copyOf(preserved), fixedCost, 0, Set.of(), 0));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        int choicesEvaluated = 0;
        for (Choice choice : choices) {
            if (System.nanoTime() >= deadline) { unresolved.add("PLANNING_DEADLINE_PARTIAL_COVERAGE"); break; }
            choicesEvaluated++;
            List<Path> expanded = new ArrayList<>(frontier);
            for (Path path : frontier) {
                Path added = extend(path, choice, context, found.effectiveTotalBudget(), unresolved, weatherCache,
                        rejectionCounts, requestedAreas, preferredServices, preferredSlots);
                if (added != null) expanded.add(added);
            }
            frontier = trim(expanded, BEAM_WIDTH, requestedAreas.size());
        }
        long planningDurationMs = Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
        lastDiagnostics.set(new PlanningDiagnostics(planningDurationMs, found.candidates().size(), choices.size(),
                choicesEvaluated, Map.copyOf(rejectionCounts)));
        List<Path> selected = distinctAlternatives(frontier, requestedAreas.size());
        List<Alternative> result = new ArrayList<>();
        String rejectionSummary = rejectionCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(","));
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
            List<String> tradeOffs = new ArrayList<>(List.of(
                    "ACTIVITIES:" + path.items().size(),
                    "SERVICE_COST_VND:" + path.total().toPlainString(),
                    "ESTIMATED_TRANSFER_MINUTES:" + path.items().stream().mapToInt(Item::estimatedTravelMinutes).sum(),
                    "PLANNING_DURATION_MS:" + planningDurationMs,
                    "CHOICES_EVALUATED:" + choicesEvaluated));
            if (!rejectionSummary.isEmpty()) {
                tradeOffs.add("REJECTION_COUNTS:" + rejectionSummary);
            }
            result.add(new Alternative("alternative-" + (index + 1), plan, List.copyOf(tradeOffs)));
        }
        return List.copyOf(result);
    }

    private List<Choice> choices(List<DiscoverServicesUseCase.Candidate> candidates,
                                 Set<UUID> excludedServices, Set<UUID> excludedSlots, Set<UUID> restrictToServices) {
        return candidates.stream().limit(MAX_SERVICES)
                .filter(candidate -> !excludedServices.contains(candidate.service().id()))
                .filter(candidate -> restrictToServices.isEmpty() || restrictToServices.contains(candidate.service().id()))
                .flatMap(candidate -> candidate.service().options().stream().flatMap(option -> option.slots().stream()
                        .filter(slot -> !excludedSlots.contains(slot.id()))
                        .map(slot -> new Choice(candidate.service(), option, slot, candidate.score()))))
                .sorted(Comparator.comparing((Choice choice) -> LocalDateTime.of(choice.slot().date(), choice.slot().start()))
                        .thenComparing(choice -> choice.option().partyTotal()).thenComparing(choice -> choice.service().id()))
                .limit(MAX_CHOICES).toList();
    }

    private Path extend(Path path, Choice choice, TravelContext context, BigDecimal budget,
                        Set<String> unresolved, Map<UUID, TravelWeatherPort.Assessment> weatherCache,
                        Map<String, Integer> rejectionCounts, List<String> requestedAreas,
                        Set<UUID> preferredServices, Set<UUID> preferredSlots) {
        LocalDate date = choice.slot().date();
        if (date.isBefore(context.from()) || date.isAfter(context.to())) {
            reject(rejectionCounts, "REJECTED_BY_DATE_RANGE");
            return null;
        }
        if (path.items().stream().anyMatch(item -> item.serviceId().equals(choice.service().id()))) {
            reject(rejectionCounts, "REJECTED_BY_DUPLICATE_SERVICE");
            return null;
        }
        if (path.items().stream().filter(item -> item.start().toLocalDate().equals(date)).count() >= context.maxActivities()) {
            reject(rejectionCounts, "REJECTED_BY_MAX_ACTIVITIES");
            return null;
        }
        BigDecimal total = path.total().add(choice.option().partyTotal());
        if (budget != null && total.compareTo(budget) > 0) {
            reject(rejectionCounts, "REJECTED_BY_BUDGET");
            return null;
        }
        LocalDateTime start = LocalDateTime.of(date, choice.slot().start());
        LocalDateTime end = LocalDateTime.of(date, DiscoverServicesUseCase.endTime(choice.slot().end(), choice.slot().start(), choice.service().durationMinutes()));
        if (!start.isAfter(LocalDateTime.now(TravelContext.ZONE))) {
            reject(rejectionCounts, "REJECTED_BY_TIME_WINDOW");
            return null;
        }
        if (start.toLocalTime().isBefore(context.dayStart()) || end.toLocalTime().isAfter(context.dayEnd()) || !end.isAfter(start)) {
            reject(rejectionCounts, "REJECTED_BY_TIME_WINDOW");
            return null;
        }
        Item before = path.items().stream().filter(item -> item.start().toLocalDate().equals(date) && !item.end().isAfter(start))
                .max(Comparator.comparing(Item::end)).orElse(null);
        Item after = path.items().stream().filter(item -> item.start().toLocalDate().equals(date) && !item.start().isBefore(end))
                .min(Comparator.comparing(Item::start)).orElse(null);
        if (path.items().stream().anyMatch(item -> start.isBefore(item.end()) && end.isAfter(item.start()))) {
            reject(rejectionCounts, "REJECTED_BY_OVERLAP");
            return null;
        }
        int transit = estimate(before == null ? context.latitude() : before.latitude(),
                before == null ? context.longitude() : before.longitude(), choice.service().latitude(), choice.service().longitude());
        if (transit < 0 && before != null) {
            unresolved.add("MISSING_TRANSFER_COORDINATES");
            reject(rejectionCounts, "REJECTED_BY_MISSING_COORDINATES");
            return null;
        }
        if (before != null && start.isBefore(before.end().plusMinutes(transit))) {
            reject(rejectionCounts, "REJECTED_BY_TRANSIT_TIME");
            return null;
        }
        if (before == null && context.latitude() != null && start.isBefore(LocalDateTime.of(date, context.dayStart()).plusMinutes(Math.max(0, transit)))) {
            reject(rejectionCounts, "REJECTED_BY_TRANSIT_TIME");
            return null;
        }
        if (after != null) {
            int nextTransit = estimate(choice.service().latitude(), choice.service().longitude(), after.latitude(), after.longitude());
            if (nextTransit < 0) {
                unresolved.add("MISSING_TRANSFER_COORDINATES");
                reject(rejectionCounts, "REJECTED_BY_MISSING_COORDINATES");
                return null;
            }
            if (end.plusMinutes(nextTransit).isAfter(after.start())) {
                reject(rejectionCounts, "REJECTED_BY_TRANSIT_TIME");
                return null;
            }
        }
        long sensitiveChecks = weatherCache.values().stream().filter(value -> value != null && !"NOT_REQUIRED".equals(value.status())).count();
        if (choice.service().weatherSensitive() && !weatherCache.containsKey(choice.slot().id()) && sensitiveChecks >= 20) {
            unresolved.add("WEATHER_CHECK_LIMIT_PARTIAL_COVERAGE");
            reject(rejectionCounts, "REJECTED_BY_WEATHER_CHECK_LIMIT");
            return null;
        }
        var assessment = weatherCache.computeIfAbsent(choice.slot().id(), ignored -> weather.assess(choice.service(), choice.slot()));
        boolean waitingForForecast = assessment != null && "WAITING_FOR_FORECAST".equals(assessment.status());
        if (assessment == null || (!assessment.acceptable() && !waitingForForecast)) {
            unresolved.add(assessment == null ? "WEATHER_UNKNOWN" : "WEATHER_" + assessment.status());
            if (assessment == null || "UNKNOWN".equals(assessment.status()) || "STALE".equals(assessment.status())) {
                reject(rejectionCounts, "REJECTED_BY_WEATHER_UNKNOWN");
            } else {
                reject(rejectionCounts, "REJECTED_BY_WEATHER_UNSAFE");
            }
            return null;
        }
        if (waitingForForecast) unresolved.add("WAITING_FOR_FORECAST_NO_SAFETY_AUTHORIZATION");
        List<Item> items = new ArrayList<>(path.items());
        items.add(new Item(choice.service().id(), choice.option().id(), choice.slot().id(), choice.service().name(), start, end,
                choice.option().quantity(), choice.option().unitPrice(), choice.option().partyTotal(), choice.service().latitude(),
                choice.service().longitude(), Math.max(0, transit), assessment.status(), assessment.provisional() || waitingForForecast, assessment.message()));
        items.sort(Comparator.comparing(Item::start));
        Set<String> areas = new HashSet<>(path.coveredAreas());
        if (requestedAreas.size() >= 2) {
            String primary = TravelQueryParser.primaryArea(choice.service().name(), choice.service().address(),
                    choice.service().description(), requestedAreas);
            if (primary != null) areas.add(primary);
        }
        int preferredHitDelta = (preferredSlots.contains(choice.slot().id()) ? 2 : 0)
                + (preferredServices.contains(choice.service().id()) ? 1 : 0);
        return new Path(List.copyOf(items), total, path.relevance() + choice.relevance(), Set.copyOf(areas),
                path.preferredHits() + preferredHitDelta);
    }

    private static void reject(Map<String, Integer> rejectionCounts, String reason) {
        rejectionCounts.merge(reason, 1, Integer::sum);
    }

    private int estimate(Double fromLat, Double fromLon, Double toLat, Double toLon) {
        if (fromLat == null || fromLon == null || toLat == null || toLon == null) return -1;
        return 15 + (int) Math.ceil(TravelQueryParser.distanceKm(fromLat, fromLon, toLat, toLon) / 25 * 60);
    }

    private List<Path> trim(List<Path> paths, int limit, int requestedAreaCount) {
        Set<String> seen = new HashSet<>();
        Comparator<Path> comparator = Comparator.comparingInt((Path path) -> path.items().size()).reversed();
        if (requestedAreaCount >= 2) {
            comparator = comparator.thenComparing(Comparator.comparingInt((Path path) -> path.coveredAreas().size()).reversed());
        }
        comparator = comparator
                .thenComparing(Comparator.comparingInt(Path::preferredHits).reversed())
                .thenComparing(Path::total)
                .thenComparing(Comparator.comparingDouble(Path::relevance).reversed())
                .thenComparing(this::fingerprint);
        return paths.stream().sorted(comparator)
                .filter(path -> seen.add(fingerprint(path))).limit(limit).toList();
    }

    private List<Path> distinctAlternatives(List<Path> paths, int requestedAreaCount) {
        List<Path> choices = new ArrayList<>(trim(paths, BEAM_WIDTH, requestedAreaCount));
        List<Path> selected = new ArrayList<>();
        selected.add(choices.getFirst());
        choices.removeFirst();
        Comparator<Path> secondary = Comparator.comparingDouble(Path::relevance).reversed();
        if (requestedAreaCount >= 2) {
            secondary = Comparator.comparingInt((Path path) -> path.coveredAreas().size()).reversed().thenComparing(secondary);
        }
        choices.stream().filter(path -> !path.items().isEmpty()).sorted(secondary.thenComparing(Path::total))
                .limit(2).forEach(selected::add);
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
