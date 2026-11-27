package com.danasea.backend.modules.service.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.danasea.backend.modules.service.application.dtos.CreateServiceCommand;
import com.danasea.backend.modules.service.application.dtos.ServiceResult;
import com.danasea.backend.modules.service.domain.exceptions.CategoryInactiveException;
import com.danasea.backend.modules.service.domain.exceptions.CategoryNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.VendorNotApprovedException;
import com.danasea.backend.modules.service.domain.exceptions.WeatherRequirementsMissingException;
import com.danasea.backend.modules.service.domain.models.Category;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.domain.ports.CategoryRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceImageRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateServiceUseCase Tests")
class CreateServiceUseCaseTest {

    @Mock private ServiceRepositoryPort serviceRepository;
    @Mock private CategoryRepositoryPort categoryRepository;
    @Mock private ServiceImageRepositoryPort serviceImageRepository;
    @Mock private VendorPort vendorPort;

    private CreateServiceUseCase useCase;

    private final UUID userId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new CreateServiceUseCase(serviceRepository, categoryRepository, serviceImageRepository, vendorPort);
    }

    private Vendor approvedVendor() {
        Vendor vendor = new Vendor();
        vendor.setId(vendorId);
        vendor.setUserId(userId);
        vendor.setVerificationStatus(VerificationStatus.APPROVED);
        return vendor;
    }

    private Category activeCategory() {
        Category category = new Category();
        category.setId(categoryId);
        category.setName("Water Sports");
        category.setIsActive(true);
        return category;
    }

    private CreateServiceCommand basicCommand() {
        return new CreateServiceCommand(
                userId, categoryId, "Scuba Diving", "Scuba Diving EN",
                "Description", "Description EN", BigDecimal.valueOf(500_000),
                120, 10, "Da Nang Beach", "123 Beach Rd",
                BigDecimal.valueOf(16.0), BigDecimal.valueOf(108.0),
                null, false, null, null
        );
    }

    @Test
    @DisplayName("✅ Tạo dịch vụ thành công, mặc định status=DRAFT và viewCount=0")
    void create_success_defaultsDraftAndZeroViewCount() {
        // Arrange
        Vendor vendor = approvedVendor();
        Category category = activeCategory();

        Service savedService = Service.builder()
                .id(UUID.randomUUID())
                .vendorId(vendorId)
                .categoryId(categoryId)
                .name("Scuba Diving")
                .status(ServiceStatus.DRAFT)
                .viewCount(0)
                .build();

        when(vendorPort.findByUserId(userId)).thenReturn(Optional.of(vendor));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(serviceRepository.save(any(Service.class))).thenReturn(savedService);
        when(serviceImageRepository.findByServiceId(any())).thenReturn(List.of());

        // Act
        ServiceResult result = useCase.execute(basicCommand());

        // Assert
        assertThat(result.status()).isEqualTo(ServiceStatus.DRAFT);
        assertThat(result.viewCount()).isEqualTo(0);

        ArgumentCaptor<Service> captor = ArgumentCaptor.forClass(Service.class);
        verify(serviceRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ServiceStatus.DRAFT);
        assertThat(captor.getValue().getViewCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("❌ Vendor chưa APPROVED (PENDING) → VendorNotApprovedException")
    void create_vendorNotApproved_throwsException() {
        // Arrange
        Vendor pendingVendor = new Vendor();
        pendingVendor.setId(vendorId);
        pendingVendor.setVerificationStatus(VerificationStatus.PENDING);
        when(vendorPort.findByUserId(userId)).thenReturn(Optional.of(pendingVendor));

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(basicCommand()))
                .isInstanceOf(VendorNotApprovedException.class);

        verify(serviceRepository, never()).save(any());
    }

    @Test
    @DisplayName("❌ Vendor không tồn tại → VendorNotApprovedException")
    void create_vendorNotFound_throwsException() {
        when(vendorPort.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(basicCommand()))
                .isInstanceOf(VendorNotApprovedException.class);
    }

    @Test
    @DisplayName("❌ category_id không tồn tại → CategoryNotFoundException")
    void create_categoryNotFound_throwsException() {
        when(vendorPort.findByUserId(userId)).thenReturn(Optional.of(approvedVendor()));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(basicCommand()))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    @DisplayName("❌ Category không active → CategoryInactiveException")
    void create_categoryInactive_throwsException() {
        Category inactiveCategory = activeCategory();
        inactiveCategory.setIsActive(false);

        when(vendorPort.findByUserId(userId)).thenReturn(Optional.of(approvedVendor()));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(inactiveCategory));

        assertThatThrownBy(() -> useCase.execute(basicCommand()))
                .isInstanceOf(CategoryInactiveException.class);
    }

    @Test
    @DisplayName("❌ weatherSensitive=true nhưng thiếu minWindKmh/maxWaveM → WeatherRequirementsMissingException")
    void create_weatherSensitiveMissingFields_throwsException() {
        CreateServiceCommand cmd = new CreateServiceCommand(
                userId, categoryId, "Sea Tour", null, null, null,
                BigDecimal.valueOf(200_000), 60, 5, null, null,
                null, null, null, true, null, null  // weatherSensitive=true, but missing fields
        );

        when(vendorPort.findByUserId(userId)).thenReturn(Optional.of(approvedVendor()));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(activeCategory()));

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(WeatherRequirementsMissingException.class);
    }
}
