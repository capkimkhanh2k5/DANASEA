package com.danasea.backend.modules.service.application.usecases;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.danasea.backend.modules.service.application.ports.output.CategoryRepositoryPort;
import com.danasea.backend.modules.service.application.ports.output.ActiveServiceCheckPort;
import com.danasea.backend.modules.service.domain.exceptions.CategoryHierarchyLoopException;
import com.danasea.backend.modules.service.domain.models.Category;
import com.danasea.backend.modules.service.presentation.dtos.CategoryTreeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Category Hierarchy Adversarial & Stress Tests")
class CategoryHierarchyAdversarialUseCaseTest {

    private CategoryRepositoryPort categoryRepositoryPort;
    private CreateCategoryUseCase createCategoryUseCase;
    private UpdateCategoryUseCase updateCategoryUseCase;
    private GetCategoryTreeUseCase getCategoryTreeUseCase;

    @BeforeEach
    void setUp() {
        categoryRepositoryPort = mock(CategoryRepositoryPort.class);
        createCategoryUseCase = new CreateCategoryUseCase(categoryRepositoryPort);
        updateCategoryUseCase = new UpdateCategoryUseCase(categoryRepositoryPort, mock(ActiveServiceCheckPort.class));
        getCategoryTreeUseCase = new GetCategoryTreeUseCase(categoryRepositoryPort);
    }

    @Nested
    @DisplayName("Self-Loop Adversarial Tests")
    class SelfLoopTests {

        @Test
        @DisplayName("CreateCategory: setting category as its own parent (A -> A) must throw CategoryHierarchyLoopException")
        void shouldRejectSelfParent_OnCreate() {
            UUID catId = UUID.randomUUID();
            when(categoryRepositoryPort.existsBySlug("self-loop")).thenReturn(false);
            when(categoryRepositoryPort.findById(catId)).thenReturn(Optional.of(
                    Category.builder().id(catId).name("Cat").slug("self-loop").isActive(true).build()
            ));

            CreateCategoryCommand command = new CreateCategoryCommand(catId, "Cat", "Cat En", "self-loop", catId, null);

            CategoryHierarchyLoopException ex = assertThrows(
                    CategoryHierarchyLoopException.class,
                    () -> createCategoryUseCase.execute(command)
            );
            assertTrue(ex.getMessage().contains("Cannot set category as its own parent")
                    || ex.getMessage().contains("Circular hierarchy detected"));
            verify(categoryRepositoryPort, never()).save(any());
        }

