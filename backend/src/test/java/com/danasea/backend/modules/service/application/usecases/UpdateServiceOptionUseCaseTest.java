package com.danasea.backend.modules.service.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.UnauthorizedServiceAccessException;
import com.danasea.backend.modules.service.domain.exceptions.VendorNotApprovedException;
import com.danasea.backend.modules.service.domain.models.OptionStatus;
import com.danasea.backend.modules.service.domain.models.OptionType;
import com.danasea.backend.modules.service.domain.models.PricingUnit;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.ServiceOption;
import com.danasea.backend.modules.service.domain.ports.ServiceOptionRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.service.presentation.dtos.ServiceOptionResponse;
import com.danasea.backend.modules.service.presentation.dtos.UpdateServiceOptionRequest;
import com.danasea.backend.modules.vendor.domain.models.Vendor;

@ExtendWith(MockitoExtension.class)
class UpdateServiceOptionUseCaseTest {

    @Mock
    private ServiceRepositoryPort serviceRepository;

    @Mock
    private ServiceOptionRepositoryPort serviceOptionRepository;

    @Mock
    private VendorPort vendorPort;

    private UpdateServiceOptionUseCase useCase;

    private UUID userId;
    private UUID vendorId;
    private UUID serviceId;
    private UUID optionId;

    @BeforeEach
    void setUp() {
        useCase = new UpdateServiceOptionUseCase(serviceRepository, serviceOptionRepository, vendorPort);
        userId = UUID.randomUUID();
        vendorId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        optionId = UUID.randomUUID();
    }

    @Test
    @DisplayName("patchUpdatesOnlySpecifiedFieldsAndReturnsSavedPayload: trims fields, updates status and pax, preserves immutable fields")
    void patchUpdatesOnlySpecifiedFieldsAndReturnsSavedPayload() {
        authorize();
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-10-01T10:00:00+07:00");
        OffsetDateTime initialUpdatedAt = createdAt.plusMinutes(5);
        OffsetDateTime finalUpdatedAt = createdAt.plusHours(1);

        ServiceOption existingOption = ServiceOption.builder()
                .id(optionId)
                .serviceId(serviceId)
                .name("Old Kayak Name")
                .optionType(OptionType.PRIVATE)
                .pricingUnit(PricingUnit.PER_PACKAGE)
                .price(new BigDecimal("180000.00"))
                .maxPaxPerPackage(2)
                .benefits("Basic equipment")
                .status(OptionStatus.ACTIVE)
                .createdAt(createdAt)
                .updatedAt(initialUpdatedAt)
                .build();

        when(serviceOptionRepository.findById(optionId)).thenReturn(Optional.of(existingOption));
        when(serviceOptionRepository.save(any(ServiceOption.class))).thenAnswer(invocation -> {
            ServiceOption opt = invocation.getArgument(0);
            opt.setUpdatedAt(finalUpdatedAt);
            return opt;
        });

        UpdateServiceOptionRequest request = new UpdateServiceOptionRequest(
                "  Kayak private  ",
                new BigDecimal("250000"),
                4,
                "  VIP life jacket  ",
                OptionStatus.INACTIVE
        );

        ServiceOptionResponse response = useCase.execute(userId, serviceId, optionId, request);

        ArgumentCaptor<ServiceOption> captor = ArgumentCaptor.forClass(ServiceOption.class);
        verify(serviceOptionRepository).save(captor.capture());
        ServiceOption saved = captor.getValue();

        // Mutated fields
        assertThat(saved.getName()).isEqualTo("Kayak private");
        assertThat(saved.getPrice()).isEqualByComparingTo("250000");
        assertThat(saved.getMaxPaxPerPackage()).isEqualTo(4);
        assertThat(saved.getBenefits()).isEqualTo("VIP life jacket");
        assertThat(saved.getStatus()).isEqualTo(OptionStatus.INACTIVE);

        // Immutable fields preserved
        assertThat(saved.getId()).isEqualTo(optionId);
        assertThat(saved.getServiceId()).isEqualTo(serviceId);
        assertThat(saved.getOptionType()).isEqualTo(OptionType.PRIVATE);
        assertThat(saved.getPricingUnit()).isEqualTo(PricingUnit.PER_PACKAGE);
        assertThat(saved.getCreatedAt()).isEqualTo(createdAt);

        // Response matches saved entity including new updatedAt
        assertThat(response.id()).isEqualTo(optionId);
        assertThat(response.serviceId()).isEqualTo(serviceId);
        assertThat(response.name()).isEqualTo("Kayak private");
        assertThat(response.optionType()).isEqualTo(OptionType.PRIVATE);
        assertThat(response.pricingUnit()).isEqualTo(PricingUnit.PER_PACKAGE);
        assertThat(response.price()).isEqualByComparingTo("250000");
        assertThat(response.maxPaxPerPackage()).isEqualTo(4);
        assertThat(response.benefits()).isEqualTo("VIP life jacket");
        assertThat(response.status()).isEqualTo(OptionStatus.INACTIVE);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(finalUpdatedAt);
    }

