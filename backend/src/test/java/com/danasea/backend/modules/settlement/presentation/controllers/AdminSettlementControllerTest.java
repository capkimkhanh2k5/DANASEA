package com.danasea.backend.modules.settlement.presentation.controllers;

import com.danasea.backend.modules.settlement.application.dtos.GenerateSettlementRequest;
import com.danasea.backend.modules.settlement.application.dtos.SettlementDetailResponse;
import com.danasea.backend.modules.settlement.application.dtos.SettlementResponse;
import com.danasea.backend.modules.settlement.application.usecases.FinalizeSettlementUseCase;
import com.danasea.backend.modules.settlement.application.usecases.GenerateSettlementUseCase;
import com.danasea.backend.modules.settlement.application.usecases.GetSettlementsUseCase;
import com.danasea.backend.modules.settlement.domain.models.SettlementStatus;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminSettlementController Unit Tests")
class AdminSettlementControllerTest {

    @Mock
    private GenerateSettlementUseCase generateSettlementUseCase;

    @Mock
    private GetSettlementsUseCase getSettlementsUseCase;

    @Mock
    private FinalizeSettlementUseCase finalizeSettlementUseCase;

    @InjectMocks
    private AdminSettlementController controller;

    private UUID vendorId;
    private UUID settlementId;
    private LocalDate start;
    private LocalDate end;

    @BeforeEach
    void setUp() {
        vendorId = UUID.randomUUID();
        settlementId = UUID.randomUUID();
        start = LocalDate.of(2026, 9, 1);
        end = LocalDate.of(2026, 9, 30);
    }

    @Test
    @DisplayName("generateSettlement calls usecase and returns 201 CREATED")
    void generateSettlement_Success() {
        GenerateSettlementRequest request = new GenerateSettlementRequest(vendorId, start, end);
        SettlementResponse expectedResponse = SettlementResponse.builder()
                .id(settlementId)
                .vendorId(vendorId)
                .periodStart(start)
                .periodEnd(end)
                .grossAmount(new BigDecimal("1000000.00"))
                .commissionAmount(new BigDecimal("100000.00"))
                .netPayableAmount(new BigDecimal("900000.00"))
                .status(SettlementStatus.DRAFT)
                .build();

        when(generateSettlementUseCase.execute(request)).thenReturn(expectedResponse);

        ResponseEntity<SettlementResponse> response = controller.generateSettlement(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(expectedResponse);
        verify(generateSettlementUseCase).execute(request);
    }

    @Test
    @DisplayName("getSettlements calls usecase and returns 200 OK")
    void getSettlements_Success() {
        Pageable pageable = PageRequest.of(0, 20);
        SettlementResponse settlementResponse = SettlementResponse.builder()
                .id(settlementId)
                .vendorId(vendorId)
                .periodStart(start)
                .periodEnd(end)
                .status(SettlementStatus.FINALIZED)
                .build();

        when(getSettlementsUseCase.executeForAdmin(vendorId, SettlementStatus.FINALIZED, start, end, pageable))
                .thenReturn(new PageImpl<>(List.of(settlementResponse)));

        ResponseEntity<Page<SettlementResponse>> response = controller.getSettlements(
                vendorId, SettlementStatus.FINALIZED, start, end, pageable
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(1);
    }

    @Test
    @DisplayName("getSettlementDetail returns 200 OK with line items")
    void getSettlementDetail_Success() {
        SettlementDetailResponse detailResponse = SettlementDetailResponse.builder()
                .id(settlementId)
                .vendorId(vendorId)
                .periodStart(start)
                .periodEnd(end)
                .status(SettlementStatus.FINALIZED)
                .lineItems(Collections.emptyList())
                .build();

        when(getSettlementsUseCase.getAdminSettlementById(settlementId)).thenReturn(detailResponse);

        ResponseEntity<SettlementDetailResponse> response = controller.getSettlementDetail(settlementId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(detailResponse);
    }

    @Test
    @DisplayName("finalizeSettlement calls usecase and returns 200 OK")
    void finalizeSettlement_Success() {
        SettlementResponse expectedResponse = SettlementResponse.builder()
                .id(settlementId)
                .vendorId(vendorId)
                .status(SettlementStatus.FINALIZED)
                .build();

        when(finalizeSettlementUseCase.execute(settlementId)).thenReturn(expectedResponse);

        ResponseEntity<SettlementResponse> response = controller.finalizeSettlement(settlementId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedResponse);
        verify(finalizeSettlementUseCase).execute(settlementId);
    }
}
