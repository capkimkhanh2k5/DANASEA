package com.danasea.backend.modules.partner.presentation.controllers;

import com.danasea.backend.modules.partner.application.usecases.GetMyPartnerApplicationUseCase;
import com.danasea.backend.modules.partner.application.usecases.ResubmitPartnerApplicationUseCase;
import com.danasea.backend.modules.partner.application.usecases.SubmitPartnerApplicationUseCase;
import com.danasea.backend.modules.partner.domain.exceptions.ApplicationNotFoundException;
import com.danasea.backend.modules.partner.domain.exceptions.ApplicationStatusConflictException;
import com.danasea.backend.modules.partner.domain.models.PartnerApplicationStatus;
import com.danasea.backend.modules.partner.presentation.dtos.PartnerApplicationResponse;
import com.danasea.backend.modules.partner.presentation.dtos.ResubmitPartnerApplicationRequest;
import com.danasea.backend.modules.partner.presentation.dtos.SubmitPartnerApplicationRequest;
import com.danasea.backend.modules.partner.presentation.handlers.PartnerExceptionHandler;
import com.danasea.backend.security.authentication.infrastructure.security.JwtAuthenticationFilter;
import com.danasea.backend.security.authentication.presentation.filter.RateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PartnerApplicationController.class)
@Import({
        PartnerExceptionHandler.class,
        PartnerApplicationControllerTest.TestSecurityConfig.class
})
class PartnerApplicationControllerTest {

    @TestConfiguration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestSecurityConfig {

        @Bean
        @Primary
        RateLimitFilter rateLimitFilter() {
            return new RateLimitFilter(null) {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                        FilterChain filterChain) throws ServletException, IOException {
                    filterChain.doFilter(request, response);
                }
            };
        }

