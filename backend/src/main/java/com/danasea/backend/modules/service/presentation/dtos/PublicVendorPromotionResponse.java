package com.danasea.backend.modules.service.presentation.dtos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PublicVendorPromotionResponse(
        UUID id,
        String code,
        UUID vendorId,
        UUID serviceId,
        String discountType,
        BigDecimal discountValue,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscountAmount,
        OffsetDateTime validTo
) {
}
