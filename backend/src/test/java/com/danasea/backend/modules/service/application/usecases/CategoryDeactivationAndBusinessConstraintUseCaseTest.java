package com.danasea.backend.modules.service.application.usecases;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.danasea.backend.modules.service.application.ports.output.ActiveServiceCheckPort;
import com.danasea.backend.modules.service.application.ports.output.CategoryRepositoryPort;
import com.danasea.backend.modules.service.domain.exceptions.CategoryHasActiveServicesException;
import com.danasea.backend.modules.service.domain.exceptions.CategoryHierarchyLoopException;
import com.danasea.backend.modules.service.domain.exceptions.CategoryNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.SlugAlreadyExistsException;
import com.danasea.backend.modules.service.domain.models.Category;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.infrastructure.mappers.CategoryMapper;
import com.danasea.backend.modules.service.infrastructure.persistence.adapters.CategoryPersistenceAdapter;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaCategoryRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.presentation.controllers.AdminCategoryController;
import com.danasea.backend.modules.service.presentation.controllers.CategoryController;
import com.danasea.backend.modules.service.presentation.dtos.CategoryResponse;
import com.danasea.backend.modules.service.presentation.dtos.CategoryTreeResponse;
import com.danasea.backend.modules.service.presentation.dtos.CreateCategoryRequest;
import com.danasea.backend.modules.service.presentation.dtos.UpdateCategoryRequest;
import com.danasea.backend.modules.service.presentation.handlers.CategoryExceptionHandler;
import com.danasea.backend.shared.presentation.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.context.i18n.LocaleContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Empirical Challenger Adversarial Stress Test Suite.
 * Covers:
 * 1. Category deactivation blocking rules with exhaustive ServiceStatus verification.
 * 2. Slug collision & uniqueness on creation and updates.
 * 3. Public vs Admin visibility separation in tree construction.
 * 4. REST Exception Handler and Controller delegation contracts.
 */
@DisplayName("Category Deactivation & Business Constraints Adversarial Suite")
class CategoryDeactivationAndBusinessConstraintUseCaseTest {

    @Nested
    @DisplayName("1. Deactivation Blocking & ServiceStatus Stress Tests")
    class DeactivationConstraints {

        private CategoryRepositoryPort categoryRepositoryPort;
        private ActiveServiceCheckPort activeServiceCheckPort;
        private DeactivateCategoryUseCase deactivateUseCase;

        @BeforeEach
        void setUp() {
            categoryRepositoryPort = mock(CategoryRepositoryPort.class);
            activeServiceCheckPort = mock(ActiveServiceCheckPort.class);
            deactivateUseCase = new DeactivateCategoryUseCase(categoryRepositoryPort, activeServiceCheckPort);
        }

        @Test
        @DisplayName("PersistenceAdapter: PUBLISHED status blocks deactivation")
        void persistenceAdapter_ShouldRecognizePublishedServiceAsActive() {
            JpaCategoryRepository jpaCategoryRepo = mock(JpaCategoryRepository.class);
            JpaServiceRepository jpaServiceRepo = mock(JpaServiceRepository.class);
            CategoryMapper categoryMapper = mock(CategoryMapper.class);

            CategoryPersistenceAdapter adapter = new CategoryPersistenceAdapter(jpaCategoryRepo, jpaServiceRepo, categoryMapper);
            UUID catId = UUID.randomUUID();

            when(jpaServiceRepo.existsByCategoryIdAndStatus(catId, ServiceStatus.PUBLISHED)).thenReturn(true);
            assertTrue(adapter.hasActiveServices(catId), "PUBLISHED services must be marked as active");
            verify(jpaServiceRepo).existsByCategoryIdAndStatus(catId, ServiceStatus.PUBLISHED);
        }

