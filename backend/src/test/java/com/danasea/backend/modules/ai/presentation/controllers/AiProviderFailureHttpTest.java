package com.danasea.backend.modules.ai.presentation.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.danasea.backend.modules.ai.infrastructure.cliproxy.CliProxyException;
import com.danasea.backend.modules.ai.presentation.controllers.AiFeatureExceptionHandler;

class AiProviderFailureHttpTest {
    @Test void failedProviderRecoveryIsServiceUnavailableWithoutLeakingInternalErrorOrAskingForNewChat() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new UnavailableController())
                .setControllerAdvice(new AiFeatureExceptionHandler()).build();
        mvc.perform(get("/api/assistant/test-provider-failure"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("code").value("AI_SERVICE_UNAVAILABLE"));
    }

    @RestController
    static class UnavailableController {
        @GetMapping("/api/assistant/test-provider-failure") String execute() {
            throw new CliProxyException("private provider transport diagnostic");
        }
    }
}
