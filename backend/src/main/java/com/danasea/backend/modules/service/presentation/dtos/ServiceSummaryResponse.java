package com.danasea.backend.modules.service.presentation.dtos;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServiceSummaryResponse {
    private java.util.List<com.danasea.backend.modules.flashsale.application.FlashSaleService.PublicOffer> flashSales;
    private UUID id;
    private String name;
    private String shortDescription;
    private BigDecimal price;
    private BigDecimal promotionalPrice;
    private String address;
    private BigDecimal averageRating;
    private int reviewCount;
    private int viewCount;
    private String primaryImageUrl;
}
