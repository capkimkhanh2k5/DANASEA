package com.danasea.backend.modules.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
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

import com.danasea.backend.modules.ai.application.ports.DecisionModelPort;
import com.danasea.backend.modules.ai.application.ports.TravelWeatherPort;
import com.danasea.backend.modules.ai.domain.models.DecisionResult;
import com.danasea.backend.modules.ai.domain.models.TravelContext;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.danasea.backend.modules.service.application.api.AiCatalogReadApi;

@AutoConfigureMockMvc
@Transactional
class CustomerAiCatalogUpgradeIntegrationTest extends BaseSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @MockitoBean DecisionModelPort decisions;
    @MockitoBean TravelWeatherPort weather;
    private UUID owner, category;
    private final LocalDate date = LocalDate.now(TravelContext.ZONE).plusDays(1);

    @BeforeEach void setup() {
        owner = UUID.randomUUID(); category = UUID.randomUUID();
        jdbc.update("insert into users(id,email,role,full_name,is_email_verified,is_locked,created_at,updated_at) values(?,?,'CUSTOMER','Catalog test',true,false,now(),now())", owner, owner + "@catalog.test");
        jdbc.update("insert into categories(id,name,slug,is_active,requires_safety_cert,created_at,updated_at) values(?,'Catalog tests',?,true,false,now(),now())", category, "catalog-" + category);
        when(decisions.decide(any(), any())).thenAnswer(call -> DecisionResult.unavailable(call.getArgument(0), "MODEL_HTTP_429"));
        when(weather.assess(any(), any())).thenReturn(new TravelWeatherPort.Assessment("NOT_REQUIRED", true, false, null, null, null));
    }

    private UUID service(String name, int capacity, double latitude, double longitude, int rating, boolean old) {
        UUID service = UUID.randomUUID(), option = UUID.randomUUID(), slot = UUID.randomUUID();
        jdbc.update("insert into services(id,category_id,name,description,status,weather_sensitive,duration_minutes,latitude,longitude,avg_rating,rating_count,view_count,created_at,updated_at) values(?,?,?,?,'PUBLISHED',false,60,?,?,?,10,11,now(),now())",
                service, category, name, "Catalog regression evidence", latitude, longitude, rating);
        if (old) jdbc.update("update services set created_at=now()-interval '200 days' where id=?", service);
        jdbc.update("insert into service_options(id,service_id,name,option_type,pricing_unit,price,status) values(?,?,'Standard','SHARED','PER_PERSON',100000,'ACTIVE')", option, service);
        jdbc.update("insert into service_slots(id,service_id,date,start_time,end_time,capacity,booked_count,status,created_at,updated_at) values(?,?,?,'09:00','10:00',?,0,'OPEN',now(),now())", slot, service, date, capacity);
        return service;
    }

    private String request(String fields) {
        return "{\"categoryId\":\"" + category + "\",\"partySize\":1,\"from\":\"" + date + "\",\"limit\":15" + fields + "}";
    }

    private JsonNode discover(String endpoint, String body) throws Exception {
        return mapper.readTree(mvc.perform(post(endpoint).with(user(owner.toString()).roles("CUSTOMER"))
                .header("Accept-Language", "en").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test void oldAvailableServiceIsFoundAfterMoreThanFiftyHigherRankedUnavailableServices() throws Exception {
        for (int index = 0; index < 65; index++) service("Unavailable service " + index, 0, 16.1, 108.2, 5, false);
        UUID valid = service("Old available service", 10, 16.1, 108.2, 1, true);
        JsonNode result = discover("/api/ai/search", request(""));
        assertThat(result.path("status").asText()).isEqualTo("AVAILABLE");
        assertThat(result.path("candidates")).hasSize(1);
        assertThat(result.path("candidates").get(0).path("service").path("id").asText()).isEqualTo(valid.toString());
        assertThat(jdbc.queryForObject("select view_count from services where id=?", Integer.class, valid)).isEqualTo(11);
    }

    @Test void nearbyUsesDistanceAcrossWholeCatalogAndCanSelectOlderService() throws Exception {
        for (int index = 0; index < 55; index++) service("Far recent service " + index, 10, 16.3 + index * .001, 108.2, 5, false);
        UUID closest = service("Closest old service", 10, 16.1001, 108.2001, 1, true);
        JsonNode result = discover("/api/ai/nearby", request(",\"latitude\":16.1,\"longitude\":108.2"));
        assertThat(result.path("candidates")).hasSize(15);
        assertThat(result.path("candidates").get(0).path("service").path("id").asText()).isEqualTo(closest.toString());
        assertThat(result.path("candidates").get(0).path("distanceKm").asDouble()).isLessThan(.1);
    }

    @Test void stableCatalogPaginationReturnsAtMostFifteenDistinctServicesPerPage() throws Exception {
        for (int index = 0; index < 32; index++) service("Available service " + index, 10, 16.1, 108.2, 4, false);
        JsonNode first = discover("/api/ai/search", request(""));
        JsonNode second = discover("/api/ai/search", request(",\"offset\":15"));
        assertThat(first.path("candidates")).hasSize(15); assertThat(second.path("candidates")).hasSize(15);
        Set<String> ids = new HashSet<>();
        first.path("candidates").forEach(candidate -> ids.add(candidate.path("service").path("id").asText()));
        second.path("candidates").forEach(candidate -> assertThat(ids).doesNotContain(candidate.path("service").path("id").asText()));
        assertThat(first.path("nextOffset").asInt()).isEqualTo(15);
        assertThat(second.path("nextOffset").asInt()).isEqualTo(30);
        JsonNode last = discover("/api/ai/search", request(",\"offset\":30"));
        assertThat(last.path("candidates")).hasSize(2); assertThat(last.path("nextOffset").isNull()).isTrue();
    }

    @Test void unavailableDecisionModelPreservesNativeCatalogSearchResult() throws Exception {
        UUID valid = service("Kayak model offline", 10, 16.1, 108.2, 4, false);
        JsonNode result = discover("/api/ai/recommendations", request(",\"query\":\"kayak\""));
        assertThat(result.path("status").asText()).isEqualTo("AVAILABLE");
        assertThat(result.path("candidates").get(0).path("service").path("id").asText()).isEqualTo(valid.toString());
        assertThat(result.path("candidates").get(0).path("relevance").path("available").asBoolean()).isFalse();
    }

    @Test void weatherFilteredPageRemainsPartialAndExposesNextCatalogPage() throws Exception {
        for (int index=0; index<15; index++) service("Unsafe ranked service " + index, 10, 16.1, 108.2, 5, false);
        UUID acceptable = service("Acceptable older service", 10, 16.1, 108.2, 1, true);
        jdbc.update("update services set weather_sensitive=true where category_id=?", category);
        when(weather.assess(any(), any())).thenAnswer(call -> {
            var candidate = (AiCatalogReadApi.PublishedService) call.getArgument(0);
            boolean safe = candidate.id().equals(acceptable);
            return new TravelWeatherPort.Assessment(safe ? "ACCEPTABLE" : "UNSAFE", safe, false, null, null, "FULL");
        });
        JsonNode first = discover("/api/ai/search", request(",\"weatherSafeOnly\":true"));
        assertThat(first.path("status").asText()).isEqualTo("PARTIAL");
        assertThat(first.path("candidates")).isEmpty();
        assertThat(first.path("nextOffset").asInt()).isEqualTo(15);
        JsonNode next = discover("/api/ai/search", request(",\"weatherSafeOnly\":true,\"offset\":15"));
        assertThat(next.path("candidates")).hasSize(1);
        assertThat(next.path("candidates").get(0).path("service").path("id").asText()).isEqualTo(acceptable.toString());
    }

    @Test void excludedActivityMentionInDescriptionDoesNotMisclassifyDeclaredKayakService() throws Exception {
        UUID kayak = service("Kayak", 10, 16.1, 108.2, 4, false);
        service("SUP", 10, 16.1, 108.2, 4, false);
        jdbc.update("update services set description='Kayak trip; this service does not include SUP' where id=?", kayak);
        JsonNode result = discover("/api/ai/search", request(",\"query\":\"kayak, không SUP\""));
        assertThat(result.path("candidates")).hasSize(1);
        assertThat(result.path("candidates").get(0).path("service").path("id").asText()).isEqualTo(kayak.toString());
    }

    @Test void malformedNaturalDateIsRejectedAsBadInput() throws Exception {
        mvc.perform(post("/api/ai/search").with(user(owner.toString()).roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(request(",\"query\":\"kayak on 2026-99-40\"")))
                .andExpect(status().isBadRequest());
    }

    @Test void storedExclusionsAffectRecommendationsOnlyWhenCustomerConsentIsEnabled() throws Exception {
        UUID kayak = service("Kayak", 10, 16.1, 108.2, 4, false);
        UUID sup = service("SUP", 10, 16.1, 108.2, 4, false);
        var disabled = preference(0, false);
        var withoutConsent = discover("/api/ai/recommendations", request(",\"useSavedPreferences\":true"));
        assertThat(withoutConsent.path("candidates")).hasSize(2);
        var enabled = preference(disabled.path("version").asLong(), true);
        var withConsent = discover("/api/ai/recommendations", request(",\"useSavedPreferences\":true"));
        assertThat(withConsent.path("candidates")).hasSize(1);
        assertThat(withConsent.path("candidates").get(0).path("service").path("id").asText()).isEqualTo(sup.toString());
        preference(enabled.path("version").asLong(), false);
        var revoked = discover("/api/ai/recommendations", request(",\"useSavedPreferences\":true"));
        assertThat(revoked.path("candidates")).hasSize(2);
    }

    @Test void feedbackMustBelongToOwnedReturnedRecommendationAndRetryIsIdempotent() throws Exception {
        UUID valid = service("Returned service", 10, 16.1, 108.2, 4, false);
        UUID notReturned = service("Unavailable service", 0, 16.1, 108.2, 4, false);
        JsonNode recommendation = discover("/api/ai/recommendations", request(""));
        String recommendationId = recommendation.path("recommendationId").asText();
        String key = UUID.randomUUID().toString();
        String body = "{\"serviceId\":\"" + valid + "\",\"signal\":\"POSITIVE\"}";
        String first = feedback(owner, recommendationId, key, body, 200);
        String retry = feedback(owner, recommendationId, key, body, 200);
        assertThat(mapper.readTree(first).path("id")).isEqualTo(mapper.readTree(retry).path("id"));
        feedback(owner, recommendationId, key,
                "{\"serviceId\":\"" + valid + "\",\"signal\":\"NEGATIVE\"}", 409);
        feedback(owner, recommendationId, UUID.randomUUID().toString(),
                "{\"serviceId\":\"" + notReturned + "\",\"signal\":\"POSITIVE\"}", 400);
        UUID other = UUID.randomUUID();
        jdbc.update("insert into users(id,email,role,full_name,is_email_verified,is_locked,created_at,updated_at) values(?,?,'CUSTOMER','Other catalog test',true,false,now(),now())", other, other + "@catalog.test");
        feedback(other, recommendationId, UUID.randomUUID().toString(), body, 404);
    }

    private JsonNode preference(long version, boolean enabled) throws Exception {
        return mapper.readTree(mvc.perform(put("/api/ai/preferences").with(user(owner.toString()).roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":" + version
                        + ",\"enabled\":" + enabled + ",\"interests\":[\"sup\"],\"exclusions\":[\"kayak\"]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String feedback(UUID actor, String recommendation, String key, String body, int expected) throws Exception {
        return mvc.perform(post("/api/ai/recommendations/" + recommendation + "/feedback")
                .with(user(actor.toString()).roles("CUSTOMER")).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
    }
}
