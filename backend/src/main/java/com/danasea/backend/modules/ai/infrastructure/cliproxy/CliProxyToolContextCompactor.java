package com.danasea.backend.modules.ai.infrastructure.cliproxy;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class CliProxyToolContextCompactor {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> REFERENCES = List.of("id", "serviceId", "optionId", "slotId", "cardId",
            "status", "errorCode", "source", "provider");

    CliProxyDto.Message compact(CliProxyDto.Message message, int maximumChars) {
        if (!"tool".equals(message.role()) || message.content() == null || message.content().length() <= maximumChars) return message;
        ObjectNode compacted = MAPPER.createObjectNode();
        compacted.put("contextCompacted", true);
        compacted.put("sourceToolCallId", message.tool_call_id());
        compacted.put("evidenceComplete", false);
        compacted.put("fullResultInBackendCard", true);
        try {
            JsonNode source = MAPPER.readTree(message.content());
            ObjectNode references = compacted.putObject("references");
            for (String key : REFERENCES) {
                JsonNode value = source == null ? null : source.get(key);
                if (value != null && value.isValueNode() && value.toString().length() <= 128) references.set(key, value);
            }
        } catch (Exception ignored) { /* An opaque tool result still has its original call reference. */ }
        compacted.put("excerpt", prefix(message.content(), maximumChars / 2));
        if (compacted.toString().length() > maximumChars) compacted.remove("excerpt");
        if (compacted.toString().length() > maximumChars) compacted.remove("references");
        return new CliProxyDto.Message(message.role(), compacted.toString(), message.tool_calls(), message.tool_call_id());
    }

    static String prefix(String text, int maximumChars) {
        int end = Math.min(text.length(), Math.max(0, maximumChars));
        if (end > 0 && end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end);
    }
}
