package com.danasea.backend.modules.order.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.springframework.data.jpa.domain.Specification;

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
class AdminDiscountCodeUseCaseTest {

    @Mock
    private JpaDiscountCodeRepository discountCodeRepository;

    @Mock
    private JpaServiceRepository serviceRepository;

    private AdminDiscountCodeUseCase useCase;

    private UUID discountId;
    private UUID vendorId;
    private UUID serviceId;

    @BeforeEach
    void setUp() {
        useCase = new AdminDiscountCodeUseCase(discountCodeRepository, serviceRepository);
        discountId = UUID.randomUUID();
        vendorId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
    }

    @Test
    @DisplayName("getDiscountCodes applies Specification, Pageable and returns Page of DiscountCodeResponse")
    @SuppressWarnings("unchecked")
    void getDiscountCodesReturnsPageAndHandlesEmpty() {
        Pageable pageable = PageRequest.of(0, 10);
        DiscountCodeJpaEntity entity = createValidPlatformEntity();

        when(discountCodeRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity), pageable, 1));

        Page<DiscountCodeResponse> page = useCase.getDiscountCodes("danasea", DiscountScope.PLATFORM, null, true, pageable);
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).code()).isEqualTo("DANASEA2026");

        // Empty page
        when(discountCodeRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty(pageable));
        Page<DiscountCodeResponse> emptyPage = useCase.getDiscountCodes(null, null, null, null, pageable);
        assertThat(emptyPage.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("getDiscountCodeById returns full response when found")
    void getDiscountCodeByIdReturnsFullResponseWhenFound() {
        DiscountCodeJpaEntity entity = createValidPlatformEntity();
        when(discountCodeRepository.findById(discountId)).thenReturn(Optional.of(entity));

        DiscountCodeResponse response = useCase.getDiscountCodeById(discountId);
        assertThat(response.id()).isEqualTo(discountId);
        assertThat(response.code()).isEqualTo("DANASEA2026");
        assertThat(response.scope()).isEqualTo(DiscountScope.PLATFORM);
        assertThat(response.sponsorType()).isEqualTo(DiscountSponsorType.PLATFORM);
    }

    @Test
    @DisplayName("getDiscountCodeById throws DISCOUNT_NOT_FOUND when not found")
    void getDiscountCodeByIdThrowsDiscountNotFoundWhenMissing() {
        when(discountCodeRepository.findById(discountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getDiscountCodeById(discountId))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DISCOUNT_NOT_FOUND");
    }

    @Test
    @DisplayName("createDiscountCode normalizes code, defaults sponsorType based on scope, and saves")
    void createDiscountCodeNormalizesCodeAndDefaultsSponsor() {
        when(discountCodeRepository.existsByCodeIgnoreCase("SUMMER50")).thenReturn(false);
        when(discountCodeRepository.save(any(DiscountCodeJpaEntity.class))).thenAnswer(inv -> {
            DiscountCodeJpaEntity e = inv.getArgument(0);
            e.setId(discountId);
            return e;
        });

        // Platform scope without explicit sponsorType defaults to PLATFORM sponsor
        CreateDiscountCodeRequest request = new CreateDiscountCodeRequest(
                "  summer50  ",
                DiscountScope.PLATFORM,
                null,
                null,
                null,
                DiscountType.FIXED,
                new BigDecimal("50000.00"),
                BigDecimal.ZERO,
                null,
                500,
                1,
                OffsetDateTime.now().minusHours(1),
                OffsetDateTime.now().plusDays(30),
                true
        );

        DiscountCodeResponse response = useCase.createDiscountCode(request);

        ArgumentCaptor<DiscountCodeJpaEntity> captor = ArgumentCaptor.forClass(DiscountCodeJpaEntity.class);
        verify(discountCodeRepository).save(captor.capture());
        DiscountCodeJpaEntity saved = captor.getValue();

        assertThat(saved.getCode()).isEqualTo("SUMMER50");
        assertThat(saved.getScope()).isEqualTo(DiscountScope.PLATFORM);
        assertThat(saved.getSponsorType()).isEqualTo(DiscountSponsorType.PLATFORM);
        assertThat(saved.getUsedCount()).isEqualTo(0);
        assertThat(saved.getIsActive()).isTrue();
        assertThat(response.code()).isEqualTo("SUMMER50");
    }

    @Test
    @DisplayName("createDiscountCode throws CODE_ALREADY_EXISTS when duplicate")
    void createDiscountCodeThrowsWhenCodeExists() {
        when(discountCodeRepository.existsByCodeIgnoreCase("DUPCODE")).thenReturn(true);

        CreateDiscountCodeRequest request = validCreateRequest("DUPCODE", DiscountScope.PLATFORM, null, null, DiscountType.FIXED, new BigDecimal("10000"));

        assertThatThrownBy(() -> useCase.createDiscountCode(request))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CODE_ALREADY_EXISTS");

        verify(discountCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("createDiscountCode requires vendorId for VENDOR scope")
    void createDiscountCodeRequiresVendorIdForVendorScope() {
        when(discountCodeRepository.existsByCodeIgnoreCase("VENDORSCOPE")).thenReturn(false);

        CreateDiscountCodeRequest request = new CreateDiscountCodeRequest(
                "VENDORSCOPE", DiscountScope.VENDOR, null, null, null,
                DiscountType.PERCENTAGE, new BigDecimal("10.00"), BigDecimal.ZERO, null, 100, 1, null, null, true
        );

        assertThatThrownBy(() -> useCase.createDiscountCode(request))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "VENDOR_ID_REQUIRED");

        verify(discountCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("createDiscountCode rejects PLATFORM scope with vendorId")
    void createDiscountCodeRejectsPlatformScopeWithVendorId() {
        when(discountCodeRepository.existsByCodeIgnoreCase("PLATMISM")).thenReturn(false);

        CreateDiscountCodeRequest request = new CreateDiscountCodeRequest(
                "PLATMISM", DiscountScope.PLATFORM, DiscountSponsorType.PLATFORM, vendorId, null,
                DiscountType.FIXED, new BigDecimal("10000"), BigDecimal.ZERO, null, 100, 1, null, null, true
        );

        assertThatThrownBy(() -> useCase.createDiscountCode(request))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DISCOUNT_SPONSOR_SCOPE_INVALID");

        verify(discountCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("createDiscountCode rejects VENDOR sponsor with PLATFORM scope")
    void createDiscountCodeRejectsVendorSponsorWithPlatformScope() {
        when(discountCodeRepository.existsByCodeIgnoreCase("SPONMISM")).thenReturn(false);

        CreateDiscountCodeRequest request = new CreateDiscountCodeRequest(
                "SPONMISM", DiscountScope.PLATFORM, DiscountSponsorType.VENDOR, null, null,
                DiscountType.FIXED, new BigDecimal("10000"), BigDecimal.ZERO, null, 100, 1, null, null, true
        );

        assertThatThrownBy(() -> useCase.createDiscountCode(request))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DISCOUNT_SPONSOR_SCOPE_INVALID");

        verify(discountCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("createDiscountCode accepts percentage 100 and rejects percentage 101")
    void createDiscountCodePercentageBoundary() {
        when(discountCodeRepository.existsByCodeIgnoreCase("PCT100")).thenReturn(false);
        when(discountCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateDiscountCodeRequest req100 = validCreateRequest("PCT100", DiscountScope.PLATFORM, null, null, DiscountType.PERCENTAGE, new BigDecimal("100.00"));
        assertThat(useCase.createDiscountCode(req100).discountValue()).isEqualByComparingTo("100.00");

        when(discountCodeRepository.existsByCodeIgnoreCase("PCT101")).thenReturn(false);
        CreateDiscountCodeRequest req101 = validCreateRequest("PCT101", DiscountScope.PLATFORM, null, null, DiscountType.PERCENTAGE, new BigDecimal("100.01"));
        assertThatThrownBy(() -> useCase.createDiscountCode(req101))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DISCOUNT_PERCENTAGE_INVALID");

        verify(discountCodeRepository, never()).save(argThat(e -> "PCT101".equals(e.getCode())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-0.01"})
    @DisplayName("createDiscountCode rejects non-positive discount value")
    void createDiscountCodeRejectsNonPositiveValue(String invalidValue) {
        when(discountCodeRepository.existsByCodeIgnoreCase("VALTEST")).thenReturn(false);
        CreateDiscountCodeRequest req = validCreateRequest("VALTEST", DiscountScope.PLATFORM, null, null, DiscountType.FIXED, new BigDecimal(invalidValue));

        assertThatThrownBy(() -> useCase.createDiscountCode(req))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_DISCOUNT");

        verify(discountCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("createDiscountCode rejects validTo before or equal validFrom")
    void createDiscountCodeRejectsInvalidDateRange() {
        when(discountCodeRepository.existsByCodeIgnoreCase("DATERANGE")).thenReturn(false);
        OffsetDateTime now = OffsetDateTime.now();

        CreateDiscountCodeRequest reqEqual = new CreateDiscountCodeRequest(
                "DATERANGE", DiscountScope.PLATFORM, null, null, null, DiscountType.FIXED, new BigDecimal("50000"),
                BigDecimal.ZERO, null, 10, 1, now, now, true
        );
        assertThatThrownBy(() -> useCase.createDiscountCode(reqEqual))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DISCOUNT_DATE_RANGE_INVALID");

        CreateDiscountCodeRequest reqBefore = new CreateDiscountCodeRequest(
                "DATERANGE", DiscountScope.PLATFORM, null, null, null, DiscountType.FIXED, new BigDecimal("50000"),
                BigDecimal.ZERO, null, 10, 1, now, now.minusHours(1), true
        );
        assertThatThrownBy(() -> useCase.createDiscountCode(reqBefore))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DISCOUNT_DATE_RANGE_INVALID");

        verify(discountCodeRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("createDiscountCode rejects maxUses and maxUsesPerUser non-positive")
    void createDiscountCodeRejectsNonPositiveUsageCaps(int invalidCap) {
        when(discountCodeRepository.existsByCodeIgnoreCase("CAPTEST")).thenReturn(false);

        CreateDiscountCodeRequest reqUses = new CreateDiscountCodeRequest(
                "CAPTEST", DiscountScope.PLATFORM, null, null, null, DiscountType.FIXED, new BigDecimal("50000"),
                BigDecimal.ZERO, null, invalidCap, 1, null, null, true
        );
        assertThatThrownBy(() -> useCase.createDiscountCode(reqUses))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_DISCOUNT");

        CreateDiscountCodeRequest reqUser = new CreateDiscountCodeRequest(
                "CAPTEST", DiscountScope.PLATFORM, null, null, null, DiscountType.FIXED, new BigDecimal("50000"),
                BigDecimal.ZERO, null, 10, invalidCap, null, null, true
        );
        assertThatThrownBy(() -> useCase.createDiscountCode(reqUser))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_DISCOUNT");

        verify(discountCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("createDiscountCode rejects negative minOrderAmount")
    void createDiscountCodeRejectsNegativeMinOrderAmount() {
        when(discountCodeRepository.existsByCodeIgnoreCase("MINORDER")).thenReturn(false);
        CreateDiscountCodeRequest req = new CreateDiscountCodeRequest(
                "MINORDER", DiscountScope.PLATFORM, null, null, null, DiscountType.FIXED, new BigDecimal("50000"),
                new BigDecimal("-1.00"), null, 10, 1, null, null, true
        );
        assertThatThrownBy(() -> useCase.createDiscountCode(req))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_DISCOUNT");

        verify(discountCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("createDiscountCode with serviceId validates service existence and vendor ownership for VENDOR scope")
    void createDiscountCodeValidatesServiceOwnership() {
        when(discountCodeRepository.existsByCodeIgnoreCase("SERVICECODE")).thenReturn(false);

        // Service not found
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.empty());
        CreateDiscountCodeRequest req = new CreateDiscountCodeRequest(
                "SERVICECODE", DiscountScope.VENDOR, DiscountSponsorType.VENDOR, vendorId, serviceId,
                DiscountType.FIXED, new BigDecimal("50000"),
                BigDecimal.ZERO, null, 10, 1, null, null, true
        );
        assertThatThrownBy(() -> useCase.createDiscountCode(req))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DISCOUNT_SERVICE_INVALID")
                .hasMessageContaining("Discount service was not found");

        // Service belongs to another vendor
        UUID otherVendorId = UUID.randomUUID();
        ServiceJpaEntity serviceEntity = new ServiceJpaEntity();
        serviceEntity.setId(serviceId);
        serviceEntity.setVendorId(otherVendorId);
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceEntity));

        assertThatThrownBy(() -> useCase.createDiscountCode(req))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DISCOUNT_SERVICE_INVALID")
                .hasMessageContaining("Discount service does not belong to the vendor");

        verify(discountCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("createDiscountCode defaults isActive to true when request.isActive() is null and supports vendor scope sponsor")
    void createDiscountCodeDefaultsIsActiveAndVendorSponsor() {
        when(discountCodeRepository.existsByCodeIgnoreCase("VENDORSPEC")).thenReturn(false);
        when(discountCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // VENDOR scope without sponsorType defaults to VENDOR sponsor
        CreateDiscountCodeRequest reqVendor = new CreateDiscountCodeRequest(
                "VENDORSPEC", DiscountScope.VENDOR, null, vendorId, null,
                DiscountType.FIXED, new BigDecimal("10000"), BigDecimal.ZERO, null, 10, 1, null, null, null
        );
        DiscountCodeResponse res = useCase.createDiscountCode(reqVendor);
        assertThat(res.isActive()).isTrue();
        assertThat(res.sponsorType()).isEqualTo(DiscountSponsorType.VENDOR);
    }

    @Test
    @DisplayName("updateDiscountCode updates all optional fields when provided")
    void updateDiscountCodeUpdatesAllOptionalFields() {
        DiscountCodeJpaEntity entity = createValidPlatformEntity();
        entity.setUsedCount(null); // tests entity.getUsedCount() == null branch

        when(discountCodeRepository.findByIdForUpdate(discountId)).thenReturn(Optional.of(entity));
        when(discountCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OffsetDateTime start = OffsetDateTime.now().plusDays(1);
        OffsetDateTime end = start.plusDays(15);

        UpdateDiscountCodeRequest req = new UpdateDiscountCodeRequest(
                new BigDecimal("60000.00"),
                new BigDecimal("200000.00"),
                new BigDecimal("500000.00"),
                1000,
                3,
                start,
                end,
                false
        );

        DiscountCodeResponse res = useCase.updateDiscountCode(discountId, req);

        assertThat(res.discountValue()).isEqualByComparingTo("60000.00");
        assertThat(res.minOrderAmount()).isEqualByComparingTo("200000.00");
        assertThat(res.maxDiscountAmount()).isEqualByComparingTo("500000.00");
        assertThat(res.maxUses()).isEqualTo(1000);
        assertThat(res.maxUsesPerUser()).isEqualTo(3);
        assertThat(res.validFrom()).isEqualTo(start);
        assertThat(res.validTo()).isEqualTo(end);
        assertThat(res.isActive()).isFalse();
    }

    @Test
    @DisplayName("single argument constructor throws IllegalStateException when service lookup needed")
    void singleArgConstructorThrowsWhenServiceLookupNeeded() {
        AdminDiscountCodeUseCase singleArgUseCase = new AdminDiscountCodeUseCase(discountCodeRepository);
        CreateDiscountCodeRequest req = new CreateDiscountCodeRequest(
                "NOSERVICEADMIN", DiscountScope.PLATFORM, DiscountSponsorType.PLATFORM, null, serviceId,
                DiscountType.FIXED, new BigDecimal("10000"), BigDecimal.ZERO, null, 10, 1, null, null, true
        );

        assertThatThrownBy(() -> singleArgUseCase.createDiscountCode(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Service ownership lookup is not configured.");
    }
    @Test
    void updateDiscountCodeValidatesInvariants() {
        // Not found
        when(discountCodeRepository.findByIdForUpdate(discountId)).thenReturn(Optional.empty());
        UpdateDiscountCodeRequest req = new UpdateDiscountCodeRequest(new BigDecimal("60000"), null, null, null, null, null, null, null);
        assertThatThrownBy(() -> useCase.updateDiscountCode(discountId, req))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DISCOUNT_NOT_FOUND");

        // maxUses < usedCount
        DiscountCodeJpaEntity entity = createValidPlatformEntity();
        entity.setUsedCount(3);
        entity.setMaxUses(5);
        when(discountCodeRepository.findByIdForUpdate(discountId)).thenReturn(Optional.of(entity));

        UpdateDiscountCodeRequest reqLess = new UpdateDiscountCodeRequest(null, null, null, 2, null, null, null, null);
        assertThatThrownBy(() -> useCase.updateDiscountCode(discountId, reqLess))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "MAX_USES_LESS_THAN_USED");

        // maxUses == usedCount (3) -> accepted
        when(discountCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        UpdateDiscountCodeRequest reqEqual = new UpdateDiscountCodeRequest(null, null, null, 3, null, null, null, null);
        DiscountCodeResponse res = useCase.updateDiscountCode(discountId, reqEqual);
        assertThat(res.maxUses()).isEqualTo(3);
        assertThat(res.usedCount()).isEqualTo(3);
        verify(discountCodeRepository).save(entity);
    }

    @Test
    void cannotChangeVendorFundedCompensationAfterItIsIssued() {
        DiscountCodeJpaEntity voucher = createValidPlatformEntity();
        voucher.setCompensationDisruptionId(UUID.randomUUID());
        BigDecimal issuedAmount = voucher.getDiscountValue();
        when(discountCodeRepository.findByIdForUpdate(discountId)).thenReturn(Optional.of(voucher));

        var update = new UpdateDiscountCodeRequest(issuedAmount.add(BigDecimal.ONE),
                null, null, null, null, null, null, null);

        assertThatThrownBy(() -> useCase.updateDiscountCode(discountId, update))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_DISCOUNT");
        assertThat(voucher.getDiscountValue()).isEqualByComparingTo(issuedAmount);
        verify(discountCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateDiscountCode preserves replacement voucher invariants (must remain personal, single-use, PLATFORM FIXED)")
    void updateDiscountCodePreservesReplacementVoucherInvariants() {
        DiscountCodeJpaEntity replacementVoucher = createValidPlatformEntity();
        replacementVoucher.setReplacementSubOrderId(UUID.randomUUID());
        replacementVoucher.setRecipientCustomerId(UUID.randomUUID());
        replacementVoucher.setMaxUses(1);
        replacementVoucher.setMaxUsesPerUser(1);
        replacementVoucher.setDiscountType(DiscountType.FIXED);
        replacementVoucher.setScope(DiscountScope.PLATFORM);
        replacementVoucher.setSponsorType(DiscountSponsorType.PLATFORM);

        when(discountCodeRepository.findByIdForUpdate(discountId)).thenReturn(Optional.of(replacementVoucher));

        // Attempting to change maxUses to 2 on replacement voucher must fail
        UpdateDiscountCodeRequest reqIncreaseUses = new UpdateDiscountCodeRequest(null, null, null, 2, null, null, null, null);
        assertThatThrownBy(() -> useCase.updateDiscountCode(discountId, reqIncreaseUses))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_DISCOUNT")
                .hasMessageContaining("Replacement vouchers must remain personal, single-use platform fixed discounts.");

        verify(discountCodeRepository, never()).save(any());
    }

    private DiscountCodeJpaEntity createValidPlatformEntity() {
        DiscountCodeJpaEntity entity = new DiscountCodeJpaEntity();
        entity.setId(discountId);
        entity.setCode("DANASEA2026");
        entity.setScope(DiscountScope.PLATFORM);
        entity.setSponsorType(DiscountSponsorType.PLATFORM);
        entity.setDiscountType(DiscountType.FIXED);
        entity.setDiscountValue(new BigDecimal("50000.00"));
        entity.setMinOrderAmount(BigDecimal.ZERO);
        entity.setMaxUses(500);
        entity.setUsedCount(0);
        entity.setMaxUsesPerUser(1);
        entity.setIsActive(true);
        return entity;
    }

    private CreateDiscountCodeRequest validCreateRequest(String code, DiscountScope scope, DiscountSponsorType sponsor, UUID vId, DiscountType type, BigDecimal value) {
        return new CreateDiscountCodeRequest(
                code, scope, sponsor, vId, null, type, value,
                BigDecimal.ZERO, null, 100, 2, null, null, true
        );
    }

    private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher);
    }
}
