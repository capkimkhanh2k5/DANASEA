package com.danasea.backend.modules.vendor.application.usecases;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.vendor.domain.exceptions.UserLockedException;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorAlreadyExistsException;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorNotFoundException;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.infrastructure.mappers.VendorMapper;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RegisterVendorProfileUseCase {

    private final JpaVendorRepository jpaVendorRepository;
    private final AccountInternalApi accountInternalApi;
    private final VendorMapper vendorMapper;

    @Transactional
    public Vendor execute(UUID userId, RegisterVendorProfileCommand command) {
        User user = accountInternalApi.findUserById(userId)
                .orElseThrow(() -> new VendorNotFoundException("User not found"));

        if (Boolean.TRUE.equals(user.getIsLocked())) {
            throw new UserLockedException();
        }

        if (user.getRole() != Role.VENDOR) {
            throw new AccessDeniedException("Direct vendor profile registration is not permitted for non-vendors. Please submit a partner application.");
        }

        if (jpaVendorRepository.existsByUserId(userId)) {
            throw new VendorAlreadyExistsException();
        }

        VendorJpaEntity entity = new VendorJpaEntity();
        entity.setUserId(userId);
        entity.setBusinessName(command.businessName());
        entity.setTaxCode(command.taxCode());
        entity.setAddress(command.address());
        entity.setBankAccountNumber(command.bankAccountNumber());
        entity.setBankName(command.bankName());
        entity.setBankAccountHolder(command.bankAccountHolder());
        entity.setVerificationStatus(VerificationStatus.PENDING);
        entity.setBadgeTier(BadgeTier.NONE);
        entity.setRatingAvg(BigDecimal.ZERO);
        entity.setRatingCount(0);

        VendorJpaEntity savedEntity = jpaVendorRepository.save(entity);

        return vendorMapper.toDomain(savedEntity);
    }
}
