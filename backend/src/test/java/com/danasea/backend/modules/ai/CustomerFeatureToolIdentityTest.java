package com.danasea.backend.modules.ai;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.danasea.backend.modules.ai.application.tools.CustomerFeatureTool;
import com.danasea.backend.modules.ai.application.tools.ToolExecutionContext;
import com.danasea.backend.modules.ai.application.usecases.CustomerSupportUseCase;
import com.danasea.backend.modules.ai.application.usecases.PlanItineraryUseCase;
import com.danasea.backend.modules.ai.application.usecases.PlanItineraryUseCase.PlanningDiagnostics;
import com.danasea.backend.modules.ai.application.usecases.PlanItineraryUseCase.Replanned;
import com.danasea.backend.modules.ai.domain.exceptions.AiResourceNotFoundException;
import com.danasea.backend.modules.ai.domain.exceptions.AiStateConflictException;
import com.danasea.backend.modules.ai.domain.models.ItineraryPlan;
import com.danasea.backend.modules.ai.domain.models.ItineraryPlan.Proposal;
import com.danasea.backend.modules.ai.domain.models.ItineraryPlan.Revision;
import com.danasea.backend.modules.ai.domain.models.ItineraryPlan.Saved;
import com.danasea.backend.modules.ai.domain.models.TravelContext;
import com.danasea.backend.modules.ai.domain.services.CustomerFeatureToolDefinitions;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class CustomerFeatureToolIdentityTest {
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test void privateToolsRequireServerActorAndIgnoreUserIdInModelArguments() throws Exception {
        var support = mock(CustomerSupportUseCase.class);
        var tool = new CustomerFeatureTool("ai_customer_support", null, null, null, null, null, support, new ObjectMapper());
        UUID real = UUID.randomUUID(), spoofed = UUID.randomUUID(), order = UUID.randomUUID();
        String args = "{\"userId\":\"" + spoofed + "\",\"orderId\":\"" + order + "\",\"message\":\"Refund?\"}";
        assertThat(new ObjectMapper().readTree(tool.execute(args)).path("error").asText()).isEqualTo("AUTHENTICATION_REQUIRED");
        verifyNoInteractions(support);
        tool.execute(args, new ToolExecutionContext(SupportedLanguage.VI, UUID.randomUUID(), real));
        verify(support).execute(real, order, "Refund?", false);
        verify(support, never()).execute(eq(spoofed), any(), any(), anyBoolean());
    }

    @Test void savedItineraryToolsRequireAuthEnforceOwnerAndExposeStructuredContracts() throws Exception {
        var plans = mock(PlanItineraryUseCase.class);
        UUID owner = UUID.randomUUID(), itineraryId = UUID.randomUUID(), serviceId = UUID.randomUUID(),
                optionId = UUID.randomUUID(), slotId = UUID.randomUUID();
        LocalDate tomorrow = LocalDate.now(TravelContext.ZONE).plusDays(1);
        var ctx = new TravelContext("SUP", null, null, 2, tomorrow, tomorrow, null, null, null, null, null, List.of(), 15, 3, false);
        var item = new ItineraryPlan.Item(serviceId, optionId, slotId, "Chèo SUP Mân Thái",
                tomorrow.atTime(9, 0), tomorrow.atTime(10, 0), 2, new BigDecimal("100000"), new BigDecimal("200000"),
                16.086, 108.250, 0, "NOT_REQUIRED", false, null);
        var plan = new ItineraryPlan("PROPOSED", ctx, new BigDecimal("200000"), List.of(item), List.of(), "TEST", false);
        var saved = new Saved(itineraryId, owner, 2L, plan, OffsetDateTime.now(), OffsetDateTime.now(), "ACCEPTED", false);
        var actor = new ToolExecutionContext(SupportedLanguage.VI, UUID.randomUUID(), owner);

        // ai_list_itineraries
        var listTool = new CustomerFeatureTool("ai_list_itineraries", null, null, plans, null, null, null, mapper);
        assertThat(mapper.readTree(listTool.execute("{}")).path("error").asText()).isEqualTo("AUTHENTICATION_REQUIRED");
        when(plans.list(owner, 0, 10)).thenReturn(List.of());
        assertThat(mapper.readTree(listTool.execute("{}", actor)).path("status").asText()).isEqualTo("NO_ITINERARIES");
        when(plans.list(owner, 0, 10)).thenReturn(List.of(saved));
        JsonNode listJson = mapper.readTree(listTool.execute("{}", actor));
        assertThat(listJson.path("status").asText()).isEqualTo("AVAILABLE");
        assertThat(listJson.path("count").asInt()).isEqualTo(1);
        assertThat(listJson.path("itineraries").get(0).path("index").asInt()).isEqualTo(1);
        assertThat(listJson.path("itineraries").get(0).path("items").get(0).path("index").asInt()).isEqualTo(1);

        // ai_get_itinerary
        var getTool = new CustomerFeatureTool("ai_get_itinerary", null, null, plans, null, null, null, mapper);
        when(plans.get(itineraryId, owner)).thenReturn(saved);
        when(plans.proposals(itineraryId, owner)).thenReturn(List.of());
        JsonNode getJson = mapper.readTree(getTool.execute("{\"itineraryId\":\"" + itineraryId + "\"}", actor));
        assertThat(getJson.path("status").asText()).isEqualTo("AVAILABLE");
        assertThat(getJson.path("version").asLong()).isEqualTo(2L);
        UUID foreignId = UUID.randomUUID();
        when(plans.get(foreignId, owner)).thenThrow(new AiResourceNotFoundException("Itinerary not found"));
        assertThat(mapper.readTree(getTool.execute("{\"itineraryId\":\"" + foreignId + "\"}", actor)).path("error").asText())
                .isEqualTo("ITINERARY_NOT_FOUND");

        // ai_itinerary_history
        var historyTool = new CustomerFeatureTool("ai_itinerary_history", null, null, plans, null, null, null, mapper);
        var rev0 = new Revision(0L, "DRAFT", plan, "DRAFT_CREATED", null, OffsetDateTime.now().minusHours(1));
        var restoredProp = new Proposal(UUID.randomUUID(), itineraryId, 2L, "PENDING", plan,
                List.of(), List.of(), List.of(), "CUSTOMER_RESTORE_REVISION", null, OffsetDateTime.now(), false);
        when(plans.revisions(itineraryId, owner)).thenReturn(List.of(rev0));
        when(plans.restoreFromRevision(itineraryId, owner, 2L, 0L, SupportedLanguage.VI)).thenReturn(restoredProp);
        JsonNode histJson = mapper.readTree(historyTool.execute(
                "{\"itineraryId\":\"" + itineraryId + "\",\"restoreRevisionVersion\":0,\"expectedVersion\":2}", actor));
        assertThat(histJson.path("status").asText()).isEqualTo("AVAILABLE");
        assertThat(histJson.path("bookingChanged").asBoolean()).isFalse();
        assertThat(histJson.path("restoredProposal").path("trigger").asText()).isEqualTo("CUSTOMER_RESTORE_REVISION");
        assertThat(mapper.readTree(historyTool.execute("{\"itineraryId\":\"" + itineraryId + "\",\"restoreRevisionVersion\":0}", actor))
                .path("error").asText()).isEqualTo("INVALID_ARGUMENTS");
    }

    @Test void replanToolHandlesWindowMoveVersionConflictAndHorizonValidation() throws Exception {
        var plans = mock(PlanItineraryUseCase.class);
        UUID owner = UUID.randomUUID(), itineraryId = UUID.randomUUID();
        var actor = new ToolExecutionContext(SupportedLanguage.VI, UUID.randomUUID(), owner);
        LocalDate tomorrow = LocalDate.now(TravelContext.ZONE).plusDays(1);
        var ctx = new TravelContext("SUP", null, null, 2, tomorrow, tomorrow, null, null, null, null, null, List.of(), 15, 3, false);
        var plan = new ItineraryPlan("PROPOSED", ctx, BigDecimal.ZERO, List.of(), List.of(), "TEST", false);
        var saved = new Saved(itineraryId, owner, 1L, plan, OffsetDateTime.now(), OffsetDateTime.now(), "ACCEPTED", false);
        var prop = new Proposal(UUID.randomUUID(), itineraryId, 1L, "PENDING", plan, List.of(), List.of(), List.of(), "CUSTOMER_REQUEST", null, OffsetDateTime.now(), false);
        when(plans.replanItemToWindow(eq(itineraryId), eq(owner), eq(1L), eq(2), eq(LocalTime.of(13, 0)), eq(LocalTime.of(18, 0)),
                isNull(), anySet(), anySet(), eq(SupportedLanguage.VI)))
                .thenReturn(new Replanned(saved, List.of(), List.of(), List.of(), "CUSTOMER_REQUEST", false, prop));
        when(plans.lastDiagnostics()).thenReturn(new PlanningDiagnostics(5L, 2, 4, 4, Map.of("REJECTED_BY_TRANSIT_TIME", 1)));

        var replanTool = new CustomerFeatureTool("ai_replan_itinerary", null, null, plans, null, null, null, mapper);
        JsonNode moved = mapper.readTree(replanTool.execute(
                "{\"itineraryId\":\"" + itineraryId + "\",\"expectedVersion\":1,\"targetItemIndex\":2,\"targetDayStart\":\"13:00\",\"targetDayEnd\":\"18:00\"}",
                actor));
        assertThat(moved.path("status").asText()).isEqualTo("PROPOSED");
        assertThat(moved.path("itineraryId").asText()).isEqualTo(itineraryId.toString());
        assertThat(moved.path("choicesEvaluated").asInt()).isEqualTo(4);
        assertThat(moved.path("rejectionCounts").path("REJECTED_BY_TRANSIT_TIME").asInt()).isEqualTo(1);

        when(plans.replan(eq(itineraryId), eq(owner), eq(0L), isNull(), anySet(), anySet(), anyString(), eq(SupportedLanguage.VI)))
                .thenThrow(new AiStateConflictException("Itinerary version has changed"));
        assertThat(mapper.readTree(replanTool.execute("{\"itineraryId\":\"" + itineraryId + "\",\"expectedVersion\":0}", actor))
                .path("error").asText()).isEqualTo("VERSION_CONFLICT_RELOAD_REQUIRED");

        // X06: ai_plan_itinerary rejects dates beyond 14 days
        var planTool = new CustomerFeatureTool("ai_plan_itinerary", null, null, plans, null, null, null, mapper);
        String future30 = LocalDate.now(TravelContext.ZONE).plusDays(30).toString();
        assertThat(mapper.readTree(planTool.execute("{\"criteria\":{\"query\":\"SUP\",\"from\":\"" + future30 + "\"}}", actor))
                .path("error").asText()).isEqualTo("INVALID_ARGUMENTS");

        // Definitions include all new customer tools and ai_marine_briefing
        List<String> definedNames = CustomerFeatureToolDefinitions.definitions().stream()
                .map(def -> (String) def.get("name")).toList();
        assertThat(definedNames).contains("ai_list_itineraries", "ai_get_itinerary", "ai_itinerary_history", "ai_marine_briefing");
    }
}
