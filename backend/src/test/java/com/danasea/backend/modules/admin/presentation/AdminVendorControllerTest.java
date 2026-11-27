package com.danasea.backend.modules.admin.presentation;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.admin.application.usecases.ApproveVendorUseCase;
import com.danasea.backend.modules.admin.application.usecases.GetVendorDetailUseCase;
import com.danasea.backend.modules.admin.application.usecases.GetVendorsUseCase;
import com.danasea.backend.modules.admin.application.usecases.RejectVendorUseCase;
import com.danasea.backend.modules.admin.presentation.dtos.AdminVendorResponse;
import com.danasea.backend.modules.vendor.domain.models.DocType;
import com.danasea.backend.modules.vendor.presentation.dtos.VendorDocumentResponse;
import com.danasea.backend.shared.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminVendorControllerTest {

    private MockMvc mockMvc;

    @Mock
    private GetVendorsUseCase getVendorsUseCase;

    @Mock
    private GetVendorDetailUseCase getVendorDetailUseCase;

    @Mock
    private ApproveVendorUseCase approveVendorUseCase;

    @Mock
    private RejectVendorUseCase rejectVendorUseCase;

    @Mock
    private AccountInternalApi accountInternalApi;

    @InjectMocks
    private AdminVendorController adminVendorController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminVendorController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getVendor_ShouldReturnVendorWithDocuments() throws Exception {
        UUID vendorId = UUID.randomUUID();
        
        VendorDocumentResponse doc1 = new VendorDocumentResponse(
                UUID.randomUUID(), vendorId, DocType.BUSINESS_LICENSE, "http://example.com/doc1", null, null, null, null, null
        );
        VendorDocumentResponse doc2 = new VendorDocumentResponse(
                UUID.randomUUID(), vendorId, DocType.SAFETY_CERT, "http://example.com/doc2", null, null, null, null, null
        );

        AdminVendorResponse response = AdminVendorResponse.builder()
                .id(vendorId)
                .businessName("Test Business")
                .documents(List.of(doc1, doc2))
                .build();

        when(getVendorDetailUseCase.execute(vendorId)).thenReturn(response);

        mockMvc.perform(get("/api/admin/vendors/{id}", vendorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vendorId.toString()))
                .andExpect(jsonPath("$.businessName").value("Test Business"))
                .andExpect(jsonPath("$.documents").isArray())
                .andExpect(jsonPath("$.documents.length()").value(2))
                .andExpect(jsonPath("$.documents[0].fileUrl").value("http://example.com/doc1"))
                .andExpect(jsonPath("$.documents[1].fileUrl").value("http://example.com/doc2"));
    }
}
