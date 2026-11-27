package com.danasea.backend.modules.dispute.presentation.controllers;

import com.danasea.backend.modules.dispute.application.usecases.UploadDisputeEvidenceUseCase;
import com.danasea.backend.modules.dispute.presentation.handlers.DisputeExceptionHandler;
import com.danasea.backend.modules.service.application.ports.FileStoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("DisputeEvidenceUpload MockMvc Tests")
class DisputeEvidenceUploadTest {

    @Mock
    private FileStoragePort fileStoragePort;

    private UploadDisputeEvidenceUseCase uploadDisputeEvidenceUseCase;
    private DisputeEvidenceController disputeEvidenceController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        uploadDisputeEvidenceUseCase = new UploadDisputeEvidenceUseCase(fileStoragePort);
        disputeEvidenceController = new DisputeEvidenceController(uploadDisputeEvidenceUseCase);

        DisputeExceptionHandler exceptionHandler = new DisputeExceptionHandler();
        mockMvc = MockMvcBuilders.standaloneSetup(disputeEvidenceController)
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    @Test
    @DisplayName("Upload valid JPEG file returns 200 OK with secure URL")
    void uploadEvidence_ValidJpeg_Returns200() throws Exception {
        byte[] validJpegContent = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x11, 0x22};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "proof.jpg",
                "image/jpeg",
                validJpegContent
        );

        when(fileStoragePort.uploadFile(any(byte[].class), eq("proof.jpg"), eq("disputes/evidence")))
                .thenReturn("https://res.cloudinary.com/danasea/image/upload/v1/disputes/evidence/proof.jpg");

        mockMvc.perform(multipart("/api/disputes/evidence").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileUrl").value("https://res.cloudinary.com/danasea/image/upload/v1/disputes/evidence/proof.jpg"));

        verify(fileStoragePort).uploadFile(any(byte[].class), eq("proof.jpg"), eq("disputes/evidence"));
    }

    @Test
    @DisplayName("Upload valid PNG file returns 200 OK with secure URL")
    void uploadEvidence_ValidPng_Returns200() throws Exception {
        byte[] validPngContent = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00
        };
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "evidence.png",
                "image/png",
                validPngContent
        );

        when(fileStoragePort.uploadFile(any(byte[].class), eq("evidence.png"), eq("disputes/evidence")))
                .thenReturn("https://res.cloudinary.com/danasea/image/upload/v1/disputes/evidence/evidence.png");

        mockMvc.perform(multipart("/api/disputes/evidence").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileUrl").value("https://res.cloudinary.com/danasea/image/upload/v1/disputes/evidence/evidence.png"));

        verify(fileStoragePort).uploadFile(any(byte[].class), eq("evidence.png"), eq("disputes/evidence"));
    }

    @Test
    @DisplayName("Upload valid PDF file returns 200 OK with secure URL")
    void uploadEvidence_ValidPdf_Returns200() throws Exception {
        byte[] validPdfContent = "%PDF-1.7 sample content".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.pdf",
                "application/pdf",
                validPdfContent
        );

        when(fileStoragePort.uploadFile(any(byte[].class), eq("contract.pdf"), eq("disputes/evidence")))
                .thenReturn("https://res.cloudinary.com/danasea/raw/upload/v1/disputes/evidence/contract.pdf");

        mockMvc.perform(multipart("/api/disputes/evidence").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileUrl").value("https://res.cloudinary.com/danasea/raw/upload/v1/disputes/evidence/contract.pdf"));

        verify(fileStoragePort).uploadFile(any(byte[].class), eq("contract.pdf"), eq("disputes/evidence"));
    }

    @Test
    @DisplayName("Upload empty file returns 400 Bad Request with INVALID_FILE_TYPE")
    void uploadEvidence_EmptyFile_Returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/disputes/evidence").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));
    }

    @Test
    @DisplayName("Upload unsupported MIME type returns 400 Bad Request")
    void uploadEvidence_UnsupportedMimeType_Returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "malicious.sh",
                "application/x-sh",
                "echo hello".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/disputes/evidence").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));
    }

    @Test
    @DisplayName("Upload file with mismatched magic bytes returns 400 Bad Request")
    void uploadEvidence_MismatchedSignature_Returns400() throws Exception {
        // Declared as image/jpeg but content is plain text
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.jpg",
                "image/jpeg",
                "Not a real jpeg file".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/disputes/evidence").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));
    }

    @Test
    @DisplayName("Upload file exceeding 5MB returns 400 Bad Request")
    void uploadEvidence_Exceeds5MB_Returns400() throws Exception {
        byte[] oversizedContent = new byte[(int) (5L * 1024 * 1024 + 10)];
        oversizedContent[0] = (byte) 0xFF;
        oversizedContent[1] = (byte) 0xD8;
        oversizedContent[2] = (byte) 0xFF;

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.jpg",
                "image/jpeg",
                oversizedContent
        );

        mockMvc.perform(multipart("/api/disputes/evidence").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));
    }
}
