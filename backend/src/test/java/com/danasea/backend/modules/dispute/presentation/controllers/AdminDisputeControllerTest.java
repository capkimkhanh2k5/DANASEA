package com.danasea.backend.modules.dispute.presentation.controllers;

import com.danasea.backend.modules.dispute.application.usecases.GetAdminDisputeDetailUseCase;
import com.danasea.backend.modules.dispute.application.usecases.GetDisputesUseCase;
import com.danasea.backend.modules.dispute.application.usecases.ResolveDisputeUseCase;
import com.danasea.backend.modules.dispute.domain.exceptions.DisputeNotFoundException;
import com.danasea.backend.modules.dispute.domain.models.DisputeReason;
import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.dispute.presentation.dtos.DisputeResponse;
import com.danasea.backend.modules.dispute.presentation.dtos.ResolveDisputeRequest;
import com.danasea.backend.modules.dispute.presentation.handlers.DisputeExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminDisputeController Unit & MockMvc Tests")
class AdminDisputeControllerTest {

    @Mock
    private GetDisputesUseCase getDisputesUseCase;

    @Mock
    private ResolveDisputeUseCase resolveDisputeUseCase;

    @Mock
    private GetAdminDisputeDetailUseCase getAdminDisputeDetailUseCase;

    @InjectMocks
    private AdminDisputeController adminDisputeController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID disputeId;

    @BeforeEach
    void setUp() {
        disputeId = UUID.randomUUID();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        DisputeExceptionHandler exceptionHandler = new DisputeExceptionHandler();
        mockMvc = MockMvcBuilders.standaloneSetup(adminDisputeController)
                .setControllerAdvice(exceptionHandler)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("getDisputes delegates to usecase and returns 200 OK")
    void getDisputes_Success200() {
        Pageable pageable = PageRequest.of(0, 10);
        DisputeResponse dispute = new DisputeResponse(
                disputeId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                DisputeReason.SAFETY_CONCERN, "Safety issue", List.of(), DisputeStatus.OPEN,
                null, null, null, null, OffsetDateTime.now(), OffsetDateTime.now()
        );
        Page<DisputeResponse> page = new PageImpl<>(List.of(dispute), pageable, 1);

        when(getDisputesUseCase.execute(DisputeStatus.OPEN, DisputeReason.SAFETY_CONCERN, pageable))
                .thenReturn(page);

        ResponseEntity<Page<DisputeResponse>> response = adminDisputeController.getDisputes(
                DisputeStatus.OPEN, DisputeReason.SAFETY_CONCERN, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(page);
        verify(getDisputesUseCase).execute(DisputeStatus.OPEN, DisputeReason.SAFETY_CONCERN, pageable);
    }

    @Test
    @DisplayName("resolveDispute delegates to usecase and returns 200 OK")
    void resolveDispute_Success200() {
        ResolveDisputeRequest request = new ResolveDisputeRequest(
                DisputeStatus.RESOLVED_REFUND, new BigDecimal("100.0"), "Full refund approved");

        DisputeResponse resolvedResponse = new DisputeResponse(
                disputeId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                DisputeReason.SERVICE_NOT_AS_DESCRIBED, "Description", List.of(),
                DisputeStatus.RESOLVED_REFUND, new BigDecimal("100.0"), "Full refund approved",
                UUID.randomUUID(), OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now()
        );

        when(resolveDisputeUseCase.execute(disputeId, request)).thenReturn(resolvedResponse);

        ResponseEntity<DisputeResponse> response = adminDisputeController.resolveDispute(disputeId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(resolvedResponse);
        verify(resolveDisputeUseCase).execute(disputeId, request);
    }

    @Test
    @DisplayName("GET /api/admin/disputes/{id} returns 200 OK with comprehensive dossier")
    void getDisputeDetail_Success200() throws Exception {
        DisputeResponse dispute = new DisputeResponse(
                disputeId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                DisputeReason.SAFETY_CONCERN, "Customer initial claim",
                List.of("http://example.com/c1.jpg"), DisputeStatus.UNDER_REVIEW,
                null, null, null, null,
                OffsetDateTime.now(), OffsetDateTime.now(),
                "Vendor counter-explanation", List.of("http://example.com/v1.pdf"), OffsetDateTime.now()
        );

        when(getAdminDisputeDetailUseCase.execute(disputeId)).thenReturn(dispute);

        mockMvc.perform(get("/api/admin/disputes/{id}", disputeId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(disputeId.toString()))
                .andExpect(jsonPath("$.reason").value("SAFETY_CONCERN"))
                .andExpect(jsonPath("$.description").value("Customer initial claim"))
                .andExpect(jsonPath("$.vendorResponse").value("Vendor counter-explanation"))
                .andExpect(jsonPath("$.evidenceUrls[0]").value("http://example.com/c1.jpg"))
                .andExpect(jsonPath("$.vendorEvidenceUrls[0]").value("http://example.com/v1.pdf"));

        verify(getAdminDisputeDetailUseCase).execute(disputeId);
    }

    @Test
    @DisplayName("GET /api/admin/disputes/{id} with non-existent id returns 404 Not Found")
    void getDisputeDetail_NotFound_Returns404() throws Exception {
        when(getAdminDisputeDetailUseCase.execute(disputeId))
                .thenThrow(new DisputeNotFoundException("Dispute not found with id: " + disputeId));

        mockMvc.perform(get("/api/admin/disputes/{id}", disputeId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DISPUTE_NOT_FOUND"));

        verify(getAdminDisputeDetailUseCase).execute(disputeId);
    }
}
