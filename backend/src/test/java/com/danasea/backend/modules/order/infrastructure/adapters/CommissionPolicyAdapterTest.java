package com.danasea.backend.modules.order.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.danasea.backend.modules.flashsale.application.FlashSaleStore;
import com.danasea.backend.modules.flashsale.domain.FlashSaleException;
import com.danasea.backend.modules.flashsale.domain.FlashSalePricing.Policy;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.systemconfig.infrastructure.persistence.entities.CommissionRuleJpaEntity;
import com.danasea.backend.modules.systemconfig.infrastructure.persistence.entities.SystemConfigJpaEntity;
import com.danasea.backend.modules.systemconfig.infrastructure.persistence.repositories.JpaCommissionRuleRepository;
import com.danasea.backend.modules.systemconfig.infrastructure.persistence.repositories.JpaSystemConfigRepository;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;

class CommissionPolicyAdapterTest {

    private final FlashSaleStore policies = mock(FlashSaleStore.class);
    private final JpaSystemConfigRepository configRepo = mock(JpaSystemConfigRepository.class);
    private final JpaCommissionRuleRepository commissionRuleRepo = mock(JpaCommissionRuleRepository.class);
    private final JpaVendorRepository vendorRepo = mock(JpaVendorRepository.class);
    private final JpaServiceRepository serviceRepo = mock(JpaServiceRepository.class);

    private final CommissionPolicyAdapter adapter = new CommissionPolicyAdapter(
            policies,
            configRepo,
            commissionRuleRepo,
            vendorRepo,
            serviceRepo
    );

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

    @Test
    @DisplayName("Cascading: New vendor within 90 days of verification gets onboarding commission 5.0%")
    void resolveCommissionRate_NewVendorWithin90Days_ReturnsOnboardingRate() {
        UUID vendorId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        // Onboarding rule: 5.0% (0.05)
        CommissionRuleJpaEntity onboardingRule = new CommissionRuleJpaEntity();
        onboardingRule.setRuleCode("ONBOARDING_NEW_VENDOR_90D");
        onboardingRule.setCommissionRate(new BigDecimal("0.0500"));
        onboardingRule.setDurationDays(90);
        onboardingRule.setPriority(100);
        onboardingRule.setIsActive(true);
        when(commissionRuleRepo.findFirstByTargetTypeAndIsActiveTrueOrderByPriorityDesc("ONBOARDING"))
                .thenReturn(Optional.of(onboardingRule));

        // Vendor verified 15 days ago
        VendorJpaEntity vendor = new VendorJpaEntity();
        vendor.setId(vendorId);
        vendor.setVerificationStatus(VerificationStatus.APPROVED);
        vendor.setVerifiedAt(OffsetDateTime.now().minusDays(15));
        when(vendorRepo.findById(vendorId)).thenReturn(Optional.of(vendor));

        BigDecimal rate = adapter.resolveCommissionRate(vendorId, serviceId, categoryId);
        assertThat(rate).isEqualByComparingTo("0.050000");
    }

    @Test
    @DisplayName("Cascading: Vendor past 90 days cascades to category-specific commission rate")
    void resolveCommissionRate_VendorPast90Days_CascadesToCategoryRate() {
        UUID vendorId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        // Onboarding rule: 90 days
        CommissionRuleJpaEntity onboardingRule = new CommissionRuleJpaEntity();
        onboardingRule.setDurationDays(90);
        onboardingRule.setPriority(100);
        onboardingRule.setIsActive(true);
        when(commissionRuleRepo.findFirstByTargetTypeAndIsActiveTrueOrderByPriorityDesc("ONBOARDING"))
                .thenReturn(Optional.of(onboardingRule));

        // Vendor verified 100 days ago (past onboarding incentive)
        VendorJpaEntity vendor = new VendorJpaEntity();
        vendor.setId(vendorId);
        vendor.setVerificationStatus(VerificationStatus.APPROVED);
        vendor.setVerifiedAt(OffsetDateTime.now().minusDays(100));
        when(vendorRepo.findById(vendorId)).thenReturn(Optional.of(vendor));

        // Category rule for SUP rental: 7.0% (0.07)
        CommissionRuleJpaEntity catRule = new CommissionRuleJpaEntity();
        catRule.setRuleCode("CATEGORY_THUE_SUP");
        catRule.setCategoryId(categoryId);
        catRule.setCommissionRate(new BigDecimal("0.0700"));
        catRule.setIsActive(true);
        when(commissionRuleRepo.findFirstByTargetTypeAndCategoryIdAndIsActiveTrueOrderByPriorityDesc("CATEGORY", categoryId))
                .thenReturn(Optional.of(catRule));

        BigDecimal rate = adapter.resolveCommissionRate(vendorId, serviceId, categoryId);
        assertThat(rate).isEqualByComparingTo("0.070000");
    }

    @Test
    @DisplayName("Cascading: Vendor resolves category via serviceId lookup when categoryId is null")
    void resolveCommissionRate_ResolvesCategoryViaServiceLookup() {
        UUID vendorId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        // No onboarding rule
        when(commissionRuleRepo.findFirstByTargetTypeAndIsActiveTrueOrderByPriorityDesc("ONBOARDING"))
                .thenReturn(Optional.empty());

        // Service entity links serviceId -> categoryId
        ServiceJpaEntity serviceEntity = new ServiceJpaEntity();
        serviceEntity.setId(serviceId);
        serviceEntity.setCategoryId(categoryId);
        when(serviceRepo.findById(serviceId)).thenReturn(Optional.of(serviceEntity));

        // Category rule for deep experience (scuba): 12.0% (0.12)
        CommissionRuleJpaEntity catRule = new CommissionRuleJpaEntity();
        catRule.setRuleCode("CATEGORY_LAN_NGAM_SAN_HO");
        catRule.setCategoryId(categoryId);
        catRule.setCommissionRate(new BigDecimal("0.1200"));
        catRule.setIsActive(true);
        when(commissionRuleRepo.findFirstByTargetTypeAndCategoryIdAndIsActiveTrueOrderByPriorityDesc("CATEGORY", categoryId))
                .thenReturn(Optional.of(catRule));

        BigDecimal rate = adapter.resolveCommissionRate(vendorId, serviceId, null);
        assertThat(rate).isEqualByComparingTo("0.120000");
    }

    @Test
    @DisplayName("Cascading: Fallback to PLATFORM_COMMISSION_RATE (10%) when no rule matches")
    void resolveCommissionRate_FallsBackToPlatformRate() {
        UUID vendorId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        when(commissionRuleRepo.findFirstByTargetTypeAndIsActiveTrueOrderByPriorityDesc("ONBOARDING"))
                .thenReturn(Optional.empty());
        when(commissionRuleRepo.findFirstByTargetTypeAndCategoryIdAndIsActiveTrueOrderByPriorityDesc("CATEGORY", categoryId))
                .thenReturn(Optional.empty());

        SystemConfigJpaEntity config = new SystemConfigJpaEntity();
        config.setKey("PLATFORM_COMMISSION_RATE");
        config.setValue("10.0");
        when(configRepo.findByKey("PLATFORM_COMMISSION_RATE")).thenReturn(Optional.of(config));

        BigDecimal rate = adapter.resolveCommissionRate(vendorId, serviceId, categoryId);
        assertThat(rate).isEqualByComparingTo("0.100000");
    }
}
