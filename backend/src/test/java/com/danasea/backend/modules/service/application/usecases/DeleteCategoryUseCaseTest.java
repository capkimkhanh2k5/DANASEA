package com.danasea.backend.modules.service.application.usecases;

import java.util.UUID;

import com.danasea.backend.modules.service.application.ports.output.CategoryRepositoryPort;
import com.danasea.backend.modules.service.domain.exceptions.CategoryHasChildrenException;
import com.danasea.backend.modules.service.domain.exceptions.CategoryHasDependentRulesException;
import com.danasea.backend.modules.service.domain.exceptions.CategoryHasLinkedServicesException;
import com.danasea.backend.modules.service.domain.exceptions.CategoryNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteCategoryUseCaseTest {

    @Mock
    private CategoryRepositoryPort categoryRepositoryPort;

    private DeleteCategoryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeleteCategoryUseCase(categoryRepositoryPort);
    }

    @Test
    void shouldDeleteCategorySuccessfully_whenNoDependencies() {
        UUID categoryId = UUID.randomUUID();
        when(categoryRepositoryPort.existsById(categoryId)).thenReturn(true);
        when(categoryRepositoryPort.hasLinkedServices(categoryId)).thenReturn(false);
        when(categoryRepositoryPort.hasChildren(categoryId)).thenReturn(false);
        when(categoryRepositoryPort.hasCommissionRules(categoryId)).thenReturn(false);
        when(categoryRepositoryPort.hasSafetyRules(categoryId)).thenReturn(false);

        useCase.execute(categoryId);

        verify(categoryRepositoryPort).deleteById(categoryId);
    }

    @Test
    void shouldThrowNotFound_whenCategoryDoesNotExist() {
        UUID categoryId = UUID.randomUUID();
        when(categoryRepositoryPort.existsById(categoryId)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(categoryId))
                .isInstanceOf(CategoryNotFoundException.class);

        verify(categoryRepositoryPort, never()).deleteById(any());
    }

    @Test
    void shouldThrowLinkedServices_whenCategoryHasServices() {
        UUID categoryId = UUID.randomUUID();
        when(categoryRepositoryPort.existsById(categoryId)).thenReturn(true);
        when(categoryRepositoryPort.hasLinkedServices(categoryId)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(categoryId))
                .isInstanceOf(CategoryHasLinkedServicesException.class);

        verify(categoryRepositoryPort, never()).deleteById(any());
    }

    @Test
    void shouldThrowChildren_whenCategoryHasChildren() {
        UUID categoryId = UUID.randomUUID();
        when(categoryRepositoryPort.existsById(categoryId)).thenReturn(true);
        when(categoryRepositoryPort.hasLinkedServices(categoryId)).thenReturn(false);
        when(categoryRepositoryPort.hasChildren(categoryId)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(categoryId))
                .isInstanceOf(CategoryHasChildrenException.class);

        verify(categoryRepositoryPort, never()).deleteById(any());
    }

    @Test
    void shouldThrowDependentRules_whenCategoryHasCommissionRules() {
        UUID categoryId = UUID.randomUUID();
        when(categoryRepositoryPort.existsById(categoryId)).thenReturn(true);
        when(categoryRepositoryPort.hasLinkedServices(categoryId)).thenReturn(false);
        when(categoryRepositoryPort.hasChildren(categoryId)).thenReturn(false);
        when(categoryRepositoryPort.hasCommissionRules(categoryId)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(categoryId))
                .isInstanceOf(CategoryHasDependentRulesException.class)
                .hasMessageContaining("biểu phí hoa hồng");

        verify(categoryRepositoryPort, never()).deleteById(any());
    }

    @Test
    void shouldThrowDependentRules_whenCategoryHasSafetyRules() {
        UUID categoryId = UUID.randomUUID();
        when(categoryRepositoryPort.existsById(categoryId)).thenReturn(true);
        when(categoryRepositoryPort.hasLinkedServices(categoryId)).thenReturn(false);
        when(categoryRepositoryPort.hasChildren(categoryId)).thenReturn(false);
        when(categoryRepositoryPort.hasCommissionRules(categoryId)).thenReturn(false);
        when(categoryRepositoryPort.hasSafetyRules(categoryId)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(categoryId))
                .isInstanceOf(CategoryHasDependentRulesException.class)
                .hasMessageContaining("quy tắc an toàn");

        verify(categoryRepositoryPort, never()).deleteById(any());
    }
}
