package com.danasea.backend.modules.service;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;

import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.infrastructure.persistence.entities.UserJpaEntity;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.CategoryJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaCategoryRepository;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.security.authentication.presentation.dtos.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class ServiceIntegrationE2ETest extends BaseSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JpaUserRepository userRepository;

    @Autowired
    private JpaCategoryRepository categoryRepository;

    @Autowired
    private JpaVendorRepository vendorRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID testVendorId;
    private UUID testVendorProfileId;
    private UUID testCategoryId;
    private String testVendorEmail;
    private String testAdminEmail;

    @BeforeEach
    void setup() {
        testVendorEmail = UUID.randomUUID() + "@vendor.example.test";
        testAdminEmail = UUID.randomUUID() + "@admin.example.test";
        // 1. Create a Vendor user in the DB
        UserJpaEntity vendor = new UserJpaEntity();
        vendor.setEmail(testVendorEmail);
        vendor.setPasswordHash(passwordEncoder.encode("Password123!"));
        vendor.setFullName("Test Vendor");
        vendor.setRole(Role.VENDOR);
        vendor.setIsEmailVerified(true);
        vendor.setIsLocked(false);
        vendor = userRepository.save(vendor);
        testVendorId = vendor.getId();

        VendorJpaEntity vendorProfile = new VendorJpaEntity();
        vendorProfile.setUserId(testVendorId);
        vendorProfile.setBusinessName("Test Vendor Business");
        vendorProfile.setVerificationStatus(VerificationStatus.APPROVED);
        vendorProfile = vendorRepository.save(vendorProfile);
        testVendorProfileId = vendorProfile.getId();

        // 2. Create an Admin user for other tests (optional)
        UserJpaEntity admin = new UserJpaEntity();
        admin.setEmail(testAdminEmail);
        admin.setPasswordHash(passwordEncoder.encode("Password123!"));
        admin.setFullName("Test Admin");
        admin.setRole(Role.ADMIN);
        admin.setIsEmailVerified(true);
        admin.setIsLocked(false);
        userRepository.save(admin);

        // 3. Create a Category
        CategoryJpaEntity category = new CategoryJpaEntity();
        category.setName("Water Sports");
        category.setSlug("water-sports-" + UUID.randomUUID());
        category.setIsActive(true);
        category = categoryRepository.save(category);
        testCategoryId = category.getId();
    }

    @Test
    void testVendorCanLoginAndCreateService() throws Exception {
        // Step 1: Login to get real JWT token
        LoginRequest loginRequest = new LoginRequest(testVendorEmail, "Password123!");
        
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseBody).get("accessToken").asText();

        // Step 2: Use token to create a service (Requires VENDOR role)
        String createServiceJson = """
            {
                "categoryId": "%s",
                "name": "Surfing Lesson",
                "price": 50.00,
                "durationMinutes": 60,
                "capacityPerSlot": 5
            }
        """.formatted(testCategoryId.toString());

        mockMvc.perform(post("/api/vendor/services")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createServiceJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Surfing Lesson"))
                .andExpect(jsonPath("$.vendorId").value(testVendorProfileId.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void testAdminCannotCreateService() throws Exception {
        // Step 1: Login as Admin
        LoginRequest loginRequest = new LoginRequest(testAdminEmail, "Password123!");
        
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asText();

        // Step 2: Try to create a service (Should be FORBIDDEN because role is ADMIN, not VENDOR)
        String createServiceJson = """
            {
                "categoryId": "%s",
                "name": "Admin Surfing Lesson",
                "price": 50.00,
                "durationMinutes": 60
            }
        """.formatted(testCategoryId.toString());

        mockMvc.perform(post("/api/vendor/services")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createServiceJson))
                .andExpect(status().isForbidden());
    }
}
