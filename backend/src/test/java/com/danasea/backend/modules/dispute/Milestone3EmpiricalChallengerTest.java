package com.danasea.backend.modules.dispute;

import com.danasea.backend.modules.dispute.application.usecases.*;
import com.danasea.backend.modules.dispute.domain.exceptions.DisputeAlreadyResolvedException;
import com.danasea.backend.modules.dispute.domain.exceptions.DisputeNotFoundException;
import com.danasea.backend.modules.dispute.domain.exceptions.UnauthorizedDisputeAccessException;
import com.danasea.backend.modules.dispute.domain.models.DisputeReason;
import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.dispute.infrastructure.persistence.entities.DisputeJpaEntity;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.dispute.presentation.controllers.CustomerDisputeController;
import com.danasea.backend.modules.dispute.presentation.controllers.DisputeEvidenceController;
import com.danasea.backend.modules.dispute.presentation.controllers.VendorDisputeController;
import com.danasea.backend.modules.dispute.presentation.dtos.DisputeResponse;
import com.danasea.backend.modules.dispute.presentation.dtos.EvidenceUploadResponse;
import com.danasea.backend.modules.dispute.presentation.dtos.SubmitVendorResponseRequest;
import com.danasea.backend.modules.dispute.presentation.handlers.DisputeExceptionHandler;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.application.ports.FileStoragePort;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Milestone 3 Empirical Challenger Test Suite (Adversarial & Stress)")
class Milestone3EmpiricalChallengerTest {

    @Mock
    private JpaDisputeRepository disputeRepository;

    @Mock
    private JpaSubOrderRepository subOrderRepository;

    @Mock
    private VendorInternalApi vendorInternalApi;

    @Mock
    private FileStoragePort fileStoragePort;

    @Mock
    private CreateDisputeUseCase createDisputeUseCase;

    @Mock
    private GetCustomerDisputesUseCase mockGetCustomerDisputesUseCase;

    @Mock
    private GetCustomerDisputeDetailUseCase mockGetCustomerDisputeDetailUseCase;

    @Mock
    private GetVendorDisputesUseCase mockGetVendorDisputesUseCase;

    @Mock
    private GetVendorDisputeDetailUseCase mockGetVendorDisputeDetailUseCase;

    @Mock
    private SubmitVendorDisputeResponseUseCase mockSubmitVendorDisputeResponseUseCase;

    @Mock
    private GetAdminDisputeDetailUseCase mockGetAdminDisputeDetailUseCase;

    private ObjectMapper objectMapper;
    private DisputeExceptionHandler exceptionHandler;

