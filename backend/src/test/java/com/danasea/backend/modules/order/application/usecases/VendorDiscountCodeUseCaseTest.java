package com.danasea.backend.modules.order.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import com.danasea.backend.modules.order.domain.exceptions.InvalidDiscountException;
import com.danasea.backend.modules.order.domain.models.DiscountScope;
import com.danasea.backend.modules.order.domain.models.DiscountSponsorType;
import com.danasea.backend.modules.order.domain.models.DiscountType;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.DiscountCodeJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaDiscountCodeRepository;
import com.danasea.backend.modules.order.presentation.dtos.CreateDiscountCodeRequest;
import com.danasea.backend.modules.order.presentation.dtos.DiscountCodeResponse;
import com.danasea.backend.modules.order.presentation.dtos.UpdateDiscountCodeRequest;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;

@ExtendWith(MockitoExtension.class)
class VendorDiscountCodeUseCaseTest {

    @Mock
    private JpaDiscountCodeRepository discountCodeRepository;

    @Mock
    private JpaServiceRepository serviceRepository;

    private VendorDiscountCodeUseCase useCase;

    private UUID vendorId;
    private UUID serviceId;
    private UUID discountId;

    @BeforeEach
    void setUp() {
        useCase = new VendorDiscountCodeUseCase(discountCodeRepository, serviceRepository);
        vendorId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        discountId = UUID.randomUUID();
    }

