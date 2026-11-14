package com.danasea.backend.modules.vendor.presentation.controllers;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.vendor.application.usecases.GetVendorDocumentsUseCase;
import com.danasea.backend.modules.vendor.application.usecases.GetVendorProfileUseCase;
import com.danasea.backend.modules.vendor.application.usecases.RegisterVendorProfileUseCase;
import com.danasea.backend.modules.vendor.application.usecases.UpdateVendorProfileUseCase;
import com.danasea.backend.modules.vendor.application.usecases.ResubmitVendorProfileUseCase;
import com.danasea.backend.modules.vendor.application.usecases.UploadVendorDocumentUseCase;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorDocumentsIncompleteException;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorStatusConflictException;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorNotFoundException;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.DocStatus;
import com.danasea.backend.modules.vendor.domain.models.DocType;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.modules.vendor.domain.models.VendorDocument;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.presentation.handlers.VendorExceptionHandler;
import com.danasea.backend.modules.vendor.presentation.dtos.RegisterVendorProfileRequest;
import com.danasea.backend.modules.vendor.presentation.dtos.UpdateVendorProfileRequest;
import com.danasea.backend.security.authentication.infrastructure.security.JwtAuthenticationFilter;
import com.danasea.backend.security.authentication.presentation.filter.RateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = VendorProfileController.class)
@Import({VendorExceptionHandler.class, VendorProfileControllerTest.TestSecurityConfig.class})
class VendorProfileControllerTest {

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
                            .requestMatchers("/api/auth/**", "/actuator/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                            .anyRequest().authenticated())
                    .build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private RegisterVendorProfileUseCase registerVendorProfileUseCase;

    @MockitoBean
    private GetVendorProfileUseCase getVendorProfileUseCase;

    @MockitoBean
    private UpdateVendorProfileUseCase updateVendorProfileUseCase;

    @MockitoBean
    private UploadVendorDocumentUseCase uploadVendorDocumentUseCase;

    @MockitoBean
    private GetVendorDocumentsUseCase getVendorDocumentsUseCase;

    @MockitoBean
    private ResubmitVendorProfileUseCase resubmitVendorProfileUseCase;

    @MockitoBean
    private AccountInternalApi accountInternalApi;

    private UUID vendorUserId;
    private UUID customerUserId;
    private UUID adminUserId;
    private UUID vendorId;
    private Vendor testVendor;

    @BeforeEach
    void setUp() {
        vendorUserId = UUID.randomUUID();
        customerUserId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();
        vendorId = UUID.randomUUID();

        User vendorUser = new User();
        vendorUser.setId(vendorUserId);
        vendorUser.setEmail("vendor@example.com");
        vendorUser.setRole(Role.VENDOR);

        User customerUser = new User();
        customerUser.setId(customerUserId);
        customerUser.setEmail("customer@example.com");
        customerUser.setRole(Role.CUSTOMER);

        User adminUser = new User();
        adminUser.setId(adminUserId);
        adminUser.setEmail("admin@example.com");
        adminUser.setRole(Role.ADMIN);

        when(accountInternalApi.findUserByEmail("vendor@example.com")).thenReturn(Optional.of(vendorUser));
        when(accountInternalApi.findUserByEmail("customer@example.com")).thenReturn(Optional.of(customerUser));
        when(accountInternalApi.findUserByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));

        testVendor = new Vendor();
        testVendor.setId(vendorId);
        testVendor.setUserId(vendorUserId);
        testVendor.setBusinessName("Danang Fresh Seafood");
        testVendor.setTaxCode("0123456789");
        testVendor.setAddress("123 Vo Nguyen Giap, Da Nang");
        testVendor.setBankAccountNumber("9876543210");
        testVendor.setBankName("Vietcombank");
        testVendor.setBankAccountHolder("NGUYEN VAN A");
        testVendor.setVerificationStatus(VerificationStatus.APPROVED);
        testVendor.setBadgeTier(BadgeTier.VERIFIED);
        testVendor.setRatingAvg(new BigDecimal("4.80"));
        testVendor.setRatingCount(12);
        testVendor.setCreatedAt(OffsetDateTime.now());
        testVendor.setUpdatedAt(OffsetDateTime.now());
    }