    private UUID customerId;
    private UUID otherCustomerId;
    private UUID vendorUserId;
    private UUID vendorId;
    private UUID otherVendorId;
    private UUID orderId;
    private UUID subOrderId;
    private UUID disputeId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        otherCustomerId = UUID.randomUUID();
        vendorUserId = UUID.randomUUID();
        vendorId = UUID.randomUUID();
        otherVendorId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        subOrderId = UUID.randomUUID();
        disputeId = UUID.randomUUID();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        exceptionHandler = new DisputeExceptionHandler();
    }

    private Vendor createVendor(UUID id, UUID userId) {
        Vendor vendor = new Vendor();
        vendor.setId(id);
        vendor.setUserId(userId);
        return vendor;
    }

    // =========================================================================
    // 1. CUSTOMER ISOLATION & BOUNDARY TESTS (R2, HTTP 403 / 404 / 400)
    // =========================================================================
    @Nested
    @DisplayName("1. Customer Isolation & Edge Cases")
    class CustomerIsolationTests {

        @Test
        @DisplayName("CHALLENGE: Customer B accessing Customer A's dispute directly in UseCase throws 403 UnauthorizedDisputeAccessException")
        void customerIsolation_UseCase_ThrowsUnauthorized() {
            GetCustomerDisputeDetailUseCase useCase = new GetCustomerDisputeDetailUseCase(disputeRepository);

            DisputeJpaEntity disputeOfCustomerA = DisputeJpaEntity.builder()
                    .orderId(orderId)
                    .subOrderId(subOrderId)
                    .customerId(customerId) // Customer A
                    .reason(DisputeReason.SERVICE_NOT_AS_DESCRIBED)
                    .status(DisputeStatus.OPEN)
                    .build();
            disputeOfCustomerA.setId(disputeId);

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(disputeOfCustomerA));

            // Customer B attempts access
            assertThatThrownBy(() -> useCase.execute(disputeId, otherCustomerId))
                    .isInstanceOf(UnauthorizedDisputeAccessException.class)
                    .hasMessageContaining("User does not have permission to access this dispute");
        }

        @Test
        @DisplayName("CHALLENGE: Customer B accessing Customer A's dispute via REST endpoint returns 403 Forbidden with ACCESS_DENIED")
        void customerIsolation_MockMvc_Returns403Forbidden() throws Exception {
            CustomerDisputeController controller = new CustomerDisputeController(
                    createDisputeUseCase, mockGetCustomerDisputesUseCase, mockGetCustomerDisputeDetailUseCase);
            MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                    .setControllerAdvice(exceptionHandler)
                    .build();

            when(mockGetCustomerDisputeDetailUseCase.execute(disputeId))
                    .thenThrow(new UnauthorizedDisputeAccessException("User does not have permission to access this dispute"));

            mockMvc.perform(get("/api/disputes/{id}", disputeId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("CHALLENGE: Customer querying non-existent dispute returns 404 Not Found DISPUTE_NOT_FOUND")
        void customerAccess_NonExistentDispute_Returns404() throws Exception {
            CustomerDisputeController controller = new CustomerDisputeController(
                    createDisputeUseCase, mockGetCustomerDisputesUseCase, mockGetCustomerDisputeDetailUseCase);
            MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                    .setControllerAdvice(exceptionHandler)
                    .build();

            when(mockGetCustomerDisputeDetailUseCase.execute(disputeId))
                    .thenThrow(new DisputeNotFoundException("Dispute not found with id: " + disputeId));

            mockMvc.perform(get("/api/disputes/{id}", disputeId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("DISPUTE_NOT_FOUND"));
        }

        @Test
        @DisplayName("CHALLENGE: Customer detail query with null disputeId throws IllegalArgumentException")
        void customerDetail_NullDisputeId_ThrowsIllegalArgumentException() {
            GetCustomerDisputeDetailUseCase useCase = new GetCustomerDisputeDetailUseCase(disputeRepository);
            assertThatThrownBy(() -> useCase.execute(null, customerId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Dispute ID cannot be null");
        }

        @Test
        @DisplayName("CHALLENGE: Pagination boundary stress - page < 0, size = 0, size > 100 throw IllegalArgumentException")
        void customerPagination_BoundaryViolations_ThrowIllegalArgumentException() {
            GetCustomerDisputesUseCase useCase = new GetCustomerDisputesUseCase(disputeRepository);

            assertThatThrownBy(() -> useCase.execute(PageRequest.of(-1, 20), customerId))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> useCase.execute(PageRequest.of(0, 0), customerId))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> useCase.execute(PageRequest.of(0, 101), customerId))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CHALLENGE: Unauthenticated invocation without SecurityContext throws UnauthorizedDisputeAccessException")
        void customerUseCase_NoSecurityContext_ThrowsUnauthorized() {
            GetCustomerDisputeDetailUseCase useCase = new GetCustomerDisputeDetailUseCase(disputeRepository);
            assertThatThrownBy(() -> useCase.execute(disputeId))
                    .isInstanceOf(UnauthorizedDisputeAccessException.class)
                    .hasMessageContaining("User is not authenticated");

            GetCustomerDisputesUseCase listUseCase = new GetCustomerDisputesUseCase(disputeRepository);
            assertThatThrownBy(() -> listUseCase.execute(PageRequest.of(0, 20)))
                    .isInstanceOf(UnauthorizedDisputeAccessException.class)
                    .hasMessageContaining("User is not authenticated");
        }
    }

    // =========================================================================
    // 2. VENDOR ISOLATION & ACCESS CONTROL TESTS (R3, HTTP 403 / 404)
    // =========================================================================
    @Nested
    @DisplayName("2. Vendor Isolation & Access Control")
    class VendorIsolationTests {

        @Test
        @DisplayName("CHALLENGE: Vendor B viewing dispute of Vendor A's sub-order throws 403 UnauthorizedDisputeAccessException")
        void vendorIsolation_ViewDisputeOfOtherVendor_Throws403() {
            GetVendorDisputeDetailUseCase useCase = new GetVendorDisputeDetailUseCase(
                    disputeRepository, subOrderRepository, vendorInternalApi);

            Vendor vendorB = createVendor(otherVendorId, vendorUserId);
            when(vendorInternalApi.findByUserId(vendorUserId)).thenReturn(Optional.of(vendorB));

            DisputeJpaEntity dispute = DisputeJpaEntity.builder()
                    .orderId(orderId)
                    .subOrderId(subOrderId)
                    .customerId(customerId)
                    .status(DisputeStatus.OPEN)
                    .build();
            dispute.setId(disputeId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setVendorId(vendorId); // Vendor A's sub-order

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));

            assertThatThrownBy(() -> useCase.execute(disputeId, vendorUserId))
                    .isInstanceOf(UnauthorizedDisputeAccessException.class)
                    .hasMessageContaining("User does not have permission to access this dispute");
        }

        @Test
        @DisplayName("CHALLENGE: Vendor B attempting to submit response to Vendor A's dispute throws 403")
        void vendorIsolation_SubmitResponseToOtherVendorDispute_Throws403() {
            SubmitVendorDisputeResponseUseCase useCase = new SubmitVendorDisputeResponseUseCase(
                    disputeRepository, subOrderRepository, vendorInternalApi);

            Vendor vendorB = createVendor(otherVendorId, vendorUserId);
            when(vendorInternalApi.findByUserId(vendorUserId)).thenReturn(Optional.of(vendorB));

            DisputeJpaEntity dispute = DisputeJpaEntity.builder()
                    .orderId(orderId)
                    .subOrderId(subOrderId)
                    .customerId(customerId)
                    .status(DisputeStatus.OPEN)
                    .build();
            dispute.setId(disputeId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setVendorId(vendorId); // Vendor A's sub-order

            when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));

            SubmitVendorResponseRequest request = new SubmitVendorResponseRequest("Unauthorized response", List.of());

            assertThatThrownBy(() -> useCase.execute(disputeId, request, vendorUserId))
                    .isInstanceOf(UnauthorizedDisputeAccessException.class)
                    .hasMessageContaining("User does not have permission to respond to this dispute");
        }

        @Test
        @DisplayName("CHALLENGE: Vendor REST endpoint returns 403 Forbidden with ACCESS_DENIED on IDOR attempt")
        void vendorIsolation_MockMvc_Returns403() throws Exception {
            VendorDisputeController controller = new VendorDisputeController(
                    mockGetVendorDisputesUseCase, mockGetVendorDisputeDetailUseCase, mockSubmitVendorDisputeResponseUseCase);
            MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                    .setControllerAdvice(exceptionHandler)
                    .build();

            when(mockGetVendorDisputeDetailUseCase.execute(disputeId))
                    .thenThrow(new UnauthorizedDisputeAccessException("User does not have permission to access this dispute"));

            mockMvc.perform(get("/api/vendor/disputes/{id}", disputeId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("CHALLENGE: User calling vendor endpoints without a vendor profile throws UnauthorizedDisputeAccessException")
        void vendorAccess_NoVendorProfile_ThrowsUnauthorized() {
            GetVendorDisputeDetailUseCase useCase = new GetVendorDisputeDetailUseCase(
                    disputeRepository, subOrderRepository, vendorInternalApi);

            when(vendorInternalApi.findByUserId(vendorUserId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.execute(disputeId, vendorUserId))
                    .isInstanceOf(UnauthorizedDisputeAccessException.class)
                    .hasMessageContaining("User is not a vendor");
        }

        @Test
        @DisplayName("CHALLENGE: Sub-order missing for dispute in vendor query throws UnauthorizedDisputeAccessException")
        void vendorAccess_SubOrderMissing_ThrowsUnauthorized() {
            GetVendorDisputeDetailUseCase useCase = new GetVendorDisputeDetailUseCase(
                    disputeRepository, subOrderRepository, vendorInternalApi);

            Vendor vendor = createVendor(vendorId, vendorUserId);
            when(vendorInternalApi.findByUserId(vendorUserId)).thenReturn(Optional.of(vendor));

            DisputeJpaEntity dispute = DisputeJpaEntity.builder()
                    .orderId(orderId)
                    .subOrderId(subOrderId)
                    .customerId(customerId)
                    .status(DisputeStatus.OPEN)
                    .build();
            dispute.setId(disputeId);

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.execute(disputeId, vendorUserId))
                    .isInstanceOf(UnauthorizedDisputeAccessException.class)
                    .hasMessageContaining("Sub-order associated with dispute not found");
        }

        @Test
        @DisplayName("CHALLENGE: Vendor with 0 sub-orders returns empty page gracefully without database query")
        void vendorList_NoSubOrders_ReturnsEmptyPage() {
            GetVendorDisputesUseCase useCase = new GetVendorDisputesUseCase(
                    disputeRepository, subOrderRepository, vendorInternalApi);

            Vendor vendor = createVendor(vendorId, vendorUserId);
            when(vendorInternalApi.findByUserId(vendorUserId)).thenReturn(Optional.of(vendor));
            when(subOrderRepository.findByVendorId(vendorId)).thenReturn(List.of());

            Pageable pageable = PageRequest.of(0, 20);
            Page<DisputeResponse> result = useCase.execute(null, pageable, vendorUserId);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    // =========================================================================
    // 3. EVIDENCE UPLOAD VALIDATION & ANTI-SPOOFING TESTS (R5, HTTP 400 / 200)
    // =========================================================================
    @Nested
    @DisplayName("3. Evidence Upload Validation & Anti-Spoofing Stress")
    class EvidenceUploadValidationTests {

        private UploadDisputeEvidenceUseCase uploadUseCase;
        private MockMvc mockMvc;

        @BeforeEach
        void initUpload() {
            uploadUseCase = new UploadDisputeEvidenceUseCase(fileStoragePort);
            DisputeEvidenceController controller = new DisputeEvidenceController(uploadUseCase);
            mockMvc = MockMvcBuilders.standaloneSetup(controller)
                    .setControllerAdvice(exceptionHandler)
                    .build();
        }

        @Test
        @DisplayName("CHALLENGE: Empty file (0 bytes) returns 400 Bad Request with INVALID_FILE_TYPE")
        void upload_EmptyFile_Returns400() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "zero.png", "image/png", new byte[0]);

            mockMvc.perform(multipart("/api/disputes/evidence").file(file))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));
        }

        @Test
        @DisplayName("CHALLENGE: Oversized file (5MB + 1 byte) returns 400 Bad Request with INVALID_FILE_TYPE")
        void upload_Exceeds5MB_Returns400() throws Exception {
            int size = (int) (5L * 1024 * 1024 + 1); // 5242881 bytes
            byte[] oversized = new byte[size];
            oversized[0] = (byte) 0xFF;
            oversized[1] = (byte) 0xD8;
            oversized[2] = (byte) 0xFF;

            MockMultipartFile file = new MockMultipartFile(
                    "file", "too_large.jpg", "image/jpeg", oversized);

            mockMvc.perform(multipart("/api/disputes/evidence").file(file))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));
        }

        @Test
        @DisplayName("CHALLENGE: Boundary exact 5MB file (5,242,880 bytes) with valid JPEG signature succeeds (200 OK)")
        void upload_Exactly5MB_Succeeds200() throws Exception {
            int size = (int) (5L * 1024 * 1024); // exactly 5242880 bytes
            byte[] exact5MB = new byte[size];
            exact5MB[0] = (byte) 0xFF;
            exact5MB[1] = (byte) 0xD8;
            exact5MB[2] = (byte) 0xFF;

            MockMultipartFile file = new MockMultipartFile(
                    "file", "boundary_5mb.jpg", "image/jpeg", exact5MB);

            when(fileStoragePort.uploadFile(any(byte[].class), eq("boundary_5mb.jpg"), eq("disputes/evidence")))
                    .thenReturn("https://res.cloudinary.com/test/boundary_5mb.jpg");

            mockMvc.perform(multipart("/api/disputes/evidence").file(file))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fileUrl").value("https://res.cloudinary.com/test/boundary_5mb.jpg"));
        }

        @Test
        @DisplayName("CHALLENGE: Spoofed MIME - JPEG declared but content is plain ASCII returns 400")
        void upload_SpoofedJpeg_Returns400() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "spoof.jpg", "image/jpeg", "Plain text not a real JPEG".getBytes(StandardCharsets.UTF_8));

            mockMvc.perform(multipart("/api/disputes/evidence").file(file))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));
        }

        @Test
        @DisplayName("CHALLENGE: Spoofed MIME - PNG declared but starts with JPEG header returns 400")
        void upload_PngWithJpegHeader_Returns400() throws Exception {
            byte[] fakePng = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01};
            MockMultipartFile file = new MockMultipartFile(
                    "file", "fake.png", "image/png", fakePng);

            mockMvc.perform(multipart("/api/disputes/evidence").file(file))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));
        }

        @Test
        @DisplayName("CHALLENGE: Spoofed MIME - PDF declared but content is HTML/PHP returns 400")
        void upload_PdfWithHtmlContent_Returns400() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "payload.pdf", "application/pdf", "<html><body>Exploit</body></html>".getBytes(StandardCharsets.UTF_8));

            mockMvc.perform(multipart("/api/disputes/evidence").file(file))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));
        }

        @Test
        @DisplayName("CHALLENGE: Spoofed WEBP - starts with RIFF but offset 8 is WAVE (WAV audio disguised as image) returns 400")
        void upload_WebpWithWavSignature_Returns400() throws Exception {
            byte[] wavBytes = new byte[20];
            System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, wavBytes, 0, 4);
            System.arraycopy("WAVE".getBytes(StandardCharsets.US_ASCII), 0, wavBytes, 8, 4);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "disguised_audio.webp", "image/webp", wavBytes);

            mockMvc.perform(multipart("/api/disputes/evidence").file(file))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));
        }

        @Test
        @DisplayName("CHALLENGE: Valid WEBP - starts with RIFF and offset 8 is WEBP succeeds (200 OK)")
        void upload_ValidWebp_Succeeds200() throws Exception {
            byte[] webpBytes = new byte[20];
            System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, webpBytes, 0, 4);
            System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, webpBytes, 8, 4);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "valid.webp", "image/webp", webpBytes);

            when(fileStoragePort.uploadFile(any(byte[].class), eq("valid.webp"), eq("disputes/evidence")))
                    .thenReturn("https://res.cloudinary.com/test/valid.webp");

            mockMvc.perform(multipart("/api/disputes/evidence").file(file))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fileUrl").value("https://res.cloudinary.com/test/valid.webp"));
        }

        @Test
        @DisplayName("CHALLENGE: Unallowed MIME - image/gif rejected with 400 because GIF is not in allowed types for dispute")
        void upload_GifNotAllowed_Returns400() throws Exception {
            byte[] gifBytes = "GIF89a sample content".getBytes(StandardCharsets.US_ASCII);
            MockMultipartFile file = new MockMultipartFile(
                    "file", "animation.gif", "image/gif", gifBytes);

            mockMvc.perform(multipart("/api/disputes/evidence").file(file))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));
        }

        @Test
        @DisplayName("CHALLENGE: Filename null/blank fallback - usecase safely defaults to 'evidence'")
        void upload_NullFilename_FallsBackToEvidence() {
            byte[] validPdf = "%PDF-1.4 sample".getBytes(StandardCharsets.US_ASCII);
            MockMultipartFile file = new MockMultipartFile(
                    "file", "", "application/pdf", validPdf);

            when(fileStoragePort.uploadFile(any(byte[].class), eq("evidence"), eq("disputes/evidence")))
                    .thenReturn("https://res.cloudinary.com/test/evidence");

            EvidenceUploadResponse response = uploadUseCase.execute(file);
            assertThat(response.fileUrl()).isEqualTo("https://res.cloudinary.com/test/evidence");
            verify(fileStoragePort).uploadFile(any(byte[].class), eq("evidence"), eq("disputes/evidence"));
        }
    }

    // =========================================================================
    // 4. DISPUTE LIFECYCLE & STATE CONFLICT TESTS (R3, HTTP 409 / 400 / 200)
    // =========================================================================
    @Nested
    @DisplayName("4. Dispute Lifecycle & State Conflict")
    class DisputeLifecycleConflictTests {

        private SubmitVendorDisputeResponseUseCase useCase;
        private VendorDisputeController controller;
        private MockMvc mockMvc;

        @BeforeEach
        void initLifecycle() {
            useCase = new SubmitVendorDisputeResponseUseCase(
                    disputeRepository, subOrderRepository, vendorInternalApi);
            controller = new VendorDisputeController(
                    mockGetVendorDisputesUseCase, mockGetVendorDisputeDetailUseCase, useCase);
            mockMvc = MockMvcBuilders.standaloneSetup(controller)
                    .setControllerAdvice(exceptionHandler)
                    .build();
        }

        @Test
        @DisplayName("CHALLENGE: Submitting response to dispute in RESOLVED_REFUND throws 409 DISPUTE_ALREADY_RESOLVED")
        void submitResponse_ResolvedRefund_Returns409() throws Exception {
            Vendor vendor = createVendor(vendorId, vendorUserId);
            when(vendorInternalApi.findByUserId(vendorUserId)).thenReturn(Optional.of(vendor));

            DisputeJpaEntity dispute = DisputeJpaEntity.builder()
                    .orderId(orderId)
                    .subOrderId(subOrderId)
                    .customerId(customerId)
                    .status(DisputeStatus.RESOLVED_REFUND)
                    .build();
            dispute.setId(disputeId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setVendorId(vendorId);

            when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));

            SubmitVendorResponseRequest request = new SubmitVendorResponseRequest(
                    "Attempting late response", List.of());

            assertThatThrownBy(() -> useCase.execute(disputeId, request, vendorUserId))
                    .isInstanceOf(DisputeAlreadyResolvedException.class)
                    .hasMessageContaining("Cannot submit response for an already resolved dispute");
        }

        @Test
        @DisplayName("CHALLENGE: Submitting response to dispute in RESOLVED_REJECTED throws 409 Conflict")
        void submitResponse_ResolvedRejected_Throws409() {
            Vendor vendor = createVendor(vendorId, vendorUserId);
            when(vendorInternalApi.findByUserId(vendorUserId)).thenReturn(Optional.of(vendor));

            DisputeJpaEntity dispute = DisputeJpaEntity.builder()
                    .orderId(orderId)
                    .subOrderId(subOrderId)
                    .customerId(customerId)
                    .status(DisputeStatus.RESOLVED_REJECTED)
                    .build();
            dispute.setId(disputeId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setVendorId(vendorId);

            when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));

            SubmitVendorResponseRequest request = new SubmitVendorResponseRequest(
                    "Trying to respond after rejection", List.of());

            assertThatThrownBy(() -> useCase.execute(disputeId, request, vendorUserId))
                    .isInstanceOf(DisputeAlreadyResolvedException.class);
        }

        @Test
        @DisplayName("CHALLENGE: Submitting response to dispute in RESOLVED_PARTIAL throws 409 Conflict")
        void submitResponse_ResolvedPartial_Throws409() {
            Vendor vendor = createVendor(vendorId, vendorUserId);
            when(vendorInternalApi.findByUserId(vendorUserId)).thenReturn(Optional.of(vendor));

            DisputeJpaEntity dispute = DisputeJpaEntity.builder()
                    .orderId(orderId)
                    .subOrderId(subOrderId)
                    .customerId(customerId)
                    .status(DisputeStatus.RESOLVED_PARTIAL)
                    .build();
            dispute.setId(disputeId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setVendorId(vendorId);

            when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));

            SubmitVendorResponseRequest request = new SubmitVendorResponseRequest(
                    "Trying to respond after partial resolution", List.of());

            assertThatThrownBy(() -> useCase.execute(disputeId, request, vendorUserId))
                    .isInstanceOf(DisputeAlreadyResolvedException.class);
        }

        @Test
        @DisplayName("CHALLENGE: Submitting response when dispute is OPEN correctly transitions to UNDER_REVIEW and saves fields")
        void submitResponse_OpenDispute_TransitionsToUnderReview() {
            Vendor vendor = createVendor(vendorId, vendorUserId);
            when(vendorInternalApi.findByUserId(vendorUserId)).thenReturn(Optional.of(vendor));

            DisputeJpaEntity dispute = DisputeJpaEntity.builder()
                    .orderId(orderId)
                    .subOrderId(subOrderId)
                    .customerId(customerId)
                    .status(DisputeStatus.OPEN)
                    .build();
            dispute.setId(disputeId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setVendorId(vendorId);

            when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(disputeRepository.save(any(DisputeJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            SubmitVendorResponseRequest request = new SubmitVendorResponseRequest(
                    "Official vendor rebuttal", List.of("http://evidence.pdf"));

            DisputeResponse response = useCase.execute(disputeId, request, vendorUserId);

            assertThat(response.status()).isEqualTo(DisputeStatus.UNDER_REVIEW);
            assertThat(response.vendorResponse()).isEqualTo("Official vendor rebuttal");
            assertThat(response.vendorEvidenceUrls()).containsExactly("http://evidence.pdf");
            assertThat(response.vendorRespondedAt()).isNotNull();

            ArgumentCaptor<DisputeJpaEntity> captor = ArgumentCaptor.forClass(DisputeJpaEntity.class);
            verify(disputeRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(DisputeStatus.UNDER_REVIEW);
            assertThat(captor.getValue().getVendorResponse()).isEqualTo("Official vendor rebuttal");
            assertThat(captor.getValue().getVendorEvidenceUrls()).containsExactly("http://evidence.pdf");
        }

        @Test
        @DisplayName("CHALLENGE: Submitting updated response when dispute is already UNDER_REVIEW retains UNDER_REVIEW status")
        void submitResponse_AlreadyUnderReview_RetainsUnderReview() {
            Vendor vendor = createVendor(vendorId, vendorUserId);
            when(vendorInternalApi.findByUserId(vendorUserId)).thenReturn(Optional.of(vendor));

            DisputeJpaEntity dispute = DisputeJpaEntity.builder()
                    .orderId(orderId)
                    .subOrderId(subOrderId)
                    .customerId(customerId)
                    .status(DisputeStatus.UNDER_REVIEW)
                    .vendorResponse("Previous response")
                    .vendorEvidenceUrls(List.of())
                    .build();
            dispute.setId(disputeId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setVendorId(vendorId);

            when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(disputeRepository.save(any(DisputeJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            SubmitVendorResponseRequest request = new SubmitVendorResponseRequest(
                    "Updated vendor rebuttal with new evidence", List.of("http://new_evidence.pdf"));

            DisputeResponse response = useCase.execute(disputeId, request, vendorUserId);

            assertThat(response.status()).isEqualTo(DisputeStatus.UNDER_REVIEW);
            assertThat(response.vendorResponse()).isEqualTo("Updated vendor rebuttal with new evidence");
            assertThat(response.vendorEvidenceUrls()).containsExactly("http://new_evidence.pdf");
        }

        @Test
        @DisplayName("CHALLENGE: Blank response content in request returns 400 Bad Request with INVALID_INPUT")
        void submitResponse_BlankContent_Returns400() throws Exception {
            SubmitVendorResponseRequest request = new SubmitVendorResponseRequest("   ", List.of());

            mockMvc.perform(post("/api/vendor/disputes/{id}/responses", disputeId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("CHALLENGE: Null response string in UseCase throws IllegalArgumentException")
        void submitResponse_NullContentInUseCase_ThrowsIllegalArgumentException() {
            SubmitVendorResponseRequest request = new SubmitVendorResponseRequest(null, List.of());
            assertThatThrownBy(() -> useCase.execute(disputeId, request, vendorUserId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Response content is required");
        }
    }

    // =========================================================================
    // 5. ADMIN DOSSIER & COMPREHENSIVE REVIEW (R4, HTTP 200 / 404)
    // =========================================================================
    @Nested
    @DisplayName("5. Admin Comprehensive Dispute Dossier")
    class AdminDisputeDossierTests {

        @Test
        @DisplayName("CHALLENGE: Admin retrieves comprehensive dispute dossier with customer & vendor evidence (200 OK)")
        void adminDossier_Success200() {
            GetAdminDisputeDetailUseCase useCase = new GetAdminDisputeDetailUseCase(disputeRepository);

            OffsetDateTime now = OffsetDateTime.now();
            DisputeJpaEntity dispute = DisputeJpaEntity.builder()
                    .orderId(orderId)
                    .subOrderId(subOrderId)
                    .customerId(customerId)
                    .reason(DisputeReason.SERVICE_NOT_AS_DESCRIBED)
                    .description("Customer claim description")
                    .evidenceUrls(List.of("http://customer_proof.jpg"))
                    .status(DisputeStatus.UNDER_REVIEW)
                    .vendorResponse("Vendor counter-argument")
                    .vendorEvidenceUrls(List.of("http://vendor_counter.pdf"))
                    .vendorRespondedAt(now)
                    .build();
            dispute.setId(disputeId);

            when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));

            DisputeResponse response = useCase.execute(disputeId);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(disputeId);
            assertThat(response.reason()).isEqualTo(DisputeReason.SERVICE_NOT_AS_DESCRIBED);
            assertThat(response.description()).isEqualTo("Customer claim description");
            assertThat(response.evidenceUrls()).containsExactly("http://customer_proof.jpg");
            assertThat(response.vendorResponse()).isEqualTo("Vendor counter-argument");
            assertThat(response.vendorEvidenceUrls()).containsExactly("http://vendor_counter.pdf");
            assertThat(response.vendorRespondedAt()).isEqualTo(now);
            assertThat(response.status()).isEqualTo(DisputeStatus.UNDER_REVIEW);
        }

        @Test
        @DisplayName("CHALLENGE: Admin retrieving non-existent dispute throws DisputeNotFoundException")
        void adminDossier_NotFound_Throws404() {
            GetAdminDisputeDetailUseCase useCase = new GetAdminDisputeDetailUseCase(disputeRepository);
            when(disputeRepository.findById(disputeId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.execute(disputeId))
                    .isInstanceOf(DisputeNotFoundException.class)
                    .hasMessageContaining("Dispute not found with id: " + disputeId);
        }

        @Test
        @DisplayName("CHALLENGE: Admin retrieving with null disputeId throws IllegalArgumentException")
        void adminDossier_NullId_ThrowsIllegalArgumentException() {
            GetAdminDisputeDetailUseCase useCase = new GetAdminDisputeDetailUseCase(disputeRepository);
            assertThatThrownBy(() -> useCase.execute(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Dispute ID cannot be null");
        }
    }
}
