package com.danasea.backend.modules.systemconfig.infrastructure.persistence.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.danasea.backend.modules.systemconfig.infrastructure.persistence.entities.CommissionRuleJpaEntity;

@Repository
public interface JpaCommissionRuleRepository extends JpaRepository<CommissionRuleJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rule from CommissionRuleJpaEntity rule where rule.id = :id")
    Optional<CommissionRuleJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    Optional<CommissionRuleJpaEntity> findByRuleCode(String ruleCode);

    List<CommissionRuleJpaEntity> findByTargetTypeAndIsActiveOrderByPriorityDesc(String targetType, Boolean isActive);

    Optional<CommissionRuleJpaEntity> findFirstByTargetTypeAndCategoryIdAndIsActiveTrueOrderByPriorityDesc(String targetType, UUID categoryId);

    Optional<CommissionRuleJpaEntity> findFirstByTargetTypeAndIsActiveTrueOrderByPriorityDesc(String targetType);

    List<CommissionRuleJpaEntity> findAllByOrderByPriorityDescCreatedAtAsc();
}
