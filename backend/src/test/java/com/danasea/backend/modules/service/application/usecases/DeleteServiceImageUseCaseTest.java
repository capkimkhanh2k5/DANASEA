package com.danasea.backend.modules.service.application.usecases;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.danasea.backend.modules.service.application.ports.FileStoragePort;
import com.danasea.backend.modules.service.domain.exceptions.ImageNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.UnauthorizedServiceAccessException;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceImageJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceImageRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;

class DeleteServiceImageUseCaseTest {
    private final JpaServiceRepository services = mock(JpaServiceRepository.class);
    private final JpaServiceImageRepository images = mock(JpaServiceImageRepository.class);
    private final FileStoragePort storage = mock(FileStoragePort.class);
    private final DeleteServiceImageUseCase useCase = new DeleteServiceImageUseCase(services, images, storage);
    private final UUID serviceId = UUID.randomUUID(), imageId = UUID.randomUUID(), ownerId = UUID.randomUUID();

    @BeforeEach
    void service() {
        var service = new ServiceJpaEntity();
        service.setId(serviceId);
        service.setVendorId(ownerId);
        when(services.findById(serviceId)).thenReturn(Optional.of(service));
    }

    @Test
    void deletingOwnedImageBindsDatabaseDeletionAndStoredUrlToTheSameService() {
        var image = new ServiceImageJpaEntity();
        image.setId(imageId);
        image.setServiceId(serviceId);
        image.setUrl("https://cdn.example.com/service/owned.jpg");
        when(images.findByIdAndServiceId(imageId, serviceId)).thenReturn(Optional.of(image));

        useCase.execute(serviceId, imageId, ownerId);

        var writes = inOrder(images, storage);
        writes.verify(images).deleteByIdAndServiceId(imageId, serviceId);
        writes.verify(storage).deleteFile("https://cdn.example.com/service/owned.jpg");
    }

    @Test
    void missingServiceCannotDeleteAnyImageOrFile() {
        when(services.findById(serviceId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(serviceId, imageId, ownerId))
                .isInstanceOf(ServiceNotFoundException.class);
        verifyNoInteractions(images, storage);
    }

    @Test
    void anotherVendorCannotLookUpOrDeleteTheOwnersImage() {
        assertThatThrownBy(() -> useCase.execute(serviceId, imageId, UUID.randomUUID()))
                .isInstanceOf(UnauthorizedServiceAccessException.class);
        verifyNoInteractions(images, storage);
    }

    @Test
    void imageFromAnotherServiceCannotBeDeletedThroughThisService() {
        when(images.findByIdAndServiceId(imageId, serviceId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(serviceId, imageId, ownerId))
                .isInstanceOf(ImageNotFoundException.class);
        verify(images, never()).deleteByIdAndServiceId(any(), any());
        verifyNoInteractions(storage);
    }
}
