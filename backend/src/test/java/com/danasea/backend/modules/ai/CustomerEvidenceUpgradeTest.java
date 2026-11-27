package com.danasea.backend.modules.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.danasea.backend.modules.ai.application.api.CustomerPreferenceReadApi.Signals;
import com.danasea.backend.modules.ai.application.ports.CustomerOrderReadPort;
import com.danasea.backend.modules.ai.application.ports.CustomerPreferenceStorePort;
import com.danasea.backend.modules.ai.application.ports.CustomerSupportRequestStorePort;
import com.danasea.backend.modules.ai.application.ports.DecisionModelPort;
import com.danasea.backend.modules.ai.application.ports.ReviewHighlightPort;
import com.danasea.backend.modules.ai.application.ports.TravelDataPort;
import com.danasea.backend.modules.ai.application.usecases.CustomerPreferenceUseCase;
import com.danasea.backend.modules.ai.application.usecases.CustomerSupportRequestUseCase;
import com.danasea.backend.modules.ai.application.usecases.CustomerSupportUseCase;
import com.danasea.backend.modules.ai.application.usecases.ReviewSummaryUseCase;
import com.danasea.backend.modules.ai.domain.exceptions.AiResourceNotFoundException;
import com.danasea.backend.modules.ai.domain.exceptions.AiStateConflictException;
import com.danasea.backend.modules.ai.domain.models.DecisionTask;
import com.danasea.backend.modules.operation.application.api.AiReviewReadApi;
import com.danasea.backend.modules.operation.application.api.AiReviewReadApi.ReviewEvidence;
import com.danasea.backend.modules.operation.application.api.AiReviewReadApi.ReviewSnapshot;
import com.danasea.backend.modules.order.application.dtos.CancellationPreviewResult;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.domain.services.CustomerRefundEligibilityPolicy;
import com.danasea.backend.modules.service.application.api.CustomerServiceActivityReadApi;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.databind.ObjectMapper;

class CustomerEvidenceUpgradeTest {
    private final UUID owner = UUID.randomUUID(), service = UUID.randomUUID(), order = UUID.randomUUID(), recommendation = UUID.randomUUID();
    private final CustomerPreferenceStorePort preferenceStore = mock(CustomerPreferenceStorePort.class);
    private final CustomerServiceActivityReadApi activity = mock(CustomerServiceActivityReadApi.class);
    private final CustomerPreferenceUseCase preferences = new CustomerPreferenceUseCase(preferenceStore, activity);
    private final CustomerOrderReadPort orders = mock(CustomerOrderReadPort.class);
    private final CustomerSupportRequestStorePort requests = mock(CustomerSupportRequestStorePort.class);
    private final CustomerSupportRequestUseCase workflow = new CustomerSupportRequestUseCase(orders, requests, new ObjectMapper().findAndRegisterModules());