        @Test
        @DisplayName("UpdateCategory: setting category as its own parent (A -> A) must throw CategoryHierarchyLoopException")
        void shouldRejectSelfParent_OnUpdate() {
            UUID catId = UUID.randomUUID();
            Category existingCat = Category.builder()
                    .id(catId)
                    .name("Existing")
                    .slug("existing")
                    .parentId(null)
                    .isActive(true)
                    .build();

            when(categoryRepositoryPort.findByIdForUpdate(catId)).thenReturn(Optional.of(existingCat));

            UpdateCategoryCommand command = new UpdateCategoryCommand("Existing", "Existing En", "existing", catId, null);

            CategoryHierarchyLoopException ex = assertThrows(
                    CategoryHierarchyLoopException.class,
                    () -> updateCategoryUseCase.execute(catId, command)
            );
            assertEquals("Cannot set category as its own parent", ex.getMessage());
            verify(categoryRepositoryPort, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Multi-Hop Cycle Prevention Tests")
    class MultiHopCycleTests {

        @Test
        @DisplayName("UpdateCategory: 2-node cycle (A -> B -> A) must throw CategoryHierarchyLoopException")
        void shouldRejectTwoNodeCycle_OnUpdate() {
            // A is parent of B. Try to update A to have parent B.
            UUID idA = UUID.randomUUID();
            UUID idB = UUID.randomUUID();

            Category catA = Category.builder().id(idA).name("A").slug("a").parentId(null).isActive(true).build();
            Category catB = Category.builder().id(idB).name("B").slug("b").parentId(idA).isActive(true).build();

            when(categoryRepositoryPort.findByIdForUpdate(idA)).thenReturn(Optional.of(catA));
            when(categoryRepositoryPort.findById(idB)).thenReturn(Optional.of(catB));

            UpdateCategoryCommand command = new UpdateCategoryCommand("A", "A En", "a", idB, null);

            CategoryHierarchyLoopException ex = assertThrows(
                    CategoryHierarchyLoopException.class,
                    () -> updateCategoryUseCase.execute(idA, command)
            );
            assertTrue(ex.getMessage().contains("Circular hierarchy detected"));
            verify(categoryRepositoryPort, never()).save(any());
        }

        @Test
        @DisplayName("UpdateCategory: 5-hop deep cycle (A -> B -> C -> D -> E -> A) must be blocked")
        void shouldRejectDeepFiveHopCycle_OnUpdate() {
            // A -> B -> C -> D -> E
            UUID idA = UUID.randomUUID();
            UUID idB = UUID.randomUUID();
            UUID idC = UUID.randomUUID();
            UUID idD = UUID.randomUUID();
            UUID idE = UUID.randomUUID();

            Category catA = Category.builder().id(idA).name("A").slug("a").parentId(null).isActive(true).build();
            Category catB = Category.builder().id(idB).name("B").slug("b").parentId(idA).isActive(true).build();
            Category catC = Category.builder().id(idC).name("C").slug("c").parentId(idB).isActive(true).build();
            Category catD = Category.builder().id(idD).name("D").slug("d").parentId(idC).isActive(true).build();
            Category catE = Category.builder().id(idE).name("E").slug("e").parentId(idD).isActive(true).build();

            when(categoryRepositoryPort.findByIdForUpdate(idA)).thenReturn(Optional.of(catA));
            when(categoryRepositoryPort.findById(idB)).thenReturn(Optional.of(catB));
            when(categoryRepositoryPort.findById(idC)).thenReturn(Optional.of(catC));
            when(categoryRepositoryPort.findById(idD)).thenReturn(Optional.of(catD));
            when(categoryRepositoryPort.findById(idE)).thenReturn(Optional.of(catE));

            // Try to set E as parent of A (A -> E -> D -> C -> B -> A)
            UpdateCategoryCommand command = new UpdateCategoryCommand("A", "A En", "a", idE, null);

            CategoryHierarchyLoopException ex = assertThrows(
                    CategoryHierarchyLoopException.class,
                    () -> updateCategoryUseCase.execute(idA, command)
            );
            assertTrue(ex.getMessage().contains("Circular hierarchy detected"));
            verify(categoryRepositoryPort, never()).save(any());
        }

        @Test
        @DisplayName("UpdateCategory: Subtree reparenting into descendant must be blocked (B -> D when A -> B -> C -> D)")
        void shouldRejectReparentingToDescendant_OnUpdate() {
            UUID idA = UUID.randomUUID();
            UUID idB = UUID.randomUUID();
            UUID idC = UUID.randomUUID();
            UUID idD = UUID.randomUUID();

            Category catA = Category.builder().id(idA).name("A").slug("a").parentId(null).isActive(true).build();
            Category catB = Category.builder().id(idB).name("B").slug("b").parentId(idA).isActive(true).build();
            Category catC = Category.builder().id(idC).name("C").slug("c").parentId(idB).isActive(true).build();
            Category catD = Category.builder().id(idD).name("D").slug("d").parentId(idC).isActive(true).build();

            when(categoryRepositoryPort.findById(idA)).thenReturn(Optional.of(catA));
            when(categoryRepositoryPort.findByIdForUpdate(idB)).thenReturn(Optional.of(catB));
            when(categoryRepositoryPort.findById(idC)).thenReturn(Optional.of(catC));
            when(categoryRepositoryPort.findById(idD)).thenReturn(Optional.of(catD));

            // Try to set B's parent to D (B -> D -> C -> B)
            UpdateCategoryCommand command = new UpdateCategoryCommand("B", "B En", "b", idD, null);

            assertThrows(
                    CategoryHierarchyLoopException.class,
                    () -> updateCategoryUseCase.execute(idB, command)
            );
            verify(categoryRepositoryPort, never()).save(any());
        }

        @Test
        @DisplayName("UpdateCategory: Valid non-cyclical reparenting to sibling branch must succeed")
        void shouldAllowValidSiblingReparenting_OnUpdate() {
            // Root -> Branch1, Root -> Branch2 -> Sub2.
            // Move Sub2 to Branch1.
            UUID idRoot = UUID.randomUUID();
            UUID idBranch1 = UUID.randomUUID();
            UUID idBranch2 = UUID.randomUUID();
            UUID idSub2 = UUID.randomUUID();

            Category root = Category.builder().id(idRoot).name("Root").slug("root").parentId(null).isActive(true).build();
            Category b1 = Category.builder().id(idBranch1).name("B1").slug("b1").parentId(idRoot).isActive(true).build();
            Category b2 = Category.builder().id(idBranch2).name("B2").slug("b2").parentId(idRoot).isActive(true).build();
            Category sub2 = Category.builder().id(idSub2).name("Sub2").slug("sub2").parentId(idBranch2).isActive(true).build();

            when(categoryRepositoryPort.findById(idRoot)).thenReturn(Optional.of(root));
            when(categoryRepositoryPort.findById(idBranch1)).thenReturn(Optional.of(b1));
            when(categoryRepositoryPort.findById(idBranch2)).thenReturn(Optional.of(b2));
            when(categoryRepositoryPort.findByIdForUpdate(idSub2)).thenReturn(Optional.of(sub2));
            when(categoryRepositoryPort.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateCategoryCommand command = new UpdateCategoryCommand("Sub2", "Sub2 En", "sub2", idBranch1, null);

            Category updated = updateCategoryUseCase.execute(idSub2, command);
            assertNotNull(updated);
            assertEquals(idBranch1, updated.getParentId());
            verify(categoryRepositoryPort).save(sub2);
        }

        @Test
        @DisplayName("CreateCategory: 3-node cycle (A -> B -> C -> A) must throw CategoryHierarchyLoopException")
        void shouldRejectThreeNodeCycle_OnCreate() {
            UUID idA = UUID.randomUUID();
            UUID idB = UUID.randomUUID();
            UUID idC = UUID.randomUUID();

            Category catB = Category.builder().id(idB).name("B").slug("b").parentId(idA).isActive(true).build();
            Category catC = Category.builder().id(idC).name("C").slug("c").parentId(idB).isActive(true).build();

            when(categoryRepositoryPort.existsBySlug("a")).thenReturn(false);
            when(categoryRepositoryPort.findById(idC)).thenReturn(Optional.of(catC));
            when(categoryRepositoryPort.findById(idB)).thenReturn(Optional.of(catB));

            CreateCategoryCommand command = new CreateCategoryCommand(idA, "A", "A En", "a", idC, null);

            CategoryHierarchyLoopException ex = assertThrows(
                    CategoryHierarchyLoopException.class,
                    () -> createCategoryUseCase.execute(command)
            );
            assertTrue(ex.getMessage().contains("Circular hierarchy detected"));
            verify(categoryRepositoryPort, never()).save(any());
        }

        @Test
        @DisplayName("UpdateCategory: 3-node cycle (A -> B -> C -> A) must throw CategoryHierarchyLoopException")
        void shouldRejectThreeNodeCycle_OnUpdate() {
            UUID idA = UUID.randomUUID();
            UUID idB = UUID.randomUUID();
            UUID idC = UUID.randomUUID();

            Category catA = Category.builder().id(idA).name("A").slug("a").parentId(null).isActive(true).build();
            Category catB = Category.builder().id(idB).name("B").slug("b").parentId(idA).isActive(true).build();
            Category catC = Category.builder().id(idC).name("C").slug("c").parentId(idB).isActive(true).build();

            when(categoryRepositoryPort.findByIdForUpdate(idA)).thenReturn(Optional.of(catA));
            when(categoryRepositoryPort.findById(idC)).thenReturn(Optional.of(catC));
            when(categoryRepositoryPort.findById(idB)).thenReturn(Optional.of(catB));

            UpdateCategoryCommand command = new UpdateCategoryCommand("A", "A En", "a", idC, null);

            CategoryHierarchyLoopException ex = assertThrows(
                    CategoryHierarchyLoopException.class,
                    () -> updateCategoryUseCase.execute(idA, command)
            );
            assertTrue(ex.getMessage().contains("Circular hierarchy detected"));
            verify(categoryRepositoryPort, never()).save(any());
        }

        @Test
        @DisplayName("UpdateCategory: Pre-existing corrupt loop in DB (X <-> Y) causes CategoryHierarchyLoopException when updating Z to parent X")
        void shouldRejectCycle_WhenTargetParentIsInPreExistingLoop() {
            UUID idZ = UUID.randomUUID();
            UUID idX = UUID.randomUUID();
            UUID idY = UUID.randomUUID();

            Category catZ = Category.builder().id(idZ).name("Z").slug("z").parentId(null).isActive(true).build();
            Category catX = Category.builder().id(idX).name("X").slug("x").parentId(idY).isActive(true).build();
            Category catY = Category.builder().id(idY).name("Y").slug("y").parentId(idX).isActive(true).build();

            when(categoryRepositoryPort.findByIdForUpdate(idZ)).thenReturn(Optional.of(catZ));
            when(categoryRepositoryPort.findById(idX)).thenReturn(Optional.of(catX));
            when(categoryRepositoryPort.findById(idY)).thenReturn(Optional.of(catY));

            UpdateCategoryCommand command = new UpdateCategoryCommand("Z", "Z En", "z", idX, null);

            CategoryHierarchyLoopException ex = assertThrows(
                    CategoryHierarchyLoopException.class,
                    () -> updateCategoryUseCase.execute(idZ, command)
            );
            assertTrue(ex.getMessage().contains("Circular hierarchy detected"));
            verify(categoryRepositoryPort, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Tree Generation Stress & Resilience Tests")
    class TreeGenerationStressTests {

        @Test
        @DisplayName("Stack Safety: Deep chain hierarchy of 5,000 levels builds without StackOverflowError")
        @Timeout(5)
        void shouldBuildDeepLinearHierarchyWithoutStackOverflow() {
            int depth = 5000;
            List<Category> chain = new ArrayList<>(depth);
            UUID previousId = null;

            for (int i = 0; i < depth; i++) {
                UUID currentId = UUID.randomUUID();
                Category cat = Category.builder()
                        .id(currentId)
                        .name("Level-" + i)
                        .slug("level-" + i)
                        .parentId(previousId)
                        .isActive(true)
                        .build();
                chain.add(cat);
                previousId = currentId;
            }

            when(categoryRepositoryPort.findAllActive()).thenReturn(chain);
            when(categoryRepositoryPort.findAll()).thenReturn(chain);

            // Execute tree build
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                List<CategoryTreeResponse> tree = getCategoryTreeUseCase.execute(false);
                assertNotNull(tree);
                assertEquals(1, tree.size(), "Should have exactly 1 root node for linear chain");
                assertEquals("Level-0", tree.get(0).name());

                // Verify first child is attached
                assertEquals(1, tree.get(0).children().size());
                assertEquals("Level-1", tree.get(0).children().get(0).name());
            });
        }

        @Test
        @DisplayName("Corrupt DB Resilience: Mutual loop in DB (A <-> B) does not cause infinite loop or StackOverflow")
        @Timeout(2)
        void shouldHandlePreExistingMutualCycleInDbGracefully() {
            UUID idA = UUID.randomUUID();
            UUID idB = UUID.randomUUID();

            // A's parent is B, B's parent is A
            Category catA = Category.builder().id(idA).name("Cat A").slug("cat-a").parentId(idB).isActive(true).build();
            Category catB = Category.builder().id(idB).name("Cat B").slug("cat-b").parentId(idA).isActive(true).build();

            when(categoryRepositoryPort.findAllActive()).thenReturn(List.of(catA, catB));
            when(categoryRepositoryPort.findAll()).thenReturn(List.of(catA, catB));

            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                List<CategoryTreeResponse> tree = getCategoryTreeUseCase.execute(false);
                assertNotNull(tree);
                // Both nodes should be preserved at top level rather than infinitely nesting or crashing
                assertEquals(2, tree.size());
                assertTrue(tree.stream().allMatch(node -> node.children().isEmpty()));
            });
        }

        @Test
        @DisplayName("Corrupt DB Resilience: Self-parent in DB (A -> A) does not cause infinite loop")
        @Timeout(2)
        void shouldHandlePreExistingSelfParentInDbGracefully() {
            UUID idA = UUID.randomUUID();
            Category catA = Category.builder().id(idA).name("Self Cat").slug("self-cat").parentId(idA).isActive(true).build();

            when(categoryRepositoryPort.findAllActive()).thenReturn(List.of(catA));
            when(categoryRepositoryPort.findAll()).thenReturn(List.of(catA));

            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                List<CategoryTreeResponse> tree = getCategoryTreeUseCase.execute(false);
                assertNotNull(tree);
                assertEquals(1, tree.size());
                assertEquals(idA, tree.get(0).id());
                assertTrue(tree.get(0).children().isEmpty());
            });
        }

        @Test
        @DisplayName("Orphan Nodes: Category referencing non-existent parent becomes root gracefully")
        void shouldPromoteOrphanNodesToRootGracefully() {
            UUID idOrphan = UUID.randomUUID();
            UUID nonExistentParent = UUID.randomUUID();
            Category orphan = Category.builder()
                    .id(idOrphan)
                    .name("Orphan")
                    .slug("orphan")
                    .parentId(nonExistentParent)
                    .isActive(true)
                    .build();

            when(categoryRepositoryPort.findAllActive()).thenReturn(List.of(orphan));
            when(categoryRepositoryPort.findAll()).thenReturn(List.of(orphan));

            List<CategoryTreeResponse> tree = getCategoryTreeUseCase.execute(false);
            assertNotNull(tree);
            assertEquals(1, tree.size());
            assertEquals(idOrphan, tree.get(0).id());
            assertEquals(nonExistentParent, tree.get(0).parentId());
        }

        @Test
        @DisplayName("Forest Structure: Multiple roots each having multiple child levels")
        void shouldBuildComplexForestCorrectly() {
            // Root 1 -> Child 1.1 -> Grandchild 1.1.1
            // Root 2 -> Child 2.1
            UUID r1 = UUID.randomUUID();
            UUID c11 = UUID.randomUUID();
            UUID g111 = UUID.randomUUID();
            UUID r2 = UUID.randomUUID();
            UUID c21 = UUID.randomUUID();

            List<Category> forest = List.of(
                    Category.builder().id(r1).name("R1").slug("r1").parentId(null).isActive(true).build(),
                    Category.builder().id(c11).name("C11").slug("c11").parentId(r1).isActive(true).build(),
                    Category.builder().id(g111).name("G111").slug("g111").parentId(c11).isActive(true).build(),
                    Category.builder().id(r2).name("R2").slug("r2").parentId(null).isActive(true).build(),
                    Category.builder().id(c21).name("C21").slug("c21").parentId(r2).isActive(true).build()
            );

            when(categoryRepositoryPort.findAllActive()).thenReturn(forest);
            when(categoryRepositoryPort.findAll()).thenReturn(forest);

            List<CategoryTreeResponse> tree = getCategoryTreeUseCase.execute(false);
            assertNotNull(tree);
            assertEquals(2, tree.size(), "Forest must have 2 roots");

            CategoryTreeResponse root1Node = tree.stream().filter(n -> n.id().equals(r1)).findFirst().orElseThrow();
            assertEquals(1, root1Node.children().size());
            assertEquals(c11, root1Node.children().get(0).id());
            assertEquals(1, root1Node.children().get(0).children().size());
            assertEquals(g111, root1Node.children().get(0).children().get(0).id());

            CategoryTreeResponse root2Node = tree.stream().filter(n -> n.id().equals(r2)).findFirst().orElseThrow();
            assertEquals(1, root2Node.children().size());
            assertEquals(c21, root2Node.children().get(0).id());
        }

        @Test
        @DisplayName("Empty or Null Categories List: Returns empty list safely")
        void shouldHandleEmptyOrNullGracefully() {
            when(categoryRepositoryPort.findAllActive()).thenReturn(List.of());
            when(categoryRepositoryPort.findAll()).thenReturn(null);

            List<CategoryTreeResponse> emptyTree = getCategoryTreeUseCase.execute(false);
            assertNotNull(emptyTree);
            assertTrue(emptyTree.isEmpty());

            List<CategoryTreeResponse> nullTree = getCategoryTreeUseCase.execute(true);
            assertNotNull(nullTree);
            assertTrue(nullTree.isEmpty());
        }

        @Test
        @DisplayName("Corrupt DB Resilience: 3-node cycle with attached subtree (A -> B -> C -> A with D -> B, E -> D)")
        @Timeout(2)
        void shouldHandleThreeNodeCycleWithAttachedSubtreeGracefully() {
            UUID idA = UUID.randomUUID();
            UUID idB = UUID.randomUUID();
            UUID idC = UUID.randomUUID();
            UUID idD = UUID.randomUUID();
            UUID idE = UUID.randomUUID();

            Category catA = Category.builder().id(idA).name("A").slug("a").parentId(idC).isActive(true).build();
            Category catB = Category.builder().id(idB).name("B").slug("b").parentId(idA).isActive(true).build();
            Category catC = Category.builder().id(idC).name("C").slug("c").parentId(idB).isActive(true).build();
            Category catD = Category.builder().id(idD).name("D").slug("d").parentId(idB).isActive(true).build();
            Category catE = Category.builder().id(idE).name("E").slug("e").parentId(idD).isActive(true).build();

            when(categoryRepositoryPort.findAllActive()).thenReturn(List.of(catA, catB, catC, catD, catE));
            when(categoryRepositoryPort.findAll()).thenReturn(List.of(catA, catB, catC, catD, catE));

            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                List<CategoryTreeResponse> tree = getCategoryTreeUseCase.execute(false);
                assertNotNull(tree);
                assertEquals(3, tree.size(), "All 3 loop nodes become roots");

                CategoryTreeResponse nodeB = tree.stream().filter(n -> n.id().equals(idB)).findFirst().orElseThrow();
                assertEquals(1, nodeB.children().size(), "Node D is safely attached as child of B");
                assertEquals(idD, nodeB.children().get(0).id());

                CategoryTreeResponse nodeD = nodeB.children().get(0);
                assertEquals(1, nodeD.children().size(), "Node E is safely attached as child of D");
                assertEquals(idE, nodeD.children().get(0).id());
            });
        }

