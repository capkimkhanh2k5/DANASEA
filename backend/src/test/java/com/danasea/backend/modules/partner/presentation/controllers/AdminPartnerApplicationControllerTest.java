package com.danasea.backend.modules.partner.presentation.controllers;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.partner.application.usecases.ApprovePartnerApplicationUseCase;
import com.danasea.backend.modules.partner.application.usecases.GetAdminPartnerApplicationDetailUseCase;
import com.danasea.backend.modules.partner.application.usecases.GetAdminPartnerApplicationsUseCase;
import com.danasea.backend.modules.partner.application.usecases.RejectPartnerApplicationUseCase;
import com.danasea.backend.modules.partner.domain.exceptions.ApplicationNotFoundException;
import com.danasea.backend.modules.partner.domain.exceptions.CustomerHasActiveObligationsException;
import com.danasea.backend.modules.partner.domain.models.PartnerApplicationStatus;
import com.danasea.backend.modules.partner.presentation.dtos.AdminRejectApplicationRequest;
import com.danasea.backend.modules.partner.presentation.dtos.PartnerApplicationResponse;
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
import org.springframework.data.domain.PageImpl;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminPartnerApplicationController.class)
@Import({
        PartnerExceptionHandler.class,
        AdminPartnerApplicationControllerTest.TestSecurityConfig.class
})
class AdminPartnerApplicationControllerTest {

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
    private GetAdminPartnerApplicationsUseCase getApplicationsUseCase;

    @MockitoBean
    private GetAdminPartnerApplicationDetailUseCase getApplicationDetailUseCase;

    @MockitoBean
    private ApprovePartnerApplicationUseCase approveApplicationUseCase;

    @MockitoBean
    private RejectPartnerApplicationUseCase rejectApplicationUseCase;

    @MockitoBean
    private AccountInternalApi accountInternalApi;

    private UUID applicationId;
    private UUID adminId;
    private PartnerApplicationResponse sampleResponse;

