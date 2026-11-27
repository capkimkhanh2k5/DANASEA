package com.danasea.backend.modules.settlement.presentation.controllers;

import com.danasea.backend.modules.settlement.application.dtos.GenerateSettlementRequest;
import com.danasea.backend.modules.settlement.application.dtos.SettlementResponse;
import com.danasea.backend.modules.settlement.application.usecases.FinalizeSettlementUseCase;
import com.danasea.backend.modules.settlement.application.usecases.GenerateSettlementUseCase;
import com.danasea.backend.modules.settlement.application.usecases.GetSettlementDetailUseCase;
import com.danasea.backend.modules.settlement.application.usecases.GetSettlementsUseCase;
import com.danasea.backend.modules.settlement.domain.exceptions.InvalidSettlementPeriodException;
import com.danasea.backend.modules.settlement.domain.exceptions.SettlementAlreadyFinalizedException;
import com.danasea.backend.modules.settlement.domain.exceptions.SettlementNotFoundException;
import com.danasea.backend.modules.settlement.domain.models.SettlementStatus;
import com.danasea.backend.modules.settlement.presentation.handlers.SettlementExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementControllerTest — Web Layer & Exception Handling Tests")
class SettlementControllerTest {

    private MockMvc mockMvcAdmin;
    private MockMvc mockMvcVendor;
    private ObjectMapper objectMapper;

    @Mock
    private GenerateSettlementUseCase generateSettlementUseCase;

    @Mock
    private GetSettlementsUseCase getSettlementsUseCase;

    @Mock
    private GetSettlementDetailUseCase getSettlementDetailUseCase;

    @Mock
    private FinalizeSettlementUseCase finalizeSettlementUseCase;

    @InjectMocks
    private AdminSettlementController adminSettlementController;

    @InjectMocks
    private VendorSettlementController vendorSettlementController;

    private UUID vendorId;
    private UUID settlementId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        mockMvcAdmin = MockMvcBuilders.standaloneSetup(adminSettlementController)
                .setControllerAdvice(new SettlementExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        mockMvcVendor = MockMvcBuilders.standaloneSetup(vendorSettlementController)
                .setControllerAdvice(new SettlementExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        vendorId = UUID.randomUUID();
        settlementId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Admin: POST /api/admin/settlements/generate thành công trả về 201 Created")
    void testAdminGenerateSettlementSuccess() throws Exception {
        GenerateSettlementRequest request = new GenerateSettlementRequest(
                vendorId,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30)
        );

        SettlementResponse response = SettlementResponse.builder()
                .id(settlementId)
                .vendorId(vendorId)
                .periodStart(request.periodStart())
                .periodEnd(request.periodEnd())
                .grossAmount(new BigDecimal("1000000.00"))
                .commissionAmount(new BigDecimal("100000.00"))
                .netPayableAmount(new BigDecimal("900000.00"))
                .status(SettlementStatus.DRAFT)
                .build();

        when(generateSettlementUseCase.execute(any())).thenReturn(response);

        mockMvcAdmin.perform(post("/api/admin/settlements/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(settlementId.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.grossAmount").value(1000000.00));
    }

    @Test
    @DisplayName("Admin: POST generate với kỳ đã FINALIZED trả về 409 Conflict")
    void testAdminGenerateSettlementConflict() throws Exception {
        GenerateSettlementRequest request = new GenerateSettlementRequest(
                vendorId,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30)
        );

        when(generateSettlementUseCase.execute(any()))
                .thenThrow(new SettlementAlreadyFinalizedException("Settlement is already in FINALIZED status. Cannot regenerate."));

        mockMvcAdmin.perform(post("/api/admin/settlements/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_ALREADY_FINALIZED"));
    }

    @Test
    @DisplayName("Admin: POST generate với ngày bắt đầu sau ngày kết thúc trả về 400 Bad Request")
    void testAdminGenerateSettlementInvalidPeriod() throws Exception {
        GenerateSettlementRequest request = new GenerateSettlementRequest(
                vendorId,
                LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 9, 1)
        );

        when(generateSettlementUseCase.execute(any()))
                .thenThrow(new InvalidSettlementPeriodException("Start date must be before or equal to end date"));

        mockMvcAdmin.perform(post("/api/admin/settlements/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SETTLEMENT_PERIOD"));
    }

    @Test
    @DisplayName("Admin: PATCH /api/admin/settlements/{id}/finalize thành công trả về 200 OK")
    void testAdminFinalizeSettlementSuccess() throws Exception {
        SettlementResponse response = SettlementResponse.builder()
                .id(settlementId)
                .vendorId(vendorId)
                .status(SettlementStatus.FINALIZED)
                .build();

        when(finalizeSettlementUseCase.execute(settlementId)).thenReturn(response);

        mockMvcAdmin.perform(patch("/api/admin/settlements/{id}/finalize", settlementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"));
    }

    @Test
    @DisplayName("Admin: GET /api/admin/settlements/{id} không tìm thấy trả về 404 Not Found")
    void testAdminGetDetailNotFound() throws Exception {
        when(getSettlementsUseCase.getAdminSettlementById(settlementId))
                .thenThrow(new SettlementNotFoundException("Settlement not found: " + settlementId));

        mockMvcAdmin.perform(get("/api/admin/settlements/{id}", settlementId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("Vendor: GET /api/vendor/settlements/{id} của Vendor khác trả về 403 Forbidden")
    void testVendorCrossAccessForbidden() throws Exception {
        mockMvcVendor.perform(get("/api/vendor/settlements/{id}", settlementId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_SETTLEMENT_ACCESS"));
    }
}
