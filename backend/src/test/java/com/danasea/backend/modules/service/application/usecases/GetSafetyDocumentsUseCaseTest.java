package com.danasea.backend.modules.service.application.usecases;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.danasea.backend.modules.service.application.ports.FileStoragePort;
import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.models.DocStatus;
import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSafetyDocumentJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSafetyDocumentRepository;
import com.danasea.backend.modules.service.presentation.controllers.AdminSafetyDocumentController;
import com.danasea.backend.modules.service.presentation.controllers.VendorSafetyDocumentController;
import com.danasea.backend.modules.service.presentation.dtos.SafetyDocumentResponse;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSafetyDocumentsUseCaseTest {

    @Mock
    private JpaServiceRepository serviceRepository;
    @Mock
    private JpaServiceSafetyDocumentRepository documentRepository;
    @Mock
    private VendorPort vendorPort;
    @Mock
    private FileStoragePort fileStoragePort;
    private AdminSafetyDocumentController adminController;
    private VendorSafetyDocumentController vendorController;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        GetSafetyDocumentsUseCase useCase = new GetSafetyDocumentsUseCase(serviceRepository, documentRepository);
        adminController = new AdminSafetyDocumentController(useCase,
                mock(ApproveSafetyDocumentUseCase.class), fileStoragePort);
        vendorController = new VendorSafetyDocumentController(mock(UploadSafetyDocumentUseCase.class),
                useCase, serviceRepository, vendorPort, fileStoragePort);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminListMapsReviewedDocumentAndSignsItsStoredCanonicalUrl() {
        UUID serviceId = UUID.randomUUID();
        ServiceSafetyDocumentJpaEntity document = reviewedDocument(serviceId);
        when(serviceRepository.existsById(serviceId)).thenReturn(true);
        when(documentRepository.findByServiceId(serviceId)).thenReturn(List.of(document));
        when(fileStoragePort.generateDeliveryUrl(document.getFileUrl()))
                .thenReturn("https://delivery.example/signed-safety.pdf");

        ResponseEntity<List<SafetyDocumentResponse>> response = adminController.listSafetyDocuments(serviceId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsExactly(new SafetyDocumentResponse(document.getId(), serviceId,
                "https://delivery.example/signed-safety.pdf", DocStatus.REJECTED, document.getReviewedBy(),
                document.getReviewedAt(), "Certificate expired", document.getCreatedAt()));
        assertThat(document.getFileUrl()).isEqualTo("https://storage.example/safety.pdf");
        verify(serviceRepository).existsById(serviceId);
        verify(documentRepository).findByServiceId(serviceId);
        verify(fileStoragePort).generateDeliveryUrl("https://storage.example/safety.pdf");
        verifyNoMoreInteractions(serviceRepository, documentRepository, fileStoragePort);
    }

    @Test
    void existingServiceWithoutDocumentsReturnsAnEmptyListWithoutStorageAccess() {
        UUID serviceId = UUID.randomUUID();
        when(serviceRepository.existsById(serviceId)).thenReturn(true);
        when(documentRepository.findByServiceId(serviceId)).thenReturn(List.of());

        ResponseEntity<List<SafetyDocumentResponse>> response = adminController.listSafetyDocuments(serviceId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEmpty();
        verify(documentRepository).findByServiceId(serviceId);
        verifyNoInteractions(fileStoragePort);
    }

    @Test
    void missingServiceIsRejectedBeforeDocumentQueryOrUrlGeneration() {
        UUID serviceId = UUID.randomUUID();
        when(serviceRepository.existsById(serviceId)).thenReturn(false);

        assertThatThrownBy(() -> adminController.listSafetyDocuments(serviceId))
                .isInstanceOf(ServiceNotFoundException.class).hasMessage("Service not found: " + serviceId);

        verify(serviceRepository).existsById(serviceId);
        verifyNoInteractions(documentRepository, fileStoragePort);
    }

    @Test
    void vendorListBindsCurrentUserToOwningVendorAndReturnsOnlyRequestedServiceDocuments() {
        UUID userId = authenticate();
        UUID vendorId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        Vendor vendor = new Vendor();
        vendor.setId(vendorId);
        vendor.setUserId(userId);
        ServiceJpaEntity service = new ServiceJpaEntity();
        service.setId(serviceId);
        service.setVendorId(vendorId);
        ServiceSafetyDocumentJpaEntity document = reviewedDocument(serviceId);
        when(vendorPort.findByUserId(userId)).thenReturn(Optional.of(vendor));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(serviceRepository.existsById(serviceId)).thenReturn(true);
        when(documentRepository.findByServiceId(serviceId)).thenReturn(List.of(document));
        when(fileStoragePort.generateDeliveryUrl(document.getFileUrl())).thenReturn("https://delivery.example/vendor.pdf");

        ResponseEntity<List<SafetyDocumentResponse>> response = vendorController.listSafetyDocuments(serviceId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsExactly(new SafetyDocumentResponse(document.getId(), serviceId,
                "https://delivery.example/vendor.pdf", DocStatus.REJECTED, document.getReviewedBy(),
                document.getReviewedAt(), "Certificate expired", document.getCreatedAt()));
        verify(vendorPort).findByUserId(userId);
        verify(serviceRepository).findById(serviceId);
        verify(serviceRepository).existsById(serviceId);
        verify(documentRepository).findByServiceId(serviceId);
        verify(fileStoragePort).generateDeliveryUrl(document.getFileUrl());
        verifyNoMoreInteractions(vendorPort, serviceRepository, documentRepository, fileStoragePort);
    }

    @Test
    void anotherVendorsServiceIsDeniedBeforeAnyDocumentOrStorageAccess() {
        UUID userId = authenticate();
        UUID serviceId = UUID.randomUUID();
        Vendor vendor = new Vendor();
        vendor.setId(UUID.randomUUID());
        ServiceJpaEntity service = new ServiceJpaEntity();
        service.setId(serviceId);
        service.setVendorId(UUID.randomUUID());
        when(vendorPort.findByUserId(userId)).thenReturn(Optional.of(vendor));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));

        assertThatThrownBy(() -> vendorController.listSafetyDocuments(serviceId))
                .isInstanceOf(AccessDeniedException.class).hasMessage("Vendor does not own this service");

        verify(serviceRepository).findById(serviceId);
        verifyNoMoreInteractions(serviceRepository);
        verifyNoInteractions(documentRepository, fileStoragePort);
    }

    @Test
    void missingVendorIsDeniedBeforeLoadingServiceOrDocuments() {
        UUID userId = authenticate();
        when(vendorPort.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vendorController.listSafetyDocuments(UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class).hasMessage("Vendor profile not found");

        verify(vendorPort).findByUserId(userId);
        verifyNoInteractions(serviceRepository, documentRepository, fileStoragePort);
    }

    @Test
    void vendorRequestForMissingServiceIsRejectedBeforeLoadingDocuments() {
        UUID userId = authenticate();
        UUID serviceId = UUID.randomUUID();
        Vendor vendor = new Vendor();
        vendor.setId(UUID.randomUUID());
        when(vendorPort.findByUserId(userId)).thenReturn(Optional.of(vendor));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vendorController.listSafetyDocuments(serviceId))
                .isInstanceOf(ServiceNotFoundException.class).hasMessage("Service not found: " + serviceId);

        verify(serviceRepository).findById(serviceId);
        verifyNoMoreInteractions(serviceRepository);
        verifyNoInteractions(documentRepository, fileStoragePort);
    }

    @Test
    void unauthenticatedVendorRequestIsDeniedBeforeAnyDataAccess() {
        assertThatThrownBy(() -> vendorController.listSafetyDocuments(UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class).hasMessage("User ID not found");
        verifyNoInteractions(vendorPort, serviceRepository, documentRepository, fileStoragePort);
    }

    private UUID authenticate() {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                userId.toString(), "unused", List.of(new SimpleGrantedAuthority("ROLE_VENDOR"))));
        return userId;
    }

    private ServiceSafetyDocumentJpaEntity reviewedDocument(UUID serviceId) {
        ServiceSafetyDocumentJpaEntity document = new ServiceSafetyDocumentJpaEntity();
        document.setId(UUID.randomUUID());
        document.setServiceId(serviceId);
        document.setFileUrl("https://storage.example/safety.pdf");
        document.setStatus(DocStatus.REJECTED);
        document.setReviewedBy(UUID.randomUUID());
        document.setReviewedAt(OffsetDateTime.parse("2026-10-02T10:00:00+07:00"));
        document.setRejectionReason("Certificate expired");
        document.setCreatedAt(OffsetDateTime.parse("2026-10-01T10:00:00+07:00"));
        return document;
    }
}
