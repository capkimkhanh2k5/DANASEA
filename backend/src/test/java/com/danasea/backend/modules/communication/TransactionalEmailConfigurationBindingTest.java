package com.danasea.backend.modules.communication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import com.danasea.backend.modules.communication.infrastructure.email.TransactionalEmailProperties;

class TransactionalEmailConfigurationBindingTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void bindsWorkerFlagFromProductionEnvironment(boolean enabled) {
        String sourceName = StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;
        var environment = new SystemEnvironmentPropertySource(sourceName, Map.of(
                "APP_TRANSACTIONAL_EMAIL_MODE", "capture",
                "APP_TRANSACTIONAL_EMAIL_WORKER_ENABLED", Boolean.toString(enabled)));

        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().getPropertySources()
                        .replace(sourceName, environment))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(PropertiesConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(TransactionalEmailProperties.class);
                    assertThat(properties.getWorker().isEnabled()).isEqualTo(enabled);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TransactionalEmailProperties.class)
    static class PropertiesConfiguration {
    }
}
