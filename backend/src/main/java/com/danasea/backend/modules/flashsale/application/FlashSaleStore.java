package com.danasea.backend.modules.flashsale.application;

import com.danasea.backend.modules.flashsale.domain.FlashSale;
import com.danasea.backend.modules.flashsale.domain.FlashSalePricing.*;
import java.math.BigDecimal;
import java.util.*;

public interface FlashSaleStore {
    record Option(UUID serviceId, UUID optionId, UUID vendorId, BigDecimal price, String pricingUnit,
            String name, String image, boolean eligible) {}
    record Usage(int quantity, BigDecimal budget, int reservedQuantity, int consumedQuantity,
            long releasedQuantity, BigDecimal reservedBudget, BigDecimal consumedBudget, BigDecimal releasedBudget) {}
    record Request(UUID bookingId, String fingerprint) {}
    Option option(UUID serviceId, UUID optionId);
    List<Option> eligible(int page, int size);
    Policy policy(UUID id);
    Policy latestPolicy(UUID vendorId);
    Policy createPolicy(Policy policy, UUID actor);
    List<Policy> policies(UUID vendorId);
    FlashSale sale(UUID id, boolean lock);
    List<FlashSale> sales(int page, int size);
    List<FlashSale> enabledSales();
    void save(FlashSale sale);
    void delete(UUID id);
    Usage usage(UUID id);
    void allocate(UUID saleId, UUID bookingId, UUID itemId, String pricingUnit, Snapshot snapshot);
    Optional<Snapshot> snapshot(UUID itemId);
    boolean hasAllocation(UUID bookingId);
    Request lockRequest(UUID customerId, String key);
    void saveRequest(UUID customerId, String key, String fingerprint, UUID bookingId);
}
