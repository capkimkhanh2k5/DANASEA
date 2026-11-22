package com.danasea.backend.modules.service.application.usecases;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.danasea.backend.modules.service.application.ports.FileStoragePort;
import com.danasea.backend.modules.service.domain.exceptions.InvalidDocumentStateException;
import com.danasea.backend.modules.service.domain.exceptions.UnauthorizedServiceAccessException;
import com.danasea.backend.modules.service.domain.models.DocStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSafetyDocumentJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSafetyDocumentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReplaceSafetyDocumentUseCase Tests")
class ReplaceSafetyDocumentUseCaseTest {

    @Mock
    private JpaServiceRepository serviceRepository;

    @Mock
    private JpaServiceSafetyDocumentRepository safetyDocumentRepository;

    @Mock
    private FileStoragePort fileStoragePort;

    private ReplaceSafetyDocumentUseCase useCase;

    private final UUID serviceId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();

    private ServiceJpaEntity serviceEntity;
    private ServiceSafetyDocumentJpaEntity documentEntity;
    private MockMultipartFile validPdfFile;

    @BeforeEach
    void setUp() {
        useCase = new ReplaceSafetyDocumentUseCase(serviceRepository, safetyDocumentRepository, fileStoragePort);

        serviceEntity = new ServiceJpaEntity();
        serviceEntity.setId(serviceId);
        serviceEntity.setVendorId(vendorId);

        documentEntity = new ServiceSafetyDocumentJpaEntity();
        documentEntity.setId(documentId);
        documentEntity.setServiceId(serviceId);
        documentEntity.setStatus(DocStatus.REJECTED);
        documentEntity.setRejectionReason("File is blurred");

        byte[] pdfBytes = "%PDF-1.4 test document".getBytes();
        validPdfFile = new MockMultipartFile("file", "cert.pdf", "application/pdf", pdfBytes);
    }

    @Test
    @DisplayName("✅ Thay thế chứng chỉ REJECTED → Bản cũ thành SUPERSEDED, bản mới tạo thành PENDING")
    void replace_rejected_success() {
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(safetyDocumentRepository.findByIdAndServiceId(documentId, serviceId)).thenReturn(Optional.of(documentEntity));
        when(fileStoragePort.uploadFile(any(), eq("cert.pdf"), any())).thenReturn("https://storage.example/new-cert.pdf");
        when(safetyDocumentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var replacement = useCase.execute(serviceId, documentId, vendorId, validPdfFile);

        assertThat(documentEntity.getStatus()).isEqualTo(DocStatus.SUPERSEDED);
        assertThat(replacement.getStatus()).isEqualTo(DocStatus.PENDING);
        assertThat(replacement.getFileUrl()).isEqualTo("https://storage.example/new-cert.pdf");
        assertThat(replacement.getServiceId()).isEqualTo(serviceId);

        verify(safetyDocumentRepository).save(documentEntity);
        verify(safetyDocumentRepository).save(replacement);
    }

    @Test
    @DisplayName("❌ Thay thế chứng chỉ APPROVED → Chặn với InvalidDocumentStateException")
    void replace_approved_throws() {
        documentEntity.setStatus(DocStatus.APPROVED);
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(safetyDocumentRepository.findByIdAndServiceId(documentId, serviceId)).thenReturn(Optional.of(documentEntity));

        assertThatThrownBy(() -> useCase.execute(serviceId, documentId, vendorId, validPdfFile))
                .isInstanceOf(InvalidDocumentStateException.class)
                .hasMessageContaining("APPROVED");

        verify(safetyDocumentRepository, never()).save(any());
    }

    @Test
    @DisplayName("❌ Vendor không sở hữu service → Chặn UnauthorizedServiceAccessException")
    void replace_unauthorized_throws() {
        UUID otherVendor = UUID.randomUUID();
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceEntity));

        assertThatThrownBy(() -> useCase.execute(serviceId, documentId, otherVendor, validPdfFile))
                .isInstanceOf(UnauthorizedServiceAccessException.class);

        verify(safetyDocumentRepository, never()).save(any());
    }
}
