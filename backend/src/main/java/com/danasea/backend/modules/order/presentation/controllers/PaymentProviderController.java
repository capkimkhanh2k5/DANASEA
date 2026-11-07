package com.danasea.backend.modules.order.presentation.controllers;

import java.util.Arrays;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.danasea.backend.modules.order.application.services.PaymentProviderAvailability;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PaymentProviderController {

    private final PaymentProviderAvailability availability;

    @GetMapping("/api/payments/providers")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<PaymentProvider> getAvailableProviders() {
        return Arrays.stream(PaymentProvider.values()).filter(availability::isEnabled).toList();
    }
}