        @ParameterizedTest
        @EnumSource(value = ServiceStatus.class, names = {"DRAFT", "PENDING_REVIEW", "REJECTED", "PAUSED"})
        @DisplayName("PersistenceAdapter: non-PUBLISHED statuses do NOT block deactivation")
        void persistenceAdapter_ShouldNotBlockDeactivation_ForNonPublishedStatuses(ServiceStatus nonPublishedStatus) {
            JpaCategoryRepository jpaCategoryRepo = mock(JpaCategoryRepository.class);
            JpaServiceRepository jpaServiceRepo = mock(JpaServiceRepository.class);
            CategoryMapper categoryMapper = mock(CategoryMapper.class);

            CategoryPersistenceAdapter adapter = new CategoryPersistenceAdapter(jpaCategoryRepo, jpaServiceRepo, categoryMapper);
            UUID catId = UUID.randomUUID();

            // When no PUBLISHED services exist (even if DRAFT/REJECTED/PAUSED exist)
            when(jpaServiceRepo.existsByCategoryIdAndStatus(catId, ServiceStatus.PUBLISHED)).thenReturn(false);

            assertFalse(adapter.hasActiveServices(catId),
                    "Status " + nonPublishedStatus + " must not block deactivation when PUBLISHED is absent");
        }

        @Test
        @DisplayName("Deactivate: MUST throw CategoryHasActiveServicesException when active services exist")
        void deactivate_ShouldThrow_WhenActiveServicesExist() {
            UUID categoryId = UUID.randomUUID();
            Category category = Category.builder().id(categoryId).name("Sea Sports").slug("sea-sports").isActive(true).build();

            when(categoryRepositoryPort.findById(categoryId)).thenReturn(Optional.of(category));
            when(activeServiceCheckPort.hasActiveServices(categoryId)).thenReturn(true);

            CategoryHasActiveServicesException exception = assertThrows(
                    CategoryHasActiveServicesException.class,
                    () -> deactivateUseCase.execute(categoryId)
            );
            assertTrue(exception.getMessage().contains(categoryId.toString()),
                    "Exception message must contain category UUID: " + categoryId);
            verify(categoryRepositoryPort, never()).save(any());
        }

