package com.danasea.backend.modules.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.danasea.backend.modules.ai.application.ports.LlmClientPort;
import com.danasea.backend.modules.ai.application.ports.ModerationPort;
import com.danasea.backend.modules.ai.application.ports.RateLimiterPort;
import com.danasea.backend.modules.ai.domain.models.LlmResponse;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class AssistantChatIdempotencyIntegrationTest extends BaseSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @MockitoBean LlmClientPort llm;
    @MockitoBean ModerationPort moderation;
    @MockitoBean RateLimiterPort limiter;
    private UUID owner;

    @BeforeEach void setup() {
        owner = UUID.randomUUID();
        jdbc.update("insert into users(id,email,role,full_name,is_email_verified,is_locked,created_at,updated_at) values(?,?,'CUSTOMER','Assistant test',true,false,now(),now())",
                owner, owner + "@assistant.test");
        when(moderation.isSafe(any())).thenReturn(true);
        when(limiter.isAllowed(any(), any(), any())).thenReturn(true);
        LlmResponse reply = new LlmResponse(); reply.setContent("Hello from the test assistant.");
        when(llm.generateResponse(any(), any())).thenReturn(reply);
    }

    @Test void firstChatRetryReplaysSameConversationWithoutDuplicateMessagesOrModelCall() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = send(owner, key, "{\"message\":\"Hello\"}", 200);
        String retry = send(owner, key, "{\"message\":\"Hello\"}", 200);
        assertThat(mapper.readTree(retry)).isEqualTo(mapper.readTree(first));
        UUID conversation = UUID.fromString(mapper.readTree(first).path("conversationId").asText());
        assertThat(jdbc.queryForObject("select count(*) from ai_messages where conversation_id=?", Integer.class, conversation)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select structured_context from ai_conversations where id=?", String.class, conversation)).isEqualTo("{}");
        assertThat(jdbc.queryForObject("select count(*) from ai_chat_requests where actor_id=? and status='COMPLETE'", Integer.class, owner)).isEqualTo(1);
        verify(llm, times(1)).generateResponse(any(), any());
        send(owner, key, "{\"message\":\"Different request\"}", 409);
    }

    @Test void sameKeyDoesNotExposeAnotherCustomersCachedResponse() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = send(owner, key, "{\"message\":\"Hello\"}", 200);
        UUID other = UUID.randomUUID();
        jdbc.update("insert into users(id,email,role,full_name,is_email_verified,is_locked,created_at,updated_at) values(?,?,'CUSTOMER','Other assistant test',true,false,now(),now())",
                other, other + "@assistant.test");
        String second = send(other, key, "{\"message\":\"Hello\"}", 200);
        assertThat(mapper.readTree(second).path("conversationId").asText())
                .isNotEqualTo(mapper.readTree(first).path("conversationId").asText());
    }

    private String send(UUID actor, String key, String body, int expected) throws Exception {
        return mvc.perform(post("/api/assistant/chat").with(user(actor.toString()).roles("CUSTOMER"))
                .header("Accept-Language", "en").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }
}
