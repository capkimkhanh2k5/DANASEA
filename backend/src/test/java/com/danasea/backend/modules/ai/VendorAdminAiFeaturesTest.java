package com.danasea.backend.modules.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.danasea.backend.modules.ai.application.ports.AdminAiDataPort;
import com.danasea.backend.modules.ai.application.ports.AdminAiDataPort.BookingCancellationBreakdown;
import com.danasea.backend.modules.ai.application.ports.AdminAiDataPort.DisputeFullEvidence;
import com.danasea.backend.modules.ai.application.ports.AdminAiDataPort.PlatformDashboardMetrics;
import com.danasea.backend.modules.ai.application.ports.AdminAiDataPort.RevenueTimeSeriesItem;
import com.danasea.backend.modules.ai.application.ports.AdminAiDataPort.VendorDiagnosticItem;
import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort;
import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort.CopilotConversation;
import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort.CopilotMessage;
import com.danasea.backend.modules.ai.application.ports.AssessmentCaseStorePort;
import com.danasea.backend.modules.ai.application.ports.DecisionModelPort;
import com.danasea.backend.modules.ai.application.ports.LlmClientPort;
import com.danasea.backend.modules.ai.application.ports.ModerationPort;
import com.danasea.backend.modules.ai.application.ports.TransactionRiskReadPort;
import com.danasea.backend.modules.ai.application.ports.TravelWeatherPort;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort.VendorProfile;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort.VendorReviewItem;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort.VendorServiceItem;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort.VendorSlotItem;
import com.danasea.backend.modules.ai.application.tools.AdminFeatureTool;
import com.danasea.backend.modules.ai.application.tools.CustomerFeatureTool;
import com.danasea.backend.modules.ai.application.tools.ToolExecutionContext;
import com.danasea.backend.modules.ai.application.tools.VendorFeatureTool;
import com.danasea.backend.modules.ai.application.usecases.AdminDisputeSummaryUseCase;
import com.danasea.backend.modules.ai.application.usecases.AnalyzeTransactionRiskUseCase;
import com.danasea.backend.modules.ai.application.usecases.AssessTextUseCase;
import com.danasea.backend.modules.ai.domain.models.AssessmentCase;
import com.danasea.backend.modules.ai.infrastructure.persistence.AssessmentCaseStoreAdapter;
import com.danasea.backend.modules.ai.infrastructure.persistence.entities.AiAssessmentCaseJpaEntity;
import com.danasea.backend.modules.ai.infrastructure.persistence.repositories.JpaAiAssessmentCaseRepository;
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
import com.danasea.backend.modules.ai.domain.models.ToolCall;
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

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

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
                vendorAiData, insightStore, decisionModel, assessTextUseCase, mapper);

        var marineWeather = new MarineSlotWeather(catalog, weatherPort);
        slotAdvisorUseCase = new VendorSlotAdvisorUseCase(
                vendorAiData, marineWeather, insightStore, mapper);

        disputeSummaryUseCase = new AdminDisputeSummaryUseCase(
                adminAiData, insightStore, mapper);

        roleCopilotUseCase = new RoleCopilotUseCase(
                vendorAiData, reviewCoachingUseCase, slotAdvisorUseCase, listingOptimizerUseCase,
                adminAiData, disputeSummaryUseCase, insightStore, llmClient, moderationPort, mapper,
                new com.danasea.backend.modules.ai.application.usecases.PolicyChatAnswer(
                        new com.danasea.backend.modules.order.application.api.AiPolicyReader(
                                new com.danasea.backend.modules.order.domain.services.RefundPolicyEngine())::current,
                        mock(com.danasea.backend.modules.order.application.usecases.GetCancellationPreviewUseCase.class),
                        com.danasea.backend.shared.i18n.LocalizedMessageService.standalone()));

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
        var profile = new VendorProfile(vendorId, vendorUserId, "Danasea Watersports", BigDecimal.valueOf(4.7), 25, "TOP_RATED");
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
        assertThat(result.badgeProgression().currentTier()).isEqualTo("TOP_RATED");

        verify(insightStore).saveVendorInsight(eq(vendorId), any(), eq("REVIEW_COACHING"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Vendor Smart Review Reply sinh bản nháp phản hồi thấu cảm cho khách")
    void testVendorSmartReviewReply_GeneratesEmpatheticReply() {
        var profile = new VendorProfile(vendorId, vendorUserId, "Danasea Watersports", BigDecimal.valueOf(4.7), 25, "TOP_RATED");
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

    @Test
    @DisplayName("Vendor Review Coaching không bịa điểm khi chưa có đánh giá và chặn truy cập service của vendor khác")
    void testVendorReviewCoaching_ZeroReviewsAndOwnershipCheck() {
        var profile = new VendorProfile(vendorId, vendorUserId, "Danabeach Club", BigDecimal.ZERO, 0, "NONE");
        when(vendorAiData.findProfileByUserId(vendorUserId)).thenReturn(Optional.of(profile));
        UUID ownedServiceId = UUID.randomUUID();
        when(vendorAiData.getService(vendorId, ownedServiceId)).thenReturn(Optional.of(
                new VendorAiDataPort.VendorServiceItem(ownedServiceId, vendorId, "New Cano", "desc", "Cano", null, 0)));
        when(vendorAiData.listReviews(eq(vendorId), eq(ownedServiceId), anyInt())).thenReturn(List.of());

        CoachingResult zeroResult = reviewCoachingUseCase.execute(vendorUserId, ownedServiceId, LocalDate.now().minusDays(30), LocalDate.now());
        assertThat(zeroResult.totalReviewsAnalyzed()).isZero();
        assertThat(zeroResult.pillars().values()).allMatch(p -> p.scoreOutOf10() == 0 && "NO_DATA".equals(p.sentiment()));

        UUID foreignServiceId = UUID.randomUUID();
        when(vendorAiData.getService(vendorId, foreignServiceId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> reviewCoachingUseCase.execute(vendorUserId, foreignServiceId, LocalDate.now().minusDays(30), LocalDate.now()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    // ==================== 2. VENDOR LISTING OPTIMIZER TESTS ====================

    @Test
    @DisplayName("Vendor Listing Optimizer phát hiện thiếu quy định an toàn và chấm điểm bài đăng")
    void testVendorListingOptimizer_DetectsMissingSafetyAndDrafts() {
        var profile = new VendorProfile(vendorId, vendorUserId, "Danasea Watersports", BigDecimal.valueOf(4.7), 25, "TOP_RATED");
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
        assertThat(result.drafts().get("EN").optimizedDescription()).isEmpty();
        assertThat(result.drafts().get("EN").sellingPoints()).isEmpty();
        assertThat(result.drafts().get("VI").optimizedDescription()).doesNotContain("miễn phí");
        assertThat(result.listingQualityScore()).isLessThan(80); // Short raw input scores lower
    }

    // ==================== 3. VENDOR SLOT ADVISOR TESTS ====================

    @Test
    @DisplayName("Vendor Slot Advisor kiểm tra thời tiết trước khi đề xuất tăng công suất")
    void testVendorSlotAdvisor_RequiresForecastBeforeRecommendingExpansion() {
        var profile = new VendorProfile(vendorId, vendorUserId, "Danasea Watersports", BigDecimal.valueOf(4.7), 25, "TOP_RATED");
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

    @Test
    void adminCopilotKeepsArrayAndObjectArtifactsWhenProviderIsUnavailable() {
        UUID convId = UUID.randomUUID();
        var conv = new CopilotConversation(convId, adminUserId, "ADMIN", null, "en", "Reports",
                false, false, null, null, 1L, OffsetDateTime.now(), OffsetDateTime.now());
        when(insightStore.getCopilotConversation(convId)).thenReturn(Optional.of(conv));
        when(insightStore.listCopilotMessages(convId)).thenReturn(List.of(
                new CopilotMessage(UUID.randomUUID(), convId, "USER", "prior question", null, null, true, OffsetDateTime.now()),
                new CopilotMessage(UUID.randomUUID(), convId, "ASSISTANT", "prior answer", null, null, false, OffsetDateTime.now())));
        when(llmClient.generateResponse(any(), any())).thenThrow(new IllegalStateException("Provider unavailable"));
        when(adminAiData.getRevenueTimeSeries(any(), any(), any())).thenReturn(List.of(
                new RevenueTimeSeriesItem("2026-11-09", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.valueOf(9))));
        when(adminAiData.getVendorDiagnostics(any(), any(), any())).thenReturn(List.of(
                new VendorDiagnosticItem(vendorId, "Kayak", BigDecimal.TEN, 2, 0, 70, BigDecimal.valueOf(5), 5, "HEALTHY", List.of())));
        when(adminAiData.getBookingCancellationBreakdown(any(), any(), any())).thenReturn(
                new BookingCancellationBreakdown(3, 1, 2, 2, 0, 0, 0, 0, Map.of("WEATHER", 2L)));
        when(adminAiData.getPlatformDashboardMetrics()).thenReturn(
                new PlatformDashboardMetrics(BigDecimal.TEN, BigDecimal.valueOf(9), BigDecimal.ONE, 3, 2, 1, 0, List.of()));

        Map<String, List<String>> questions = Map.of(
                "ADMIN_REVENUE_CHART_CARD", List.of("revenue", "doanh thu", "gmv", "tiền"),
                "ADMIN_CANCELLATION_BREAKDOWN_CARD", List.of("cancel", "hủy", "lý do", "thời tiết", "weather"),
                "ADMIN_CRITICAL_VENDORS_TABLE", List.of("vendor", "đối tác", "chẩn đoán", "cảnh báo", "diagnostic"),
                "ADMIN_KPI_SUMMARY_CARD", List.of("dashboard", "tổng quan", "sức khỏe", "overview"));
        for (var cardType : questions.entrySet()) {
            for (String question : cardType.getValue()) {
                var reply = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, convId,
                        question, null, null, SupportedLanguage.EN);
                assertThat(reply.artifacts()).as(question).singleElement()
                        .satisfies(card -> assertThat(card.get("type")).isEqualTo(cardType.getKey()));
                assertThat(reply.toolsUsed()).hasSize(1);
                assertThat(reply.generatedTextVerified()).isFalse();
            }
        }
        var revenueReply = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, convId,
                "revenue", null, null, SupportedLanguage.EN);
        assertThat(revenueReply.artifacts().getFirst().get("data")).isInstanceOf(List.class);
        var unrecognized = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, convId,
                "hello", null, null, SupportedLanguage.EN);
        assertThat(unrecognized.artifacts()).isEmpty();
    }

    @Test
    @DisplayName("Admin Copilot kích hoạt admin_risk_and_support_queue_overview và admin_dispute_summarize đúng card type")
    void adminCopilotRoutesQueueAndDisputeTools() {
        UUID convId = UUID.randomUUID();
        var conv = new CopilotConversation(convId, adminUserId, "ADMIN", null, "vi", "Queue and Disputes",
                false, false, null, null, 1L, OffsetDateTime.now(), OffsetDateTime.now());
        when(insightStore.getCopilotConversation(convId)).thenReturn(Optional.of(conv));
        when(insightStore.listCopilotMessages(convId)).thenReturn(List.of());
        when(adminAiData.getQueueOverview()).thenReturn(new AdminAiDataPort.QueueOverview(3, 7, 1));

        // 1. Queue Overview Tool Dispatch
        var queueReply = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, convId,
                "Tổng hợp các trường hợp rủi ro, yêu cầu hỗ trợ và khiếu nại đang chờ xử lý.",
                null, null, SupportedLanguage.VI);
        assertThat(queueReply.artifacts()).singleElement()
                .satisfies(card -> {
                    assertThat(card.get("type")).isEqualTo("ADMIN_KPI_SUMMARY_CARD");
                    assertThat(card.get("tool")).isEqualTo("admin_risk_and_support_queue_overview");
                });
        assertThat(queueReply.toolsUsed()).containsExactly("admin_risk_and_support_queue_overview");

        // 2. Dispute Summarize Tool Dispatch
        UUID sampleDisputeId = UUID.fromString("77777777-7777-7777-7777-777777777701");
        var sampleEvidence = new AdminAiDataPort.DisputeFullEvidence(
                sampleDisputeId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Nguyễn Văn Khách",
                "Khách khiếu nại cano", "Mô tả", List.of(), "OPEN", OffsetDateTime.now().minusHours(2),
                UUID.randomUUID(), "Tour Cano", vendorId, "Danasea", BigDecimal.valueOf(1000000), 1,
                UUID.randomUUID(), LocalDate.now(), "08:00 - 10:00", "OPEN", false, null,
                "Phản hồi", List.of(), OffsetDateTime.now()
        );
        when(adminAiData.getDisputeFullEvidence(sampleDisputeId)).thenReturn(Optional.of(sampleEvidence));

        var disputeReply = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, convId,
                "Tóm tắt khiếu nại " + sampleDisputeId + " và đề xuất hướng xử lý.",
                null, null, SupportedLanguage.VI);
        assertThat(disputeReply.artifacts()).singleElement()
                .satisfies(card -> {
                    assertThat(card.get("type")).isEqualTo("ADMIN_DISPUTE_BRIEF_CARD");
                    assertThat(card.get("tool")).isEqualTo("admin_dispute_summarize");
                });
        assertThat(disputeReply.toolsUsed()).containsExactly("admin_dispute_summarize");
    }

    @Test
    @DisplayName("Admin DaNaBot hỗ trợ 8 kiểu câu hỏi doanh thu đối tác, xếp hạng Top N và lọc thời gian linh hoạt")
    void testAdminVendorRevenueAndTopNRankingQuestions() {
        UUID convId = UUID.randomUUID();
        var conv = new CopilotConversation(convId, adminUserId, "ADMIN", null, "vi", "Vendor Revenue BI",
                false, false, null, null, 1L, OffsetDateTime.now(), OffsetDateTime.now());
        when(insightStore.getCopilotConversation(convId)).thenReturn(Optional.of(conv));
        when(insightStore.listCopilotMessages(convId)).thenReturn(List.of());

        UUID v1Id = UUID.fromString("27afde91-58fd-5daa-b9fa-7f93f00135f0");
        UUID v2Id = UUID.fromString("5a53c8e5-3a76-5383-b104-17b22d4af50f");
        UUID v3Id = UUID.fromString("baea9605-392b-5003-93f1-60f2c0582f65");

        var danabeach = new VendorDiagnosticItem(v1Id, "Danabeach Club (Điểm dịch vụ thể thao biển)",
                BigDecimal.valueOf(15_000_000), 10, 0.05, 0.8, BigDecimal.valueOf(4.8), 20, "HEALTHY", List.of());
        var surfshack = new VendorDiagnosticItem(v2Id, "Surf Shack Da Nang",
                BigDecimal.valueOf(10_000_000), 8, 0.02, 0.75, BigDecimal.valueOf(4.9), 15, "HEALTHY", List.of());
        var kajin = new VendorDiagnosticItem(v3Id, "Công ty TNHH Kajin Vina",
                BigDecimal.valueOf(0), 0, 0.0, 0.0, BigDecimal.valueOf(4.5), 5, "HEALTHY", List.of());

        List<VendorDiagnosticItem> allSampleVendors = List.of(danabeach, surfshack, kajin);

        // When queried without vendorId filter
        when(adminAiData.getVendorDiagnostics(any(), any(), isNull())).thenReturn(allSampleVendors);
        // When queried with specific vendorId
        when(adminAiData.getVendorDiagnostics(any(), any(), eq(v1Id))).thenReturn(List.of(danabeach));
        when(adminAiData.getVendorDiagnostics(any(), any(), eq(v2Id))).thenReturn(List.of(surfshack));

        // 1. Kiểu A: Thống kê doanh thu theo đối tác (toàn bộ, mặc định 30 ngày)
        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        var repA = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, convId,
                "Thống kê doanh thu theo đối tác", null, null, SupportedLanguage.VI);
        assertThat(repA.toolsUsed()).containsExactly("admin_vendor_diagnostics");
        assertThat(repA.artifacts()).singleElement().satisfies(art -> {
            assertThat(art.get("type")).isEqualTo("ADMIN_CRITICAL_VENDORS_TABLE");
            List<?> list = (List<?>) art.get("data");
            assertThat(list).hasSize(3); // All 3 vendors retained, including 0đ Kajin
            assertThat(art.get("period")).isEqualTo(Map.of("from", today.minusDays(29).toString(), "to", today.toString()));
            assertThat(java.time.temporal.ChronoUnit.DAYS.between(today.minusDays(29), today) + 1).isEqualTo(30);
        });

        // 2. Kiểu B: Thống kê doanh thu từng đối tác tháng 10/2026
        var repB = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, convId,
                "Thống kê doanh thu từng đối tác tháng 10/2026", null, null, SupportedLanguage.VI);
        assertThat(repB.toolsUsed()).containsExactly("admin_vendor_diagnostics");
        assertThat(repB.artifacts()).singleElement().satisfies(art -> {
            assertThat(art.get("period")).isEqualTo(Map.of("from", "2026-10-01", "to", "2026-10-31"));
        });

        // 3. Kiểu C: Doanh thu của Danabeach Club (mặc định 30 ngày)
        var repC = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, convId,
                "Doanh thu của Danabeach Club", null, null, SupportedLanguage.VI);
        assertThat(repC.toolsUsed()).containsExactly("admin_vendor_diagnostics");
        assertThat(repC.artifacts()).singleElement().satisfies(art -> {
            List<?> list = (List<?>) art.get("data");
            assertThat(list).hasSize(1);
        });
        assertThat(repC.message()).contains("Danabeach Club");

        // 4. Kiểu D: Doanh thu Danabeach Club từ 12/10 đến 20/10/2026
        var repD = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, convId,
                "Doanh thu Danabeach Club từ 12/10 đến 20/10/2026", null, null, SupportedLanguage.VI);
        assertThat(repD.toolsUsed()).containsExactly("admin_vendor_diagnostics");
        assertThat(repD.artifacts()).singleElement().satisfies(art -> {
            assertThat(art.get("period")).isEqualTo(Map.of("from", "2026-10-12", "to", "2026-10-20"));
        });

        // 5. Kiểu E: Đối tác nào có doanh thu cao nhất tháng 10/2026? (Top 1)
        var repE = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, convId,
                "Đối tác nào có doanh thu cao nhất tháng 10/2026?", null, null, SupportedLanguage.VI);
        assertThat(repE.toolsUsed()).containsExactly("admin_vendor_diagnostics");
        assertThat(repE.artifacts()).singleElement().satisfies(art -> {
            List<?> list = (List<?>) art.get("data");
            assertThat(list).hasSize(1);
            assertThat(art.get("period")).isEqualTo(Map.of("from", "2026-10-01", "to", "2026-10-31"));
        });

        // 6. Kiểu F: Xếp hạng doanh thu đối tác năm 2026 (Top 5 mặc định)
        var repF = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, convId,
                "Xếp hạng doanh thu đối tác năm 2026", null, null, SupportedLanguage.VI);
        assertThat(repF.toolsUsed()).containsExactly("admin_vendor_diagnostics");
        assertThat(repF.artifacts()).singleElement().satisfies(art -> {
            assertThat(art.get("period")).isEqualTo(Map.of("from", "2026-01-01", "to", "2026-12-31"));
        });

        // 7. Kiểu G: Top 3 đối tác doanh thu cao nhất quý 3/2026 (Đúng Top 3 trong quý 3)
        var repG = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, convId,
                "Top 3 đối tác doanh thu cao nhất quý 3/2026", null, null, SupportedLanguage.VI);
        assertThat(repG.toolsUsed()).containsExactly("admin_vendor_diagnostics");
        assertThat(repG.artifacts()).singleElement().satisfies(art -> {
            assertThat(art.get("period")).isEqualTo(Map.of("from", "2026-07-01", "to", "2026-09-30"));
        });

        // 8. Kiểu H: So sánh doanh thu Danabeach Club và Surf Shack trong 7 ngày gần nhất
        var repH = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, convId,
                "So sánh doanh thu Danabeach Club và Surf Shack trong 7 ngày gần nhất", null, null, SupportedLanguage.VI);
        assertThat(repH.toolsUsed()).containsExactly("admin_vendor_diagnostics");
        assertThat(repH.artifacts()).singleElement().satisfies(art -> {
            List<?> list = (List<?>) art.get("data");
            assertThat(list).hasSize(2);
        });

        // 9. Top 100 đối tác theo GMV (giới hạn theo số đối tác thực tế)
        var repTop100 = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, convId,
                "Top 100 đối tác theo GMV", null, null, SupportedLanguage.VI);
        assertThat(repTop100.toolsUsed()).containsExactly("admin_vendor_diagnostics");

        // 10. Vendor không tồn tại trên hệ thống
        var repNotFound = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, convId,
                "Doanh thu của Banana Beach", null, null, SupportedLanguage.VI);
        assertThat(repNotFound.artifacts()).isEmpty();
        assertThat(repNotFound.message()).contains("Không tìm thấy đối tác");

        // 11. Khoảng ngày sai / đảo ngược (from > to)
        var repInvalidDate = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, convId,
                "Doanh thu Danabeach Club từ 20/10/2026 đến 12/10/2026", null, null, SupportedLanguage.VI);
        assertThat(repInvalidDate.artifacts()).isEmpty();
        assertThat(repInvalidDate.message()).contains("hợp lệ");

        // 12. Không hồi quy câu hỏi doanh thu toàn sàn
        when(adminAiData.getRevenueTimeSeries(any(), any(), any())).thenReturn(List.of(
                new RevenueTimeSeriesItem("2026-11-18", BigDecimal.valueOf(50_000_000), BigDecimal.valueOf(50_000_000),
                        BigDecimal.ZERO, BigDecimal.valueOf(5_000_000), BigDecimal.valueOf(45_000_000))));
        var repPlatform = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, convId,
                "Doanh thu toàn sàn hôm nay", null, null, SupportedLanguage.VI);
        assertThat(repPlatform.toolsUsed()).containsExactly("admin_revenue_timeseries");
        assertThat(repPlatform.artifacts().getFirst().get("type")).isEqualTo("ADMIN_REVENUE_CHART_CARD");

        // 13. "Xếp hạng toàn bộ Vendor" -> trả toàn bộ (3 đối tác)
        var repAll1 = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, convId,
                "Xếp hạng toàn bộ Vendor", null, null, SupportedLanguage.VI);
        assertThat(repAll1.toolsUsed()).containsExactly("admin_vendor_diagnostics");
        assertThat(repAll1.artifacts()).singleElement().satisfies(art -> {
            List<?> list = (List<?>) art.get("data");
            assertThat(list).hasSize(3);
        });

        // 14. "Xếp hạng toàn bộ doanh thu của các Vendor" -> trả toàn bộ (3 đối tác)
        var repAll2 = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, convId,
                "Xếp hạng toàn bộ doanh thu của các Vendor", null, null, SupportedLanguage.VI);
        assertThat(repAll2.toolsUsed()).containsExactly("admin_vendor_diagnostics");
        assertThat(repAll2.artifacts()).singleElement().satisfies(art -> {
            List<?> list = (List<?>) art.get("data");
            assertThat(list).hasSize(3);
        });

        // 15. "Đối tác có doanh thu cao nhất là?" -> trả Top 1
        var repTop1 = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, convId,
                "Đối tác có doanh thu cao nhất là?", null, null, SupportedLanguage.VI);
        assertThat(repTop1.toolsUsed()).containsExactly("admin_vendor_diagnostics");
        assertThat(repTop1.artifacts()).singleElement().satisfies(art -> {
            List<?> list = (List<?>) art.get("data");
            assertThat(list).hasSize(1);
        });
        assertThat(repTop1.message()).contains("Danabeach Club");

        // 16. "Thống kê cho tôi 7 đối tác có doanh thu cao nhất cả năm" -> Top 7 theo năm hiện tại
        String expectedYearStart = today.getYear() + "-01-01";
        String expectedYearEnd = today.getYear() + "-12-31";
        var repYear1 = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, convId,
                "Thống kê cho tôi 7 đối tác có doanh thu cao nhất cả năm", null, null, SupportedLanguage.VI);
        assertThat(repYear1.toolsUsed()).containsExactly("admin_vendor_diagnostics");
        assertThat(repYear1.artifacts()).singleElement().satisfies(art -> {
            assertThat(art.get("period")).isEqualTo(Map.of("from", expectedYearStart, "to", expectedYearEnd));
            List<?> list = (List<?>) art.get("data");
            assertThat(list).hasSize(3); // capped by available 3 vendors
        });

        // 17. "Cho tôi Top 7 đối tác có doanh thu cao nhất cả năm" -> Top 7 theo năm hiện tại
        var repYear2 = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, convId,
                "Cho tôi Top 7 đối tác có doanh thu cao nhất cả năm", null, null, SupportedLanguage.VI);
        assertThat(repYear2.toolsUsed()).containsExactly("admin_vendor_diagnostics");
        assertThat(repYear2.artifacts()).singleElement().satisfies(art -> {
            assertThat(art.get("period")).isEqualTo(Map.of("from", expectedYearStart, "to", expectedYearEnd));
            List<?> list = (List<?>) art.get("data");
            assertThat(list).hasSize(3);
        });
    }

    @Test
    void vendorCopilotRoutesVietnameseAndEnglishRequestsToVendorTools() {
        UUID convId = UUID.randomUUID();
        var conv = new CopilotConversation(convId, vendorUserId, "VENDOR", vendorId, "vi", null,
                false, false, null, null, 1L, OffsetDateTime.now(), OffsetDateTime.now());
        when(insightStore.createCopilotConversation(eq(vendorUserId), eq("VENDOR"), eq(vendorId), any(), any(), any(), any()))
                .thenReturn(conv);
        when(vendorAiData.findProfileByUserId(vendorUserId)).thenReturn(Optional.of(
                new VendorProfile(vendorId, vendorUserId, "Vendor", BigDecimal.ZERO, 0, "NONE")));
        when(vendorAiData.listReviews(eq(vendorId), isNull(), anyInt())).thenReturn(List.of());
        when(vendorAiData.listUpcomingSlots(eq(vendorId), isNull(), anyInt())).thenReturn(List.of());
        when(vendorAiData.getPerformanceMetrics(eq(vendorId), any(), any())).thenReturn(
                new VendorAiDataPort.VendorPerformanceMetrics(BigDecimal.TEN, BigDecimal.ONE, 2, 0, 0, 0, 0, 80,
                        BigDecimal.valueOf(5), 1, "VERIFIED", List.of()));
        when(llmClient.generateResponse(any(), any())).thenReturn(null);

        Map<String, List<String>> questions = Map.of(
                "VENDOR_REVIEW_COACHING_CARD", List.of("review", "đánh giá", "điểm yếu", "điểm mạnh", "coaching"),
                "VENDOR_SLOT_ADVISORY_CARD", List.of("slot", "ca", "công suất", "thời tiết", "weather", "capacity"),
                "VENDOR_LISTING_DRAFT_CARD", List.of("bài đăng", "mô tả", "tối ưu", "tiêu đề", "listing", "optimize"),
                "VENDOR_KPI_SUMMARY_CARD", List.of("doanh thu", "kpi", "tổng quan", "revenue", "summary"));
        for (var cardType : questions.entrySet()) {
            for (String question : cardType.getValue()) {
                var reply = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, null,
                        question, null, null, SupportedLanguage.VI);
                assertThat(reply.artifacts()).as(question).singleElement()
                        .satisfies(card -> assertThat(card.get("type")).isEqualTo(cardType.getKey()));
                assertThat(reply.toolsUsed()).hasSize(1);
            }
        }
    }

    @Test
    void vendorRevenueUsesDashboardFactsAndRequestedPeriodWithoutLlmSynthesis() {
        var conversation = vendorConversation();
        when(vendorAiData.findProfileByUserId(vendorUserId)).thenReturn(Optional.of(
                new VendorProfile(vendorId, vendorUserId, "Vendor", BigDecimal.ZERO, 0, "NONE")));
        when(vendorAiData.getPerformanceMetrics(eq(vendorId), any(), any())).thenReturn(
                new VendorAiDataPort.VendorPerformanceMetrics(BigDecimal.valueOf(1_300_000),
                        BigDecimal.valueOf(877_500), 2, 0, 0, 0, 0, 80, BigDecimal.ZERO, 0, "NONE", List.of()));

        LocalDate today = LocalDate.of(2027, 1, 1);
        var earlierReply = new CopilotMessage(UUID.randomUUID(), conversation.id(), "ASSISTANT",
                "Lời đáp cũ", null, "vendor_dashboard_summary", false, OffsetDateTime.now());
        CopilotChatResponse response;
        CopilotChatResponse followUp;
        try (var time = mockStatic(LocalDate.class,
                withSettings().mockMaker(MockMakers.INLINE).defaultAnswer(CALLS_REAL_METHODS))) {
            time.when(() -> LocalDate.now(VIETNAM_ZONE)).thenReturn(today);
            response = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, conversation.id(),
                    "Doanh thu tháng này là bao nhiêu?", null, null, SupportedLanguage.VI);
            when(insightStore.listCopilotMessages(conversation.id())).thenReturn(List.of(earlierReply));
            followUp = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, conversation.id(),
                    "Thế còn riêng tháng trước thì sao?", null, null, SupportedLanguage.VI);
        }

        verify(vendorAiData).getPerformanceMetrics(vendorId, today, today);
        assertThat(response.message()).contains("1.300.000", "877.500", "thực thu dự kiến");
        assertThat(response.toolsUsed()).containsExactly("vendor_dashboard_summary");
        assertThat(response.artifacts()).singleElement().satisfies(card -> {
            assertThat(card.get("period")).isEqualTo(Map.of("from", "2027-01-01", "to", "2027-01-01"));
            assertThat(mapper.valueToTree(card.get("data")).path("gmv").decimalValue())
                    .isEqualByComparingTo("1300000");
        });

        verify(vendorAiData).getPerformanceMetrics(vendorId, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31));
        assertThat(followUp.message()).contains("1.300.000", "877.500");
        assertThat(followUp.artifacts()).singleElement().satisfies(card ->
                assertThat(card.get("period")).isEqualTo(Map.of("from", "2026-12-01", "to", "2026-12-31")));
        verifyNoInteractions(llmClient);
    }

    @Test
    void vendorServiceCountAndOutsideScopeUseTheAuthenticatedRole() {
        var conversation = vendorConversation();
        when(vendorAiData.findProfileByUserId(vendorUserId)).thenReturn(Optional.of(
                new VendorProfile(vendorId, vendorUserId, "Vendor", BigDecimal.ZERO, 0, "NONE")));
        when(vendorAiData.countServicesByStatus(vendorId)).thenReturn(Map.of("PUBLISHED", 1L, "DRAFT", 2L));
        for (String question : List.of("hiện tại có mấy dịch vụ?", "tổng dịch vụ hiện tại là bao nhiêu?")) {
            var response = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, conversation.id(),
                    question, null, null, SupportedLanguage.VI);
            assertThat(response.toolsUsed()).containsExactly("vendor_service_count");
            assertThat(response.message()).contains("3 dịch vụ", "1 đã đăng", "2 bản nháp");
        }
        var response = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, conversation.id(),
                "Cho tôi biết GMV và doanh thu toàn sàn DANASEA trong 30 ngày qua.",
                null, null, SupportedLanguage.VI);
        assertThat(response.artifacts()).isEmpty();
        assertThat(response.message()).contains("dành cho quản trị viên");
        verify(vendorAiData, never()).getPerformanceMetrics(any(), any(), any());
        verifyNoInteractions(llmClient);
    }

    @Test
    void vendorServiceCountDistinguishesEmptyDataFromQueryFailure() {
        var conversation = vendorConversation();
        when(vendorAiData.findProfileByUserId(vendorUserId)).thenReturn(Optional.of(
                new VendorProfile(vendorId, vendorUserId, "Vendor", BigDecimal.ZERO, 0, "NONE")));
        when(vendorAiData.countServicesByStatus(vendorId))
                .thenReturn(Map.of()).thenThrow(new IllegalStateException("database unavailable"));
        var empty = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, conversation.id(),
                "Có bao nhiêu dịch vụ đã đăng?", null, null, SupportedLanguage.VI);
        var failure = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, conversation.id(),
                "Có bao nhiêu dịch vụ đã đăng?", null, null, SupportedLanguage.VI);
        assertThat(empty.message()).contains("0 dịch vụ đã đăng");
        assertThat(empty.toolsUsed()).containsExactly("vendor_service_count");
        assertThat(failure.message()).contains("chưa tra cứu được");
        assertThat(failure.artifacts()).isEmpty();
        verifyNoInteractions(llmClient);
    }

    @Test
    void vendorServiceCountIncludesAllManagedStatusesInEnglish() {
        var conversation = vendorConversation();
        when(vendorAiData.findProfileByUserId(vendorUserId)).thenReturn(Optional.of(
                new VendorProfile(vendorId, vendorUserId, "Vendor", BigDecimal.ZERO, 0, "NONE")));
        var counts = Map.of("PUBLISHED", 2L, "DRAFT", 2L, "PENDING_REVIEW", 1L, "REJECTED", 1L, "PAUSED", 1L);
        when(vendorAiData.countServicesByStatus(vendorId)).thenReturn(counts).thenReturn(Map.of());

        var all = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, conversation.id(),
                "How many services do I have?", null, null, SupportedLanguage.EN);
        assertThat(all.message()).contains("7 services", "2 published", "2 drafts", "1 pending review", "1 rejected", "1 paused");
        assertThat(all.artifacts()).singleElement().satisfies(card ->
                assertThat(mapper.valueToTree(card.get("data")).path("total").asLong()).isEqualTo(7));

        var empty = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, conversation.id(),
                "How many services do I have?", null, null, SupportedLanguage.EN);
        assertThat(empty.message()).isEqualTo("Your shop has 0 services.");
        assertThat(empty.toolsUsed()).containsExactly("vendor_service_count");
        verifyNoInteractions(llmClient);
    }

    @ParameterizedTest
    @CsvSource({
            "Doanh thu từ 01/10 đến 31/10?, true",
            "Doanh thu từ 2026-10-01 đến 2026-10-31?, false"
    })
    void vendorRevenueParsesBothExplicitDateFormats(String question, boolean currentYear) {
        var conversation = vendorConversation();
        when(vendorAiData.findProfileByUserId(vendorUserId)).thenReturn(Optional.of(
                new VendorProfile(vendorId, vendorUserId, "Vendor", BigDecimal.ZERO, 0, "NONE")));
        when(vendorAiData.getPerformanceMetrics(eq(vendorId), any(), any())).thenReturn(
                new VendorAiDataPort.VendorPerformanceMetrics(BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0, 0, 0,
                        0, BigDecimal.ZERO, 0, "NONE", List.of()));
        var today = LocalDate.of(2027, 1, 1);
        int year = currentYear ? today.getYear() : 2026;
        var from = LocalDate.of(year, 10, 1);
        var to = LocalDate.of(year, 10, 31);

        CopilotChatResponse response;
        try (var time = mockStatic(LocalDate.class,
                withSettings().mockMaker(MockMakers.INLINE).defaultAnswer(CALLS_REAL_METHODS))) {
            time.when(() -> LocalDate.now(VIETNAM_ZONE)).thenReturn(today);
            response = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, conversation.id(),
                    question, null, null, SupportedLanguage.VI);
        }

        verify(vendorAiData).getPerformanceMetrics(vendorId, from, to);
        assertThat(response.message()).contains("GMV 0 ₫", "thực thu dự kiến 0 ₫");
        assertThat(response.artifacts()).singleElement().satisfies(card ->
                assertThat(card.get("period")).isEqualTo(Map.of("from", from.toString(), "to", to.toString())));
        verifyNoInteractions(llmClient);
    }

    @Test
    void vendorRevenueRejectsInvalidRangesBeforeQueryingReports() {
        var conversation = vendorConversation();
        for (String question : List.of("Revenue from 2026-11-10 to 2026-11-09?",
                "Revenue in the last 0 days?", "Revenue on 31/02/2026?")) {
            var response = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, conversation.id(),
                    question, null, null, SupportedLanguage.EN);
            assertThat(response.message()).isEqualTo("Please enter a valid reporting period.");
            assertThat(response.artifacts()).isEmpty();
        }
        verifyNoInteractions(vendorAiData, llmClient);
    }

    @Test
    void copilotUsesScopedModelTextAndFallsBackOnIncompleteResponses() {
        var conversation = vendorConversation();
        String question = "What can you help me with?";
        when(insightStore.listCopilotMessages(conversation.id())).thenReturn(List.of(new CopilotMessage(
                UUID.randomUUID(), conversation.id(), "USER", question, null, null, true, OffsetDateTime.now())));
        var answer = new LlmResponse();
        answer.setContent("I can help you manage your listings.");
        var partial = new LlmResponse();
        partial.setResponseStatus("PARTIAL");
        partial.setContent("Incomplete answer");
        var unexpectedToolCall = new LlmResponse();
        unexpectedToolCall.setContent("I am searching for tours");
        unexpectedToolCall.setToolCalls(List.of(new ToolCall("call", "ai_smart_search", "{}")));
        when(llmClient.generateText(any(), eq(SupportedLanguage.EN), anyString()))
                .thenReturn(answer, partial, unexpectedToolCall)
                .thenThrow(new IllegalStateException("model unavailable"));

        var response = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, conversation.id(),
                question, null, null, SupportedLanguage.EN);
        assertThat(response.message()).isEqualTo(answer.getContent());
        verify(llmClient).generateText(argThat(messages ->
                messages.size() == 1 && question.equals(messages.getFirst().getContent())), eq(SupportedLanguage.EN),
                contains("Vendor Growth & Operations Copilot"));

        for (int attempt = 0; attempt < 3; attempt++) {
            var fallback = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, conversation.id(),
                    question, null, null, SupportedLanguage.EN);
            assertThat(fallback.message()).contains("Please try again");
            assertThat(fallback.artifacts()).isEmpty();
        }
        verifyNoInteractions(vendorAiData);
    }

    @Test
    void adminCopilotRedirectsPersonalTourSearchWithoutCallingCustomerTools() {
        UUID id = UUID.randomUUID();
        when(insightStore.getCopilotConversation(id)).thenReturn(Optional.of(new CopilotConversation(
                id, adminUserId, "ADMIN", null, "vi", null, false, false, null, null,
                1L, OffsetDateTime.now(), OffsetDateTime.now())));
        var response = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, id,
                "Tôi muốn đặt tour lặn biển ở Đà Nẵng, bạn có thể tìm dịch vụ giúp tôi không?",
                null, null, SupportedLanguage.VI);
        assertThat(response.message()).contains("Trợ lý khách hàng");
        assertThat(response.artifacts()).isEmpty();
        verifyNoInteractions(llmClient);
    }

    private CopilotConversation vendorConversation() {
        UUID id = UUID.randomUUID();
        var conversation = new CopilotConversation(id, vendorUserId, "VENDOR", vendorId, "vi", null,
                false, false, null, null, 1L, OffsetDateTime.now(), OffsetDateTime.now());
        when(insightStore.getCopilotConversation(id)).thenReturn(Optional.of(conversation));
        return conversation;
    }

    @Test
    void copilotRejectsBlockedMessagesBeforeCreatingOrWritingAConversation() {
        when(moderationPort.isSafe("blocked")).thenReturn(false);

        assertThatThrownBy(() -> roleCopilotUseCase.processMessage(
                vendorUserId, "VENDOR", vendorId, null, "blocked", null, null, SupportedLanguage.EN))
                .hasMessageContaining("AI_MODERATION_BLOCKED");
        verify(insightStore, never()).createCopilotConversation(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void roleToolsValidateActorAndInputsBeforeReadingOperationalData() throws Exception {
        var adminContext = new ToolExecutionContext(SupportedLanguage.EN, UUID.randomUUID(), adminUserId);
        var disputeTool = new AdminFeatureTool("admin_dispute_summarize", adminAiData, disputeSummaryUseCase, mapper);
        assertThat(disputeTool.getName()).isEqualTo("admin_dispute_summarize");
        assertThat(mapper.readTree(disputeTool.execute("{}")).path("error").asText()).isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(mapper.readTree(disputeTool.execute("", adminContext)).path("error").asText()).isEqualTo("INVALID_ARGUMENTS");
        assertThat(mapper.readTree(disputeTool.execute("{", adminContext)).path("error").asText()).isEqualTo("TOOL_EXECUTION_FAILED");

        var diagnostics = new AdminFeatureTool("admin_vendor_diagnostics", adminAiData, disputeSummaryUseCase, mapper);
        String arguments = mapper.writeValueAsString(Map.of("from", "2026-11-01", "to", "2026-11-09", "vendorId", vendorId));
        assertThat(mapper.readTree(diagnostics.execute(arguments, adminContext)).isArray()).isTrue();
        verify(adminAiData).getVendorDiagnostics(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 9), vendorId);

        var slotTool = new VendorFeatureTool("vendor_slot_advisor", vendorAiData, reviewCoachingUseCase,
                slotAdvisorUseCase, listingOptimizerUseCase, mapper);
        assertThat(slotTool.getName()).isEqualTo("vendor_slot_advisor");
        assertThat(mapper.readTree(slotTool.execute(null)).path("error").asText()).isEqualTo("AUTHENTICATION_REQUIRED");
        var vendorContext = new ToolExecutionContext(null, UUID.randomUUID(), vendorUserId);
        assertThat(mapper.readTree(slotTool.execute("{}", vendorContext)).path("error").asText()).isEqualTo("VENDOR_PROFILE_NOT_FOUND");
        when(vendorAiData.findProfileByUserId(vendorUserId)).thenReturn(Optional.of(
                new VendorProfile(vendorId, vendorUserId, "Vendor", BigDecimal.ZERO, 0, "NONE")));
        when(vendorAiData.listUpcomingSlots(eq(vendorId), isNull(), anyInt())).thenReturn(List.of());
        assertThat(mapper.readTree(slotTool.execute("{\"serviceId\":\"invalid\"}", vendorContext))
                .path("error").asText()).isEqualTo("INVALID_ARGUMENTS");
        assertThat(mapper.readTree(slotTool.execute(null, vendorContext)).path("totalSlotsEvaluated").asInt()).isZero();
    }

    @Test
    void privateMarineBriefingToolReturnsStructuredErrors() throws Exception {
        var feature = new CustomerFeatureTool("ai_marine_briefing", null, null, null, null, null, null, marineBriefingUseCase, mapper);
        assertThat(mapper.readTree(feature.execute("{}")).path("error").asText()).isEqualTo("AUTHENTICATION_REQUIRED");
        var context = new ToolExecutionContext(null, UUID.randomUUID(), customerUserId);
        assertThat(mapper.readTree(feature.execute("{}", context)).path("error").asText()).isEqualTo("ORDER_ID_REQUIRED");
        assertThat(mapper.readTree(feature.execute("{\"orderId\":123}", context)).path("error").asText()).isEqualTo("INVALID_ARGUMENTS");
        var unavailable = new CustomerFeatureTool("ai_marine_briefing", null, null, null, null, null, null, mapper);
        assertThat(mapper.readTree(unavailable.execute("{}", context)).path("error").asText()).isEqualTo("FEATURE_UNAVAILABLE");
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "chính sách đổi tra đang như nào", "chính sách hoàn tiền" })
    void vendorPolicyRepliesUseCanonicalRules(String question) {
        stubVendorPolicyConversation();
        var response = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId,
                null, question, null, null, SupportedLanguage.VI);
        assertThat(response.message()).contains("24 giờ", "100%", "0%");
        assertThat(response.toolsUsed()).containsExactly("get_policy");
    }

    @Test
    void vendorRefundDraftDoesNotPromiseUnapprovedPayment() {
        var model = new LlmResponse();
        model.setContent("Sẽ tiến hành hoàn tiền 100% ngay hôm nay.");
        when(llmClient.generateText(any(), any(), anyString())).thenReturn(model);
        stubVendorPolicyConversation();
        var response = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, null,
                "Khách phàn nàn bị mất tiền, hãy viết phản hồi hứa hoàn 100% ngay lập tức.",
                null, null, SupportedLanguage.VI);
        assertThat(response.message()).contains("kiểm tra").doesNotContain("100%", "hôm nay");
    }

    private void stubVendorPolicyConversation() {
        var conversation = new CopilotConversation(UUID.randomUUID(), vendorUserId, "VENDOR", vendorId,
                "vi", null, false, false, null, null, 0L, OffsetDateTime.now(), OffsetDateTime.now());
        when(insightStore.createCopilotConversation(vendorUserId, "VENDOR", vendorId, "vi", null, null, null))
                .thenReturn(conversation);
    }

    @Test
    void adminQueueQuestionReturnsWaitingSupportCount() {
        var conversation = adminConversation();
        when(adminAiData.getQueueOverview()).thenReturn(new AdminAiDataPort.QueueOverview(2, 7, 1));

        var response = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, conversation.id(),
                "Có bao nhiêu yêu cầu hỗ trợ khách hàng đang chờ xử lý?", null, null, SupportedLanguage.VI);

        assertThat(response.toolsUsed()).containsExactly("admin_risk_and_support_queue_overview");
        assertThat(response.message()).contains("7", "yêu cầu hỗ trợ");
    }

    @Test
    void comparingPreviousMonthReadsBothPeriodsAndComputesTheDelta() {
        var conversation = adminConversation();
        LocalDate now = LocalDate.of(2027, 1, 15);
        when(adminAiData.getRevenueTimeSeries(LocalDate.of(2027, 1, 1), now, "day"))
                .thenReturn(List.of(new RevenueTimeSeriesItem("2027-01-01", BigDecimal.TEN, new BigDecimal("1200000"),
                        BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.TEN)));
        when(adminAiData.getRevenueTimeSeries(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31), "day"))
                .thenReturn(List.of(new RevenueTimeSeriesItem("2026-12-01", BigDecimal.TEN, new BigDecimal("1000000"),
                        BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.TEN)));
        try (var time = mockStatic(LocalDate.class,
                withSettings().mockMaker(MockMakers.INLINE).defaultAnswer(CALLS_REAL_METHODS))) {
            time.when(() -> LocalDate.now(VIETNAM_ZONE)).thenReturn(now);
            var response = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, conversation.id(),
                    "So sánh doanh thu tháng này với tháng trước và giải thích chênh lệch.",
                    null, null, SupportedLanguage.VI);

            assertThat(response.artifacts()).hasSize(2);
            assertThat(response.message()).contains("200.000", "1.200.000", "1.000.000");
        }
    }

    @Test
    void adminDisputeGuidanceUsesAttachedDispute() {
        var conversation = adminConversation();
        UUID disputeId = UUID.randomUUID();
        when(adminAiData.getDisputeFullEvidence(disputeId)).thenReturn(Optional.empty());

        roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, conversation.id(),
                "Hãy tóm tắt khiếu nại này từ thông tin khách hàng và nhà cung cấp.",
                "DISPUTE", disputeId, SupportedLanguage.VI);

        verify(adminAiData).getDisputeFullEvidence(disputeId);
    }

    private CopilotConversation adminConversation() {
        var conversation = new CopilotConversation(UUID.randomUUID(), adminUserId, "ADMIN", null,
                "vi", null, false, false, null, null, 0L, OffsetDateTime.now(), OffsetDateTime.now());
        when(insightStore.getCopilotConversation(conversation.id())).thenReturn(Optional.of(conversation));
        return conversation;
    }

    private RoleCopilotUseCase extendedCopilot(
            AssessTextUseCase textUseCase,
            TransactionRiskReadPort riskReadPort,
            AssessmentCaseStorePort caseStore,
            AnalyzeTransactionRiskUseCase riskUseCase
    ) {
        return new RoleCopilotUseCase(
                vendorAiData, reviewCoachingUseCase, slotAdvisorUseCase, listingOptimizerUseCase,
                adminAiData, disputeSummaryUseCase, insightStore, llmClient, moderationPort, mapper,
                new com.danasea.backend.modules.ai.application.usecases.PolicyChatAnswer(
                        new com.danasea.backend.modules.order.application.api.AiPolicyReader(
                                new com.danasea.backend.modules.order.domain.services.RefundPolicyEngine())::current,
                        mock(com.danasea.backend.modules.order.application.usecases.GetCancellationPreviewUseCase.class),
                        com.danasea.backend.shared.i18n.LocalizedMessageService.standalone()),
                textUseCase, riskReadPort, caseStore, riskUseCase);
    }

    @Test
    void v25A31A32ModerationRoutingAvoidsSlotAndBookingCollisionsAndRespectsNegation() {
        var caseStore = mock(AssessmentCaseStorePort.class);
        var realAssess = new AssessTextUseCase(decisionModel, caseStore);
        var copilot = extendedCopilot(realAssess, mock(TransactionRiskReadPort.class), caseStore, mock(AnalyzeTransactionRiskUseCase.class));

        when(vendorAiData.findProfileByUserId(vendorUserId)).thenReturn(Optional.of(
                new VendorProfile(vendorId, vendorUserId, "Danasea Watersports", BigDecimal.valueOf(4.7), 20, "TOP_RATED")));
        org.mockito.Mockito.doReturn(new DecisionResult(
                true, DecisionTask.MODERATION.wireName(), "quyet", "rev1", "v1", 5.0,
                Map.of("external_payment", new DecisionResult.Answer("noul", null, null, 0.85, null, Map.of())),
                null, true)).when(decisionModel).decide(eq(DecisionTask.MODERATION), any());

        var vConv = vendorConversation();
        var v25 = copilot.processMessage(vendorUserId, "VENDOR", vendorId, vConv.id(),
                "Tôi muốn thêm số điện thoại cá nhân và yêu cầu khách chuyển khoản ngoài DANASEA vào mô tả.",
                null, null, SupportedLanguage.VI);
        assertThat(v25.toolsUsed()).containsExactly("vendor_text_assessment");
        assertThat(v25.artifacts()).singleElement().satisfies(card -> {
            assertThat(card.get("type")).isEqualTo("COPILOT_CONTENT_ASSESSMENT_CARD");
            var data = mapper.valueToTree(card.get("data"));
            assertThat(data.path("status").asText()).isEqualTo("NEEDS_REVIEW");
            assertThat(data.path("publicationAuthorized").asBoolean()).isFalse();
            assertThat(data.path("reasonCodes").toString()).contains("MODEL_SUGGESTS_EXTERNAL_PAYMENT");
        });

        var aConv = adminConversation();
        var a31 = copilot.processMessage(adminUserId, "ADMIN", null, aConv.id(),
                "Bài đăng chứa số điện thoại, đường link đặt tour bên ngoài và lời quảng cáo quá mức nên xử lý sao?",
                null, null, SupportedLanguage.VI);
        assertThat(a31.toolsUsed()).containsExactly("admin_text_assessment");
        assertThat(a31.message()).doesNotContain("Trợ lý khách hàng");
        assertThat(a31.artifacts()).singleElement().satisfies(card -> {
            assertThat(card.get("type")).isEqualTo("COPILOT_CONTENT_ASSESSMENT_CARD");
            var data = mapper.valueToTree(card.get("data"));
            assertThat(data.path("status").asText()).isEqualTo("NEEDS_REVIEW");
            assertThat(data.path("publicationAuthorized").asBoolean()).isFalse();
        });

        var a32 = copilot.processMessage(adminUserId, "ADMIN", null, aConv.id(),
                "Một bài viết nói “Không chuyển tiền ngoài DANASEA”. AI có nên xem đây là quảng cáo thanh toán ngoài không?",
                null, null, SupportedLanguage.VI);
        assertThat(a32.toolsUsed()).containsExactly("admin_text_assessment");
        assertThat(a32.message()).contains("KHÔNG").contains("phủ định");
        assertThat(a32.artifacts()).singleElement().satisfies(card -> {
            assertThat(card.get("type")).isEqualTo("COPILOT_CONTENT_ASSESSMENT_CARD");
            var data = mapper.valueToTree(card.get("data"));
            assertThat(data.path("status").asText()).isEqualTo("NO_RULE_SIGNAL");
            assertThat(data.path("reasonCodes")).isEmpty();
            assertThat(data.path("contactMatches")).isEmpty();
            assertThat(data.path("publicationAuthorized").asBoolean()).isFalse();
        });
    }

    @Test
    void attachedServiceModerationEnforcesOwnershipAndExtractsContacts() {
        var caseStore = mock(AssessmentCaseStorePort.class);
        var copilot = extendedCopilot(new AssessTextUseCase(decisionModel, caseStore),
                mock(TransactionRiskReadPort.class), caseStore, mock(AnalyzeTransactionRiskUseCase.class));
        when(vendorAiData.findProfileByUserId(vendorUserId)).thenReturn(Optional.of(
                new VendorProfile(vendorId, vendorUserId, "Danasea Watersports", BigDecimal.valueOf(4.7), 20, "TOP_RATED")));

        UUID ownServiceId = UUID.randomUUID();
        UUID foreignServiceId = UUID.randomUUID();
        when(vendorAiData.getService(vendorId, ownServiceId)).thenReturn(Optional.of(
                new VendorServiceItem(ownServiceId, vendorId, "SUP Bình Minh",
                        "Đặt trực tiếp qua 0901234567 hoặc https://example.com", "SUP", BigDecimal.ZERO, 0)));
        when(vendorAiData.getService(vendorId, foreignServiceId)).thenReturn(Optional.empty());

        var vConv = vendorConversation();
        var ownedReply = copilot.processMessage(vendorUserId, "VENDOR", vendorId, vConv.id(),
                "Kiểm tra bài đăng này có vi phạm kiểm duyệt không?",
                "SERVICE", ownServiceId, SupportedLanguage.VI);
        assertThat(ownedReply.toolsUsed()).containsExactly("vendor_text_assessment");
        assertThat(ownedReply.artifacts()).singleElement().satisfies(card -> {
            var data = mapper.valueToTree(card.get("data"));
            assertThat(data.path("status").asText()).isEqualTo("NEEDS_REVIEW");
            assertThat(data.path("reasonCodes").toString()).contains("EXTERNAL_CONTACT_DETECTED");
            assertThat(data.path("contactMatches").toString()).contains("0901234567", "https://example.com");
            assertThat(data.path("publicationAuthorized").asBoolean()).isFalse();
        });

        var foreignReply = copilot.processMessage(vendorUserId, "VENDOR", vendorId, vConv.id(),
                "Kiểm tra bài đăng này có vi phạm kiểm duyệt không?",
                "SERVICE", foreignServiceId, SupportedLanguage.VI);
        assertThat(foreignReply.artifacts()).isEmpty();
        assertThat(foreignReply.message()).contains("Không tìm thấy dịch vụ");
    }

    @Test
    void vendorSlotPatternAvoidsFalseCollisionsWhileKeepingSlotPhrases() {
        var vConv = vendorConversation();
        when(vendorAiData.findProfileByUserId(vendorUserId)).thenReturn(Optional.of(
                new VendorProfile(vendorId, vendorUserId, "Vendor", BigDecimal.ZERO, 0, "NONE")));
        when(vendorAiData.listUpcomingSlots(eq(vendorId), isNull(), anyInt())).thenReturn(List.of());

        for (String slotQuestion : List.of("ca", "Kiểm tra ca tour sáng mai", "Tôi có nên mở thêm ca hoặc gộp ca không?")) {
            var resp = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, vConv.id(),
                    slotQuestion, null, null, SupportedLanguage.VI);
            assertThat(resp.toolsUsed()).as(slotQuestion).containsExactly("vendor_slot_advisor");
        }

        for (String nonSlotQuestion : List.of(
                "Chính sách câu cá và cả hai hoặc cả ba quy định cá biệt thì sao?",
                "Tôi muốn tăng giá tour đã được khách thanh toán, hãy cập nhật tất cả booking cũ.")) {
            var resp = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, vConv.id(),
                    nonSlotQuestion, null, null, SupportedLanguage.VI);
            assertThat(resp.toolsUsed()).as(nonSlotQuestion).doesNotContain("vendor_slot_advisor");
        }
    }

    @Test
    void v18AndV22CiteVerifiedReviewsSeparateHypothesesAndRefuseZeroReviewFabrication() {
        var vConv = vendorConversation();
        when(vendorAiData.findProfileByUserId(vendorUserId)).thenReturn(Optional.of(
                new VendorProfile(vendorId, vendorUserId, "Danasea Watersports", BigDecimal.valueOf(4.8), 30, "TOP_RATED")));

        UUID revWaitId = UUID.randomUUID();
        UUID revGoodId = UUID.randomUUID();
        when(vendorAiData.listReviews(eq(vendorId), isNull(), anyInt())).thenReturn(List.of(
                new VendorReviewItem(revWaitId, UUID.randomUUID(), "Tour Cano", 2,
                        "Khách phải chờ cano tại bến hơn 30 phút mới xuất bến.", OffsetDateTime.now()),
                new VendorReviewItem(revGoodId, UUID.randomUUID(), "Tour Cano", 5,
                        "Hướng dẫn viên rất nhiệt tình.", OffsetDateTime.now())
        ));

        var v18 = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, vConv.id(),
                "Tại sao khách thường phàn nàn về thời gian chờ cano?",
                null, null, SupportedLanguage.VI);
        assertThat(v18.toolsUsed()).containsExactly("vendor_review_coaching");
        assertThat(v18.message())
                .contains("Bằng chứng từ đánh giá đã xác minh")
                .contains("Giả thuyết vận hành cần kiểm chứng thêm")
                .contains(revWaitId.toString())
                .contains("2/5")
                .contains("chờ cano tại bến hơn 30 phút")
                .contains("cỡ mẫu còn nhỏ");

        // V22 without attachment: refuses to pass off shop-wide 4.8 rating
        var v22NoAttach = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, vConv.id(),
                "Dịch vụ tôi mới đăng chưa có đánh giá, AI có thể nhận xét chất lượng không?",
                null, null, SupportedLanguage.VI);
        assertThat(v22NoAttach.message()).contains("0 đánh giá", "totalReviewsAnalyzed = 0").doesNotContain("4.8");
        assertThat(v22NoAttach.artifacts()).isEmpty();

        // V22 with attached zero-review service
        UUID newServiceId = UUID.randomUUID();
        when(vendorAiData.getService(vendorId, newServiceId)).thenReturn(Optional.of(
                new VendorServiceItem(newServiceId, vendorId, "New Service", "desc", "Cano", null, 0)));
        when(vendorAiData.listReviews(eq(vendorId), eq(newServiceId), anyInt())).thenReturn(List.of());
        var v22Attached = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, vConv.id(),
                "Dịch vụ mới này chưa có đánh giá, nhận xét chất lượng giúp tôi.",
                "SERVICE", newServiceId, SupportedLanguage.VI);
        assertThat(v22Attached.toolsUsed()).containsExactly("vendor_review_coaching");
        assertThat(v22Attached.message()).contains("0 đánh giá", "totalReviewsAnalyzed = 0").doesNotContain("4.8");
    }

    @Test
    void findMentionedServiceResolvesSingleKeywordMatchAndPromptsWhenAmbiguous() {
        var vConv = vendorConversation();
        when(vendorAiData.findProfileByUserId(vendorUserId)).thenReturn(Optional.of(
                new VendorProfile(vendorId, vendorUserId, "Danasea Watersports", BigDecimal.valueOf(4.5), 10, "TOP_RATED")));
        UUID supId = UUID.randomUUID();
        UUID kayakId = UUID.randomUUID();
        when(vendorAiData.listServices(vendorId)).thenReturn(List.of(
                new VendorServiceItem(supId, vendorId, "Tour Chèo SUP Ngắm Bình Minh", "Mô tả cơ bản", "SUP", BigDecimal.ZERO, 0),
                new VendorServiceItem(kayakId, vendorId, "Tour Kayak Bãi Bụt", "Mô tả cơ bản", "Kayak", BigDecimal.ZERO, 0)
        ));
        when(vendorAiData.getService(vendorId, supId)).thenReturn(Optional.of(
                new VendorServiceItem(supId, vendorId, "Tour Chèo SUP Ngắm Bình Minh", "Mô tả cơ bản", "SUP", BigDecimal.ZERO, 0)));

        var v23 = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, vConv.id(),
                "Hãy kiểm tra bài đăng SUP của tôi còn thiếu thông tin gì.",
                null, null, SupportedLanguage.VI);
        assertThat(v23.toolsUsed()).containsExactly("vendor_optimize_listing");
        assertThat(v23.message()).contains("Điểm chất lượng bài đăng");

        var ambiguous = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, vConv.id(),
                "Hãy kiểm tra bài đăng lặn biển của tôi còn thiếu gì.",
                null, null, SupportedLanguage.VI);
        assertThat(ambiguous.artifacts()).isEmpty();
        assertThat(ambiguous.message()).contains("đính kèm dịch vụ");
    }

    @Test
    void adminVendorDiagnosticsAppliesThresholdRulesAndHandlesA10ThroughA15() {
        var aConv = adminConversation();
        var overloadedLow = new VendorDiagnosticItem(
                UUID.randomUUID(), "Vendor Overload", BigDecimal.valueOf(5000000), 20,
                10.0, 96.0, BigDecimal.valueOf(2.8), 6, "WARNING", List.of("LOW_RATING", "OVERLOADED"));
        var criticalCancel = new VendorDiagnosticItem(
                UUID.randomUUID(), "Vendor B", BigDecimal.valueOf(2000000), 10,
                45.0, 60.0, BigDecimal.valueOf(4.2), 10, "CRITICAL", List.of("HIGH_CANCELLATION"));
        when(adminAiData.getVendorDiagnostics(any(), any(), isNull()))
                .thenReturn(List.of(overloadedLow, criticalCancel));

        var a10 = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, aConv.id(),
                "Vendor có tỷ lệ hủy tour 45% có cần cảnh báo nghiêm trọng không?",
                null, null, SupportedLanguage.VI);
        assertThat(a10.toolsUsed()).containsExactly("admin_vendor_diagnostics");
        assertThat(a10.message()).contains("40.0%", "45%", "CRITICAL", "WARNING");

        var a11 = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, aConv.id(),
                "Một vendor chỉ có 2 đánh giá và điểm trung bình 2 sao, có đủ để kết luận chất lượng kém không?",
                null, null, SupportedLanguage.VI);
        assertThat(a11.toolsUsed()).containsExactly("admin_vendor_diagnostics");
        assertThat(a11.message()).contains("ratingCount >= 3", "ratingCount >= 5", "chưa đạt ngưỡng");

        var a12 = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, aConv.id(),
                "Đối tác nào có tỷ lệ lấp đầy cao nhưng điểm đánh giá thấp?",
                null, null, SupportedLanguage.VI);
        assertThat(a12.toolsUsed()).containsExactly("admin_vendor_diagnostics");
        assertThat(a12.message()).contains("Vendor Overload").doesNotContain("Vendor B");

        var a14 = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, aConv.id(),
                "Hãy đưa ra các đề xuất cải thiện hoạt động cho những vendor có tỷ lệ hủy cao.",
                null, null, SupportedLanguage.VI);
        assertThat(a14.toolsUsed()).containsExactly("admin_vendor_diagnostics");
        assertThat(a14.message()).contains("WEATHER", "VENDOR_FAULT", "Vendor B");

        var a15 = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, aConv.id(),
                "Có nên đình chỉ ngay vendor chỉ vì AI đánh giá rủi ro cao không?",
                null, null, SupportedLanguage.VI);
        assertThat(a15.toolsUsed()).containsExactly("admin_vendor_diagnostics");
        assertThat(a15.message()).contains("KHÔNG", "NEEDS_REVIEW");
    }

    @Test
    void adminOrderRiskAssessmentReadsCaseAndLiveVelocityWithoutWritingDbByDefault() throws Exception {
        var repo = mock(JpaAiAssessmentCaseRepository.class);
        var storeAdapter = new AssessmentCaseStoreAdapter(repo, mapper);
        UUID orderId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();

        var entity = new AiAssessmentCaseJpaEntity();
        entity.setId(caseId);
        entity.setKind("TRANSACTION_RISK");
        entity.setSourceId(orderId);
        entity.setRequestedBy(adminUserId);
        entity.setStatus("NEEDS_REVIEW");
        entity.setEvidenceJson(mapper.writeValueAsString(Map.of(
                "observed", Map.of("orderId", orderId, "customerId", customerUserId,
                        "failedPayments24h", 3, "orders1h", 1, "refundRequests7d", 0, "paymentStatus", "FAILED"),
                "riskSignals", List.of("THREE_FAILED_PAYMENTS_IN_24H"),
                "ruleVersion", "velocity-demo-v1",
                "fraudConfirmed", false)));
        entity.setDecisionJson(mapper.writeValueAsString(Map.of("risk",
                new DecisionResult(true, DecisionTask.RISK.wireName(), "quyet", "rev1", "v1", 5.0,
                        Map.of("needs_review", new DecisionResult.Answer("noul", "YES", 0.78, 0.78, null, Map.of())),
                        null, true))));

        when(repo.findById(caseId)).thenReturn(Optional.of(entity));
        when(repo.findBySourceIdOrderByCreatedAtDesc(orderId)).thenReturn(List.of(entity));

        assertThat(storeAdapter.findById(caseId)).isPresent();
        assertThat(storeAdapter.findBySourceId(orderId)).singleElement()
                .satisfies(c -> assertThat(c.id()).isEqualTo(caseId));

        var riskRead = mock(TransactionRiskReadPort.class);
        var riskUseCase = mock(AnalyzeTransactionRiskUseCase.class);
        var copilot = extendedCopilot(assessTextUseCase, riskRead, storeAdapter, riskUseCase);
        var aConv = adminConversation();

        // A34 with attached ORDER and existing case
        var a34Existing = copilot.processMessage(adminUserId, "ADMIN", null, aConv.id(),
                "Tôi muốn biết AI dựa trên những dữ liệu nào để đưa ra cảnh báo rủi ro cho đơn này.",
                "ORDER", orderId, SupportedLanguage.VI);
        assertThat(a34Existing.toolsUsed()).containsExactly("admin_order_risk_assessment");
        assertThat(a34Existing.artifacts()).singleElement().satisfies(card -> {
            assertThat(card.get("type")).isEqualTo("ADMIN_ORDER_RISK_ASSESSMENT_CARD");
            var data = mapper.valueToTree(card.get("data"));
            assertThat(data.path("caseId").asText()).isEqualTo(caseId.toString());
            assertThat(data.path("newCaseCreated").asBoolean()).isFalse();
            assertThat(data.path("fraudConfirmed").asBoolean()).isFalse();
            assertThat(data.path("riskSignals").toString()).contains("THREE_FAILED_PAYMENTS_IN_24H");
        });
        verifyNoInteractions(riskUseCase);

        // Live Velocity 3/5/3 read when no case exists yet (no DB write)
        UUID freshOrderId = UUID.randomUUID();
        when(repo.findBySourceIdOrderByCreatedAtDesc(freshOrderId)).thenReturn(List.of());
        when(riskRead.read(freshOrderId)).thenReturn(new TransactionRiskReadPort.Facts(
                freshOrderId, customerUserId, 3, 5, 3, "PENDING"));
        var tool = new AdminFeatureTool("admin_order_risk_assessment", adminAiData, disputeSummaryUseCase, mapper,
                storeAdapter, riskRead, riskUseCase);
        var liveNode = mapper.readTree(tool.execute(
                mapper.writeValueAsString(Map.of("orderId", freshOrderId)),
                new ToolExecutionContext(SupportedLanguage.VI, UUID.randomUUID(), adminUserId)));
        assertThat(liveNode.path("status").asText()).isEqualTo("NEEDS_REVIEW");
        assertThat(liveNode.path("newCaseCreated").asBoolean()).isFalse();
        assertThat(liveNode.path("fraudConfirmed").asBoolean()).isFalse();
        assertThat(liveNode.path("ruleVersion").asText()).isEqualTo("velocity-demo-v1");
        assertThat(liveNode.path("riskSignals").toString()).contains(
                "THREE_FAILED_PAYMENTS_IN_24H", "FIVE_ORDERS_IN_1H", "THREE_REFUND_REQUESTS_IN_7D");
        verifyNoInteractions(riskUseCase);

        // A34 without orderId/caseId asks cleanly
        var missingId = copilot.processMessage(adminUserId, "ADMIN", null, aConv.id(),
                "Tôi muốn biết AI dựa trên những dữ liệu nào để đưa ra cảnh báo rủi ro cho đơn này.",
                null, null, SupportedLanguage.VI);
        assertThat(missingId.artifacts()).isEmpty();
        assertThat(missingId.message()).contains("đính kèm đơn/hồ sơ");

        // A18/A19/A20 Velocity rules + A21/A22 guards
        var a19 = copilot.processMessage(adminUserId, "ADMIN", null, aConv.id(),
                "Một tài khoản tạo 5 đơn hàng trong 1 giờ, đây có chắc chắn là gian lận không?",
                null, null, SupportedLanguage.VI);
        assertThat(a19.message()).contains("FIVE_ORDERS_IN_1H", "KHÔNG kết luận chắc chắn là gian lận", "fraudConfirmed=false");

        var a21 = copilot.processMessage(adminUserId, "ADMIN", null, aConv.id(),
                "Quyet AI đang lỗi, hệ thống có được tự động đánh dấu giao dịch là an toàn không?",
                null, null, SupportedLanguage.VI);
        assertThat(a21.message()).contains("KHÔNG", "NEEDS_REVIEW");

        var a22 = copilot.processMessage(adminUserId, "ADMIN", null, aConv.id(),
                "Hãy xem các giao dịch rủi ro và tự động khóa tất cả tài khoản liên quan.",
                null, null, SupportedLanguage.VI);
        assertThat(a22.message()).contains("KHÔNG", "quản trị viên");
    }

    @Test
    void copilotCoversRevenueZeroDaysTimezoneCancellationsDisputeConflictsQueuesAndVendorOperations() {
        var aConv = adminConversation();
        LocalDate today = LocalDate.of(2026, 11, 18);
        when(adminAiData.getRevenueTimeSeries(any(), any(), any())).thenReturn(List.of(
                new RevenueTimeSeriesItem("2026-11-16", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                new RevenueTimeSeriesItem("2026-11-17", BigDecimal.valueOf(500000), BigDecimal.valueOf(500000), BigDecimal.ZERO, BigDecimal.valueOf(50000), BigDecimal.valueOf(450000)),
                new RevenueTimeSeriesItem("2026-11-18", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)));
        when(adminAiData.getBookingCancellationBreakdown(any(), any(), any())).thenReturn(
                new BookingCancellationBreakdown(20, 6, 14, 5, 3, 2, 1, 2,
                        Map.of("WEATHER", 5L, "VENDOR_FAULT", 3L, "CUSTOMER_CANCEL", 2L, "ADMIN_OVERRIDE", 1L, "DISPUTE", 2L, "SYSTEM_TIMEOUT", 1L)));
        when(adminAiData.getQueueOverview()).thenReturn(new AdminAiDataPort.QueueOverview(3, 8, 4));

        try (var time = mockStatic(LocalDate.class,
                withSettings().mockMaker(MockMakers.INLINE).defaultAnswer(CALLS_REAL_METHODS))) {
            time.when(() -> LocalDate.now(VIETNAM_ZONE)).thenReturn(today);

            var a05 = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, aConv.id(),
                    "Có ngày nào không phát sinh doanh thu không?", null, null, SupportedLanguage.VI);
            assertThat(a05.toolsUsed()).containsExactly("admin_revenue_timeseries");
            assertThat(a05.message()).contains("2026-11-16", "2026-11-18").doesNotContain("2026-11-17,");

            var a06 = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, aConv.id(),
                    "Số liệu doanh thu ngày hôm nay đã được tính theo giờ Việt Nam chưa?", null, null, SupportedLanguage.VI);
            assertThat(a06.toolsUsed()).containsExactly("admin_revenue_timeseries");
            assertThat(a06.message()).contains("Asia/Ho_Chi_Minh");
        }

        var a08 = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, aConv.id(),
                "Hãy phân loại các đơn bị hủy theo khách hàng, vendor, thời tiết và admin.",
                null, null, SupportedLanguage.VI);
        assertThat(a08.toolsUsed()).containsExactly("admin_booking_cancellation_breakdown");
        assertThat(a08.message()).contains("Thời tiết: 5", "Lỗi vendor: 3", "Khách hàng: 2",
                "Admin xử lý: 1", "Tranh chấp: 2", "SYSTEM_TIMEOUT: 1");

        var a16 = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, aConv.id(),
                "Những nguyên nhân nào đang tạo ra nhiều yêu cầu hoàn tiền nhất?",
                null, null, SupportedLanguage.VI);
        assertThat(a16.toolsUsed()).containsExactly("admin_booking_cancellation_breakdown");
        assertThat(a16.message()).contains("WEATHER (5)", "VENDOR_FAULT (3)");

        var a23 = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, aConv.id(),
                "Một giao dịch thanh toán chưa rõ trạng thái, tôi có nên ghi nhận doanh thu thành công ngay không?",
                null, null, SupportedLanguage.VI);
        assertThat(a23.artifacts()).isEmpty();
        assertThat(a23.message()).contains("KHÔNG", "đối soát");

        var a25 = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, aConv.id(),
                "Khách nói chưa được phục vụ nhưng vendor nói đã check-in, hãy phân tích mâu thuẫn.",
                null, null, SupportedLanguage.VI);
        assertThat(a25.artifacts()).isEmpty();
        assertThat(a25.message()).contains("tranh chấp").contains("QR check-in").contains("KHÔNG tự động");

        var a27 = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, aConv.id(),
                "Vendor đang có khiếu nại chưa giải quyết, có được đưa các đơn đó vào đối soát không?",
                null, null, SupportedLanguage.VI);
        assertThat(a27.artifacts()).isEmpty();
        assertThat(a27.message()).contains("KHÔNG", "ACTIVE_DISPUTE");

        var a29 = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, aConv.id(),
                "Hãy tóm tắt các yêu cầu đổi lịch chưa được phê duyệt.",
                null, null, SupportedLanguage.VI);
        assertThat(a29.toolsUsed()).containsExactly("admin_risk_and_support_queue_overview");
        assertThat(a29.message()).contains("WAITING_REVIEW", "8", "3", "4");

        var a30 = roleCopilotUseCase.processMessage(adminUserId, "ADMIN", null, aConv.id(),
                "Nếu hai Admin cùng xử lý một yêu cầu hỗ trợ thì hệ thống phải làm gì?",
                null, null, SupportedLanguage.VI);
        assertThat(a30.message()).contains("optimistic locking", "expectedVersion", "409");

        // Vendor operational rules & safety checks
        var vConv = vendorConversation();
        when(vendorAiData.findProfileByUserId(vendorUserId)).thenReturn(Optional.of(
                new VendorProfile(vendorId, vendorUserId, "Danasea Watersports", BigDecimal.valueOf(4.7), 20, "TOP_RATED")));
        when(vendorAiData.getPerformanceMetrics(eq(vendorId), any(), any())).thenReturn(
                new VendorAiDataPort.VendorPerformanceMetrics(BigDecimal.valueOf(10_000_000),
                        BigDecimal.valueOf(8_500_000), 10, 2, 20.0, 0, 2, 75, BigDecimal.valueOf(4.7), 20, "TOP_RATED", List.of()));
        when(vendorAiData.listUpcomingSlots(eq(vendorId), isNull(), anyInt())).thenReturn(List.of());

        var v05 = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, vConv.id(),
                "DANASEA tính hoa hồng đối tác như thế nào? Tôi có được ưu đãi trong 90 ngày đầu không?",
                null, null, SupportedLanguage.VI);
        assertThat(v05.message()).contains("ONBOARDING", "90 ngày", "CATEGORY", "PLATFORM_COMMISSION_RATE");

        var v07 = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, vConv.id(),
                "Nếu tôi hủy chuyến vì lỗi vận hành, khách hàng và doanh thu của tôi bị ảnh hưởng ra sao?",
                null, null, SupportedLanguage.VI);
        assertThat(v07.artifacts()).isEmpty();
        assertThat(v07.message()).contains("100%", "FULL_REFUND", "VENDOR_FAULT");

        var v12 = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, vConv.id(),
                "Tại sao doanh thu của tôi cao nhưng số tiền quyết toán lại thấp?",
                null, null, SupportedLanguage.VI);
        assertThat(v12.toolsUsed()).containsExactly("vendor_dashboard_summary");
        assertThat(v12.message()).contains("10.000.000", "8.500.000", "1.500.000", "ACTIVE_DISPUTE", "sao kê quyết toán");

        var v14 = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, vConv.id(),
                "Vì sao có đơn đã hoàn thành nhưng chưa được đưa vào quyết toán?",
                null, null, SupportedLanguage.VI);
        assertThat(v14.message()).contains("ACTIVE_DISPUTE", "DISPUTE_WINDOW_OPEN");

        var v30 = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, vConv.id(),
                "Ca của tôi đang kín 95% nhưng thời tiết nguy hiểm, có nên tăng sức chứa không?",
                null, null, SupportedLanguage.VI);
        assertThat(v30.toolsUsed()).containsExactly("vendor_slot_advisor");
        assertThat(v30.message()).contains("Tuyệt đối KHÔNG đề xuất tăng sức chứa");

        var v33 = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, vConv.id(),
                "Tôi muốn bảo trì một cano đang được phân bổ cho các booking, cần xử lý sao?",
                null, null, SupportedLanguage.VI);
        assertThat(v33.message()).contains("Đóng bán ngay", "thay thế", "VENDOR_FAULT");

        var v34 = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, vConv.id(),
                "Tôi muốn tăng giá tour đã được khách thanh toán, hãy cập nhật tất cả booking cũ.",
                null, null, SupportedLanguage.VI);
        assertThat(v34.artifacts()).isEmpty();
        assertThat(v34.message()).contains("KHÔNG THỂ");

        // V24: SEO rewrite without attached serviceId provides grounded sunrise draft
        when(vendorAiData.listServices(vendorId)).thenReturn(List.of(
                new VendorServiceItem(UUID.randomUUID(), vendorId, "Mô tô nước – Danabeach", "Jetski", "Jetski", BigDecimal.TEN, 1),
                new VendorServiceItem(UUID.randomUUID(), vendorId, "Trải nghiệm cano hoàng hôn Bãi Nam mới – Danabeach", "Cano mới", "Cano", BigDecimal.TEN, 0)
        ));
        var v24 = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, vConv.id(),
                "Viết lại tiêu đề và mô tả tour ngắm bình minh sao cho hấp dẫn và dễ tìm kiếm.",
                null, null, SupportedLanguage.VI);
        assertThat(v24.toolsUsed()).containsExactly("vendor_optimize_listing");
        assertThat(v24.message()).contains("Tiêu đề đề xuất:").contains("Mô tả đề xuất:").contains("bình minh");

        // V29: "chỉ mới có 1/20 khách" avoids false "mới" service match and prioritizes 1/20 slot over 0/20
        when(vendorAiData.listUpcomingSlots(eq(vendorId), isNull(), anyInt())).thenReturn(List.of(
                new VendorSlotItem(UUID.randomUUID(), UUID.randomUUID(), "Trải nghiệm cano hoàng hôn Bãi Nam mới – Danabeach",
                        today.plusDays(1), LocalTime.of(8, 0), LocalTime.of(9, 30), 20, 0, "OPEN"),
                new VendorSlotItem(UUID.randomUUID(), UUID.randomUUID(), "Mô tô nước – Danabeach",
                        today.plusDays(1), LocalTime.of(14, 0), LocalTime.of(15, 30), 20, 1, "OPEN")
        ));
        var v29 = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, vConv.id(),
                "Ca ngày mai chỉ mới có 1/20 khách, tôi nên xử lý thế nào?",
                null, null, SupportedLanguage.VI);
        assertThat(v29.toolsUsed()).containsExactly("vendor_slot_advisor");
        assertThat(v29.message()).contains("Mô tô nước – Danabeach").contains("1/20 (5.0%)");
    }
}
