package com.danasea.backend.modules.partner.application.usecases;

import com.danasea.backend.modules.partner.domain.models.PartnerApplication;
import com.danasea.backend.modules.partner.domain.models.PartnerApplicationStatus;
import com.danasea.backend.modules.partner.infrastructure.mappers.PartnerApplicationMapper;
import com.danasea.backend.modules.partner.infrastructure.persistence.entities.VendorRoleApplicationJpaEntity;
import com.danasea.backend.modules.partner.infrastructure.persistence.repositories.JpaVendorRoleApplicationRepository;
import com.danasea.backend.modules.partner.presentation.dtos.PartnerApplicationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAdminPartnerApplicationsUseCaseTest {

    @Mock
    private JpaVendorRoleApplicationRepository applicationRepository;

    @Mock
    private PartnerApplicationMapper mapper;

    @InjectMocks
    private GetAdminPartnerApplicationsUseCase useCase;

    private VendorRoleApplicationJpaEntity sampleEntity;
    private PartnerApplication sampleDomain;
    private PartnerApplicationResponse sampleResponse;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        pageable = PageRequest.of(0, 8);

        sampleEntity = new VendorRoleApplicationJpaEntity();
        sampleEntity.setId(id);
        sampleEntity.setUserId(userId);
        sampleEntity.setBusinessName("Test Sea Tours");
        sampleEntity.setStatus(PartnerApplicationStatus.PENDING);

        sampleDomain = PartnerApplication.builder()
                .id(id)
                .userId(userId)
                .businessName("Test Sea Tours")
                .status(PartnerApplicationStatus.PENDING)
                .build();

        sampleResponse = PartnerApplicationResponse.builder()
                .id(id)
                .userId(userId)
                .businessName("Test Sea Tours")
                .status(PartnerApplicationStatus.PENDING)
                .build();

        when(mapper.toDomain(sampleEntity)).thenReturn(sampleDomain);
        when(mapper.toResponse(sampleDomain)).thenReturn(sampleResponse);
    }

    @Test
    @DisplayName("When search is null and status is null, delegates to findAll")
    void whenSearchIsNullAndStatusIsNull_delegatesToFindAll() {
        when(applicationRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(sampleEntity)));

        Page<PartnerApplicationResponse> result = useCase.execute(null, null, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).businessName()).isEqualTo("Test Sea Tours");

        verify(applicationRepository).findAll(pageable);
        verifyNoMoreInteractions(applicationRepository);
    }

    @Test
    @DisplayName("When search is blank and status is null, delegates to findAll")
    void whenSearchIsBlankAndStatusIsNull_delegatesToFindAll() {
        when(applicationRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(sampleEntity)));

        Page<PartnerApplicationResponse> result = useCase.execute(null, "   ", pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);

        verify(applicationRepository).findAll(pageable);
        verifyNoMoreInteractions(applicationRepository);
    }

    @Test
    @DisplayName("When search is empty and status is PENDING, delegates to findByStatus")
    void whenSearchIsEmptyAndStatusIsProvided_delegatesToFindByStatus() {
        when(applicationRepository.findByStatus(PartnerApplicationStatus.PENDING, pageable))
                .thenReturn(new PageImpl<>(List.of(sampleEntity)));

        Page<PartnerApplicationResponse> result = useCase.execute(PartnerApplicationStatus.PENDING, "", pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);

        verify(applicationRepository).findByStatus(PartnerApplicationStatus.PENDING, pageable);
        verifyNoMoreInteractions(applicationRepository);
    }

    @Test
    @DisplayName("When search has keyword, normalizes to lowercase pattern and calls searchApplications")
    void whenSearchHasKeyword_callsSearchApplicationsWithNormalizedPattern() {
        when(applicationRepository.searchApplications(eq(PartnerApplicationStatus.PENDING), eq("%sea tours%"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(sampleEntity)));

        Page<PartnerApplicationResponse> result = useCase.execute(PartnerApplicationStatus.PENDING, "  Sea Tours  ", pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);

        verify(applicationRepository).searchApplications(PartnerApplicationStatus.PENDING, "%sea tours%", pageable);
        verifyNoMoreInteractions(applicationRepository);
    }

    @Test
    @DisplayName("When search has special characters, properly escapes them")
    void whenSearchHasSpecialCharacters_escapesSpecialCharacters() {
        when(applicationRepository.searchApplications(eq(null), eq("%100!%!_safe!!%"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(sampleEntity)));

        Page<PartnerApplicationResponse> result = useCase.execute(null, "100%_safe!", pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);

        verify(applicationRepository).searchApplications(null, "%100!%!_safe!!%", pageable);
        verifyNoMoreInteractions(applicationRepository);
    }
}
