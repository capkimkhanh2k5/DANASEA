package com.danasea.backend.modules.ai;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.danasea.backend.modules.ai.application.ports.*;
import com.danasea.backend.modules.ai.application.usecases.*;
import com.danasea.backend.modules.ai.domain.exceptions.AiResourceNotFoundException;
import com.danasea.backend.modules.ai.domain.models.*;
import com.danasea.backend.modules.ai.domain.services.TravelQueryParser;
import com.danasea.backend.modules.operation.application.api.AiReviewReadApi.ReviewEvidence;
import com.danasea.backend.modules.service.application.api.AiCatalogReadApi.*;
import com.danasea.backend.shared.i18n.SupportedLanguage;

class CustomerAiFeaturesTest {
    private final TravelDataPort data = mock(TravelDataPort.class);
    private final DecisionModelPort model = mock(DecisionModelPort.class);
    private final TravelWeatherPort weather = mock(TravelWeatherPort.class);
    private final ItineraryStorePort store = mock(ItineraryStorePort.class);
    private final LocalDate date = LocalDate.now(TravelContext.ZONE).plusDays(1);
    private final UUID owner = UUID.randomUUID();
    private DiscoverServicesUseCase discovery;
    private PlanItineraryUseCase planner;

    @BeforeEach void setup() {
        discovery = new DiscoverServicesUseCase(data, model, weather);
        planner = new PlanItineraryUseCase(discovery, weather, store);
        when(model.decide(any(), any())).thenAnswer(call -> DecisionResult.unavailable(call.getArgument(0), "TEST_UNAVAILABLE"));
    }

