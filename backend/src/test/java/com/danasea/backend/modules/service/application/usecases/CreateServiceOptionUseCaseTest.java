package com.danasea.backend.modules.service.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import com.danasea.backend.modules.service.presentation.dtos.CreateServiceOptionRequest;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateServiceOptionUseCaseTest {
    @Mock private ServiceRepositoryPort services;
    @Mock private ServiceOptionRepositoryPort options;
    @Mock private VendorPort vendors;
    private CreateServiceOptionUseCase useCase;
    private UUID userId;
    private UUID vendorId;
    private UUID serviceId;

    @BeforeEach
    void setUp() {
        useCase = new CreateServiceOptionUseCase(services, options, vendors);
        userId = UUID.randomUUID();
        vendorId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
    }

    @ParameterizedTest
    @EnumSource(OptionType.class)
    void validSharedAndPrivatePricingSavesEveryFieldAndMapsThePersistedResult(OptionType type) {
        authorize();
        var createdAt = OffsetDateTime.parse("2026-10-01T10:00:00+07:00");
        var updatedAt = createdAt.plusMinutes(1);
        when(options.save(any())).thenAnswer(invocation -> {
            ServiceOption option = invocation.getArgument(0);
            option.setCreatedAt(createdAt);
            option.setUpdatedAt(updatedAt);
            return option;
        });
        PricingUnit pricing = type == OptionType.PRIVATE ? PricingUnit.PER_PACKAGE : PricingUnit.PER_PERSON;
        Integer maxPax = type == OptionType.PRIVATE ? 6 : null;
        var request = new CreateServiceOptionRequest("  Diving lesson  ", type, pricing,
                new BigDecimal("750000.25"), maxPax, "  Equipment and guide included  ");

        var response = useCase.execute(userId, serviceId, request);

        var captor = ArgumentCaptor.forClass(ServiceOption.class);
        verify(options).save(captor.capture());
        ServiceOption saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getServiceId()).isEqualTo(serviceId);
        assertThat(saved.getName()).isEqualTo("Diving lesson");
        assertThat(saved.getBenefits()).isEqualTo("Equipment and guide included");
        assertThat(saved.getOptionType()).isEqualTo(type);
        assertThat(saved.getPricingUnit()).isEqualTo(pricing);
        assertThat(saved.getPrice()).isEqualByComparingTo(request.price());
        assertThat(saved.getMaxPaxPerPackage()).isEqualTo(maxPax);
        assertThat(saved.getStatus()).isEqualTo(OptionStatus.ACTIVE);
        assertThat(response.id()).isEqualTo(saved.getId());
        assertThat(response.serviceId()).isEqualTo(serviceId);
        assertThat(response.name()).isEqualTo("Diving lesson");
        assertThat(response.optionType()).isEqualTo(type);
        assertThat(response.pricingUnit()).isEqualTo(pricing);
        assertThat(response.price()).isEqualByComparingTo(request.price());
        assertThat(response.maxPaxPerPackage()).isEqualTo(maxPax);
        assertThat(response.benefits()).isEqualTo("Equipment and guide included");
        assertThat(response.status()).isEqualTo(OptionStatus.ACTIVE);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void optionalBenefitsCanRemainNullAndMinimumPrivatePackageSizeIsAccepted() {
        authorize();
        when(options.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new CreateServiceOptionRequest("One person private", OptionType.PRIVATE, PricingUnit.PER_PACKAGE,
                new BigDecimal("0.01"), 1, null);
        var response = useCase.execute(userId, serviceId, request);
        assertThat(response.benefits()).isNull();
        assertThat(response.maxPaxPerPackage()).isEqualTo(1);
        assertThat(response.price()).isEqualByComparingTo("0.01");
        assertThat(response.status()).isEqualTo(OptionStatus.ACTIVE);
        var captor = ArgumentCaptor.forClass(ServiceOption.class);
        verify(options).save(captor.capture());
        assertThat(captor.getValue().getBenefits()).isNull();
    }

    @Test
    void nullRequestIsRejectedBeforeAnyPortInteraction() {
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Request cannot be null");
        verifyNoInteractions(services, options, vendors);
    }

    @Test
    void missingVendorCannotCreateAnOption() {
        when(vendors.findByUserId(userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, validRequest()))
                .isInstanceOf(VendorNotApprovedException.class);
        verifyNoInteractions(services, options);
    }

    @Test
    void missingServiceCannotCreateAnOption() {
        vendor();
        when(services.findById(serviceId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, validRequest()))
                .isInstanceOf(ServiceNotFoundException.class);
        verifyNoInteractions(options);
    }

    @Test
    void anotherVendorsServiceIsRejectedBeforeAnyOptionIsSaved() {
        vendor();
        when(services.findById(serviceId))
                .thenReturn(Optional.of(Service.builder().id(serviceId).vendorId(UUID.randomUUID()).build()));
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, validRequest()))
                .isInstanceOf(UnauthorizedServiceAccessException.class);
        verifyNoInteractions(options);
    }

    @ParameterizedTest
    @MethodSource("invalidPricing")
    void typePricingAndPositiveAmountAreAllMandatory(OptionType type, PricingUnit pricing, BigDecimal price) {
        authorize();
        var request = new CreateServiceOptionRequest("Diving", type, pricing, price, null, null);
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A valid option type, pricing unit and positive price are required");
        verify(options, never()).save(any());
    }

    private static Stream<Arguments> invalidPricing() {
        return Stream.of(
                Arguments.of(null, PricingUnit.PER_PERSON, BigDecimal.ONE),
                Arguments.of(OptionType.SHARED, null, BigDecimal.ONE),
                Arguments.of(OptionType.SHARED, PricingUnit.PER_PERSON, null),
                Arguments.of(OptionType.SHARED, PricingUnit.PER_PERSON, BigDecimal.ZERO),
                Arguments.of(OptionType.SHARED, PricingUnit.PER_PERSON, new BigDecimal("-0.01")));
    }

    @ParameterizedTest
    @CsvSource({"SHARED,PER_PACKAGE", "PRIVATE,PER_PERSON"})
    void pricingUnitMustMatchTheOptionType(OptionType type, PricingUnit pricing) {
        authorize();
        var request = new CreateServiceOptionRequest("Diving", type, pricing, BigDecimal.ONE, 6, null);
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Shared options are priced per person; private options are priced per package");
        verify(options, never()).save(any());
    }

    @ParameterizedTest
    @MethodSource("invalidPackageSizes")
    void privatePackageMustHaveAPositivePassengerLimit(Integer maximum) {
        authorize();
        var request = new CreateServiceOptionRequest("Diving", OptionType.PRIVATE, PricingUnit.PER_PACKAGE,
                BigDecimal.ONE, maximum, null);
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PRIVATE package required configuration: maxPaxPerPackage > 0");
        verify(options, never()).save(any());
    }

    private static Stream<Arguments> invalidPackageSizes() {
        return Stream.of(Arguments.of((Object) null), Arguments.of(0), Arguments.of(-1));
    }

    private CreateServiceOptionRequest validRequest() {
        return new CreateServiceOptionRequest("Diving", OptionType.SHARED, PricingUnit.PER_PERSON, BigDecimal.ONE, null, null);
    }

    private void vendor() {
        Vendor vendor = new Vendor();
        vendor.setId(vendorId);
        when(vendors.findByUserId(userId)).thenReturn(Optional.of(vendor));
    }

    private void authorize() {
        vendor();
        when(services.findById(serviceId))
                .thenReturn(Optional.of(Service.builder().id(serviceId).vendorId(vendorId).build()));
    }
}
