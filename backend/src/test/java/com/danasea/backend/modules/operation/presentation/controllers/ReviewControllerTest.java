package com.danasea.backend.modules.operation.presentation.controllers;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.danasea.backend.modules.operation.application.usecases.*;
import com.danasea.backend.modules.operation.domain.exceptions.DuplicateReviewException;
import com.danasea.backend.modules.operation.domain.exceptions.InvalidReviewSubOrderStateException;
import com.danasea.backend.modules.operation.presentation.dtos.*;
import com.danasea.backend.modules.operation.presentation.handlers.ReviewExceptionHandler;
import com.danasea.backend.security.authentication.infrastructure.security.JwtAuthenticationFilter;
import com.danasea.backend.security.authentication.presentation.filter.RateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        CustomerReviewController.class,
        ServiceReviewController.class,
        VendorReviewController.class,
        AdminReviewController.class
})
@Import({ReviewExceptionHandler.class, ReviewControllerTest.TestSecurityConfig.class})
@DisplayName("Review Controllers WebMvc Unit Tests")
class ReviewControllerTest {

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
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/services/**").permitAll()
                            .requestMatchers("/api/admin/**").hasRole("ADMIN")
                            .requestMatchers("/api/vendor/**").hasRole("VENDOR")
                            .anyRequest().authenticated())
                    .build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private CreateReviewUseCase createReviewUseCase;

    @MockitoBean
    private UpdateReviewUseCase updateReviewUseCase;

    @MockitoBean
    private FlagReviewUseCase flagReviewUseCase;

    @MockitoBean
    private GetCustomerReviewsUseCase getCustomerReviewsUseCase;

    @MockitoBean
    private GetServiceReviewsUseCase getServiceReviewsUseCase;

    @MockitoBean
    private GetVendorReviewsUseCase getVendorReviewsUseCase;

    @MockitoBean
    private ReplyVendorReviewUseCase replyVendorReviewUseCase;

    @MockitoBean
    private GetAdminReviewsUseCase getAdminReviewsUseCase;

    @MockitoBean
    private UpdateReviewVisibilityUseCase updateReviewVisibilityUseCase;

    private UUID customerId;
    private UUID vendorId;
    private UUID serviceId;
    private UUID subOrderId;
    private UUID reviewId;

    @BeforeEach
    void setUp() {
        customerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        vendorId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        subOrderId = UUID.randomUUID();
        reviewId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("Customer Endpoints (/api/sub-orders/{id}/reviews, /api/reviews/**)")
    class CustomerEndpoints {

        @Test
        @WithMockUser(username = "11111111-1111-1111-1111-111111111111", roles = "CUSTOMER")
        @DisplayName("POST /api/sub-orders/{id}/reviews returns 201 Created on valid request")
        void createReviewSuccess() throws Exception {
            CreateReviewRequest request = new CreateReviewRequest((short) 5, "Dịch vụ tuyệt vời", List.of("https://img.jpg"));

            ReviewResponse response = ReviewResponse.builder()
                    .id(reviewId)
                    .subOrderId(subOrderId)
                    .customerId(customerId)
                    .rating((short) 5)
                    .comment("Dịch vụ tuyệt vời")
                    .images(List.of("https://img.jpg"))
                    .isVisible(true)
                    .isFlagged(false)
                    .createdAt(OffsetDateTime.now())
                    .build();

            when(createReviewUseCase.execute(eq(subOrderId), any(CreateReviewRequest.class), any(UUID.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/sub-orders/" + subOrderId + "/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(reviewId.toString()))
                    .andExpect(jsonPath("$.rating").value(5))
                    .andExpect(jsonPath("$.comment").value("Dịch vụ tuyệt vời"));
        }

        @Test
        @WithMockUser(username = "11111111-1111-1111-1111-111111111111", roles = "CUSTOMER")
        @DisplayName("POST /api/sub-orders/{id}/reviews returns 409 Conflict when duplicate")
        void createReviewDuplicate() throws Exception {
            CreateReviewRequest request = new CreateReviewRequest((short) 5, "Comment", null);

            when(createReviewUseCase.execute(eq(subOrderId), any(CreateReviewRequest.class), any(UUID.class)))
                    .thenThrow(new DuplicateReviewException("Duplicate review"));

            mockMvc.perform(post("/api/sub-orders/" + subOrderId + "/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_REVIEW"));
        }

        @Test
        @WithMockUser(username = "11111111-1111-1111-1111-111111111111", roles = "CUSTOMER")
        @DisplayName("POST /api/sub-orders/{id}/reviews returns 400 Bad Request when order not completed")
        void createReviewNotCompleted() throws Exception {
            CreateReviewRequest request = new CreateReviewRequest((short) 5, "Comment", null);

            when(createReviewUseCase.execute(eq(subOrderId), any(CreateReviewRequest.class), any(UUID.class)))
                    .thenThrow(new InvalidReviewSubOrderStateException("Only completed sub-orders can be reviewed"));

            mockMvc.perform(post("/api/sub-orders/" + subOrderId + "/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_SUB_ORDER_STATE"));
        }

        @Test
        @WithMockUser(username = "11111111-1111-1111-1111-111111111111", roles = "CUSTOMER")
        @DisplayName("PUT /api/reviews/{id} returns 200 OK when updating review")
        void updateReviewSuccess() throws Exception {
            UpdateReviewRequest request = new UpdateReviewRequest((short) 4, "Đã cập nhật", null);

            ReviewResponse response = ReviewResponse.builder()
                    .id(reviewId)
                    .rating((short) 4)
                    .comment("Đã cập nhật")
                    .build();

            when(updateReviewUseCase.execute(eq(reviewId), any(UpdateReviewRequest.class), any(UUID.class)))
                    .thenReturn(response);

            mockMvc.perform(put("/api/reviews/" + reviewId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rating").value(4))
                    .andExpect(jsonPath("$.comment").value("Đã cập nhật"));
        }

        @Test
        @WithMockUser(username = "11111111-1111-1111-1111-111111111111", roles = "CUSTOMER")
        @DisplayName("POST /api/reviews/{id}/flag returns 200 OK when flagging review")
        void flagReviewSuccess() throws Exception {
            FlagReviewRequest request = new FlagReviewRequest("Nội dung không chuẩn mực");

            FlagReviewResponse response = new FlagReviewResponse(reviewId, true);

            when(flagReviewUseCase.execute(eq(reviewId), any(FlagReviewRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/reviews/" + reviewId + "/flag")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isFlagged").value(true))
                    .andExpect(jsonPath("$.comment").doesNotExist())
                    .andExpect(jsonPath("$.flagReason").doesNotExist());
        }

        @Test
        @WithMockUser(username = "11111111-1111-1111-1111-111111111111", roles = "CUSTOMER")
        @DisplayName("GET /api/customer/reviews returns 200 OK with page of customer reviews")
        void getCustomerReviewsSuccess() throws Exception {
            ReviewResponse response = ReviewResponse.builder()
                    .id(reviewId)
                    .subOrderId(subOrderId)
                    .customerId(customerId)
                    .rating((short) 5)
                    .comment("Trải nghiệm đáng nhớ")
                    .vendorReply("Cảm ơn bạn!")
                    .vendorRepliedAt(OffsetDateTime.now())
                    .build();

            when(getCustomerReviewsUseCase.execute(eq(customerId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(response)));

            mockMvc.perform(get("/api/customer/reviews"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(reviewId.toString()))
                    .andExpect(jsonPath("$.content[0].vendorReply").value("Cảm ơn bạn!"));
        }

        @Test
        @WithMockUser(username = "11111111-1111-1111-1111-111111111111", roles = "CUSTOMER")
        @DisplayName("GET /api/sub-orders/{id}/reviews returns 200 OK when review exists")
        void getReviewBySubOrderSuccess() throws Exception {
            ReviewResponse response = ReviewResponse.builder()
                    .id(reviewId)
                    .subOrderId(subOrderId)
                    .customerId(customerId)
                    .rating((short) 5)
                    .comment("Trải nghiệm tuyệt vời")
                    .vendorReply("Cảm ơn quý khách")
                    .vendorRepliedAt(OffsetDateTime.now())
                    .build();

            when(getCustomerReviewsUseCase.executeBySubOrder(eq(subOrderId), eq(customerId)))
                    .thenReturn(response);

            mockMvc.perform(get("/api/sub-orders/" + subOrderId + "/reviews"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(reviewId.toString()))
                    .andExpect(jsonPath("$.vendorReply").value("Cảm ơn quý khách"));
        }
    }

    @Nested
    @DisplayName("Public Service Review Endpoints (/api/services/{id}/reviews)")
    class ServiceReviewEndpoints {

        @Test
        @DisplayName("GET /api/services/{id}/reviews returns paginated reviews without auth")
        void getServiceReviewsSuccess() throws Exception {
            ReviewResponse review = ReviewResponse.builder()
                    .id(reviewId)
                    .serviceId(serviceId)
                    .rating((short) 5)
                    .comment("Rất đáng tiền")
                    .build();

            when(getServiceReviewsUseCase.execute(eq(serviceId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(review)));

            mockMvc.perform(get("/api/services/" + serviceId + "/reviews"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(reviewId.toString()))
                    .andExpect(jsonPath("$.content[0].comment").value("Rất đáng tiền"));
        }
    }

    @Nested
    @DisplayName("Vendor Review Endpoints (/api/vendor/reviews/**)")
    class VendorEndpoints {

        @Test
        @WithMockUser(username = "22222222-2222-2222-2222-222222222222", roles = "VENDOR")
        @DisplayName("GET /api/vendor/reviews returns vendor's reviews")
        void getVendorReviewsSuccess() throws Exception {
            ReviewResponse review = ReviewResponse.builder()
                    .id(reviewId)
                    .vendorId(vendorId)
                    .rating((short) 5)
                    .comment("Tốt")
                    .build();

            when(getVendorReviewsUseCase.execute(eq(UUID.fromString("22222222-2222-2222-2222-222222222222")), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(review)));

            mockMvc.perform(get("/api/vendor/reviews"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(reviewId.toString()));
        }

        @Test
        @WithMockUser(username = "22222222-2222-2222-2222-222222222222", roles = "VENDOR")
        @DisplayName("POST /api/vendor/reviews/{id}/reply returns 200 with updated reply")
        void vendorReplySuccess() throws Exception {
            VendorReplyRequest request = new VendorReplyRequest("Cảm ơn bạn!");

            ReviewResponse response = ReviewResponse.builder()
                    .id(reviewId)
                    .vendorReply("Cảm ơn bạn!")
                    .vendorRepliedAt(OffsetDateTime.now())
                    .build();

            when(replyVendorReviewUseCase.execute(eq(reviewId), any(VendorReplyRequest.class), eq(UUID.fromString("22222222-2222-2222-2222-222222222222"))))
                    .thenReturn(response);

            mockMvc.perform(post("/api/vendor/reviews/" + reviewId + "/reply")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.vendorReply").value("Cảm ơn bạn!"));
        }
    }

    @Nested
    @DisplayName("Admin Review Endpoints (/api/admin/reviews/**)")
    class AdminEndpoints {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("GET /api/admin/reviews returns filtered reviews")
        void getAdminReviewsSuccess() throws Exception {
            ReviewResponse review = ReviewResponse.builder()
                    .id(reviewId)
                    .rating((short) 1)
                    .isFlagged(true)
                    .build();

            when(getAdminReviewsUseCase.execute(any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(review)));

            mockMvc.perform(get("/api/admin/reviews")
                            .param("isFlagged", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(reviewId.toString()));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("PATCH /api/admin/reviews/{id}/visibility returns 200 with updated visibility")
        void updateVisibilitySuccess() throws Exception {
            UpdateReviewVisibilityRequest request = new UpdateReviewVisibilityRequest(false, "Ẩn do vi phạm");

            ReviewResponse response = ReviewResponse.builder()
                    .id(reviewId)
                    .isVisible(false)
                    .moderationNote("Ẩn do vi phạm")
                    .build();

            when(updateReviewVisibilityUseCase.execute(eq(reviewId), any(UpdateReviewVisibilityRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(patch("/api/admin/reviews/" + reviewId + "/visibility")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isVisible").value(false))
                    .andExpect(jsonPath("$.moderationNote").value("Ẩn do vi phạm"));
        }
    }
}
