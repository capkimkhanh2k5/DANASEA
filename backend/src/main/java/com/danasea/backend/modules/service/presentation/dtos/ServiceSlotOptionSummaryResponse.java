package com.danasea.backend.modules.service.presentation.dtos;

import java.math.BigDecimal;
import java.util.UUID;

import com.danasea.backend.modules.service.domain.models.OptionType;
import com.danasea.backend.modules.service.domain.models.PricingUnit;

public record ServiceSlotOptionSummaryResponse(
        UUID id,
        String name,
        OptionType optionType,
        PricingUnit pricingUnit,
        BigDecimal price,
        Integer maxPaxPerPackage
) {}
