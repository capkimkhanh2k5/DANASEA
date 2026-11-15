package com.danasea.backend.modules.booking.domain.ports;

import java.util.UUID;

public interface CustomerAccountPort {
    void validateCustomerForBookingWithLock(UUID customerId);
}
