package com.danasea.backend.modules.service.application.usecases;

import java.util.Optional;
import java.util.UUID;

import com.danasea.backend.modules.service.application.ports.output.ActiveServiceCheckPort;
import com.danasea.backend.modules.service.application.ports.output.CategoryRepositoryPort;
import com.danasea.backend.modules.service.domain.models.Category;
import com.danasea.backend.modules.service.infrastructure.mappers.CategoryMapper;
import com.danasea.backend.modules.service.presentation.controllers.AdminCategoryController;
import com.danasea.backend.modules.service.presentation.dtos.CategoryResponse;
import com.danasea.backend.modules.service.presentation.dtos.CategoryTreeResponse;
import com.danasea.backend.modules.service.presentation.dtos.CreateCategoryRequest;
import com.danasea.backend.modules.service.presentation.dtos.UpdateCategoryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CategorySafetyCertUseCaseTest {

    private CategoryRepositoryPort categoryRepositoryPort;
    private ActiveServiceCheckPort activeServiceCheckPort;
    private CreateCategoryUseCase createCategoryUseCase;
    private UpdateCategoryUseCase updateCategoryUseCase;
    private DeactivateCategoryUseCase deactivateCategoryUseCase;
    private GetCategoryTreeUseCase getCategoryTreeUseCase;
    private CategoryMapper categoryMapper;
    private AdminCategoryController adminController;

    @BeforeEach
    void setUp() {
        categoryRepositoryPort = mock(CategoryRepositoryPort.class);
        activeServiceCheckPort = mock(ActiveServiceCheckPort.class);
        categoryMapper = new CategoryMapper();

        createCategoryUseCase = new CreateCategoryUseCase(categoryRepositoryPort);
        updateCategoryUseCase = new UpdateCategoryUseCase(categoryRepositoryPort, activeServiceCheckPort);
        deactivateCategoryUseCase = new DeactivateCategoryUseCase(categoryRepositoryPort, activeServiceCheckPort);
        getCategoryTreeUseCase = new GetCategoryTreeUseCase(categoryRepositoryPort);

        adminController = new AdminCategoryController(
                getCategoryTreeUseCase,
                createCategoryUseCase,
                updateCategoryUseCase,
                deactivateCategoryUseCase,
                categoryMapper
        );
    }

    @Test
    @DisplayName("Create with requiresSafetyCert = true should save true")
    void createWithSafetyCertTrue_shouldSaveTrue() {
        when(categoryRepositoryPort.existsBySlug("diving")).thenReturn(false);
        when(categoryRepositoryPort.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateCategoryCommand command = new CreateCategoryCommand(
                "Lặn biển", "Scuba Diving", "diving", null, "diving.png", true
        );
        Category created = createCategoryUseCase.execute(command);

        assertNotNull(created);
        assertTrue(created.getRequiresSafetyCert());
    }

    @Test
    @DisplayName("Create with requiresSafetyCert = false should save false")
    void createWithSafetyCertFalse_shouldSaveFalse() {
        when(categoryRepositoryPort.existsBySlug("sightseeing")).thenReturn(false);
        when(categoryRepositoryPort.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateCategoryCommand command = new CreateCategoryCommand(
                "Ngắm cảnh", "Sightseeing", "sightseeing", null, "sight.png", false
        );
        Category created = createCategoryUseCase.execute(command);

        assertNotNull(created);
        assertFalse(created.getRequiresSafetyCert());
    }

    @Test
    @DisplayName("Create without sending requiresSafetyCert (null) should default to false")
    void createWithoutSafetyCert_shouldDefaultToFalse() {
        when(categoryRepositoryPort.existsBySlug("walking")).thenReturn(false);
        when(categoryRepositoryPort.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateCategoryCommand command = new CreateCategoryCommand(
                "Đi bộ bờ biển", "Walking", "walking", null, "walk.png", null
        );
        Category created = createCategoryUseCase.execute(command);

        assertNotNull(created);
        assertFalse(created.getRequiresSafetyCert());
    }

    @Test
    @DisplayName("Update false -> true should persist true")
    void updateFalseToTrue_shouldPersistTrue() {
        UUID id = UUID.randomUUID();
        Category existing = Category.builder()
                .id(id)
                .name("Thể thao nước")
                .slug("water-sports")
                .isActive(true)
                .requiresSafetyCert(false)
                .build();

        when(categoryRepositoryPort.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
        when(categoryRepositoryPort.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCategoryCommand command = new UpdateCategoryCommand(
                null, null, null, null, null, null, true
        );
        Category updated = updateCategoryUseCase.execute(id, command);

        assertNotNull(updated);
        assertTrue(updated.getRequiresSafetyCert());
    }

    @Test
    @DisplayName("Update true -> false should persist false without being confused with omitted")
    void updateTrueToFalse_shouldPersistFalse() {
        UUID id = UUID.randomUUID();
        Category existing = Category.builder()
                .id(id)
                .name("Lướt sóng")
                .slug("surfing")
                .isActive(true)
                .requiresSafetyCert(true)
                .build();

        when(categoryRepositoryPort.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
        when(categoryRepositoryPort.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCategoryCommand command = new UpdateCategoryCommand(
                null, null, null, null, null, null, false
        );
        Category updated = updateCategoryUseCase.execute(id, command);

        assertNotNull(updated);
        assertFalse(updated.getRequiresSafetyCert());
    }

    @Test
    @DisplayName("Update without requiresSafetyCert (null) should preserve existing value")
    void updateWithNullSafetyCert_shouldPreserveExistingValue() {
        UUID idTrue = UUID.randomUUID();
        Category existingTrue = Category.builder()
                .id(idTrue)
                .name("Lặn ống thở")
                .slug("snorkeling")
                .isActive(true)
                .requiresSafetyCert(true)
                .build();

        when(categoryRepositoryPort.findByIdForUpdate(idTrue)).thenReturn(Optional.of(existingTrue));
        when(categoryRepositoryPort.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCategoryCommand cmd1 = new UpdateCategoryCommand(
                "Lặn ống thở VIP", null, null, null, null, null, null
        );
        Category updated1 = updateCategoryUseCase.execute(idTrue, cmd1);
        assertTrue(updated1.getRequiresSafetyCert(), "Must preserve true when null was provided");

        UUID idFalse = UUID.randomUUID();
        Category existingFalse = Category.builder()
                .id(idFalse)
                .name("Chụp ảnh")
                .slug("photography")
                .isActive(true)
                .requiresSafetyCert(false)
                .build();

        when(categoryRepositoryPort.findByIdForUpdate(idFalse)).thenReturn(Optional.of(existingFalse));
        when(categoryRepositoryPort.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCategoryCommand cmd2 = new UpdateCategoryCommand(
                "Chụp ảnh cưới", null, null, null, null, null, null
        );
        Category updated2 = updateCategoryUseCase.execute(idFalse, cmd2);
        assertFalse(updated2.getRequiresSafetyCert(), "Must preserve false when null was provided");
    }

    @Test
    @DisplayName("Admin Controller POST and PATCH properly map requiresSafetyCert in request and response")
    void adminControllerPostAndPatch_shouldMapSafetyCertCorrectly() {
        when(categoryRepositoryPort.existsBySlug("jetski")).thenReturn(false);
        when(categoryRepositoryPort.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. POST with true
        CreateCategoryRequest createReq = new CreateCategoryRequest(
                "Mô tô nước", "Jet Ski", "jetski", null, "jetski.png", true
        );
        ResponseEntity<CategoryResponse> createRes = adminController.createCategory(createReq);
        assertEquals(HttpStatus.CREATED, createRes.getStatusCode());
        assertNotNull(createRes.getBody());
        assertTrue(createRes.getBody().requiresSafetyCert());

        // 2. PATCH with false
        UUID id = UUID.randomUUID();
        Category existing = Category.builder()
                .id(id)
                .name("Mô tô nước")
                .slug("jetski")
                .isActive(true)
                .requiresSafetyCert(true)
                .build();
        when(categoryRepositoryPort.findByIdForUpdate(id)).thenReturn(Optional.of(existing));

        UpdateCategoryRequest patchReq = new UpdateCategoryRequest(
                null, null, null, null, null, null, false
        );
        ResponseEntity<CategoryResponse> patchRes = adminController.updateCategory(id, patchReq);
        assertEquals(HttpStatus.OK, patchRes.getStatusCode());
        assertNotNull(patchRes.getBody());
        assertFalse(patchRes.getBody().requiresSafetyCert());
    }

    @Test
    @DisplayName("CategoryMapper and GetCategoryTreeUseCase include requiresSafetyCert")
    void treeResponseAndMapper_shouldIncludeRequiresSafetyCert() {
        UUID id = UUID.randomUUID();
        Category cat = Category.builder()
                .id(id)
                .name("Lặn biển sâu")
                .nameEn("Deep Sea Diving")
                .slug("deep-sea-diving")
                .isActive(true)
                .requiresSafetyCert(true)
                .build();

        CategoryResponse res = categoryMapper.toResponse(cat);
        assertTrue(res.requiresSafetyCert());

        CategoryTreeResponse tree = categoryMapper.toTreeResponse(cat, null);
        assertTrue(tree.requiresSafetyCert());
    }
}