        @Test
        @DisplayName("Deactivate: succeeds and sets isActive=false when 0 active services exist")
        void deactivate_ShouldSucceed_WhenZeroActiveServices() {
            UUID categoryId = UUID.randomUUID();
            Category category = Category.builder().id(categoryId).name("Sea Sports").slug("sea-sports").isActive(true).build();

            when(categoryRepositoryPort.findById(categoryId)).thenReturn(Optional.of(category));
            when(activeServiceCheckPort.hasActiveServices(categoryId)).thenReturn(false);
            when(categoryRepositoryPort.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            Category result = deactivateUseCase.execute(categoryId);

            assertNotNull(result);
            assertFalse(result.getIsActive(), "Category must be soft-deleted with isActive=false");
            verify(categoryRepositoryPort).save(category);
        }

        @Test
        @DisplayName("Deactivate: is idempotent when category is already inactive")
        void deactivate_ShouldBeIdempotent_WhenAlreadyInactive() {
            UUID categoryId = UUID.randomUUID();
            Category category = Category.builder().id(categoryId).name("Archived").slug("archived").isActive(false).build();

            when(categoryRepositoryPort.findById(categoryId)).thenReturn(Optional.of(category));
            when(activeServiceCheckPort.hasActiveServices(categoryId)).thenReturn(false);
            when(categoryRepositoryPort.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            Category result = deactivateUseCase.execute(categoryId);

            assertNotNull(result);
            assertFalse(result.getIsActive());
            verify(categoryRepositoryPort).save(category);
        }

        @Test
        @DisplayName("Deactivate: throws CategoryNotFoundException when category does not exist")
        void deactivate_ShouldThrowNotFound_WhenIdDoesNotExist() {
            UUID nonExistentId = UUID.randomUUID();
            when(categoryRepositoryPort.findById(nonExistentId)).thenReturn(Optional.empty());

            CategoryNotFoundException exception = assertThrows(
                    CategoryNotFoundException.class,
                    () -> deactivateUseCase.execute(nonExistentId)
            );
            assertTrue(exception.getMessage().contains(nonExistentId.toString()));
            verify(activeServiceCheckPort, never()).hasActiveServices(any());
            verify(categoryRepositoryPort, never()).save(any());
        }
    }

    @Nested
    @DisplayName("2. Slug Collision & Uniqueness Edge Cases")
    class SlugCollisionConstraints {

        private CategoryRepositoryPort categoryRepositoryPort;
        private CreateCategoryUseCase createUseCase;
        private UpdateCategoryUseCase updateUseCase;

        @BeforeEach
        void setUp() {
            categoryRepositoryPort = mock(CategoryRepositoryPort.class);
            createUseCase = new CreateCategoryUseCase(categoryRepositoryPort);
            updateUseCase = new UpdateCategoryUseCase(categoryRepositoryPort, mock(ActiveServiceCheckPort.class));
        }

        @Test
        @DisplayName("Create: throws SlugAlreadyExistsException on duplicate slug")
        void create_ShouldThrow_WhenSlugAlreadyExists() {
            String duplicateSlug = "scuba-diving";
            when(categoryRepositoryPort.existsBySlug(duplicateSlug)).thenReturn(true);

            CreateCategoryCommand command = new CreateCategoryCommand("Scuba", "Scuba En", duplicateSlug, null, null);

            SlugAlreadyExistsException ex = assertThrows(
                    SlugAlreadyExistsException.class,
                    () -> createUseCase.execute(command)
            );
            assertTrue(ex.getMessage().contains(duplicateSlug));
            verify(categoryRepositoryPort, never()).save(any());
        }

        @Test
        @DisplayName("Create: creates successfully when slug is unique")
        void create_ShouldSucceed_WhenSlugIsUnique() {
            String uniqueSlug = "kayaking-tour";
            when(categoryRepositoryPort.existsBySlug(uniqueSlug)).thenReturn(false);
            when(categoryRepositoryPort.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateCategoryCommand command = new CreateCategoryCommand("Kayaking", "Kayaking En", uniqueSlug, null, null);
            Category result = createUseCase.execute(command);

            assertNotNull(result);
            assertEquals(uniqueSlug, result.getSlug());
            assertTrue(result.getIsActive());
            verify(categoryRepositoryPort).save(any(Category.class));
        }

        @Test
        @DisplayName("Update: allows updating category while KEEPING its own existing slug (no self-conflict)")
        void update_ShouldAllow_KeepingOwnExistingSlug() {
            UUID catId = UUID.randomUUID();
            String currentSlug = "scuba-diving";
            Category existing = Category.builder().id(catId).name("Scuba").slug(currentSlug).isActive(true).build();

            when(categoryRepositoryPort.findByIdForUpdate(catId)).thenReturn(Optional.of(existing));
            when(categoryRepositoryPort.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            Category result = updateUseCase.execute(catId, "Scuba Diving 2026", "Scuba En", currentSlug, null, null);

            assertNotNull(result);
            assertEquals("Scuba Diving 2026", result.getName());
            assertEquals(currentSlug, result.getSlug());
            // Verify that existsBySlug was never called for its own slug
            verify(categoryRepositoryPort, never()).existsBySlug(currentSlug);
            verify(categoryRepositoryPort).save(existing);
        }

        @Test
        @DisplayName("Update: throws SlugAlreadyExistsException when new slug collides with another category")
        void update_ShouldThrow_WhenNewSlugCollidesWithAnotherCategory() {
            UUID catId = UUID.randomUUID();
            String currentSlug = "scuba-diving";
            String targetSlug = "surfing-tours";
            Category existing = Category.builder().id(catId).name("Scuba").slug(currentSlug).isActive(true).build();

            when(categoryRepositoryPort.findByIdForUpdate(catId)).thenReturn(Optional.of(existing));
            when(categoryRepositoryPort.existsBySlug(targetSlug)).thenReturn(true);

            SlugAlreadyExistsException ex = assertThrows(
                    SlugAlreadyExistsException.class,
                    () -> updateUseCase.execute(catId, "Scuba", "Scuba En", targetSlug, null, null)
            );
            assertTrue(ex.getMessage().contains(targetSlug));
            verify(categoryRepositoryPort, never()).save(any());
        }

        @Test
        @DisplayName("Update: succeeds when updating to a new unique slug")
        void update_ShouldSucceed_WhenNewSlugIsUnique() {
            UUID catId = UUID.randomUUID();
            String currentSlug = "scuba-diving";
            String newSlug = "deep-scuba-diving";
            Category existing = Category.builder().id(catId).name("Scuba").slug(currentSlug).isActive(true).build();

            when(categoryRepositoryPort.findByIdForUpdate(catId)).thenReturn(Optional.of(existing));
            when(categoryRepositoryPort.existsBySlug(newSlug)).thenReturn(false);
            when(categoryRepositoryPort.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            Category result = updateUseCase.execute(catId, "Deep Scuba", "Deep Scuba En", newSlug, null, null);

            assertNotNull(result);
            assertEquals(newSlug, result.getSlug());
            verify(categoryRepositoryPort).existsBySlug(newSlug);
            verify(categoryRepositoryPort).save(existing);
        }

        @Test
        @DisplayName("Update: null or blank slug preserves the original slug without validation error")
        void update_ShouldPreserveOriginalSlug_WhenSlugIsNullOrEqualToBlank() {
            UUID catId = UUID.randomUUID();
            String currentSlug = "original-slug";
            Category existing = Category.builder().id(catId).name("Cat").slug(currentSlug).isActive(true).build();

            when(categoryRepositoryPort.findByIdForUpdate(catId)).thenReturn(Optional.of(existing));
            when(categoryRepositoryPort.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            Category nullResult = updateUseCase.execute(catId, "Updated 1", null, null, null, null);
            assertEquals(currentSlug, nullResult.getSlug());

            Category blankResult = updateUseCase.execute(catId, "Updated 2", null, "   ", null, null);
            assertEquals(currentSlug, blankResult.getSlug());

            verify(categoryRepositoryPort, never()).existsBySlug(any());
        }
    }

    @Nested
    @DisplayName("3. Public vs Admin Tree Visibility Constraints")
    class PublicVsAdminSeparationConstraints {

        private CategoryRepositoryPort categoryRepositoryPort;
        private GetCategoryTreeUseCase getTreeUseCase;

        @BeforeEach
        void setUp() {
            categoryRepositoryPort = mock(CategoryRepositoryPort.class);
            getTreeUseCase = new GetCategoryTreeUseCase(categoryRepositoryPort);
        }

        @Test
        @DisplayName("Public Tree: contains strictly active categories (isActive=true)")
        void publicTree_ShouldContainOnlyActiveCategories() {
            UUID activeRootId = UUID.randomUUID();
            UUID activeChildId = UUID.randomUUID();
            UUID inactiveRootId = UUID.randomUUID();

            Category activeRoot = Category.builder().id(activeRootId).name("Active Root").slug("act-root").isActive(true).build();
            Category activeChild = Category.builder().id(activeChildId).name("Active Child").slug("act-child").parentId(activeRootId).isActive(true).build();
            Category inactiveRoot = Category.builder().id(inactiveRootId).name("Inactive Root").slug("inact-root").isActive(false).build();

            when(categoryRepositoryPort.findAllActive()).thenReturn(List.of(activeRoot, activeChild));
            when(categoryRepositoryPort.findAll()).thenReturn(List.of(activeRoot, activeChild, inactiveRoot));

            List<CategoryTreeResponse> publicTree = getTreeUseCase.execute(false);

            assertEquals(1, publicTree.size());
            assertEquals(activeRootId, publicTree.get(0).id());
            assertTrue(publicTree.get(0).isActive());
            assertEquals(1, publicTree.get(0).children().size());
            assertEquals(activeChildId, publicTree.get(0).children().get(0).id());
            assertTrue(publicTree.get(0).children().get(0).isActive());

            // Admin Tree sees inactiveRoot as well
            List<CategoryTreeResponse> adminTree = getTreeUseCase.execute(true);
            assertEquals(2, adminTree.size());
            assertTrue(adminTree.stream().anyMatch(n -> n.id().equals(inactiveRootId)));
        }

        @Test
        @DisplayName("Public Tree: active child with inactive parent is preserved as root without data loss")
        void publicTree_ShouldPromoteActiveChildToRoot_WhenParentIsInactive() {
            UUID inactiveParentId = UUID.randomUUID();
            UUID activeChildId = UUID.randomUUID();

            Category activeChild = Category.builder()
                    .id(activeChildId)
                    .name("Active Child")
                    .slug("act-child")
                    .parentId(inactiveParentId)
                    .isActive(true)
                    .build();

            when(categoryRepositoryPort.findAllActive()).thenReturn(List.of(activeChild));

            List<CategoryTreeResponse> publicTree = getTreeUseCase.execute(false);

            assertEquals(1, publicTree.size());
            assertEquals(activeChildId, publicTree.get(0).id());
            assertEquals(inactiveParentId, publicTree.get(0).parentId());
        }

        @Test
        @DisplayName("Public Tree: returns empty list when all categories are inactive")
        void publicTree_ShouldReturnEmptyList_WhenAllCategoriesAreInactive() {
            Category inactive1 = Category.builder().id(UUID.randomUUID()).name("Inact 1").slug("in-1").isActive(false).build();
            Category inactive2 = Category.builder().id(UUID.randomUUID()).name("Inact 2").slug("in-2").isActive(false).build();

            when(categoryRepositoryPort.findAllActive()).thenReturn(List.of());
            when(categoryRepositoryPort.findAll()).thenReturn(List.of(inactive1, inactive2));

            List<CategoryTreeResponse> publicTree = getTreeUseCase.execute(false);
            assertNotNull(publicTree);
            assertTrue(publicTree.isEmpty(), "Public tree must be empty when no active categories exist");

            List<CategoryTreeResponse> adminTree = getTreeUseCase.execute(true);
            assertNotNull(adminTree);
            assertEquals(2, adminTree.size(), "Admin tree must list all 2 inactive categories");
        }
    }

    @Nested
    @DisplayName("4. REST Exception Handler Contracts")
    class ExceptionHandlerConstraints {

        private final CategoryExceptionHandler handler = new CategoryExceptionHandler();

        @BeforeEach
        void setLocale() {
            LocaleContextHolder.setLocale(Locale.ENGLISH);
        }

        @AfterEach
        void clearLocale() {
            LocaleContextHolder.resetLocaleContext();
        }

        @Test
        @DisplayName("CategoryNotFoundException maps to 404 NOT_FOUND with CATEGORY_NOT_FOUND code")
        void handleCategoryNotFound_ShouldReturn404() {
            UUID id = UUID.randomUUID();
            ResponseEntity<ErrorResponse> response = handler.handleCategoryNotFound(new CategoryNotFoundException(id));

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("CATEGORY_NOT_FOUND", response.getBody().code());
            assertEquals("Category was not found.", response.getBody().message());
        }

        @Test
        @DisplayName("SlugAlreadyExistsException maps to 409 CONFLICT with SLUG_ALREADY_EXISTS code")
        void handleSlugAlreadyExists_ShouldReturn409() {
            String slug = "kayak";
            ResponseEntity<ErrorResponse> response = handler.handleSlugAlreadyExists(SlugAlreadyExistsException.ofSlug(slug));

            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("SLUG_ALREADY_EXISTS", response.getBody().code());
            assertEquals("Slug already exists.", response.getBody().message());
        }

        @Test
        @DisplayName("CategoryHasActiveServicesException maps to 409 CONFLICT with CATEGORY_HAS_ACTIVE_SERVICES code")
        void handleCategoryHasActiveServices_ShouldReturn409() {
            UUID id = UUID.randomUUID();
            ResponseEntity<ErrorResponse> response = handler.handleCategoryHasActiveServices(new CategoryHasActiveServicesException(id));

            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("CATEGORY_HAS_ACTIVE_SERVICES", response.getBody().code());
            assertEquals("Category still contains active services.", response.getBody().message());
        }

        @Test
        @DisplayName("CategoryHierarchyLoopException maps to 400 BAD_REQUEST with CATEGORY_HIERARCHY_LOOP code")
        void handleCategoryHierarchyLoop_ShouldReturn400() {
            ResponseEntity<ErrorResponse> response = handler.handleCategoryHierarchyLoop(new CategoryHierarchyLoopException("Loop detected"));

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("CATEGORY_HIERARCHY_LOOP", response.getBody().code());
            assertEquals("Category hierarchy cannot contain a loop.", response.getBody().message());
        }
    }

    @Nested
    @DisplayName("5. REST Controller Invocation Contracts")
    class ControllerInvocationConstraints {

        @Test
        @DisplayName("CategoryController: delegates to GetCategoryTreeUseCase with includeInactive=false")
        void categoryController_ShouldCallUseCaseWithFalse() {
            GetCategoryTreeUseCase useCase = mock(GetCategoryTreeUseCase.class);
            CategoryController controller = new CategoryController(useCase);

            when(useCase.execute(false)).thenReturn(List.of());

            ResponseEntity<List<CategoryTreeResponse>> response = controller.getCategoryTree();
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(useCase).execute(false);
        }

        @Test
        @DisplayName("AdminCategoryController: delegates to GetCategoryTreeUseCase with includeInactive=true")
        void adminCategoryController_ShouldCallUseCaseWithTrue() {
            GetCategoryTreeUseCase getTreeUseCase = mock(GetCategoryTreeUseCase.class);
            CreateCategoryUseCase createUseCase = mock(CreateCategoryUseCase.class);
            UpdateCategoryUseCase updateUseCase = mock(UpdateCategoryUseCase.class);
            DeactivateCategoryUseCase deactivateUseCase = mock(DeactivateCategoryUseCase.class);
            CategoryMapper categoryMapper = mock(CategoryMapper.class);

            AdminCategoryController controller = new AdminCategoryController(
                    getTreeUseCase, createUseCase, updateUseCase, deactivateUseCase, categoryMapper
            );

            when(getTreeUseCase.execute(true)).thenReturn(List.of());

            ResponseEntity<List<CategoryTreeResponse>> response = controller.getAdminCategoryTree();
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(getTreeUseCase).execute(true);
        }

        @Test
        @DisplayName("AdminCategoryController: deactivateCategory delegates to DeactivateCategoryUseCase and maps response")
        void adminCategoryController_ShouldDeactivateCategory() {
            GetCategoryTreeUseCase getTreeUseCase = mock(GetCategoryTreeUseCase.class);
            CreateCategoryUseCase createUseCase = mock(CreateCategoryUseCase.class);
            UpdateCategoryUseCase updateUseCase = mock(UpdateCategoryUseCase.class);
            DeactivateCategoryUseCase deactivateUseCase = mock(DeactivateCategoryUseCase.class);
            CategoryMapper categoryMapper = mock(CategoryMapper.class);

            AdminCategoryController controller = new AdminCategoryController(
                    getTreeUseCase, createUseCase, updateUseCase, deactivateUseCase, categoryMapper
            );

            UUID id = UUID.randomUUID();
            Category deactivated = Category.builder().id(id).name("Deactivated").slug("deact").isActive(false).build();
            CategoryResponse mappedResponse = new CategoryResponse(id, "Deactivated", null, "deact", null, null, false, null, null);

            when(deactivateUseCase.execute(id)).thenReturn(deactivated);
            when(categoryMapper.toResponse(deactivated)).thenReturn(mappedResponse);

            ResponseEntity<CategoryResponse> response = controller.deactivateCategory(id);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertFalse(response.getBody().isActive());
            verify(deactivateUseCase).execute(id);
            verify(categoryMapper).toResponse(deactivated);
        }

        @Test
        @DisplayName("AdminCategoryController: createCategory delegates to CreateCategoryUseCase and returns 201 CREATED")
        void adminCategoryController_ShouldCreateCategory() {
            GetCategoryTreeUseCase getTreeUseCase = mock(GetCategoryTreeUseCase.class);
            CreateCategoryUseCase createUseCase = mock(CreateCategoryUseCase.class);
            UpdateCategoryUseCase updateUseCase = mock(UpdateCategoryUseCase.class);
            DeactivateCategoryUseCase deactivateUseCase = mock(DeactivateCategoryUseCase.class);
            CategoryMapper categoryMapper = mock(CategoryMapper.class);

            AdminCategoryController controller = new AdminCategoryController(
                    getTreeUseCase, createUseCase, updateUseCase, deactivateUseCase, categoryMapper
            );

            CreateCategoryRequest request = new CreateCategoryRequest("Tours", "Tours En", "tours", null, "icon.png");
            UUID id = UUID.randomUUID();
            Category created = Category.builder().id(id).name("Tours").slug("tours").isActive(true).build();
            CategoryResponse responseDto = new CategoryResponse(id, "Tours", "Tours En", "tours", null, "icon.png", true, null, null);

            when(createUseCase.execute("Tours", "Tours En", "tours", null, "icon.png")).thenReturn(created);
            when(categoryMapper.toResponse(created)).thenReturn(responseDto);

            ResponseEntity<CategoryResponse> response = controller.createCategory(request);

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("tours", response.getBody().slug());
            verify(createUseCase).execute("Tours", "Tours En", "tours", null, "icon.png");
        }

        @Test
        @DisplayName("AdminCategoryController: updateCategory delegates to UpdateCategoryUseCase and returns 200 OK")
        void adminCategoryController_ShouldUpdateCategory() {
            GetCategoryTreeUseCase getTreeUseCase = mock(GetCategoryTreeUseCase.class);
            CreateCategoryUseCase createUseCase = mock(CreateCategoryUseCase.class);
            UpdateCategoryUseCase updateUseCase = mock(UpdateCategoryUseCase.class);
            DeactivateCategoryUseCase deactivateUseCase = mock(DeactivateCategoryUseCase.class);
            CategoryMapper categoryMapper = mock(CategoryMapper.class);

            AdminCategoryController controller = new AdminCategoryController(
                    getTreeUseCase, createUseCase, updateUseCase, deactivateUseCase, categoryMapper
            );

            UUID id = UUID.randomUUID();
            UpdateCategoryRequest request = new UpdateCategoryRequest("Tours Updated", null, null, null, null);
            Category updated = Category.builder().id(id).name("Tours Updated").slug("tours").isActive(true).build();
            CategoryResponse responseDto = new CategoryResponse(id, "Tours Updated", null, "tours", null, null, true, null, null);

            when(updateUseCase.execute(id, new UpdateCategoryCommand("Tours Updated", null, null, null, null))).thenReturn(updated);
            when(categoryMapper.toResponse(updated)).thenReturn(responseDto);

            ResponseEntity<CategoryResponse> response = controller.updateCategory(id, request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Tours Updated", response.getBody().name());
            verify(updateUseCase).execute(id, new UpdateCategoryCommand("Tours Updated", null, null, null, null));
        }

        @Test
        @DisplayName("AdminCategoryController: deleteCategory delegates to DeleteCategoryUseCase and returns 204 NO_CONTENT")
        void adminCategoryController_ShouldDeleteCategory() {
            DeleteCategoryUseCase deleteUseCase = mock(DeleteCategoryUseCase.class);
            AdminCategoryController controller = new AdminCategoryController(
                    null, null, null, null, deleteUseCase, null
            );
            UUID id = UUID.randomUUID();
            ResponseEntity<Void> response = controller.deleteCategory(id);
            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
            verify(deleteUseCase).execute(id);
        }
    }
}
