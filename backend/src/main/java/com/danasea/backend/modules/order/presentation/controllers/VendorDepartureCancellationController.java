package com.danasea.backend.modules.order.presentation.controllers;

import com.danasea.backend.modules.order.application.services.VendorDepartureCancellation;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class VendorDepartureCancellationController {
    private final VendorDepartureCancellation cancellations;

    public record Request(@NotBlank @Size(max = 500) String reason) {}

    @PostMapping("/api/vendor/services/{serviceId}/slots/{slotId}/cancel")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<VendorDepartureCancellation.Cancellation> cancel(
            @PathVariable UUID serviceId, @PathVariable UUID slotId, @Valid @RequestBody Request request) {
        return ResponseEntity.accepted().body(cancellations.cancel(currentUser(), serviceId, slotId, request.reason()));
    }

    @GetMapping("/api/vendor/services/{serviceId}/slots/{slotId}/cancel")
    @PreAuthorize("hasRole('VENDOR')")
    public VendorDepartureCancellation.CancellationStatus status(@PathVariable UUID serviceId, @PathVariable UUID slotId) {
        return cancellations.status(currentUser(), serviceId, slotId);
    }

    private UUID currentUser() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated."));
    }
}
