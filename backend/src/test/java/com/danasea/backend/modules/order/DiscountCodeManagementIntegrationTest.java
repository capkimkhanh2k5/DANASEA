package com.danasea.backend.modules.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.infrastructure.persistence.entities.UserJpaEntity;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.order.application.usecases.AdminDiscountCodeUseCase;
import com.danasea.backend.modules.order.application.usecases.VendorDiscountCodeUseCase;
import com.danasea.backend.modules.order.domain.exceptions.InvalidDiscountException;
import com.danasea.backend.modules.order.domain.models.DiscountScope;
import com.danasea.backend.modules.order.domain.models.DiscountSponsorType;
import com.danasea.backend.modules.order.domain.models.DiscountType;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.DiscountCodeJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaDiscountCodeRepository;
import com.danasea.backend.modules.order.presentation.dtos.CreateDiscountCodeRequest;
import com.danasea.backend.modules.order.presentation.dtos.DiscountCodeResponse;
import com.danasea.backend.modules.order.presentation.dtos.UpdateDiscountCodeRequest;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.CategoryJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaCategoryRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;

@SpringBootTest
public class DiscountCodeManagementIntegrationTest extends BaseSecurityIntegrationTest {

    @Autowired
    private JpaDiscountCodeRepository discountCodeRepository;

    @Autowired
    private JpaUserRepository userRepository;

    @Autowired
    private JpaVendorRepository vendorRepository;

    @Autowired
    private JpaCategoryRepository categoryRepository;

    @Autowired
    private JpaServiceRepository serviceRepository;

    @Autowired
    private VendorDiscountCodeUseCase vendorDiscountCodeUseCase;

    @Autowired
    private AdminDiscountCodeUseCase adminDiscountCodeUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserJpaEntity vendorAUser;
    private VendorJpaEntity vendorA;

    private UserJpaEntity vendorBUser;
    private VendorJpaEntity vendorB;

    private CategoryJpaEntity category;
    private ServiceJpaEntity serviceA;
    private ServiceJpaEntity serviceB;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE users, categories, services, discount_codes CASCADE");

        // Vendor A
        vendorAUser = new UserJpaEntity();
        vendorAUser.setEmail("vendora_" + UUID.randomUUID().toString().substring(0, 8) + "@danasea.com");
        vendorAUser.setPasswordHash("hashed_pw");
        vendorAUser.setFullName("Vendor A");
        vendorAUser.setRole(Role.VENDOR);
        vendorAUser.setIsEmailVerified(true);
        vendorAUser.setIsLocked(false);
        vendorAUser = userRepository.save(vendorAUser);

        vendorA = new VendorJpaEntity();
        vendorA.setUserId(vendorAUser.getId());
        vendorA.setBusinessName("Vendor A Tours");
        vendorA.setVerificationStatus(VerificationStatus.APPROVED);
        vendorA = vendorRepository.save(vendorA);

        // Vendor B
        vendorBUser = new UserJpaEntity();
        vendorBUser.setEmail("vendorb_" + UUID.randomUUID().toString().substring(0, 8) + "@danasea.com");
        vendorBUser.setPasswordHash("hashed_pw");
        vendorBUser.setFullName("Vendor B");
        vendorBUser.setRole(Role.VENDOR);
        vendorBUser.setIsEmailVerified(true);
        vendorBUser.setIsLocked(false);
        vendorBUser = userRepository.save(vendorBUser);

        vendorB = new VendorJpaEntity();
        vendorB.setUserId(vendorBUser.getId());
        vendorB.setBusinessName("Vendor B Tours");
        vendorB.setVerificationStatus(VerificationStatus.APPROVED);
        vendorB = vendorRepository.save(vendorB);

        // Category
        category = new CategoryJpaEntity();
        category.setName("Tours");
        category.setSlug("tours-" + UUID.randomUUID().toString().substring(0, 8));
        category.setIsActive(true);
        category = categoryRepository.save(category);

        // Service A (owned by Vendor A)
        serviceA = new ServiceJpaEntity();
        serviceA.setVendorId(vendorA.getId());
        serviceA.setCategoryId(category.getId());
        serviceA.setName("Service A");
        serviceA.setDescription("Tour Service A");
        serviceA.setPrice(new BigDecimal("100000.00"));
        serviceA.setStatus(ServiceStatus.PUBLISHED);
        serviceA = serviceRepository.save(serviceA);

