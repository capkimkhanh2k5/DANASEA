package com.danasea.backend.modules.weather.infrastructure.jobs;

import com.danasea.backend.modules.weather.application.dtos.WeatherInfoDto;
import com.danasea.backend.modules.weather.application.ports.output.WeatherCacheRepositoryPort;
import com.danasea.backend.modules.weather.application.ports.output.WeatherProviderPort;
import com.danasea.backend.modules.weather.domain.models.WeatherCache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class FetchWeatherJob {

    private final WeatherProviderPort weatherProviderPort;
    private final WeatherCacheRepositoryPort weatherCacheRepositoryPort;
    private final ObjectMapper objectMapper;

    // Location of Man Thai Beach, Da Nang
    private static final double LATITUDE = 16.089035780716284;
    private static final double LONGITUDE = 108.24959555394304;
    private static final String LOCATION_KEY = "MAN_THAI_BEACH";

    @Scheduled(fixedRateString = "${app.scheduler.weather-fetch-rate:30m}")
    public void fetchAndCacheWeatherInfo() {
        log.info("Starting scheduled job: fetching weather and marine data for Man Thai Beach");

        try {
            CompletableFuture<WeatherInfoDto.WeatherData> weatherFuture = CompletableFuture
                    .supplyAsync(() -> weatherProviderPort.getWeatherByCoordinates(LATITUDE, LONGITUDE));

            CompletableFuture<WeatherInfoDto.MarineData> marineFuture = CompletableFuture
                    .supplyAsync(() -> weatherProviderPort.getMarineByCoordinates(LATITUDE, LONGITUDE));

            CompletableFuture.allOf(weatherFuture, marineFuture).join();

            WeatherInfoDto.WeatherData weatherData = weatherFuture.get();
            WeatherInfoDto.MarineData marineData = marineFuture.get();

            WeatherInfoDto dto = WeatherInfoDto.builder()
                    .latitude(LATITUDE)
                    .longitude(LONGITUDE)
                    .weather(weatherData)
                    .marine(marineData)
                    .build();

            String rawPayload = objectMapper.writeValueAsString(dto);

            OffsetDateTime fetchedAt = OffsetDateTime.now();
            WeatherCache cache = new WeatherCache();
            cache.setLocationKey(LOCATION_KEY);
            cache.setFetchedAt(fetchedAt);
            cache.setExpiresAt(fetchedAt.plusMinutes(30));
            cache.setRawPayload(rawPayload);

            // Set additional fields if needed for direct access
            if (weatherData != null) {
                if (weatherData.getWindSpeed() != null) {
                    cache.setWindSpeedKmh(BigDecimal.valueOf(weatherData.getWindSpeed()));
                }
                if (weatherData.getPrecipitation() != null) {
                    cache.setPrecipitationMm(BigDecimal.valueOf(weatherData.getPrecipitation()));
                }
            }

            if (marineData != null && marineData.getWaveHeight() != null) {
                cache.setWaveHeightM(BigDecimal.valueOf(marineData.getWaveHeight()));
            }

            weatherCacheRepositoryPort.save(cache);
            log.info("Successfully fetched and cached weather data.");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Weather fetch job interrupted", e);
        } catch (Exception e) {
            log.error("Failed to fetch or cache weather data", e);
        }
    }
}