        @Test
        @DisplayName("Corrupt DB Resilience: Multiple disjoint cycles in database terminate without cycle")
        @Timeout(2)
        void shouldHandleMultipleDisjointCyclesGracefully() {
            UUID idA = UUID.randomUUID();
            UUID idB = UUID.randomUUID();
            UUID idX = UUID.randomUUID();
            UUID idY = UUID.randomUUID();
            UUID idZ = UUID.randomUUID();

            // Cycle 1: A <-> B
            Category catA = Category.builder().id(idA).name("A").slug("a").parentId(idB).isActive(true).build();
            Category catB = Category.builder().id(idB).name("B").slug("b").parentId(idA).isActive(true).build();

            // Cycle 2: X -> Y -> Z -> X
            Category catX = Category.builder().id(idX).name("X").slug("x").parentId(idZ).isActive(true).build();
            Category catY = Category.builder().id(idY).name("Y").slug("y").parentId(idX).isActive(true).build();
            Category catZ = Category.builder().id(idZ).name("Z").slug("z").parentId(idY).isActive(true).build();

            when(categoryRepositoryPort.findAllActive()).thenReturn(List.of(catA, catB, catX, catY, catZ));
            when(categoryRepositoryPort.findAll()).thenReturn(List.of(catA, catB, catX, catY, catZ));

            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                List<CategoryTreeResponse> tree = getCategoryTreeUseCase.execute(false);
                assertNotNull(tree);
                assertEquals(5, tree.size(), "All 5 nodes in disjoint cycles become roots with empty children");
                assertTrue(tree.stream().allMatch(node -> node.children().isEmpty()));
            });
        }

        @Test
        @DisplayName("Stress: Wide tree with 5,000 children under a single root")
        @Timeout(5)
        void shouldHandleWideTreeWithThousandsOfSiblings() {
            UUID rootId = UUID.randomUUID();
            Category root = Category.builder().id(rootId).name("Root").slug("root").parentId(null).isActive(true).build();

            int siblingCount = 5000;
            List<Category> all = new ArrayList<>(siblingCount + 1);
            all.add(root);

            for (int i = 0; i < siblingCount; i++) {
                all.add(Category.builder()
                        .id(UUID.randomUUID())
                        .name("Child-" + i)
                        .slug("child-" + i)
                        .parentId(rootId)
                        .isActive(true)
                        .build());
            }

            when(categoryRepositoryPort.findAllActive()).thenReturn(all);
            when(categoryRepositoryPort.findAll()).thenReturn(all);

            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                List<CategoryTreeResponse> tree = getCategoryTreeUseCase.execute(false);
                assertNotNull(tree);
                assertEquals(1, tree.size());
                assertEquals(siblingCount, tree.get(0).children().size());
            });
        }

        @Test
        @DisplayName("Mixed Active/Inactive: Verify public tree promotion of active child whose parent is inactive")
        void shouldDemonstrateActiveChildOfInactiveParentPromotion() {
            UUID inactiveRootId = UUID.randomUUID();
            UUID activeChildId = UUID.randomUUID();
            UUID activeGrandchildId = UUID.randomUUID();

            Category inactiveRoot = Category.builder()
                    .id(inactiveRootId)
                    .name("Inactive Root")
                    .slug("inactive-root")
                    .parentId(null)
                    .isActive(false)
                    .build();

            Category activeChild = Category.builder()
                    .id(activeChildId)
                    .name("Active Child")
                    .slug("active-child")
                    .parentId(inactiveRootId)
                    .isActive(true)
                    .build();

            Category activeGrandchild = Category.builder()
                    .id(activeGrandchildId)
                    .name("Active Grandchild")
                    .slug("active-grandchild")
                    .parentId(activeChildId)
                    .isActive(true)
                    .build();

            // For public tree, findAllActive() does not return inactiveRoot
            when(categoryRepositoryPort.findAllActive()).thenReturn(List.of(activeChild, activeGrandchild));
            when(categoryRepositoryPort.findAll()).thenReturn(List.of(inactiveRoot, activeChild, activeGrandchild));

            List<CategoryTreeResponse> publicTree = getCategoryTreeUseCase.execute(false);

            // Empirical verification:
            // Because inactiveRoot is missing from active list, activeChild is promoted to root in public tree
            assertNotNull(publicTree);
            assertEquals(1, publicTree.size());
            CategoryTreeResponse publicRoot = publicTree.get(0);
            assertEquals(activeChildId, publicRoot.id());
            assertEquals(inactiveRootId, publicRoot.parentId(), "ParentId still holds the inactive parent's UUID");
            assertEquals(1, publicRoot.children().size());
            assertEquals(activeGrandchildId, publicRoot.children().get(0).id());

            // Admin tree retains the full structure with inactiveRoot at top
            List<CategoryTreeResponse> adminTree = getCategoryTreeUseCase.execute(true);
            assertEquals(1, adminTree.size());
            assertEquals(inactiveRootId, adminTree.get(0).id());
            assertFalse(adminTree.get(0).isActive());
            assertEquals(1, adminTree.get(0).children().size());
            assertEquals(activeChildId, adminTree.get(0).children().get(0).id());
        }
    }
}

