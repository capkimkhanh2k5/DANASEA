package com.danasea.backend.modules.order.domain.ports;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Domain Port xác định tỷ lệ hoa hồng cho vendor.
 * Missing configuration must fail explicitly; there is no default rate.
 */
public interface CommissionPolicyPort {

    BigDecimal getCommissionRate(UUID vendorId);
}
