package com.danasea.backend.modules.service.presentation.dtos;

import com.danasea.backend.modules.flashsale.application.FlashSaleService.PublicOffer;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServiceSummaryResponse {
    private List<PublicOffer> flashSales;
    private UUID id;
    private String name;
    private String slug;
    private String shortDescription;
    private BigDecimal price;
    private BigDecimal promotionalPrice;
    private String address;
    private BigDecimal averageRating;
    private int reviewCount;
    private int viewCount;
    private String primaryImageUrl;
}
