package com.danasea.backend.modules.ai;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.ai.application.ports.*;
import com.danasea.backend.modules.ai.domain.exceptions.AiModerationUnavailableException;
import com.danasea.backend.modules.ai.domain.models.DecisionResult;
import com.danasea.backend.modules.ai.domain.models.TravelContext;
import com.danasea.backend.modules.ai.domain.services.ChatHistoryService;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@Transactional
class CustomerAiHttpIntegrationTest extends BaseSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired AiExtendedInsightStorePort insights;
    @Autowired ChatHistoryService chatHistory;
    @MockitoBean DecisionModelPort decisions;
    @MockitoBean TravelWeatherPort weather;
    @MockitoBean ReviewHighlightPort highlights;
    @MockitoBean RateLimiterPort limiter;
    @MockitoBean ModerationPort moderation;
    private UUID owner, other, admin, serviceId, slotId, optionId, categoryId;

    @BeforeEach void setup() {
        owner = account("CUSTOMER"); other = account("CUSTOMER"); admin = account("ADMIN");
        categoryId = UUID.randomUUID();
        jdbc.update("insert into categories(id,name,slug,is_active,requires_safety_cert,created_at,updated_at) values(?,'AI HTTP regression',?,true,false,now(),now())", categoryId, "http-test-" + categoryId);
        serviceId = UUID.randomUUID(); slotId = UUID.randomUUID(); optionId = UUID.randomUUID();
        jdbc.update("insert into services(id,name,description,status,weather_sensitive,duration_minutes,view_count,created_at,updated_at) values(?,?,?,'PUBLISHED',false,60,7,now(),now())",
                serviceId, "Kayak test", "Coastal kayaking");
        jdbc.update("update services set category_id=? where id=?", categoryId, serviceId);
        jdbc.update("insert into service_options(id,service_id,name,option_type,pricing_unit,price,max_pax_per_package,status) values(?,?,?,'PRIVATE','PER_PACKAGE',300000,2,'ACTIVE')",
                optionId, serviceId, "Private kayak");
        jdbc.update("insert into service_slots(id,service_id,date,start_time,end_time,capacity,booked_count,status,created_at,updated_at) values(?,?,?,?,?,10,0,'OPEN',now(),now())",
                slotId, serviceId, LocalDate.now(TravelContext.ZONE).plusDays(1), LocalTime.of(9,0), LocalTime.of(10,0));
        jdbc.update("update service_slots set inventory_type='SHARED_CAPACITY_UNITS' where id=?", slotId);
        for (int number = 1; number <= 3; number++) jdbc.update("insert into service_slot_units(id,slot_id,unit_number,capacity,booked_count) values(?,?,?,2,0)", UUID.randomUUID(), slotId, number);
        when(decisions.decide(any(), any())).thenAnswer(c -> DecisionResult.unavailable(c.getArgument(0), "TEST_OFFLINE"));
        when(weather.assess(any(), any())).thenReturn(new TravelWeatherPort.Assessment("NOT_REQUIRED", true, false, null, null, null));
        when(highlights.select(any(), any())).thenReturn(List.of());
        when(limiter.isAllowed(any(), any(), any())).thenReturn(true);
    }
    private UUID account(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users(id,email,role,full_name,is_email_verified,is_locked,created_at,updated_at) values(?,?,?,?,true,false,now(),now())",
                id, id + "@example.test", role, "AI test account");
        return id;
    }
    private String request() { return "{\"categoryId\":\"" + categoryId + "\",\"query\":\"Kayak test\",\"partySize\":3,\"from\":\"" + LocalDate.now(TravelContext.ZONE).plusDays(1) + "\"}"; }

    @Test void copilotConversationsStayWithinTheirRoleAndOwner() throws Exception {
        UUID otherAdmin = account("ADMIN");
        var own = insights.createCopilotConversation(admin, "ADMIN", null, "vi", "Own report", null, null);
        insights.createCopilotConversation(otherAdmin, "ADMIN", null, "vi", "Other report", null, null);
        mvc.perform(get("/api/admin/ai/copilot/conversations").with(user(admin.toString()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(own.id().toString()));
        mvc.perform(get("/api/admin/ai/copilot/conversations/" + own.id() + "/history")
                .with(user(otherAdmin.toString()).roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/ai/copilot/conversations/" + own.id()).with(user(admin.toString()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"  Weekly  \",\"pinned\":true,\"archived\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Weekly"));
        mvc.perform(get("/api/admin/ai/copilot/conversations").param("archived", "false")
                .with(user(admin.toString()).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/admin/ai/copilot/conversations/" + own.id() + "/history")
                .with(user(admin.toString()).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete("/api/admin/ai/copilot/conversations/" + own.id())
                .with(user(admin.toString()).roles("ADMIN")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/ai/copilot/conversations").with(user(admin.toString()).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test void copilotDistinguishesProviderOutageFromBlockedInput() throws Exception {
        String path = "/api/admin/ai/copilot/chat";
        when(moderation.isSafe("doanh thu")).thenThrow(new AiModerationUnavailableException());
        mvc.perform(post(path).with(user(admin.toString()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"doanh thu\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_SERVICE_UNAVAILABLE"));
        when(moderation.isSafe("blocked")).thenReturn(false);
        mvc.perform(post(path).with(user(admin.toString()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"blocked\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_MODERATION_BLOCKED"));
        assertThat(insights.listCopilotConversations(admin, "ADMIN", null, null, 10)).isEmpty();
    }

    @Test void customerConversationsCanBeRenamedArchivedAndDeletedByTheirOwner() throws Exception {
        UUID own = chatHistory.getOrCreateConversation(null, owner).getId();
        UUID foreign = chatHistory.getOrCreateConversation(null, other).getId();
        String base = "/api/assistant/conversations";
        mvc.perform(get(base).with(user(owner.toString()).roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(own.toString()));
        mvc.perform(patch(base + "/" + foreign).with(user(owner.toString()).roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"forbidden\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch(base + "/" + own).with(user(owner.toString()).roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"  Weekend  \",\"pinned\":true,\"archived\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Weekend"))
                .andExpect(jsonPath("$.pinned").value(true));
        mvc.perform(get(base).param("archived", "false").with(user(owner.toString()).roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(base).param("archived", "true").with(user(owner.toString()).roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(delete(base + "/" + own).with(user(owner.toString()).roles("CUSTOMER")))
                .andExpect(status().isNoContent());
        mvc.perform(get(base + "/" + own).with(user(owner.toString()).roles("CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    @Test void marineBriefingUpdatesTheSamePersistentRecord() {
        UUID orderId = UUID.randomUUID();
        UUID subOrderId = UUID.randomUUID();
        var first = insights.saveMarineBriefing(orderId, subOrderId, owner, slotId, "UNKNOWN", "{}");
        var second = insights.saveMarineBriefing(orderId, subOrderId, owner, slotId, "UNSAFE", "{}");

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.weatherStatus()).isEqualTo("UNSAFE");
        assertThat(insights.listMarineBriefingsByMasterOrder(orderId)).hasSize(1);
    }

    @Test void flywayMigratesThroughV21AndCatalogReadsPreserveViews() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version='21' and success=true", Integer.class)).isEqualTo(1);
        var body = mvc.perform(post("/api/ai/search").with(user(owner.toString()).roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(request())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var candidates = mapper.readTree(body).path("candidates");
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).path("minimumPartyTotal").decimalValue()).isEqualByComparingTo(new BigDecimal("600000"));
        assertThat(jdbc.queryForObject("select view_count from services where id=?", Integer.class, serviceId)).isEqualTo(7);
    }
    @Test void unauthenticatedCustomerEndpointAndCustomerAdminEndpointAreBlocked() throws Exception {
        mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/ai/assessment-cases").with(user(owner.toString()).roles("CUSTOMER"))).andExpect(status().isForbidden());
    }
    @Test void persistedItineraryIsPrivateAndReplanUsesOptimisticVersion() throws Exception {
        var saved = mapper.readTree(mvc.perform(post("/api/ai/itineraries").with(user(owner.toString()).roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(request())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String id = saved.path("id").asText();
        assertThat(saved.path("plan").path("items")).hasSize(1);
        mvc.perform(get("/api/ai/itineraries/" + id).with(user(other.toString()).roles("CUSTOMER"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/ai/itineraries/" + id).with(user(owner.toString()).roles("CUSTOMER"))).andExpect(status().isOk());
        UUID alternativeSlot = UUID.randomUUID();
        jdbc.update("insert into service_slots(id,service_id,date,start_time,end_time,capacity,booked_count,status,inventory_type,created_at,updated_at) values(?,?,?,?,?,10,0,'OPEN','SHARED_CAPACITY_UNITS',now(),now())",
                alternativeSlot, serviceId, LocalDate.now(TravelContext.ZONE).plusDays(1), LocalTime.of(11, 0), LocalTime.of(12, 0));
        for (int number=1; number<=3; number++) jdbc.update("insert into service_slot_units(id,slot_id,unit_number,capacity,booked_count) values(?,?,?,2,0)", UUID.randomUUID(), alternativeSlot, number);
        String change = "{\"expectedVersion\":0,\"excludedSlotIds\":[\"" + slotId + "\"]}";
        var preview = mapper.readTree(mvc.perform(post("/api/ai/itineraries/" + id + "/replan").with(user(owner.toString()).roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(change)).andExpect(status().isOk()).andExpect(jsonPath("$.itinerary.version").value(0))
                .andExpect(jsonPath("$.bookingChanged").value(false)).andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/ai/itineraries/" + id).with(user(owner.toString()).roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plan.items").isNotEmpty());
        mvc.perform(post("/api/ai/itineraries/" + id + "/proposals/" + preview.path("proposal").path("id").asText() + "/accept")
                .with(user(owner.toString()).roles("CUSTOMER")).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(post("/api/ai/itineraries/" + id + "/replan").with(user(owner.toString()).roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(change)).andExpect(status().isConflict());
    }
    @Test void currentHoldsRemoveSlotsFromAiResults() throws Exception {
        jdbc.update("update service_slot_units set booked_count=capacity where slot_id=?", slotId);
        mvc.perform(post("/api/ai/search").with(user(owner.toString()).roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(request())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_MATCHES"));
    }
    @Test void sourceReviewsExcludeFlaggedRecordsAndReturnActualEvidence() throws Exception {
        jdbc.update("insert into reviews(id,service_id,rating,comment,is_flagged,created_at,updated_at) values(?,?,5,'Good guide',false,now(),now())", UUID.randomUUID(), serviceId);
        jdbc.update("insert into reviews(id,service_id,rating,comment,is_flagged,created_at,updated_at) values(?,?,1,'Flagged content',true,now(),now())", UUID.randomUUID(), serviceId);
        mvc.perform(get("/api/ai/review-summaries/" + serviceId).with(user(owner.toString()).roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sampledReviews").value(1)).andExpect(jsonPath("$.totalPublicReviews").value(1))
                .andExpect(jsonPath("$.highlights[0].comment").value("Good guide"));
    }
    @Test void moderationCasePersistsAndOnlyAdminCanResolveItOnce() throws Exception {
        var body = mapper.readTree(mvc.perform(post("/api/ai/content-assessments").with(user(owner.toString()).roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Call 0901234567\",\"persist\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publicationAuthorized").value(false)).andReturn().getResponse().getContentAsString());
        String id = body.path("caseRecord").path("id").asText();
        assertThat(id).isNotBlank();
        String resolution = "{\"resolution\":\"DISMISSED\",\"note\":\"Reviewed source manually\"}";
        mvc.perform(post("/api/admin/ai/assessment-cases/" + id + "/resolve").with(user(owner.toString()).roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(resolution)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/ai/assessment-cases/" + id + "/resolve").with(user(admin.toString()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(resolution)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/ai/assessment-cases/" + id + "/resolve").with(user(admin.toString()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(resolution)).andExpect(status().isConflict());
    }
    @Test void rateLimitUsesAuthenticatedActorAndRemoteAddress() throws Exception {
        when(limiter.isAllowed(any(), any(), any())).thenReturn(false);
        mvc.perform(post("/api/ai/search").with(user(owner.toString()).roles("CUSTOMER"))
                .header("X-Forwarded-For", "1.2.3.4").contentType(MediaType.APPLICATION_JSON).content(request()))
                .andExpect(status().isTooManyRequests());
        verify(limiter).isAllowed(eq(owner.toString()), eq("127.0.0.1"), any());
        verify(decisions, never()).decide(any(), any());
    }
    @Test void supportReadsOnlyOwnedOrderAndDoesNotTouchFinancialState() throws Exception {
        UUID order = UUID.randomUUID();
        jdbc.update("insert into master_orders(id,customer_id,total_amount,status,created_at,updated_at) values(?,?,600000,'PENDING_PAYMENT',now(),now())", order, owner);
        String body = "{\"orderId\":\"" + order + "\",\"message\":\"Why is payment pending?\"}";
        mvc.perform(post("/api/ai/support").with(user(other.toString()).roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        verify(decisions, never()).decide(any(), any());
        mvc.perform(post("/api/ai/support").with(user(owner.toString()).roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.financialActionPerformed").value(false)).andExpect(jsonPath("$.order.id").value(order.toString()));
        assertThat(jdbc.queryForObject("select status from master_orders where id=?", String.class, order)).isEqualTo("PENDING_PAYMENT");
    }
    @Test void adminRiskUsesStoredPaymentSignalsAndDoesNotChangePayments() throws Exception {
        UUID order = UUID.randomUUID();
        jdbc.update("insert into master_orders(id,customer_id,total_amount,status,created_at,updated_at) values(?,?,600000,'PENDING_PAYMENT',now(),now())", order, owner);
        for (int n=0;n<3;n++) jdbc.update("insert into payments(id,master_order_id,provider,status,amount,created_at,updated_at) values(?,?,'VNPAY','FAILED',600000,now(),now())", UUID.randomUUID(), order);
        mvc.perform(post("/api/admin/ai/risk-cases").with(user(admin.toString()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":\"" + order + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.evidence.riskSignals[0]").value("THREE_FAILED_PAYMENTS_IN_24H"))
                .andExpect(jsonPath("$.evidence.fraudConfirmed").value(false));
        assertThat(jdbc.queryForObject("select count(*) from payments where master_order_id=? and status='FAILED'", Integer.class, order)).isEqualTo(3);
    }

    @Test void guestCanChatFiveTurnsThenHitsLimitAndCannotAccessSignedInConversation() throws Exception {
        when(moderation.isSafe(any())).thenReturn(true);
        String convId = null;
        for (int turn = 1; turn <= 5; turn++) {
            String payload = convId == null
                    ? "{\"message\":\"Các phương thức thanh toán được hỗ trợ là gì?\"}"
                    : "{\"conversationId\":\"" + convId + "\",\"message\":\"Các phương thức thanh toán được hỗ trợ là gì?\"}";
            var res = mvc.perform(post("/api/assistant/chat")
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.guestLimit").value(5))
                    .andExpect(jsonPath("$.guestRemainingTurns").value(5 - turn))
                    .andReturn();
            convId = mapper.readTree(res.getResponse().getContentAsString()).path("conversationId").asText();
        }
        mvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + convId + "\",\"message\":\"Hỏi thêm lần thứ 6\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GUEST_CHAT_LIMIT_REACHED"));

        mvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Đơn hàng của tôi đâu?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseStatus").value("NEEDS_INPUT"))
                .andExpect(jsonPath("$.cards[0].type").value("CUSTOMER_SUPPORT"))
                .andExpect(jsonPath("$.cards[0].status").value("NEEDS_INPUT"));

        var ownedConv = chatHistory.getOrCreateConversation(null, owner, SupportedLanguage.VI);
        mvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + ownedConv.getId() + "\",\"message\":\"Xin chào\"}"))
                .andExpect(status().isForbidden());
    }

    @Test void guestConversationIsClaimedOnLoginAndRetainsFullHistoryWhileBlockingOtherUsers() throws Exception {
        when(moderation.isSafe(any())).thenReturn(true);
        String convId = null;
        for (int turn = 1; turn <= 5; turn++) {
            String payload = convId == null
                    ? "{\"message\":\"Các phương thức thanh toán được hỗ trợ là gì?\"}"
                    : "{\"conversationId\":\"" + convId + "\",\"message\":\"Các phương thức thanh toán được hỗ trợ là gì?\"}";
            var res = mvc.perform(post("/api/assistant/chat")
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isOk())
                    .andReturn();
            convId = mapper.readTree(res.getResponse().getContentAsString()).path("conversationId").asText();
        }
        mvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + convId + "\",\"message\":\"Hỏi thêm lượt 6\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GUEST_CHAT_LIMIT_REACHED"));

        mvc.perform(post("/api/assistant/conversations/" + convId + "/claim")
                        .with(user(owner.toString()).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(owner.toString()));

        mvc.perform(get("/api/assistant/conversations").with(user(owner.toString()).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(convId));

        mvc.perform(get("/api/assistant/conversations/" + convId + "/history")
                        .with(user(owner.toString()).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10));

        mvc.perform(post("/api/assistant/chat").with(user(owner.toString()).roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + convId + "\",\"message\":\"Các phương thức thanh toán được hỗ trợ là gì?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(convId));

        mvc.perform(post("/api/assistant/conversations/" + convId + "/claim")
                        .with(user(other.toString()).roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/assistant/conversations/" + convId + "/history")
                        .with(user(other.toString()).roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + convId + "\",\"message\":\"Xin chào\"}"))
                .andExpect(status().isForbidden());
    }

    @Test void customerOrderLookupSupportsCanonicalDisplayCodesSubOrderDnsPinnedContextAndOwnershipIsolation() throws Exception {
        when(moderation.isSafe(any())).thenReturn(true);
        UUID orderId = UUID.fromString("7a2f1c4d-1234-4abc-9def-0123456789ab");
        UUID subOrderId = UUID.fromString("ec938b9f-5678-4abc-9def-0123456789ab");
        UUID vendorId = UUID.randomUUID();
        jdbc.update("insert into master_orders(id,customer_id,order_code,total_amount,status,created_at,updated_at) values(?,?,'ORD-7A2F1C4D',600000,'PAID',now(),now())",
                orderId, owner);
        jdbc.update("insert into sub_orders(id,master_order_id,vendor_id,service_id,slot_id,sub_order_code,quantity,unit_price,subtotal_amount,final_amount,status,created_at,updated_at) values(?,?,?,?,?,'SUB-EC938B9F',2,300000,600000,600000,'CONFIRMED',now(),now())",
                subOrderId, orderId, vendorId, serviceId, slotId);
        jdbc.update("insert into payments(id,master_order_id,provider,status,amount,created_at,updated_at) values(?,?,'VNPAY','SUCCESS',600000,now(),now())",
                UUID.randomUUID(), orderId);

        mvc.perform(post("/api/assistant/chat").with(user(owner.toString()).roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Kiểm tra đơn hàng #ORD-7A2F1C4D\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseStatus").value("ANSWERED"))
                .andExpect(jsonPath("$.cards[0].type").value("CUSTOMER_SUPPORT"))
                .andExpect(jsonPath("$.message").value(allOf(
                        containsString("#ORD-7A2F1C4D"),
                        containsString("PAID"))));

        mvc.perform(post("/api/assistant/chat").with(user(owner.toString()).roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Trạng thái mã #SUB-EC938B9F\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseStatus").value("ANSWERED"))
                .andExpect(jsonPath("$.message").value(allOf(
                        containsString("#ORD-7A2F1C4D"),
                        containsString("#SUB-EC938B9F"))));

        mvc.perform(post("/api/assistant/chat").with(user(owner.toString()).roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Đơn DNS-7A2F1C4D của tôi thế nào?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseStatus").value("ANSWERED"))
                .andExpect(jsonPath("$.message").value(containsString("#ORD-7A2F1C4D")));

        mvc.perform(post("/api/assistant/chat").with(user(owner.toString()).roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Đơn này thanh toán chưa?\",\"contextType\":\"ORDER\",\"contextId\":\"#ORD-7A2F1C4D\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseStatus").value("ANSWERED"))
                .andExpect(jsonPath("$.message").value(containsString("#ORD-7A2F1C4D")));

        mvc.perform(post("/api/assistant/chat").with(user(owner.toString()).roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Xem đơn hàng của tôi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseStatus").value("ANSWERED"))
                .andExpect(jsonPath("$.cards[0].type").value("ORDER_LIST"))
                .andExpect(jsonPath("$.message").value(containsString("#ORD-7A2F1C4D")));

        mvc.perform(post("/api/assistant/chat").with(user(other.toString()).roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Kiểm tra đơn hàng #ORD-7A2F1C4D\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards[0].status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(not(
                        anyOf(
                                containsString("Kayak test"),
                                containsString("600.000"),
                                containsString("600,000")))));
    }

}
