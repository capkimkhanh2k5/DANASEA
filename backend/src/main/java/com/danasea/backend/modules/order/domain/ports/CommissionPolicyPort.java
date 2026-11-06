package com.danasea.backend.modules.order.domain.ports;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Domain Port xác định tỷ lệ hoa hồng cho vendor.
 * Hỗ trợ phân giải đa tầng: Onboarding, Category-based và Platform default.
 */
public interface CommissionPolicyPort {

    BigDecimal getCommissionRate(UUID vendorId);

    default BigDecimal resolveCommissionRate(UUID vendorId, UUID serviceId, UUID categoryId) {
        return getCommissionRate(vendorId);
    }
}
