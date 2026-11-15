package com.danasea.backend.modules.service.presentation.dtos;

import com.danasea.backend.modules.service.domain.models.OperationType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateServiceRequest(
        @NotNull UUID categoryId,
        @NotBlank String name,
        String nameEn,
        String description,
        String descriptionEn,
        @NotNull @DecimalMin("0.0") BigDecimal price,
        @NotNull @Positive Integer durationMinutes,
        @Positive Integer capacityPerSlot,
        OperationType operationType,
        String locationName,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String waiverContent,
        String waiverContentEn,
        Boolean waiverRequired,
        Boolean weatherSensitive,
        BigDecimal minWindKmh,
        BigDecimal maxWaveM
) {
    public CreateServiceRequest(
            UUID categoryId, String name, String nameEn, String description,
            String descriptionEn, BigDecimal price, Integer durationMinutes,
            Integer capacityPerSlot, String locationName, String address,
            BigDecimal latitude, BigDecimal longitude, String waiverContent,
            String waiverContentEn, Boolean waiverRequired, Boolean weatherSensitive,
            BigDecimal minWindKmh, BigDecimal maxWaveM) {
        this(categoryId, name, nameEn, description, descriptionEn, price,
                durationMinutes, capacityPerSlot, OperationType.FIXED_DEPARTURE, locationName, address,
                latitude, longitude, waiverContent, waiverContentEn, waiverRequired,
                weatherSensitive, minWindKmh, maxWaveM);
    }

    public CreateServiceRequest(
            UUID categoryId, String name, String nameEn, String description,
            String descriptionEn, BigDecimal price, Integer durationMinutes,
            Integer capacityPerSlot, String locationName, String address,
            BigDecimal latitude, BigDecimal longitude, String waiverContent,
            String waiverContentEn, Boolean weatherSensitive,
            BigDecimal minWindKmh, BigDecimal maxWaveM) {
        this(categoryId, name, nameEn, description, descriptionEn, price,
                durationMinutes, capacityPerSlot, OperationType.FIXED_DEPARTURE, locationName, address,
                latitude, longitude, waiverContent, waiverContentEn, false,
                weatherSensitive, minWindKmh, maxWaveM);
    }
}
