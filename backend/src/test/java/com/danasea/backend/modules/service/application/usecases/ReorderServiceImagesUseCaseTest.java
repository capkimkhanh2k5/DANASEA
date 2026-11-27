package com.danasea.backend.modules.service.application.usecases;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.UnauthorizedServiceAccessException;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceImageJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceImageRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReorderServiceImagesUseCaseTest {

    @Mock
    private JpaServiceRepository serviceRepository;

    @Mock
    private JpaServiceImageRepository serviceImageRepository;

    @Captor
    private ArgumentCaptor<List<ServiceImageJpaEntity>> imagesCaptor;

    private ReorderServiceImagesUseCase useCase;

    private UUID serviceId;
    private UUID vendorId;
    private ServiceJpaEntity serviceEntity;

    private ServiceImageJpaEntity img1;
    private ServiceImageJpaEntity img2;
    private ServiceImageJpaEntity img3;

    @BeforeEach
    void setUp() {
        useCase = new ReorderServiceImagesUseCase(serviceRepository, serviceImageRepository);

        serviceId = UUID.randomUUID();
        vendorId = UUID.randomUUID();

        serviceEntity = new ServiceJpaEntity();
        serviceEntity.setId(serviceId);
        serviceEntity.setVendorId(vendorId);
        serviceEntity.setName("Lặn Biển Cù Lao Chàm");

        img1 = new ServiceImageJpaEntity();
        img1.setId(UUID.randomUUID());
        img1.setServiceId(serviceId);
        img1.setUrl("https://res.cloudinary.com/danasea/image/upload/img1.jpg");
        img1.setSortOrder((short) 1);

        img2 = new ServiceImageJpaEntity();
        img2.setId(UUID.randomUUID());
        img2.setServiceId(serviceId);
        img2.setUrl("https://res.cloudinary.com/danasea/image/upload/img2.jpg");
        img2.setSortOrder((short) 2);

        img3 = new ServiceImageJpaEntity();
        img3.setId(UUID.randomUUID());
        img3.setServiceId(serviceId);
        img3.setUrl("https://res.cloudinary.com/danasea/image/upload/img3.jpg");
        img3.setSortOrder((short) 3);
    }

    @Test
    @DisplayName("AC2.1: Sắp xếp lại thứ tự ảnh thành công khi danh sách ID hợp lệ")
    void shouldReorderImagesSuccessfully() {
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(serviceImageRepository.findByServiceIdOrderBySortOrderAsc(serviceId))
                .thenReturn(List.of(img1, img2, img3));

        // Thứ tự mới mong muốn: img3, img1, img2
        List<UUID> newOrder = List.of(img3.getId(), img1.getId(), img2.getId());

        useCase.execute(serviceId, vendorId, newOrder);

        verify(serviceImageRepository).saveAll(imagesCaptor.capture());
        List<ServiceImageJpaEntity> savedImages = imagesCaptor.getValue();

        assertEquals(3, savedImages.size());

        // Kiểm tra img3 lên đầu (sortOrder = 1)
        ServiceImageJpaEntity savedImg3 = savedImages.stream()
                .filter(i -> i.getId().equals(img3.getId()))
                .findFirst().orElseThrow();
        assertEquals((short) 1, savedImg3.getSortOrder());

        // img1 về thứ 2 (sortOrder = 2)
        ServiceImageJpaEntity savedImg1 = savedImages.stream()
                .filter(i -> i.getId().equals(img1.getId()))
                .findFirst().orElseThrow();
        assertEquals((short) 2, savedImg1.getSortOrder());

        // img2 về thứ 3 (sortOrder = 3)
        ServiceImageJpaEntity savedImg2 = savedImages.stream()
                .filter(i -> i.getId().equals(img2.getId()))
                .findFirst().orElseThrow();
        assertEquals((short) 3, savedImg2.getSortOrder());
    }

    @Test
    @DisplayName("AC2.2: Chặn thao túng khi truyền imageId thuộc Service khác (Cross-service Tampering)")
    void shouldThrowWhenImageBelongsToDifferentService() {
        UUID foreignImageId = UUID.randomUUID();

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(serviceImageRepository.findByServiceIdOrderBySortOrderAsc(serviceId))
                .thenReturn(List.of(img1, img2, img3));

        // Danh sách chứa 1 imageId của service khác
        List<UUID> tamperingOrder = List.of(foreignImageId, img1.getId(), img2.getId());

        assertThrows(RuntimeException.class, () ->
                useCase.execute(serviceId, vendorId, tamperingOrder)
        );

        verify(serviceImageRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("AC2.3: Chặn khi danh sách ID truyền vào thiếu hoặc thừa so với ảnh hiện có")
    void shouldThrowWhenImageCountMismatch() {
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(serviceImageRepository.findByServiceIdOrderBySortOrderAsc(serviceId))
                .thenReturn(List.of(img1, img2, img3));

        // Truyền thiếu (chỉ có 2 id trong khi service có 3 ảnh)
        List<UUID> incompleteOrder = List.of(img1.getId(), img2.getId());

        assertThrows(IllegalArgumentException.class, () ->
                useCase.execute(serviceId, vendorId, incompleteOrder)
        );

        verify(serviceImageRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("AC2.4: Chặn khi danh sách ID chứa phần tử trùng lặp")
    void shouldThrowWhenDuplicateImageIdsProvided() {
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(serviceImageRepository.findByServiceIdOrderBySortOrderAsc(serviceId))
                .thenReturn(List.of(img1, img2, img3));

        // Danh sách trùng id img1 hai lần
        List<UUID> duplicateOrder = List.of(img1.getId(), img1.getId(), img3.getId());

        assertThrows(IllegalArgumentException.class, () ->
                useCase.execute(serviceId, vendorId, duplicateOrder)
        );

        verify(serviceImageRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("AC2.5: Chặn khi Vendor không sở hữu Service (HTTP 403)")
    void shouldThrowWhenNotServiceOwner() {
        UUID otherVendorId = UUID.randomUUID();

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceEntity));

        List<UUID> order = List.of(img1.getId(), img2.getId(), img3.getId());

        assertThrows(UnauthorizedServiceAccessException.class, () ->
                useCase.execute(serviceId, otherVendorId, order)
        );

        verify(serviceImageRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("AC2.6: Chặn khi Service không tồn tại (HTTP 404)")
    void shouldThrowWhenServiceNotFound() {
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.empty());

        List<UUID> order = List.of(img1.getId(), img2.getId(), img3.getId());

        assertThrows(ServiceNotFoundException.class, () ->
                useCase.execute(serviceId, vendorId, order)
        );

        verifyNoInteractions(serviceImageRepository);
    }
}
