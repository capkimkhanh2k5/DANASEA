package com.danasea.backend.modules.weather;

import com.danasea.backend.modules.weather.application.dtos.AdvanceBookingSafetyRequest;
import com.danasea.backend.modules.weather.application.dtos.AdvanceBookingSafetyResponse;
import com.danasea.backend.modules.weather.application.usecases.CheckAdvanceBookingSafetyUseCase;
import com.danasea.backend.modules.weather.application.usecases.GetWeatherInfoUseCase;
import com.danasea.backend.modules.weather.presentation.controllers.WeatherController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentMatchers;

@ExtendWith(MockitoExtension.class)
class WeatherControllerContractTest {

    @Mock
    private GetWeatherInfoUseCase getWeatherInfoUseCase;

    @Mock
    private CheckAdvanceBookingSafetyUseCase checkAdvanceBookingSafetyUseCase;

    @Test
    void acceptsDocumentedCategoryIdAndTargetDateTimeContract() {
        WeatherController controller = new WeatherController(
                getWeatherInfoUseCase, checkAdvanceBookingSafetyUseCase);
        UUID categoryId = UUID.randomUUID();
        LocalDateTime targetDateTime = LocalDateTime.of(2026, 10, 1, 9, 30);
        AdvanceBookingSafetyResponse response = AdvanceBookingSafetyResponse.builder().build();
        when(checkAdvanceBookingSafetyUseCase.execute(ArgumentMatchers.any()))
                .thenReturn(response);

        controller.checkAdvanceBookingSafety(
                null,
                categoryId,
                null,
                16.05,
                108.20,
                targetDateTime,
                null,
                null,
                null);

        ArgumentCaptor<AdvanceBookingSafetyRequest> requestCaptor =
                ArgumentCaptor.forClass(AdvanceBookingSafetyRequest.class);
        verify(checkAdvanceBookingSafetyUseCase).execute(requestCaptor.capture());
        AdvanceBookingSafetyRequest request = requestCaptor.getValue();
        assertEquals(categoryId, request.getCategoryId());
        assertNull(request.getCategorySlug());
        assertEquals(targetDateTime.toLocalDate(), request.getBookingDate());
        assertEquals(targetDateTime.toLocalTime(), request.getStartTime());
        assertEquals(16.05, request.getLatitude());
        assertEquals(108.20, request.getLongitude());
    }
}
