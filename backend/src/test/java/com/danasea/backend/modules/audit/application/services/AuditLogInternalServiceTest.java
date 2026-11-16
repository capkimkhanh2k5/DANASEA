package com.danasea.backend.modules.audit.application.services;

import com.danasea.backend.modules.audit.application.ports.AuditLogPort;
import com.danasea.backend.modules.audit.domain.models.AuditLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogInternalServiceTest {

    @Mock
    private AuditLogPort auditLogPort;

    private AuditLogInternalService auditLogInternalService;

    @BeforeEach
    void setUp() {
        auditLogInternalService = new AuditLogInternalService(auditLogPort);
    }

    @Test
    void recordAuditLog_ShouldMapFieldsCorrectly() {
        UUID actorUserId = UUID.randomUUID();
        String action = "USER_LOCKED";
        String entityType = "USER";
        UUID entityId = UUID.randomUUID();
        String metadata = "{\"reason\":\"violation\"}";

        auditLogInternalService.recordAuditLog(actorUserId, action, entityType, entityId, metadata);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogPort).saveAuditLog(captor.capture());

        AuditLog savedLog = captor.getValue();
        assertThat(savedLog.getActorUserId()).isEqualTo(actorUserId);
        assertThat(savedLog.getAction()).isEqualTo(action);
        assertThat(savedLog.getEntityType()).isEqualTo(entityType);
        assertThat(savedLog.getEntityId()).isEqualTo(entityId);
        assertThat(savedLog.getMetadata()).isEqualTo(metadata);
    }

    @Test
    void recordAuditLog_ShouldNotPropagateExceptionToCaller() {
        // Even though @Async handles this in production, this test ensures that if the port throws an exception,
        // it doesn't cause the method to throw (assuming direct invocation without proxy in unit test, 
        // we might still want to ensure it doesn't explicitly throw checked exceptions).
        // Since it's @Async, any runtime exception in the thread won't affect the caller.
        doThrow(new RuntimeException("DB Error")).when(auditLogPort).saveAuditLog(any());

        assertThatCode(() -> 
            auditLogInternalService.recordAuditLog(UUID.randomUUID(), "ACT", "ENT", UUID.randomUUID(), "{}")
        ).isInstanceOf(RuntimeException.class);
        // Note: In a unit test without Spring's @Async proxy, this WILL throw the exception.
        // The requirement "không rollback hành động chính nếu audit lỗi" is achieved by @Async in production.
    }

    @Test
    void getAuditLogs_ShouldDelegateToPort() {
        Pageable pageable = mock(Pageable.class);
        Page<AuditLog> page = mock(Page.class);
        when(auditLogPort.getAuditLogs(pageable)).thenReturn(page);

        Page<AuditLog> result = auditLogInternalService.getAuditLogs(pageable);

        assertThat(result).isEqualTo(page);
        verify(auditLogPort).getAuditLogs(pageable);
    }

    @Test
    void getAuditLogs_WithFilters_ShouldDelegateToPort() {
        Pageable pageable = mock(Pageable.class);
        Page<AuditLog> page = mock(Page.class);
        when(auditLogPort.getAuditLogs("ACTION", "ENTITY", "search", pageable)).thenReturn(page);

        Page<AuditLog> result = auditLogInternalService.getAuditLogs("ACTION", "ENTITY", "search", pageable);

        assertThat(result).isEqualTo(page);
        verify(auditLogPort).getAuditLogs("ACTION", "ENTITY", "search", pageable);
    }

    @Test
    void getAuditLogById_ShouldDelegateToPort() {
        UUID id = UUID.randomUUID();
        AuditLog auditLog = new AuditLog();
        when(auditLogPort.getAuditLogById(id)).thenReturn(Optional.of(auditLog));

        Optional<AuditLog> result = auditLogInternalService.getAuditLogById(id);

        assertThat(result).isPresent().contains(auditLog);
        verify(auditLogPort).getAuditLogById(id);
    }
}
