package com.danasea.backend.modules.service.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.danasea.backend.modules.service.application.dtos.ServiceResult;
import com.danasea.backend.modules.service.domain.exceptions.InvalidServiceStateException;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.domain.ports.AuditLogPort;
import com.danasea.backend.modules.service.domain.ports.ServiceImageRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApproveServiceUseCase Tests")
class ApproveServiceUseCaseTest {

    @Mock
    private ServiceRepositoryPort serviceRepository;
    @Mock
    private ServiceImageRepositoryPort serviceImageRepository;
    @Mock
    private AuditLogPort auditLogPort;
    @Mock
    private ApproveSafetyDocumentUseCase approveSafetyDocumentUseCase;

    private ApproveServiceUseCase useCase;

    private final UUID adminUserId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new ApproveServiceUseCase(serviceRepository, serviceImageRepository, auditLogPort,
                approveSafetyDocumentUseCase);
    }

    private Service serviceWithStatus(ServiceStatus status) {
        return Service.builder().id(serviceId).vendorId(UUID.randomUUID()).status(status).build();
    }

    @Test
    @DisplayName("✅ Approve từ PENDING_REVIEW → PUBLISHED và ghi audit log SERVICE_APPROVED")
    void approve_pendingReview_publishedAndAuditLogged() {
        Service pending = serviceWithStatus(ServiceStatus.PENDING_REVIEW);
        Service saved = serviceWithStatus(ServiceStatus.PUBLISHED);

        when(serviceRepository.findByIdForUpdate(serviceId)).thenReturn(Optional.of(pending));
        when(serviceRepository.save(any())).thenReturn(saved);
        when(serviceImageRepository.findByServiceId(any())).thenReturn(List.of());

        ServiceResult result = useCase.execute(adminUserId, serviceId);

        assertThat(result.status()).isEqualTo(ServiceStatus.PUBLISHED);
        verify(auditLogPort).recordAuditLog(
                eq(adminUserId), eq("SERVICE_APPROVED"), eq("SERVICE"), eq(serviceId), any());
    }

    @Test
    @DisplayName("❌ Approve dịch vụ đang PUBLISHED → InvalidServiceStateException (chặn bấm nhầm 2 lần)")
    void approve_alreadyPublished_throwsException() {
        Service published = serviceWithStatus(ServiceStatus.PUBLISHED);

        when(serviceRepository.findByIdForUpdate(serviceId)).thenReturn(Optional.of(published));

        assertThatThrownBy(() -> useCase.execute(adminUserId, serviceId))
                .isInstanceOf(InvalidServiceStateException.class);

        verify(auditLogPort, never()).recordAuditLog(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("❌ Approve dịch vụ đang DRAFT → InvalidServiceStateException")
    void approve_draftStatus_throwsException() {
        Service draft = serviceWithStatus(ServiceStatus.DRAFT);
        when(serviceRepository.findByIdForUpdate(serviceId)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> useCase.execute(adminUserId, serviceId))
                .isInstanceOf(InvalidServiceStateException.class);
    }
}