        // Service B (owned by Vendor B)
        serviceB = new ServiceJpaEntity();
        serviceB.setVendorId(vendorB.getId());
        serviceB.setCategoryId(category.getId());
        serviceB.setName("Service B");
        serviceB.setDescription("Tour Service B");
        serviceB.setPrice(new BigDecimal("200000.00"));
        serviceB.setStatus(ServiceStatus.PUBLISHED);
        serviceB = serviceRepository.save(serviceB);
    }

    @Test
    @DisplayName("Transaction rollback: Invalid PATCH rolls back mutations made to managed entity before exception")
    void invalidVendorUpdateRollsBackMutationsMadeBeforeException() {
        // 1. Create a persistent discount code with initial values: discountValue=10.00, usedCount=3, maxUses=5
        DiscountCodeJpaEntity code = new DiscountCodeJpaEntity();
        code.setCode("ROLLBACKTEST");
        code.setScope(DiscountScope.VENDOR);
        code.setSponsorType(DiscountSponsorType.VENDOR);
        code.setVendorId(vendorA.getId());
        code.setDiscountType(DiscountType.PERCENTAGE);
        code.setDiscountValue(new BigDecimal("10.00"));
        code.setMinOrderAmount(BigDecimal.ZERO);
        code.setMaxUses(5);
        code.setUsedCount(3);
        code.setMaxUsesPerUser(1);
        code.setIsActive(true);
        code = discountCodeRepository.save(code);

        UUID codeId = code.getId();

        // 2. Call updateVendorDiscountCode with new discountValue = 99.00 AND maxUses = 2 (< usedCount 3)
        // In VendorDiscountCodeUseCase, discountValue is set on the managed entity before the maxUses check throws MAX_USES_LESS_THAN_USED.
        UpdateDiscountCodeRequest invalidRequest = new UpdateDiscountCodeRequest(
                new BigDecimal("99.00"),
                null,
                null,
                2, // invalid: < usedCount (3)
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> vendorDiscountCodeUseCase.updateVendorDiscountCode(vendorA.getId(), codeId, invalidRequest))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "MAX_USES_LESS_THAN_USED");

        // 3. Reload from database directly - verify that the transaction rolled back and discountValue was NOT modified to 99.00!
        DiscountCodeJpaEntity reloaded = discountCodeRepository.findById(codeId).orElseThrow();
        assertThat(reloaded.getDiscountValue()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(reloaded.getMaxUses()).isEqualTo(5);
        assertThat(reloaded.getUsedCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Transaction rollback: Admin invalid PATCH rolls back mutations made before exception")
    void invalidAdminUpdateRollsBackMutationsMadeBeforeException() {
        DiscountCodeJpaEntity code = new DiscountCodeJpaEntity();
        code.setCode("ADMINROLLBACK");
        code.setScope(DiscountScope.PLATFORM);
        code.setSponsorType(DiscountSponsorType.PLATFORM);
        code.setDiscountType(DiscountType.FIXED);
        code.setDiscountValue(new BigDecimal("50000.00"));
        code.setMinOrderAmount(BigDecimal.ZERO);
        code.setMaxUses(10);
        code.setUsedCount(4);
        code.setMaxUsesPerUser(1);
        code.setIsActive(true);
        code = discountCodeRepository.save(code);

        UUID codeId = code.getId();

        // Mutate discountValue to 80000, but set maxUses to 3 (< usedCount 4)
        UpdateDiscountCodeRequest invalidRequest = new UpdateDiscountCodeRequest(
                new BigDecimal("80000.00"),
                null,
                null,
                3, // invalid: < usedCount (4)
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> adminDiscountCodeUseCase.updateDiscountCode(codeId, invalidRequest))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "MAX_USES_LESS_THAN_USED");

        DiscountCodeJpaEntity reloaded = discountCodeRepository.findById(codeId).orElseThrow();
        assertThat(reloaded.getDiscountValue()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(reloaded.getMaxUses()).isEqualTo(10);
    }

    @Test
    @DisplayName("Tenant boundary: Vendor B cannot read or edit Vendor A's discount code")
    void tenantBoundaryPreventsCrossVendorAccess() {
        CreateDiscountCodeRequest createReq = new CreateDiscountCodeRequest(
                "VENDORA10",
                DiscountScope.VENDOR,
                DiscountSponsorType.VENDOR,
                null,
                null,
                DiscountType.PERCENTAGE,
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                null,
                50,
                1,
                null,
                null,
                true
        );

        DiscountCodeResponse created = vendorDiscountCodeUseCase.createVendorDiscountCode(vendorA.getId(), createReq);
        UUID codeId = created.id();

        // Vendor B cannot read Vendor A's code
        assertThatThrownBy(() -> vendorDiscountCodeUseCase.getVendorDiscountCodeById(vendorB.getId(), codeId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Vendor does not own this discount code.");

        // Vendor B cannot update Vendor A's code
        UpdateDiscountCodeRequest updateReq = new UpdateDiscountCodeRequest(
                new BigDecimal("15.00"), null, null, null, null, null, null, null
        );
        assertThatThrownBy(() -> vendorDiscountCodeUseCase.updateVendorDiscountCode(vendorB.getId(), codeId, updateReq))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Vendor cannot edit this discount code.");

        // Vendor B listing does not contain Vendor A's code
        Page<DiscountCodeResponse> vendorBPages = vendorDiscountCodeUseCase.getVendorDiscountCodes(vendorB.getId(), PageRequest.of(0, 10));
        assertThat(vendorBPages.getContent()).noneMatch(c -> c.id().equals(codeId));

        // Vendor A listing contains it
        Page<DiscountCodeResponse> vendorAPages = vendorDiscountCodeUseCase.getVendorDiscountCodes(vendorA.getId(), PageRequest.of(0, 10));
        assertThat(vendorAPages.getContent()).anyMatch(c -> c.id().equals(codeId));
    }

    @Test
    @DisplayName("Admin search and filtering across codes by code, scope, vendorId, isActive")
    void adminSearchAndFilterAcrossCodes() {
        // Create 1 platform code
        adminDiscountCodeUseCase.createDiscountCode(new CreateDiscountCodeRequest(
                "PLATFORM_ACTIVE",
                DiscountScope.PLATFORM,
                DiscountSponsorType.PLATFORM,
                null,
                null,
                DiscountType.FIXED,
                new BigDecimal("20000.00"),
                BigDecimal.ZERO,
                null,
                100,
                1,
                null,
                null,
                true
        ));

        // Create 1 vendor A code (inactive)
        adminDiscountCodeUseCase.createDiscountCode(new CreateDiscountCodeRequest(
                "VENDORA_INACTIVE",
                DiscountScope.VENDOR,
                DiscountSponsorType.VENDOR,
                vendorA.getId(),
                null,
                DiscountType.PERCENTAGE,
                new BigDecimal("5.00"),
                BigDecimal.ZERO,
                null,
                50,
                1,
                null,
                null,
                false
        ));

        // Filter by scope PLATFORM
        Page<DiscountCodeResponse> platformCodes = adminDiscountCodeUseCase.getDiscountCodes(
                null, DiscountScope.PLATFORM, null, null, PageRequest.of(0, 10)
        );
        assertThat(platformCodes.getContent()).allMatch(c -> c.scope() == DiscountScope.PLATFORM);
        assertThat(platformCodes.getContent()).anyMatch(c -> "PLATFORM_ACTIVE".equals(c.code()));

        // Filter by vendorId
        Page<DiscountCodeResponse> vendorACodes = adminDiscountCodeUseCase.getDiscountCodes(
                null, null, vendorA.getId(), null, PageRequest.of(0, 10)
        );
        assertThat(vendorACodes.getContent()).allMatch(c -> vendorA.getId().equals(c.vendorId()));

        // Filter by isActive = false
        Page<DiscountCodeResponse> inactiveCodes = adminDiscountCodeUseCase.getDiscountCodes(
                null, null, null, false, PageRequest.of(0, 10)
        );
        assertThat(inactiveCodes.getContent()).allMatch(c -> Boolean.FALSE.equals(c.isActive()));

        // Filter by partial code search
        Page<DiscountCodeResponse> codeSearch = adminDiscountCodeUseCase.getDiscountCodes(
                "active", null, null, null, PageRequest.of(0, 10)
        );
        assertThat(codeSearch.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("Vendor discount code with serviceId validates against real services in DB")
    void vendorDiscountCodeValidatesAgainstRealServicesInDb() {
        // Valid: Vendor A uses Service A (which Vendor A owns)
        CreateDiscountCodeRequest validReq = new CreateDiscountCodeRequest(
                "SERVICE_A_DISCOUNT",
                DiscountScope.VENDOR,
                DiscountSponsorType.VENDOR,
                null,
                serviceA.getId(),
                DiscountType.FIXED,
                new BigDecimal("15000.00"),
                BigDecimal.ZERO,
                null,
                20,
                1,
                null,
                null,
                true
        );
        DiscountCodeResponse created = vendorDiscountCodeUseCase.createVendorDiscountCode(vendorA.getId(), validReq);
        assertThat(created.serviceId()).isEqualTo(serviceA.getId());

        // Invalid: Vendor A tries to use Service B (owned by Vendor B)
        CreateDiscountCodeRequest invalidReq = new CreateDiscountCodeRequest(
                "SERVICE_B_DISCOUNT",
                DiscountScope.VENDOR,
                DiscountSponsorType.VENDOR,
                null,
                serviceB.getId(),
                DiscountType.FIXED,
                new BigDecimal("15000.00"),
                BigDecimal.ZERO,
                null,
                20,
                1,
                null,
                null,
                true
        );
        assertThatThrownBy(() -> vendorDiscountCodeUseCase.createVendorDiscountCode(vendorA.getId(), invalidReq))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DISCOUNT_SERVICE_INVALID")
                .hasMessageContaining("Discount service does not belong to the vendor");

        assertThat(discountCodeRepository.existsByCodeIgnoreCase("SERVICE_B_DISCOUNT")).isFalse();
    }
}
