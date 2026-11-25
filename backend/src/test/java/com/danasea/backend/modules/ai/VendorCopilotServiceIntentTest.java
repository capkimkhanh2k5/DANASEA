package com.danasea.backend.modules.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.danasea.backend.modules.ai.application.ports.AdminAiDataPort;
import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort;
import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort.CopilotConversation;
import com.danasea.backend.modules.ai.application.ports.DecisionModelPort;
import com.danasea.backend.modules.ai.application.ports.LlmClientPort;
import com.danasea.backend.modules.ai.application.ports.ModerationPort;
import com.danasea.backend.modules.ai.application.ports.TravelWeatherPort;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort.VendorDisputeDetail;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort.VendorEquipmentDetail;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort.VendorOrderDetail;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort.VendorPayoutDetail;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort.VendorProfile;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort.VendorRefundDetail;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort.VendorReviewDetail;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort.VendorServiceDetail;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort.VendorServiceItem;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort.VendorServicePriceOption;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort.VendorSlotItem;
import com.danasea.backend.modules.ai.domain.models.LlmResponse;
import com.danasea.backend.modules.ai.application.usecases.AdminDisputeSummaryUseCase;
import com.danasea.backend.modules.ai.application.usecases.AssessTextUseCase;
import com.danasea.backend.modules.ai.application.usecases.MarineSlotWeather;
import com.danasea.backend.modules.ai.application.usecases.PolicyChatAnswer;
import com.danasea.backend.modules.ai.application.usecases.RoleCopilotUseCase;
import com.danasea.backend.modules.ai.application.usecases.VendorListingOptimizerUseCase;
import com.danasea.backend.modules.ai.application.usecases.VendorReviewCoachingUseCase;
import com.danasea.backend.modules.ai.application.usecases.VendorSlotAdvisorUseCase;
import com.danasea.backend.modules.order.application.api.AiPolicyReader;
import com.danasea.backend.modules.order.application.usecases.GetCancellationPreviewUseCase;
import com.danasea.backend.modules.order.domain.services.RefundPolicyEngine;
import com.danasea.backend.modules.report.domain.models.TimeRange;
import com.danasea.backend.modules.service.application.api.AiCatalogReadApi;
import com.danasea.backend.shared.i18n.LocalizedMessageService;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class VendorCopilotServiceIntentTest {

    private VendorAiDataPort vendorAiData;
    private AiExtendedInsightStorePort insightStore;
    private LlmClientPort llmClient;
    private RoleCopilotUseCase copilot;

    private final UUID userId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final UUID convId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        vendorAiData = mock(VendorAiDataPort.class);
        var adminAiData = mock(AdminAiDataPort.class);
        insightStore = mock(AiExtendedInsightStorePort.class);
        var decisionModel = mock(DecisionModelPort.class);
        llmClient = mock(LlmClientPort.class);
        var moderationPort = mock(ModerationPort.class);
        var assessTextUseCase = mock(AssessTextUseCase.class);
        var mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        var reviewCoaching = new VendorReviewCoachingUseCase(vendorAiData, insightStore, decisionModel, llmClient, assessTextUseCase, mapper);
        var listingOptimizer = new VendorListingOptimizerUseCase(vendorAiData, insightStore, decisionModel, assessTextUseCase, mapper);
        var slotAdvisor = new VendorSlotAdvisorUseCase(vendorAiData, new MarineSlotWeather(mock(AiCatalogReadApi.class), mock(TravelWeatherPort.class)), insightStore, mapper);
        var disputeSummary = new AdminDisputeSummaryUseCase(adminAiData, insightStore, mapper);

        copilot = new RoleCopilotUseCase(
                vendorAiData, reviewCoaching, slotAdvisor, listingOptimizer,
                adminAiData, disputeSummary, insightStore, llmClient, moderationPort, mapper,
                new PolicyChatAnswer(new AiPolicyReader(new RefundPolicyEngine())::current,
                        mock(GetCancellationPreviewUseCase.class), LocalizedMessageService.standalone()));

        var conv = new CopilotConversation(convId, userId, "VENDOR", vendorId, "vi", null,
                false, false, "SERVICE", serviceId, 1L, OffsetDateTime.now(), OffsetDateTime.now());
        when(moderationPort.isSafe(anyString())).thenReturn(true);
        when(insightStore.getCopilotConversation(convId)).thenReturn(Optional.of(conv));
        when(vendorAiData.findProfileByUserId(userId)).thenReturn(Optional.of(
                new VendorProfile(vendorId, userId, "Kajin Vina", BigDecimal.valueOf(4.5), 10, "SILVER")));
        when(vendorAiData.countServicesByStatus(vendorId)).thenReturn(Map.of("PUBLISHED", 3L));
        when(vendorAiData.listServices(vendorId)).thenReturn(List.of(
                new VendorServiceItem(serviceId, vendorId, "Dù bay trên biển – Kajin Vina",
                        "Trải nghiệm dù bay biển Mỹ Khê", "Dù bay", BigDecimal.valueOf(4.5), 10)
        ));
        when(vendorAiData.getServiceDetail(vendorId, serviceId)).thenReturn(Optional.of(
                new VendorServiceDetail(
                        serviceId, vendorId, "Dù bay trên biển – Kajin Vina", "Dù bay", "PUBLISHED",
                        BigDecimal.valueOf(850_000), 15, 2, BigDecimal.valueOf(4.5), 10,
                        List.of(
                                new VendorServicePriceOption(UUID.randomUUID(), "Bay đơn 1 người", "PER_PERSON", BigDecimal.valueOf(850_000), 1, "ACTIVE"),
                                new VendorServicePriceOption(UUID.randomUUID(), "Bay đôi 2 người", "PER_PACKAGE", BigDecimal.valueOf(1_500_000), 2, "ACTIVE")
                        )
                )
        ));
    }

    @Test
    @DisplayName("Hỏi giá dịch vụ có tiền tố ngữ cảnh trả đúng giá & gói dịch vụ, không nhảy sang vendor_service_count")
    void servicePriceWithContextPrefixReturnsServiceDetailCard() {
        String question = "[Về [Dịch vụ / Tour] Dù bay trên biển – Kajin Vina (ID: #ORD-4190046D)] dịch vụ này có giá bao nhiêu?";
        var reply = copilot.processMessage(userId, "VENDOR", vendorId, convId, question, "SERVICE", serviceId, SupportedLanguage.VI);

        assertThat(reply.toolsUsed()).containsExactly("vendor_service_detail");
        assertThat(reply.message())
                .contains("Dù bay trên biển – Kajin Vina", "850.000", "1.500.000", "2 gói", "thẻ bên dưới")
                .doesNotContain("Gian hàng của bạn có 3 dịch vụ");
        assertThat(reply.artifacts()).singleElement().satisfies(card -> {
            assertThat(card.get("type")).isEqualTo("VENDOR_SERVICE_DETAIL_CARD");
            assertThat(card.get("tool")).isEqualTo("vendor_service_detail");
        });
        verify(vendorAiData, never()).countServicesByStatus(any());
    }

    @Test
    @DisplayName("Hỏi số ca ngày mai của dịch vụ chỉ đếm ca ngày mai và không nhảy sang vendor_service_count")
    void tomorrowSlotCountWithContextPrefixFiltersOnlyTomorrowSlots() {
        LocalDate today = LocalDate.now(TimeRange.VIETNAM_ZONE);
        LocalDate tomorrow = today.plusDays(1);
        LocalDate dayAfter = today.plusDays(2);

        when(vendorAiData.listUpcomingSlots(eq(vendorId), eq(serviceId), anyInt())).thenReturn(List.of(
                new VendorSlotItem(UUID.randomUUID(), serviceId, "Dù bay trên biển – Kajin Vina",
                        today, LocalTime.of(9, 0), LocalTime.of(9, 30), 4, 2, "OPEN"),
                new VendorSlotItem(UUID.randomUUID(), serviceId, "Dù bay trên biển – Kajin Vina",
                        tomorrow, LocalTime.of(8, 0), LocalTime.of(8, 30), 4, 1, "OPEN"),
                new VendorSlotItem(UUID.randomUUID(), serviceId, "Dù bay trên biển – Kajin Vina",
                        tomorrow, LocalTime.of(14, 0), LocalTime.of(14, 30), 4, 0, "OPEN"),
                new VendorSlotItem(UUID.randomUUID(), serviceId, "Dù bay trên biển – Kajin Vina",
                        dayAfter, LocalTime.of(10, 0), LocalTime.of(10, 30), 4, 0, "OPEN")
        ));

        String question = "[Về [Dịch vụ / Tour] Dù bay trên biển – Kajin Vina (ID: #ORD-4190046D)] ngày mai có bao nhiêu ca của dịch vụ này?";
        var reply = copilot.processMessage(userId, "VENDOR", vendorId, convId, question, "SERVICE", serviceId, SupportedLanguage.VI);

        assertThat(reply.toolsUsed()).containsExactly("vendor_slot_advisor");
        assertThat(reply.message())
                .contains("có 2 ca hoạt động", "08:00", "14:00", "thẻ bên dưới")
                .doesNotContain("Gian hàng của bạn có 3 dịch vụ", "09:00", "10:00");
        assertThat(reply.artifacts()).singleElement().satisfies(card -> {
            assertThat(card.get("type")).isEqualTo("VENDOR_SLOT_ADVISORY_CARD");
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) card.get("data");
            assertThat(data.get("totalSlotsEvaluated")).isEqualTo(2);
        });
        verify(vendorAiData, never()).countServicesByStatus(any());
    }

    @Test
    @DisplayName("Không đính kèm contextId vẫn nhận diện đúng tên dịch vụ trong tiền tố [Về ...]")
    void resolvesServiceFromContextPrefixTitleWithoutContextId() {
        var convNoAttach = new CopilotConversation(UUID.randomUUID(), userId, "VENDOR", vendorId, "vi", null,
                false, false, null, null, 1L, OffsetDateTime.now(), OffsetDateTime.now());
        when(insightStore.getCopilotConversation(convNoAttach.id())).thenReturn(Optional.of(convNoAttach));

        String question = "[Về [Dịch vụ / Tour] Dù bay trên biển – Kajin Vina (ID: #ORD-4190046D)] dịch vụ này có giá bao nhiêu?";
        var reply = copilot.processMessage(userId, "VENDOR", vendorId, convNoAttach.id(), question, null, null, SupportedLanguage.VI);

        assertThat(reply.toolsUsed()).containsExactly("vendor_service_detail");
        assertThat(reply.message()).contains("Dù bay trên biển – Kajin Vina", "850.000");
    }

    @Test
    @DisplayName("Nhận diện đúng dịch vụ đang PENDING_REVIEW qua mã ngắn #ORD-4190046D thay vì nhảy sang dịch vụ PUBLISHED khác")
    void resolvesPendingReviewServiceByShortCodeOverDifferentPublishedService() {
        UUID publishedId = UUID.fromString("2cdfabcf-13a2-550a-8f9e-283aad7053c9");
        UUID pendingId = UUID.fromString("4190046d-112a-5b93-beb2-bcc8b1cd16d4");
        var convNoAttach = new CopilotConversation(UUID.randomUUID(), userId, "VENDOR", vendorId, "vi", null,
                false, false, null, null, 1L, OffsetDateTime.now(), OffsetDateTime.now());
        when(insightStore.getCopilotConversation(convNoAttach.id())).thenReturn(Optional.of(convNoAttach));
        when(vendorAiData.listServices(vendorId)).thenReturn(List.of(
                new VendorServiceItem(publishedId, vendorId, "Mô tô nước – Kajin Vina", "Mô tả", "Mô tô nước", BigDecimal.valueOf(4.5), 5)
        ));
        when(vendorAiData.listAllOwnedServices(vendorId)).thenReturn(List.of(
                new VendorServiceItem(publishedId, vendorId, "Mô tô nước – Kajin Vina", "Mô tả", "Mô tô nước", BigDecimal.valueOf(4.5), 5),
                new VendorServiceItem(pendingId, vendorId, "Dù bay trên biển – Kajin Vina", "Mô tả", "Dù bay", BigDecimal.ZERO, 0)
        ));
        when(vendorAiData.getServiceDetail(vendorId, pendingId)).thenReturn(Optional.of(
                new VendorServiceDetail(pendingId, vendorId, "Dù bay trên biển – Kajin Vina", "Dù bay", "PENDING_REVIEW",
                        BigDecimal.valueOf(650_000), 15, 2, BigDecimal.ZERO, 0, List.of())
        ));

        String question = "[Về [Dịch vụ / Tour] Dù bay trên biển – Kajin Vina (ID: #ORD-4190046D)] dịch vụ này có giá bao nhiêu?";
        var reply = copilot.processMessage(userId, "VENDOR", vendorId, convNoAttach.id(), question, null, null, SupportedLanguage.VI);

        assertThat(reply.toolsUsed()).containsExactly("vendor_service_detail");
        assertThat(reply.message()).contains("Dù bay trên biển – Kajin Vina", "650.000").doesNotContain("Mô tô nước");
    }

    @Test
    @DisplayName("Câu hỏi đếm số dịch vụ thực sự vẫn đi đúng vendor_service_count")
    void genuineServiceCountQueriesStillRouteToServiceCount() {
        for (String q : List.of(
                "hiện tại có mấy dịch vụ?",
                "tổng dịch vụ hiện tại là bao nhiêu?",
                "Có bao nhiêu dịch vụ đã đăng?",
                "Tôi có bao nhiêu dịch vụ đang hoạt động, đang chờ duyệt và ngừng bán?"
        )) {
            var reply = copilot.processMessage(userId, "VENDOR", vendorId, convId, q, null, null, SupportedLanguage.VI);
            assertThat(reply.toolsUsed()).as(q).containsExactly("vendor_service_count");
        }
    }

    @Test
    @DisplayName("Nạp dữ liệu thật vào prompt LLM cho ORDER, DISPUTE, REFUND, EQUIPMENT, PAYOUT, SLOT, REVIEW và lưu context theo tin nhắn")
    void groundsAllPinnedEntityTypesAndPersistsMessageContext() {
        var llmAnswer = new LlmResponse();
        llmAnswer.setResponseStatus("ANSWERED");
        llmAnswer.setContent("Đã kiểm tra dữ liệu thực tế.");
        when(llmClient.generateText(any(), any(), anyString())).thenReturn(llmAnswer);
        when(insightStore.listCopilotMessages(any())).thenReturn(List.of());

        UUID orderId = UUID.randomUUID();
        UUID disputeId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();

        when(vendorAiData.getOrderDetail(vendorId, orderId)).thenReturn(Optional.of(new VendorOrderDetail(
                orderId, UUID.randomUUID(), serviceId, "Tour cano Sơn Trà",
                LocalDate.of(2026, 11, 25), LocalTime.of(9, 0), LocalTime.of(10, 30),
                2, BigDecimal.valueOf(325_000), BigDecimal.valueOf(650_000), BigDecimal.ZERO,
                BigDecimal.valueOf(650_000), BigDecimal.valueOf(65_000), BigDecimal.valueOf(585_000),
                "COMPLETED", "PAID", null, OffsetDateTime.now(), OffsetDateTime.now()
        )));
        when(vendorAiData.getDisputeDetail(vendorId, disputeId)).thenReturn(Optional.of(new VendorDisputeDetail(
                disputeId, orderId, UUID.randomUUID(), "Tour cano Sơn Trà",
                "SERVICE_NOT_AS_DESCRIBED", "Khách báo thiếu áo phao", "OPEN",
                BigDecimal.valueOf(50), BigDecimal.valueOf(650_000), "Đã cấp đủ", null,
                OffsetDateTime.now(), null
        )));
        when(vendorAiData.getRefundDetail(vendorId, refundId)).thenReturn(Optional.of(new VendorRefundDetail(
                refundId, orderId, "Tour cano Sơn Trà",
                BigDecimal.valueOf(325_000), BigDecimal.valueOf(50), "DISPUTE", "PROCESSED", "VNPAY",
                OffsetDateTime.now(), OffsetDateTime.now()
        )));
        when(vendorAiData.getEquipmentDetail(vendorId, equipmentId)).thenReturn(Optional.of(new VendorEquipmentDetail(
                equipmentId, "Cano Composite #01", "SPEEDBOAT", 12, 1, "2026-11-28T08:00..12:00 (Thay dầu)"
        )));
        when(vendorAiData.getPayoutDetail(vendorId, payoutId)).thenReturn(Optional.of(new VendorPayoutDetail(
                payoutId, UUID.randomUUID(), BigDecimal.valueOf(845_000), "APPROVED",
                "VCB-REF-001", "Rút kỳ tháng 11", null, null, OffsetDateTime.now(), null
        )));
        when(vendorAiData.getSlotDetail(vendorId, slotId)).thenReturn(Optional.of(new VendorSlotItem(
                slotId, serviceId, "Tour cano Sơn Trà",
                LocalDate.of(2026, 11, 22), LocalTime.of(14, 0), LocalTime.of(15, 30),
                8, 3, "OPEN"
        )));
        when(vendorAiData.getReviewDetail(vendorId, reviewId)).thenReturn(Optional.of(new VendorReviewDetail(
                reviewId, serviceId, "Tour cano Sơn Trà", 4, "Cano chạy êm, đợi hơi lâu", null, OffsetDateTime.now()
        )));

        record Case(String type, UUID id, String label, String question, String expectedSnippet) {}
        var cases = List.of(
                new Case("ORDER", orderId, "Đơn #01", "Nói sơ về đơn hàng này", "vendorPayoutVnd=585000"),
                new Case("DISPUTE", disputeId, "Khiếu nại #01", "Nói sơ về tranh chấp này", "SERVICE_NOT_AS_DESCRIBED"),
                new Case("REFUND", refundId, "Hoàn tiền #01", "Yêu cầu hoàn tiền này hoàn bao nhiêu?", "amountVnd=325000"),
                new Case("EQUIPMENT", equipmentId, "Cano #01", "Nói sơ về thiết bị này", "Cano Composite #01"),
                new Case("PAYOUT", payoutId, "Rút tiền #01", "Khoản chi trả này trạng thái gì?", "amountVnd=845000"),
                new Case("SLOT", slotId, "Ca 14:00", "Nói sơ về ca này", "remainingSeats=5"),
                new Case("REVIEW", reviewId, "Đánh giá 4 sao", "Nói sơ về đánh giá này", "Cano chạy êm")
        );

        for (var c : cases) {
            var reply = copilot.processMessage(
                    userId, "VENDOR", vendorId, convId, c.question(), c.type(), c.id(), c.label(), SupportedLanguage.VI);
            assertThat(reply.toolsUsed()).as(c.type()).isEmpty();
            verify(insightStore).appendCopilotMessage(
                    eq(convId), eq("USER"), eq(c.question()), eq(null), eq(null), eq(true),
                    eq(c.type()), eq(c.id()), eq(c.label()));
        }

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient, org.mockito.Mockito.times(cases.size())).generateText(any(), any(), promptCaptor.capture());
        var prompts = promptCaptor.getAllValues();
        for (int i = 0; i < cases.size(); i++) {
            assertThat(prompts.get(i))
                    .as("System prompt for " + cases.get(i).type())
                    .contains(cases.get(i).type() + " (verified, owned by this vendor)")
                    .contains(cases.get(i).expectedSnippet());
        }
    }

    @Test
    @DisplayName("Từ chối rò rỉ dữ liệu hoặc nhãn giả mạo khi entity đính kèm không thuộc gian hàng vendor")
    void deniesUnownedPinnedEntityWithoutEchoingSpoofedLabel() {
        var llmAnswer = new LlmResponse();
        llmAnswer.setResponseStatus("ANSWERED");
        llmAnswer.setContent("Không tìm thấy đơn hàng thuộc gian hàng của bạn.");
        when(llmClient.generateText(any(), any(), anyString())).thenReturn(llmAnswer);
        when(insightStore.listCopilotMessages(any())).thenReturn(List.of());

        UUID otherVendorOrderId = UUID.randomUUID();
        when(vendorAiData.getOrderDetail(vendorId, otherVendorOrderId)).thenReturn(Optional.empty());

        copilot.processMessage(userId, "VENDOR", vendorId, convId,
                "Nói sơ về đơn hàng này", "ORDER", otherVendorOrderId, "SPOOFED_SECRET_LABEL", SupportedLanguage.VI);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).generateText(any(), any(), promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("ORDER " + otherVendorOrderId + " is NOT found in this vendor's shop")
                .doesNotContain("SPOOFED_SECRET_LABEL");
    }
}
