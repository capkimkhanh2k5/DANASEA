package com.danasea.backend.modules.admin.application.usecases;

import com.danasea.backend.modules.admin.presentation.dtos.AdminVendorResponse;
import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorNotFoundException;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.domain.models.DocType;
import com.danasea.backend.modules.admin.domain.exceptions.VendorDocumentsIncompleteException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApproveVendorUseCaseTest {

    @Mock
    private VendorInternalApi vendorInternalApi;

    @Mock
    private AuditLogInternalApi auditLogInternalApi;

    private ApproveVendorUseCase approveVendorUseCase;

    private UUID vendorId;
    private UUID adminId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        approveVendorUseCase = new ApproveVendorUseCase(vendorInternalApi, auditLogInternalApi);
        vendorId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void execute_WhenVendorNotFound_ThrowsVendorNotFoundException() {
        when(vendorInternalApi.findById(vendorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approveVendorUseCase.execute(vendorId, adminId))
                .isInstanceOf(VendorNotFoundException.class)
                .hasMessageContaining("Vendor not found with id");
    }

    @Test
    void execute_WhenVendorNotPending_ThrowsIllegalArgumentException() {
        Vendor vendor = new Vendor();
        vendor.setId(vendorId);
        vendor.setVerificationStatus(VerificationStatus.APPROVED);

        when(vendorInternalApi.findById(vendorId)).thenReturn(Optional.of(vendor));

        assertThatThrownBy(() -> approveVendorUseCase.execute(vendorId, adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Vendor is not in PENDING status");

        verify(vendorInternalApi, never()).saveVendor(any());
    }

    @Test
    void execute_WhenVendorIsPending_Success() {
        Vendor vendor = new Vendor();
        vendor.setId(vendorId);
        vendor.setUserId(userId);
        vendor.setVerificationStatus(VerificationStatus.PENDING);

        when(vendorInternalApi.findById(vendorId)).thenReturn(Optional.of(vendor));
        when(vendorInternalApi.getApprovedDocumentTypes(vendorId))
                .thenReturn(EnumSet.allOf(DocType.class));
        when(vendorInternalApi.saveVendor(any(Vendor.class))).thenAnswer(i -> i.getArgument(0));

        AdminVendorResponse response = approveVendorUseCase.execute(vendorId, adminId);

        // Verify Vendor updated
        assertThat(response.getVerificationStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(response.getVerifiedBy()).isEqualTo(adminId);
        assertThat(vendor.getVerificationStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(vendor.getVerifiedBy()).isEqualTo(adminId);
        assertThat(vendor.getVerifiedAt()).isNotNull();
        verify(vendorInternalApi).saveVendor(vendor);

        // Verify Audit Log
        verify(auditLogInternalApi).recordAuditLog(
                eq(adminId),
                eq("VENDOR_APPROVAL"),
                eq("VENDOR"),
                eq(vendorId),
                anyString()
        );
    }

    @Test
    void execute_WhenRequiredDocumentsAreNotApproved_RejectsApproval() {
        Vendor vendor = new Vendor();
        vendor.setId(vendorId);
        vendor.setUserId(userId);
        vendor.setVerificationStatus(VerificationStatus.PENDING);
        when(vendorInternalApi.findById(vendorId)).thenReturn(Optional.of(vendor));
        when(vendorInternalApi.getApprovedDocumentTypes(vendorId))
                .thenReturn(Set.of(DocType.BUSINESS_LICENSE));

        assertThatThrownBy(() -> approveVendorUseCase.execute(vendorId, adminId))
                .isInstanceOf(VendorDocumentsIncompleteException.class)
                .hasMessageContaining("SAFETY_CERT");

        verify(vendorInternalApi, never()).saveVendor(any());
    }
}
