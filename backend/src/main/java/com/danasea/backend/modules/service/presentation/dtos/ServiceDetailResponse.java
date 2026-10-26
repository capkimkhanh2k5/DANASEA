package com.danasea.backend.modules.service.presentation.dtos;

import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDetailResponse {
    private java.util.List<com.danasea.backend.modules.flashsale.application.FlashSaleService.PublicOffer> flashSales;
    private UUID id;
    private String name;
    private String description;
    private BigDecimal price;
    private BigDecimal promotionalPrice;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal averageRating;
    private int reviewCount;
    private int viewCount;
    private UUID categoryId;
    private String categoryName;
    private List<String> imageUrls;
    private List<String> availableSlots;
    private List<ServiceOptionResponse> options;

    // Public vendor info
    private UUID vendorId;
    private String businessName;
    private BadgeTier badgeTier;

    // Operational info
    private Integer duration;
    private Integer capacity;
    private String participantConditions;
    private Boolean waiverRequired;
    private Integer waiverVersion;
    private String waiverContent;
    private String waiverLanguage;
    private Boolean waiverFallbackUsed;
    private String refundPolicy;
    private String cancellationPolicy;
    private String safetyRules;

    // Structured slots
    private List<StructuredSlotResponse> slots;
}
