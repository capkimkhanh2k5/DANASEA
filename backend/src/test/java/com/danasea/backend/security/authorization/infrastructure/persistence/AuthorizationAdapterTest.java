package com.danasea.backend.security.authorization.infrastructure.persistence;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.security.authorization.domain.models.AuthorizationSubject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuthorizationAdapterTest {

    private AccountInternalApi accountApi;
    private AuthorizationAdapter adapter;

    @BeforeEach
    void setUp() {
        accountApi = mock(AccountInternalApi.class);
        adapter = new AuthorizationAdapter(accountApi);
    }

    @Test
    @DisplayName("findSubjectByEmail: Ủy quyền 100% qua AccountInternalApi và map chính xác quyền ADMIN")
    void findSubjectByEmail_adminRole_delegatesToAccountApiAndMapsPermissions() {
        String email = "admin@example.com";
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail(email);
        user.setRole(Role.ADMIN);

        when(accountApi.findUserByEmail(email)).thenReturn(Optional.of(user));

        AuthorizationSubject subject = adapter.findSubjectByEmail(email);

        assertThat(subject).isNotNull();
        assertThat(subject.userId()).isEqualTo(userId);
        assertThat(subject.email()).isEqualTo(email);
        assertThat(subject.roles()).containsExactly("ADMIN");
        assertThat(subject.permissions()).contains("USER_READ", "USER_UPDATE", "PRODUCT_CREATE", "ORDER_READ");

        verify(accountApi, times(1)).findUserByEmail(email);
        verifyNoMoreInteractions(accountApi);
    }

    @Test
    @DisplayName("findSubjectByEmail: Map chính xác quyền VENDOR và CUSTOMER")
    void findSubjectByEmail_vendorAndCustomerRoles_mappedCorrectly() {
        UUID vendorId = UUID.randomUUID();
        User vendor = new User();
        vendor.setId(vendorId);
        vendor.setEmail("vendor@example.com");
        vendor.setRole(Role.VENDOR);

        when(accountApi.findUserByEmail("vendor@example.com")).thenReturn(Optional.of(vendor));

        AuthorizationSubject vendorSubject = adapter.findSubjectByEmail("vendor@example.com");
        assertThat(vendorSubject.roles()).containsExactly("VENDOR");
        assertThat(vendorSubject.permissions()).contains("PRODUCT_CREATE", "PRODUCT_READ");
        assertThat(vendorSubject.permissions()).doesNotContain("USER_READ");

        UUID customerId = UUID.randomUUID();
        User customer = new User();
        customer.setId(customerId);
        customer.setEmail("customer@example.com");
        customer.setRole(Role.CUSTOMER);

        when(accountApi.findUserByEmail("customer@example.com")).thenReturn(Optional.of(customer));

        AuthorizationSubject customerSubject = adapter.findSubjectByEmail("customer@example.com");
        assertThat(customerSubject.roles()).containsExactly("CUSTOMER");
        assertThat(customerSubject.permissions()).contains("PRODUCT_READ", "ORDER_READ");
        assertThat(customerSubject.permissions()).doesNotContain("PRODUCT_CREATE");
    }

    @Test
    @DisplayName("findSubjectByUserId: Ủy quyền qua AccountInternalApi.findUserById")
    void findSubjectByUserId_delegatesToAccountApiFindUserById() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setRole(Role.CUSTOMER);

        when(accountApi.findUserById(userId)).thenReturn(Optional.of(user));

        AuthorizationSubject subject = adapter.findSubjectByUserId(userId);

        assertThat(subject.userId()).isEqualTo(userId);
        verify(accountApi, times(1)).findUserById(userId);
        verifyNoMoreInteractions(accountApi);
    }

    @Test
    @DisplayName("hasPermission: Kiểm tra quyền dựa trên AccountInternalApi")
    void hasPermission_checksPermissionCorrectly() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("vendor@example.com");
        user.setRole(Role.VENDOR);

        when(accountApi.findUserById(userId)).thenReturn(Optional.of(user));

        assertThat(adapter.hasPermission(userId, "PRODUCT_CREATE")).isTrue();
        assertThat(adapter.hasPermission(userId, "USER_READ")).isFalse();
    }

    @Test
    @DisplayName("findSubjectByEmail: Ném NoSuchElementException khi user không tồn tại")
    void findSubjectByEmail_userNotFound_throwsException() {
        when(accountApi.findUserByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.findSubjectByEmail("unknown@example.com"))
                .isInstanceOf(NoSuchElementException.class);
    }
}
