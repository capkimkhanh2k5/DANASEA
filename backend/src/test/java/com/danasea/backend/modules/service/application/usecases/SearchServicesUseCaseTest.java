package com.danasea.backend.modules.service.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.danasea.backend.modules.service.application.dtos.SearchServicesCriteria;
import com.danasea.backend.modules.service.application.dtos.ServiceSummaryResult;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.ports.ServiceImageRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;

@ExtendWith(MockitoExtension.class)
class SearchServicesUseCaseTest {

    @Mock
    private ServiceRepositoryPort serviceRepositoryPort;

    @Mock
    private ServiceImageRepositoryPort serviceImageRepositoryPort;

    @InjectMocks
    private SearchServicesUseCase searchServicesUseCase;

    private Service service;

    @BeforeEach
    void setUp() {
        service = new Service();
        service.setId(UUID.randomUUID());
        service.setName("Test Service");
        service.setPrice(BigDecimal.valueOf(100));
        service.setDescription("A concise public description");
    }

    @Test
    void execute_ShouldReturnServiceSummaries() {
        SearchServicesCriteria criteria = SearchServicesCriteria.builder().page(0).size(20).build();
        
        when(serviceRepositoryPort.searchPublishedServices(criteria))
                .thenReturn(List.of(service));

        List<ServiceSummaryResult> results = searchServicesUseCase.execute(criteria);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Test Service");
        assertThat(results.get(0).getShortDescription()).isEqualTo("A concise public description");
    }
}
