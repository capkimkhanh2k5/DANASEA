package com.danasea.backend.security.authentication.presentation;
import com.danasea.backend.shared.presentation.GlobalExceptionHandler;
import com.danasea.backend.shared.i18n.SupportedLanguage;

import com.danasea.backend.security.authentication.domain.exceptions.InvalidCredentialsException;
import com.danasea.backend.security.authentication.domain.exceptions.OtpInvalidException;
import com.danasea.backend.security.authentication.domain.exceptions.OtpMaxAttemptsExceededException;
import com.danasea.backend.security.authentication.application.results.LoginResult;
import com.danasea.backend.security.authentication.application.usecases.LoginUseCase;
import com.danasea.backend.security.authentication.application.usecases.LogoutUseCase;
import com.danasea.backend.security.authentication.application.usecases.RefreshTokenUseCase;
import com.danasea.backend.security.authentication.application.usecases.RegisterUseCase;
import com.danasea.backend.security.authentication.application.usecases.SendVerificationOtpUseCase;
import com.danasea.backend.security.authentication.application.usecases.VerifyOtpUseCase;
import com.danasea.backend.security.authentication.application.usecases.GoogleOAuth2LoginUseCase;
import com.danasea.backend.security.authentication.application.usecases.ForgotPasswordUseCase;
import com.danasea.backend.security.authentication.application.usecases.ResetPasswordUseCase;
import com.danasea.backend.security.authentication.presentation.dtos.GoogleOAuth2Request;
import com.danasea.backend.security.authentication.presentation.dtos.LoginRequest;
import com.danasea.backend.security.authentication.presentation.dtos.RegisterRequest;
import com.danasea.backend.security.authentication.presentation.dtos.VerifyOtpRequest;
import com.danasea.backend.security.authentication.presentation.dtos.ForgotPasswordRequest;
import com.danasea.backend.security.authentication.presentation.dtos.ResetPasswordRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.UUID;
import com.danasea.backend.security.authentication.infrastructure.security.JwtProperties;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private LoginUseCase loginUseCase;

    @Mock
    private RegisterUseCase registerUseCase;

    @Mock
    private RefreshTokenUseCase refreshTokenUseCase;

    @Mock
    private LogoutUseCase logoutUseCase;

    @Mock
    private VerifyOtpUseCase verifyOtpUseCase;

    @Mock
    private SendVerificationOtpUseCase sendVerificationOtpUseCase;

    @Mock
    private GoogleOAuth2LoginUseCase googleOAuth2LoginUseCase;

    @Mock
    private ForgotPasswordUseCase forgotPasswordUseCase;

    @Mock
    private ResetPasswordUseCase resetPasswordUseCase;

    private AuthenticationController authenticationController;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties(
                "test-secret-key-for-unit-tests-minimum-256-bits-long-enough", 60, 1);
        authenticationController = new AuthenticationController(
                loginUseCase,
                registerUseCase,
                refreshTokenUseCase,
                logoutUseCase,
                sendVerificationOtpUseCase,
                verifyOtpUseCase,
                googleOAuth2LoginUseCase,
                forgotPasswordUseCase,
                resetPasswordUseCase,
                jwtProperties);
        mockMvc = MockMvcBuilders.standaloneSetup(authenticationController)
                .setControllerAdvice(new AuthenticationExceptionHandler(), new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldLoginAndReturnCookies() throws Exception {
        LoginRequest request = new LoginRequest("test@example.com", "Password1!");
        LoginResult result = new LoginResult("access-token", "refresh-token", UUID.randomUUID(), "test@example.com",
                "CUSTOMER");

        when(loginUseCase.execute(anyString(), anyString())).thenReturn(result);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().secure("refresh_token", true));
    }

    @Test
    void shouldRespectCookieSecureFalseForLocalHttpDevelopment() throws Exception {
        JwtProperties insecureCookieProps = new JwtProperties(
                "test-secret-key-for-unit-tests-minimum-256-bits-long-enough", 60, 7, false);
        AuthenticationController controller = new AuthenticationController(
                loginUseCase,
                registerUseCase,
                refreshTokenUseCase,
                logoutUseCase,
                sendVerificationOtpUseCase,
                verifyOtpUseCase,
                googleOAuth2LoginUseCase,
                forgotPasswordUseCase,
                resetPasswordUseCase,
                insecureCookieProps);
        MockMvc localMockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AuthenticationExceptionHandler(), new GlobalExceptionHandler())
                .build();

        LoginRequest request = new LoginRequest("test@example.com", "Password1!");
        LoginResult result = new LoginResult("access-token", "refresh-token", UUID.randomUUID(), "test@example.com",
                "CUSTOMER");
        when(loginUseCase.execute(anyString(), anyString())).thenReturn(result);

        localMockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().secure("refresh_token", false));
    }

    @Test
    void shouldGoogleLoginAndReturnCookies() throws Exception {
        GoogleOAuth2Request request = new GoogleOAuth2Request("google-id-token-abc", null);
        LoginResult result = new LoginResult("google-access-token", "google-refresh-token", UUID.randomUUID(), "google@example.com",
                "CUSTOMER");

        when(googleOAuth2LoginUseCase.execute("google-id-token-abc")).thenReturn(result);

        mockMvc.perform(post("/api/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("google-access-token"))
                .andExpect(jsonPath("$.email").value("google@example.com"))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true));
    }

    @Test
    void shouldRegisterAndReturnCookies() throws Exception {
        RegisterRequest request = new RegisterRequest("test@example.com", "Password1!");
        LoginResult result = new LoginResult("access-token", "refresh-token", UUID.randomUUID(), "test@example.com",
                "CUSTOMER");

        when(registerUseCase.execute(anyString(), anyString(), any(SupportedLanguage.class))).thenReturn(result);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(cookie().exists("refresh_token"));
    }

    @Test
    void shouldRefreshTokenSuccessfully() throws Exception {
        LoginResult result = new LoginResult("new-access-token", "new-refresh-token", UUID.randomUUID(),
                "test@example.com", "CUSTOMER");

        when(refreshTokenUseCase.execute("old-refresh-token")).thenReturn(result);

        mockMvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie("refresh_token", "old-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(cookie().value("refresh_token", "new-refresh-token"));
    }

    @Test
    void shouldFailRefreshWhenCookieIsMissing() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldFailRefreshWhenTokenInvalidAndClearCookie() throws Exception {
        when(refreshTokenUseCase.execute("invalid-refresh-token"))
                .thenThrow(new InvalidCredentialsException("Invalid refresh token"));

        mockMvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie("refresh_token", "invalid-refresh-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().maxAge("refresh_token", 0))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void shouldLogoutAndClearCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .cookie(new Cookie("refresh_token", "old-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("refresh_token", 0));

        verify(logoutUseCase).execute("old-refresh-token");
    }

    @Test
    void shouldFailVerifyOtpWhenOtpIsInvalid() throws Exception {
        VerifyOtpRequest request = new VerifyOtpRequest("123456");

        doThrow(new OtpInvalidException())
                .when(verifyOtpUseCase).execute(anyString(), eq("123456"));

        // Mock principal for the request since the controller extracts name from
        // principal
        Principal mockPrincipal = mock(Principal.class);
        when(mockPrincipal.getName()).thenReturn("test@example.com");

        mockMvc.perform(post("/api/auth/otp/verify")
                .principal(mockPrincipal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OTP"));
    }

    @Test
    void shouldRejectMissingOtpCode() throws Exception {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("test@example.com");

        mockMvc.perform(post("/api/auth/otp/verify")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(verifyOtpUseCase);
    }

    @Test
    void shouldHandleForgotPasswordSuccessfully() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("user@example.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isNotEmpty());

        verify(forgotPasswordUseCase).execute(eq("user@example.com"), any());
    }

    @Test
    void shouldRejectForgotPasswordWithInvalidEmail() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("invalid-email");

        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(forgotPasswordUseCase);
    }

    @Test
    void shouldResetPasswordSuccessfully() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("user@example.com", "123456", "NewPassword1!");

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isNotEmpty());

        verify(resetPasswordUseCase).execute("user@example.com", "123456", "NewPassword1!");
    }

    @Test
    void shouldRejectResetPasswordWithWeakPassword() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("user@example.com", "123456", "weak");

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(resetPasswordUseCase);
    }

    @Test
    void shouldRejectResetPasswordWithInvalidOtpLength() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("user@example.com", "123", "NewPassword1!");

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(resetPasswordUseCase);
    }

    @Test
    void shouldFailResetPasswordWhenOtpIsInvalid() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("user@example.com", "123456", "NewPassword1!");

        doThrow(new OtpInvalidException())
                .when(resetPasswordUseCase).execute(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OTP"));
    }

    @Test
    void shouldFailResetPasswordWhenMaxAttemptsExceeded() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("user@example.com", "123456", "NewPassword1!");

        doThrow(new OtpMaxAttemptsExceededException())
                .when(resetPasswordUseCase).execute(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OTP"));
    }
}
