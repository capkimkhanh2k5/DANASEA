package com.danasea.backend.modules.order.presentation.controllers;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

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

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.order.application.usecases.GetVendorSubOrderDetailUseCase;
import com.danasea.backend.modules.order.domain.exceptions.SubOrderNotFoundException;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.presentation.dtos.VendorSubOrderDetailResponse;
import com.danasea.backend.modules.order.presentation.handlers.OrderExceptionHandler;
import com.danasea.backend.security.authentication.infrastructure.security.JwtAuthenticationFilter;
import com.danasea.backend.security.authentication.presentation.filter.RateLimitFilter;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = VendorSubOrderController.class)
@Import({OrderExceptionHandler.class, VendorSubOrderControllerTest.TestSecurityConfig.class})
class VendorSubOrderControllerTest {

    @TestConfiguration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestSecurityConfig {

        @Bean
        @Primary
        public JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter(null, null, null) {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                        throws ServletException, IOException {
                    filterChain.doFilter(request, response);
                }
            };
        }

        @Bean
        @Primary
        public RateLimitFilter rateLimitFilter() {
            return new RateLimitFilter(null) {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                        throws ServletException, IOException {
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
                            .requestMatchers("/api/auth/**").permitAll()
                            .anyRequest().authenticated())
                    .build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetVendorSubOrderDetailUseCase getVendorSubOrderDetailUseCase;

    @MockitoBean
    private AccountInternalApi accountInternalApi;

    private UUID userId;
    private UUID subOrderId;
    private User vendorUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        subOrderId = UUID.randomUUID();

        vendorUser = new User();
        vendorUser.setId(userId);
        vendorUser.setEmail("vendor@example.com");

        when(accountInternalApi.findUserByEmail("vendor@example.com")).thenReturn(Optional.of(vendorUser));
    }

    @Test
    @DisplayName("GET /api/vendor/sub-orders/{id} returns 200 OK with detail when caller is VENDOR and owns suborder")
    @WithMockUser(username = "vendor@example.com", roles = "VENDOR")
    void shouldReturn200WithSubOrderDetail() throws Exception {
        VendorSubOrderDetailResponse detailResponse = VendorSubOrderDetailResponse.builder()
                .id(subOrderId)
                .masterOrderId(UUID.randomUUID())
                .bookingItemId(UUID.randomUUID())
                .status(SubOrderStatus.CONFIRMED)
                .masterOrderStatus(MasterOrderStatus.PAID)
                .serviceId(UUID.randomUUID())
                .serviceName("Da Nang Snorkeling")
                .optionName("Standard")
                .bookingDate(LocalDate.of(2026, 11, 15))
                .bookingTime(LocalTime.of(8, 0))
                .customerName("Tran Van B")
                .customerPhone("0987654321")
                .customerEmail("customerB@example.com")
                .quantity(1)
                .unitPrice(new BigDecimal("350000"))
                .subtotalAmount(new BigDecimal("350000"))
                .finalAmount(new BigDecimal("350000"))
                .vendorPayoutAmount(new BigDecimal("315000"))
                .paymentStatus(PaymentOrderStatus.PAID)
                .checkedIn(false)
                .waiverRequired(true)
                .waiverAccepted(true)
                .build();

        when(getVendorSubOrderDetailUseCase.execute(eq(userId), eq(subOrderId))).thenReturn(detailResponse);

        mockMvc.perform(get("/api/vendor/sub-orders/{id}", subOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(subOrderId.toString()))
                .andExpect(jsonPath("$.serviceName").value("Da Nang Snorkeling"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.customerName").value("Tran Van B"))
                .andExpect(jsonPath("$.customerPhone").value("0987654321"))
                .andExpect(jsonPath("$.customerEmail").value("customerB@example.com"))
                .andExpect(jsonPath("$.finalAmount").value(350000))
                .andExpect(jsonPath("$.vendorPayoutAmount").value(315000));
    }

    @Test
    @DisplayName("GET /api/vendor/sub-orders/{id} returns 403 Forbidden when called by CUSTOMER")
    @WithMockUser(username = "customer@example.com", roles = "CUSTOMER")
    void shouldReturn403WhenCustomerCallsEndpoint() throws Exception {
        mockMvc.perform(get("/api/vendor/sub-orders/{id}", subOrderId))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/vendor/sub-orders/{id} returns 404 NOT FOUND when suborder is not found or not owned")
    @WithMockUser(username = "vendor@example.com", roles = "VENDOR")
    void shouldReturn404WhenSubOrderNotFoundOrNotOwned() throws Exception {
        when(getVendorSubOrderDetailUseCase.execute(eq(userId), eq(subOrderId)))
                .thenThrow(new SubOrderNotFoundException(subOrderId));

        mockMvc.perform(get("/api/vendor/sub-orders/{id}", subOrderId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SUB_ORDER_NOT_FOUND"));
    }
}
