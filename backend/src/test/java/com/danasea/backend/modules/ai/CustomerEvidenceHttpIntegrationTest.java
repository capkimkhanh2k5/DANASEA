package com.danasea.backend.modules.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import com.danasea.backend.modules.ai.application.api.CustomerPreferenceReadApi;
import com.danasea.backend.modules.ai.application.ports.DecisionModelPort;
import com.danasea.backend.modules.ai.application.ports.RateLimiterPort;
import com.danasea.backend.modules.ai.application.ports.ReviewHighlightPort;
import com.danasea.backend.modules.ai.application.ports.TravelWeatherPort;
import com.danasea.backend.modules.ai.domain.models.DecisionResult;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;

@AutoConfigureMockMvc
@Transactional
class CustomerEvidenceHttpIntegrationTest extends BaseSecurityIntegrationTest {
    @Autowired EntityManager entityManager;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired CustomerPreferenceReadApi preferences;
    @MockitoBean DecisionModelPort decisions;
    @MockitoBean TravelWeatherPort weather;
    @MockitoBean ReviewHighlightPort highlights;
    @MockitoBean RateLimiterPort limiter;
    private UUID owner, other, admin, service, order;
    @BeforeEach void setup() {
        owner = account(); other = account(); admin = account(); jdbc.update("update users set role='ADMIN' where id=?", admin); service = UUID.randomUUID(); order = UUID.randomUUID();
        jdbc.update("insert into services(id,name,description,status,weather_sensitive,view_count,created_at,updated_at) values(?,?,?,'PUBLISHED',false,7,now(),now())", service, "Evidence service", "Water sport");
        jdbc.update("insert into master_orders(id,customer_id,total_amount,status,created_at,updated_at) values(?,?,100000,'PENDING_PAYMENT',now(),now())", order, owner);
        when(decisions.decide(any(), any())).thenAnswer(call -> DecisionResult.unavailable(call.getArgument(0), "TEST_OFFLINE"));
        when(highlights.select(any(), any())).thenReturn(List.of());
        when(limiter.isAllowed(any(), any(), any())).thenReturn(true);
    }
    @Test void preferenceConsentAndFeedbackArePersistedAgainstOwnerResultWithoutSyntheticViews() throws Exception {
        mvc.perform(get("/api/ai/preferences").with(user(owner.toString()).roles("CUSTOMER"))).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(put("/api/ai/preferences").with(user(owner.toString()).roles("CUSTOMER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"enabled\":true,\"interests\":[\"kayak\"],\"exclusions\":[\"diving\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.interests[0]").value("kayak")).andExpect(jsonPath("$.version").value(1));
        UUID resultId = preferences.recordRecommendation(owner, List.of(service), "verified-criteria");
        String body = "{\"serviceId\":\"" + service + "\",\"signal\":\"POSITIVE\"}";
        String path = "/api/ai/recommendations/" + resultId + "/feedback";
        var first = mapper.readTree(mvc.perform(post(path).with(user(owner.toString()).roles("CUSTOMER")).header("Idempotency-Key", "vote-1")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(post(path).with(user(owner.toString()).roles("CUSTOMER")).header("Idempotency-Key", "vote-1")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(first.path("id").asText()));
        assertThat(jdbc.queryForObject("select count(*) from ai_recommendation_feedback where owner_id=?", Integer.class, owner)).isEqualTo(1);
        assertThat(preferences.load(owner).positiveServiceIds()).contains(service);
        assertThat(jdbc.queryForObject("select view_count from services where id=?", Integer.class, service)).isEqualTo(7);
        assertThat(jdbc.queryForObject("select count(*) from recently_vieweds where user_id=?", Integer.class, owner)).isZero();
    }
    @Test void feedbackRejectsForeignResultsChangedReplayPayloadAndUnlistedService() throws Exception {
        UUID resultId = preferences.recordRecommendation(owner, List.of(service), "criteria");
        String path = "/api/ai/recommendations/" + resultId + "/feedback";
        String body = "{\"serviceId\":\"" + service + "\",\"signal\":\"CLICK\"}";
        mvc.perform(post(path).with(user(other.toString()).roles("CUSTOMER")).header("Idempotency-Key", "foreign-1")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mvc.perform(post(path).with(user(owner.toString()).roles("CUSTOMER")).header("Idempotency-Key", "vote-1")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(post(path).with(user(owner.toString()).roles("CUSTOMER")).header("Idempotency-Key", "vote-1")
                .contentType(MediaType.APPLICATION_JSON).content(body.replace("CLICK", "NEGATIVE"))).andExpect(status().isConflict());
        mvc.perform(post(path).with(user(owner.toString()).roles("CUSTOMER")).header("Idempotency-Key", "vote-2")
                .contentType(MediaType.APPLICATION_JSON).content(body.replace(service.toString(), UUID.randomUUID().toString()))).andExpect(status().isBadRequest());
    }
    @Test void humanHandoffIsConfirmedIdempotentPrivateAndCancelsOnlyRequest() throws Exception {
        String body = "{\"orderId\":\"" + order + "\",\"kind\":\"HANDOFF\",\"message\":\"Need a human reply\",\"confirmed\":true}";
        var first = mapper.readTree(mvc.perform(post("/api/ai/support/requests").with(user(owner.toString()).roles("CUSTOMER"))
                .header("Idempotency-Key", "handoff-1").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.financialActionPerformed").value(false)).andExpect(jsonPath("$.inventoryReserved").value(false)).andReturn().getResponse().getContentAsString());
        String id = first.path("id").asText();
        mvc.perform(post("/api/ai/support/requests").with(user(owner.toString()).roles("CUSTOMER"))
                .header("Idempotency-Key", "handoff-1").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/ai/support/requests/" + id).with(user(other.toString()).roles("CUSTOMER"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/ai/support/requests/" + id + "/cancel").with(user(owner.toString()).roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(jdbc.queryForObject("select count(*) from ai_customer_support_requests where owner_id=?", Integer.class, owner)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from master_orders where id=?", String.class, order)).isEqualTo("PENDING_PAYMENT");
    }
    @Test void reviewPopulationAggregatesStayAccurateAndCacheInvalidatesEditedFlaggedDeletedSources() throws Exception {
        UUID low = UUID.randomUUID(), good = UUID.randomUUID();
        jdbc.update("insert into reviews(id,service_id,rating,comment,is_flagged,created_at,updated_at) values(?,?,1,'Late guide',false,now(),now())", low, service);
        jdbc.update("insert into reviews(id,service_id,rating,comment,is_flagged,created_at,updated_at) values(?,?,5,'Good equipment',false,now(),now())", good, service);
        for (int index = 0; index < 60; index++) jdbc.update("insert into reviews(id,service_id,rating,comment,is_flagged,created_at,updated_at) values(?,?,5,'Good trip',false,now(),now())", UUID.randomUUID(), service);
        String path = "/api/ai/review-summaries/" + service;
        var initial = mapper.readTree(mvc.perform(get(path).with(user(owner.toString()).roles("CUSTOMER"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPublicReviews").value(62)).andExpect(jsonPath("$.positiveRatingCount").value(61))
                .andExpect(jsonPath("$.sampledReviews").value(50)).andReturn().getResponse().getContentAsString());
        jdbc.update("update reviews set comment='Updated guide evidence', updated_at=now() where id=?", low);
        var edited = mapper.readTree(mvc.perform(get(path).with(user(owner.toString()).roles("CUSTOMER"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(edited.path("sourceFingerprint").asText()).isNotEqualTo(initial.path("sourceFingerprint").asText());
        jdbc.update("update reviews set is_flagged=true where id=?", low);
        mvc.perform(get(path).with(user(owner.toString()).roles("CUSTOMER"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalPublicReviews").value(61)).andExpect(jsonPath("$.negativeRatingCount").value(0));
        jdbc.update("delete from reviews where id=?", good);
        mvc.perform(get(path).with(user(owner.toString()).roles("CUSTOMER"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalPublicReviews").value(60)).andExpect(jsonPath("$.positiveRatingCount").value(60));
    }
    @Test void adminQueueCompletesHandoffWithCustomerResponseAndTransactionalNotifications() throws Exception {
        String body = "{\"orderId\":\"" + order + "\",\"kind\":\"HANDOFF\",\"message\":\"Need a human reply\",\"confirmed\":true}";
        var request = mapper.readTree(mvc.perform(post("/api/ai/support/requests").with(user(owner.toString()).roles("CUSTOMER"))
                .header("Idempotency-Key", "manual-handoff").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String id = request.path("id").asText();
        entityManager.flush();
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id=? and related_entity_id=?", Integer.class, owner, UUID.fromString(id))).isEqualTo(1);
        entityManager.flush();
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id=? and related_entity_id=?", Integer.class, admin, UUID.fromString(id))).isEqualTo(1);
        mvc.perform(get("/api/admin/support/requests").with(user(owner.toString()).roles("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/support/requests/" + id + "/handle").with(user(admin.toString()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"status\":\"IN_REVIEW\",\"responseNote\":\"A support agent is reviewing this request\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(post("/api/admin/support/requests/" + id + "/handle").with(user(admin.toString()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"status\":\"RESOLVED\",\"responseNote\":\"Answered\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/support/requests/" + id + "/handle").with(user(admin.toString()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1,\"status\":\"RESOLVED\",\"responseNote\":\"We have answered your payment question\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.financialActionPerformed").value(false));
        mvc.perform(get("/api/ai/support/requests/" + id).with(user(owner.toString()).roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED")).andExpect(jsonPath("$.responseNote").value("We have answered your payment question"));
        entityManager.flush();
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id=? and related_entity_id=?", Integer.class, owner, UUID.fromString(id))).isEqualTo(3);
        assertThat(jdbc.queryForObject("select status from master_orders where id=?", String.class, order)).isEqualTo("PENDING_PAYMENT");
    }
    @Test void allNewCustomerWorkflowsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/ai/preferences")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ai/support/requests")).andExpect(status().isUnauthorized());
    }
    private UUID account() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users(id,email,role,full_name,is_email_verified,is_locked,created_at,updated_at) values(?,?,'CUSTOMER','Evidence customer',true,false,now(),now())", id, id + "@example.test");
        return id;
    }
}
