package com.danasea.backend.modules.admin.presentation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.admin.application.usecases.GetUserDetailUseCase;
import com.danasea.backend.modules.admin.application.usecases.GetUsersUseCase;
import com.danasea.backend.modules.admin.application.usecases.LockUserUseCase;
import com.danasea.backend.modules.admin.application.usecases.UnlockUserUseCase;
import com.danasea.backend.modules.admin.domain.exceptions.SelfLockNotAllowedException;
import com.danasea.backend.modules.admin.domain.exceptions.UserAlreadyLockedException;
import com.danasea.backend.modules.admin.domain.exceptions.UserAlreadyUnlockedException;
import com.danasea.backend.modules.admin.domain.exceptions.UserNotFoundException;
import com.danasea.backend.modules.admin.presentation.dtos.LockUserRequest;
import com.danasea.backend.modules.admin.presentation.dtos.UserDetailResponse;
import com.danasea.backend.modules.admin.presentation.dtos.UserSummaryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private GetUsersUseCase getUsersUseCase;

    @Mock
    private GetUserDetailUseCase getUserDetailUseCase;

    @Mock
    private LockUserUseCase lockUserUseCase;

    @Mock
    private UnlockUserUseCase unlockUserUseCase;

    @Mock
    private AccountInternalApi accountInternalApi;

    @InjectMocks
    private AdminUserController adminUserController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminUserController)
                .setControllerAdvice(new AdminExceptionHandler())
                .build();

        UsernamePasswordAuthenticationToken adminAuth = new UsernamePasswordAuthenticationToken(
                "admin@example.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(adminAuth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldGetUsersWithPaginationSuccessfully() throws Exception {
        UUID userId = UUID.randomUUID();
        UserSummaryResponse summary = new UserSummaryResponse(
                userId,
                "customer@example.com",
                "0123456789",
                "Customer Name",
                Role.CUSTOMER,
                "https://avatar.com/img.png",
                true,
                false,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        Page<UserSummaryResponse> page = new PageImpl<>(List.of(summary), PageRequest.of(0, 10), 1);

        when(getUsersUseCase.execute(any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/users")
                        .param("page", "0")
                        .param("size", "10")
                        .param("role", "CUSTOMER")
                        .param("isLocked", "false")
                        .param("search", "customer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(userId.toString()))
                .andExpect(jsonPath("$.content[0].email").value("customer@example.com"))
                .andExpect(jsonPath("$.content[0].fullName").value("Customer Name"))
                .andExpect(jsonPath("$.content[0].role").value("CUSTOMER"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void shouldReturn404WhenUserDetailNotFound() throws Exception {
        UUID userId = UUID.randomUUID();
        when(getUserDetailUseCase.execute(userId))
                .thenThrow(new UserNotFoundException("User not found with id: " + userId));

        mockMvc.perform(get("/api/admin/users/{id}", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User was not found."));
    }

    @Test
    void shouldGetUserDetailSuccessfully() throws Exception {
        UUID userId = UUID.randomUUID();
        UserDetailResponse detail = new UserDetailResponse(
                userId,
                "user@example.com",
                "0987654321",
                "User Name",
                Role.VENDOR,
                "https://avatar.com/img.png",
                true,
                false,
                "vi",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(getUserDetailUseCase.execute(userId)).thenReturn(detail);

        mockMvc.perform(get("/api/admin/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.fullName").value("User Name"))
                .andExpect(jsonPath("$.role").value("VENDOR"))
                .andExpect(jsonPath("$.locale").value("vi"));
    }

    @Test
    void shouldLockUserSuccessfully() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        LockUserRequest request = new LockUserRequest("Violation of terms");

        doNothing().when(lockUserUseCase).execute(any(), eq(targetUserId), eq("Violation of terms"));

        mockMvc.perform(patch("/api/admin/users/{id}/lock", targetUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn404WhenLockingNonExistentUser() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        LockUserRequest request = new LockUserRequest("Violation of terms");

        doThrow(new UserNotFoundException("User not found with id: " + targetUserId))
                .when(lockUserUseCase).execute(any(), eq(targetUserId), any());

        mockMvc.perform(patch("/api/admin/users/{id}/lock", targetUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User was not found."));
    }

    @Test
    void shouldReturn403WhenLockingSelf() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        LockUserRequest request = new LockUserRequest("Self lock attempt");

        doThrow(new SelfLockNotAllowedException("Admin cannot lock own account"))
                .when(lockUserUseCase).execute(any(), eq(targetUserId), any());

        mockMvc.perform(patch("/api/admin/users/{id}/lock", targetUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELF_LOCK_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value("Administrators cannot lock their own account."));
    }

    @Test
    void shouldReturn409WhenLockingAlreadyLockedUser() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        LockUserRequest request = new LockUserRequest("Already locked");

        doThrow(new UserAlreadyLockedException("User is already locked"))
                .when(lockUserUseCase).execute(any(), eq(targetUserId), any());

        mockMvc.perform(patch("/api/admin/users/{id}/lock", targetUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_ALREADY_LOCKED"))
                .andExpect(jsonPath("$.message").value("This user account is already locked."));
    }

    @Test
    void shouldReturn403WhenCallingAdminEndpointWithoutAdminRole() throws Exception {
        UsernamePasswordAuthenticationToken nonAdminAuth = new UsernamePasswordAuthenticationToken(
                "customer@example.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );
        SecurityContextHolder.getContext().setAuthentication(nonAdminAuth);

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("Access denied."));
    }

    @Test
    void shouldReturn403WhenLockingWithoutAdminRole() throws Exception {
        UsernamePasswordAuthenticationToken nonAdminAuth = new UsernamePasswordAuthenticationToken(
                "vendor@example.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_VENDOR"))
        );
        SecurityContextHolder.getContext().setAuthentication(nonAdminAuth);

        mockMvc.perform(patch("/api/admin/users/{id}/lock", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LockUserRequest("Spam"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("Access denied."));
    }

    @Test
    void shouldUnlockUserSuccessfully() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        doNothing().when(unlockUserUseCase).execute(any(), eq(targetUserId));

        mockMvc.perform(patch("/api/admin/users/{id}/unlock", targetUserId))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn404WhenUnlockingNonExistentUser() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        doThrow(new UserNotFoundException("User not found with id: " + targetUserId))
                .when(unlockUserUseCase).execute(any(), eq(targetUserId));

        mockMvc.perform(patch("/api/admin/users/{id}/unlock", targetUserId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void shouldReturn409WhenUnlockingAlreadyUnlockedUser() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        doThrow(new UserAlreadyUnlockedException("User is already unlocked"))
                .when(unlockUserUseCase).execute(any(), eq(targetUserId));

        mockMvc.perform(patch("/api/admin/users/{id}/unlock", targetUserId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_ALREADY_UNLOCKED"));
    }
}
