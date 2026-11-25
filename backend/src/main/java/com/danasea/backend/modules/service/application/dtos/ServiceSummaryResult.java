package com.danasea.backend.modules.service.application.dtos;

import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServiceSummaryResult {
    private UUID id;
    private UUID vendorId;
    private BadgeTier vendorBadgeTier;
    private String name;
    private String slug;
    private String shortDescription;
    private BigDecimal price;
    private BigDecimal promotionalPrice;
    private String address;
    private String locationDisplay;
    private BigDecimal averageRating;
    private int reviewCount;
    private int viewCount;
    private String primaryImageUrl;
}
