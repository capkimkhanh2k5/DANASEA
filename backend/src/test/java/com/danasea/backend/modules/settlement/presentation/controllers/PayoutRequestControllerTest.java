package com.danasea.backend.modules.settlement.presentation.controllers;

import com.danasea.backend.modules.booking.domain.ports.VendorLookupPort;
import com.danasea.backend.modules.operation.domain.models.PayoutRequestStatus;
import com.danasea.backend.modules.settlement.application.dtos.ApprovePayoutRequest;
import com.danasea.backend.modules.settlement.application.dtos.CreatePayoutRequestRequest;
import com.danasea.backend.modules.settlement.application.dtos.EligibleSettlementPayoutResponse;
import com.danasea.backend.modules.settlement.application.dtos.MarkPaidPayoutRequest;
import com.danasea.backend.modules.settlement.application.dtos.PayoutRequestDetailResponse;
import com.danasea.backend.modules.settlement.application.dtos.PayoutRequestResponse;
import com.danasea.backend.modules.settlement.application.dtos.RejectPayoutRequest;
import com.danasea.backend.modules.settlement.application.usecases.ApprovePayoutRequestUseCase;
import com.danasea.backend.modules.settlement.application.usecases.CreatePayoutRequestUseCase;
import com.danasea.backend.modules.settlement.application.usecases.GetAdminPayoutRequestsUseCase;
import com.danasea.backend.modules.settlement.application.usecases.GetPayoutRequestDetailUseCase;
import com.danasea.backend.modules.settlement.application.usecases.GetVendorEligibleSettlementsUseCase;
import com.danasea.backend.modules.settlement.application.usecases.GetVendorPayoutRequestsUseCase;
import com.danasea.backend.modules.settlement.application.usecases.MarkPayoutPaidUseCase;
import com.danasea.backend.modules.settlement.application.usecases.RejectPayoutRequestUseCase;
import com.danasea.backend.modules.settlement.domain.exceptions.InvalidPayoutRequestStateException;
import com.danasea.backend.modules.settlement.domain.exceptions.PayoutAmountExceedsBalanceException;
import com.danasea.backend.modules.settlement.domain.exceptions.PayoutRequestNotFoundException;
import com.danasea.backend.modules.settlement.domain.exceptions.SettlementNotFinalizedException;
import com.danasea.backend.modules.settlement.presentation.handlers.SettlementExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("PayoutRequestControllerTest — Web Layer & Controller Integration Unit Tests")
class PayoutRequestControllerTest {

    private MockMvc mockMvcVendor;
    private MockMvc mockMvcAdmin;
    private ObjectMapper objectMapper;

    @Mock
    private CreatePayoutRequestUseCase createPayoutRequestUseCase;

    @Mock
    private GetVendorPayoutRequestsUseCase getVendorPayoutRequestsUseCase;

    @Mock
    private GetPayoutRequestDetailUseCase getPayoutRequestDetailUseCase;

    @Mock
    private GetVendorEligibleSettlementsUseCase getVendorEligibleSettlementsUseCase;

    @Mock
    private VendorLookupPort vendorLookupPort;

    @Mock
    private GetAdminPayoutRequestsUseCase getAdminPayoutRequestsUseCase;

    @Mock
    private ApprovePayoutRequestUseCase approvePayoutRequestUseCase;

    @Mock
    private RejectPayoutRequestUseCase rejectPayoutRequestUseCase;

    @Mock
    private MarkPayoutPaidUseCase markPayoutPaidUseCase;

    @InjectMocks
    private VendorPayoutRequestController vendorPayoutRequestController;

    @InjectMocks
    private AdminPayoutRequestController adminPayoutRequestController;

