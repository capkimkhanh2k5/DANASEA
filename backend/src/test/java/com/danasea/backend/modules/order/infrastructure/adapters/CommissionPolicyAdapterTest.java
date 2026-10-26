package com.danasea.backend.modules.order.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.danasea.backend.modules.flashsale.application.FlashSaleStore;
import com.danasea.backend.modules.flashsale.domain.FlashSaleException;
import com.danasea.backend.modules.flashsale.domain.FlashSalePricing.Policy;
import com.danasea.backend.modules.systemconfig.infrastructure.persistence.entities.SystemConfigJpaEntity;
import com.danasea.backend.modules.systemconfig.infrastructure.persistence.repositories.JpaSystemConfigRepository;

class CommissionPolicyAdapterTest {

    private final FlashSaleStore policies = mock(FlashSaleStore.class);
    private final JpaSystemConfigRepository configRepo = mock(JpaSystemConfigRepository.class);
    private final CommissionPolicyAdapter adapter = new CommissionPolicyAdapter(policies, configRepo);

    @Test
    @DisplayName("Returns PLATFORM_COMMISSION_RATE from system_configs as the single source of truth")
    void returnsPlatformRateFromSystemConfigs() {
        UUID vendorId = UUID.randomUUID();

        SystemConfigJpaEntity config = new SystemConfigJpaEntity();
        config.setKey("PLATFORM_COMMISSION_RATE");
        config.setValue("10.0");
        when(configRepo.findByKey("PLATFORM_COMMISSION_RATE")).thenReturn(Optional.of(config));

        BigDecimal rate = adapter.getCommissionRate(vendorId);
        assertThat(rate).isEqualByComparingTo("0.100000");
    }

    @Test
    @DisplayName("Legacy vendor policy does NOT override PLATFORM_COMMISSION_RATE")
    void legacyVendorPolicyDoesNotOverridePlatformCommissionRate() {
        UUID vendorId = UUID.randomUUID();
        // Legacy vendor policy has 25%
        Policy policy = new Policy(UUID.randomUUID(), vendorId, 1, new BigDecimal("0.250000"),
                new BigDecimal("0.50"), BigDecimal.ZERO, BigDecimal.ZERO, "CUSTOM_CONTRACT");
        when(policies.latestPolicy(vendorId)).thenReturn(policy);

        // Platform config has 10%
        SystemConfigJpaEntity config = new SystemConfigJpaEntity();
        config.setKey("PLATFORM_COMMISSION_RATE");
        config.setValue("10.0");
        when(configRepo.findByKey("PLATFORM_COMMISSION_RATE")).thenReturn(Optional.of(config));

        // Resolver must strictly return 10%, NOT 25%
        BigDecimal rate = adapter.getCommissionRate(vendorId);
        assertThat(rate).isEqualByComparingTo("0.100000");
    }

    @Test
    @DisplayName("Correctly handles decimal format in PLATFORM_COMMISSION_RATE")
    void handlesDecimalFormatInPlatformRate() {
        UUID vendorId = UUID.randomUUID();

        SystemConfigJpaEntity config = new SystemConfigJpaEntity();
        config.setKey("PLATFORM_COMMISSION_RATE");
        config.setValue("0.15");
        when(configRepo.findByKey("PLATFORM_COMMISSION_RATE")).thenReturn(Optional.of(config));

        BigDecimal rate = adapter.getCommissionRate(vendorId);
        assertThat(rate).isEqualByComparingTo("0.150000");
    }

    @Test
    @DisplayName("Throws COMMISSION_POLICY_MISSING when PLATFORM_COMMISSION_RATE is missing")
    void throwsExceptionWhenNoPolicyConfigured() {
        UUID vendorId = UUID.randomUUID();
        when(configRepo.findByKey("PLATFORM_COMMISSION_RATE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.getCommissionRate(vendorId))
                .isInstanceOf(FlashSaleException.class)
                .hasMessageContaining("COMMISSION_POLICY_MISSING");
    }

    @Test
    @DisplayName("Resolves Flash Sale policy using PLATFORM_COMMISSION_RATE and safety constraints")
    void resolvesFlashSalePolicyWithSafetyConstraints() {
        UUID vendorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();

        SystemConfigJpaEntity rateConfig = new SystemConfigJpaEntity();
        rateConfig.setId(configId);
        rateConfig.setKey("PLATFORM_COMMISSION_RATE");
        rateConfig.setValue("10.0");
        when(configRepo.findByKey("PLATFORM_COMMISSION_RATE")).thenReturn(Optional.of(rateConfig));

        SystemConfigJpaEntity ratioConfig = new SystemConfigJpaEntity();
        ratioConfig.setKey("FLASH_SALE_MAX_SUBSIDY_RATIO");
        ratioConfig.setValue("0.80");
        when(configRepo.findByKey("FLASH_SALE_MAX_SUBSIDY_RATIO")).thenReturn(Optional.of(ratioConfig));

        Policy policy = adapter.resolveFlashSalePolicy(vendorId);
        assertThat(policy.rate()).isEqualByComparingTo("0.100000");
        assertThat(policy.maximumRatio()).isEqualByComparingTo("0.800000");
        assertThat(policy.provenance()).isEqualTo("PLATFORM_CONFIG");
        assertThat(policy.id()).isEqualTo(configId);
    }
}
