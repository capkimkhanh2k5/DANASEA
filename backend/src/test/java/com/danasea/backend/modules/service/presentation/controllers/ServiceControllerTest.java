package com.danasea.backend.modules.service.presentation.controllers;
import com.danasea.backend.security.authentication.application.ports.TokenProvider;
import com.danasea.backend.security.authentication.application.ports.UserAccountPort;
import com.danasea.backend.security.authorization.application.ports.AuthorizationPort;

import org.springframework.boot.test.context.TestConfiguration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.danasea.backend.modules.service.application.usecases.ApproveServiceUseCase;
import com.danasea.backend.modules.service.application.usecases.CreateServiceUseCase;
import com.danasea.backend.modules.service.application.usecases.DeleteServiceUseCase;
import com.danasea.backend.modules.service.application.usecases.GetAdminServicesUseCase;
import com.danasea.backend.modules.service.application.usecases.GetServiceDetailUseCase;
import com.danasea.backend.modules.service.application.usecases.GetVendorServicesUseCase;
import com.danasea.backend.modules.service.application.usecases.PauseServiceUseCase;
import com.danasea.backend.modules.service.application.usecases.RejectServiceUseCase;
import com.danasea.backend.modules.service.application.usecases.ResumeServiceUseCase;
import com.danasea.backend.modules.service.application.usecases.SubmitServiceForReviewUseCase;
import com.danasea.backend.modules.service.application.usecases.UpdateServiceUseCase;
import com.danasea.backend.modules.service.presentation.handlers.ServiceExceptionHandler;

/**
 * MockMvc security tests for VendorServiceController and
 * AdminServiceController.
 * Tests focus on RBAC: correct role gets 2xx, wrong role gets 403.
 *
 * Strategy:
 * - Use @WebMvcTest (lightweight slice) without loading infrastructure beans.
 * - Override security config with a minimal test-only config that enables
 * 
 * @PreAuthorize RBAC checks without the JwtAuthenticationFilter/Redis/RabbitMQ.
 *               - Use SecurityMockMvcRequestPostProcessors.user() to inject
 *               test principals.
 */
@WebMvcTest(controllers = { VendorServiceController.class, AdminServiceController.class })
@Import({ ServiceControllerTest.TestSecurityConfig.class, ServiceExceptionHandler.class })
@DisplayName("ServiceControllerTest — RBAC & Security")
class ServiceControllerTest {

