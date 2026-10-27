package com.danasea.backend.modules.ai;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import com.danasea.backend.modules.ai.application.ports.LlmClientPort;
import com.danasea.backend.modules.ai.domain.models.LlmResponse;
import com.danasea.backend.modules.ai.infrastructure.cliproxy.CliProxyReviewHighlightAdapter;
import com.danasea.backend.modules.operation.application.api.AiReviewReadApi.ReviewEvidence;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.databind.ObjectMapper;

class ReviewHighlightSafetyTest {
    @Test void partialModelOutputCannotSelectReviewEvidenceEvenIfJsonParses() {
        var llm = mock(LlmClientPort.class);
        var adapter = new CliProxyReviewHighlightAdapter(llm, new ObjectMapper().findAndRegisterModules());
        var source = new ReviewEvidence(UUID.randomUUID(), UUID.randomUUID(), 5, "Good", OffsetDateTime.now());
        var response = new LlmResponse();
        response.setContent("{\"reviewIds\":[\"" + source.id() + "\"]}");
        response.setResponseStatus("PARTIAL");
        when(llm.generateResponse(anyList(), eq(SupportedLanguage.VI))).thenReturn(response);
        assertThat(adapter.select(List.of(source), SupportedLanguage.VI)).isEmpty();
    }
    @Test void inventedSourceIdsAreRejectedWhileVerifiedIdsAreDeduplicated() {
        var llm = mock(LlmClientPort.class);
        var adapter = new CliProxyReviewHighlightAdapter(llm, new ObjectMapper().findAndRegisterModules());
        var source = new ReviewEvidence(UUID.randomUUID(), UUID.randomUUID(), 5, "Good", OffsetDateTime.now());
        var response = new LlmResponse();
        response.setContent("{\"reviewIds\":[\"" + UUID.randomUUID() + "\"]}");
        when(llm.generateResponse(anyList(), eq(SupportedLanguage.VI))).thenReturn(response);
        assertThat(adapter.select(List.of(source), SupportedLanguage.VI)).isEmpty();
        response.setContent("{\"reviewIds\":[\"" + source.id() + "\",\"" + source.id() + "\"]}");
        assertThat(adapter.select(List.of(source), SupportedLanguage.VI)).containsExactly(source.id());
    }
}