    private UUID userId;
    private UUID vendorId;
    private UUID settlementId;
    private UUID payoutRequestId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        mockMvcVendor = MockMvcBuilders.standaloneSetup(vendorPayoutRequestController)
                .setControllerAdvice(new SettlementExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        mockMvcAdmin = MockMvcBuilders.standaloneSetup(adminPayoutRequestController)
                .setControllerAdvice(new SettlementExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        userId = UUID.randomUUID();
        vendorId = UUID.randomUUID();
        settlementId = UUID.randomUUID();
        payoutRequestId = UUID.randomUUID();

        // Setup mock authentication
        var auth = new UsernamePasswordAuthenticationToken(
                userId.toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_VENDOR"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================================
    // VENDOR ENDPOINTS
    // =========================================================================

    @Test
    @DisplayName("Vendor: POST /api/vendor/payout-requests thành công trả về 201 Created")
    void testVendorCreatePayoutRequestSuccess() throws Exception {
        when(vendorLookupPort.findVendorIdByUserId(userId)).thenReturn(Optional.of(vendorId));

        CreatePayoutRequestRequest request = new CreatePayoutRequestRequest(
                settlementId,
                new BigDecimal("5000000.00"),
                "Rút đợt 1"
        );

        PayoutRequestResponse response = PayoutRequestResponse.builder()
                .id(payoutRequestId)
                .settlementId(settlementId)
                .vendorId(vendorId)
                .amount(new BigDecimal("5000000.00"))
                .status(PayoutRequestStatus.REQUESTED)
                .adminNote("Rút đợt 1")
                .createdAt(OffsetDateTime.now())
                .build();

        when(createPayoutRequestUseCase.execute(eq(vendorId), any(CreatePayoutRequestRequest.class)))
                .thenReturn(response);

        mockMvcVendor.perform(post("/api/vendor/payout-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(payoutRequestId.toString()))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.amount").value(5000000.00));
    }

    @Test
    @DisplayName("Vendor: POST /api/vendor/payout-requests trả về 400 khi số tiền vượt số dư")
    void testVendorCreatePayoutRequestExceedsBalance() throws Exception {
        when(vendorLookupPort.findVendorIdByUserId(userId)).thenReturn(Optional.of(vendorId));

        CreatePayoutRequestRequest request = new CreatePayoutRequestRequest(
                settlementId,
                new BigDecimal("99999999.00"),
                null
        );

        when(createPayoutRequestUseCase.execute(eq(vendorId), any(CreatePayoutRequestRequest.class)))
                .thenThrow(new PayoutAmountExceedsBalanceException("Số tiền yêu cầu vượt quá số dư"));

        mockMvcVendor.perform(post("/api/vendor/payout-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYOUT_AMOUNT_EXCEEDS_BALANCE"));
    }

    @Test
    @DisplayName("Vendor: POST /api/vendor/payout-requests trả về 400 khi Settlement chưa FINALIZED")
    void testVendorCreatePayoutRequestNotFinalized() throws Exception {
        when(vendorLookupPort.findVendorIdByUserId(userId)).thenReturn(Optional.of(vendorId));

        CreatePayoutRequestRequest request = new CreatePayoutRequestRequest(
                settlementId,
                new BigDecimal("1000000.00"),
                null
        );

        when(createPayoutRequestUseCase.execute(eq(vendorId), any(CreatePayoutRequestRequest.class)))
                .thenThrow(new SettlementNotFinalizedException("Kỳ đối soát chưa được chốt"));

        mockMvcVendor.perform(post("/api/vendor/payout-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FINALIZED"));
    }

    @Test
    @DisplayName("Vendor: GET /api/vendor/payout-requests trả về 200 OK với danh sách phân trang")
    void testVendorGetPayoutRequests() throws Exception {
        when(vendorLookupPort.findVendorIdByUserId(userId)).thenReturn(Optional.of(vendorId));

        PayoutRequestResponse item = PayoutRequestResponse.builder()
                .id(payoutRequestId)
                .settlementId(settlementId)
                .vendorId(vendorId)
                .amount(new BigDecimal("3000000.00"))
                .status(PayoutRequestStatus.REQUESTED)
                .createdAt(OffsetDateTime.now())
                .build();

        when(getVendorPayoutRequestsUseCase.execute(eq(vendorId), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

        mockMvcVendor.perform(get("/api/vendor/payout-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(payoutRequestId.toString()))
                .andExpect(jsonPath("$.content[0].status").value("REQUESTED"));
    }

    @Test
    @DisplayName("Vendor: GET /api/vendor/payout-requests/eligible-settlements trả về 200 OK")
    void testVendorGetEligibleSettlements() throws Exception {
        when(vendorLookupPort.findVendorIdByUserId(userId)).thenReturn(Optional.of(vendorId));

        EligibleSettlementPayoutResponse item = EligibleSettlementPayoutResponse.builder()
                .settlementId(settlementId)
                .periodStart(LocalDate.of(2026, 9, 1))
                .periodEnd(LocalDate.of(2026, 9, 30))
                .netPayableAmount(new BigDecimal("10000000.00"))
                .alreadyRequestedAmount(new BigDecimal("3000000.00"))
                .remainingEligibleBalance(new BigDecimal("7000000.00"))
                .build();

        when(getVendorEligibleSettlementsUseCase.execute(vendorId))
                .thenReturn(List.of(item));

        mockMvcVendor.perform(get("/api/vendor/payout-requests/eligible-settlements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].settlementId").value(settlementId.toString()))
                .andExpect(jsonPath("$[0].remainingEligibleBalance").value(7000000.00));
    }

    @Test
    @DisplayName("Vendor: GET /api/vendor/payout-requests/{id} trả về 404 khi không tìm thấy")
    void testVendorGetDetailNotFound() throws Exception {
        when(vendorLookupPort.findVendorIdByUserId(userId)).thenReturn(Optional.of(vendorId));
        when(getPayoutRequestDetailUseCase.execute(payoutRequestId, vendorId))
                .thenThrow(new PayoutRequestNotFoundException("Không tìm thấy yêu cầu chi trả"));

        mockMvcVendor.perform(get("/api/vendor/payout-requests/" + payoutRequestId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYOUT_REQUEST_NOT_FOUND"));
    }

    // =========================================================================
    // ADMIN ENDPOINTS
    // =========================================================================

    @Test
    @DisplayName("Admin: GET /api/admin/payout-requests trả về 200 OK")
    void testAdminGetPayoutRequests() throws Exception {
        PayoutRequestResponse item = PayoutRequestResponse.builder()
                .id(payoutRequestId)
                .settlementId(settlementId)
                .vendorId(vendorId)
                .amount(new BigDecimal("5000000.00"))
                .status(PayoutRequestStatus.REQUESTED)
                .createdAt(OffsetDateTime.now())
                .build();

        when(getAdminPayoutRequestsUseCase.execute(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

        mockMvcAdmin.perform(get("/api/admin/payout-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(payoutRequestId.toString()));
    }

    @Test
    @DisplayName("Admin: PATCH /api/admin/payout-requests/{id}/approve thành công trả về 200 OK")
    void testAdminApprovePayoutRequestSuccess() throws Exception {
        ApprovePayoutRequest request = new ApprovePayoutRequest("Hồ sơ hợp lệ");
        PayoutRequestResponse response = PayoutRequestResponse.builder()
                .id(payoutRequestId)
                .status(PayoutRequestStatus.APPROVED)
                .adminNote("Hồ sơ hợp lệ")
                .build();

        when(approvePayoutRequestUseCase.execute(eq(payoutRequestId), any(), any()))
                .thenReturn(response);

        mockMvcAdmin.perform(patch("/api/admin/payout-requests/" + payoutRequestId + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(payoutRequestId.toString()))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("Admin: PATCH /api/admin/payout-requests/{id}/reject trả về 400 khi trạng thái không hợp lệ")
    void testAdminRejectInvalidState() throws Exception {
        RejectPayoutRequest request = new RejectPayoutRequest("Lý do từ chối", "Ghi chú");

        when(rejectPayoutRequestUseCase.execute(eq(payoutRequestId), any(), any()))
                .thenThrow(new InvalidPayoutRequestStateException("Chỉ có thể từ chối yêu cầu ở trạng thái REQUESTED"));

        mockMvcAdmin.perform(patch("/api/admin/payout-requests/" + payoutRequestId + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAYOUT_REQUEST_STATE"));
    }

    @Test
    @DisplayName("Admin: PATCH /api/admin/payout-requests/{id}/mark-paid thành công trả về 200 OK")
    void testAdminMarkPaidSuccess() throws Exception {
        MarkPaidPayoutRequest request = new MarkPaidPayoutRequest("REF9999", "Đã chuyển khoản");
        PayoutRequestResponse response = PayoutRequestResponse.builder()
                .id(payoutRequestId)
                .status(PayoutRequestStatus.PAID)
                .bankReference("REF9999")
                .adminNote("Đã chuyển khoản")
                .build();

        when(markPayoutPaidUseCase.execute(eq(payoutRequestId), any(), any()))
                .thenReturn(response);

        mockMvcAdmin.perform(patch("/api/admin/payout-requests/" + payoutRequestId + "/mark-paid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(payoutRequestId.toString()))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.bankReference").value("REF9999"));
    }

    @Test
    @DisplayName("Admin: PATCH /api/admin/payout-requests/{id}/mark-paid thành công khi không có bankReference (nullable/optional)")
    void testAdminMarkPaidWithoutBankReferenceSuccess() throws Exception {
        MarkPaidPayoutRequest request = new MarkPaidPayoutRequest(null, "Chi trả nội bộ không dùng mã ngân hàng");
        PayoutRequestResponse response = PayoutRequestResponse.builder()
                .id(payoutRequestId)
                .status(PayoutRequestStatus.PAID)
                .bankReference(null)
                .adminNote("Chi trả nội bộ không dùng mã ngân hàng")
                .build();

        when(markPayoutPaidUseCase.execute(eq(payoutRequestId), any(), any()))
                .thenReturn(response);

        mockMvcAdmin.perform(patch("/api/admin/payout-requests/" + payoutRequestId + "/mark-paid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(payoutRequestId.toString()))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.bankReference").doesNotExist());
    }
}
