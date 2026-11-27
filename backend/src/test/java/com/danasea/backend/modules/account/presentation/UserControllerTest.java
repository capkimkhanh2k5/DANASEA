package com.danasea.backend.modules.account.presentation;
import com.danasea.backend.shared.presentation.GlobalExceptionHandler;

import com.danasea.backend.modules.account.application.usecases.ChangePasswordUseCase;
import com.danasea.backend.modules.account.application.usecases.GetMyProfileUseCase;
import com.danasea.backend.modules.account.application.usecases.UpdateProfileUseCase;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.security.authentication.domain.exceptions.InvalidCredentialsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private GetMyProfileUseCase getMyProfileUseCase;

    @Mock
    private UpdateProfileUseCase updateProfileUseCase;

    @Mock
    private ChangePasswordUseCase changePasswordUseCase;

    @InjectMocks
    private UserController userController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getMyProfile_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyProfile_WithValidToken_Returns200() throws Exception {
        Principal mockPrincipal = mock(Principal.class);
        when(mockPrincipal.getName()).thenReturn("test@example.com");

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setFullName("Test User");
        user.setRole(Role.CUSTOMER);
        user.setIsEmailVerified(true);

        when(getMyProfileUseCase.execute("test@example.com")).thenReturn(user);

        mockMvc.perform(get("/api/users/me")
                .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.fullName").value("Test User"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void updateMyProfile_MassAssignmentProtection() throws Exception {
        Principal mockPrincipal = mock(Principal.class);
        when(mockPrincipal.getName()).thenReturn("test@example.com");

        User updatedUser = new User();
        updatedUser.setId(UUID.randomUUID());
        updatedUser.setEmail("test@example.com");
        updatedUser.setFullName("New Name");
        updatedUser.setRole(Role.CUSTOMER); // Should remain CUSTOMER

        when(updateProfileUseCase.execute("test@example.com", "New Name", null, null))
                .thenReturn(updatedUser);

        // Sending payload with "role": "ADMIN" to attempt mass assignment
        String jsonPayload = """
                {
                  "fullName": "New Name",
                  "role": "ADMIN"
                }
                """;

        mockMvc.perform(patch("/api/users/me")
                .principal(mockPrincipal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("New Name"))
                .andExpect(jsonPath("$.role").value("CUSTOMER")); // Role is unchanged in response
    }

    @Test
    void changePassword_WrongCurrentPassword_Returns400() throws Exception {
        Principal mockPrincipal = mock(Principal.class);
        when(mockPrincipal.getName()).thenReturn("test@example.com");

        doThrow(new InvalidCredentialsException())
                .when(changePasswordUseCase).execute("test@example.com", "wrongPassword", "NewPassword123!");

        String jsonPayload = """
                {
                  "currentPassword": "wrongPassword",
                  "newPassword": "NewPassword123!"
                }
                """;

        mockMvc.perform(post("/api/users/me/change-password")
                .principal(mockPrincipal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void changePassword_Success_Returns200() throws Exception {
        Principal mockPrincipal = mock(Principal.class);
        when(mockPrincipal.getName()).thenReturn("test@example.com");

        doNothing().when(changePasswordUseCase).execute("test@example.com", "oldPassword", "NewPassword123!");

        String jsonPayload = """
                {
                  "currentPassword": "oldPassword",
                  "newPassword": "NewPassword123!"
                }
                """;

        mockMvc.perform(post("/api/users/me/change-password")
                .principal(mockPrincipal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isOk());
    }
}
