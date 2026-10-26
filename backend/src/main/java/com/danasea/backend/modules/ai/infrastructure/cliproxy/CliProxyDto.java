package com.danasea.backend.modules.ai.infrastructure.cliproxy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import java.util.List;
import java.util.Map;

public final class CliProxyDto {

    private CliProxyDto() {
    }

    @JsonInclude(Include.NON_NULL)
    public record Message(
            String role,
            String content,
            List<ToolCallDto> tool_calls,
            String tool_call_id
    ) {
        public Message(String role, String content) {
            this(role, content, null, null);
        }
    }

    public record ToolCallDto(String id, String type, FunctionDto function) {
    }

    public record FunctionDto(String name, String arguments) {
    }

    @JsonInclude(Include.NON_NULL)
    public record ChatRequest(
            String model,
            List<Message> messages,
            List<Map<String, Object>> tools,
            String reasoning_effort
    ) {
        public ChatRequest(String model, List<Message> messages, List<Map<String, Object>> tools) {
            this(model, messages, tools, "high");
        }
    }

    public record ChatResponse(List<Choice> choices) {
        public record Choice(Message message) {
        }
    }
}
