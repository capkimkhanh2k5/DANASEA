package com.danasea.backend.modules.admin.application.usecases;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.admin.presentation.dtos.AdminVendorResponse;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.application.usecases.GetVendorDocumentsUseCase;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorNotFoundException;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.modules.vendor.domain.models.VendorDocument;
import com.danasea.backend.modules.vendor.presentation.dtos.VendorDocumentResponse;

@Service
public class GetVendorDetailUseCase {

    private final VendorInternalApi vendorInternalApi;
    private final GetVendorDocumentsUseCase getVendorDocumentsUseCase;
    private final AccountInternalApi accountInternalApi;

    public GetVendorDetailUseCase(VendorInternalApi vendorInternalApi,
                                  GetVendorDocumentsUseCase getVendorDocumentsUseCase,
                                  @Autowired(required = false) AccountInternalApi accountInternalApi) {
        this.vendorInternalApi = vendorInternalApi;
        this.getVendorDocumentsUseCase = getVendorDocumentsUseCase;
        this.accountInternalApi = accountInternalApi;
    }

    public GetVendorDetailUseCase(VendorInternalApi vendorInternalApi,
                                  GetVendorDocumentsUseCase getVendorDocumentsUseCase) {
        this(vendorInternalApi, getVendorDocumentsUseCase, null);
    }

    public AdminVendorResponse execute(UUID vendorId) {
        Vendor vendor = vendorInternalApi.findById(vendorId)
                .orElseThrow(() -> new VendorNotFoundException("Vendor not found with id: " + vendorId));
        
        List<VendorDocument> documents = getVendorDocumentsUseCase.execute(vendor.getUserId());

        Map<UUID, User> usersMap = Map.of();
        if (accountInternalApi != null) {
            Set<UUID> userIdsToFetch = new HashSet<>();
            if (vendor.getVerifiedBy() != null) {
                userIdsToFetch.add(vendor.getVerifiedBy());
            }
            for (VendorDocument doc : documents) {
                if (doc.getReviewedBy() != null) {
                    userIdsToFetch.add(doc.getReviewedBy());
                }
            }
            if (!userIdsToFetch.isEmpty()) {
                usersMap = accountInternalApi.findUsersByIds(userIdsToFetch);
            }
        }
        final Map<UUID, User> finalUsersMap = usersMap;

        List<VendorDocumentResponse> documentResponses = documents.stream()
                .map(doc -> {
                    String reviewerName = null;
                    if (doc.getReviewedBy() != null && finalUsersMap.containsKey(doc.getReviewedBy())) {
                        reviewerName = finalUsersMap.get(doc.getReviewedBy()).getFullName();
                    }
                    return VendorDocumentResponse.fromDomain(doc, doc.getFileUrl(), reviewerName);
                })
                .toList();
                
        String verifiedByName = null;
        if (vendor.getVerifiedBy() != null && finalUsersMap.containsKey(vendor.getVerifiedBy())) {
            verifiedByName = finalUsersMap.get(vendor.getVerifiedBy()).getFullName();
        }

        return mapToResponse(vendor, documentResponses, verifiedByName);
    }

    private AdminVendorResponse mapToResponse(Vendor vendor, List<VendorDocumentResponse> documents, String verifiedByName) {
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
                .documents(documents)
                .build();
    }
}