    @Test
    @DisplayName("getVendorDiscountCodes returns page of responses and handles empty page")
    void getVendorDiscountCodesReturnsPageAndHandlesEmpty() {
        Pageable pageable = PageRequest.of(0, 10);
        DiscountCodeJpaEntity entity = createValidVendorEntity();

        when(discountCodeRepository.findByVendorId(vendorId, pageable))
                .thenReturn(new PageImpl<>(List.of(entity), pageable, 1));

        Page<DiscountCodeResponse> page = useCase.getVendorDiscountCodes(vendorId, pageable);
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).code()).isEqualTo("KAYAK10");

        // Empty page
        when(discountCodeRepository.findByVendorId(vendorId, pageable))
                .thenReturn(Page.empty(pageable));
        Page<DiscountCodeResponse> emptyPage = useCase.getVendorDiscountCodes(vendorId, pageable);
        assertThat(emptyPage.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("getVendorDiscountCodes requires vendorId")
    void getVendorDiscountCodesRequiresVendorId() {
        assertThatThrownBy(() -> useCase.getVendorDiscountCodes(null, PageRequest.of(0, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Vendor ID is required.");
        verifyNoInteractions(discountCodeRepository);
    }

    @Test
    @DisplayName("getVendorDiscountCodeById returns full response when owner matches")
    void getVendorDiscountCodeByIdReturnsFullResponseWhenOwnerMatches() {
        DiscountCodeJpaEntity entity = createValidVendorEntity();
        when(discountCodeRepository.findById(discountId)).thenReturn(Optional.of(entity));

        DiscountCodeResponse response = useCase.getVendorDiscountCodeById(vendorId, discountId);
        assertThat(response.id()).isEqualTo(discountId);
        assertThat(response.code()).isEqualTo("KAYAK10");
        assertThat(response.scope()).isEqualTo(DiscountScope.VENDOR);
        assertThat(response.sponsorType()).isEqualTo(DiscountSponsorType.VENDOR);
        assertThat(response.vendorId()).isEqualTo(vendorId);
    }

    @Test
    @DisplayName("getVendorDiscountCodeById throws DISCOUNT_NOT_FOUND when ID missing")
    void getVendorDiscountCodeByIdThrowsDiscountNotFoundWhenMissing() {
        when(discountCodeRepository.findById(discountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getVendorDiscountCodeById(vendorId, discountId))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DISCOUNT_NOT_FOUND");
    }

    @Test
    @DisplayName("getVendorDiscountCodeById throws AccessDeniedException when vendor is not owner")
    void getVendorDiscountCodeByIdThrowsAccessDeniedWhenVendorMismatch() {
        DiscountCodeJpaEntity entity = createValidVendorEntity();
        entity.setVendorId(UUID.randomUUID()); // other vendor
        when(discountCodeRepository.findById(discountId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> useCase.getVendorDiscountCodeById(vendorId, discountId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Vendor does not own this discount code.");
    }

    @Test
    @DisplayName("createVendorDiscountCode normalizes code, defaults usedCount=0, and saves full payload")
    void createVendorDiscountCodeNormalizesCodeAndSaves() {
        when(discountCodeRepository.existsByCodeIgnoreCase("KAYAK10")).thenReturn(false);
        when(discountCodeRepository.save(any(DiscountCodeJpaEntity.class))).thenAnswer(inv -> {
            DiscountCodeJpaEntity e = inv.getArgument(0);
            e.setId(discountId);
            return e;
        });

        CreateDiscountCodeRequest request = new CreateDiscountCodeRequest(
                "  kayak10  ",
                DiscountScope.VENDOR,
                DiscountSponsorType.VENDOR,
                null,
                null,
                DiscountType.PERCENTAGE,
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                new BigDecimal("50000.00"),
                100,
                2,
                OffsetDateTime.now().minusHours(1),
                OffsetDateTime.now().plusDays(7),
                true
        );

        DiscountCodeResponse response = useCase.createVendorDiscountCode(vendorId, request);

        ArgumentCaptor<DiscountCodeJpaEntity> captor = ArgumentCaptor.forClass(DiscountCodeJpaEntity.class);
        verify(discountCodeRepository).save(captor.capture());
        DiscountCodeJpaEntity saved = captor.getValue();

        assertThat(saved.getCode()).isEqualTo("KAYAK10");
        assertThat(saved.getScope()).isEqualTo(DiscountScope.VENDOR);
        assertThat(saved.getSponsorType()).isEqualTo(DiscountSponsorType.VENDOR);
        assertThat(saved.getVendorId()).isEqualTo(vendorId);
        assertThat(saved.getUsedCount()).isEqualTo(0);
        assertThat(saved.getIsActive()).isTrue();
        assertThat(saved.getMinOrderAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.code()).isEqualTo("KAYAK10");
    }

    @Test
    @DisplayName("createVendorDiscountCode throws CODE_ALREADY_EXISTS when code exists")
    void createVendorDiscountCodeThrowsWhenCodeExists() {
        when(discountCodeRepository.existsByCodeIgnoreCase("KAYAK10")).thenReturn(true);

        CreateDiscountCodeRequest request = validCreateRequest("  kayak10  ", DiscountType.FIXED, new BigDecimal("50000"));

        assertThatThrownBy(() -> useCase.createVendorDiscountCode(vendorId, request))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CODE_ALREADY_EXISTS");

        verify(discountCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("createVendorDiscountCode accepts percentage 100 and rejects percentage 101")
    void createVendorDiscountCodePercentageBoundary() {
        when(discountCodeRepository.existsByCodeIgnoreCase("PCT100")).thenReturn(false);
        when(discountCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateDiscountCodeRequest req100 = validCreateRequest("PCT100", DiscountType.PERCENTAGE, new BigDecimal("100.00"));
        assertThat(useCase.createVendorDiscountCode(vendorId, req100).discountValue()).isEqualByComparingTo("100.00");

        when(discountCodeRepository.existsByCodeIgnoreCase("PCT101")).thenReturn(false);
        CreateDiscountCodeRequest req101 = validCreateRequest("PCT101", DiscountType.PERCENTAGE, new BigDecimal("100.01"));
        assertThatThrownBy(() -> useCase.createVendorDiscountCode(vendorId, req101))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DISCOUNT_PERCENTAGE_INVALID");

        verify(discountCodeRepository, never()).save(argThat(e -> "PCT101".equals(e.getCode())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-0.01"})
    @DisplayName("createVendorDiscountCode rejects non-positive discount value")
    void createVendorDiscountCodeRejectsNonPositiveValue(String invalidValue) {
        when(discountCodeRepository.existsByCodeIgnoreCase("VALTEST")).thenReturn(false);
        CreateDiscountCodeRequest req = validCreateRequest("VALTEST", DiscountType.FIXED, new BigDecimal(invalidValue));

        assertThatThrownBy(() -> useCase.createVendorDiscountCode(vendorId, req))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_DISCOUNT");

        verify(discountCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("createVendorDiscountCode rejects validTo before or equal validFrom")
    void createVendorDiscountCodeRejectsInvalidDateRange() {
        when(discountCodeRepository.existsByCodeIgnoreCase("DATERANGE")).thenReturn(false);
        OffsetDateTime now = OffsetDateTime.now();

        // validTo equal validFrom
        CreateDiscountCodeRequest reqEqual = new CreateDiscountCodeRequest(
                "DATERANGE", DiscountScope.VENDOR, DiscountSponsorType.VENDOR, null, null,
                DiscountType.FIXED, new BigDecimal("50000"),
                BigDecimal.ZERO, null, 10, 1, now, now, true
        );
        assertThatThrownBy(() -> useCase.createVendorDiscountCode(vendorId, reqEqual))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DISCOUNT_DATE_RANGE_INVALID");

        // validTo before validFrom
        CreateDiscountCodeRequest reqBefore = new CreateDiscountCodeRequest(
                "DATERANGE", DiscountScope.VENDOR, DiscountSponsorType.VENDOR, null, null,
                DiscountType.FIXED, new BigDecimal("50000"),
                BigDecimal.ZERO, null, 10, 1, now, now.minusHours(1), true
        );
        assertThatThrownBy(() -> useCase.createVendorDiscountCode(vendorId, reqBefore))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DISCOUNT_DATE_RANGE_INVALID");

        verify(discountCodeRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("createVendorDiscountCode rejects maxUses and maxUsesPerUser non-positive")
    void createVendorDiscountCodeRejectsNonPositiveUsageCaps(int invalidCap) {
        when(discountCodeRepository.existsByCodeIgnoreCase("CAPTEST")).thenReturn(false);

        CreateDiscountCodeRequest reqUses = new CreateDiscountCodeRequest(
                "CAPTEST", DiscountScope.VENDOR, DiscountSponsorType.VENDOR, null, null,
                DiscountType.FIXED, new BigDecimal("50000"),
                BigDecimal.ZERO, null, invalidCap, 1, null, null, true
        );
        assertThatThrownBy(() -> useCase.createVendorDiscountCode(vendorId, reqUses))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_DISCOUNT");

        CreateDiscountCodeRequest reqUser = new CreateDiscountCodeRequest(
                "CAPTEST", DiscountScope.VENDOR, DiscountSponsorType.VENDOR, null, null,
                DiscountType.FIXED, new BigDecimal("50000"),
                BigDecimal.ZERO, null, 10, invalidCap, null, null, true
        );
        assertThatThrownBy(() -> useCase.createVendorDiscountCode(vendorId, reqUser))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_DISCOUNT");

        verify(discountCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("createVendorDiscountCode rejects negative minOrderAmount")
    void createVendorDiscountCodeRejectsNegativeMinOrderAmount() {
        when(discountCodeRepository.existsByCodeIgnoreCase("MINORDER")).thenReturn(false);
        CreateDiscountCodeRequest req = new CreateDiscountCodeRequest(
                "MINORDER", DiscountScope.VENDOR, DiscountSponsorType.VENDOR, null, null,
                DiscountType.FIXED, new BigDecimal("50000"),
                new BigDecimal("-1.00"), null, 10, 1, null, null, true
        );
        assertThatThrownBy(() -> useCase.createVendorDiscountCode(vendorId, req))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_DISCOUNT");

        verify(discountCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("createVendorDiscountCode with serviceId validates service existence and vendor ownership")
    void createVendorDiscountCodeValidatesServiceOwnership() {
        when(discountCodeRepository.existsByCodeIgnoreCase("SERVICECODE")).thenReturn(false);

        // Service not found
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.empty());
        CreateDiscountCodeRequest req = new CreateDiscountCodeRequest(
                "SERVICECODE", DiscountScope.VENDOR, DiscountSponsorType.VENDOR, null, serviceId,
                DiscountType.FIXED, new BigDecimal("50000"),
                BigDecimal.ZERO, null, 10, 1, null, null, true
        );
        assertThatThrownBy(() -> useCase.createVendorDiscountCode(vendorId, req))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DISCOUNT_SERVICE_INVALID")
                .hasMessageContaining("Discount service was not found");

        // Service belongs to other vendor
        UUID otherVendorId = UUID.randomUUID();
        ServiceJpaEntity otherServiceEntity = new ServiceJpaEntity();
        otherServiceEntity.setId(serviceId);
        otherServiceEntity.setVendorId(otherVendorId);
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(otherServiceEntity));
        assertThatThrownBy(() -> useCase.createVendorDiscountCode(vendorId, req))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DISCOUNT_SERVICE_INVALID")
                .hasMessageContaining("Discount service does not belong to the vendor");

        verify(discountCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateVendorDiscountCode uses findByIdForUpdate, checks owner, scope, and sponsorType")
    void updateVendorDiscountCodeValidatesPermissions() {
        // Not found
        when(discountCodeRepository.findByIdForUpdate(discountId)).thenReturn(Optional.empty());
        UpdateDiscountCodeRequest req = new UpdateDiscountCodeRequest(new BigDecimal("60000"), null, null, null, null, null, null, null);
        assertThatThrownBy(() -> useCase.updateVendorDiscountCode(vendorId, discountId, req))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DISCOUNT_NOT_FOUND");

        // Wrong vendor
        DiscountCodeJpaEntity entityOtherVendor = createValidVendorEntity();
        entityOtherVendor.setVendorId(UUID.randomUUID());
        when(discountCodeRepository.findByIdForUpdate(discountId)).thenReturn(Optional.of(entityOtherVendor));
        assertThatThrownBy(() -> useCase.updateVendorDiscountCode(vendorId, discountId, req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Vendor cannot edit this discount code.");

        // Wrong scope
        DiscountCodeJpaEntity entityPlatformScope = createValidVendorEntity();
        entityPlatformScope.setScope(DiscountScope.PLATFORM);
        when(discountCodeRepository.findByIdForUpdate(discountId)).thenReturn(Optional.of(entityPlatformScope));
        assertThatThrownBy(() -> useCase.updateVendorDiscountCode(vendorId, discountId, req))
                .isInstanceOf(AccessDeniedException.class);

        // Wrong sponsor
        DiscountCodeJpaEntity entityPlatformSponsor = createValidVendorEntity();
        entityPlatformSponsor.setSponsorType(DiscountSponsorType.PLATFORM);
        when(discountCodeRepository.findByIdForUpdate(discountId)).thenReturn(Optional.of(entityPlatformSponsor));
        assertThatThrownBy(() -> useCase.updateVendorDiscountCode(vendorId, discountId, req))
                .isInstanceOf(AccessDeniedException.class);

        verify(discountCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("getVendorDiscountCodeById requires vendorId")
    void getVendorDiscountCodeByIdRequiresVendorId() {
        assertThatThrownBy(() -> useCase.getVendorDiscountCodeById(null, discountId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Vendor ID is required.");
        verifyNoInteractions(discountCodeRepository);
    }

    @Test
    @DisplayName("updateVendorDiscountCode requires vendorId")
    void updateVendorDiscountCodeRequiresVendorId() {
        UpdateDiscountCodeRequest req = new UpdateDiscountCodeRequest(null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> useCase.updateVendorDiscountCode(null, discountId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Vendor ID is required.");
        verifyNoInteractions(discountCodeRepository);
    }

    @Test
    @DisplayName("createVendorDiscountCode defaults isActive to true when request.isActive() is null and requires vendorId")
    void createVendorDiscountCodeDefaultsIsActiveAndRequiresVendorId() {
        assertThatThrownBy(() -> useCase.createVendorDiscountCode(null, validCreateRequest("CODE", DiscountType.FIXED, BigDecimal.ONE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Vendor ID is required.");

        when(discountCodeRepository.existsByCodeIgnoreCase("NULLACTIVE")).thenReturn(false);
        when(discountCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateDiscountCodeRequest reqNullActive = new CreateDiscountCodeRequest(
                "NULLACTIVE", DiscountScope.VENDOR, DiscountSponsorType.VENDOR, null, null,
                DiscountType.FIXED, new BigDecimal("10000"), BigDecimal.ZERO, null, 10, 1, null, null, null
        );
        DiscountCodeResponse res = useCase.createVendorDiscountCode(vendorId, reqNullActive);
        assertThat(res.isActive()).isTrue();
    }

    @Test
    @DisplayName("updateVendorDiscountCode updates all optional fields when provided")
    void updateVendorDiscountCodeUpdatesAllOptionalFields() {
        DiscountCodeJpaEntity entity = createValidVendorEntity();
        entity.setUsedCount(null); // tests entity.getUsedCount() == null branch

        when(discountCodeRepository.findByIdForUpdate(discountId)).thenReturn(Optional.of(entity));
        when(discountCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OffsetDateTime start = OffsetDateTime.now().plusDays(1);
        OffsetDateTime end = start.plusDays(10);

        UpdateDiscountCodeRequest req = new UpdateDiscountCodeRequest(
                new BigDecimal("20.00"),
                new BigDecimal("50000.00"),
                new BigDecimal("100000.00"),
                200,
                5,
                start,
                end,
                false
        );

        DiscountCodeResponse res = useCase.updateVendorDiscountCode(vendorId, discountId, req);

        assertThat(res.discountValue()).isEqualByComparingTo("20.00");
        assertThat(res.minOrderAmount()).isEqualByComparingTo("50000.00");
        assertThat(res.maxDiscountAmount()).isEqualByComparingTo("100000.00");
        assertThat(res.maxUses()).isEqualTo(200);
        assertThat(res.maxUsesPerUser()).isEqualTo(5);
        assertThat(res.validFrom()).isEqualTo(start);
        assertThat(res.validTo()).isEqualTo(end);
        assertThat(res.isActive()).isFalse();
    }

    @Test
    @DisplayName("single argument constructor throws IllegalStateException when service lookup needed")
    void singleArgConstructorThrowsWhenServiceLookupNeeded() {
        VendorDiscountCodeUseCase singleArgUseCase = new VendorDiscountCodeUseCase(discountCodeRepository);
        CreateDiscountCodeRequest req = new CreateDiscountCodeRequest(
                "NOSERVICEREPO", DiscountScope.VENDOR, DiscountSponsorType.VENDOR, null, serviceId,
                DiscountType.FIXED, new BigDecimal("10000"), BigDecimal.ZERO, null, 10, 1, null, null, true
        );

        assertThatThrownBy(() -> singleArgUseCase.createVendorDiscountCode(vendorId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Service ownership lookup is not configured.");
    }
    @Test
    void updateVendorDiscountCodeMaxUsesBoundary() {
        DiscountCodeJpaEntity entity = createValidVendorEntity();
        entity.setUsedCount(3);
        entity.setMaxUses(5);

        when(discountCodeRepository.findByIdForUpdate(discountId)).thenReturn(Optional.of(entity));

        // maxUses = 2 < usedCount (3) -> MAX_USES_LESS_THAN_USED
        UpdateDiscountCodeRequest reqLess = new UpdateDiscountCodeRequest(null, null, null, 2, null, null, null, null);
        assertThatThrownBy(() -> useCase.updateVendorDiscountCode(vendorId, discountId, reqLess))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "MAX_USES_LESS_THAN_USED");

        // maxUses = 3 == usedCount (3) -> accepted
        when(discountCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        UpdateDiscountCodeRequest reqEqual = new UpdateDiscountCodeRequest(null, null, null, 3, null, null, null, null);
        DiscountCodeResponse res = useCase.updateVendorDiscountCode(vendorId, discountId, reqEqual);
        assertThat(res.maxUses()).isEqualTo(3);
        assertThat(res.usedCount()).isEqualTo(3);
        verify(discountCodeRepository).save(entity);
    }

    private DiscountCodeJpaEntity createValidVendorEntity() {
        DiscountCodeJpaEntity entity = new DiscountCodeJpaEntity();
        entity.setId(discountId);
        entity.setCode("KAYAK10");
        entity.setScope(DiscountScope.VENDOR);
        entity.setSponsorType(DiscountSponsorType.VENDOR);
        entity.setVendorId(vendorId);
        entity.setDiscountType(DiscountType.PERCENTAGE);
        entity.setDiscountValue(new BigDecimal("10.00"));
        entity.setMinOrderAmount(BigDecimal.ZERO);
        entity.setMaxUses(100);
        entity.setUsedCount(0);
        entity.setMaxUsesPerUser(2);
        entity.setIsActive(true);
        return entity;
    }

    private CreateDiscountCodeRequest validCreateRequest(String code, DiscountType type, BigDecimal value) {
        return new CreateDiscountCodeRequest(
                code, DiscountScope.VENDOR, DiscountSponsorType.VENDOR, null, null, type, value,
                BigDecimal.ZERO, null, 100, 2, null, null, true
        );
    }

    private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher);
    }
}