    @Test
    @DisplayName("null request throws IllegalArgumentException before any port interaction")
    void nullRequestThrowsIllegalArgumentExceptionBeforeAnyPortInteraction() {
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, optionId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Request cannot be null");

        verifyNoInteractions(vendorPort, serviceRepository, serviceOptionRepository);
    }

    @Test
    @DisplayName("missing vendor cannot update option")
    void missingVendorCannotUpdateOption() {
        when(vendorPort.findByUserId(userId)).thenReturn(Optional.empty());

        UpdateServiceOptionRequest request = new UpdateServiceOptionRequest("New Name", null, null, null, null);
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, optionId, request))
                .isInstanceOf(VendorNotApprovedException.class)
                .hasMessageContaining("Vendor not found for user: " + userId);

        verifyNoInteractions(serviceRepository, serviceOptionRepository);
    }

    @Test
    @DisplayName("missing service cannot update option")
    void missingServiceCannotUpdateOption() {
        vendor();
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.empty());

        UpdateServiceOptionRequest request = new UpdateServiceOptionRequest("New Name", null, null, null, null);
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, optionId, request))
                .isInstanceOf(ServiceNotFoundException.class);

        verifyNoInteractions(serviceOptionRepository);
    }

    @Test
    @DisplayName("foreign vendor cannot update option belonging to another vendor's service")
    void foreignVendorCannotUpdateOptionBelongingToAnotherVendorsService() {
        vendor();
        UUID otherVendorId = UUID.randomUUID();
        when(serviceRepository.findById(serviceId))
                .thenReturn(Optional.of(Service.builder().id(serviceId).vendorId(otherVendorId).build()));

        UpdateServiceOptionRequest request = new UpdateServiceOptionRequest("New Name", null, null, null, null);
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, optionId, request))
                .isInstanceOf(UnauthorizedServiceAccessException.class);

        verify(serviceOptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("missing option throws IllegalArgumentException")
    void missingOptionThrowsIllegalArgumentException() {
        authorize();
        when(serviceOptionRepository.findById(optionId)).thenReturn(Optional.empty());

        UpdateServiceOptionRequest request = new UpdateServiceOptionRequest("New Name", null, null, null, null);
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, optionId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Option not found: " + optionId);

        verify(serviceOptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("option belonging to another service is rejected")
    void optionBelongingToAnotherServiceIsRejected() {
        authorize();
        UUID otherServiceId = UUID.randomUUID();
        ServiceOption optionOtherService = ServiceOption.builder()
                .id(optionId)
                .serviceId(otherServiceId)
                .name("Option Other Service")
                .optionType(OptionType.SHARED)
                .pricingUnit(PricingUnit.PER_PERSON)
                .price(new BigDecimal("100000"))
                .build();

        when(serviceOptionRepository.findById(optionId)).thenReturn(Optional.of(optionOtherService));

        UpdateServiceOptionRequest request = new UpdateServiceOptionRequest("New Name", null, null, null, null);
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, optionId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Option does not belong to the specified service");

        verify(serviceOptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("partial patch preserves existing fields when request fields are null")
    void partialPatchPreservesExistingFieldsWhenRequestFieldsAreNull() {
        authorize();
        ServiceOption existingOption = ServiceOption.builder()
                .id(optionId)
                .serviceId(serviceId)
                .name("Preserved Name")
                .optionType(OptionType.PRIVATE)
                .pricingUnit(PricingUnit.PER_PACKAGE)
                .price(new BigDecimal("200000.00"))
                .maxPaxPerPackage(5)
                .benefits("Preserved Benefits")
                .status(OptionStatus.ACTIVE)
                .createdAt(OffsetDateTime.parse("2026-10-01T10:00:00+07:00"))
                .build();

        when(serviceOptionRepository.findById(optionId)).thenReturn(Optional.of(existingOption));
        when(serviceOptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateServiceOptionRequest request = new UpdateServiceOptionRequest(null, null, null, null, null);
        ServiceOptionResponse response = useCase.execute(userId, serviceId, optionId, request);

        assertThat(response.name()).isEqualTo("Preserved Name");
        assertThat(response.price()).isEqualByComparingTo("200000.00");
        assertThat(response.maxPaxPerPackage()).isEqualTo(5);
        assertThat(response.benefits()).isEqualTo("Preserved Benefits");
        assertThat(response.status()).isEqualTo(OptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("blank name is ignored according to current contract")
    void blankNameIsIgnoredAccordingToCurrentContract() {
        authorize();
        ServiceOption existingOption = ServiceOption.builder()
                .id(optionId)
                .serviceId(serviceId)
                .name("Initial Name")
                .optionType(OptionType.SHARED)
                .pricingUnit(PricingUnit.PER_PERSON)
                .price(new BigDecimal("50000.00"))
                .status(OptionStatus.ACTIVE)
                .build();

        when(serviceOptionRepository.findById(optionId)).thenReturn(Optional.of(existingOption));
        when(serviceOptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateServiceOptionRequest request = new UpdateServiceOptionRequest("   ", null, null, null, null);
        ServiceOptionResponse response = useCase.execute(userId, serviceId, optionId, request);

        assertThat(response.name()).isEqualTo("Initial Name");
    }

    @Test
    @DisplayName("empty benefits are trimmed")
    void emptyBenefitsAreTrimmed() {
        authorize();
        ServiceOption existingOption = ServiceOption.builder()
                .id(optionId)
                .serviceId(serviceId)
                .name("Option")
                .optionType(OptionType.SHARED)
                .pricingUnit(PricingUnit.PER_PERSON)
                .price(new BigDecimal("50000.00"))
                .benefits("Initial Benefits")
                .status(OptionStatus.ACTIVE)
                .build();

        when(serviceOptionRepository.findById(optionId)).thenReturn(Optional.of(existingOption));
        when(serviceOptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateServiceOptionRequest request = new UpdateServiceOptionRequest(null, null, null, "   ", null);
        ServiceOptionResponse response = useCase.execute(userId, serviceId, optionId, request);

        assertThat(response.benefits()).isEqualTo("");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -5})
    @DisplayName("private option rejects maxPaxPerPackage zero or negative")
    void privateOptionRejectsMaxPaxPerPackageZeroOrNegative(int invalidMaxPax) {
        authorize();
        ServiceOption existingOption = ServiceOption.builder()
                .id(optionId)
                .serviceId(serviceId)
                .name("Private Boat")
                .optionType(OptionType.PRIVATE)
                .pricingUnit(PricingUnit.PER_PACKAGE)
                .price(new BigDecimal("500000.00"))
                .maxPaxPerPackage(4)
                .status(OptionStatus.ACTIVE)
                .build();

        when(serviceOptionRepository.findById(optionId)).thenReturn(Optional.of(existingOption));

        UpdateServiceOptionRequest request = new UpdateServiceOptionRequest(null, null, invalidMaxPax, null, null);
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, optionId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Private package maximum pax must be greater than 0");

        verify(serviceOptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("private option accepts maxPaxPerPackage equal to 1")
    void privateOptionAcceptsMaxPaxPerPackageEqualToOne() {
        authorize();
        ServiceOption existingOption = ServiceOption.builder()
                .id(optionId)
                .serviceId(serviceId)
                .name("Private Solo")
                .optionType(OptionType.PRIVATE)
                .pricingUnit(PricingUnit.PER_PACKAGE)
                .price(new BigDecimal("500000.00"))
                .maxPaxPerPackage(4)
                .status(OptionStatus.ACTIVE)
                .build();

        when(serviceOptionRepository.findById(optionId)).thenReturn(Optional.of(existingOption));
        when(serviceOptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateServiceOptionRequest request = new UpdateServiceOptionRequest(null, null, 1, null, null);
        ServiceOptionResponse response = useCase.execute(userId, serviceId, optionId, request);

        assertThat(response.maxPaxPerPackage()).isEqualTo(1);
    }

    @Test
    @DisplayName("shared option follows valid path when maxPax is updated")
    void sharedOptionFollowsValidPathWhenMaxPaxIsUpdated() {
        authorize();
        ServiceOption existingOption = ServiceOption.builder()
                .id(optionId)
                .serviceId(serviceId)
                .name("Shared Tour")
                .optionType(OptionType.SHARED)
                .pricingUnit(PricingUnit.PER_PERSON)
                .price(new BigDecimal("150000.00"))
                .maxPaxPerPackage(null)
                .status(OptionStatus.ACTIVE)
                .build();

        when(serviceOptionRepository.findById(optionId)).thenReturn(Optional.of(existingOption));
        when(serviceOptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Shared option with maxPax = 0 or non-null does not hit private check
        UpdateServiceOptionRequest request = new UpdateServiceOptionRequest(null, null, 0, null, null);
        ServiceOptionResponse response = useCase.execute(userId, serviceId, optionId, request);

        assertThat(response.maxPaxPerPackage()).isEqualTo(0);
        verify(serviceOptionRepository).save(any());
    }

    private void vendor() {
        Vendor vendor = new Vendor();
        vendor.setId(vendorId);
        when(vendorPort.findByUserId(userId)).thenReturn(Optional.of(vendor));
    }

    private void authorize() {
        vendor();
        when(serviceRepository.findById(serviceId))
                .thenReturn(Optional.of(Service.builder().id(serviceId).vendorId(vendorId).build()));
    }
}
