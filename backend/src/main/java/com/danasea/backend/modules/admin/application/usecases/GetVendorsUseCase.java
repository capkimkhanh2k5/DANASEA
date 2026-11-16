package com.danasea.backend.modules.admin.application.usecases;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.admin.presentation.dtos.AdminVendorResponse;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;

@Service
public class GetVendorsUseCase {

    private final VendorInternalApi vendorInternalApi;
    private final AccountInternalApi accountInternalApi;

    @Autowired
    public GetVendorsUseCase(VendorInternalApi vendorInternalApi,
                             @Autowired(required = false) AccountInternalApi accountInternalApi) {
        this.vendorInternalApi = vendorInternalApi;
        this.accountInternalApi = accountInternalApi;
    }

    public GetVendorsUseCase(VendorInternalApi vendorInternalApi) {
        this(vendorInternalApi, null);
    }

    public Page<AdminVendorResponse> execute(VerificationStatus status, Pageable pageable) {
        Page<Vendor> vendors = vendorInternalApi.getVendors(status, pageable);

        Map<UUID, User> usersMap = new HashMap<>();
        if (accountInternalApi != null) {
            Set<UUID> verifierIds = vendors.getContent().stream()
                    .map(Vendor::getVerifiedBy)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!verifierIds.isEmpty()) {
                usersMap = accountInternalApi.findUsersByIds(verifierIds);
            }
        }
        final Map<UUID, User> finalUsersMap = usersMap;

        return vendors.map(vendor -> {
            String verifiedByName = null;
            if (vendor.getVerifiedBy() != null && finalUsersMap.containsKey(vendor.getVerifiedBy())) {
                verifiedByName = finalUsersMap.get(vendor.getVerifiedBy()).getFullName();
            }
            return mapToResponse(vendor, verifiedByName);
        });
    }

    private AdminVendorResponse mapToResponse(Vendor vendor, String verifiedByName) {
        return AdminVendorResponse.builder()
                .id(vendor.getId())
                .userId(vendor.getUserId())
                .businessName(vendor.getBusinessName())
                .taxCode(vendor.getTaxCode())
                .address(vendor.getAddress())
                .bankAccountNumber(vendor.getBankAccountNumber())
                .bankName(vendor.getBankName())
                .bankAccountHolder(vendor.getBankAccountHolder())
                .verificationStatus(vendor.getVerificationStatus())
                .verifiedBy(vendor.getVerifiedBy())
                .verifiedByName(verifiedByName)
                .verifiedAt(vendor.getVerifiedAt())
                .ratingAvg(vendor.getRatingAvg())
                .ratingCount(vendor.getRatingCount())
                .badgeTier(vendor.getBadgeTier())
                .createdAt(vendor.getCreatedAt())
                .updatedAt(vendor.getUpdatedAt())
                .build();
    }
}