    private TravelContext context(String query, BigDecimal budget) {
        return new TravelContext(query, null, budget, 3, date, date, null, null,
                null, null, null, List.of(), 10, 3, false);
    }
    private PublishedService service(String name, String price, int hour, boolean slots) {
        Slot slot = new Slot(UUID.randomUUID(), date, LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0), 10);
        Option option = new Option(UUID.randomUUID(), "Package", "PER_PACKAGE", new BigDecimal(price), 2,
                new BigDecimal(price).multiply(BigDecimal.TWO), "Guide", slots ? List.of(slot) : List.of());
        return new PublishedService(UUID.randomUUID(), UUID.randomUUID(), name, "Coastal trip", null, "Water sports", "water",
                "Da Nang", 16.1, 108.2, 60, false, new BigDecimal("4.5"), 3, List.of(option));
    }
    private DecisionResult noul(DecisionTask task, String key, double value) {
        return new DecisionResult(true, task.wireName(), "test", "test", "test", 1,
                Map.of(key, new DecisionResult.Answer("noul", null, null, value, null, Map.of())), null, true);
    }

    @Test void pricesAndAvailabilityRemainHardConstraintsWhenModelScoresEverythingHighly() {
        var cheap = service("Kayak", "100000", 9, true);
        when(data.search(any())).thenReturn(List.of(service("Expensive", "400000", 9, true), cheap,
                service("Unavailable", "10000", 9, false)));
        doReturn(new DecisionResult(true, "relevance", "test", "test", "test", 1,
                Map.of("relevance", new DecisionResult.Answer("score", null, 1.0, null, 3.0, Map.of())), null, true)).when(model).decide(eq(DecisionTask.RELEVANCE), any());
        var result = discovery.execute(context("", new BigDecimal("300000")), "RECOMMENDATION", SupportedLanguage.VI);
        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().getFirst().service().id()).isEqualTo(cheap.id());
        assertThat(result.candidates().getFirst().minimumPartyTotal()).isEqualByComparingTo("200000");
        verify(model, times(1)).decide(eq(DecisionTask.RELEVANCE), any());
    }
    @Test void perPersonBudgetIsConvertedToTotalAndExplicitTotalWins() {
        when(data.search(any())).thenReturn(List.of());
        assertThat(discovery.execute(context("dưới 200k mỗi người", null), "SEARCH", SupportedLanguage.VI).effectiveTotalBudget())
                .isEqualByComparingTo("600000");
        assertThat(discovery.execute(context("dưới 200k mỗi người", new BigDecimal("100000")), "SEARCH", SupportedLanguage.VI).effectiveTotalBudget())
                .isEqualByComparingTo("100000");
    }
    @Test void nearbyRequiresCoordinatesBeforeCatalogOrInference() {
        var result = discovery.execute(context("", null), "NEARBY", SupportedLanguage.VI);
        assertThat(result.status()).isEqualTo("NEEDS_INPUT");
        verifyNoInteractions(data, weather);
        verify(model, never()).decide(any(), any());
    }
    @Test void negativeActivityIsExcludedAndMultiActivityQueryDoesNotSelectArbitrarySingleCategory() {
        assertThat(TravelQueryParser.activityKeyword("SUP và kayak")).isNull();
        assertThat(TravelQueryParser.activityKeyword("Không SUP, tìm kayak")).isEqualTo("kayak");
        when(data.search(any())).thenReturn(List.of(service("SUP", "100000", 9, true), service("Kayak", "100000", 9, true)));
        assertThat(discovery.execute(context("không SUP", null), "SEARCH", SupportedLanguage.VI).candidates())
                .extracting(c -> c.service().name()).containsExactly("Kayak");
    }
    @Test void parserHandlesVietnameseCurrencyAndGroup() {
        assertThat(TravelQueryParser.budget("ngân sách 1.000.000 VND")).isEqualByComparingTo("1000000");
        assertThat(TravelQueryParser.budget("dưới 1,5 triệu")).isEqualByComparingTo("1500000");
        assertThat(TravelQueryParser.partySize("3 người ngày mai")).isEqualTo(3);
        assertThat(TravelQueryParser.date("ngày mai")).isEqualTo(date);
    }
    @Test void nonFiniteCoordinatesAndNullInterestsAreRejected() {
        assertThatThrownBy(() -> new TravelContext("", null, null, 1, date, date, null, null,
                Double.NaN, 108.0, null, List.of(), 10, 3, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TravelContext("", null, null, 1, date, date, null, null,
                null, null, null, java.util.Arrays.asList((String) null), 10, 3, false)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void itineraryHonorsAggregateBudgetOverlapAndTransferTime() {
        var first = service("First", "100000", 9, true);
        var overlap = service("Overlap", "100000", 9, true);
        var second = service("Second", "100000", 11, true);
        var third = service("Third", "100000", 13, true);
        when(data.search(any())).thenReturn(List.of(first, overlap, second, third));
        when(weather.assess(any(), any())).thenReturn(new TravelWeatherPort.Assessment("NOT_REQUIRED", true, false, null, null, null));
        var plan = planner.propose(context("", new BigDecimal("400000")), Set.of(), Set.of(), SupportedLanguage.VI);
        assertThat(plan.items()).hasSize(2);
        assertThat(plan.totalPrice()).isEqualByComparingTo("400000");
        assertThat(plan.items().get(1).start()).isAfterOrEqualTo(plan.items().getFirst().end().plusMinutes(15));
        assertThat(plan.inventoryReserved()).isFalse();
    }
    @Test void unknownWeatherBlocksPlanAndProvisionalWeatherIsExplicit() {
        var svc = service("Kayak", "100000", 9, true);
        when(data.search(any())).thenReturn(List.of(svc));
        when(weather.assess(any(), any())).thenReturn(new TravelWeatherPort.Assessment("UNKNOWN", false, false, null, null, null));
        assertThat(planner.propose(context("", null), Set.of(), Set.of(), SupportedLanguage.VI).items()).isEmpty();
        when(weather.assess(any(), any())).thenReturn(new TravelWeatherPort.Assessment("ACCEPTABLE", true, true, "YELLOW", "Estimated", "ESTIMATED"));
        assertThat(planner.propose(context("", null), Set.of(), Set.of(), SupportedLanguage.VI).status()).isEqualTo("PROVISIONAL");
    }
    @Test void privateReplanRejectsForeignIdBeforeReadingCatalog() {
        UUID id = UUID.randomUUID();
        when(store.find(id, owner)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> planner.replan(id, owner, 0, context("", null), Set.of(), Set.of(), "CHANGE", SupportedLanguage.VI))
                .isInstanceOf(AiResourceNotFoundException.class);
        verifyNoInteractions(data, weather);
        verify(store, never()).replace(any(), any(), anyLong(), any());
    }
    @Test void historicalPlanCanBeReadButCannotBeUsedForNewSearch() {
        var past = new TravelContext("", null, null, 1, date.minusDays(3), date.minusDays(3), null, null,
                null, null, null, List.of(), 10, 3, false);
        assertThatThrownBy(past::validateCurrentDates).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void supportChecksOrderOwnershipBeforeModelOrCancellationPreview() {
        var orders = mock(CustomerOrderReadPort.class);
        var support = new CustomerSupportUseCase(orders, model);
        UUID id = UUID.randomUUID();
        when(orders.read(owner, id)).thenThrow(new AccessDeniedException("Foreign order"));
        assertThatThrownBy(() -> support.execute(owner, id, "Refund this", true)).isInstanceOf(AccessDeniedException.class);
        verify(model, never()).decide(any(), any());
        verify(orders, never()).previewCancellation(any(), any());
    }
    @Test void summariesUseRealRatingCountsAndNeverInventReviewsWhenEmpty() {
        var selector = mock(ReviewHighlightPort.class);
        var summary = new ReviewSummaryUseCase(data, model, selector);
        UUID id = UUID.randomUUID();
        when(data.reviews(id, 50)).thenReturn(List.of());
        assertThat(summary.execute(id, SupportedLanguage.VI).status()).isEqualTo("NO_REVIEWS");
        verifyNoInteractions(selector);
        var good = new ReviewEvidence(UUID.randomUUID(), id, 5, "Good guide", OffsetDateTime.now());
        var bad = new ReviewEvidence(UUID.randomUUID(), id, 1, "Late", OffsetDateTime.now());
        when(data.reviews(id, 50)).thenReturn(List.of(good, bad));
        when(data.reviewCount(id)).thenReturn(100L);
        when(selector.select(any(), any())).thenReturn(List.of());
        var result = summary.execute(id, SupportedLanguage.VI);
        assertThat(result.sampledAverageRating()).isEqualByComparingTo("3");
        assertThat(result.positiveExamples()).containsExactly(good);
        assertThat(result.negativeExamples()).containsExactly(bad);
        assertThat(result.exhaustive()).isFalse();
        assertThat(result.highlights()).containsExactly(good, bad);
    }
    @Test void backendRiskSignalsCannotBeClearedByModelNo() {
        var facts = mock(TransactionRiskReadPort.class);
        var cases = mock(AssessmentCaseStorePort.class);
        UUID id = UUID.randomUUID();
        when(facts.read(id)).thenReturn(new TransactionRiskReadPort.Facts(id, owner, 3, 1, 0, "PENDING"));
        doReturn(noul(DecisionTask.RISK, "needs_review", 0.01)).when(model).decide(eq(DecisionTask.RISK), any());
        new AnalyzeTransactionRiskUseCase(facts, model, cases).execute(owner, id, "");
        verify(cases).create(eq("TRANSACTION_RISK"), eq(id), eq(owner), eq("NEEDS_REVIEW"),
                argThat(e -> Boolean.FALSE.equals(e.get("fraudConfirmed"))), any());
    }
    @Test void moderationFallsBackToReviewAndContactRuleSurvivesModelFailure() {
        var cases = mock(AssessmentCaseStorePort.class);
        var result = new AssessTextUseCase(model, cases).execute(owner, "Call 0901234567", true);
        assertThat(result.reasonCodes()).contains("EXTERNAL_CONTACT_DETECTED", "MODEL_UNAVAILABLE");
        assertThat(result.status()).isEqualTo("NEEDS_REVIEW");
        assertThat(result.publicationAuthorized()).isFalse();
        assertThat(result.evidenceType()).isEqualTo("TEXT_ONLY");
    }
    @Test void nearbyUsesExactRadiusRatherThanOnlyBoundingBox() {
        var near = service("Near", "100000", 9, true);
        var far = new PublishedService(UUID.randomUUID(), near.vendorId(), "Far", "Trip", null, "Water", "water",
                "Far", 17.0, 109.0, 60, false, null, 0, near.options());
        var context = new TravelContext("", null, null, 3, date, date, null, null, 16.1, 108.2, 1.0,
                List.of(), 10, 3, false);
        when(data.search(any())).thenReturn(List.of(far, near));
        assertThat(discovery.execute(context, "NEARBY", SupportedLanguage.VI).candidates())
                .extracting(c -> c.service().id()).containsExactly(near.id());
    }
    @Test void weatherAdviceCannotUseAnotherOptionOrAnUnavailableSlot() {
        var service = service("Kayak", "100000", 9, true);
        when(data.find(eq(service.id()), any())).thenReturn(service);
        var advisor = new WeatherAwareUseCase(discovery, weather);
        assertThatThrownBy(() -> advisor.execute(service.id(), UUID.randomUUID(), service.options().getFirst().slots().getFirst().id(),
                context("", null), SupportedLanguage.VI)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> advisor.execute(service.id(), service.options().getFirst().id(), UUID.randomUUID(),
                context("", null), SupportedLanguage.VI)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(weather);
    }


    @Test void paymentWarningDoesNotTriggerModelOnlyFlagsButSolicitationStillDoes() {
        var cases = mock(AssessmentCaseStorePort.class);
        doReturn(noul(DecisionTask.MODERATION, "external_payment", 0.68))
                .when(model).decide(eq(DecisionTask.MODERATION), any());
        var useCase = new AssessTextUseCase(model, cases);
        assertThat(useCase.execute(owner, "Không chuyển tiền ngoài DANASEA.", false).reasonCodes()).isEmpty();
        assertThat(useCase.execute(owner, "Vui lòng chuyển tiền ngoài DANASEA.", false).status()).isEqualTo("NEEDS_REVIEW");
        assertThat(useCase.execute(owner, "Không chuyển tiền ngoài DANASEA. Gọi 0901234567", false).reasonCodes())
                .contains("EXTERNAL_CONTACT_DETECTED");
    }
}
