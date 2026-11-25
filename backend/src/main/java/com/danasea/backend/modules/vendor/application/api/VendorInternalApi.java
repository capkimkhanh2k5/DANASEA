package com.danasea.backend.modules.vendor.application.api;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.domain.models.DocType;
import com.danasea.backend.modules.vendor.domain.models.VendorDocument;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public interface VendorInternalApi {
    Optional<Vendor> findByUserId(UUID userId);

    Optional<Vendor> findById(UUID vendorId);

    default Map<UUID, BadgeTier> findBadgeTiersByVendorIds(Set<UUID> vendorIds) {
        return Collections.emptyMap();
    }

    boolean isVendorApproved(UUID vendorId);

    Vendor saveVendor(Vendor vendor);

    Page<Vendor> getVendors(VerificationStatus status, Pageable pageable);

    Set<DocType> getApprovedDocumentTypes(UUID vendorId);

    Optional<VendorDocument> findDocumentForReview(UUID vendorId, UUID documentId);

    VendorDocument saveDocument(VendorDocument document);
}
