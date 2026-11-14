package com.danasea.backend.modules.service.infrastructure.persistence.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.danasea.backend.modules.service.infrastructure.persistence.entities.CategoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaCategoryRepository extends JpaRepository<CategoryJpaEntity, UUID> {
    Optional<CategoryJpaEntity> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<CategoryJpaEntity> findAllByIsActiveTrue();
    boolean existsByParentId(UUID parentId);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM commission_rules WHERE category_id = :categoryId)", nativeQuery = true)
    boolean existsCommissionRulesByCategoryId(@Param("categoryId") UUID categoryId);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM category_safety_rules WHERE category_id = :categoryId)", nativeQuery = true)
    boolean existsCategorySafetyRulesByCategoryId(@Param("categoryId") UUID categoryId);
}
