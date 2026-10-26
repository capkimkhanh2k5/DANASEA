package com.danasea.backend.modules.admin.presentation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.danasea.backend.modules.systemconfig.infrastructure.persistence.entities.SystemConfigJpaEntity;
import com.danasea.backend.modules.systemconfig.infrastructure.persistence.repositories.JpaSystemConfigRepository;
import com.danasea.backend.security.infrastructure.SecurityUtils;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/api/admin/settings", "/api/admin/system-configs"})
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSystemConfigController {

    private final JpaSystemConfigRepository repository;

    public record ConfigDto(String key, String value, String description, OffsetDateTime updatedAt, UUID updatedBy) {
        public static ConfigDto fromEntity(SystemConfigJpaEntity entity) {
            return new ConfigDto(entity.getKey(), entity.getValue(), entity.getDescription(), entity.getUpdatedAt(), entity.getUpdatedBy());
        }
    }

    public record UpdateConfigRequest(String value, String description) {}

    @GetMapping
    public ResponseEntity<List<ConfigDto>> getAllConfigs() {
        List<ConfigDto> list = repository.findAll().stream()
                .map(ConfigDto::fromEntity)
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{key}")
    public ResponseEntity<ConfigDto> getConfig(@PathVariable String key) {
        return repository.findByKey(key)
                .map(ConfigDto::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{key}")
    @Transactional
    public ResponseEntity<ConfigDto> updateConfig(
            @PathVariable String key,
            @RequestBody UpdateConfigRequest request) {
        UUID actorId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("Admin identity required"));

        if ("PLATFORM_COMMISSION_RATE".equalsIgnoreCase(key)) {
            validateCommissionRate(request.value());
        } else if ("FLASH_SALE_MAX_SUBSIDY_RATIO".equalsIgnoreCase(key)) {
            validateRatio(request.value());
        } else if ("FLASH_SALE_ESTIMATED_UNIT_COST".equalsIgnoreCase(key) || "FLASH_SALE_MINIMUM_UNIT_MARGIN".equalsIgnoreCase(key)) {
            validateMoney(request.value());
        }

        SystemConfigJpaEntity entity = repository.findByKey(key)
                .orElseGet(() -> {
                    SystemConfigJpaEntity newEntity = new SystemConfigJpaEntity();
                    newEntity.setId(UUID.randomUUID());
                    newEntity.setKey(key);
                    return newEntity;
                });

        entity.setValue(request.value() != null ? request.value().trim() : null);
        if (request.description() != null) {
            entity.setDescription(request.description().trim());
        }
        entity.setUpdatedBy(actorId);

        SystemConfigJpaEntity saved = repository.save(entity);
        return ResponseEntity.ok(ConfigDto.fromEntity(saved));
    }

    private void validateCommissionRate(String val) {
        if (val == null || val.isBlank()) {
            throw new IllegalArgumentException("PLATFORM_COMMISSION_RATE cannot be empty");
        }
        try {
            BigDecimal rate = new BigDecimal(val.trim());
            if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("PLATFORM_COMMISSION_RATE must be between 0% and 100%");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid commission rate format: " + val);
        }
    }

    private void validateRatio(String val) {
        if (val == null || val.isBlank()) {
            throw new IllegalArgumentException("FLASH_SALE_MAX_SUBSIDY_RATIO cannot be empty");
        }
        try {
            BigDecimal ratio = new BigDecimal(val.trim());
            if (ratio.compareTo(BigDecimal.ZERO) < 0 || ratio.compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("FLASH_SALE_MAX_SUBSIDY_RATIO must be between 0 and 1 (or 0% and 100%)");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid subsidy ratio format: " + val);
        }
    }

    private void validateMoney(String val) {
        if (val == null || val.isBlank()) {
            throw new IllegalArgumentException("Amount cannot be empty");
        }
        try {
            BigDecimal amount = new BigDecimal(val.trim());
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Amount must be non-negative");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid money amount format: " + val);
        }
    }
}
