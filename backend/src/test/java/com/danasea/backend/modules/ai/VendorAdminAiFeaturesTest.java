package com.danasea.backend.modules.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.danasea.backend.modules.ai.application.ports.AdminAiDataPort;
import com.danasea.backend.modules.ai.application.ports.AdminAiDataPort.DisputeFullEvidence;
import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort;
import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort.CopilotConversation;
import com.danasea.backend.modules.ai.application.ports.DecisionModelPort;
import com.danasea.backend.modules.ai.application.ports.LlmClientPort;
import com.danasea.backend.modules.ai.application.ports.ModerationPort;
import com.danasea.backend.modules.ai.application.ports.TravelWeatherPort;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort.VendorProfile;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort.VendorReviewItem;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort.VendorSlotItem;
import com.danasea.backend.modules.ai.application.usecases.AdminDisputeSummaryUseCase;
import com.danasea.backend.modules.ai.application.usecases.AssessTextUseCase;
import com.danasea.backend.modules.ai.application.usecases.CustomerMarineBriefingUseCase;
import com.danasea.backend.modules.ai.application.usecases.CustomerMarineBriefingUseCase.MarineBriefingOverview;
import com.danasea.backend.modules.ai.application.usecases.MarineSlotWeather;
import com.danasea.backend.modules.ai.application.usecases.RoleCopilotUseCase;
import com.danasea.backend.modules.ai.application.usecases.RoleCopilotUseCase.CopilotChatResponse;
import com.danasea.backend.modules.ai.application.usecases.VendorListingOptimizerUseCase;
import com.danasea.backend.modules.ai.application.usecases.VendorListingOptimizerUseCase.OptimizeRequest;
import com.danasea.backend.modules.ai.application.usecases.VendorListingOptimizerUseCase.OptimizeResult;
import com.danasea.backend.modules.ai.application.usecases.VendorReviewCoachingUseCase;
import com.danasea.backend.modules.ai.application.usecases.VendorReviewCoachingUseCase.CoachingResult;
import com.danasea.backend.modules.ai.application.usecases.VendorReviewCoachingUseCase.ReplyDraftResult;
import com.danasea.backend.modules.ai.application.usecases.VendorSlotAdvisorUseCase;
import com.danasea.backend.modules.ai.application.usecases.VendorSlotAdvisorUseCase.SlotAdvisorResult;
import com.danasea.backend.modules.ai.domain.models.DecisionResult;
import com.danasea.backend.modules.ai.domain.models.DecisionTask;
import com.danasea.backend.modules.ai.domain.models.LlmResponse;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.application.api.AiCatalogReadApi;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class VendorAdminAiFeaturesTest {

    private final VendorAiDataPort vendorAiData = mock(VendorAiDataPort.class);
    private final AdminAiDataPort adminAiData = mock(AdminAiDataPort.class);
    private final AiExtendedInsightStorePort insightStore = mock(AiExtendedInsightStorePort.class);
    private final DecisionModelPort decisionModel = mock(DecisionModelPort.class);
    private final LlmClientPort llmClient = mock(LlmClientPort.class);
    private final ModerationPort moderationPort = mock(ModerationPort.class);
    private final AssessTextUseCase assessTextUseCase = mock(AssessTextUseCase.class);
    private final AiCatalogReadApi catalog = mock(AiCatalogReadApi.class);
    private final TravelWeatherPort weatherPort = mock(TravelWeatherPort.class);

    private final JpaMasterOrderRepository masterOrderRepo = mock(JpaMasterOrderRepository.class);
    private final JpaSubOrderRepository subOrderRepo = mock(JpaSubOrderRepository.class);
    private final JpaServiceRepository serviceRepo = mock(JpaServiceRepository.class);
    private final JpaServiceSlotRepository slotRepo = mock(JpaServiceSlotRepository.class);

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private VendorReviewCoachingUseCase reviewCoachingUseCase;
    private VendorListingOptimizerUseCase listingOptimizerUseCase;
    private VendorSlotAdvisorUseCase slotAdvisorUseCase;
    private AdminDisputeSummaryUseCase disputeSummaryUseCase;
    private RoleCopilotUseCase roleCopilotUseCase;
    private CustomerMarineBriefingUseCase marineBriefingUseCase;

    private final UUID vendorUserId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final UUID adminUserId = UUID.randomUUID();
    private final UUID customerUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reviewCoachingUseCase = new VendorReviewCoachingUseCase(
                vendorAiData, insightStore, decisionModel, llmClient, assessTextUseCase, mapper);

        listingOptimizerUseCase = new VendorListingOptimizerUseCase(
                vendorAiData, insightStore, decisionModel, llmClient, assessTextUseCase, mapper);

        var marineWeather = new MarineSlotWeather(catalog, weatherPort);
        slotAdvisorUseCase = new VendorSlotAdvisorUseCase(
                vendorAiData, marineWeather, insightStore, mapper);

        disputeSummaryUseCase = new AdminDisputeSummaryUseCase(
                adminAiData, insightStore, mapper);

        roleCopilotUseCase = new RoleCopilotUseCase(
                vendorAiData, reviewCoachingUseCase, slotAdvisorUseCase, listingOptimizerUseCase,
                adminAiData, disputeSummaryUseCase, insightStore, llmClient, moderationPort, mapper);

        marineBriefingUseCase = new CustomerMarineBriefingUseCase(
                masterOrderRepo, subOrderRepo, serviceRepo, slotRepo, marineWeather, insightStore, mapper);

        // Default Mocks
        when(moderationPort.isSafe(anyString())).thenReturn(true);
        when(decisionModel.decide(any(), any())).thenAnswer(call ->
                DecisionResult.unavailable((DecisionTask) call.getArgument(0), "FALLBACK"));

        LlmResponse mockLlm = new LlmResponse();
        mockLlm.setContent("Trợ lý AI phản hồi tự nhiên và chuẩn xác.");
        mockLlm.setGeneratedTextVerified(false);
        when(llmClient.generateResponse(any(), any())).thenReturn(mockLlm);
    }

    // ==================== 1. VENDOR REVIEW COACHING TESTS ====================

    @Test
    @DisplayName("Vendor Review Coaching phân tích 4 trụ cột và đề xuất lộ trình thăng hạng huy hiệu")
    void testVendorReviewCoaching_AnalyzesFourPillarsAndBadges() {
        var profile = new VendorProfile(vendorId, vendorUserId, "Danasea Watersports", BigDecimal.valueOf(4.7), 25, "SILVER");
        when(vendorAiData.findProfileByUserId(vendorUserId)).thenReturn(Optional.of(profile));

        var rev1 = new VendorReviewItem(UUID.randomUUID(), UUID.randomUUID(), "SUP Sơn Trà",
                5, "Hướng dẫn viên nhiệt tình, chu đáo và thân thiện!", OffsetDateTime.now());
        var rev2 = new VendorReviewItem(UUID.randomUUID(), UUID.randomUUID(), "SUP Sơn Trà",
                3, "Chờ xuất bến hơi lâu trễ mất 15 phút, nhưng áo phao an toàn sạch sẽ.", OffsetDateTime.now());

        when(vendorAiData.listReviews(eq(vendorId), any(), anyInt())).thenReturn(List.of(rev1, rev2));

        CoachingResult result = reviewCoachingUseCase.execute(vendorUserId, null, LocalDate.now().minusDays(90), LocalDate.now());

        assertThat(result).isNotNull();
        assertThat(result.vendorId()).isEqualTo(vendorId);
        assertThat(result.businessName()).isEqualTo("Danasea Watersports");
        assertThat(result.totalReviewsAnalyzed()).isEqualTo(2);

        // Verify 4 pillars present
        assertThat(result.pillars()).containsKeys("GUIDE", "SAFETY", "PUNCTUALITY", "VALUE");
        assertThat(result.pillars().get("GUIDE").positiveQuotes()).isNotEmpty();
        assertThat(result.badgeProgression()).isNotNull();
        assertThat(result.badgeProgression().currentTier()).isEqualTo("SILVER");

        verify(insightStore).saveVendorInsight(eq(vendorId), any(), eq("REVIEW_COACHING"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Vendor Smart Review Reply sinh bản nháp phản hồi thấu cảm cho khách")
    void testVendorSmartReviewReply_GeneratesEmpatheticReply() {
        var profile = new VendorProfile(vendorId, vendorUserId, "Danasea Watersports", BigDecimal.valueOf(4.7), 25, "SILVER");
        when(vendorAiData.findProfileByUserId(vendorUserId)).thenReturn(Optional.of(profile));

        UUID reviewId = UUID.randomUUID();
        var rev = new VendorReviewItem(reviewId, UUID.randomUUID(), "SUP Sơn Trà",
                2, "Cano đón trễ và không thông báo trước.", OffsetDateTime.now());
        when(vendorAiData.listReviews(eq(vendorId), any(), anyInt())).thenReturn(List.of(rev));

        LlmResponse replyLlm = new LlmResponse();
        replyLlm.setContent("Dạ Danasea Watersports xin chân thành cảm ơn quý khách đã phản hồi.");
        when(llmClient.generateResponse(any(), any())).thenReturn(replyLlm);

        ReplyDraftResult draft = reviewCoachingUseCase.draftReply(vendorUserId, reviewId, "EMPATHETIC", "thời gian chờ cano");

        assertThat(draft).isNotNull();
        assertThat(draft.reviewId()).isEqualTo(reviewId);
        assertThat(draft.rating()).isEqualTo(2);
        assertThat(draft.draftedReply()).isNotEmpty();
        assertThat(draft.draftedReply()).contains("Danasea Watersports");
    }

    // ==================== 2. VENDOR LISTING OPTIMIZER TESTS ====================

    @Test
    @DisplayName("Vendor Listing Optimizer phát hiện thiếu quy định an toàn và chấm điểm bài đăng")
    void testVendorListingOptimizer_DetectsMissingSafetyAndDrafts() {
        var profile = new VendorProfile(vendorId, vendorUserId, "Danasea Watersports", BigDecimal.valueOf(4.7), 25, "SILVER");
        when(vendorAiData.findProfileByUserId(vendorUserId)).thenReturn(Optional.of(profile));

        OptimizeRequest req = new OptimizeRequest(
                UUID.randomUUID(),
                "Thuê sup",
                "Cho thuê sup bãi biển có chèo",
                List.of("Ván mới"),
                List.of("Ván sup"),
                List.of("VI", "EN")
        );

        OptimizeResult result = listingOptimizerUseCase.execute(vendorUserId, req);

        assertThat(result).isNotNull();
        assertThat(result.vendorId()).isEqualTo(vendorId);
        // Missing critical items should be identified (e.g. áo phao, leash an toàn)
        assertThat(result.missingItems()).isNotEmpty();
        assertThat(result.drafts()).containsKey("VI");
        assertThat(result.drafts().get("VI").optimizedTitle()).containsIgnoringCase("sup");
        assertThat(result.listingQualityScore()).isLessThan(80); // Short raw input scores lower
    }

    // ==================== 3. VENDOR SLOT ADVISOR TESTS ====================

    @Test
    @DisplayName("Vendor Slot Advisor kiểm tra thời tiết trước khi đề xuất tăng công suất")
    void testVendorSlotAdvisor_RequiresForecastBeforeRecommendingExpansion() {
        var profile = new VendorProfile(vendorId, vendorUserId, "Danasea Watersports", BigDecimal.valueOf(4.7), 25, "SILVER");
        when(vendorAiData.findProfileByUserId(vendorUserId)).thenReturn(Optional.of(profile));

        UUID slotId = UUID.randomUUID();
        UUID srvId = UUID.randomUUID();
        // Slot at 95% capacity
        var slotItem = new VendorSlotItem(slotId, srvId, "SUP Bình Minh",
                LocalDate.now().plusDays(2), LocalTime.of(5, 0), LocalTime.of(7, 0),
                20, 19, "OPEN");

        when(vendorAiData.listUpcomingSlots(eq(vendorId), any(), anyInt())).thenReturn(List.of(slotItem));

        SlotAdvisorResult advisor = slotAdvisorUseCase.execute(vendorUserId, srvId, 7);

        assertThat(advisor).isNotNull();
        assertThat(advisor.totalSlotsEvaluated()).isEqualTo(1);
        assertThat(advisor.highDemandSlotsCount()).isEqualTo(1);
        assertThat(advisor.recommendations()).hasSize(1);
        assertThat(advisor.recommendations().get(0).weatherRiskLevel()).isEqualTo("UNKNOWN");
        assertThat(advisor.recommendations().get(0).recommendationType()).isEqualTo("WEATHER_WATCH");

        var published = new AiCatalogReadApi.PublishedService(srvId, vendorId, "SUP Bình Minh", null,
                null, null, null, null, 16.1, 108.2, 120, true, BigDecimal.ZERO, 0, List.of());
        when(catalog.metadata(eq(srvId), any())).thenReturn(published);
        when(weatherPort.assess(eq(published), any())).thenReturn(
                new TravelWeatherPort.Assessment("UNSAFE", false, false, null, null, null),
                new TravelWeatherPort.Assessment("ACCEPTABLE", true, false, null, null, null));
        var unsafe = slotAdvisorUseCase.execute(vendorUserId, srvId, 7);
        assertThat(unsafe.weatherRiskSlotsCount()).isEqualTo(1);
        assertThat(unsafe.recommendations().getFirst().weatherRiskLevel()).isEqualTo("HIGH_RISK");
        assertThat(unsafe.recommendations().getFirst().recommendationType()).isEqualTo("WEATHER_WATCH");

        var safe = slotAdvisorUseCase.execute(vendorUserId, srvId, 7);
        assertThat(safe.recommendations().getFirst().recommendationType()).isEqualTo("EXPAND_CAPACITY");
    }

    // ==================== 4. ADMIN DISPUTE ARBITRATION TESTS ====================

    @Test
    @DisplayName("Admin Dispute Arbitration chuyển quản trị viên duyệt khi bằng chứng chưa đủ")
    void testAdminDisputeArbitration_RequiresReviewForConflictingClaims() {
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID subOrderId = UUID.randomUUID();

        // System truths: checkinUsed == false (QR never scanned), weather == SAFE, policy == strict no show
        DisputeFullEvidence evidence = new DisputeFullEvidence(
                disputeId, orderId, subOrderId, customerUserId, "Nguyễn Văn Khách",
                "Khách khiếu nại cano không đến đón",
                "Tôi đã chờ 30 phút ở bãi biển nhưng không thấy ai.",
                List.of(), "OPEN", OffsetDateTime.now().minusHours(2),
                UUID.randomUUID(), "Tour Cano Sơn Trà", vendorId, "Danasea Watersports",
                BigDecimal.valueOf(1500000), 2, UUID.randomUUID(), LocalDate.now(), "08:00 - 10:00", "COMPLETED",
                false, null, // checkinUsed = false, checkinUsedAt = null
                "Chúng tôi đã cho nhân viên ra đợi nhưng khách không bắt máy.", List.of(), OffsetDateTime.now().minusHours(1)
        );

        when(adminAiData.getDisputeFullEvidence(disputeId)).thenReturn(Optional.of(evidence));

        var result = disputeSummaryUseCase.execute(adminUserId, disputeId);

        assertThat(result).isNotNull();
        assertThat(result.disputeId()).isEqualTo(disputeId);
        assertThat(result.customerClaimSummary()).isNotEmpty();
        assertThat(result.vendorDefenseSummary()).isNotEmpty();
        assertThat(result.objectiveSystemFacts()).hasSize(3);
        assertThat(result.recommendedResolution()).isEqualTo("MANUAL_REVIEW_REQUIRED");
        assertThat(result.recommendedRefundPercent()).isNull();
        assertThat(result.draftAdminNote()).doesNotContain("50%");

        verify(insightStore).saveDisputeSummary(eq(disputeId), eq(subOrderId), eq(adminUserId), eq("MANUAL_REVIEW_REQUIRED"), isNull(), anyString(), anyString());

        // Verify English (EN) language support
        var resultEn = disputeSummaryUseCase.execute(adminUserId, disputeId, SupportedLanguage.EN);
        assertThat(resultEn.objectiveSystemFacts().get(0)).contains("CHECK-IN VERIFICATION");
        assertThat(resultEn.draftAdminNote()).contains("review");
    }

    // ==================== 5. ROLE COPILOT MULTI-SESSION TESTS ====================

    @Test
    @DisplayName("Role Copilot quản lý vòng đời đa phiên hội thoại cho Vendor và Admin")
    void testRoleCopilot_MultiSessionLifecycleAndToolDispatch() {
        UUID convId = UUID.randomUUID();
        var sampleConv = new CopilotConversation(convId, vendorUserId, "VENDOR", vendorId,
                "vi", "Kế hoạch kinh doanh tuần này", false, false, null, null,
                1L, OffsetDateTime.now(), OffsetDateTime.now());

        when(insightStore.createCopilotConversation(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(sampleConv);
        when(insightStore.getCopilotConversation(convId)).thenReturn(Optional.of(sampleConv));
        when(insightStore.listCopilotConversations(eq(vendorUserId), eq("VENDOR"), eq(vendorId), any(), anyInt()))
                .thenReturn(List.of(sampleConv));

        // 1. List conversations
        List<CopilotConversation> convs = roleCopilotUseCase.listConversations(vendorUserId, "VENDOR", vendorId, false, 20);
        assertThat(convs).hasSize(1);
        assertThat(convs.get(0).title()).isEqualTo("Kế hoạch kinh doanh tuần này");

        // 2. Chat with tool execution
        CopilotChatResponse chatResp = roleCopilotUseCase.processMessage(
                vendorUserId, "VENDOR", vendorId, convId, "Hôm nay doanh thu thế nào?", null, null, SupportedLanguage.VI);

        assertThat(chatResp).isNotNull();
        assertThat(chatResp.status()).isEqualTo("success");
        assertThat(chatResp.role()).isEqualTo("ASSISTANT");
        assertThat(chatResp.conversationId()).isEqualTo(convId);

        // 3. Update meta
        roleCopilotUseCase.updateConversationMeta(convId, vendorUserId, "VENDOR", vendorId, "Đổi tên chat", true, false);
        verify(insightStore).updateCopilotConversationMeta(convId, vendorUserId, "Đổi tên chat", true, false);

        // 4. Soft delete
        roleCopilotUseCase.deleteConversation(convId, vendorUserId, "VENDOR", vendorId);
        verify(insightStore).softDeleteCopilotConversation(convId, vendorUserId);
    }

    @Test
    void testRoleCopilot_DeniesPreviousAdminConversationAfterRoleChange() {
        UUID convId = UUID.randomUUID();
        var adminConversation = new CopilotConversation(convId, vendorUserId, "ADMIN", null,
                "vi", "Admin reports", false, false, null, null, 1L, OffsetDateTime.now(), OffsetDateTime.now());
        when(insightStore.getCopilotConversation(convId)).thenReturn(Optional.of(adminConversation));

        assertThatThrownBy(() -> roleCopilotUseCase.processMessage(
                vendorUserId, "VENDOR", vendorId, convId, "show prior reports", null, null, SupportedLanguage.EN))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ==================== 6. CUSTOMER MARINE BRIEFING & IDOR GUARD TESTS ====================

    @Test
    @DisplayName("Customer Marine Briefing chuẩn bị checklist an toàn và bảo vệ IDOR")
    void testCustomerMarineBriefing_EnforcesOwnershipAndBuildsChecklist() {
        UUID orderId = UUID.randomUUID();
        UUID subOrderId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
        ReflectionTestUtils.setField(masterOrder, "id", orderId);
        masterOrder.setCustomerId(customerUserId);

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setMasterOrderId(orderId);
        subOrder.setServiceId(serviceId);
        subOrder.setSlotId(slotId);

        ServiceJpaEntity service = new ServiceJpaEntity();
        service.setName("Tour Lặn Ngắm San Hô Bán Đảo");
        service.setAddress("Bến tàu Sông Hàn Đà Nẵng");

        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setDate(LocalDate.now().plusDays(1));
        slot.setStartTime(LocalTime.of(8, 0));

        when(masterOrderRepo.findById(orderId)).thenReturn(Optional.of(masterOrder));
        when(subOrderRepo.findByMasterOrderId(orderId)).thenReturn(List.of(subOrder));
        when(serviceRepo.findById(serviceId)).thenReturn(Optional.of(service));
        when(slotRepo.findById(slotId)).thenReturn(Optional.of(slot));

        // 1. Success case with owner
        MarineBriefingOverview overview = marineBriefingUseCase.getMarineBriefing(customerUserId, orderId);
        assertThat(overview).isNotNull();
        assertThat(overview.customerId()).isEqualTo(customerUserId);
        assertThat(overview.activities()).hasSize(1);

        var activity = overview.activities().get(0);
        assertThat(activity.serviceTitle()).isEqualTo("Tour Lặn Ngắm San Hô Bán Đảo");
        assertThat(activity.weatherSafetyStatus()).isEqualTo("UNKNOWN");
        assertThat(activity.packingChecklist()).isNotEmpty();
        assertThat(activity.arrivalAndSafetyGuidelines()).isNotEmpty();

        // 2. IDOR Guard: other user attempts to read
        UUID attackerUserId = UUID.randomUUID();
        assertThatThrownBy(() -> marineBriefingUseCase.getMarineBriefing(attackerUserId, orderId))
                .isInstanceOf(AccessDeniedException.class);
    }
}
