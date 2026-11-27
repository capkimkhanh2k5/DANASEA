package com.danasea.backend.modules.service.presentation;

import com.danasea.backend.modules.service.application.dtos.WishlistItemResult;
import com.danasea.backend.modules.service.application.usecases.WishlistUseCase;
import com.danasea.backend.modules.service.presentation.dtos.WishlistItemResponse;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/wishlists")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistUseCase wishlistUseCase;

    @PostMapping("/{serviceId}")
    public ResponseEntity<Void> addWishlist(@PathVariable UUID serviceId) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException("Unauthorized")); // Let global handler handle this, or create CustomException
        
        wishlistUseCase.addWishlist(userId, serviceId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{serviceId}")
    public ResponseEntity<Void> removeWishlist(@PathVariable UUID serviceId) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException("Unauthorized"));
        
        wishlistUseCase.removeWishlist(userId, serviceId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<WishlistItemResponse>> getWishlists() {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException("Unauthorized"));

        List<WishlistItemResult> results = wishlistUseCase.getWishlists(userId);
        List<WishlistItemResponse> responses = results.stream().map(r -> WishlistItemResponse.builder()
                .id(r.getId())
                .serviceId(r.getServiceId())
                .serviceName(r.getServiceName())
                .primaryImageUrl(r.getPrimaryImageUrl())
                .addedAt(r.getAddedAt())
                .build()
        ).collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }
}
