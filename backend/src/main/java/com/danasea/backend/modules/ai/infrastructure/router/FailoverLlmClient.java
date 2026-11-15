package com.danasea.backend.modules.ai.infrastructure.router;

import com.danasea.backend.modules.ai.application.ports.LlmClientPort;
import com.danasea.backend.modules.ai.domain.models.AiMessage;
import com.danasea.backend.modules.ai.domain.models.LlmResponse;
import com.danasea.backend.modules.ai.infrastructure.cliproxy.CliProxyLlmClient;
import com.danasea.backend.modules.ai.infrastructure.cliproxy.CliProxyException;
import com.danasea.backend.modules.ai.infrastructure.cliproxy.configs.CliProxyProperties;
import com.danasea.backend.shared.i18n.SupportedLanguage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Primary
@Component
public class FailoverLlmClient implements LlmClientPort {

    private final CliProxyLlmClient cliProxyLlmClient;
    private final CliProxyProperties cliProxyProperties;

    @Autowired
    public FailoverLlmClient(
            CliProxyLlmClient cliProxyLlmClient,
            CliProxyProperties cliProxyProperties
    ) {
        this.cliProxyLlmClient = cliProxyLlmClient;
        this.cliProxyProperties = cliProxyProperties;
    }

    @Override
    public LlmResponse generateResponse(List<AiMessage> messages) {
        return generateResponse(messages, SupportedLanguage.VI);
    }

    @Override
    public LlmResponse generateResponse(List<AiMessage> messages, SupportedLanguage language) {
        return requireResponse(enabledClient().generateResponse(messages, language));
    }

    @Override
    public LlmResponse generateText(List<AiMessage> messages, SupportedLanguage language, String systemPrompt) {
        return requireResponse(enabledClient().generateText(messages, language, systemPrompt));
    }

    private CliProxyLlmClient enabledClient() {
        if (cliProxyProperties == null || !cliProxyProperties.enabled()) {
            throw new CliProxyException("CLI Proxy is disabled; no AI provider is configured");
        }
        return cliProxyLlmClient;
    }

    private LlmResponse requireResponse(LlmResponse response) {
        if (response == null || ((response.getContent() == null || response.getContent().isBlank())
                && (response.getToolCalls() == null || response.getToolCalls().isEmpty()))) {
            throw new CliProxyException("CLI Proxy returned no content or tool calls");
        }
        return response;
    }
}
