package com.danasea.backend.modules.admin.application.usecases;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import com.danasea.backend.modules.admin.presentation.dtos.AdminVendorResponse;
import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorNotFoundException;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RejectVendorUseCaseTest {

    @Mock
    private VendorInternalApi vendorInternalApi;
    @Mock
    private AuditLogInternalApi auditLogInternalApi;
    @InjectMocks
    private RejectVendorUseCase useCase;

    @Test
    void rejectsPendingVendorPersistsDecisionAndAuditsExactReasonBeforeReturningSavedSnapshot() {
        UUID adminId = UUID.randomUUID();
        String reason = "Business license does not match the registered tax code";
        Vendor vendor = vendor();
        Vendor persisted = vendor();
        persisted.setId(vendor.getId());
        persisted.setUserId(vendor.getUserId());
        persisted.setVerificationStatus(VerificationStatus.REJECTED);
        persisted.setVerifiedBy(adminId);
        persisted.setUpdatedAt(OffsetDateTime.parse("2026-11-01T11:00:00+07:00"));
        when(vendorInternalApi.findById(vendor.getId())).thenReturn(Optional.of(vendor));
        OffsetDateTime before = OffsetDateTime.now();
        when(vendorInternalApi.saveVendor(vendor)).thenAnswer(invocation -> {
            Vendor decision = invocation.getArgument(0);
            assertThat(decision.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
            assertThat(decision.getVerifiedBy()).isEqualTo(adminId);
            assertThat(decision.getVerifiedAt()).isAfterOrEqualTo(before);
            persisted.setVerifiedAt(decision.getVerifiedAt());
            return persisted;
        });

        AdminVendorResponse response = useCase.execute(vendor.getId(), adminId, reason);

        assertThat(vendor.getVerifiedAt()).isBeforeOrEqualTo(OffsetDateTime.now());
        assertThat(response).usingRecursiveComparison().ignoringFields("documents").isEqualTo(persisted);
        InOrder order = inOrder(vendorInternalApi, auditLogInternalApi);
        order.verify(vendorInternalApi).findById(vendor.getId());
        order.verify(vendorInternalApi).saveVendor(vendor);
        order.verify(auditLogInternalApi).recordAuditLog(adminId, "VENDOR_REJECTION", "VENDOR",
                vendor.getId(), "Vendor rejected by admin. Reason: " + reason);
    }

    @Test
    void rejectsMissingVendorWithoutPersistingOrAuditing() {
        UUID vendorId = UUID.randomUUID();
        when(vendorInternalApi.findById(vendorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(vendorId, UUID.randomUUID(), "Missing license"))
                .isInstanceOf(VendorNotFoundException.class)
                .hasMessage("Vendor not found with id: " + vendorId);

        verify(vendorInternalApi, never()).saveVendor(any());
        verifyNoInteractions(auditLogInternalApi);
    }

    @Test
    void rejectsNullReasonWithoutModifyingVendorOrAuditing() {
        UUID vendorId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.execute(vendorId, adminId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rejection reason cannot be blank");

        verifyNoInteractions(vendorInternalApi);
        verifyNoInteractions(auditLogInternalApi);
    }

    @Test
    void rejectsBlankReasonWithoutModifyingVendorOrAuditing() {
        UUID vendorId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.execute(vendorId, adminId, "   \t\n  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rejection reason cannot be blank");

        verifyNoInteractions(vendorInternalApi);
        verifyNoInteractions(auditLogInternalApi);
    }

    @Test
    void refusesAlreadyRejectedVendorWithoutChangingItsDecision() {
        assertFinalDecisionCannotBeRejected(VerificationStatus.REJECTED, "Vendor is already rejected");
    }

    @Test
    void refusesApprovedVendorWithoutChangingItsDecision() {
        assertFinalDecisionCannotBeRejected(VerificationStatus.APPROVED,
                "Cannot reject an already approved vendor");
    }

    @Test
    void doesNotAuditRejectionWhenPersistenceFails() {
        Vendor vendor = vendor();
        when(vendorInternalApi.findById(vendor.getId())).thenReturn(Optional.of(vendor));
        when(vendorInternalApi.saveVendor(vendor)).thenThrow(new IllegalStateException("Database unavailable"));

        assertThatThrownBy(() -> useCase.execute(vendor.getId(), UUID.randomUUID(), "Invalid license"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Database unavailable");

        verifyNoInteractions(auditLogInternalApi);
    }

    private void assertFinalDecisionCannotBeRejected(VerificationStatus status, String message) {
        Vendor vendor = vendor();
        vendor.setVerificationStatus(status);
        UUID previousAdmin = UUID.randomUUID();
        OffsetDateTime previousDecisionTime = OffsetDateTime.parse("2026-10-15T10:00:00+07:00");
        vendor.setVerifiedBy(previousAdmin);
        vendor.setVerifiedAt(previousDecisionTime);
        when(vendorInternalApi.findById(vendor.getId())).thenReturn(Optional.of(vendor));

        assertThatThrownBy(() -> useCase.execute(vendor.getId(), UUID.randomUUID(), "Invalid license"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);

        assertThat(vendor.getVerificationStatus()).isEqualTo(status);
        assertThat(vendor.getVerifiedBy()).isEqualTo(previousAdmin);
        assertThat(vendor.getVerifiedAt()).isEqualTo(previousDecisionTime);
        verify(vendorInternalApi, never()).saveVendor(any());
        verifyNoInteractions(auditLogInternalApi);
    }

    private Vendor vendor() {
        Vendor vendor = new Vendor();
        vendor.setId(UUID.randomUUID());
        vendor.setUserId(UUID.randomUUID());
        vendor.setBusinessName("Da Nang Sea Tours");
        vendor.setTaxCode("0401234567");
        vendor.setAddress("12 Vo Nguyen Giap, Da Nang");
        vendor.setBankAccountNumber("0123456789");
        vendor.setBankName("Vietcombank");
        vendor.setBankAccountHolder("DA NANG SEA TOURS");
        vendor.setVerificationStatus(VerificationStatus.PENDING);
        vendor.setRatingAvg(new BigDecimal("4.25"));
        vendor.setRatingCount(8);
        vendor.setBadgeTier(BadgeTier.VERIFIED);
        vendor.setCreatedAt(OffsetDateTime.parse("2026-09-01T10:00:00+07:00"));
        vendor.setUpdatedAt(OffsetDateTime.parse("2026-10-02T10:00:00+07:00"));
        return vendor;
    }
}
