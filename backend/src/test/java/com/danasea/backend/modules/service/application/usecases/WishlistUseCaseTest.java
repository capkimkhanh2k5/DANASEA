package com.danasea.backend.modules.service.application.usecases;

import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.Wishlist;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceImageRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.WishlistRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WishlistUseCaseTest {

    @Mock
    private WishlistRepositoryPort wishlistRepositoryPort;

    @Mock
    private ServiceRepositoryPort serviceRepositoryPort;

    @Mock
    private ServiceImageRepositoryPort serviceImageRepositoryPort;

    @InjectMocks
    private WishlistUseCase wishlistUseCase;

    @Test
    void addWishlist_ShouldSave_WhenNotExists() {
        UUID userId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        
        when(serviceRepositoryPort.findPublishedById(serviceId))
                .thenReturn(Optional.of(Service.builder().id(serviceId).build()));
        when(wishlistRepositoryPort.existsByUserIdAndServiceId(userId, serviceId)).thenReturn(false);

        wishlistUseCase.addWishlist(userId, serviceId);

        verify(wishlistRepositoryPort, times(1)).save(any(Wishlist.class));
    }

    @Test
    void addWishlist_ShouldBeIdempotent_WhenExists() {
        UUID userId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        
        when(serviceRepositoryPort.findPublishedById(serviceId))
                .thenReturn(Optional.of(Service.builder().id(serviceId).build()));
        when(wishlistRepositoryPort.existsByUserIdAndServiceId(userId, serviceId)).thenReturn(true);

        wishlistUseCase.addWishlist(userId, serviceId);

        verify(wishlistRepositoryPort, never()).save(any(Wishlist.class));
    }

    @Test
    void addWishlist_ShouldThrow_WhenServiceNotFound() {
        UUID userId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        
        when(serviceRepositoryPort.findPublishedById(serviceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wishlistUseCase.addWishlist(userId, serviceId))
                .isInstanceOf(ServiceNotFoundException.class);
    }
}
