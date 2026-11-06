package com.danasea.backend.modules.order.infrastructure.adapters;

import com.danasea.backend.modules.order.domain.exceptions.InvalidOrderStateException;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Strategy Registry để định tuyến PaymentGatewayPort phù hợp dựa trên PaymentProvider.
 * Quản lý danh sách 4 cổng: payOS, ZaloPay, VNPay, PayPal.
 */
@Component
public class PaymentGatewayRegistry {

    private final List<PaymentGatewayPort> adapters;
    private final Map<PaymentProvider, PaymentGatewayPort> adapterCache = new ConcurrentHashMap<>();

    @Autowired
    public PaymentGatewayRegistry(List<PaymentGatewayPort> adapters) {
        this.adapters = adapters != null ? adapters : Collections.emptyList();
    }

    public PaymentGatewayPort getAdapter(PaymentProvider provider) {
        if (provider == null) {
            throw new InvalidOrderStateException("Payment provider must not be null.");
        }
        return adapterCache.computeIfAbsent(provider, p -> adapters.stream()
                .filter(adapter -> {
                    try {
                        return adapter.supportsProvider(p);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .findFirst()
                .orElseGet(() -> {
                    for (PaymentGatewayPort adapter : adapters) {
                        String name = adapter.getClass().getSimpleName().toLowerCase();
                        if (p == PaymentProvider.PAYPAL && name.contains("paypal")) return adapter;
                        if (p == PaymentProvider.VNPAY && name.contains("vnpay")) return adapter;
                        if (p == PaymentProvider.PAYOS && name.contains("payos")) return adapter;
                        if (p == PaymentProvider.ZALOPAY && name.contains("zalopay")) return adapter;
                    }
                    throw new InvalidOrderStateException("Unsupported payment provider: " + p);
                }));
    }

    public boolean supports(PaymentProvider provider) {
        if (provider == null) {
            return false;
        }
        try {
            return getAdapter(provider) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public List<PaymentProvider> getSupportedProviders() {
        return List.of(PaymentProvider.VNPAY, PaymentProvider.PAYPAL, PaymentProvider.PAYOS, PaymentProvider.ZALOPAY);
    }
}
