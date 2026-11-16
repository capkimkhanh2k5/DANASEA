package com.danasea.backend.modules.admin.presentation;

import com.danasea.backend.modules.admin.application.usecases.GetAuditLogDetailUseCase;
import com.danasea.backend.modules.admin.application.usecases.GetAuditLogsUseCase;
import com.danasea.backend.modules.admin.presentation.dtos.AuditLogResponse;
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
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdminAuditLogControllerTest {

    private MockMvc mockMvc;

    @Mock
    private GetAuditLogsUseCase getAuditLogsUseCase;

    @Mock
    private GetAuditLogDetailUseCase getAuditLogDetailUseCase;

    private AdminAuditLogController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminAuditLogController(getAuditLogsUseCase, getAuditLogDetailUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/admin/audit-logs with default params delegates to use case with tie-breaker sort")
    void getAuditLogs_DefaultParams() throws Exception {
        AuditLogResponse item = AuditLogResponse.builder()
                .id(UUID.randomUUID())
                .action("USER_LOCKED")
                .entityType("USER")
                .timestamp(OffsetDateTime.now())
                .build();
        Page<AuditLogResponse> pageResult = new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1);

        when(getAuditLogsUseCase.execute(eq(null), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(pageResult);

        mockMvc.perform(get("/api/admin/audit-logs")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("USER_LOCKED"));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(getAuditLogsUseCase).execute(eq(null), eq(null), eq(null), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        Sort sort = pageable.getSort();
        assertThat(sort.getOrderFor("createdAt")).isNotNull();
        assertThat(sort.getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(sort.getOrderFor("id")).isNotNull();
        assertThat(sort.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("GET /api/admin/audit-logs with filters and pagination passes parameters correctly")
    void getAuditLogs_WithFilters() throws Exception {
        Page<AuditLogResponse> pageResult = new PageImpl<>(List.of(), PageRequest.of(2, 15), 0);

        when(getAuditLogsUseCase.execute(eq("RECONCILE_PAYMENT_PENDING"), eq("PAYMENT"), eq("keyword"), any(Pageable.class)))
                .thenReturn(pageResult);

        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("page", "2")
                        .param("size", "15")
                        .param("action", "RECONCILE_PAYMENT_PENDING")
                        .param("entityType", "PAYMENT")
                        .param("search", "keyword")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(getAuditLogsUseCase).execute(
                eq("RECONCILE_PAYMENT_PENDING"),
                eq("PAYMENT"),
                eq("keyword"),
                pageableCaptor.capture()
        );

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(15);
    }

    @Test
    @DisplayName("GET /api/admin/audit-logs/{id} returns detail")
    void getAuditLogDetail_Success() throws Exception {
        UUID id = UUID.randomUUID();
        AuditLogResponse detail = AuditLogResponse.builder()
                .id(id)
                .action("ORDER_CANCELLED")
                .entityType("ORDER")
                .metadata("{\"note\":\"cancelled by user\"}")
                .timestamp(OffsetDateTime.now())
                .build();

        when(getAuditLogDetailUseCase.execute(id)).thenReturn(detail);

        mockMvc.perform(get("/api/admin/audit-logs/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.action").value("ORDER_CANCELLED"));

        verify(getAuditLogDetailUseCase).execute(id);
    }
}
