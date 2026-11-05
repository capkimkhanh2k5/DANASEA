package com.danasea.backend.modules.admin.application.usecases;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.danasea.backend.modules.admin.presentation.dtos.AdminVendorResponse;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetVendorsUseCaseTest {

    @Mock
    private VendorInternalApi vendorInternalApi;
    @InjectMocks
    private GetVendorsUseCase useCase;

    @Test
    void filtersByStatusAndMapsCompleteVendorWhilePreservingPagination() {
        PageRequest pageable = PageRequest.of(2, 3, Sort.by("createdAt").descending());
        Vendor vendor = new Vendor();
        vendor.setId(UUID.randomUUID());
        vendor.setUserId(UUID.randomUUID());
        vendor.setBusinessName("Da Nang Sea Tours");
        vendor.setTaxCode("0401234567");
        vendor.setAddress("12 Vo Nguyen Giap, Da Nang");
        vendor.setBankAccountNumber("0123456789");
        vendor.setBankName("Vietcombank");
        vendor.setBankAccountHolder("DA NANG SEA TOURS");
        vendor.setVerificationStatus(VerificationStatus.APPROVED);
        vendor.setVerifiedBy(UUID.randomUUID());
        vendor.setVerifiedAt(OffsetDateTime.parse("2026-10-01T10:00:00+07:00"));
        vendor.setRatingAvg(new BigDecimal("4.75"));
        vendor.setRatingCount(12);
        vendor.setBadgeTier(BadgeTier.TOP_RATED);
        vendor.setCreatedAt(OffsetDateTime.parse("2026-09-01T10:00:00+07:00"));
        vendor.setUpdatedAt(OffsetDateTime.parse("2026-10-02T10:00:00+07:00"));
        when(vendorInternalApi.getVendors(VerificationStatus.APPROVED, pageable))
                .thenReturn(new PageImpl<>(List.of(vendor), pageable, 10));

        Page<AdminVendorResponse> response = useCase.execute(VerificationStatus.APPROVED, pageable);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().getFirst()).usingRecursiveComparison()
                .ignoringFields("documents").isEqualTo(vendor);
        assertThat(response.getNumber()).isEqualTo(2);
        assertThat(response.getSize()).isEqualTo(3);
        assertThat(response.getTotalElements()).isEqualTo(10);
        assertThat(response.getTotalPages()).isEqualTo(4);
        assertThat(response.getSort()).isEqualTo(pageable.getSort());
        verify(vendorInternalApi).getVendors(VerificationStatus.APPROVED, pageable);
    }

    @Test
    void forwardsAbsentStatusAndPreservesAnEmptyPage() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(vendorInternalApi.getVendors(null, pageable)).thenReturn(Page.empty(pageable));

        Page<AdminVendorResponse> response = useCase.execute(null, pageable);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getPageable()).isEqualTo(pageable);
        verify(vendorInternalApi).getVendors(null, pageable);
    }
}
