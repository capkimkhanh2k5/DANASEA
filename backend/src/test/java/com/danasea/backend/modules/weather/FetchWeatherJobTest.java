package com.danasea.backend.modules.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.danasea.backend.modules.weather.application.dtos.WeatherInfoDto;
import com.danasea.backend.modules.weather.application.ports.output.WeatherCacheRepositoryPort;
import com.danasea.backend.modules.weather.application.ports.output.WeatherProviderPort;
import com.danasea.backend.modules.weather.domain.models.WeatherCache;
import com.danasea.backend.modules.weather.infrastructure.jobs.FetchWeatherJob;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class FetchWeatherJobTest {

    private static final double EXPECTED_LAT = 16.089035780716284;
    private static final double EXPECTED_LNG = 108.24959555394304;
    private static final String EXPECTED_LOCATION = "MAN_THAI_BEACH";

    @Mock
    private WeatherProviderPort weatherProviderPort;

    @Mock
    private WeatherCacheRepositoryPort weatherCacheRepositoryPort;

    private ObjectMapper objectMapper;
    private FetchWeatherJob job;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        job = new FetchWeatherJob(weatherProviderPort, weatherCacheRepositoryPort, objectMapper);
    }

    @Test
    @DisplayName("Success: queries both providers with exact coordinates, captures location, numeric fields, rawPayload and 30-min TTL")
    void fetchAndCacheWeatherInfoSuccess() throws Exception {
        WeatherInfoDto.WeatherData weatherData = WeatherInfoDto.WeatherData.builder()
                .temperature(29.5)
                .windSpeed(18.2)
                .precipitation(0.5)
                .cloudCover(20.0)
                .weatherCode(1)
                .sourceFetchedAt(Instant.now())
                .build();

        WeatherInfoDto.MarineData marineData = WeatherInfoDto.MarineData.builder()
                .waveHeight(1.2)
                .waveDirection(85.0)
                .wavePeriod(6.0)
                .seaSurfaceTemperature(28.0)
                .sourceFetchedAt(Instant.now())
                .build();

        when(weatherProviderPort.getWeatherByCoordinates(EXPECTED_LAT, EXPECTED_LNG)).thenReturn(weatherData);
        when(weatherProviderPort.getMarineByCoordinates(EXPECTED_LAT, EXPECTED_LNG)).thenReturn(marineData);

        OffsetDateTime beforeInvocation = OffsetDateTime.now().minusSeconds(1);
        job.fetchAndCacheWeatherInfo();
        OffsetDateTime afterInvocation = OffsetDateTime.now().plusSeconds(1);

        ArgumentCaptor<WeatherCache> captor = ArgumentCaptor.forClass(WeatherCache.class);
        verify(weatherCacheRepositoryPort).save(captor.capture());
        WeatherCache saved = captor.getValue();

        assertThat(saved.getLocationKey()).isEqualTo(EXPECTED_LOCATION);
        assertThat(saved.getWindSpeedKmh()).isEqualByComparingTo(BigDecimal.valueOf(18.2));
        assertThat(saved.getPrecipitationMm()).isEqualByComparingTo(BigDecimal.valueOf(0.5));
        assertThat(saved.getWaveHeightM()).isEqualByComparingTo(BigDecimal.valueOf(1.2));

        // Exact TTL: expiresAt must be exactly 30 minutes after fetchedAt
        assertThat(saved.getFetchedAt()).isAfterOrEqualTo(beforeInvocation).isBeforeOrEqualTo(afterInvocation);
        assertThat(Duration.between(saved.getFetchedAt(), saved.getExpiresAt())).isEqualTo(Duration.ofMinutes(30));

        // Raw payload deserializes to identical coordinates, weather, and marine values
        WeatherInfoDto deserialized = objectMapper.readValue(saved.getRawPayload(), WeatherInfoDto.class);
        assertThat(deserialized.getLatitude()).isEqualTo(EXPECTED_LAT);
        assertThat(deserialized.getLongitude()).isEqualTo(EXPECTED_LNG);
        assertThat(deserialized.getWeather().getWindSpeed()).isEqualTo(18.2);
        assertThat(deserialized.getWeather().getPrecipitation()).isEqualTo(0.5);
        assertThat(deserialized.getMarine().getWaveHeight()).isEqualTo(1.2);
    }

    @Test
    @DisplayName("Parallel execution: uses latches to prove weather and marine requests launch independently")
    void weatherAndMarineRequestsLaunchConcurrently() throws Exception {
        CountDownLatch weatherStarted = new CountDownLatch(1);
        CountDownLatch marineStarted = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);

        when(weatherProviderPort.getWeatherByCoordinates(EXPECTED_LAT, EXPECTED_LNG)).thenAnswer(inv -> {
            weatherStarted.countDown();
            boolean ok = releaseLatch.await(5, TimeUnit.SECONDS);
            assertThat(ok).isTrue();
            return WeatherInfoDto.WeatherData.builder().windSpeed(10.0).build();
        });

        when(weatherProviderPort.getMarineByCoordinates(EXPECTED_LAT, EXPECTED_LNG)).thenAnswer(inv -> {
            marineStarted.countDown();
            boolean ok = releaseLatch.await(5, TimeUnit.SECONDS);
            assertThat(ok).isTrue();
            return WeatherInfoDto.MarineData.builder().waveHeight(0.8).build();
        });

        CompletableFuture<Void> jobExecution = CompletableFuture.runAsync(() -> job.fetchAndCacheWeatherInfo());

        // Prove BOTH requests started concurrently before we release the latch
        boolean bothStarted = weatherStarted.await(5, TimeUnit.SECONDS) && marineStarted.await(5, TimeUnit.SECONDS);
        assertThat(bothStarted).isTrue();

        releaseLatch.countDown();
        jobExecution.get(5, TimeUnit.SECONDS);

        verify(weatherCacheRepositoryPort).save(any(WeatherCache.class));
    }

    @Test
    @DisplayName("Null handling: null weather, marine and numeric fields remain null without creating fake defaults")
    void nullFieldsRemainNullWithoutCreatingDefaults() throws Exception {
        WeatherInfoDto.WeatherData nullNumericWeather = WeatherInfoDto.WeatherData.builder()
                .windSpeed(null)
                .precipitation(null)
                .build();
        WeatherInfoDto.MarineData nullNumericMarine = WeatherInfoDto.MarineData.builder()
                .waveHeight(null)
                .build();

        when(weatherProviderPort.getWeatherByCoordinates(EXPECTED_LAT, EXPECTED_LNG)).thenReturn(nullNumericWeather);
        when(weatherProviderPort.getMarineByCoordinates(EXPECTED_LAT, EXPECTED_LNG)).thenReturn(nullNumericMarine);

        job.fetchAndCacheWeatherInfo();

        ArgumentCaptor<WeatherCache> captor = ArgumentCaptor.forClass(WeatherCache.class);
        verify(weatherCacheRepositoryPort).save(captor.capture());
        WeatherCache saved = captor.getValue();

        assertThat(saved.getWindSpeedKmh()).isNull();
        assertThat(saved.getPrecipitationMm()).isNull();
        assertThat(saved.getWaveHeightM()).isNull();

        WeatherInfoDto deserialized = objectMapper.readValue(saved.getRawPayload(), WeatherInfoDto.class);
        assertThat(deserialized.getWeather().getWindSpeed()).isNull();
        assertThat(deserialized.getWeather().getPrecipitation()).isNull();
        assertThat(deserialized.getMarine().getWaveHeight()).isNull();
    }

    @Test
    @DisplayName("Completely null provider data keeps cache fields null and does not throw")
    void completelyNullProviderDataLeavesCacheFieldsNull() throws Exception {
        when(weatherProviderPort.getWeatherByCoordinates(EXPECTED_LAT, EXPECTED_LNG)).thenReturn(null);
        when(weatherProviderPort.getMarineByCoordinates(EXPECTED_LAT, EXPECTED_LNG)).thenReturn(null);

        job.fetchAndCacheWeatherInfo();

        ArgumentCaptor<WeatherCache> captor = ArgumentCaptor.forClass(WeatherCache.class);
        verify(weatherCacheRepositoryPort).save(captor.capture());
        WeatherCache saved = captor.getValue();

        assertThat(saved.getWindSpeedKmh()).isNull();
        assertThat(saved.getPrecipitationMm()).isNull();
        assertThat(saved.getWaveHeightM()).isNull();

        WeatherInfoDto deserialized = objectMapper.readValue(saved.getRawPayload(), WeatherInfoDto.class);
        assertThat(deserialized.getWeather()).isNull();
        assertThat(deserialized.getMarine()).isNull();
    }

    @Test
    @DisplayName("providerFailureDoesNotPersistPartialCacheOrEscapeScheduledInvocation: failure does not save cache and terminates cleanly")
    void providerFailureDoesNotPersistPartialCacheOrEscapeScheduledInvocation() {
        when(weatherProviderPort.getWeatherByCoordinates(EXPECTED_LAT, EXPECTED_LNG))
                .thenThrow(new RuntimeException("Upstream weather API timeout"));
        when(weatherProviderPort.getMarineByCoordinates(EXPECTED_LAT, EXPECTED_LNG))
                .thenReturn(WeatherInfoDto.MarineData.builder().waveHeight(1.0).build());

        // Must NOT escape with an exception (scheduled invocation must terminate gracefully)
        assertThatCode(() -> job.fetchAndCacheWeatherInfo()).doesNotThrowAnyException();

        // Must NOT persist partial cache
        verify(weatherCacheRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("marine provider failure also terminates cleanly without saving partial cache")
    void marineProviderFailureDoesNotPersistPartialCacheOrEscape() {
        when(weatherProviderPort.getWeatherByCoordinates(EXPECTED_LAT, EXPECTED_LNG))
                .thenReturn(WeatherInfoDto.WeatherData.builder().windSpeed(12.0).build());
        when(weatherProviderPort.getMarineByCoordinates(EXPECTED_LAT, EXPECTED_LNG))
                .thenThrow(new RuntimeException("Upstream marine API connection error"));

        assertThatCode(() -> job.fetchAndCacheWeatherInfo()).doesNotThrowAnyException();
        verify(weatherCacheRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("JSON serialization failure is handled cleanly without escaping or saving cache")
    void jsonSerializationFailureHandledCleanly() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Serialization failed") {});

        FetchWeatherJob failingJob = new FetchWeatherJob(weatherProviderPort, weatherCacheRepositoryPort, failingMapper);

        when(weatherProviderPort.getWeatherByCoordinates(EXPECTED_LAT, EXPECTED_LNG))
                .thenReturn(WeatherInfoDto.WeatherData.builder().windSpeed(10.0).build());
        when(weatherProviderPort.getMarineByCoordinates(EXPECTED_LAT, EXPECTED_LNG))
                .thenReturn(WeatherInfoDto.MarineData.builder().waveHeight(0.5).build());

        assertThatCode(() -> failingJob.fetchAndCacheWeatherInfo()).doesNotThrowAnyException();
        verify(weatherCacheRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("Cache save failure terminates cleanly without infinite retries")
    void cacheSaveFailureTerminatesCleanlyWithoutInfiniteRetries() {
        when(weatherProviderPort.getWeatherByCoordinates(EXPECTED_LAT, EXPECTED_LNG))
                .thenReturn(WeatherInfoDto.WeatherData.builder().windSpeed(10.0).build());
        when(weatherProviderPort.getMarineByCoordinates(EXPECTED_LAT, EXPECTED_LNG))
                .thenReturn(WeatherInfoDto.MarineData.builder().waveHeight(0.5).build());

        doThrow(new RuntimeException("Database constraint error")).when(weatherCacheRepositoryPort).save(any());

        assertThatCode(() -> job.fetchAndCacheWeatherInfo()).doesNotThrowAnyException();
        verify(weatherCacheRepositoryPort, times(1)).save(any());
    }
}