        @Bean
        @Primary
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter(null, null, null) {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                        FilterChain filterChain) throws ServletException, IOException {
                    filterChain.doFilter(request, response);
                }
            };
        }

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .exceptionHandling(exception -> exception
                            .authenticationEntryPoint((request, response, authException) -> {
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                                response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Unauthorized\"}");
                            })
                            .accessDeniedHandler((request, response, accessDenied) -> {
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                                response.getWriter().write("{\"code\":\"ACCESS_DENIED\",\"message\":\"Access denied\"}");
                            }))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/auth/**", "/actuator/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                            .anyRequest().authenticated())
                    .build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private SubmitPartnerApplicationUseCase submitUseCase;

    @MockitoBean
    private GetMyPartnerApplicationUseCase getMyUseCase;

    @MockitoBean
    private ResubmitPartnerApplicationUseCase resubmitUseCase;

    private UUID customerId;
    private PartnerApplicationResponse sampleResponse;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        sampleResponse = PartnerApplicationResponse.builder()
                .id(UUID.randomUUID())
                .userId(customerId)
                .businessName("Danang Beach Tour Co.")
                .taxCode("0401234567")
                .address("123 Vo Nguyen Giap")
                .bankAccountNumber("1234567890")
                .bankName("Vietcombank")
                .bankAccountHolder("NGUYEN VAN A")
                .status(PartnerApplicationStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("POST /api/partner-applications returns 201 Created for authenticated CUSTOMER")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111", roles = "CUSTOMER")
    void shouldReturn201OnValidSubmit() throws Exception {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        SubmitPartnerApplicationRequest request = new SubmitPartnerApplicationRequest(
                "Danang Beach Tour Co.",
                "0401234567",
                "123 Vo Nguyen Giap",
                "1234567890",
                "Vietcombank",
                "NGUYEN VAN A"
        );

        when(submitUseCase.execute(eq(userId), any())).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/partner-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessName").value("Danang Beach Tour Co."))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/partner-applications returns 403 Forbidden for non-CUSTOMER (ADMIN)")
    @WithMockUser(username = "22222222-2222-2222-2222-222222222222", roles = "ADMIN")
    void shouldReturn403ForAdminOnSubmit() throws Exception {
        SubmitPartnerApplicationRequest request = new SubmitPartnerApplicationRequest(
                "Danang Beach Tour Co.",
                "0401234567",
                "123 Vo Nguyen Giap",
                "1234567890",
                "Vietcombank",
                "NGUYEN VAN A"
        );

        mockMvc.perform(post("/api/partner-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("POST /api/partner-applications returns 409 Conflict when already pending")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111", roles = "CUSTOMER")
    void shouldReturn409WhenAlreadyPending() throws Exception {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        SubmitPartnerApplicationRequest request = new SubmitPartnerApplicationRequest(
                "Danang Beach Tour Co.",
                "0401234567",
                "123 Vo Nguyen Giap",
                "1234567890",
                "Vietcombank",
                "NGUYEN VAN A"
        );

        when(submitUseCase.execute(eq(userId), any()))
                .thenThrow(new ApplicationStatusConflictException("error.application_already_pending"));

        mockMvc.perform(post("/api/partner-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_STATUS_CONFLICT"));
    }

    @Test
    @DisplayName("POST /api/partner-applications returns 409 Conflict when already rejected")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111", roles = "CUSTOMER")
    void shouldReturn409WhenAlreadyRejectedOnSubmit() throws Exception {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        SubmitPartnerApplicationRequest request = new SubmitPartnerApplicationRequest(
                "Danang Beach Tour Co.",
                "0401234567",
                "123 Vo Nguyen Giap",
                "1234567890",
                "Vietcombank",
                "NGUYEN VAN A"
        );

        when(submitUseCase.execute(eq(userId), any()))
                .thenThrow(new ApplicationStatusConflictException("error.application_already_rejected"));

        mockMvc.perform(post("/api/partner-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_STATUS_CONFLICT"));
    }

    @Test
    @DisplayName("GET /api/partner-applications/my returns 200 OK for CUSTOMER")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111", roles = "CUSTOMER")
    void shouldReturn200OnGetMy() throws Exception {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(getMyUseCase.execute(userId)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/partner-applications/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Danang Beach Tour Co."))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/partner-applications/my returns 404 when user has no application")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111", roles = "CUSTOMER")
    void shouldReturn404WhenNoApplication() throws Exception {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(getMyUseCase.execute(userId)).thenThrow(new ApplicationNotFoundException("error.application_not_found"));

        mockMvc.perform(get("/api/partner-applications/my"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/partner-applications/resubmit returns 200 OK for REJECTED application")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111", roles = "CUSTOMER")
    void shouldReturn200OnResubmit() throws Exception {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        ResubmitPartnerApplicationRequest request = new ResubmitPartnerApplicationRequest(
                "Danang Beach Tour Co. Updated",
                "0401234567",
                "123 Vo Nguyen Giap",
                "1234567890",
                "Vietcombank",
                "NGUYEN VAN A"
        );

        when(resubmitUseCase.execute(eq(userId), any())).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/partner-applications/resubmit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/partner-applications returns 409 Conflict when customer has active obligations")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111", roles = "CUSTOMER")
    void shouldReturn409WhenCustomerHasActiveObligations() throws Exception {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        SubmitPartnerApplicationRequest request = new SubmitPartnerApplicationRequest(
                "Danang Beach Tour Co.",
                "0401234567",
                "123 Vo Nguyen Giap",
                "1234567890",
                "Vietcombank",
                "NGUYEN VAN A"
        );

        when(submitUseCase.execute(eq(userId), any()))
                .thenThrow(new com.danasea.backend.modules.partner.domain.exceptions.CustomerHasActiveObligationsException(
                        "Customer has active booking hold or pending payment"));

        mockMvc.perform(post("/api/partner-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_HAS_ACTIVE_OBLIGATIONS"))
                .andExpect(jsonPath("$.message").value("Customer still has active bookings, incomplete orders, pending refunds, or unresolved disputes."));
    }
}
