package com.danasea.backend.modules.systemconfig.infrastructure.persistence.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.danasea.backend.modules.systemconfig.infrastructure.persistence.entities.CommissionRuleJpaEntity;

@Repository
public interface JpaCommissionRuleRepository extends JpaRepository<CommissionRuleJpaEntity, UUID> {

    Optional<CommissionRuleJpaEntity> findByRuleCode(String ruleCode);

    List<CommissionRuleJpaEntity> findByTargetTypeAndIsActiveOrderByPriorityDesc(String targetType, Boolean isActive);

    Optional<CommissionRuleJpaEntity> findFirstByTargetTypeAndCategoryIdAndIsActiveTrueOrderByPriorityDesc(String targetType, UUID categoryId);

    Optional<CommissionRuleJpaEntity> findFirstByTargetTypeAndIsActiveTrueOrderByPriorityDesc(String targetType);

    List<CommissionRuleJpaEntity> findAllByOrderByPriorityDescCreatedAtAsc();
}
