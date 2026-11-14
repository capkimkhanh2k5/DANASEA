package com.danasea.backend.modules.service.presentation.dtos;

import com.danasea.backend.modules.service.domain.models.OperationType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record UpdateServiceRequest(
        UUID categoryId,
        @Pattern(regexp = ".*\\S.*") @Size(max = 200)
        String name,
        @Size(max = 200)
        String nameEn,
        @Size(max = 5000)
        String description,
        @Size(max = 5000)
        String descriptionEn,
        @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal price,
        @Positive
        Integer durationMinutes,
        @Positive
        Integer capacityPerSlot,
        OperationType operationType,
        @Size(max = 255)
        String locationName,
        @Size(max = 500)
        String address,
        @DecimalMin("-90.0") @DecimalMax("90.0")
        BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0")
        BigDecimal longitude,
        @Size(max = 10000)
        String waiverContent,
        String waiverContentEn,
        Boolean waiverRequired,
        Boolean weatherSensitive,
        @DecimalMin("0.0")
        BigDecimal minWindKmh,
        @DecimalMin("0.0")
        BigDecimal maxWaveM
) {
    public UpdateServiceRequest(
            UUID categoryId, String name, String nameEn, String description,
            String descriptionEn, BigDecimal price, Integer durationMinutes,
            Integer capacityPerSlot, String locationName, String address,
            BigDecimal latitude, BigDecimal longitude, String waiverContent,
            String waiverContentEn, Boolean waiverRequired, Boolean weatherSensitive,
            BigDecimal minWindKmh, BigDecimal maxWaveM) {
        this(categoryId, name, nameEn, description, descriptionEn, price,
                durationMinutes, capacityPerSlot, null, locationName, address,
                latitude, longitude, waiverContent, waiverContentEn, waiverRequired,
                weatherSensitive, minWindKmh, maxWaveM);
    }

    public UpdateServiceRequest(
            UUID categoryId, String name, String nameEn, String description,
            String descriptionEn, BigDecimal price, Integer durationMinutes,
            Integer capacityPerSlot, String locationName, String address,
            BigDecimal latitude, BigDecimal longitude, String waiverContent,
            String waiverContentEn, Boolean weatherSensitive,
            BigDecimal minWindKmh, BigDecimal maxWaveM) {
        this(categoryId, name, nameEn, description, descriptionEn, price,
                durationMinutes, capacityPerSlot, null, locationName, address,
                latitude, longitude, waiverContent, waiverContentEn, null,
                weatherSensitive, minWindKmh, maxWaveM);
    }
}
