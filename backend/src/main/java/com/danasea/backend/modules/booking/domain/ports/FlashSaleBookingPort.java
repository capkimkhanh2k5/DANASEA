package com.danasea.backend.modules.booking.domain.ports;

import com.danasea.backend.modules.flashsale.domain.FlashSalePricing.Snapshot;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface FlashSaleBookingPort {
    void lockOffers(java.util.List<UUID> ids);
    UUID replay(UUID customerId, String key, String fingerprint);
    void remember(UUID customerId, String key, String fingerprint, UUID bookingId);
    Snapshot reserve(UUID saleId, UUID serviceId, UUID optionId, BigDecimal verifiedPrice,
            String pricingUnit, int quantity, UUID bookingId, UUID itemId);
    Optional<Snapshot> snapshot(UUID itemId);
    boolean hasAllocation(UUID bookingId);
}
