package com.danasea.backend.security.authentication.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.security.authentication.domain.exceptions.EmailAlreadyUsedException;
import com.danasea.backend.security.authentication.domain.models.Authentication;

class UserAccountAdapterTest {

    private AccountInternalApi accountInternalApi;
    private UserAccountAdapter adapter;

    @BeforeEach
    void setUp() {
        accountInternalApi = mock(AccountInternalApi.class);
        adapter = new UserAccountAdapter(accountInternalApi);
    }

    @Test
    void shouldFindUserByEmail() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setPasswordHash("hash");
        user.setRole(Role.CUSTOMER);
        user.setIsLocked(false);

        when(accountInternalApi.findUserByEmailUncached("test@example.com")).thenReturn(Optional.of(user));

        Optional<Authentication> result = adapter.findByEmail("test@example.com");

        assertTrue(result.isPresent());
        assertEquals("test@example.com", result.get().email());
        assertEquals("CUSTOMER", result.get().role());
    }

    @Test
    void shouldReturnEmptyWhenUserNotFound() {
        when(accountInternalApi.findUserByEmailUncached(anyString())).thenReturn(Optional.empty());

        Optional<Authentication> result = adapter.findByEmail("test@example.com");

        assertFalse(result.isPresent());
    }

    @Test
    void shouldReturnTrueWhenEmailExists() {
        when(accountInternalApi.existsByEmail("test@example.com")).thenReturn(true);

        assertTrue(adapter.existsByEmail("test@example.com"));
    }

    @Test
    void shouldSaveUserSuccessfully() {
        Authentication auth = new Authentication(UUID.randomUUID(), "test@example.com", "hash", "CUSTOMER", true, true);
        
        User user = new User();
        user.setId(auth.id());
        user.setEmail(auth.email());
        user.setPasswordHash(auth.passwordHash());
        user.setRole(Role.CUSTOMER);
        user.setIsLocked(false);

        when(accountInternalApi.saveUser(any(User.class))).thenReturn(user);

        Authentication result = adapter.save(auth);

        assertNotNull(result);
        assertEquals(auth.id(), result.id());
        assertEquals(auth.email(), result.email());
    }

    @Test
    void shouldThrowWhenEmailAlreadyUsedOnSave() {
        Authentication auth = new Authentication(UUID.randomUUID(), "test@example.com", "hash", "CUSTOMER", true, true);
        when(accountInternalApi.saveUser(any(User.class))).thenThrow(new DataIntegrityViolationException("Duplicate"));

        assertThrows(EmailAlreadyUsedException.class, () -> adapter.save(auth));
    }
}
