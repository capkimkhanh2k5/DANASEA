package com.danasea.backend.modules.ai.infrastructure.cliproxy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import com.danasea.backend.modules.ai.infrastructure.cliproxy.configs.CliProxyProperties;

class ProxyConfigurationBindingTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class)
            .withPropertyValues("ai.cliproxy.enabled=true", "ai.cliproxy.base-url=http://localhost:8318/v1",
                    "ai.cliproxy.api-key=private-test-key", "ai.cliproxy.model=gemini-3.5-flash-lite",
                    "ai.cliproxy.fallback-model=gpt-oss-120b-medium");

    @Test void bindsModelSettingsAndDefaultsMaximumOutput() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(CliProxyProperties.class);
            assertThat(properties.model()).isEqualTo("gemini-3.5-flash-lite");
            assertThat(properties.maxOutputTokens()).isEqualTo(65536);
            assertThat(properties.maxInputTokens()).isEqualTo(1048576);
            assertThat(properties.fallbackContextTokens()).isEqualTo(131072);
            assertThat(properties.inputSafetyTokens()).isEqualTo(8192);
        });
    }

    @Test void outputCeilingRemainsConfigurable() {
        runner.withPropertyValues("ai.cliproxy.max-output-tokens=32768").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CliProxyProperties.class).maxOutputTokens()).isEqualTo(32768);
        });
    }

    @Test void rejectsCeilingBeyondSupportedMaximum() {
        runner.withPropertyValues("ai.cliproxy.max-output-tokens=65537")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test void rejectsOutputAndReserveWhichExhaustFallbackContext() {
        runner.withPropertyValues("ai.cliproxy.fallback-context-tokens=73728")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test void rejectsInputCeilingWhichCannotFitSafetyReserve() {
        runner.withPropertyValues("ai.cliproxy.max-input-tokens=8192")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CliProxyProperties.class)
    static class PropertiesConfiguration { }
}
