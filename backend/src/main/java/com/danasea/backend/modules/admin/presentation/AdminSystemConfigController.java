package com.danasea.backend.modules.admin.presentation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaCategoryRepository;
import com.danasea.backend.modules.systemconfig.infrastructure.persistence.entities.CommissionRuleJpaEntity;
import com.danasea.backend.modules.systemconfig.infrastructure.persistence.entities.SystemConfigJpaEntity;
import com.danasea.backend.modules.systemconfig.infrastructure.persistence.repositories.JpaCommissionRuleRepository;
import com.danasea.backend.modules.systemconfig.infrastructure.persistence.repositories.JpaSystemConfigRepository;
import com.danasea.backend.security.infrastructure.SecurityUtils;

@RestController
@RequestMapping({"/api/admin/settings", "/api/admin/system-configs"})
@PreAuthorize("hasRole('ADMIN')")
public class AdminSystemConfigController {

    private final JpaSystemConfigRepository repository;
    private final JpaCommissionRuleRepository commissionRuleRepository;
    private final JpaCategoryRepository categoryRepository;

    @Autowired
    public AdminSystemConfigController(
            JpaSystemConfigRepository repository,
            @Autowired(required = false) JpaCommissionRuleRepository commissionRuleRepository,
            @Autowired(required = false) JpaCategoryRepository categoryRepository) {
        this.repository = repository;
        this.commissionRuleRepository = commissionRuleRepository;
        this.categoryRepository = categoryRepository;
    }

    public AdminSystemConfigController(JpaSystemConfigRepository repository) {
        this(repository, null, null);
    }

    public record ConfigDto(String key, String value, String description, OffsetDateTime updatedAt, UUID updatedBy) {
        public static ConfigDto fromEntity(SystemConfigJpaEntity entity) {
            return new ConfigDto(entity.getKey(), entity.getValue(), entity.getDescription(), entity.getUpdatedAt(), entity.getUpdatedBy());
        }
    }

    public record UpdateConfigRequest(String value, String description) {}

    public record CommissionRuleDto(
            UUID id,
            String ruleCode,
            String ruleName,
            String targetType,
            UUID categoryId,
            String categoryName,
            String categorySlug,
            UUID vendorId,
            BigDecimal commissionRate,
            BigDecimal commissionRatePercent,
            Integer durationDays,
            Integer priority,
            Boolean isActive,
            String description,
            OffsetDateTime updatedAt,
            UUID updatedBy
    ) {
        public static CommissionRuleDto fromEntity(
                CommissionRuleJpaEntity entity,
                Map<UUID, String> categoryNames,
                Map<UUID, String> categorySlugs) {
            BigDecimal percent = entity.getCommissionRate() != null
                    ? entity.getCommissionRate().multiply(new BigDecimal("100")).stripTrailingZeros()
                    : BigDecimal.ZERO;
            String catName = entity.getCategoryId() != null ? categoryNames.get(entity.getCategoryId()) : null;
            String catSlug = entity.getCategoryId() != null ? categorySlugs.get(entity.getCategoryId()) : null;
            return new CommissionRuleDto(
                    entity.getId(),
                    entity.getRuleCode(),
                    entity.getRuleName(),
                    entity.getTargetType(),
                    entity.getCategoryId(),
                    catName,
                    catSlug,
                    entity.getVendorId(),
                    entity.getCommissionRate(),
                    percent,
                    entity.getDurationDays(),
                    entity.getPriority(),
                    entity.getIsActive(),
                    entity.getDescription(),
                    entity.getUpdatedAt(),
                    entity.getUpdatedBy()
            );
        }
    }

    public record UpdateCommissionRuleRequest(
            BigDecimal commissionRatePercent,
            Integer durationDays,
            Boolean isActive,
            String description
    ) {}

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

        if ("PLATFORM_COMMISSION_RATE".equalsIgnoreCase(key) || "SAFETY_REBATE_RATE".equalsIgnoreCase(key)) {
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

    @GetMapping("/commission-rules")
    public ResponseEntity<List<CommissionRuleDto>> getCommissionRules() {
        Map<UUID, String> categoryNames = new HashMap<>();
        Map<UUID, String> categorySlugs = new HashMap<>();
        if (categoryRepository != null) {
            categoryRepository.findAll().forEach(cat -> {
                categoryNames.put(cat.getId(), cat.getName());
                categorySlugs.put(cat.getId(), cat.getSlug());
            });
        }

        List<CommissionRuleDto> rules = commissionRuleRepository.findAllByOrderByPriorityDescCreatedAtAsc().stream()
                .map(r -> CommissionRuleDto.fromEntity(r, categoryNames, categorySlugs))
                .toList();
        return ResponseEntity.ok(rules);
    }

    @PutMapping("/commission-rules/{id}")
    @Transactional
    public ResponseEntity<CommissionRuleDto> updateCommissionRule(
            @PathVariable UUID id,
            @RequestBody UpdateCommissionRuleRequest request) {
        UUID actorId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("Admin identity required"));

        CommissionRuleJpaEntity entity = commissionRuleRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Commission rule not found with ID: " + id));

        if (request.commissionRatePercent() != null) {
            BigDecimal percent = request.commissionRatePercent();
            if (percent.compareTo(BigDecimal.ZERO) < 0 || percent.compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("Commission rate percent must be between 0 and 100");
            }
            entity.setCommissionRate(percent.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        }

        if (request.durationDays() != null) {
            if (request.durationDays() <= 0) {
                throw new IllegalArgumentException("Duration days must be positive");
            }
            entity.setDurationDays(request.durationDays());
        }

        if (request.isActive() != null) {
            entity.setIsActive(request.isActive());
        }

        if (request.description() != null) {
            entity.setDescription(request.description().trim());
        }

        entity.setUpdatedBy(actorId);
        CommissionRuleJpaEntity saved = commissionRuleRepository.save(entity);

        Map<UUID, String> categoryNames = new HashMap<>();
        Map<UUID, String> categorySlugs = new HashMap<>();
        if (categoryRepository != null && saved.getCategoryId() != null) {
            categoryRepository.findById(saved.getCategoryId()).ifPresent(cat -> {
                categoryNames.put(cat.getId(), cat.getName());
                categorySlugs.put(cat.getId(), cat.getSlug());
            });
        }

        return ResponseEntity.ok(CommissionRuleDto.fromEntity(saved, categoryNames, categorySlugs));
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
