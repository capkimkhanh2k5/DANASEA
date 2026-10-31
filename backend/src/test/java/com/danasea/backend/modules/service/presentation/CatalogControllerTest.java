package com.danasea.backend.modules.service.presentation;

import com.danasea.backend.modules.service.application.dtos.ServiceDetailResult;
import com.danasea.backend.modules.service.application.dtos.ServiceSummaryResult;
import com.danasea.backend.modules.service.application.usecases.GetPublicServiceDetailUseCase;
import com.danasea.backend.modules.service.application.usecases.SearchServicesUseCase;
import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CatalogControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SearchServicesUseCase searchServicesUseCase;

    @Mock
    private GetPublicServiceDetailUseCase getServiceDetailUseCase;

    @InjectMocks
    private CatalogController catalogController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(catalogController)
                .setControllerAdvice(new CatalogExceptionHandler())
                .build();
    }

    @Test
    void searchServices_ShouldReturn200() throws Exception {
        ServiceSummaryResult result = ServiceSummaryResult.builder()
                .id(UUID.randomUUID())
                .name("Public Service")
                .build();

        when(searchServicesUseCase.execute(any())).thenReturn(List.of(result));
        when(searchServicesUseCase.count(any())).thenReturn(1L);

        mockMvc.perform(get("/api/v1/catalog")
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Public Service"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void searchServices_CanonicalRoute_ShouldReturn200() throws Exception {
        when(searchServicesUseCase.execute(any())).thenReturn(List.of());
        when(searchServicesUseCase.count(any())).thenReturn(0L);

        mockMvc.perform(get("/api/services"))
                .andExpect(status().isOk());
    }

    @Test
    void searchServices_ShouldRejectInvalidBounds() throws Exception {
        mockMvc.perform(get("/api/services")
                .param("minPrice", "100")
                .param("maxPrice", "50")
                .param("size", "500"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getServiceDetail_ShouldReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        ServiceDetailResult result = ServiceDetailResult.builder()
                .id(id)
                .name("Service Name")
                .build();

        when(getServiceDetailUseCase.execute(eq(id), any(), any())).thenReturn(result);

        mockMvc.perform(get("/api/v1/catalog/" + id)
                .header("X-Session-Id", "sess-123")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Service Name"));
    }

    @Test
    void getServiceDetail_WhenNotFound_ShouldReturn404() throws Exception {
        UUID id = UUID.randomUUID();
        
        when(getServiceDetailUseCase.execute(eq(id), any(), any()))
                .thenThrow(new ServiceNotFoundException("Service not found"));

        mockMvc.perform(get("/api/v1/catalog/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SERVICE_NOT_FOUND"));
    }

    @Test
    void getServiceDetail_BySlug_ShouldReturn200() throws Exception {
        String slug = "tour-lan-bien-ngam-san-ho";
        ServiceDetailResult result = ServiceDetailResult.builder()
                .id(UUID.randomUUID())
                .name("Tour Lặn Biển Ngắm San Hô")
                .slug(slug)
                .build();

        when(getServiceDetailUseCase.executeByIdOrSlug(eq(slug), any(), any())).thenReturn(result);

        mockMvc.perform(get("/api/v1/catalog/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.name").value("Tour Lặn Biển Ngắm San Hô"));
    }

    @Test
    void generateSitemap_ShouldReturnValidXml() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<urlset")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("https://danasea.vn/")));
    }

    @Test
    void getOpenGraphPreview_ShouldReturnHtml() throws Exception {
        String slug = "tour-lan-bien";
        ServiceDetailResult result = ServiceDetailResult.builder()
                .id(UUID.randomUUID())
                .name("Tour Lặn Biển")
                .slug(slug)
                .description("Mô tả tour lặn")
                .build();

        when(getServiceDetailUseCase.readOnlySnapshotByIdOrSlug(eq(slug))).thenReturn(result);

        mockMvc.perform(get("/api/v1/catalog/og/" + slug))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("og:title")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(slug)));
    }
}
