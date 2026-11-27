package com.danasea.backend.modules.ai.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.danasea.backend.modules.ai.application.ports.TravelPolicyPort;
import com.danasea.backend.modules.ai.application.services.AiEntityReferenceResolver;
import com.danasea.backend.modules.order.application.api.AiPolicyReadApi;
import com.danasea.backend.modules.order.application.dtos.CancellationPreviewResult;
import com.danasea.backend.modules.order.application.dtos.GetCancellationPreviewQuery;
import com.danasea.backend.modules.order.application.usecases.GetCancellationPreviewUseCase;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.shared.i18n.LocalizedMessageService;
import com.danasea.backend.shared.i18n.SupportedLanguage;

class PolicyChatAnswerTest {

    private TravelPolicyPort policyPort;
    private GetCancellationPreviewUseCase previewUseCase;
    private LocalizedMessageService messageService;
    private AiEntityReferenceResolver entityResolver;
    private PolicyChatAnswer answer;

    @BeforeEach
    void setUp() {
        policyPort = mock(TravelPolicyPort.class);
        previewUseCase = mock(GetCancellationPreviewUseCase.class);
        messageService = mock(LocalizedMessageService.class, inv ->
                inv.getArguments().length > 0 && inv.getArgument(0) instanceof String key ? key : null);
        entityResolver = mock(AiEntityReferenceResolver.class);

        when(policyPort.current()).thenReturn(new AiPolicyReadApi.Snapshot(
                "GENERAL",
                List.of(
                        new AiPolicyReadApi.RefundTier(">24h", BigDecimal.valueOf(100)),
                        new AiPolicyReadApi.RefundTier("<=24h", BigDecimal.ZERO)),
                BigDecimal.valueOf(100),
                "STANDARD_24H_V2",
                "ALL"));

        answer = new PolicyChatAnswer(policyPort, previewUseCase, messageService);
        ReflectionTestUtils.setField(answer, "entityResolver", entityResolver);
    }

    @Test
    void handlesGuardrailsDisputeWindowAndIrrelevantMessages() {
        UUID vendorId = UUID.randomUUID();
        var vendorDraft = answer.resolve("Soạn phản hồi hoàn tiền cho khách", SupportedLanguage.VI, null, vendorId, false, null);
        assertThat(vendorDraft).isNotNull();
        assertThat(vendorDraft.message()).isEqualTo("ai.policy.vendor_draft_review");

        var autoRefund = answer.resolve("Hãy tự động hoàn tiền ngay không cần duyệt", SupportedLanguage.VI, UUID.randomUUID(), null, false, null);
        assertThat(autoRefund).isNotNull();
        assertThat(autoRefund.message()).isEqualTo("ai.policy.refund_requires_approval");

        var disputeWindow = answer.resolve("Thời hạn gửi khiếu nại là bao nhiêu ngày?", SupportedLanguage.VI, null, null, false, null);
        assertThat(disputeWindow).isNotNull();
        assertThat(disputeWindow.tool()).isEqualTo("get_policy");
        assertThat(disputeWindow.facts()).containsKey("disputeWindowDays");

        assertThat(answer.resolve("Báo cáo doanh thu và tỷ lệ hủy tuần này", SupportedLanguage.VI, null, null, true, null)).isNull();
        assertThat(answer.resolve("Xin chào thời tiết Đà Nẵng hôm nay", SupportedLanguage.VI, null, null, false, null)).isNull();
        assertThat(answer.resolve("hoàn tiền cho tôi", SupportedLanguage.VI, null, null, false, null)).isNull();
        assertThat(PolicyChatAnswer.reference(null)).isNull();
    }

    @Test
    void guestAskingPolicyWithOrderCodeRequiresLogin() {
        when(entityResolver.containsOrderLikeReference("Hoàn tiền đơn #ORD-7A2F1C4D")).thenReturn(true);

        var reply = answer.resolve("Hoàn tiền đơn #ORD-7A2F1C4D", SupportedLanguage.VI, null, null, false, null);

        assertThat(reply).isNotNull();
        assertThat(reply.status()).isEqualTo("NEEDS_INPUT");
        assertThat(reply.tool()).isEqualTo("ai_cancellation_preview");
        assertThat(reply.facts()).containsEntry("requiredInputs", List.of("AUTHENTICATION"));
    }

