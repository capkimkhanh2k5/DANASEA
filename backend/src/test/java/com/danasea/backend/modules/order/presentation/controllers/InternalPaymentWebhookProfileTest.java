package com.danasea.backend.modules.order.presentation.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.danasea.backend.modules.booking.domain.ports.FlashSaleBookingPort;
import com.danasea.backend.modules.order.application.OrderPaymentService;
import com.danasea.backend.modules.order.application.services.DisruptionDecisionService;
import com.danasea.backend.modules.order.domain.ports.CommissionPolicyPort;
import com.danasea.backend.modules.order.domain.ports.ServiceWaiverLookupPort;
import com.danasea.backend.shared.i18n.LocalizedContentSelector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class InternalPaymentWebhookProfileTest {
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withBean(
                            DisruptionDecisionService.class,
                            () -> mock(DisruptionDecisionService.class))
                    .withBean(
                            ServiceWaiverLookupPort.class,
                            () -> mock(ServiceWaiverLookupPort.class))
                    .withBean(LocalizedContentSelector.class, LocalizedContentSelector::new)
                    .withBean(
                            CommissionPolicyPort.class,
                            () -> mock(CommissionPolicyPort.class))
                    .withBean(
                            FlashSaleBookingPort.class,
                            () -> mock(FlashSaleBookingPort.class))
                    .withBean(OrderPaymentService.class, () -> mock(OrderPaymentService.class))
                    .withUserConfiguration(InternalPaymentWebhookController.class);

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod", "production"})
    void simulatedWebhookDoesNotExistOutsideTest(String profile) {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles(profile))
                .run(
                        context ->
                                assertThat(context)
                                        .doesNotHaveBean(InternalPaymentWebhookController.class));
    }

    @Test
    void simulatedWebhookDoesNotExistWithDefaultProfile() {
        runner.run(
                context ->
                        assertThat(context)
                                .doesNotHaveBean(InternalPaymentWebhookController.class));
    }

    @Test
    void simulatedWebhookIsAvailableOnlyInExplicitTestProfile() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("test"))
                .run(
                        context ->
                                assertThat(context)
                                        .hasSingleBean(InternalPaymentWebhookController.class));
    }
}
