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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import com.danasea.backend.modules.ai.application.ports.DecisionModelPort;
import com.danasea.backend.modules.ai.application.ports.LlmClientPort;
import com.danasea.backend.modules.ai.application.ports.ModerationPort;
import com.danasea.backend.modules.ai.application.ports.TravelWeatherPort;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort.VendorProfile;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort.VendorReviewItem;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort.VendorSlotItem;
import com.danasea.backend.modules.ai.application.tools.AdminFeatureTool;
import com.danasea.backend.modules.ai.application.tools.CustomerFeatureTool;
import com.danasea.backend.modules.ai.application.tools.ToolExecutionContext;
import com.danasea.backend.modules.ai.application.tools.VendorFeatureTool;
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
        assertThat(result.drafts().get("EN").optimizedDescription()).isEmpty();
        assertThat(result.drafts().get("EN").sellingPoints()).isEmpty();
        assertThat(result.drafts().get("VI").optimizedDescription()).doesNotContain("miễn phí");
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

        var response = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, conversation.id(),
                "Doanh thu tháng này là bao nhiêu?", null, null, SupportedLanguage.VI);

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        verify(vendorAiData).getPerformanceMetrics(vendorId, today.withDayOfMonth(1), today);
        assertThat(response.message()).contains("1.300.000", "877.500", "thực thu dự kiến");
        assertThat(response.toolsUsed()).containsExactly("vendor_dashboard_summary");
        assertThat(response.artifacts()).singleElement().satisfies(card -> {
            assertThat(card.get("period")).isEqualTo(Map.of("from", today.withDayOfMonth(1).toString(), "to", today.toString()));
            assertThat(mapper.valueToTree(card.get("data")).path("gmv").decimalValue())
                    .isEqualByComparingTo("1300000");
        });

        when(insightStore.listCopilotMessages(conversation.id())).thenReturn(List.of(new CopilotMessage(
                UUID.randomUUID(), conversation.id(), "ASSISTANT", "Lời đáp cũ", null,
                "vendor_dashboard_summary", false, OffsetDateTime.now())));
        var followUp = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, conversation.id(),
                "Còn tháng trước thì sao?", null, null, SupportedLanguage.VI);
        verify(vendorAiData).getPerformanceMetrics(vendorId, today.minusMonths(1).withDayOfMonth(1),
                today.withDayOfMonth(1).minusDays(1));
        assertThat(followUp.message()).contains("1.300.000", "877.500");
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
        int year = currentYear ? LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).getYear() : 2026;
        var from = LocalDate.of(year, 10, 1);
        var to = LocalDate.of(year, 10, 31);

        var response = roleCopilotUseCase.processMessage(vendorUserId, "VENDOR", vendorId, conversation.id(),
                question, null, null, SupportedLanguage.VI);

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
}
