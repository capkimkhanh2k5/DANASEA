package com.danasea.backend.modules.account.application.usecases;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.account.presentation.UserController;
import com.danasea.backend.modules.account.presentation.dtos.UserProfileResponse;
import com.danasea.backend.security.authorization.domain.exceptions.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetMyProfileUseCaseTest {

    @Mock
    private AccountInternalApi accountInternalApi;
    private UserController controller;

    @BeforeEach
    void setUp() {
        // Compose the simple lookup with its real consumer and response mapping.
        controller = new UserController(new GetMyProfileUseCase(accountInternalApi),
                mock(UpdateProfileUseCase.class), mock(ChangePasswordUseCase.class));
    }

    @Test
    void returnsCurrentPrincipalsProfileWithExactPublicFields() {
        String email = "traveller@example.test";
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setFullName("Nguyen An");
        user.setAvatarUrl("https://images.example/avatar.jpg");
        user.setLocale("en");
        user.setRole(Role.CUSTOMER);
        user.setIsEmailVerified(true);
        user.setPasswordHash("private-password-hash");
        user.setPhone("0901234567");
        when(accountInternalApi.findUserByEmail(email)).thenReturn(Optional.of(user));

        ResponseEntity<UserProfileResponse> response = controller.getMyProfile(() -> email);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(new UserProfileResponse(user.getId(), email,
                "Nguyen An", "https://images.example/avatar.jpg", "en", "CUSTOMER", true));
        verify(accountInternalApi).findUserByEmail(email);
        verifyNoMoreInteractions(accountInternalApi);
    }

    @Test
    void retainsAbsentOptionalProfileFieldsAndUnverifiedState() {
        String email = "new-user@example.test";
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setLocale("vi");
        user.setIsEmailVerified(false);
        when(accountInternalApi.findUserByEmail(email)).thenReturn(Optional.of(user));

        ResponseEntity<UserProfileResponse> response = controller.getMyProfile(() -> email);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(new UserProfileResponse(
                user.getId(), email, null, null, "vi", null, false));
        verify(accountInternalApi).findUserByEmail(email);
        verifyNoMoreInteractions(accountInternalApi);
    }

    @Test
    void deniesMissingAccountWithoutFallbackLookupOrWrites() {
        String email = "missing@example.test";
        when(accountInternalApi.findUserByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getMyProfile(() -> email))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You do not have permission to perform this action.");

        verify(accountInternalApi).findUserByEmail(email);
        verifyNoMoreInteractions(accountInternalApi);
    }

    @Test
    void returnsUnauthorizedBeforeAccountAccessWhenPrincipalIsAbsentOrUnnamed() {
        for (Principal principal : new Principal[]{null, () -> null}) {
            ResponseEntity<UserProfileResponse> response = controller.getMyProfile(principal);
            assertThat(response.getStatusCode().value()).isEqualTo(401);
            assertThat(response.getBody()).isNull();
        }
        verifyNoInteractions(accountInternalApi);
    }
}
