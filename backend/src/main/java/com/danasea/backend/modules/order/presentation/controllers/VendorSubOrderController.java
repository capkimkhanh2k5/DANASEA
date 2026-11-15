package com.danasea.backend.modules.order.presentation.controllers;

import java.security.Principal;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.order.application.usecases.GetVendorSubOrderDetailUseCase;
import com.danasea.backend.modules.order.presentation.dtos.VendorSubOrderDetailResponse;
import com.danasea.backend.security.infrastructure.SecurityUtils;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/vendor/sub-orders")
@RequiredArgsConstructor
public class VendorSubOrderController {

    private final GetVendorSubOrderDetailUseCase getVendorSubOrderDetailUseCase;
    private final AccountInternalApi accountInternalApi;

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<VendorSubOrderDetailResponse> getSubOrderDetail(
            @PathVariable("id") UUID id,
            Principal principal) {
        UUID userId = resolveUserId(principal);
        VendorSubOrderDetailResponse response = getVendorSubOrderDetailUseCase.execute(userId, id);
        return ResponseEntity.ok(response);
    }

    private UUID resolveUserId(Principal principal) {
        var fromSecurity = SecurityUtils.getCurrentUserId();
        if (fromSecurity.isPresent()) {
            return fromSecurity.get();
        }
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new AccessDeniedException("User is not authenticated");
        }
        String identifier = principal.getName();
        var userOpt = accountInternalApi.findUserByEmail(identifier);
        if (userOpt.isPresent()) {
            return userOpt.get().getId();
        }
        try {
            UUID uuid = UUID.fromString(identifier);
            userOpt = accountInternalApi.findUserById(uuid);
            if (userOpt.isPresent()) {
                return userOpt.get().getId();
            }
        } catch (IllegalArgumentException ignored) {
        }
        throw new AccessDeniedException("User not found: " + identifier);
    }
}
