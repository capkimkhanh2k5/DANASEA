package com.danasea.backend.modules.booking.infrastructure.persistence.adapters;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.booking.domain.ports.CustomerAccountPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CustomerAccountAdapter implements CustomerAccountPort {

    private final AccountInternalApi accountInternalApi;

    @Override
    public void validateCustomerForBookingWithLock(UUID customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID cannot be null");
        }
        User user = accountInternalApi.findUserByIdForUpdate(customerId)
                .orElseThrow(() -> new AccessDeniedException("User not found"));
        if (Boolean.TRUE.equals(user.getIsLocked())) {
            throw new AccessDeniedException("User account is locked");
        }
        if (user.getRole() != Role.CUSTOMER) {
            throw new AccessDeniedException("Only customers are permitted to create bookings");
        }
    }
}
