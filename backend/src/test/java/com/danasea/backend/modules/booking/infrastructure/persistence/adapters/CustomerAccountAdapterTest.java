package com.danasea.backend.modules.booking.infrastructure.persistence.adapters;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.domain.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerAccountAdapterTest {

    @Mock
    private AccountInternalApi accountInternalApi;

    private CustomerAccountAdapter adapter;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        adapter = new CustomerAccountAdapter(accountInternalApi);
        customerId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Validation succeeds when user exists, is active, and has CUSTOMER role")
    void validateCustomer_WhenCustomerRoleAndActive_Succeeds() {
        User user = new User();
        user.setId(customerId);
        user.setRole(Role.CUSTOMER);
        user.setIsLocked(false);

        when(accountInternalApi.findUserByIdForUpdate(customerId)).thenReturn(Optional.of(user));

        assertThatCode(() -> adapter.validateCustomerForBookingWithLock(customerId))
                .doesNotThrowAnyException();
        verify(accountInternalApi).findUserByIdForUpdate(customerId);
    }

    @Test
    @DisplayName("Throws AccessDeniedException when user role is VENDOR (e.g. upgraded by Admin)")
    void validateCustomer_WhenVendorRole_ThrowsAccessDenied() {
        User user = new User();
        user.setId(customerId);
        user.setRole(Role.VENDOR);
        user.setIsLocked(false);

        when(accountInternalApi.findUserByIdForUpdate(customerId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> adapter.validateCustomerForBookingWithLock(customerId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only customers are permitted to create bookings");
    }

    @Test
    @DisplayName("Throws AccessDeniedException when user account is locked")
    void validateCustomer_WhenLocked_ThrowsAccessDenied() {
        User user = new User();
        user.setId(customerId);
        user.setRole(Role.CUSTOMER);
        user.setIsLocked(true);

        when(accountInternalApi.findUserByIdForUpdate(customerId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> adapter.validateCustomerForBookingWithLock(customerId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User account is locked");
    }

    @Test
    @DisplayName("Throws AccessDeniedException when user is not found")
    void validateCustomer_WhenNotFound_ThrowsAccessDenied() {
        when(accountInternalApi.findUserByIdForUpdate(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.validateCustomerForBookingWithLock(customerId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("Throws IllegalArgumentException when customerId is null")
    void validateCustomer_WhenNullId_ThrowsIllegalArgument() {
        assertThatThrownBy(() -> adapter.validateCustomerForBookingWithLock(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
