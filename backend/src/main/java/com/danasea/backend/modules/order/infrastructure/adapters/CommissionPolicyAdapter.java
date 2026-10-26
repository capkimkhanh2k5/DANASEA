package com.danasea.backend.modules.order.infrastructure.adapters;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.danasea.backend.modules.flashsale.application.FlashSaleStore;
import com.danasea.backend.modules.flashsale.domain.FlashSaleException;
import com.danasea.backend.modules.flashsale.domain.FlashSalePricing;
import com.danasea.backend.modules.order.domain.ports.CommissionPolicyPort;
import com.danasea.backend.modules.systemconfig.infrastructure.persistence.entities.SystemConfigJpaEntity;
import com.danasea.backend.modules.systemconfig.infrastructure.persistence.repositories.JpaSystemConfigRepository;

@Component
public class CommissionPolicyAdapter implements CommissionPolicyPort {

    public static final String PLATFORM_COMMISSION_KEY = "PLATFORM_COMMISSION_RATE";
    public static final String FLASH_SALE_MAX_SUBSIDY_RATIO_KEY = "FLASH_SALE_MAX_SUBSIDY_RATIO";
    public static final String FLASH_SALE_UNIT_COST_KEY = "FLASH_SALE_ESTIMATED_UNIT_COST";
    public static final String FLASH_SALE_MIN_MARGIN_KEY = "FLASH_SALE_MINIMUM_UNIT_MARGIN";

    private final FlashSaleStore policies;
    private final JpaSystemConfigRepository systemConfigRepository;

    @Autowired
    public CommissionPolicyAdapter(
            @Autowired(required = false) FlashSaleStore policies,
            @Autowired(required = false) JpaSystemConfigRepository systemConfigRepository) {
        this.policies = policies;
        this.systemConfigRepository = systemConfigRepository;
    }

    public CommissionPolicyAdapter(FlashSaleStore policies) {
        this(policies, null);
    }

    public CommissionPolicyAdapter(JpaSystemConfigRepository systemConfigRepository) {
        this(null, systemConfigRepository);
    }

    @Override
    public BigDecimal getCommissionRate(UUID vendorId) {
        // Tỷ lệ hoa hồng sàn DANASEA được quản lý tập trung và duy nhất qua PLATFORM_COMMISSION_RATE
        if (systemConfigRepository != null) {
            Optional<SystemConfigJpaEntity> configOpt = systemConfigRepository.findByKey(PLATFORM_COMMISSION_KEY);
            if (configOpt.isPresent() && configOpt.get().getValue() != null && !configOpt.get().getValue().isBlank()) {
                BigDecimal rate = parseRate(configOpt.get().getValue().trim());
                if (rate != null) {
                    return rate;
                }
            }
        }

        // Thiếu cấu hình -> bắt buộc ném lỗi rõ ràng, không hardcode fallback
        throw new FlashSaleException("COMMISSION_POLICY_MISSING");
    }

    public FlashSalePricing.Policy resolveFlashSalePolicy(UUID vendorId) {
        BigDecimal commissionRate = getCommissionRate(vendorId);

        BigDecimal maxSubsidyRatio = null;
        BigDecimal unitCost = BigDecimal.ZERO;
        BigDecimal minMargin = BigDecimal.ZERO;
        UUID policyId = null;

        if (systemConfigRepository != null) {
            var configOpt = systemConfigRepository.findByKey(PLATFORM_COMMISSION_KEY);
            if (configOpt.isPresent()) {
                policyId = configOpt.get().getId();
            }

            var maxRatioOpt = systemConfigRepository.findByKey(FLASH_SALE_MAX_SUBSIDY_RATIO_KEY);
            if (maxRatioOpt.isPresent() && maxRatioOpt.get().getValue() != null && !maxRatioOpt.get().getValue().isBlank()) {
                maxSubsidyRatio = parseRatio(maxRatioOpt.get().getValue().trim());
            }

            var costOpt = systemConfigRepository.findByKey(FLASH_SALE_UNIT_COST_KEY);
            if (costOpt.isPresent() && costOpt.get().getValue() != null && !costOpt.get().getValue().isBlank()) {
                unitCost = parseMoney(costOpt.get().getValue().trim());
            }

            var marginOpt = systemConfigRepository.findByKey(FLASH_SALE_MIN_MARGIN_KEY);
            if (marginOpt.isPresent() && marginOpt.get().getValue() != null && !marginOpt.get().getValue().isBlank()) {
                minMargin = parseMoney(marginOpt.get().getValue().trim());
            }
        }

        // Nếu system_configs chưa có FLASH_SALE_MAX_SUBSIDY_RATIO, kiểm tra safety constraints từ policy đã cấu hình
        if (maxSubsidyRatio == null && policies != null && vendorId != null) {
            try {
                var legacyPolicy = policies.latestPolicy(vendorId);
                if (legacyPolicy != null && legacyPolicy.maximumRatio() != null) {
                    maxSubsidyRatio = legacyPolicy.maximumRatio();
                    if (legacyPolicy.unitCost() != null) unitCost = legacyPolicy.unitCost();
                    if (legacyPolicy.minimumMargin() != null) minMargin = legacyPolicy.minimumMargin();
                }
            } catch (Exception ignored) {
                // Policy not configured
            }
        }

        if (maxSubsidyRatio == null) {
            throw new FlashSaleException("COMMISSION_POLICY_MISSING");
        }

        return new FlashSalePricing.Policy(
                policyId,
                vendorId,
                1,
                commissionRate,
                maxSubsidyRatio,
                unitCost != null ? unitCost : BigDecimal.ZERO,
                minMargin != null ? minMargin : BigDecimal.ZERO,
                "PLATFORM_CONFIG"
        );
    }

    private BigDecimal parseRate(String val) {
        try {
            BigDecimal raw = new BigDecimal(val);
            if (raw.compareTo(BigDecimal.ZERO) < 0) {
                return null;
            }
            if (raw.compareTo(BigDecimal.ONE) > 0) {
                raw = raw.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
            }
            if (raw.compareTo(BigDecimal.ONE) <= 0) {
                return raw.setScale(6, RoundingMode.HALF_UP);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseRatio(String val) {
        try {
            BigDecimal raw = new BigDecimal(val);
            if (raw.compareTo(BigDecimal.ZERO) < 0) {
                return null;
            }
            if (raw.compareTo(BigDecimal.ONE) > 0) {
                raw = raw.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
            }
            if (raw.compareTo(BigDecimal.ONE) <= 0) {
                return raw.setScale(6, RoundingMode.HALF_UP);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseMoney(String val) {
        try {
            BigDecimal raw = new BigDecimal(val);
            if (raw.compareTo(BigDecimal.ZERO) < 0) {
                return BigDecimal.ZERO;
            }
            return raw.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
