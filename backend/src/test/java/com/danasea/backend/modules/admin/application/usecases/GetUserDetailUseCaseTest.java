package com.danasea.backend.modules.admin.application.usecases;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.admin.domain.exceptions.UserNotFoundException;
import com.danasea.backend.modules.admin.presentation.dtos.UserDetailResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetUserDetailUseCaseTest {

    @Mock
    private AccountInternalApi accountInternalApi;

    private GetUserDetailUseCase getUserDetailUseCase;

    @BeforeEach
    void setUp() {
        getUserDetailUseCase = new GetUserDetailUseCase(accountInternalApi);
    }

    @Test
    void shouldReturnUserDetailWhenFound() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setFullName("User Detail");
        user.setRole(Role.VENDOR);
        user.setIsLocked(false);
        user.setLocale("vi");

        when(accountInternalApi.findUserById(userId)).thenReturn(Optional.of(user));

        UserDetailResponse response = getUserDetailUseCase.execute(userId);

        assertNotNull(response);
        assertEquals(userId, response.id());
        assertEquals("user@example.com", response.email());
        assertEquals(Role.VENDOR, response.role());
        assertEquals("vi", response.locale());
    }

    @Test
    void shouldThrowWhenUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(accountInternalApi.findUserById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> getUserDetailUseCase.execute(userId));
    }
}
