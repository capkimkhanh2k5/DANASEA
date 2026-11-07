package com.danasea.backend.modules.order.application.services;

import org.springframework.stereotype.Component;

import com.danasea.backend.configs.properties.PayOSProperties;
import com.danasea.backend.configs.properties.ZaloPayProperties;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentProviderAvailability {

    private final PayOSProperties payOS;
    private final ZaloPayProperties zaloPay;

    public boolean isEnabled(PaymentProvider provider) {
        return switch (provider) {
            case VNPAY, PAYPAL -> true;
            case PAYOS -> payOS.enabled();
            case ZALOPAY -> zaloPay.enabled();
            case MOMO, SEPAY -> false;
        };
    }
}
