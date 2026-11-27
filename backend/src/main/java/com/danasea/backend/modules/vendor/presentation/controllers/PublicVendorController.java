package com.danasea.backend.modules.vendor.presentation.controllers;

import com.danasea.backend.modules.service.application.dtos.SearchServicesCriteria;
import com.danasea.backend.modules.service.application.dtos.ServiceSummaryResult;
import com.danasea.backend.modules.service.application.usecases.SearchServicesUseCase;
import com.danasea.backend.modules.service.presentation.dtos.PageResponse;
import com.danasea.backend.modules.service.presentation.dtos.ServiceSummaryResponse;
import com.danasea.backend.modules.vendor.application.usecases.GetPublicVendorProfileUseCase;
import com.danasea.backend.modules.vendor.presentation.dtos.PublicVendorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/vendors")
@RequiredArgsConstructor
public class PublicVendorController {

    private final GetPublicVendorProfileUseCase getPublicVendorProfileUseCase;
    private final SearchServicesUseCase searchServicesUseCase;

    @GetMapping("/{id}")
    public ResponseEntity<PublicVendorResponse> getPublicVendorProfile(@PathVariable UUID id) {
        PublicVendorResponse response = getPublicVendorProfileUseCase.execute(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/services")
    public ResponseEntity<PageResponse<ServiceSummaryResponse>> getVendorServices(
            @PathVariable UUID id,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        // Ensure vendor exists before returning services; throws VendorNotFoundException if not
        getPublicVendorProfileUseCase.execute(id);

        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }

        SearchServicesCriteria criteria = SearchServicesCriteria.builder()
                .vendorId(id)
                .sortBy(sortBy)
                .page(page)
                .size(size)
                .build();

        List<ServiceSummaryResult> results = searchServicesUseCase.execute(criteria);
        long total = searchServicesUseCase.count(criteria);

        List<ServiceSummaryResponse> responses = results.stream().map(r -> ServiceSummaryResponse.builder()
                .id(r.getId())
                .name(r.getName())
                .shortDescription(r.getShortDescription())
                .price(r.getPrice())
                .promotionalPrice(r.getPromotionalPrice())
                .address(r.getAddress())
                .averageRating(r.getAverageRating())
                .reviewCount(r.getReviewCount())
                .viewCount(r.getViewCount())
                .primaryImageUrl(r.getPrimaryImageUrl())
                .build()
        ).collect(Collectors.toList());

        return ResponseEntity.ok(new PageResponse<>(responses, page, size, total));
    }
}
