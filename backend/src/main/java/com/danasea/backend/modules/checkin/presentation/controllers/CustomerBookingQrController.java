package com.danasea.backend.modules.checkin.presentation.controllers;

import com.danasea.backend.modules.checkin.application.usecases.GenerateCheckinQrUseCase;
import com.danasea.backend.modules.checkin.presentation.dtos.QrCodeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class CustomerBookingQrController {

    private final GenerateCheckinQrUseCase generateCheckinQrUseCase;

    @PostMapping("/{id}/qr-code")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<QrCodeResponse> getQrCode(@PathVariable("id") UUID id) {
        log.info("Received check-in QR code request for booking or sub-order: {}", id);
        QrCodeResponse response = generateCheckinQrUseCase.execute(id);
        return ResponseEntity.ok(response);
    }
}
