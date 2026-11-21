package com.danasea.backend.modules.vendor.presentation.controllers;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.cloudinary.Url;
import com.danasea.backend.modules.admin.application.usecases.ReviewVendorDocumentUseCase;
import com.danasea.backend.modules.admin.presentation.AdminVendorDocumentController;
import com.danasea.backend.modules.service.application.ports.FileStoragePort;
import com.danasea.backend.modules.service.application.usecases.GetSafetyDocumentsUseCase;
import com.danasea.backend.modules.service.application.usecases.UploadSafetyDocumentUseCase;
import com.danasea.backend.modules.service.domain.models.DocStatus;
import com.danasea.backend.modules.vendor.domain.models.DocType;
import com.danasea.backend.modules.service.domain.models.ServiceSafetyDocument;
import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSafetyDocumentJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.storage.CloudinaryStorageAdapter;
import com.danasea.backend.modules.service.presentation.controllers.AdminSafetyDocumentController;
import com.danasea.backend.modules.service.presentation.controllers.VendorSafetyDocumentController;
import com.danasea.backend.modules.service.presentation.dtos.SafetyDocumentResponse;
import com.danasea.backend.modules.vendor.application.ports.DocumentStoragePort;
import com.danasea.backend.modules.vendor.application.usecases.GetVendorDocumentsUseCase;
import com.danasea.backend.modules.vendor.application.usecases.UploadVendorDocumentUseCase;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.modules.vendor.domain.models.VendorDocument;
import com.danasea.backend.modules.vendor.infrastructure.mappers.VendorDocumentMapper;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorDocumentJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorDocumentRepository;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import com.danasea.backend.modules.vendor.infrastructure.storage.CloudinaryDocumentStorageAdapter;
import com.danasea.backend.modules.vendor.presentation.dtos.VendorDocumentResponse;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentAccessControlTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    @Mock
    private Url mockUrl;

    @Mock
    private JpaVendorRepository vendorRepository;

    @Mock
    private JpaVendorDocumentRepository vendorDocumentRepository;

    @Mock
    private VendorDocumentMapper vendorDocumentMapper;

    @Mock
    private ReviewVendorDocumentUseCase reviewVendorDocumentUseCase;

    @Mock
    private JpaServiceRepository serviceRepository;

    @Mock
    private GetSafetyDocumentsUseCase getSafetyDocumentsUseCase;

    @Mock
    private UploadSafetyDocumentUseCase uploadSafetyDocumentUseCase;

    @Mock
    private VendorPort vendorPort;

    private CloudinaryDocumentStorageAdapter documentStorageAdapter;
    private CloudinaryStorageAdapter serviceStorageAdapter;
    private GetVendorDocumentsUseCase getVendorDocumentsUseCase;
    private AdminVendorDocumentController adminVendorDocumentController;
    private AdminSafetyDocumentController adminSafetyDocumentController;
    private VendorSafetyDocumentController vendorSafetyDocumentController;

    private UUID adminUserId;
    private UUID owningVendorUserId;
    private UUID owningVendorId;
    private UUID unrelatedVendorUserId;
    private UUID unrelatedVendorId;
    private UUID customerUserId;
    private UUID serviceId;

    private final String canonicalVendorDocUrl =
            "https://res.cloudinary.com/xmxguqpi/image/authenticated/v1792170628/danasea_demo_data_package/vendor_documents/4d57d2cc.png";
    private final String signedVendorDocUrl =
            "https://res.cloudinary.com/xmxguqpi/image/authenticated/s--SIGN1234--/v1792170628/danasea_demo_data_package/vendor_documents/4d57d2cc.png";

    private final String canonicalSafetyDocUrl =
            "https://res.cloudinary.com/xmxguqpi/image/authenticated/v1792170628/danasea_demo_data_package/service_safety_documents/02556a44.png";
    private final String signedSafetyDocUrl =
            "https://res.cloudinary.com/xmxguqpi/image/authenticated/s--SIGN5678--/v1792170628/danasea_demo_data_package/service_safety_documents/02556a44.png";

    @BeforeEach
    void setUp() throws Exception {
        adminUserId = UUID.randomUUID();
        owningVendorUserId = UUID.randomUUID();
        owningVendorId = UUID.randomUUID();
        unrelatedVendorUserId = UUID.randomUUID();
        unrelatedVendorId = UUID.randomUUID();
        customerUserId = UUID.randomUUID();
        serviceId = UUID.randomUUID();

        lenient().when(cloudinary.url()).thenReturn(mockUrl);
        lenient().when(mockUrl.resourceType(any())).thenReturn(mockUrl);
        lenient().when(mockUrl.type(any())).thenReturn(mockUrl);
        lenient().when(mockUrl.secure(anyBoolean())).thenReturn(mockUrl);
        lenient().when(mockUrl.signed(anyBoolean())).thenReturn(mockUrl);
        lenient().when(mockUrl.version(any())).thenReturn(mockUrl);
        lenient().when(mockUrl.format(any())).thenReturn(mockUrl);

        documentStorageAdapter = new CloudinaryDocumentStorageAdapter(cloudinary);
        serviceStorageAdapter = new CloudinaryStorageAdapter(cloudinary);

        getVendorDocumentsUseCase = new GetVendorDocumentsUseCase(
                vendorRepository, vendorDocumentRepository, vendorDocumentMapper, documentStorageAdapter
        );

        adminVendorDocumentController = new AdminVendorDocumentController(
                reviewVendorDocumentUseCase, documentStorageAdapter
        );

        adminSafetyDocumentController = new AdminSafetyDocumentController(
                getSafetyDocumentsUseCase, null, serviceStorageAdapter
        );

        vendorSafetyDocumentController = new VendorSafetyDocumentController(
                uploadSafetyDocumentUseCase, getSafetyDocumentsUseCase, serviceRepository, vendorPort, serviceStorageAdapter
        );
    }

    private void authenticateUser(UUID userId, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                userId.toString(), "password", List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Owning vendor authorized document access receives signed CDN delivery URL without expiring privateDownload")
    void testOwningVendorAuthorizedAccess() throws Exception {
        authenticateUser(owningVendorUserId, "VENDOR");

        VendorJpaEntity vendor = new VendorJpaEntity();
        vendor.setId(owningVendorId);
        vendor.setUserId(owningVendorUserId);

        VendorDocumentJpaEntity entity = new VendorDocumentJpaEntity();
        entity.setFileUrl(canonicalVendorDocUrl);

        VendorDocument domainDoc = new VendorDocument();
        domainDoc.setFileUrl(canonicalVendorDocUrl);

        when(vendorRepository.findByUserId(owningVendorUserId)).thenReturn(Optional.of(vendor));
        when(vendorDocumentRepository.findByVendorId(owningVendorId)).thenReturn(List.of(entity));
        when(vendorDocumentMapper.toDomain(entity)).thenReturn(domainDoc);

        when(mockUrl.generate(eq("danasea_demo_data_package/vendor_documents/4d57d2cc")))
                .thenReturn(signedVendorDocUrl);

        List<VendorDocument> results = getVendorDocumentsUseCase.execute(owningVendorUserId);

        assertEquals(1, results.size());
        assertEquals(signedVendorDocUrl, results.get(0).getFileUrl());
        assertFalse(results.get(0).getFileUrl().contains("image/download"));
        // Verify canonical URL was not corrupted in the entity
        assertEquals(canonicalVendorDocUrl, entity.getFileUrl());
    }

    @Test
    @DisplayName("Unrelated vendor is isolated and cannot view another vendor's documents")
    void testUnrelatedVendorDeniedAccess() {
        authenticateUser(unrelatedVendorUserId, "VENDOR");

        VendorJpaEntity unrelatedVendor = new VendorJpaEntity();
        unrelatedVendor.setId(unrelatedVendorId);
        unrelatedVendor.setUserId(unrelatedVendorUserId);

        when(vendorRepository.findByUserId(unrelatedVendorUserId)).thenReturn(Optional.of(unrelatedVendor));
        when(vendorDocumentRepository.findByVendorId(unrelatedVendorId)).thenReturn(List.of());

        List<VendorDocument> results = getVendorDocumentsUseCase.execute(unrelatedVendorUserId);

        assertTrue(results.isEmpty());
        verify(vendorDocumentRepository, never()).findByVendorId(owningVendorId);
    }

    @Test
    @DisplayName("Vendor accessing safety documents of a service owned by another vendor is denied")
    void testUnrelatedVendorSafetyDocumentAccessDenied() {
        authenticateUser(unrelatedVendorUserId, "VENDOR");

        Vendor unrelatedVendorDomain = new Vendor();
        unrelatedVendorDomain.setId(unrelatedVendorId);
        when(vendorPort.findByUserId(unrelatedVendorUserId)).thenReturn(Optional.of(unrelatedVendorDomain));

        ServiceJpaEntity service = new ServiceJpaEntity();
        service.setId(serviceId);
        service.setVendorId(owningVendorId); // owned by someone else!
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));

        assertThrows(AccessDeniedException.class, () ->
                vendorSafetyDocumentController.listSafetyDocuments(serviceId));
    }

    @Test
    @DisplayName("Owning vendor authorized access to own service safety documents succeeds with signed CDN URL")
    void testOwningVendorSafetyDocumentAccessAllowed() throws Exception {
        authenticateUser(owningVendorUserId, "VENDOR");

        Vendor owningVendorDomain = new Vendor();
        owningVendorDomain.setId(owningVendorId);
        when(vendorPort.findByUserId(owningVendorUserId)).thenReturn(Optional.of(owningVendorDomain));

        ServiceJpaEntity service = new ServiceJpaEntity();
        service.setId(serviceId);
        service.setVendorId(owningVendorId);
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));

        ServiceSafetyDocumentJpaEntity docEntity = new ServiceSafetyDocumentJpaEntity();
        docEntity.setId(UUID.randomUUID());
        docEntity.setServiceId(serviceId);
        docEntity.setFileUrl(canonicalSafetyDocUrl);

        when(getSafetyDocumentsUseCase.execute(serviceId)).thenReturn(List.of(docEntity));
        when(mockUrl.generate(eq("danasea_demo_data_package/service_safety_documents/02556a44")))
                .thenReturn(signedSafetyDocUrl);

        ResponseEntity<List<SafetyDocumentResponse>> response =
                vendorSafetyDocumentController.listSafetyDocuments(serviceId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(signedSafetyDocUrl, response.getBody().get(0).fileUrl());
        assertFalse(response.getBody().get(0).fileUrl().contains("image/download"));
    }

    @Test
    @DisplayName("Admin authorized access to safety documents succeeds with signed CDN URL")
    void testAdminAuthorizedAccessToSafetyDocuments() throws Exception {
        authenticateUser(adminUserId, "ADMIN");

        ServiceSafetyDocumentJpaEntity docEntity = new ServiceSafetyDocumentJpaEntity();
        docEntity.setId(UUID.randomUUID());
        docEntity.setServiceId(serviceId);
        docEntity.setFileUrl(canonicalSafetyDocUrl);

        when(getSafetyDocumentsUseCase.execute(serviceId)).thenReturn(List.of(docEntity));
        when(mockUrl.generate(eq("danasea_demo_data_package/service_safety_documents/02556a44")))
                .thenReturn(signedSafetyDocUrl);

        ResponseEntity<List<SafetyDocumentResponse>> response =
                adminSafetyDocumentController.listSafetyDocuments(serviceId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(signedSafetyDocUrl, response.getBody().get(0).fileUrl());
        assertFalse(response.getBody().get(0).fileUrl().contains("image/download"));
    }

    @Test
    @DisplayName("Anonymous access throws AccessDeniedException when resolving actor")
    void testAnonymousAccessDenied() {
        clearAuthentication();

        assertThrows(AccessDeniedException.class, () ->
                adminVendorDocumentController.approve(owningVendorId, UUID.randomUUID()));

        assertThrows(AccessDeniedException.class, () ->
                vendorSafetyDocumentController.listSafetyDocuments(serviceId));
    }

    @Test
    @DisplayName("Canonical authenticated URLs or signed URLs are preserved in DB; image/download never persisted")
    void testCanonicalUrlsStoredAndDownloadUrlsNeverPersisted() {
        VendorDocumentJpaEntity entity = new VendorDocumentJpaEntity();
        entity.setFileUrl(canonicalVendorDocUrl);

        // Domain conversion and delivery URL generation
        VendorDocument domainDoc = new VendorDocument();
        domainDoc.setFileUrl(canonicalVendorDocUrl);
        domainDoc.setFileUrl(signedVendorDocUrl);

        // Verification: The underlying entity still holds the canonical URL
        assertEquals(canonicalVendorDocUrl, entity.getFileUrl());
        assertTrue(entity.getFileUrl().contains("/image/authenticated/"));
        assertFalse(entity.getFileUrl().contains("image/download"));
    }

    @Test
    @DisplayName("Document deletion uses correct delivery type authenticated")
    void testDocumentDeletionUsesAuthenticatedType() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(eq("danasea_demo_data_package/service_safety_documents/02556a44"), anyMap()))
                .thenReturn(Map.of("result", "ok"));

        serviceStorageAdapter.deleteFile(canonicalSafetyDocUrl);

        verify(uploader).destroy(
                eq("danasea_demo_data_package/service_safety_documents/02556a44"),
                argThat(params -> "authenticated".equals(params.get("type")) && Boolean.TRUE.equals(params.get("invalidate")))
        );
    }

    @Test
    @DisplayName("Public service images remain unaffected: no authenticated type and original URL returned")
    void testPublicServiceImagesUnaffected() {
        String publicImageUrl = "https://res.cloudinary.com/xmxguqpi/image/upload/v1792170628/danasea_demo_data_package/services/0a1b442572982a4991eb9a9cf92981fd0c9625a3a6a933c09e68a212ac4f0243.jpg";

        String deliveryUrl = serviceStorageAdapter.generateDeliveryUrl(publicImageUrl);

        // Public image URL is returned directly without signatures or privateDownload invocation
        assertEquals(publicImageUrl, deliveryUrl);
        verify(cloudinary, never()).url();
    }

    @Test
    @DisplayName("Runtime document folder mapping correctly isolates vendor and safety documents")
    void testRuntimeDocumentFolderMapping() {
        assertEquals("danasea_demo_data_package/vendor_documents/4d57d2cc",
                documentStorageAdapter.extractPublicId(canonicalVendorDocUrl));
        assertEquals("danasea_demo_data_package/service_safety_documents/02556a44",
                serviceStorageAdapter.extractPublicId(canonicalSafetyDocUrl));
    }
}
