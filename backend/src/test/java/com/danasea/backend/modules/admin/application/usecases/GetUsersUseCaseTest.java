package com.danasea.backend.modules.admin.application.usecases;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.admin.presentation.dtos.UserSummaryResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetUsersUseCaseTest {

    @Mock
    private AccountInternalApi accountInternalApi;

    private GetUsersUseCase getUsersUseCase;

    @BeforeEach
    void setUp() {
        getUsersUseCase = new GetUsersUseCase(accountInternalApi);
    }

    @Test
    void shouldReturnPaginatedUserSummaries() {
        Pageable pageable = PageRequest.of(0, 10);
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setFullName("User Test");
        user.setRole(Role.CUSTOMER);
        user.setIsLocked(false);
        user.setCreatedAt(OffsetDateTime.now());

        Page<User> userPage = new PageImpl<>(List.of(user), pageable, 1);
        when(accountInternalApi.findUsers(pageable, Role.CUSTOMER, false, "test")).thenReturn(userPage);

        Page<UserSummaryResponse> result = getUsersUseCase.execute(pageable, Role.CUSTOMER, false, "test");

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("user@example.com", result.getContent().get(0).email());
        assertEquals(user.getId(), result.getContent().get(0).id());
    }
}
