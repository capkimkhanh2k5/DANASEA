package com.danasea.backend.modules.service.application.usecases;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.danasea.backend.modules.service.domain.models.DocStatus;
import com.danasea.backend.modules.service.domain.exceptions.SafetyDocumentRequiredException;
import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.CategoryJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSafetyDocumentJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaCategoryRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSafetyDocumentRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApproveSafetyDocumentUseCaseTest {

    @Mock
    private JpaServiceSafetyDocumentRepository safetyDocumentRepository;

    @Mock
    private JpaServiceRepository serviceRepository;

    @Mock
    private JpaCategoryRepository categoryRepository;

    private ApproveSafetyDocumentUseCase useCase;

    private UUID documentId;
    private UUID adminId;
    private UUID serviceId;
    private UUID categoryId;

    private ServiceSafetyDocumentJpaEntity documentEntity;
    private ServiceJpaEntity serviceEntity;
    private CategoryJpaEntity categoryEntity;

    @BeforeEach
    void setUp() {
        useCase = new ApproveSafetyDocumentUseCase(safetyDocumentRepository, serviceRepository, categoryRepository);

        documentId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        documentEntity = new ServiceSafetyDocumentJpaEntity();
        documentEntity.setId(documentId);
        documentEntity.setServiceId(serviceId);
        documentEntity.setFileUrl("https://res.cloudinary.com/danasea/raw/upload/v1/cert.pdf");
        documentEntity.setStatus(DocStatus.PENDING);

        categoryEntity = new CategoryJpaEntity();
        categoryEntity.setId(categoryId);
        categoryEntity.setName("Lặn biển mạo hiểm");
        categoryEntity.setRequiresSafetyCert(false);

        serviceEntity = new ServiceJpaEntity();
        serviceEntity.setId(serviceId);
        serviceEntity.setCategoryId(categoryId);
        serviceEntity.setName("Tour Lặn Biển");
        serviceEntity.setWeatherSensitive(false);
    }

    @Test
    @DisplayName("AC4.1: Admin phê duyệt chứng chỉ an toàn thành công với Audit Trail")
    void shouldApproveDocumentSuccessfullyWithAuditTrail() {
        when(safetyDocumentRepository.findByIdAndServiceId(documentId, serviceId))
                .thenReturn(Optional.of(documentEntity));
        when(safetyDocumentRepository.save(any(ServiceSafetyDocumentJpaEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServiceSafetyDocumentJpaEntity result = useCase.approve(serviceId, documentId, adminId);

        assertNotNull(result);
        assertEquals(DocStatus.APPROVED, result.getStatus());
        assertEquals(adminId, result.getReviewedBy());
        assertNotNull(result.getReviewedAt());

        verify(safetyDocumentRepository).findByIdAndServiceId(documentId, serviceId);
        verify(safetyDocumentRepository).save(documentEntity);
    }

    @Test
    @DisplayName("AC4.2: Admin từ chối chứng chỉ an toàn thành công kèm lý do và Audit Trail")
    void shouldRejectDocumentSuccessfullyWithReason() {
        String rejectionReason = "Giấy phép đã hết hạn hiệu lực từ ngày 01/01/2026";

        when(safetyDocumentRepository.findByIdAndServiceId(documentId, serviceId))
                .thenReturn(Optional.of(documentEntity));
        when(safetyDocumentRepository.save(any(ServiceSafetyDocumentJpaEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServiceSafetyDocumentJpaEntity result = useCase.reject(
                serviceId, documentId, adminId, rejectionReason);

        assertNotNull(result);
        assertEquals(DocStatus.REJECTED, result.getStatus());
        assertEquals(adminId, result.getReviewedBy());
        assertNotNull(result.getReviewedAt());
        assertEquals(rejectionReason, result.getRejectionReason());

        verify(safetyDocumentRepository).findByIdAndServiceId(documentId, serviceId);
        verify(safetyDocumentRepository).save(documentEntity);
    }

    @Test
    @DisplayName("AC4.3: Ném ngoại lệ khi duyệt tài liệu không tồn tại trong hệ thống")
    void shouldThrowWhenDocumentNotFoundOnApprove() {
        when(safetyDocumentRepository.findByIdAndServiceId(documentId, serviceId))
                .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () ->
                useCase.approve(serviceId, documentId, adminId)
        );

        verify(safetyDocumentRepository, never()).save(any());
    }

    @Test
    @DisplayName("AC4.4: Publish Guard — Dịch vụ thuộc Category rủi ro cao (requiresSafetyCert=true) CÓ chứng chỉ APPROVED -> Cho phép Publish")
    void shouldAllowPublishWhenCategoryRequiresCertAndDocumentApproved() {
        categoryEntity.setRequiresSafetyCert(true);

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(categoryRepository.findByIdForUpdate(categoryId)).thenReturn(Optional.of(categoryEntity));
        when(safetyDocumentRepository.existsByServiceIdAndStatus(serviceId, DocStatus.APPROVED)).thenReturn(true);

        boolean canPublish = useCase.canPublish(serviceId);

        assertTrue(canPublish);
        verify(safetyDocumentRepository).existsByServiceIdAndStatus(serviceId, DocStatus.APPROVED);
    }

    @Test
    @DisplayName("AC4.5: Publish Guard — Dịch vụ thuộc Category rủi ro cao (requiresSafetyCert=true) KHÔNG CÓ chứng chỉ APPROVED -> Chặn Publish và ném SafetyDocumentRequiredException")
    void shouldBlockPublishWhenCategoryRequiresCertAndNoApprovedDocument() {
        categoryEntity.setRequiresSafetyCert(true);

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(categoryRepository.findByIdForUpdate(categoryId)).thenReturn(Optional.of(categoryEntity));
        when(safetyDocumentRepository.existsByServiceIdAndStatus(serviceId, DocStatus.APPROVED)).thenReturn(false);

        assertThrows(SafetyDocumentRequiredException.class, () ->
                useCase.canPublish(serviceId)
        );
    }

    @Test
    @DisplayName("AC4.6: Publish Guard — Dịch vụ nhạy cảm thời tiết (weatherSensitive=true) KHÔNG CÓ chứng chỉ APPROVED -> Chặn Publish và ném SafetyDocumentRequiredException")
    void shouldBlockPublishWhenWeatherSensitiveAndNoApprovedDocument() {
        serviceEntity.setWeatherSensitive(true);
        categoryEntity.setRequiresSafetyCert(false);

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(categoryRepository.findByIdForUpdate(categoryId)).thenReturn(Optional.of(categoryEntity));
        when(safetyDocumentRepository.existsByServiceIdAndStatus(serviceId, DocStatus.APPROVED)).thenReturn(false);

        assertThrows(SafetyDocumentRequiredException.class, () ->
                useCase.canPublish(serviceId)
        );
    }

    @Test
    @DisplayName("AC4.7: Publish Guard — Dịch vụ nhạy cảm thời tiết CÓ chứng chỉ APPROVED -> Cho phép Publish")
    void shouldAllowPublishWhenWeatherSensitiveAndDocumentApproved() {
        serviceEntity.setWeatherSensitive(true);

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(categoryRepository.findByIdForUpdate(categoryId)).thenReturn(Optional.of(categoryEntity));
        when(safetyDocumentRepository.existsByServiceIdAndStatus(serviceId, DocStatus.APPROVED)).thenReturn(true);

        boolean canPublish = useCase.canPublish(serviceId);

        assertTrue(canPublish);
    }

    @Test
    @DisplayName("AC4.8: Publish Guard — Dịch vụ thông thường (weatherSensitive=false và category.requiresSafetyCert=false) -> Cho phép Publish không cần tài liệu")
    void shouldAllowPublishForNormalServiceWithoutSafetyDocument() {
        serviceEntity.setWeatherSensitive(false);
        categoryEntity.setRequiresSafetyCert(false);

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(categoryRepository.findByIdForUpdate(categoryId)).thenReturn(Optional.of(categoryEntity));

        boolean canPublish = useCase.canPublish(serviceId);

        assertTrue(canPublish);
        verify(safetyDocumentRepository, never()).existsByServiceIdAndStatus(any(), any());
    }

    @Test
    @DisplayName("AC4.9: Publish Guard — Chặn khi Service không tồn tại (HTTP 404)")
    void shouldThrowWhenServiceNotFoundOnPublishCheck() {
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.empty());

        assertThrows(ServiceNotFoundException.class, () ->
                useCase.canPublish(serviceId)
        );
    }
}