    @BeforeEach
    void setUp() {
        applicationId = UUID.randomUUID();
        adminId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        sampleResponse = PartnerApplicationResponse.builder()
                .id(applicationId)
                .userId(UUID.randomUUID())
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
    @DisplayName("GET /api/admin/partner-applications returns 200 OK for ADMIN")
    @WithMockUser(username = "99999999-9999-9999-9999-999999999999", roles = "ADMIN")
    void shouldReturn200OnListApplications() throws Exception {
        when(getApplicationsUseCase.execute(eq(PartnerApplicationStatus.PENDING), eq("Danang"), any()))
                .thenReturn(new PageImpl<>(List.of(sampleResponse)));

        mockMvc.perform(get("/api/admin/partner-applications")
                        .param("status", "PENDING").param("search", "Danang"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(applicationId.toString()))
                .andExpect(jsonPath("$.content[0].businessName").value("Danang Beach Tour Co."));
    }

    @Test
    @DisplayName("GET /api/admin/partner-applications returns 403 Forbidden for CUSTOMER")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111", roles = "CUSTOMER")
    void shouldReturn403ForCustomerOnList() throws Exception {
        mockMvc.perform(get("/api/admin/partner-applications"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("GET /api/admin/partner-applications/{id} returns 200 OK for ADMIN")
    @WithMockUser(username = "99999999-9999-9999-9999-999999999999", roles = "ADMIN")
    void shouldReturn200OnGetDetail() throws Exception {
        when(getApplicationDetailUseCase.execute(applicationId)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/admin/partner-applications/" + applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(applicationId.toString()))
                .andExpect(jsonPath("$.businessName").value("Danang Beach Tour Co."));
    }

    @Test
    @DisplayName("GET /api/admin/partner-applications/{id} returns 404 Not Found when application does not exist")
    @WithMockUser(username = "99999999-9999-9999-9999-999999999999", roles = "ADMIN")
    void shouldReturn404WhenNotFound() throws Exception {
        when(getApplicationDetailUseCase.execute(applicationId))
                .thenThrow(new ApplicationNotFoundException("error.application_not_found"));

        mockMvc.perform(get("/api/admin/partner-applications/" + applicationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /api/admin/partner-applications/{id}/approve returns 200 OK for ADMIN")
    @WithMockUser(username = "99999999-9999-9999-9999-999999999999", roles = "ADMIN")
    void shouldReturn200OnApprove() throws Exception {
        PartnerApplicationResponse approvedResponse = PartnerApplicationResponse.builder()
                .id(applicationId)
                .userId(sampleResponse.userId())
                .businessName(sampleResponse.businessName())
                .taxCode(sampleResponse.taxCode())
                .address(sampleResponse.address())
                .bankAccountNumber(sampleResponse.bankAccountNumber())
                .bankName(sampleResponse.bankName())
                .bankAccountHolder(sampleResponse.bankAccountHolder())
                .status(PartnerApplicationStatus.APPROVED)
                .reviewedBy(adminId)
                .reviewedAt(OffsetDateTime.now())
                .createdAt(sampleResponse.createdAt())
                .updatedAt(sampleResponse.updatedAt())
                .build();

        when(approveApplicationUseCase.execute(eq(applicationId), eq(adminId)))
                .thenReturn(approvedResponse);

        mockMvc.perform(patch("/api/admin/partner-applications/" + applicationId + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("PATCH /api/admin/partner-applications/{id}/approve returns 403 Forbidden for CUSTOMER")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111", roles = "CUSTOMER")
    void shouldReturn403ForCustomerOnApprove() throws Exception {
        mockMvc.perform(patch("/api/admin/partner-applications/" + applicationId + "/approve"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("PATCH /api/admin/partner-applications/{id}/reject returns 200 OK for ADMIN with valid reason")
    @WithMockUser(username = "99999999-9999-9999-9999-999999999999", roles = "ADMIN")
    void shouldReturn200OnReject() throws Exception {
        PartnerApplicationResponse rejectedResponse = PartnerApplicationResponse.builder()
                .id(applicationId)
                .userId(sampleResponse.userId())
                .businessName(sampleResponse.businessName())
                .taxCode(sampleResponse.taxCode())
                .address(sampleResponse.address())
                .bankAccountNumber(sampleResponse.bankAccountNumber())
                .bankName(sampleResponse.bankName())
                .bankAccountHolder(sampleResponse.bankAccountHolder())
                .status(PartnerApplicationStatus.REJECTED)
                .rejectionReason("Tax code invalid")
                .reviewedBy(adminId)
                .reviewedAt(OffsetDateTime.now())
                .createdAt(sampleResponse.createdAt())
                .updatedAt(sampleResponse.updatedAt())
                .build();

        when(rejectApplicationUseCase.execute(eq(applicationId), eq(adminId), eq("Tax code invalid")))
                .thenReturn(rejectedResponse);

        mockMvc.perform(patch("/api/admin/partner-applications/" + applicationId + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectionReason\":\"Tax code invalid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Tax code invalid"));
    }

    @Test
    @DisplayName("PATCH /api/admin/partner-applications/{id}/approve returns 409 Conflict when customer has active obligations")
    @WithMockUser(username = "99999999-9999-9999-9999-999999999999", roles = "ADMIN")
    void shouldReturn409OnApproveWhenCustomerHasActiveObligations() throws Exception {
        when(approveApplicationUseCase.execute(eq(applicationId), eq(adminId)))
                .thenThrow(new CustomerHasActiveObligationsException(
                        "Customer has orders currently being processed or pending payment"));

        mockMvc.perform(patch("/api/admin/partner-applications/" + applicationId + "/approve"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_HAS_ACTIVE_OBLIGATIONS"))
                .andExpect(jsonPath("$.message").value("Customer still has active bookings, incomplete orders, pending refunds, or unresolved disputes."));
    }
}
