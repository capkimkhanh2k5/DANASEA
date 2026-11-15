package com.danasea.backend.modules.audit.infrastructure.adapters;

import com.danasea.backend.modules.audit.domain.models.AuditLog;
import com.danasea.backend.modules.audit.infrastructure.mappers.AuditLogMapper;
import com.danasea.backend.modules.audit.infrastructure.persistence.entities.AuditLogJpaEntity;
import com.danasea.backend.modules.audit.infrastructure.persistence.repositories.JpaAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogAdapterTest {

    @Mock
    private JpaAuditLogRepository jpaAuditLogRepository;

    @Mock
    private AuditLogMapper auditLogMapper;

    private AuditLogAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AuditLogAdapter(jpaAuditLogRepository, auditLogMapper);
    }

    @Test
    @DisplayName("When all filter params are null or blank, calls findAll for absolute backward compatibility")
    void getAuditLogs_AllBlank_CallsFindAll() {
        Pageable pageable = PageRequest.of(0, 20);
        AuditLogJpaEntity entity = new AuditLogJpaEntity();
        AuditLog domain = new AuditLog();
        Page<AuditLogJpaEntity> entityPage = new PageImpl<>(List.of(entity));

        when(jpaAuditLogRepository.findAll(pageable)).thenReturn(entityPage);
        when(auditLogMapper.toDomain(entity)).thenReturn(domain);

        Page<AuditLog> result = adapter.getAuditLogs("   ", "", null, pageable);

        assertThat(result.getContent()).containsExactly(domain);
        verify(jpaAuditLogRepository).findAll(pageable);
        verify(jpaAuditLogRepository, never()).findByFilters(any(), any(), any(), any());
    }

    @Test
    @DisplayName("When action is provided, trims and calls findByFilters")
    void getAuditLogs_ActionOnly_CallsFindByFilters() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<AuditLogJpaEntity> entityPage = new PageImpl<>(List.of());

        when(jpaAuditLogRepository.findByFilters(eq("USER_LOCKED"), eq(null), eq(null), eq(pageable)))
                .thenReturn(entityPage);

        Page<AuditLog> result = adapter.getAuditLogs("  USER_LOCKED  ", null, "   ", pageable);

        assertThat(result.getContent()).isEmpty();
        verify(jpaAuditLogRepository).findByFilters("USER_LOCKED", null, null, pageable);
        verify(jpaAuditLogRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("When search has wildcards (% and _ and !), properly escapes and wraps in %..%")
    void getAuditLogs_SearchWithWildcards_EscapedCorrectly() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<AuditLogJpaEntity> entityPage = new PageImpl<>(List.of());

        when(jpaAuditLogRepository.findByFilters(any(), any(), any(), eq(pageable)))
                .thenReturn(entityPage);

        adapter.getAuditLogs(null, null, "100%_done!now", pageable);

        ArgumentCaptor<String> patternCaptor = ArgumentCaptor.forClass(String.class);
        verify(jpaAuditLogRepository).findByFilters(eq(null), eq(null), patternCaptor.capture(), eq(pageable));

        // 100%_done!now -> %100!%!_done!!now%
        assertThat(patternCaptor.getValue()).isEqualTo("%100!%!_done!!now%");
    }

    @Test
    @DisplayName("When both action and entityType and search are provided, all passed cleanly")
    void getAuditLogs_AllFiltersProvided() {
        Pageable pageable = PageRequest.of(1, 10);
        Page<AuditLogJpaEntity> entityPage = new PageImpl<>(List.of());

        when(jpaAuditLogRepository.findByFilters(eq("RECONCILE_PAYMENT_PENDING"), eq("PAYMENT"), eq("%test%"), eq(pageable)))
                .thenReturn(entityPage);

        adapter.getAuditLogs(" RECONCILE_PAYMENT_PENDING ", " PAYMENT ", "test", pageable);

        verify(jpaAuditLogRepository).findByFilters("RECONCILE_PAYMENT_PENDING", "PAYMENT", "%test%", pageable);
    }
}
