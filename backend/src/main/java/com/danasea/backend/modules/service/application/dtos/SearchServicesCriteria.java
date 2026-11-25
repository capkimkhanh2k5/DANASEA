package com.danasea.backend.modules.service.application.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchServicesCriteria {
    private UUID categoryId;
    private String keyword;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private BigDecimal lat;
    private BigDecimal lng;
    private Double radiusKm;
    private LocalDate date;
    private LocalTime timeSlot;
    private Integer guests;
    private UUID vendorId;
    private BadgeTier badgeTier;
    private BigDecimal minRating;
    private String sortBy;
    private int page;
    private int size;
}
