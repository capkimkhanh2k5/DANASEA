package com.danasea.backend.modules.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.infrastructure.persistence.entities.UserJpaEntity;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.service.domain.models.OptionStatus;
import com.danasea.backend.modules.service.domain.models.OptionType;
import com.danasea.backend.modules.service.domain.models.PricingUnit;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.CategoryJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceOptionJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaCategoryRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceOptionRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import com.danasea.backend.security.authentication.domain.models.Authentication;
import com.danasea.backend.security.authentication.infrastructure.security.JwtTokenProvider;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
public class ServiceOptionUpdateIntegrationTest extends BaseSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JpaUserRepository userRepository;

    @Autowired
    private JpaVendorRepository vendorRepository;

    @Autowired
    private JpaCategoryRepository categoryRepository;

    @Autowired
    private JpaServiceRepository serviceRepository;

    @Autowired
    private JpaServiceOptionRepository serviceOptionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    private UserJpaEntity ownerUser;
    private VendorJpaEntity ownerVendor;
    private String ownerToken;

    private UserJpaEntity foreignUser;
    private VendorJpaEntity foreignVendor;
    private String foreignToken;

    private CategoryJpaEntity category;
    private ServiceJpaEntity ownedService;
    private ServiceOptionJpaEntity privateOption;
    private ServiceOptionJpaEntity sharedOption;

    @BeforeEach
    void setUp() {
        if (redisTemplate.getConnectionFactory() != null) {
            redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                connection.serverCommands().flushDb();
                return null;
            });
        }
        jdbcTemplate.execute("TRUNCATE users, categories, services, service_options CASCADE");

        String ownerSuffix = UUID.randomUUID().toString().substring(0, 8);
        String foreignSuffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Create owner vendor user & profile
        ownerUser = new UserJpaEntity();
        ownerUser.setEmail("owner_" + ownerSuffix + "@danasea.com");
        ownerUser.setPasswordHash("hashed_pw_owner");
        ownerUser.setFullName("Owner Vendor");
        ownerUser.setRole(Role.VENDOR);
        ownerUser.setIsEmailVerified(true);
        ownerUser.setIsLocked(false);
        ownerUser = userRepository.save(ownerUser);

        ownerVendor = new VendorJpaEntity();
        ownerVendor.setUserId(ownerUser.getId());
        ownerVendor.setBusinessName("Owner Tours");
        ownerVendor.setVerificationStatus(VerificationStatus.APPROVED);
        ownerVendor = vendorRepository.save(ownerVendor);

        ownerToken = jwtTokenProvider.generateAccessToken(new Authentication(
                ownerUser.getId(),
                ownerUser.getEmail(),
                ownerUser.getPasswordHash(),
                ownerUser.getRole().name(),
                true,
                true
        ));

        // 2. Create foreign vendor user & profile
        foreignUser = new UserJpaEntity();
        foreignUser.setEmail("foreign_" + foreignSuffix + "@danasea.com");
        foreignUser.setPasswordHash("hashed_pw_foreign");
        foreignUser.setFullName("Foreign Vendor");
        foreignUser.setRole(Role.VENDOR);
        foreignUser.setIsEmailVerified(true);
        foreignUser.setIsLocked(false);
        foreignUser = userRepository.save(foreignUser);

        foreignVendor = new VendorJpaEntity();
        foreignVendor.setUserId(foreignUser.getId());
        foreignVendor.setBusinessName("Foreign Tours");
        foreignVendor.setVerificationStatus(VerificationStatus.APPROVED);
        foreignVendor = vendorRepository.save(foreignVendor);

        foreignToken = jwtTokenProvider.generateAccessToken(new Authentication(
                foreignUser.getId(),
                foreignUser.getEmail(),
                foreignUser.getPasswordHash(),
                foreignUser.getRole().name(),
                true,
                true
        ));

        // 3. Create Category
        category = new CategoryJpaEntity();
        category.setName("Thuyền & Cano");
        category.setSlug("thuyen-cano");
        category.setIsActive(true);
        category = categoryRepository.save(category);

        // 4. Create Service owned by ownerVendor
        ownedService = new ServiceJpaEntity();
        ownedService.setVendorId(ownerVendor.getId());
        ownedService.setCategoryId(category.getId());
        ownedService.setName("Tour Chèo Thuyền Kayak");
        ownedService.setDescription("Trải nghiệm chèo kayak biển");
        ownedService.setPrice(new BigDecimal("200000.00"));
        ownedService.setStatus(ServiceStatus.PUBLISHED);
        ownedService = serviceRepository.save(ownedService);

        // 5. Create Private Option
        privateOption = ServiceOptionJpaEntity.builder()
                .serviceId(ownedService.getId())
                .name("Cano riêng ban dau")
                .optionType(OptionType.PRIVATE)
                .pricingUnit(PricingUnit.PER_PACKAGE)
                .price(new BigDecimal("1800000.00"))
                .maxPaxPerPackage(10)
                .benefits("Ao phao, huong dan vien")
                .status(OptionStatus.ACTIVE)
                .build();
        privateOption = serviceOptionRepository.save(privateOption);

        // 6. Create Shared Option
        sharedOption = ServiceOptionJpaEntity.builder()
                .serviceId(ownedService.getId())
                .name("Ghe ghep ban dau")
                .optionType(OptionType.SHARED)
                .pricingUnit(PricingUnit.PER_PERSON)
                .price(new BigDecimal("200000.00"))
                .benefits("Ao phao tieu chuan")
                .status(OptionStatus.ACTIVE)
                .build();
        sharedOption = serviceOptionRepository.save(sharedOption);
    }

    @Test
    @DisplayName("Owner PATCH updates option, trims strings, updates DB and returns saved payload")
    void ownerPatchUpdatesOptionAndReturnsSavedPayload() throws Exception {
        OffsetDateTime originalCreatedAt = privateOption.getCreatedAt();
        String requestJson = """
                {
                    "name": "  Kayak private  ",
                    "price": 250000.0,
                    "maxPaxPerPackage": 6,
                    "benefits": "  VIP life jacket  ",
                    "status": "INACTIVE"
                }
                """;

        mockMvc.perform(patch("/api/vendor/services/{id}/options/{optionId}", ownedService.getId(), privateOption.getId())
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(privateOption.getId().toString()))
                .andExpect(jsonPath("$.serviceId").value(ownedService.getId().toString()))
                .andExpect(jsonPath("$.name").value("Kayak private"))
                .andExpect(jsonPath("$.price").value(250000.0))
                .andExpect(jsonPath("$.maxPaxPerPackage").value(6))
                .andExpect(jsonPath("$.benefits").value("VIP life jacket"))
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.optionType").value("PRIVATE"))
                .andExpect(jsonPath("$.pricingUnit").value("PER_PACKAGE"));

        // Reload directly from DB to verify persistence and trim
        ServiceOptionJpaEntity reloaded = serviceOptionRepository.findById(privateOption.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Kayak private");
        assertThat(reloaded.getPrice()).isEqualByComparingTo(new BigDecimal("250000.00"));
        assertThat(reloaded.getMaxPaxPerPackage()).isEqualTo(6);
        assertThat(reloaded.getBenefits()).isEqualTo("VIP life jacket");
        assertThat(reloaded.getStatus()).isEqualTo(OptionStatus.INACTIVE);

        // Immutable fields check
        assertThat(reloaded.getId()).isEqualTo(privateOption.getId());
        assertThat(reloaded.getServiceId()).isEqualTo(ownedService.getId());
        assertThat(reloaded.getOptionType()).isEqualTo(OptionType.PRIVATE);
        assertThat(reloaded.getPricingUnit()).isEqualTo(PricingUnit.PER_PACKAGE);
        if (originalCreatedAt != null) {
            assertThat(reloaded.getCreatedAt()).isEqualTo(originalCreatedAt);
        }
    }

    @Test
    @DisplayName("Foreign vendor is rejected with 403 Forbidden and database is not modified")
    void foreignVendorIsRejectedWith403AndDatabaseIsNotModified() throws Exception {
        String requestJson = """
                {
                    "name": "Hacked Name",
                    "price": 10000.0
                }
                """;

        mockMvc.perform(patch("/api/vendor/services/{id}/options/{optionId}", ownedService.getId(), privateOption.getId())
                .header("Authorization", "Bearer " + foreignToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isForbidden());

        // Verify DB unchanged
        ServiceOptionJpaEntity reloaded = serviceOptionRepository.findById(privateOption.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Cano riêng ban dau");
        assertThat(reloaded.getPrice()).isEqualByComparingTo(new BigDecimal("1800000.00"));
    }

    @Test
    @DisplayName("Option belonging to another service is rejected with 400 Bad Request and database is not modified")
    void optionBelongingToAnotherServiceIsRejectedWith400AndDatabaseIsNotModified() throws Exception {
        // Create another service for the same owner
        ServiceJpaEntity otherService = new ServiceJpaEntity();
        otherService.setVendorId(ownerVendor.getId());
        otherService.setCategoryId(category.getId());
        otherService.setName("Other Tour");
        otherService.setDescription("Another tour");
        otherService.setPrice(new BigDecimal("300000.00"));
        otherService.setStatus(ServiceStatus.PUBLISHED);
        otherService = serviceRepository.save(otherService);

        String requestJson = """
                {
                    "name": "Mismatched Option Update"
                }
                """;

        // Call otherService ID with privateOption ID (which belongs to ownedService)
        mockMvc.perform(patch("/api/vendor/services/{id}/options/{optionId}", otherService.getId(), privateOption.getId())
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isBadRequest());

        // Verify DB unchanged
        ServiceOptionJpaEntity reloaded = serviceOptionRepository.findById(privateOption.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Cano riêng ban dau");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0", "0", "-10000.0", "-0.01"})
    @DisplayName("Zero or negative price receives 400 Bad Request from Bean Validation and database is not modified")
    void zeroOrNegativePriceReceives400AndDatabaseIsNotModified(String invalidPrice) throws Exception {
        String requestJson = String.format("""
                {
                    "price": %s
                }
                """, invalidPrice);

        mockMvc.perform(patch("/api/vendor/services/{id}/options/{optionId}", ownedService.getId(), privateOption.getId())
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isBadRequest());

        // Verify DB unchanged
        ServiceOptionJpaEntity reloaded = serviceOptionRepository.findById(privateOption.getId()).orElseThrow();
        assertThat(reloaded.getPrice()).isEqualByComparingTo(new BigDecimal("1800000.00"));
    }

    @Test
    @DisplayName("Partial patch updates only provided field and preserves existing DB fields")
    void partialPatchUpdatesOnlyProvidedFieldAndPreservesExistingDbFields() throws Exception {
        String requestJson = """
                {
                    "status": "INACTIVE"
                }
                """;

        mockMvc.perform(patch("/api/vendor/services/{id}/options/{optionId}", ownedService.getId(), privateOption.getId())
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.name").value("Cano riêng ban dau"))
                .andExpect(jsonPath("$.price").value(1800000.0))
                .andExpect(jsonPath("$.maxPaxPerPackage").value(10))
                .andExpect(jsonPath("$.benefits").value("Ao phao, huong dan vien"));

        ServiceOptionJpaEntity reloaded = serviceOptionRepository.findById(privateOption.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OptionStatus.INACTIVE);
        assertThat(reloaded.getName()).isEqualTo("Cano riêng ban dau");
        assertThat(reloaded.getPrice()).isEqualByComparingTo(new BigDecimal("1800000.00"));
        assertThat(reloaded.getMaxPaxPerPackage()).isEqualTo(10);
        assertThat(reloaded.getBenefits()).isEqualTo("Ao phao, huong dan vien");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("Private option rejects zero or negative maxPaxPerPackage with 400 Bad Request and DB is unchanged")
    void privateOptionRejectsZeroOrNegativeMaxPaxWith400(int invalidMaxPax) throws Exception {
        String requestJson = String.format("""
                {
                    "maxPaxPerPackage": %d
                }
                """, invalidMaxPax);

        mockMvc.perform(patch("/api/vendor/services/{id}/options/{optionId}", ownedService.getId(), privateOption.getId())
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isBadRequest());

        ServiceOptionJpaEntity reloaded = serviceOptionRepository.findById(privateOption.getId()).orElseThrow();
        assertThat(reloaded.getMaxPaxPerPackage()).isEqualTo(10);
    }
}
