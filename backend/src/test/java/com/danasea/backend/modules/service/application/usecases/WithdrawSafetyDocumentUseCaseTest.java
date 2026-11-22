package com.danasea.backend.modules.service.application.usecases;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.danasea.backend.modules.service.domain.exceptions.InvalidDocumentStateException;
import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.UnauthorizedServiceAccessException;
import com.danasea.backend.modules.service.domain.models.DocStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSafetyDocumentJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSafetyDocumentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WithdrawSafetyDocumentUseCase Tests")
class WithdrawSafetyDocumentUseCaseTest {

    @Mock
    private JpaServiceRepository serviceRepository;

    @Mock
    private JpaServiceSafetyDocumentRepository safetyDocumentRepository;

    private WithdrawSafetyDocumentUseCase useCase;

    private final UUID serviceId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();

    private ServiceJpaEntity serviceEntity;
    private ServiceSafetyDocumentJpaEntity documentEntity;

    @BeforeEach
    void setUp() {
        useCase = new WithdrawSafetyDocumentUseCase(serviceRepository, safetyDocumentRepository);

        serviceEntity = new ServiceJpaEntity();
        serviceEntity.setId(serviceId);
        serviceEntity.setVendorId(vendorId);

        documentEntity = new ServiceSafetyDocumentJpaEntity();
        documentEntity.setId(documentId);
        documentEntity.setServiceId(serviceId);
        documentEntity.setStatus(DocStatus.PENDING);
    }

    @Test
    @DisplayName("✅ Thu hồi chứng chỉ PENDING → Thành công, chuyển sang WITHDRAWN")
    void withdraw_pending_success() {
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(safetyDocumentRepository.findByIdAndServiceId(documentId, serviceId)).thenReturn(Optional.of(documentEntity));
        when(safetyDocumentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = useCase.execute(serviceId, documentId, vendorId);

        assertThat(result.getStatus()).isEqualTo(DocStatus.WITHDRAWN);
        verify(safetyDocumentRepository).save(documentEntity);
    }

    @Test
    @DisplayName("✅ Thu hồi chứng chỉ REJECTED → Thành công, chuyển sang WITHDRAWN")
    void withdraw_rejected_success() {
        documentEntity.setStatus(DocStatus.REJECTED);
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(safetyDocumentRepository.findByIdAndServiceId(documentId, serviceId)).thenReturn(Optional.of(documentEntity));
        when(safetyDocumentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = useCase.execute(serviceId, documentId, vendorId);

        assertThat(result.getStatus()).isEqualTo(DocStatus.WITHDRAWN);
        verify(safetyDocumentRepository).save(documentEntity);
    }

    @Test
    @DisplayName("❌ Thu hồi chứng chỉ APPROVED → Chặn với InvalidDocumentStateException")
    void withdraw_approved_throws() {
        documentEntity.setStatus(DocStatus.APPROVED);
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(safetyDocumentRepository.findByIdAndServiceId(documentId, serviceId)).thenReturn(Optional.of(documentEntity));

        assertThatThrownBy(() -> useCase.execute(serviceId, documentId, vendorId))
                .isInstanceOf(InvalidDocumentStateException.class)
                .hasMessageContaining("APPROVED");

        verify(safetyDocumentRepository, never()).save(any());
    }

    @Test
    @DisplayName("❌ Vendor không sở hữu service → Chặn UnauthorizedServiceAccessException")
    void withdraw_unauthorized_throws() {
        UUID otherVendorId = UUID.randomUUID();
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceEntity));

        assertThatThrownBy(() -> useCase.execute(serviceId, documentId, otherVendorId))
                .isInstanceOf(UnauthorizedServiceAccessException.class);

        verify(safetyDocumentRepository, never()).save(any());
    }
}