    /**
     * Minimal security configuration for the test slice.
     * Replaces the full application SecurityConfig so we don't need JJWT/Redis.
     * Preserves method-level @PreAuthorize with @EnableMethodSecurity.
     */
    @TestConfiguration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint(
                                    (request, response, authException) -> response.sendError(401, "Unauthorized")))
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .build();
        }

        @Bean
        UserDetailsService testUserDetailsService() {
            // Provide minimal UDS so Spring Security can start without SecurityProperties
            return new InMemoryUserDetailsManager();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateServiceUseCase createServiceUseCase;
    @MockitoBean
    private GetVendorServicesUseCase getVendorServicesUseCase;
    @MockitoBean
    private GetServiceDetailUseCase getServiceDetailUseCase;
    @MockitoBean
    private UpdateServiceUseCase updateServiceUseCase;
    @MockitoBean
    private SubmitServiceForReviewUseCase submitServiceForReviewUseCase;
    @MockitoBean
    private PauseServiceUseCase pauseServiceUseCase;
    @MockitoBean
    private ResumeServiceUseCase resumeServiceUseCase;
    @MockitoBean
    private DeleteServiceUseCase deleteServiceUseCase;
    @MockitoBean
    private GetAdminServicesUseCase getAdminServicesUseCase;
    @MockitoBean
    private ApproveServiceUseCase approveServiceUseCase;
    @MockitoBean
    private RejectServiceUseCase rejectServiceUseCase;

    // Mock infrastructure beans picked up by @WebMvcTest component scan
    @MockitoBean
    private TokenProvider tokenProvider;
    @MockitoBean
    private UserAccountPort userAccountPort;
    @MockitoBean
    private AuthorizationPort authorizationPort;
    @MockitoBean
    private io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager<byte[]> lettuceBasedProxyManager;

    private final UUID serviceId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    // ==================== Vendor Endpoint Tests ====================

    @Nested
    @DisplayName("/api/vendor/services/** — Role enforcement")
    class VendorEndpointTests {

        @Test
        @DisplayName("✅ VENDOR → GET /api/vendor/services → 200 OK")
        void vendorCanListServices() throws Exception {
            when(getVendorServicesUseCase.execute(any())).thenReturn(List.of());

            mockMvc.perform(get("/api/vendor/services")
                    .with(user(userId.toString()).roles("VENDOR")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("❌ CUSTOMER → GET /api/vendor/services → 403 Forbidden")
        void customerCannotAccessVendorEndpoints() throws Exception {
            mockMvc.perform(get("/api/vendor/services")
                    .with(user("customer").roles("CUSTOMER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("❌ ADMIN → GET /api/vendor/services → 403 Forbidden")
        void adminCannotAccessVendorEndpoints() throws Exception {
            mockMvc.perform(get("/api/vendor/services")
                    .with(user("admin").roles("ADMIN")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("❌ Unauthenticated → GET /api/vendor/services → 401")
        void unauthenticatedCannotAccessVendorEndpoints() throws Exception {
            mockMvc.perform(get("/api/vendor/services"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("❌ CUSTOMER → POST /api/vendor/services → 403")
        void customerCannotCreateService() throws Exception {
            // Send a structurally valid body — security RBAC check runs before bean
            // validation
            String validBody = """
                    {"categoryId":"00000000-0000-0000-0000-000000000001",
                     "name":"Test","price":100000,"durationMinutes":60}""";
            mockMvc.perform(post("/api/vendor/services")
                    .with(user("customer").roles("CUSTOMER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validBody))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("❌ ADMIN → DELETE /api/vendor/services/{id} → 403")
        void adminCannotDeleteVendorService() throws Exception {
            mockMvc.perform(delete("/api/vendor/services/" + serviceId)
                    .with(user("admin").roles("ADMIN")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("❌ CUSTOMER → POST /api/vendor/services/{id}/submit → 403")
        void customerCannotSubmitService() throws Exception {
            mockMvc.perform(post("/api/vendor/services/" + serviceId + "/submit")
                    .with(user("customer").roles("CUSTOMER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("❌ ADMIN → PATCH /api/vendor/services/{id}/pause → 403")
        void adminCannotPauseVendorService() throws Exception {
            mockMvc.perform(patch("/api/vendor/services/" + serviceId + "/pause")
                    .with(user("admin").roles("ADMIN")))
                    .andExpect(status().isForbidden());
        }
    }

    // ==================== Admin Endpoint Tests ====================

    @Nested
    @DisplayName("/api/admin/services/** — Role enforcement")
    class AdminEndpointTests {

        @Test
        @DisplayName("✅ ADMIN → GET /api/admin/services → 200 OK")
        void adminCanListServices() throws Exception {
            when(getAdminServicesUseCase.execute(any())).thenReturn(List.of());

            mockMvc.perform(get("/api/admin/services")
                    .with(user(userId.toString()).roles("ADMIN")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("❌ VENDOR → GET /api/admin/services → 403 Forbidden")
        void vendorCannotAccessAdminEndpoints() throws Exception {
            mockMvc.perform(get("/api/admin/services")
                    .with(user("vendor").roles("VENDOR")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("❌ CUSTOMER → GET /api/admin/services → 403 Forbidden")
        void customerCannotAccessAdminEndpoints() throws Exception {
            mockMvc.perform(get("/api/admin/services")
                    .with(user("customer").roles("CUSTOMER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("❌ VENDOR → PATCH /api/admin/services/{id}/approve → 403")
        void vendorCannotApproveService() throws Exception {
            mockMvc.perform(patch("/api/admin/services/" + serviceId + "/approve")
                    .with(user("vendor").roles("VENDOR")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("❌ CUSTOMER → PATCH /api/admin/services/{id}/reject → 403")
        void customerCannotRejectService() throws Exception {
            mockMvc.perform(patch("/api/admin/services/" + serviceId + "/reject")
                    .with(user("customer").roles("CUSTOMER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"bad\"}"))
                    .andExpect(status().isForbidden());
        }
    }
}
