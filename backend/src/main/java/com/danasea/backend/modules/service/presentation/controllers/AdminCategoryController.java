package com.danasea.backend.modules.service.presentation.controllers;

import java.util.List;
import java.util.UUID;

import com.danasea.backend.modules.service.application.usecases.CreateCategoryUseCase;
import com.danasea.backend.modules.service.application.usecases.DeactivateCategoryUseCase;
import com.danasea.backend.modules.service.application.usecases.DeleteCategoryUseCase;
import com.danasea.backend.modules.service.application.usecases.GetCategoryTreeUseCase;
import com.danasea.backend.modules.service.application.usecases.UpdateCategoryUseCase;
import com.danasea.backend.modules.service.application.usecases.UpdateCategoryCommand;
import com.danasea.backend.modules.service.domain.models.Category;
import com.danasea.backend.modules.service.infrastructure.mappers.CategoryMapper;
import com.danasea.backend.modules.service.presentation.dtos.CategoryResponse;
import com.danasea.backend.modules.service.presentation.dtos.CategoryTreeResponse;
import com.danasea.backend.modules.service.presentation.dtos.CreateCategoryRequest;
import com.danasea.backend.modules.service.presentation.dtos.UpdateCategoryRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/categories")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCategoryController {

    private final GetCategoryTreeUseCase getCategoryTreeUseCase;
    private final CreateCategoryUseCase createCategoryUseCase;
    private final UpdateCategoryUseCase updateCategoryUseCase;
    private final DeactivateCategoryUseCase deactivateCategoryUseCase;
    private final DeleteCategoryUseCase deleteCategoryUseCase;
    private final CategoryMapper categoryMapper;

    @Autowired
    public AdminCategoryController(
            GetCategoryTreeUseCase getCategoryTreeUseCase,
            CreateCategoryUseCase createCategoryUseCase,
            UpdateCategoryUseCase updateCategoryUseCase,
            DeactivateCategoryUseCase deactivateCategoryUseCase,
            DeleteCategoryUseCase deleteCategoryUseCase,
            CategoryMapper categoryMapper
    ) {
        this.getCategoryTreeUseCase = getCategoryTreeUseCase;
        this.createCategoryUseCase = createCategoryUseCase;
        this.updateCategoryUseCase = updateCategoryUseCase;
        this.deactivateCategoryUseCase = deactivateCategoryUseCase;
        this.deleteCategoryUseCase = deleteCategoryUseCase;
        this.categoryMapper = categoryMapper;
    }

    public AdminCategoryController(
            GetCategoryTreeUseCase getCategoryTreeUseCase,
            CreateCategoryUseCase createCategoryUseCase,
            UpdateCategoryUseCase updateCategoryUseCase,
            DeactivateCategoryUseCase deactivateCategoryUseCase,
            CategoryMapper categoryMapper
    ) {
        this(getCategoryTreeUseCase, createCategoryUseCase, updateCategoryUseCase, deactivateCategoryUseCase, null, categoryMapper);
    }

    @GetMapping
    public ResponseEntity<List<CategoryTreeResponse>> getAdminCategoryTree() {
        return ResponseEntity.ok(getCategoryTreeUseCase.execute(true));
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        Category category = request.requiresSafetyCert() != null
                ? createCategoryUseCase.execute(
                        request.name(),
                        request.nameEn(),
                        request.slug(),
                        request.parentId(),
                        request.iconUrl(),
                        request.requiresSafetyCert()
                )
                : createCategoryUseCase.execute(
                        request.name(),
                        request.nameEn(),
                        request.slug(),
                        request.parentId(),
                        request.iconUrl()
                );
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryMapper.toResponse(category));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCategoryRequest request) {
        Category category = updateCategoryUseCase.execute(
                id,
                new UpdateCategoryCommand(
                        request.name(), request.nameEn(), request.slug(),
                        request.parentId(), request.iconUrl(), request.isActive(),
                        request.requiresSafetyCert())
        );
        return ResponseEntity.ok(categoryMapper.toResponse(category));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<CategoryResponse> deactivateCategory(@PathVariable UUID id) {
        Category category = deactivateCategoryUseCase.execute(id);
        return ResponseEntity.ok(categoryMapper.toResponse(category));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        deleteCategoryUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }
}
