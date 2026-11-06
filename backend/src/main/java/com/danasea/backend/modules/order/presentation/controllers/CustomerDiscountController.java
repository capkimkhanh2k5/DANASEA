package com.danasea.backend.modules.order.presentation.controllers;

import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaDiscountCodeRepository;
import com.danasea.backend.modules.order.presentation.dtos.DiscountCodeResponse;
import com.danasea.backend.security.infrastructure.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CustomerDiscountController {
    private final JpaDiscountCodeRepository discounts;

    @GetMapping("/api/customers/me/discount-codes")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "List personal replacement and compensation vouchers, including used and expired vouchers")
    public Page<DiscountCodeResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        if (pageable.getPageSize() > 100) throw new IllegalArgumentException("Page size cannot exceed 100.");
        var userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new AccessDeniedException("User is not authenticated."));
        return discounts.findByRecipientCustomerId(userId, pageable).map(DiscountCodeResponse::fromEntity);
    }
}
