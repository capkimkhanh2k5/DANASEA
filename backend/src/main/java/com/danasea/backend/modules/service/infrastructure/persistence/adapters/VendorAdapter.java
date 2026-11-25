package com.danasea.backend.modules.service.infrastructure.persistence.adapters;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class VendorAdapter implements VendorPort {

    private final VendorInternalApi vendorInternalApi;

    @Override
    public Optional<Vendor> findByUserId(UUID userId) {
        return vendorInternalApi.findByUserId(userId);
    }

    @Override
    public Optional<Vendor> findById(UUID vendorId) {
        return vendorInternalApi.findById(vendorId);
    }

    @Override
    public boolean isVendorApproved(UUID vendorId) {
        return vendorInternalApi.isVendorApproved(vendorId);
    }

    @Override
    public Map<UUID, BadgeTier> findBadgeTiersByVendorIds(Set<UUID> vendorIds) {
        return vendorInternalApi.findBadgeTiersByVendorIds(vendorIds);
    }
}