    // ==========================================
    // 1. GET /api/vendor/profile
    // ==========================================

    @Test
    @DisplayName("GET /api/vendor/profile returns 200 OK with VendorProfileResponse for authenticated VENDOR")
    @WithMockUser(username = "vendor@example.com", roles = "VENDOR")
    void shouldReturn200ForAuthenticatedVendorOnGetProfile() throws Exception {
        when(getVendorProfileUseCase.execute(vendorUserId)).thenReturn(testVendor);

        mockMvc.perform(get("/api/vendor/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vendorId.toString()))
                .andExpect(jsonPath("$.userId").value(vendorUserId.toString()))
                .andExpect(jsonPath("$.businessName").value("Danang Fresh Seafood"))
                .andExpect(jsonPath("$.taxCode").value("0123456789"))
                .andExpect(jsonPath("$.verificationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.badgeTier").value("VERIFIED"))
                .andExpect(jsonPath("$.ratingAvg").value(4.80))
                .andExpect(jsonPath("$.ratingCount").value(12));
    }

    @Test
    @DisplayName("GET /api/vendor/profile returns 404 Not Found for CUSTOMER without vendor profile")
    @WithMockUser(username = "customer@example.com", roles = "CUSTOMER")
    void shouldReturn404ForCustomerWithoutVendorProfileOnGetProfile() throws Exception {
        when(getVendorProfileUseCase.execute(customerUserId))
                .thenThrow(new VendorNotFoundException("Vendor profile not found"));

        mockMvc.perform(get("/api/vendor/profile"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VENDOR_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Vendor was not found."));
    }

    // ==========================================
    // 2. Security Authorization Checks
    // ==========================================

    @Test
    @DisplayName("GET /api/vendor/profile returns 401 Unauthorized for unauthenticated requests")
    void shouldReturn401ForUnauthenticatedRequestOnGetProfile() throws Exception {
        mockMvc.perform(get("/api/vendor/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/vendor/profile returns 403 Forbidden for ADMIN attempting to access VENDOR routes")
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void shouldReturn403ForAdminOnGetProfile() throws Exception {
        mockMvc.perform(get("/api/vendor/profile"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("PATCH /api/vendor/profile returns 403 Forbidden for ADMIN")
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void shouldReturn403ForAdminOnPatchProfile() throws Exception {
        UpdateVendorProfileRequest request = new UpdateVendorProfileRequest(
                "New Name", null, null, null, null, null
        );

        mockMvc.perform(patch("/api/vendor/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("PATCH /api/vendor/profile returns 403 Forbidden for CUSTOMER")
    @WithMockUser(username = "customer@example.com", roles = "CUSTOMER")
    void shouldReturn403ForCustomerOnPatchProfile() throws Exception {
        UpdateVendorProfileRequest request = new UpdateVendorProfileRequest(
                "New Name", null, null, null, null, null
        );

        mockMvc.perform(patch("/api/vendor/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("POST /api/vendor/documents returns 403 Forbidden for ADMIN")
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void shouldReturn403ForAdminOnUploadDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "license.pdf", "application/pdf", "content".getBytes()
        );

        mockMvc.perform(multipart("/api/vendor/documents")
                        .file(file)
                        .param("doc_type", "BUSINESS_LICENSE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ==========================================
    // 3. POST /api/vendor/profile (Deactivated Endpoint)
    // ==========================================

    @Test
    @DisplayName("POST /api/vendor/profile returns 403 Forbidden for CUSTOMER attempting self-elevation")
    @WithMockUser(username = "customer@example.com", roles = "CUSTOMER")
    void shouldReturn403ForCustomerOnRegisterProfile() throws Exception {
        RegisterVendorProfileRequest request = new RegisterVendorProfileRequest(
                "Danang Fresh Seafood",
                "0123456789",
                "123 Vo Nguyen Giap, Da Nang",
                "9876543210",
                "Vietcombank",
                "NGUYEN VAN A"
        );

        mockMvc.perform(post("/api/vendor/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("POST /api/vendor/profile returns 403 Forbidden for ADMIN because endpoint is deprecated and denyAll")
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void shouldReturn403ForAdminOnRegisterProfile() throws Exception {
        RegisterVendorProfileRequest request = new RegisterVendorProfileRequest(
                "Danang Fresh Seafood",
                "0123456789",
                "123 Vo Nguyen Giap, Da Nang",
                "9876543210",
                "Vietcombank",
                "NGUYEN VAN A"
        );

        mockMvc.perform(post("/api/vendor/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("POST /api/vendor/profile returns 403 Forbidden for VENDOR because endpoint is deprecated and denyAll")
    @WithMockUser(username = "vendor@example.com", roles = "VENDOR")
    void shouldReturn403ForVendorOnRegisterProfile() throws Exception {
        RegisterVendorProfileRequest request = new RegisterVendorProfileRequest(
                "Danang Fresh Seafood",
                "0123456789",
                "123 Vo Nguyen Giap, Da Nang",
                "9876543210",
                "Vietcombank",
                "NGUYEN VAN A"
        );

        mockMvc.perform(post("/api/vendor/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ==========================================
    // 4. PATCH /api/vendor/profile (Update)
    // ==========================================

    @Test
    @DisplayName("PATCH /api/vendor/profile returns 200 OK with updated profile for authenticated VENDOR")
    @WithMockUser(username = "vendor@example.com", roles = "VENDOR")
    void shouldReturn200OnValidPatchProfile() throws Exception {
        UpdateVendorProfileRequest request = new UpdateVendorProfileRequest(
                "Updated Seafood Market",
                null,
                "999 Bach Dang, Da Nang",
                null,
                null,
                null
        );

        Vendor updatedVendor = new Vendor();
        updatedVendor.setId(vendorId);
        updatedVendor.setUserId(vendorUserId);
        updatedVendor.setBusinessName("Updated Seafood Market");
        updatedVendor.setTaxCode(testVendor.getTaxCode());
        updatedVendor.setAddress("999 Bach Dang, Da Nang");
        updatedVendor.setBankAccountNumber(testVendor.getBankAccountNumber());
        updatedVendor.setBankName(testVendor.getBankName());
        updatedVendor.setBankAccountHolder(testVendor.getBankAccountHolder());
        updatedVendor.setVerificationStatus(VerificationStatus.APPROVED);
        updatedVendor.setBadgeTier(BadgeTier.VERIFIED);
        updatedVendor.setRatingAvg(testVendor.getRatingAvg());
        updatedVendor.setRatingCount(testVendor.getRatingCount());

        when(updateVendorProfileUseCase.execute(eq(vendorUserId), any())).thenReturn(updatedVendor);

        mockMvc.perform(patch("/api/vendor/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Updated Seafood Market"))
                .andExpect(jsonPath("$.address").value("999 Bach Dang, Da Nang"))
                .andExpect(jsonPath("$.verificationStatus").value("APPROVED"));
    }

    // ==========================================
    // 5. POST /api/vendor/documents (Upload)
    // ==========================================

    @Test
    @DisplayName("POST /api/vendor/documents returns 201 Created with multipart file and doc_type param")
    @WithMockUser(username = "vendor@example.com", roles = "VENDOR")
    void shouldReturn201OnDocumentUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "business_license.pdf", "application/pdf", "dummy pdf content".getBytes()
        );

        UUID docId = UUID.randomUUID();
        VendorDocument createdDoc = new VendorDocument();
        createdDoc.setId(docId);
        createdDoc.setVendorId(vendorId);
        createdDoc.setDocType(DocType.BUSINESS_LICENSE);
        createdDoc.setFileUrl("https://cloudinary.com/danasea/docs/license.pdf");
        createdDoc.setStatus(DocStatus.PENDING);

        when(uploadVendorDocumentUseCase.execute(eq(vendorUserId), any(), eq("BUSINESS_LICENSE")))
                .thenReturn(createdDoc);

        mockMvc.perform(multipart("/api/vendor/documents")
                        .file(file)
                        .param("doc_type", "BUSINESS_LICENSE"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(docId.toString()))
                .andExpect(jsonPath("$.vendorId").value(vendorId.toString()))
                .andExpect(jsonPath("$.docType").value("BUSINESS_LICENSE"))
                .andExpect(jsonPath("$.fileUrl").value("https://cloudinary.com/danasea/docs/license.pdf"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ==========================================
    // 6. GET /api/vendor/documents (Listing)
    // ==========================================

    @Test
    @DisplayName("GET /api/vendor/documents returns 200 OK with list of vendor documents")
    @WithMockUser(username = "vendor@example.com", roles = "VENDOR")
    void shouldReturn200WithDocumentList() throws Exception {
        VendorDocument doc1 = new VendorDocument();
        doc1.setId(UUID.randomUUID());
        doc1.setVendorId(vendorId);
        doc1.setDocType(DocType.BUSINESS_LICENSE);
        doc1.setFileUrl("https://cloudinary.com/license.pdf");
        doc1.setStatus(DocStatus.APPROVED);

        VendorDocument doc2 = new VendorDocument();
        doc2.setId(UUID.randomUUID());
        doc2.setVendorId(vendorId);
        doc2.setDocType(DocType.SAFETY_CERT);
        doc2.setFileUrl("https://cloudinary.com/cert.pdf");
        doc2.setStatus(DocStatus.PENDING);

        when(getVendorDocumentsUseCase.execute(vendorUserId)).thenReturn(List.of(doc1, doc2));

        mockMvc.perform(get("/api/vendor/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].docType").value("BUSINESS_LICENSE"))
                .andExpect(jsonPath("$[0].status").value("APPROVED"))
                .andExpect(jsonPath("$[1].docType").value("SAFETY_CERT"))
                .andExpect(jsonPath("$[1].status").value("PENDING"));
    }

    // ==========================================
    // 7. POST /api/vendor/profile/resubmit
    // ==========================================

    @Test
    @DisplayName("POST /api/vendor/profile/resubmit returns 200 OK with PENDING status and null rejectionReason")
    @WithMockUser(username = "vendor@example.com", roles = "VENDOR")
    void shouldReturn200WhenResubmitSuccess() throws Exception {
        Vendor resubmittedVendor = new Vendor();
        resubmittedVendor.setId(vendorId);
        resubmittedVendor.setUserId(vendorUserId);
        resubmittedVendor.setBusinessName("Da Nang Sea Tours");
        resubmittedVendor.setTaxCode("0401234567");
        resubmittedVendor.setAddress("12 Vo Nguyen Giap");
        resubmittedVendor.setBankAccountNumber("0123456789");
        resubmittedVendor.setBankName("Vietcombank");
        resubmittedVendor.setBankAccountHolder("DA NANG SEA TOURS");
        resubmittedVendor.setVerificationStatus(VerificationStatus.PENDING);
        resubmittedVendor.setRejectionReason(null);

        when(resubmitVendorProfileUseCase.execute(vendorUserId)).thenReturn(resubmittedVendor);

        mockMvc.perform(post("/api/vendor/profile/resubmit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vendorId.toString()))
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.rejectionReason").isEmpty());
    }

    @Test
    @DisplayName("POST /api/vendor/profile/resubmit returns 403 Forbidden when called by CUSTOMER")
    @WithMockUser(username = "customer@example.com", roles = "CUSTOMER")
    void shouldReturn403WhenCustomerCallsResubmit() throws Exception {
        mockMvc.perform(post("/api/vendor/profile/resubmit"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/vendor/profile/resubmit returns 409 Conflict when vendor is already PENDING")
    @WithMockUser(username = "vendor@example.com", roles = "VENDOR")
    void shouldReturn409WhenVendorAlreadyPending() throws Exception {
        when(resubmitVendorProfileUseCase.execute(vendorUserId))
                .thenThrow(new VendorStatusConflictException("VENDOR_ALREADY_PENDING", "Vendor profile is currently PENDING review"));

        mockMvc.perform(post("/api/vendor/profile/resubmit"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VENDOR_ALREADY_PENDING"));
    }

    @Test
    @DisplayName("POST /api/vendor/profile/resubmit returns 400 Bad Request when documents are incomplete")
    @WithMockUser(username = "vendor@example.com", roles = "VENDOR")
    void shouldReturn400WhenDocumentsIncomplete() throws Exception {
        when(resubmitVendorProfileUseCase.execute(vendorUserId))
                .thenThrow(new VendorDocumentsIncompleteException("Missing required document: SAFETY_CERT"));

        mockMvc.perform(post("/api/vendor/profile/resubmit"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DOCUMENTS_INCOMPLETE"));
    }
}
