package com.danasea.backend.modules.ai.domain.services;

import org.springframework.stereotype.Service;
import java.util.regex.Pattern;

@Service
public class SanitizationService {
    private static final Pattern INJECTION_PATTERNS = Pattern.compile(
            "(?i)(ignore (?:all )?(?:previous|prior) instructions|"
                    + "bỏ qua (?:toàn bộ )?(?:system prompt|chỉ dẫn|quy tắc|hướng dẫn)|"
                    + "tiết lộ toàn bộ đơn hàng|reveal all orders|"
                    + "system prompt|you are a|forget everything|bypass)"
    );

    /**
     * Sanitizes input text to prevent prompt injection or malicious instructions.
     */
    public String sanitize(String input) {
        if (input == null) return null;
        // Sanitize known multilingual prompt injection commands
        String sanitized = INJECTION_PATTERNS.matcher(input).replaceAll("[REDACTED]");
        // Escape special characters that might confuse LLMs if injected in JSON or quotes
        sanitized = sanitized.replace("\"", "\\\"");
        return sanitized;
    }
}