    @Test void consentDisabledReadsNoPrivateBehaviorSignals() {
        when(preferenceStore.read(owner)).thenReturn(new CustomerPreferenceStorePort.Profile(owner, 0, false, List.of("kayak"), List.of(), null));
        assertThat(preferences.load(owner)).isEqualTo(Signals.disabled());
        verifyNoInteractions(activity); verify(preferenceStore, never()).recentFeedback(any(), anyInt());
    }
    @Test void consentEnabledUsesRealHistoryAndOnlyLatestExplicitPreferenceVote() {
        when(preferenceStore.read(owner)).thenReturn(new CustomerPreferenceStorePort.Profile(owner, 0, true, List.of("kayak"), List.of("diving"), null));
        when(activity.read(owner)).thenReturn(new CustomerServiceActivityReadApi.Activity(Set.of(service), Set.of()));
        when(preferenceStore.recentFeedback(owner, 100)).thenReturn(List.of(feedback("NEGATIVE", "new", "new"), feedback("POSITIVE", "old", "old")));
        var signals = preferences.load(owner);
        assertThat(signals.wishedServiceIds()).contains(service); assertThat(signals.negativeServiceIds()).contains(service);
        assertThat(signals.positiveServiceIds()).isEmpty();
    }
    @Test void preferenceUpdateRequiresExplicitConsentAndBoundedTerms() {
        assertThatThrownBy(() -> preferences.replace(owner, 0L, null, List.of(), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> preferences.replace(owner, 0L, true, List.of(" "), List.of())).isInstanceOf(IllegalArgumentException.class);
        verify(preferenceStore, never()).replace(any(), anyLong(), anyBoolean(), anyList(), anyList());
    }
    @Test void feedbackCannotAttachToForeignOrFabricatedResult() {
        when(preferenceStore.findRecommendation(recommendation, owner)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> preferences.feedback(owner, recommendation, service, "POSITIVE", "request-1")).isInstanceOf(AiResourceNotFoundException.class);
        verify(preferenceStore, never()).createFeedback(any(), any(), any(), anyString(), anyString(), anyString());
    }
    @Test void feedbackCannotClaimServiceAbsentFromActualResult() {
        when(preferenceStore.findRecommendation(recommendation, owner)).thenReturn(Optional.of(new CustomerPreferenceStorePort.Recommendation(recommendation, owner, List.of(UUID.randomUUID()), "criteria", OffsetDateTime.now().plusHours(1))));
        assertThatThrownBy(() -> preferences.feedback(owner, recommendation, service, "POSITIVE", "request-1")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void expiredRecommendationRefusesNewFeedback() {
        when(preferenceStore.findRecommendation(recommendation, owner)).thenReturn(Optional.of(new CustomerPreferenceStorePort.Recommendation(recommendation, owner, List.of(service), "criteria", OffsetDateTime.now().minusMinutes(1))));
        assertThatThrownBy(() -> preferences.feedback(owner, recommendation, service, "SHOWN", "request-1")).isInstanceOf(AiStateConflictException.class);
    }
    @Test void feedbackReplayReturnsOriginalWithoutCreatingAnotherSignal() {
        String hash = CustomerPreferenceUseCase.hash(recommendation + "|" + service + "|POSITIVE");
        var prior = feedback("POSITIVE", "request-1", hash);
        when(preferenceStore.findFeedback(owner, "request-1")).thenReturn(Optional.of(prior));
        assertThat(preferences.feedback(owner, recommendation, service, "POSITIVE", "request-1")).isSameAs(prior);
        verify(preferenceStore, never()).findRecommendation(any(), any());
    }
    @Test void feedbackIdempotencyCannotBeReusedForAnotherVote() {
        when(preferenceStore.findFeedback(owner, "request-1")).thenReturn(Optional.of(feedback("POSITIVE", "request-1", "different")));
        assertThatThrownBy(() -> preferences.feedback(owner, recommendation, service, "NEGATIVE", "request-1")).isInstanceOf(AiStateConflictException.class);
    }
    @Test void emittedRecommendationsAreBoundedAtFifteen() {
        assertThatThrownBy(() -> preferences.recordRecommendation(owner, IntStream.range(0, 16).mapToObj(value -> UUID.randomUUID()).toList(), "criteria")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void supportWorkflowChecksOrderOwnershipBeforeTargetEligibility() {
        when(orders.read(owner, order)).thenThrow(new AccessDeniedException("Foreign order"));
        assertThatThrownBy(() -> workflow.preview(owner, command(false))).isInstanceOf(AccessDeniedException.class);
        verify(orders, never()).eligibility(any(), any(), any()); verifyNoInteractions(requests);
    }
    @Test void supportRequestRequiresExplicitConfirmation() {
        assertThatThrownBy(() -> workflow.create(owner, command(false), "support-1")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(orders, requests);
    }
    @Test void supportChangeChecksLiveEligibilityBeforeCreatingReviewRequest() {
        when(orders.eligibility(owner, order, null)).thenReturn(new CustomerOrderReadPort.Eligibility(false, false, "CORE", List.of()));
        var change = new CustomerSupportRequestUseCase.Command(order, "CHANGE_REQUEST", "Change date", LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(3), null, true);
        assertThatThrownBy(() -> workflow.create(owner, change, "support-1")).isInstanceOf(AiStateConflictException.class);
        verify(requests, never()).create(any(), any(), anyString(), anyString(), any(), any(), anyString(), anyString());
    }
    @Test void supportRequestReplayDoesNotRevalidateOrMutatePaidOrder() throws Exception {
        var command = command(true);
        String hash = CustomerPreferenceUseCase.hash(new ObjectMapper().findAndRegisterModules().writeValueAsString(command));
        var prior = supportRequest(hash);
        when(requests.findByKey(owner, "support-1")).thenReturn(Optional.of(prior));
        assertThat(workflow.create(owner, command, "support-1")).isSameAs(prior); verifyNoInteractions(orders);
    }
    @Test void supportStatusIsPrivate() {
        assertThatThrownBy(() -> workflow.get(owner, UUID.randomUUID())).isInstanceOf(AiResourceNotFoundException.class);
    }
    @Test void closedOrderOffersHandoffWithoutInventingCancellationOrRescheduleEligibility() {
        var model = mock(DecisionModelPort.class);
        when(orders.read(owner, order)).thenReturn(new CustomerOrderReadPort.OwnedOrder(order, "CANCELLED", "REFUNDED", BigDecimal.TEN, List.of()));
        when(orders.eligibility(owner, order, null)).thenReturn(new CustomerOrderReadPort.Eligibility(false, false, "CORE", List.of("CLOSED")));
        var result = new CustomerSupportUseCase(orders, model).execute(owner, order, "Need help", false);
        assertThat(result.nextActions()).contains("CONTACT_SUPPORT").doesNotContain("REQUEST_CHANGE_WITH_CONFIRMATION", "REQUEST_REFUND_WITH_CONFIRMATION");
        assertThat(result.financialActionPerformed()).isFalse();
    }
    @Test void positiveCanonicalRefundPreviewProducesOnlyConfirmationAction() {
        var model = mock(DecisionModelPort.class);
        when(orders.read(owner, order)).thenReturn(new CustomerOrderReadPort.OwnedOrder(order, "PAID", "PAID", BigDecimal.TEN, List.of()));
        var preview = new CancellationPreviewResult(order, BigDecimal.TEN, BigDecimal.valueOf(100), BigDecimal.TEN, "POLICY", List.of());
        when(orders.previewCancellation(owner, order)).thenReturn(preview);
        when(orders.eligibility(owner, order, preview)).thenReturn(new CustomerOrderReadPort.Eligibility(true, true, "CORE", List.of()));
        var result = new CustomerSupportUseCase(orders, model).execute(owner, order, "Refund", true);
        assertThat(result.actionDetails()).anySatisfy(action -> { assertThat(action.code()).isEqualTo("REQUEST_REFUND_WITH_CONFIRMATION"); assertThat(action.requiresConfirmation()).isTrue(); assertThat(action.requiresIdempotencyKey()).isTrue(); });
        assertThat(result.cancellationPreview()).isSameAs(preview);
    }
    @Test void sharedRefundEligibilityPreservesExistingOrderAndItemStatuses() {
        for (MasterOrderStatus status : MasterOrderStatus.values()) assertThat(CustomerRefundEligibilityPolicy.paidOrder(status)).isEqualTo(Set.of(MasterOrderStatus.PAID, MasterOrderStatus.PARTIALLY_COMPLETED, MasterOrderStatus.COMPLETED).contains(status));
        for (SubOrderStatus status : SubOrderStatus.values()) assertThat(CustomerRefundEligibilityPolicy.refundableItem(status)).isEqualTo(!Set.of(SubOrderStatus.CANCELLED, SubOrderStatus.REFUNDED, SubOrderStatus.REJECTED).contains(status));
    }
    @Test void reviewAggregateCoversPopulationWhileQuotesRemainSampleScoped() {
        var data = mock(TravelDataPort.class); var model = mock(DecisionModelPort.class); var selector = mock(ReviewHighlightPort.class); var reads = mock(AiReviewReadApi.class);
        var good = new ReviewEvidence(UUID.randomUUID(), service, 5, "Good guide but expensive equipment", OffsetDateTime.now());
        when(reads.snapshot(service, 50)).thenReturn(new ReviewSnapshot(100, new BigDecimal("4.2"), 80, 5, Map.of(5, 60L, 4, 20L, 3, 15L, 2, 3L, 1, 2L), List.of(good), "hash", OffsetDateTime.now()));
        when(selector.select(any(), any())).thenReturn(List.of(UUID.randomUUID()));
        var result = new ReviewSummaryUseCase(data, model, selector, reads).execute(service, SupportedLanguage.EN);
        assertThat(result.publicAverageRating()).isEqualByComparingTo("4.2"); assertThat(result.positiveRatingCount()).isEqualTo(80);
        assertThat(result.sampledAverageRating()).isEqualByComparingTo("5"); assertThat(result.highlights()).containsExactly(good);
        assertThat(result.aspectEvidence()).extracting(ReviewSummaryUseCase.AspectEvidence::aspect).contains("GUIDE", "SAFETY", "VALUE");
        assertThat(result.aspectEvidence()).allSatisfy(evidence -> assertThat(evidence.exactQuote()).isEqualTo(good.comment()));
        assertThat(result.exhaustive()).isFalse();
    }
    @Test void reviewCacheRechecksPublicPermissionAndFingerprintBeforeReusingModelWork() {
        var data = mock(TravelDataPort.class); var model = mock(DecisionModelPort.class); var selector = mock(ReviewHighlightPort.class); var reads = mock(AiReviewReadApi.class);
        var source = new ReviewEvidence(UUID.randomUUID(), service, 5, "Guide", OffsetDateTime.now());
        when(reads.snapshot(service, 50)).thenReturn(new ReviewSnapshot(1, BigDecimal.valueOf(5), 1, 0, Map.of(5, 1L), List.of(source), "before", OffsetDateTime.now()));
        var useCase = new ReviewSummaryUseCase(data, model, selector, reads);
        assertThat(useCase.execute(service, SupportedLanguage.VI).highlights()).contains(source);
        assertThat(useCase.execute(service, SupportedLanguage.VI).highlights()).contains(source);
        when(reads.snapshot(service, 50)).thenReturn(new ReviewSnapshot(0, null, 0, 0, Map.of(), List.of(), "flagged-or-deleted", OffsetDateTime.now()));
        assertThat(useCase.execute(service, SupportedLanguage.VI).highlights()).isEmpty();
        verify(model).decide(eq(DecisionTask.REVIEW), any());
        verify(data, times(3)).metadata(service, SupportedLanguage.VI);
    }
    private CustomerPreferenceStorePort.Feedback feedback(String signal, String key, String hash) {
        return new CustomerPreferenceStorePort.Feedback(UUID.randomUUID(), recommendation, owner, service, signal, key, hash, OffsetDateTime.now());
    }
    private CustomerSupportRequestUseCase.Command command(boolean confirmed) { return new CustomerSupportRequestUseCase.Command(order, "HANDOFF", "Need human support", null, null, confirmed); }
    private CustomerSupportRequestStorePort.Request supportRequest(String hash) {
        return new CustomerSupportRequestStorePort.Request(UUID.randomUUID(), owner, order, "HANDOFF", "Need human support", null, null, "WAITING_REVIEW", 0, "support-1", hash, OffsetDateTime.now(), OffsetDateTime.now(), "HUMAN_REVIEW_REQUIRED", false, false, null, null, null);
    }
}
