package com.danasea.backend.modules.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.danasea.backend.modules.ai.application.dtos.TravelRequest;
import com.danasea.backend.modules.ai.application.ports.DecisionModelPort;
import com.danasea.backend.modules.ai.application.ports.ItineraryStorePort;
import com.danasea.backend.modules.ai.application.ports.TravelDataPort;
import com.danasea.backend.modules.ai.application.ports.TravelWeatherPort;
import com.danasea.backend.modules.ai.application.usecases.DiscoverServicesUseCase;
import com.danasea.backend.modules.ai.application.usecases.PlanItineraryUseCase;
import com.danasea.backend.modules.ai.domain.exceptions.AiResourceNotFoundException;
import com.danasea.backend.modules.ai.domain.exceptions.AiStateConflictException;
import com.danasea.backend.modules.ai.domain.models.DecisionResult;
import com.danasea.backend.modules.ai.domain.models.ItineraryPlan;
import com.danasea.backend.modules.ai.domain.models.ItineraryPlan.Proposal;
import com.danasea.backend.modules.ai.domain.models.ItineraryPlan.Saved;
import com.danasea.backend.modules.ai.domain.models.TravelContext;
import com.danasea.backend.modules.service.application.api.AiCatalogReadApi.Option;
import com.danasea.backend.modules.service.application.api.AiCatalogReadApi.PublishedService;
import com.danasea.backend.modules.service.application.api.AiCatalogReadApi.Slot;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import java.util.ArrayList;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class ItineraryPlannerUpgradeTest {
    private final TravelDataPort data = mock(TravelDataPort.class);
    private final DecisionModelPort decisions = mock(DecisionModelPort.class);
    private final TravelWeatherPort weather = mock(TravelWeatherPort.class);
    private final ItineraryStorePort store = mock(ItineraryStorePort.class);
    private final LocalDate tomorrow = LocalDate.now(TravelContext.ZONE).plusDays(1);
    private final UUID owner = UUID.randomUUID();
    private PlanItineraryUseCase planner;

    @BeforeEach void setup() {
        planner = new PlanItineraryUseCase(new DiscoverServicesUseCase(data, decisions, weather), weather, store);
        when(decisions.decide(any(), any())).thenAnswer(call -> DecisionResult.unavailable(call.getArgument(0), "TEST_OFFLINE"));
        when(weather.assess(any(), any())).thenReturn(new TravelWeatherPort.Assessment("NOT_REQUIRED", true, false, null, null, null));
    }

    private TravelContext context(LocalDate end, int activities, BigDecimal budget) {
        return new TravelContext("", null, budget, 1, tomorrow, end, LocalTime.of(8,0), LocalTime.of(18,0),
                null, null, null, List.of(), 15, activities, false);
    }

    private PublishedService service(String name, LocalDate date, int start, int end, String price) {
        Slot slot = new Slot(UUID.randomUUID(), date, LocalTime.of(start,0), LocalTime.of(end,0), 10);
        Option option = new Option(UUID.randomUUID(), name, "PER_PERSON", new BigDecimal(price), 1,
                new BigDecimal(price), "", List.of(slot));
        return new PublishedService(UUID.randomUUID(), UUID.randomUUID(), name, "Coastal trip", null, "Water", "water",
                "Da Nang", 16.1, 108.2, 60, false, null, 0, List.of(option));
    }

    @Test void activityLimitAppliesToEachDayAndTransfersDoNotCarryAcrossDays() {
        var first = service("First", tomorrow, 9, 10, "100");
        var sameDay = service("Second", tomorrow, 12, 13, "100");
        var nextDay = service("Third", tomorrow.plusDays(1), 9, 10, "100");
        when(data.search(any())).thenReturn(List.of(first, sameDay, nextDay));
        var plan = planner.propose(context(tomorrow.plusDays(1), 1, null), Set.of(), Set.of(), SupportedLanguage.VI);
        assertThat(plan.items()).hasSize(2);
        assertThat(plan.items()).extracting(item -> item.start().toLocalDate()).containsExactly(tomorrow, tomorrow.plusDays(1));
        assertThat(plan.items().get(1).estimatedTravelMinutes()).isZero();
        assertThat(plan.totalPrice()).isEqualByComparingTo("200");
    }

    @Test void alternativeSearchCanSkipCheapEarlySlotThatBlocksTwoLaterActivities() {
        var blocking = service("Long cheap", tomorrow, 9, 12, "50");
        var first = service("Short first", tomorrow, 9, 10, "60");
        var second = service("Short second", tomorrow, 11, 12, "60");
        when(data.search(any())).thenReturn(List.of(blocking, first, second));
        var alternatives = planner.alternatives(context(tomorrow, 3, new BigDecimal("200")), Set.of(), Set.of(), List.of(), SupportedLanguage.VI);
        assertThat(alternatives).hasSizeBetween(2, 3);
        assertThat(alternatives.getFirst().plan().items()).extracting(ItineraryPlan.Item::serviceId).containsExactly(first.id(), second.id());
        assertThat(alternatives.stream().map(value -> value.plan().items()).distinct()).hasSameSizeAs(alternatives);
        assertThat(alternatives.getFirst().plan().unresolvedConstraints()).contains("MEALS_AND_TRANSFER_COSTS_NOT_INCLUDED");
        assertThat(alternatives.getFirst().plan().inventoryReserved()).isFalse();
    }

    @Test void weatherBeyondHorizonCanBeTentativeButCannotBeAccepted() {
        var service = service("Future kayak", tomorrow, 9, 10, "100");
        when(data.search(any())).thenReturn(List.of(service));
        when(data.find(eq(service.id()), any())).thenReturn(service);
        when(weather.assess(any(), any())).thenReturn(new TravelWeatherPort.Assessment("WAITING_FOR_FORECAST", false, true, null, null, null));
        var plan = planner.propose(context(tomorrow, 3, null), Set.of(), Set.of(), SupportedLanguage.VI);
        assertThat(plan.status()).isEqualTo("WAITING_FOR_FORECAST");
        assertThat(plan.items().getFirst().provisionalWeather()).isTrue();
        var saved = new Saved(UUID.randomUUID(), owner, 0, plan, OffsetDateTime.now(), OffsetDateTime.now());
        when(store.find(saved.id(), owner)).thenReturn(Optional.of(saved));
        assertThatThrownBy(() -> planner.accept(saved.id(), owner, 0, SupportedLanguage.VI)).isInstanceOf(AiStateConflictException.class);
        verify(store, never()).transition(any(), any(), anyLong(), any(), any());
    }

    @Test void replanReturnsProposalAndCannotForgeWeatherEventOrOverwriteCurrentSchedule() {
        var service = service("Old kayak", tomorrow, 9, 10, "100");
        when(data.search(any())).thenReturn(List.of(service));
        ItineraryPlan plan = planner.propose(context(tomorrow, 3, null), Set.of(), Set.of(), SupportedLanguage.VI);
        Saved saved = new Saved(UUID.randomUUID(), owner, 4, plan, OffsetDateTime.now(), OffsetDateTime.now(), "ACCEPTED", false);
        when(store.find(saved.id(), owner)).thenReturn(Optional.of(saved));
        when(store.propose(eq(saved.id()), eq(owner), eq(4L), any(), eq("CUSTOMER_REQUEST"), eq(null)))
                .thenAnswer(call -> new Proposal(UUID.randomUUID(), saved.id(), 4, "PENDING", call.getArgument(3),
                        List.of(), List.of(), List.of(), "CUSTOMER_REQUEST", null, OffsetDateTime.now(), false));
        var result = planner.replan(saved.id(), owner, 4, null, Set.of(), Set.of(), "WEATHER_RED", SupportedLanguage.VI);
        assertThat(result.itinerary()).isEqualTo(saved);
        assertThat(result.proposal().state()).isEqualTo("PENDING");
        assertThat(result.trigger()).isEqualTo("CUSTOMER_REQUEST");
        assertThat(result.bookingChanged()).isFalse();
        verify(store, never()).replace(any(), any(), anyLong(), any());
    }

    @Test void authenticSourceEventPreservesUnaffectedItemsAndDeduplicates() {
        var impacted = service("Impacted", tomorrow, 9, 10, "100");
        var unaffected = service("Unaffected", tomorrow, 12, 13, "100");
        when(data.search(any())).thenReturn(List.of(impacted, unaffected));
        ItineraryPlan plan = planner.propose(context(tomorrow, 3, null), Set.of(), Set.of(), SupportedLanguage.VI);
        Saved saved = new Saved(UUID.randomUUID(), owner, 2, plan, OffsetDateTime.now(), OffsetDateTime.now(), "ACCEPTED", false);
        Saved stale = new Saved(saved.id(), owner, 3, plan, saved.createdAt(), saved.updatedAt(), "STALE", false);
        UUID impactedSlot = impacted.options().getFirst().slots().getFirst().id();
        when(store.trackedForSlot(impactedSlot)).thenReturn(List.of(saved));
        when(store.transition(saved.id(), owner, 2, "STALE", "CANONICAL_SLOT_UNAVAILABLE")).thenReturn(stale);
        when(data.search(any())).thenReturn(List.of());
        planner.sourceChanged(impactedSlot, "SLOT:canonical:1", "CANONICAL_SLOT_UNAVAILABLE");
        verify(store).propose(eq(saved.id()), eq(owner), eq(3L), ArgumentMatchers.argThat(next ->
                next.items().equals(plan.items().stream().filter(item -> !item.slotId().equals(impactedSlot)).toList())),
                eq("CANONICAL_SLOT_UNAVAILABLE"), eq("SLOT:canonical:1"));
        when(store.hasSourceEvent(saved.id(), "SLOT:canonical:1")).thenReturn(true);
        planner.sourceChanged(impactedSlot, "SLOT:canonical:1", "CANONICAL_SLOT_UNAVAILABLE");
        verify(store, Mockito.times(1)).transition(any(), any(), anyLong(), any(), any());
    }

    @Test void ongoingItineraryPreservesPastItemsAndOnlySearchesRemainingDates() {
        var futureService = service("Remaining", tomorrow, 9, 10, "100");
        var current = context(tomorrow, 3, null);
        var pastContext = new TravelContext("", null, null, 1, tomorrow.minusDays(2), tomorrow, LocalTime.of(8,0), LocalTime.of(18,0),
                null, null, null, List.of(), 15, 3, false);
        var past = new ItineraryPlan.Item(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Completed",
                tomorrow.minusDays(2).atTime(9,0), tomorrow.minusDays(2).atTime(10,0), 1, new BigDecimal("100"), new BigDecimal("100"),
                16.1, 108.2, 0, "NOT_REQUIRED", false, null);
        when(data.search(any())).thenReturn(List.of(futureService));
        var futurePlan = planner.propose(current, Set.of(), Set.of(), SupportedLanguage.VI);
        var items = new ArrayList<>(futurePlan.items());
        items.addFirst(past);
        var original = new ItineraryPlan("PROPOSED", pastContext, new BigDecimal("200"), List.copyOf(items), List.of(), "TEST", false);
        var saved = new Saved(UUID.randomUUID(), owner, 0, original, OffsetDateTime.now(), OffsetDateTime.now(), "ACCEPTED", false);
        when(store.find(saved.id(), owner)).thenReturn(Optional.of(saved));
        when(store.propose(eq(saved.id()), eq(owner), eq(0L), any(), any(), eq(null))).thenAnswer(call ->
                new Proposal(UUID.randomUUID(), saved.id(), 0, "PENDING", call.getArgument(3), List.of(), List.of(), List.of(), "CUSTOMER_REQUEST", null, OffsetDateTime.now(), false));
        var result = planner.replan(saved.id(), owner, 0, null, Set.of(), Set.of(), "CHANGE", SupportedLanguage.VI);
        assertThat(result.proposal().plan().items()).contains(past);
        assertThat(result.proposal().plan().context()).isEqualTo(pastContext);
        verify(data).search(ArgumentMatchers.argThat(query -> query.from().equals(LocalDate.now(TravelContext.ZONE))));
    }

    @Test void acceptedQuoteRejectsChangedSlotEndOrCoordinates() {
        var service = service("Kayak", tomorrow, 9, 10, "100");
        when(data.search(any())).thenReturn(List.of(service));
        var original = planner.propose(context(tomorrow, 3, null), Set.of(), Set.of(), SupportedLanguage.VI);
        var saved = new Saved(UUID.randomUUID(), owner, 0, original, OffsetDateTime.now(), OffsetDateTime.now());
        when(store.find(saved.id(), owner)).thenReturn(Optional.of(saved));
        var option = service.options().getFirst();
        var slot = option.slots().getFirst();
        var changedSlot = new Slot(slot.id(), slot.date(), slot.start(), LocalTime.of(11,0), 10);
        var changedOption = new Option(option.id(), option.name(), option.pricingUnit(), option.unitPrice(), option.quantity(), option.partyTotal(), option.benefits(), List.of(changedSlot));
        when(data.find(eq(service.id()), any())).thenReturn(new PublishedService(service.id(), service.vendorId(), service.name(), service.description(),
                service.categoryId(), service.categoryName(), service.categorySlug(), service.address(), service.latitude(), service.longitude(),
                service.durationMinutes(), service.weatherSensitive(), service.averageRating(), service.reviewCount(), List.of(changedOption)));
        assertThatThrownBy(() -> planner.accept(saved.id(), owner, 0, SupportedLanguage.VI)).isInstanceOf(AiStateConflictException.class);
        verify(store, never()).transition(any(), any(), anyLong(), any(), any());
    }

    private PublishedService locatedService(String name, String address, Double lat, Double lon,
                                            LocalDate date, int startHour, int startMinute, int endHour, int endMinute,
                                            String price, boolean weatherSensitive) {
        Slot slot = new Slot(UUID.randomUUID(), date, LocalTime.of(startHour, startMinute), LocalTime.of(endHour, endMinute), 10);
        Option option = new Option(UUID.randomUUID(), name, "PER_PERSON", new BigDecimal(price), 1,
                new BigDecimal(price), "", List.of(slot));
        return new PublishedService(UUID.randomUUID(), UUID.randomUUID(), name, name, null, "Marine", "marine",
                address, lat, lon, (endHour * 60 + endMinute) - (startHour * 60 + startMinute), weatherSensitive, null, 0, List.of(option));
    }

    @Test void rejectionDiagnosticsTrackPerReasonCountsAndTimingTelemetry() {
        var base = service("Valid", tomorrow, 9, 10, "100");
        var overlap = service("Overlap", tomorrow, 9, 10, "100");
        var missingCoords = locatedService("NoCoords", "Da Nang", null, null, tomorrow, 11, 0, 12, 0, "100", false);
        var unknownWeather = locatedService("UnknownWeather", "Da Nang", 16.1, 108.2, tomorrow, 13, 0, 14, 0, "100", true);
        var unsafeWeather = locatedService("UnsafeWeather", "Da Nang", 16.1, 108.2, tomorrow, 15, 0, 16, 0, "100", true);
        var overBudget = service("OverBudget", tomorrow, 17, 18, "200");

        when(data.search(any())).thenReturn(List.of(base, overlap, missingCoords, unknownWeather, unsafeWeather, overBudget));
        when(weather.assess(eq(unknownWeather), any())).thenReturn(new TravelWeatherPort.Assessment("UNKNOWN", false, false, null, "No data", null));
        when(weather.assess(eq(unsafeWeather), any())).thenReturn(new TravelWeatherPort.Assessment("UNSAFE", false, false, "RED", "High waves", "FULL"));

        var alternatives = planner.alternatives(context(tomorrow, 3, new BigDecimal("250")), Set.of(), Set.of(), List.of(), SupportedLanguage.VI);
        var diagnostics = planner.lastDiagnostics();
        assertThat(diagnostics).isNotNull();
        assertThat(diagnostics.choicesEvaluated()).isEqualTo(6);
        assertThat(diagnostics.planningDurationMs()).isGreaterThanOrEqualTo(0L);
        assertThat(diagnostics.rejectionCounts())
                .containsKeys("REJECTED_BY_OVERLAP", "REJECTED_BY_MISSING_COORDINATES",
                        "REJECTED_BY_WEATHER_UNKNOWN", "REJECTED_BY_WEATHER_UNSAFE", "REJECTED_BY_BUDGET");
        assertThat(alternatives.getFirst().tradeOffs())
                .anyMatch(entry -> entry.startsWith("REJECTION_COUNTS:"))
                .anyMatch(entry -> entry.startsWith("PLANNING_DURATION_MS:"))
                .anyMatch(entry -> entry.startsWith("CHOICES_EVALUATED:6"));
    }

    @Test void multiAreaManThaiAndSonTraPairsDistinctAreasAndEnforcesTransitGap() {
        var manThaiSup = locatedService("Chèo SUP Mân Thái", "Mân Thái, Sơn Trà, Đà Nẵng", 16.086, 108.250,
                tomorrow, 8, 30, 9, 30, "200", false);
        var manThaiExtra = locatedService("Chèo Kayak Mân Thái", "Mân Thái, Sơn Trà, Đà Nẵng", 16.087, 108.251,
                tomorrow, 10, 30, 11, 30, "150", false);
        var sonTraTooClose = locatedService("Trải nghiệm biển Sơn Trà Gần Giờ", "Bán đảo Sơn Trà, Đà Nẵng", 16.1067, 108.2774,
                tomorrow, 9, 35, 10, 35, "250", false);
        var sonTraCompatible = locatedService("Khám phá biển Sơn Trà", "Bán đảo Sơn Trà, Đà Nẵng", 16.1067, 108.2774,
                tomorrow, 10, 30, 11, 30, "250", false);

        when(data.search(any())).thenReturn(List.of(manThaiSup, manThaiExtra, sonTraTooClose, sonTraCompatible));
        var ctx = new TravelRequest("Tôi muốn vừa chèo SUP ở Mân Thái vừa trải nghiệm biển Sơn Trà, hãy sắp xếp hợp lý",
                null, null, 2, tomorrow, tomorrow, null, null, null, null, null, null, null, 2, null).context();

        var alternatives = planner.alternatives(ctx, Set.of(), Set.of(), List.of(), SupportedLanguage.VI);
        assertThat(alternatives.getFirst().plan().items()).extracting(ItineraryPlan.Item::serviceId)
                .containsExactly(manThaiSup.id(), sonTraCompatible.id());
        assertThat(alternatives.getFirst().plan().items().get(1).estimatedTravelMinutes()).isGreaterThanOrEqualTo(15);
        assertThat(planner.lastDiagnostics().rejectionCounts()).containsKey("REJECTED_BY_TRANSIT_TIME");
    }

    @Test void maxActivitiesFourSelectsFourDistinctServicesInOneDay() {
        var s1 = service("S1", tomorrow, 8, 9, "100");
        var s2 = service("S2", tomorrow, 10, 11, "100");
        var s3 = service("S3", tomorrow, 12, 13, "100");
        var s4 = service("S4", tomorrow, 14, 15, "100");
        var s5 = service("S5", tomorrow, 16, 17, "100");
        when(data.search(any())).thenReturn(List.of(s1, s2, s3, s4, s5));
        var ctx = new TravelRequest("Trong một ngày, chọn tối đa 4 hoạt động nhưng không lặp lại cùng dịch vụ.",
                null, null, 1, tomorrow, tomorrow, null, null, null, null, null, null, null, null, null).context();
        var plan = planner.propose(ctx, Set.of(), Set.of(), SupportedLanguage.VI);
        assertThat(plan.items()).hasSize(4);
        assertThat(plan.items().stream().map(ItineraryPlan.Item::serviceId).distinct().count()).isEqualTo(4);
        assertThat(planner.lastDiagnostics().rejectionCounts()).containsKey("REJECTED_BY_MAX_ACTIVITIES");
    }

    @Test void replanWithExcludedSlotPreservesUnaffectedItemsAndReplanItemToWindowMovesSecondItemToAfternoon() {
        var morningFirst = locatedService("Chèo SUP sáng", "Mân Thái", 16.086, 108.250, tomorrow, 8, 30, 9, 30, "100", false);
        var slotMorningSecond = new Slot(UUID.randomUUID(), tomorrow, LocalTime.of(10, 0), LocalTime.of(11, 0), 10);
        var slotAfternoonSecond = new Slot(UUID.randomUUID(), tomorrow, LocalTime.of(14, 0), LocalTime.of(15, 0), 10);
        Option secondOpt = new Option(UUID.randomUUID(), "Lặn Sơn Trà", "PER_PERSON", new BigDecimal("150"), 1,
                new BigDecimal("150"), "", List.of(slotMorningSecond, slotAfternoonSecond));
        var secondService = new PublishedService(UUID.randomUUID(), UUID.randomUUID(), "Lặn Sơn Trà", "Lặn biển",
                null, "Marine", "marine", "Sơn Trà", 16.1067, 108.2774, 60, false, null, 0, List.of(secondOpt));
        var replacement = locatedService("Thuyền thúng", "Mân Thái", 16.088, 108.252, tomorrow, 10, 0, 11, 0, "120", false);

        when(data.search(any())).thenReturn(List.of(morningFirst, secondService, replacement));
        ItineraryPlan initialPlan = planner.propose(context(tomorrow, 2, null), Set.of(), Set.of(), SupportedLanguage.VI);
        Saved saved = new Saved(UUID.randomUUID(), owner, 1L, initialPlan, OffsetDateTime.now(), OffsetDateTime.now(), "ACCEPTED", false);
        when(store.find(saved.id(), owner)).thenReturn(Optional.of(saved));
        when(store.propose(eq(saved.id()), eq(owner), eq(1L), any(), eq("CUSTOMER_REQUEST"), eq(null)))
                .thenAnswer(call -> new Proposal(UUID.randomUUID(), saved.id(), 1L, "PENDING", call.getArgument(3),
                        List.of(), List.of(), List.of(), "CUSTOMER_REQUEST", null, OffsetDateTime.now(), false));

        // U29: exclude cancelled diving slot -> preserves morningFirst and replaces slotMorningSecond
        var u29 = planner.replan(saved.id(), owner, 1L, null, Set.of(), Set.of(slotMorningSecond.id()), "CANCELLED", SupportedLanguage.VI);
        assertThat(u29.proposal().plan().items()).extracting(ItineraryPlan.Item::serviceId)
                .contains(morningFirst.id())
                .doesNotContain(secondService.id());

        // U30: move 2nd item (10:00-11:00) to afternoon (13:00-18:00) -> keeps morningFirst and picks slotAfternoonSecond
        var u30 = planner.replanItemToWindow(saved.id(), owner, 1L, 2, LocalTime.of(13, 0), LocalTime.of(18, 0),
                null, Set.of(), Set.of(), SupportedLanguage.VI);
        assertThat(u30.proposal().plan().items()).hasSize(2);
        assertThat(u30.proposal().plan().items().get(0).serviceId()).isEqualTo(morningFirst.id());
        assertThat(u30.proposal().plan().items().get(1).slotId()).isEqualTo(slotAfternoonSecond.id());
        assertThat(u30.proposal().plan().items().get(1).start().toLocalTime()).isEqualTo(LocalTime.of(14, 0));
    }

    @Test void restoreFromRevisionRevalidatesLiveSlotsAndCreatesPendingProposal() {
        var serviceA = service("SUP Mân Thái", tomorrow, 9, 10, "100");
        var serviceB = service("Kayak Sơn Trà", tomorrow, 11, 12, "120");
        when(data.search(any())).thenReturn(List.of(serviceA, serviceB));
        ItineraryPlan rev0Plan = planner.propose(context(tomorrow, 1, null), Set.of(), Set.of(), SupportedLanguage.VI);
        // rev0 has serviceA (1 item, cheapest)
        assertThat(rev0Plan.items()).extracting(ItineraryPlan.Item::serviceId).containsExactly(serviceA.id());

        ItineraryPlan currentPlan = planner.propose(context(tomorrow, 2, null), Set.of(), Set.of(), SupportedLanguage.VI);
        Saved saved = new Saved(UUID.randomUUID(), owner, 2L, currentPlan, OffsetDateTime.now(), OffsetDateTime.now(), "ACCEPTED", false);
        when(store.find(saved.id(), owner)).thenReturn(Optional.of(saved));
        when(store.revisions(saved.id(), owner)).thenReturn(List.of(
                new ItineraryPlan.Revision(0L, "DRAFT", rev0Plan, "DRAFT_CREATED", null, OffsetDateTime.now().minusHours(2)),
                new ItineraryPlan.Revision(2L, "ACCEPTED", currentPlan, "PROPOSAL_ACCEPTED", UUID.randomUUID(), OffsetDateTime.now())
        ));
        when(store.propose(eq(saved.id()), eq(owner), eq(2L), any(), eq("CUSTOMER_RESTORE_REVISION"), eq(null)))
                .thenAnswer(call -> new Proposal(UUID.randomUUID(), saved.id(), 2L, "PENDING", call.getArgument(3),
                        List.of(), List.of(), List.of(), "CUSTOMER_RESTORE_REVISION", null, OffsetDateTime.now(), false));

        Proposal restored = planner.restoreFromRevision(saved.id(), owner, 2L, 0L, SupportedLanguage.VI);
        assertThat(restored.state()).isEqualTo("PENDING");
        assertThat(restored.trigger()).isEqualTo("CUSTOMER_RESTORE_REVISION");
        assertThat(restored.plan().items()).extracting(ItineraryPlan.Item::serviceId).containsExactly(serviceA.id());
        assertThat(restored.plan().unresolvedConstraints()).contains("RESTORED_FROM_REVISION:0");

        assertThatThrownBy(() -> planner.restoreFromRevision(saved.id(), owner, 2L, 99L, SupportedLanguage.VI))
                .isInstanceOf(AiResourceNotFoundException.class);
        verify(store, never()).replace(any(), any(), anyLong(), any());
    }
}