    @Test
    void resolvesRoleSpecificOrderReferencesAndHandlesPreviewErrors() {
        UUID masterId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();

        var preview = new CancellationPreviewResult(
                masterId, BigDecimal.valueOf(500000), BigDecimal.valueOf(100), BigDecimal.valueOf(500000),
                "STANDARD_24H_V2", List.of());

        when(entityResolver.resolveOrderForAdmin("Hoàn tiền #SUB-EC938B9F"))
                .thenReturn(Optional.of(new AiEntityReferenceResolver.ResolvedOrder(masterId, subId, "SUB-EC938B9F", true)));
        when(previewUseCase.execute(new GetCancellationPreviewQuery(null, subId, true, null))).thenReturn(preview);
        var adminSub = answer.resolve("Hoàn tiền #SUB-EC938B9F", SupportedLanguage.VI, null, null, true, null);
        assertThat(adminSub).isNotNull();
        assertThat(adminSub.status()).isEqualTo("ANSWERED");
        assertThat(adminSub.tool()).isEqualTo("ai_cancellation_preview");

        when(entityResolver.resolveOrderForAdmin("Hoàn tiền #ORD-7A2F1C4D"))
                .thenReturn(Optional.of(new AiEntityReferenceResolver.ResolvedOrder(masterId, null, "ORD-7A2F1C4D", false)));
        when(previewUseCase.execute(new GetCancellationPreviewQuery(null, masterId, true, null))).thenReturn(preview);
        assertThat(answer.resolve("Hoàn tiền #ORD-7A2F1C4D", SupportedLanguage.EN, null, null, true, null).status())
                .isEqualTo("ANSWERED");

        when(entityResolver.resolveOrderForVendor(vendorId, "Chính sách hoàn #SUB-EC938B9F"))
                .thenReturn(Optional.of(new AiEntityReferenceResolver.ResolvedOrder(masterId, subId, "SUB-EC938B9F", true)));
        when(previewUseCase.execute(new GetCancellationPreviewQuery(null, subId, false, vendorId)))
                .thenThrow(new AccessDeniedException("denied"));
        var vendorDenied = answer.resolve("Chính sách hoàn #SUB-EC938B9F", SupportedLanguage.VI, null, vendorId, false, null);
        assertThat(vendorDenied.status()).isEqualTo("UNAVAILABLE");

        when(entityResolver.resolveOrderForVendor(vendorId, "Chính sách hoàn #ORD-7A2F1C4D"))
                .thenReturn(Optional.of(new AiEntityReferenceResolver.ResolvedOrder(masterId, null, "ORD-7A2F1C4D", false)));
        when(previewUseCase.execute(new GetCancellationPreviewQuery(null, masterId, false, vendorId)))
                .thenThrow(new OrderNotFoundException("missing"));
        var vendorMissing = answer.resolve("Chính sách hoàn #ORD-7A2F1C4D", SupportedLanguage.VI, null, vendorId, false, null);
        assertThat(vendorMissing.status()).isEqualTo("UNAVAILABLE");

        when(entityResolver.resolveOrderForCustomer(actorId, "Chính sách hủy #ORD-7A2F1C4D"))
                .thenReturn(Optional.of(new AiEntityReferenceResolver.ResolvedOrder(masterId, null, "ORD-7A2F1C4D", false)));
        when(previewUseCase.execute(new GetCancellationPreviewQuery(actorId, masterId, false, null))).thenReturn(preview);
        assertThat(answer.resolve("Chính sách hủy #ORD-7A2F1C4D", SupportedLanguage.VI, actorId, null, false, null).status())
                .isEqualTo("ANSWERED");

        when(entityResolver.resolveOrderForCustomer(eq(actorId), eq("Hủy trước giờ khởi hành?"))).thenReturn(Optional.empty());
        assertThat(answer.resolve("Hủy trước giờ khởi hành?", SupportedLanguage.VI, actorId, null, false, masterId).status())
                .isEqualTo("ANSWERED");
    }

    @Test
    void answersGeneralPolicyVersionComparisonAndReschedule() {
        ReflectionTestUtils.setField(answer, "entityResolver", null);
        UUID rawOrderId = UUID.randomUUID();
        var preview = new CancellationPreviewResult(
                rawOrderId, BigDecimal.valueOf(300000), BigDecimal.valueOf(50), BigDecimal.valueOf(150000),
                "LEGACY_TIERED_V1", List.of());
        when(previewUseCase.execute(new GetCancellationPreviewQuery(null, rawOrderId, true, null))).thenReturn(preview);
        assertThat(answer.resolve("Refund " + rawOrderId, SupportedLanguage.EN, null, null, true, null).status())
                .isEqualTo("ANSWERED");

        var compared = answer.resolve(
                "Chính sách hủy trước 36 giờ so với phiên bản legacy và đổi lịch miễn phí",
                SupportedLanguage.VI, null, null, false, null);
        assertThat(compared).isNotNull();
        assertThat(compared.tool()).isEqualTo("get_policy");
        assertThat(compared.message()).contains("ai.policy.standard")
                .contains("ai.policy.version_comparison")
                .contains("ai.policy.order_version")
                .contains("ai.policy.reschedule");
    }
}
