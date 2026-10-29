package com.danasea.backend.modules.weather.application.usecases;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.danasea.backend.modules.booking.domain.models.Booking;
import java.time.Clock;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaCategoryRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.weather.application.dtos.AdvanceBookingSafetyRequest;
import com.danasea.backend.modules.weather.application.dtos.AdvanceBookingSafetyResponse;
import com.danasea.backend.modules.weather.application.dtos.WeatherInfoDto;
import com.danasea.backend.modules.weather.application.ports.output.WeatherProviderPort;
import com.danasea.backend.modules.weather.domain.models.CategorySafetyRule;
import com.danasea.backend.modules.weather.domain.services.CategorySafetyRuleService;
import com.danasea.backend.modules.weather.domain.services.WeatherRuleEngine;
import com.danasea.backend.modules.weather.infrastructure.adapters.WeatherForecastCacheService;
import com.danasea.backend.modules.weather.infrastructure.adapters.WeatherProviderAdapter;
import com.danasea.backend.modules.weather.infrastructure.api.OpenMeteoApiClient;
import com.danasea.backend.modules.weather.infrastructure.api.dtos.OpenMeteoWeatherResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Empirical Adversarial Challenge Suite for Milestone 2:
 * Stress-testing CheckAdvanceBookingSafetyUseCase and WeatherProviderAdapter.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdvanceBookingSafetyAdversarialTest - Empirical Challenger 2 Suite")
public class AdvanceBookingSafetyAdversarialTest {

    @Mock
    private CategorySafetyRuleService categorySafetyRuleService;

    @Mock
    private WeatherProviderPort weatherProviderPort;

    @Mock
    private JpaServiceSlotRepository slotRepository;

    @Mock
    private JpaServiceRepository serviceRepository;

    @Mock
    private JpaCategoryRepository categoryRepository;

    @Mock
    private OpenMeteoApiClient mockApiClient;

    private WeatherRuleEngine weatherRuleEngine;
    private CheckAdvanceBookingSafetyUseCase useCase;

    private final LocalDate today = LocalDate.now(Booking.VIETNAM_ZONE);
    private CategorySafetyRule supRule;
    private CategorySafetyRule parasailingRule;

    @BeforeEach
    void setUp() {
        weatherRuleEngine = new WeatherRuleEngine();
        useCase = new CheckAdvanceBookingSafetyUseCase(
                categorySafetyRuleService,
                weatherProviderPort,
                weatherRuleEngine,
                slotRepository,
                serviceRepository,
                categoryRepository,
                Clock.system(Booking.VIETNAM_ZONE)
        );

        supRule = CategorySafetyRule.getBySlug("cheo-sup-kayak");
        parasailingRule = CategorySafetyRule.getBySlug("cano-du-bay");
    }

    // =========================================================================
    // 1. EXACT BOUNDARIES (7, 8, 9, 14 DAYS AHEAD)
    // =========================================================================
    @Nested
    @DisplayName("Dimension 1: Exact Boundary Verification")
    class ExactBoundaryTests {

        @Test
        @DisplayName("Boundary: Exactly 7 days ahead -> FULL_MARINE, isProvisional=false, marineCutoffExceeded=false")
        void testExactBoundary_7DaysAhead() {
            LocalDate day7 = today.plusDays(7);
            when(categorySafetyRuleService.getRuleByCategorySlug("cheo-sup-kayak")).thenReturn(supRule);

            WeatherInfoDto.TimeWindowForecast forecast = WeatherInfoDto.TimeWindowForecast.builder().sourceFetchedAt(Instant.now()).validUntil(Instant.now().plusSeconds(1800))
                    .peakWaveHeight(0.4)
                    .peakWindSpeed(10.0)
                    .peakWindGust(14.0)
                    .peakOceanCurrent(0.1)
                    .minVisibility(8000.0)
                    .severeWeatherCode(1)
                    .build();

            when(weatherProviderPort.getTimeWindowForecast(anyDouble(), anyDouble(), eq(day7), any(), any()))
                    .thenReturn(forecast);

            AdvanceBookingSafetyResponse res = useCase.execute(
                    AdvanceBookingSafetyRequest.builder().categorySlug("cheo-sup-kayak").bookingDate(day7).build(),
                    today
            );

            assertEquals(7, res.getDaysAhead());
            assertEquals(7, res.getDaysInAdvance());
            assertFalse(res.isProvisional(), "Day 7 must NOT be provisional");
            assertFalse(res.isMarineCutoffExceeded(), "Day 7 must NOT exceed marine cutoff");
            assertEquals("FULL_MARINE_AND_METEOROLOGY", res.getDataCoverage());
            assertEquals(0.4, res.getPeakWaveHeightM());
            assertEquals("GREEN", res.getSafetyStatus());
            assertTrue(res.isSafe());
        }

        @Test
        @DisplayName("Boundary: Exactly 8 days ahead -> FULL_MARINE, isProvisional=false, marineCutoffExceeded=false")
        void testExactBoundary_8DaysAhead() {
            LocalDate day8 = today.plusDays(8);
            when(categorySafetyRuleService.getRuleByCategorySlug("cheo-sup-kayak")).thenReturn(supRule);

            WeatherInfoDto.TimeWindowForecast forecast = WeatherInfoDto.TimeWindowForecast.builder().sourceFetchedAt(Instant.now()).validUntil(Instant.now().plusSeconds(1800))
                    .peakWaveHeight(0.45)
                    .peakWindSpeed(12.0)
                    .peakWindGust(16.0)
                    .peakOceanCurrent(0.15)
                    .minVisibility(8000.0)
                    .severeWeatherCode(0)
                    .build();

            when(weatherProviderPort.getTimeWindowForecast(anyDouble(), anyDouble(), eq(day8), any(), any()))
                    .thenReturn(forecast);

            AdvanceBookingSafetyResponse res = useCase.execute(
                    AdvanceBookingSafetyRequest.builder().categorySlug("cheo-sup-kayak").bookingDate(day8).build(),
                    today
            );

            assertEquals(8, res.getDaysAhead());
            assertFalse(res.isProvisional(), "Day 8 is the last day of Marine API, must NOT be provisional");
            assertFalse(res.isMarineCutoffExceeded(), "Day 8 must NOT exceed marine cutoff");
            assertEquals("FULL_MARINE_AND_METEOROLOGY", res.getDataCoverage());
            assertEquals(0.45, res.getPeakWaveHeightM());
            assertEquals("GREEN", res.getSafetyStatus());
        }

        @Test
        @DisplayName("Boundary: Exactly 9 days ahead -> Marine Cutoff Exceeded, isProvisional=true, peakWaveHeightM=null in response")
        void testExactBoundary_9DaysAhead() {
            LocalDate day9 = today.plusDays(9);
            when(categorySafetyRuleService.getRuleByCategorySlug("cheo-sup-kayak")).thenReturn(supRule);

            // Day 9: Marine API has no data (wave is null) with gentle atmospheric weather
            WeatherInfoDto.TimeWindowForecast forecast = WeatherInfoDto.TimeWindowForecast.builder().sourceFetchedAt(Instant.now()).validUntil(Instant.now().plusSeconds(1800))
                    .peakWaveHeight(null)
                    .peakWindSpeed(10.0)
                    .peakWindGust(15.0)
                    .peakOceanCurrent(null)
                    .minVisibility(7000.0)
                    .severeWeatherCode(1)
                    .build();

            when(weatherProviderPort.getTimeWindowForecast(anyDouble(), anyDouble(), eq(day9), any(), any()))
                    .thenReturn(forecast);

            AdvanceBookingSafetyResponse res = useCase.execute(
                    AdvanceBookingSafetyRequest.builder().categorySlug("cheo-sup-kayak").bookingDate(day9).build(),
                    today
            );

            assertEquals(9, res.getDaysAhead());
            assertTrue(res.isProvisional(), "Day 9 must be provisional");
            assertTrue(res.isMarineCutoffExceeded(), "Day 9 must exceed marine cutoff");
            assertEquals("METEOROLOGY_ESTIMATED_MARINE", res.getDataCoverage());
            assertNull(res.getPeakWaveHeightM(), "Day 9 peakWaveHeightM in response must be null");
            assertNull(res.getPeakOceanCurrentMs(), "Day 9 peakOceanCurrentMs in response must be null");
            assertTrue(res.isEstimatedMarine());
            assertEquals("GREEN", res.getSafetyStatus());
            assertTrue(res.isSafe());
        }

        @Test
        @DisplayName("Boundary: Exactly 14 days ahead -> isProvisional=true, advisory details include 7-14 day note")
        void testExactBoundary_14DaysAhead() {
            LocalDate day14 = today.plusDays(14);
            when(categorySafetyRuleService.getRuleByCategorySlug("cheo-sup-kayak")).thenReturn(supRule);

            WeatherInfoDto.TimeWindowForecast forecast = WeatherInfoDto.TimeWindowForecast.builder().sourceFetchedAt(Instant.now()).validUntil(Instant.now().plusSeconds(1800))
                    .peakWaveHeight(null)
                    .peakWindSpeed(10.0)
                    .peakWindGust(14.0)
                    .minVisibility(6000.0)
                    .severeWeatherCode(0)
                    .build();

            when(weatherProviderPort.getTimeWindowForecast(anyDouble(), anyDouble(), eq(day14), any(), any()))
                    .thenReturn(forecast);

            AdvanceBookingSafetyResponse res = useCase.execute(
                    AdvanceBookingSafetyRequest.builder().categorySlug("cheo-sup-kayak").bookingDate(day14).build(),
                    today
            );

            assertEquals(14, res.getDaysAhead());
            assertTrue(res.isProvisional());
            assertTrue(res.isMarineCutoffExceeded());
            assertTrue(res.isSafe());
            assertTrue(res.getDetails().stream().anyMatch(d -> d.contains("14 days ahead")));
        }
    }

    // =========================================================================
    // 2. OUT-OF-BOUNDS VERIFICATION (6 DAYS, 15 DAYS, 16 DAYS, 17+ DAYS)
    // =========================================================================
    @Nested
    @DisplayName("Dimension 2: Out of Bounds Verification")
    class OutOfBoundsTests {

        @Test
        @DisplayName("Out of bounds check: 6 days ahead is NOT rejected by execute() — evaluates with full data")
        void testOutOfBounds_6DaysAhead_isNotRejected() {
            LocalDate day6 = today.plusDays(6);
            when(categorySafetyRuleService.getRuleByCategorySlug("cheo-sup-kayak")).thenReturn(supRule);

            WeatherInfoDto.TimeWindowForecast forecast = WeatherInfoDto.TimeWindowForecast.builder().sourceFetchedAt(Instant.now()).validUntil(Instant.now().plusSeconds(1800))
                    .peakWaveHeight(0.35)
                    .peakWindSpeed(10.0)
                    .peakWindGust(15.0)
                    .minVisibility(8000.0)
                    .severeWeatherCode(0)
                    .build();

            when(weatherProviderPort.getTimeWindowForecast(anyDouble(), anyDouble(), eq(day6), any(), any()))
                    .thenReturn(forecast);

            AdvanceBookingSafetyResponse res = useCase.execute(
                    AdvanceBookingSafetyRequest.builder().categorySlug("cheo-sup-kayak").bookingDate(day6).build(),
                    today
            );

            assertNotNull(res);
            assertEquals(6, res.getDaysAhead());
            assertTrue(res.isSafe(), "6 days ahead should be accepted and evaluated as safe");
            assertFalse(res.isMarineCutoffExceeded(), "6 days is within 8-day marine range");
        }

        @Test
        @DisplayName("Out of bounds check: 15 days ahead is NOT rejected by execute() — evaluates with meteorological model")
        void testOutOfBounds_15DaysAhead_isNotRejected() {
            LocalDate day15 = today.plusDays(15);
            when(categorySafetyRuleService.getRuleByCategorySlug("cheo-sup-kayak")).thenReturn(supRule);

            WeatherInfoDto.TimeWindowForecast forecast = WeatherInfoDto.TimeWindowForecast.builder().sourceFetchedAt(Instant.now()).validUntil(Instant.now().plusSeconds(1800))
                    .peakWaveHeight(null)
                    .peakWindSpeed(12.0)
                    .peakWindGust(16.0)
                    .minVisibility(5000.0)
                    .severeWeatherCode(1)
                    .build();

            when(weatherProviderPort.getTimeWindowForecast(anyDouble(), anyDouble(), eq(day15), any(), any()))
                    .thenReturn(forecast);

            AdvanceBookingSafetyResponse res = useCase.execute(
                    AdvanceBookingSafetyRequest.builder().categorySlug("cheo-sup-kayak").bookingDate(day15).build(),
                    today
            );

            assertNotNull(res);
            assertEquals(15, res.getDaysAhead());
            assertTrue(res.isSafe());
            assertTrue(res.isProvisional());
            assertTrue(res.isMarineCutoffExceeded());
        }

        @Test
        @DisplayName("Out of bounds check: 16 days ahead is boundary limit — evaluates with meteorological model")
        void testOutOfBounds_16DaysAhead_boundaryLimit() {
            LocalDate day16 = today.plusDays(16);
            when(categorySafetyRuleService.getRuleByCategorySlug("cheo-sup-kayak")).thenReturn(supRule);

            WeatherInfoDto.TimeWindowForecast forecast = WeatherInfoDto.TimeWindowForecast.builder().sourceFetchedAt(Instant.now()).validUntil(Instant.now().plusSeconds(1800))
                    .peakWaveHeight(null)
                    .peakWindSpeed(10.0)
                    .peakWindGust(15.0)
                    .build();

            when(weatherProviderPort.getTimeWindowForecast(anyDouble(), anyDouble(), eq(day16), any(), any()))
                    .thenReturn(forecast);

            AdvanceBookingSafetyResponse res = useCase.execute(
                    AdvanceBookingSafetyRequest.builder().categorySlug("cheo-sup-kayak").bookingDate(day16).build(),
                    today
            );

            assertNotNull(res);
            assertEquals(16, res.getDaysAhead());
            assertEquals("METEOROLOGY_ESTIMATED_MARINE", res.getDataCoverage());
        }

        @Test
        @DisplayName("Out of bounds check: 17 days ahead via execute() returns OUT_OF_RANGE coverage")
        void testOutOfBounds_17DaysAhead_viaExecute_returnsOutOfRange() {
            LocalDate day17 = today.plusDays(17);
            when(categorySafetyRuleService.getRuleByCategorySlug("cheo-sup-kayak")).thenReturn(supRule);

            AdvanceBookingSafetyResponse res = useCase.execute(
                    AdvanceBookingSafetyRequest.builder().categorySlug("cheo-sup-kayak").bookingDate(day17).build(),
                    today
            );

            assertNotNull(res);
            assertEquals(17, res.getDaysAhead());
            assertEquals("OUT_OF_RANGE", res.getDataCoverage());
            assertEquals(WeatherRuleEngine.ALERT_YELLOW, res.getAlertLevel());
            assertTrue(res.getWarningMessage().contains("17 days ahead"));
            verify(weatherProviderPort, never()).getTimeWindowForecast(anyDouble(), anyDouble(), any(), any(), any());
        }

        @Test
        @DisplayName("Out of bounds check: 17 days ahead via checkSafety() throws IllegalArgumentException")
        void testOutOfBounds_17DaysAhead_viaCheckSafety_throwsIllegalArgumentException() {
            LocalDate day17 = today.plusDays(17);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    useCase.checkSafety(UUID.randomUUID(), UUID.randomUUID(), "cheo-sup-kayak",
                            day17, LocalTime.of(8, 0), LocalTime.of(10, 0), 16.089, 108.249)
            );

            assertTrue(ex.getMessage().contains("exceeds the 16-day Open-Meteo forecast range"));
        }

        @Test
        @DisplayName("Past date check: negative daysInAdvance throws IllegalArgumentException")
        void testOutOfBounds_pastDate_throwsException() {
            LocalDate pastDate = today.minusDays(2);
            assertThrows(IllegalArgumentException.class, () ->
                    useCase.execute(AdvanceBookingSafetyRequest.builder().bookingDate(pastDate).build(), today)
            );
        }
    }

    // =========================================================================
    // 3. EXTREME INPUTS & RESILIENCE
    // =========================================================================
    @Nested
    @DisplayName("Dimension 3: Extreme Inputs & Resilience")
    class ExtremeInputTests {

        @Test
        @DisplayName("Extreme input: checkBySlotId with non-existent slotId throws IllegalArgumentException")
        void testExtremeInput_slotNotFound_throwsIllegalArgumentException() {
            UUID unknownSlotId = UUID.randomUUID();
            when(slotRepository.findById(unknownSlotId)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    useCase.checkBySlotId(unknownSlotId)
            );
            assertTrue(ex.getMessage().contains("Service slot not found"));
        }

        @Test
        @DisplayName("Extreme input: checkBySlotId with null slotId throws IllegalArgumentException")
        void testExtremeInput_nullSlotId_throwsIllegalArgumentException() {
            when(slotRepository.findById(null)).thenThrow(new IllegalArgumentException("The given id must not be null!"));

            assertThrows(IllegalArgumentException.class, () -> useCase.checkBySlotId(null));
        }

        @Test
        @DisplayName("Extreme input: execute with null bookingDate throws IllegalArgumentException")
        void testExtremeInput_nullBookingDate_throwsException() {
            AdvanceBookingSafetyRequest req = AdvanceBookingSafetyRequest.builder().bookingDate(null).build();
            assertThrows(IllegalArgumentException.class, () -> useCase.execute(req, today));
        }

        @Test
        @DisplayName("Extreme input: Unknown category slug falls back safely to DEFAULT_RULE without 500")
        void testExtremeInput_unknownCategorySlug_fallsBackToDefaultRule() {
            LocalDate date = today.plusDays(7);
            when(categorySafetyRuleService.getRuleByCategorySlug("unknown-sport-xyz"))
                    .thenReturn(CategorySafetyRule.DEFAULT_RULE);

            WeatherInfoDto.TimeWindowForecast forecast = WeatherInfoDto.TimeWindowForecast.builder().sourceFetchedAt(Instant.now()).validUntil(Instant.now().plusSeconds(1800))
                    .peakWaveHeight(0.5)
                    .peakWindSpeed(10.0)
                    .build();

            when(weatherProviderPort.getTimeWindowForecast(anyDouble(), anyDouble(), eq(date), any(), any()))
                    .thenReturn(forecast);

            AdvanceBookingSafetyResponse res = useCase.execute(
                    AdvanceBookingSafetyRequest.builder().categorySlug("unknown-sport-xyz").bookingDate(date).build(),
                    today
            );

            assertNotNull(res);
            assertEquals("default", res.getCategorySlug());
            assertEquals(CategorySafetyRule.DEFAULT_RULE.getCategoryName(), res.getCategoryName());
        }

        @Test
        @DisplayName("Extreme input: Null wave data on Day 8 vs Day 9 behavioral difference")
        void testExtremeInput_nullWaveOnDay8VsDay9() {
            when(categorySafetyRuleService.getRuleByCategorySlug("cheo-sup-kayak")).thenReturn(supRule);

            WeatherInfoDto.TimeWindowForecast forecastDay8 = WeatherInfoDto.TimeWindowForecast.builder().sourceFetchedAt(Instant.now()).validUntil(Instant.now().plusSeconds(1800))
                    .peakWaveHeight(null)
                    .peakWindSpeed(20.0)
                    .build();

            when(weatherProviderPort.getTimeWindowForecast(anyDouble(), anyDouble(), eq(today.plusDays(8)), any(), any()))
                    .thenReturn(forecastDay8);

            // On Day 8 with null wave:
            AdvanceBookingSafetyResponse resDay8 = useCase.execute(
                    AdvanceBookingSafetyRequest.builder().categorySlug("cheo-sup-kayak").bookingDate(today.plusDays(8)).build(),
                    today
            );

            // Wave should be estimated from wind: Hs ≈ 0.024 * (20)^1.15 ≈ 0.74m
            assertFalse(resDay8.isMarineCutoffExceeded(), "Day 8 marineCutoffExceeded is false");
            assertTrue(resDay8.isEstimatedMarine(), "Wave was null so estimatedMarine is true");
            assertNotNull(resDay8.getPeakWaveHeightM(), "On Day 8, estimated wave is populated in response");
            assertEquals(0.74, resDay8.getPeakWaveHeightM(), 0.05);

            // On Day 9 with null wave:
            WeatherInfoDto.TimeWindowForecast forecastDay9 = WeatherInfoDto.TimeWindowForecast.builder().sourceFetchedAt(Instant.now()).validUntil(Instant.now().plusSeconds(1800))
                    .peakWaveHeight(null)
                    .peakWindSpeed(20.0)
                    .build();

            when(weatherProviderPort.getTimeWindowForecast(anyDouble(), anyDouble(), eq(today.plusDays(9)), any(), any()))
                    .thenReturn(forecastDay9);

            AdvanceBookingSafetyResponse resDay9 = useCase.execute(
                    AdvanceBookingSafetyRequest.builder().categorySlug("cheo-sup-kayak").bookingDate(today.plusDays(9)).build(),
                    today
            );

            assertTrue(resDay9.isMarineCutoffExceeded(), "Day 9 marineCutoffExceeded is true");
            assertTrue(resDay9.isProvisional(), "Day 9 is provisional");
            assertNull(resDay9.getPeakWaveHeightM(), "On Day 9, peakWaveHeightM is masked as null in response");
        }

        @Test
        @DisplayName("Extreme input: WeatherProviderAdapter handles null and missing hourly timestamps without NPE")
        void testExtremeInput_missingHourlyTimestampsInAdapter() {
            WeatherForecastCacheService cache = new WeatherForecastCacheService();
            WeatherProviderAdapter adapter = new WeatherProviderAdapter(mockApiClient, cache);

            OpenMeteoWeatherResponse weatherResp = new OpenMeteoWeatherResponse();
            OpenMeteoWeatherResponse.HourlyData hourlyWeather = new OpenMeteoWeatherResponse.HourlyData();
            // Timestamps list contains invalid format and nulls
            hourlyWeather.setTime(List.of("invalid-timestamp", "2026-09-25T08:00"));
            hourlyWeather.setWindSpeed10m(List.of(15.0, 18.0));
            weatherResp.setHourly(hourlyWeather);

            when(mockApiClient.fetchWeather(16.089, 108.249)).thenReturn(weatherResp);
            when(mockApiClient.fetchMarine(16.089, 108.249)).thenReturn(null);

            WeatherInfoDto.TimeWindowForecast forecast = adapter.getTimeWindowForecast(
                    16.089, 108.249, LocalDate.of(2026, 9, 25), LocalTime.of(8, 0), LocalTime.of(9, 0)
            );

            assertNotNull(forecast);
            assertEquals(18.0, forecast.getPeakWindSpeed());
            assertNull(forecast.getPeakWaveHeight(), "Marine was null, peak wave must be null");
        }
    }

    // =========================================================================
    // 4. HIGH WINDS, GUSTS, AND SEVERE WEATHER OVERLAPPING
    // =========================================================================
    @Nested
    @DisplayName("Dimension 4: High Winds, Gusts & Severe Weather Overlapping")
    class HighWindsAndSevereWeatherTests {

        @Test
        @DisplayName("Overlapping: High wind (35 km/h) + severe gust (55 km/h) + WMO 95 thunderstorm -> All violations captured in RED")
        void testOverlapping_highWindGustAndThunderstorm() {
            LocalDate date = today.plusDays(7);
            when(categorySafetyRuleService.getRuleByCategorySlug("cheo-sup-kayak")).thenReturn(supRule);

            WeatherInfoDto.TimeWindowForecast forecast = WeatherInfoDto.TimeWindowForecast.builder().sourceFetchedAt(Instant.now()).validUntil(Instant.now().plusSeconds(1800))
                    .peakWaveHeight(1.2) // > 0.8 max
                    .peakWindSpeed(35.0) // > 20.0 max
                    .peakWindGust(55.0) // > 28.0 max
                    .severeWeatherCode(95) // Thunderstorm fatal
                    .build();

            when(weatherProviderPort.getTimeWindowForecast(anyDouble(), anyDouble(), eq(date), any(), any()))
                    .thenReturn(forecast);

            AdvanceBookingSafetyResponse res = useCase.execute(
                    AdvanceBookingSafetyRequest.builder().categorySlug("cheo-sup-kayak").bookingDate(date).build(),
                    today
            );

            assertFalse(res.isSafe());
            assertEquals("RED", res.getSafetyStatus());
            assertEquals(WeatherRuleEngine.ALERT_RED, res.getAlertLevel());

            // Details must contain wave, wind, gust, and thunderstorm violations
            assertTrue(res.getDetails().stream().anyMatch(d -> d.contains("Wave height 1.2m")));
            assertTrue(res.getDetails().stream().anyMatch(d -> d.contains("35.0 km/h")));
            assertTrue(res.getDetails().stream().anyMatch(d -> d.contains("55.0 km/h")));
            assertTrue(res.getDetails().stream().anyMatch(d -> d.contains("WMO code: 95")));
        }

        @Test
        @DisplayName("ASTM F3099 Parasailing: Wind gust violation (38 km/h > 37 km/h) alone triggers RED even if wind is gentle")
        void testParasailingGustViolationAlone_triggersRed() {
            LocalDate date = today.plusDays(8);
            when(categorySafetyRuleService.getRuleByCategorySlug("cano-du-bay")).thenReturn(parasailingRule);

            WeatherInfoDto.TimeWindowForecast forecast = WeatherInfoDto.TimeWindowForecast.builder().sourceFetchedAt(Instant.now()).validUntil(Instant.now().plusSeconds(1800))
                    .peakWaveHeight(0.4) // Well below max (1.0m)
                    .peakWindSpeed(12.0) // Well below max (22.0 km/h)
                    .peakWindGust(38.0) // > maxWindGust (37.0 km/h / 20 knots)
                    .severeWeatherCode(0)
                    .build();

            when(weatherProviderPort.getTimeWindowForecast(anyDouble(), anyDouble(), eq(date), any(), any()))
                    .thenReturn(forecast);

            AdvanceBookingSafetyResponse res = useCase.execute(
                    AdvanceBookingSafetyRequest.builder().categorySlug("cano-du-bay").bookingDate(date).build(),
                    today
            );

            assertFalse(res.isSafe());
            assertEquals("RED", res.getSafetyStatus());
            assertTrue(res.getWarningMessage().contains("Peak wind gust 38.0 km/h exceeds the aerodynamic safety limit"));
        }

        @Test
        @DisplayName("Overlapping: Caution wind (15 km/h > 12 caution) with Thunderstorm (WMO 99) -> RED takes precedence over YELLOW")
        void testYellowWindWithRedThunderstorm_redTakesPrecedence() {
            LocalDate date = today.plusDays(7);
            when(categorySafetyRuleService.getRuleByCategorySlug("cheo-sup-kayak")).thenReturn(supRule);

            WeatherInfoDto.TimeWindowForecast forecast = WeatherInfoDto.TimeWindowForecast.builder().sourceFetchedAt(Instant.now()).validUntil(Instant.now().plusSeconds(1800))
                    .peakWaveHeight(0.3)
                    .peakWindSpeed(15.0) // caution range (12-20)
                    .peakWindGust(18.0)
                    .severeWeatherCode(99) // Violent thunderstorm with hail
                    .build();

            when(weatherProviderPort.getTimeWindowForecast(anyDouble(), anyDouble(), eq(date), any(), any()))
                    .thenReturn(forecast);

            AdvanceBookingSafetyResponse res = useCase.execute(
                    AdvanceBookingSafetyRequest.builder().categorySlug("cheo-sup-kayak").bookingDate(date).build(),
                    today
            );

            assertFalse(res.isSafe());
            assertEquals("RED", res.getSafetyStatus());
            assertTrue(res.getWarningMessage().contains("WMO code: 99"));
        }

        @Test
        @DisplayName("Severe weather code precedence in WeatherProviderAdapter: 95/96/99 overrides higher non-fatal numbers")
        void testWeatherProviderAdapter_severeCodePrecedence() {
            WeatherForecastCacheService cache = new WeatherForecastCacheService();
            WeatherProviderAdapter adapter = new WeatherProviderAdapter(mockApiClient, cache);

            OpenMeteoWeatherResponse weatherResp = new OpenMeteoWeatherResponse();
            OpenMeteoWeatherResponse.HourlyData hourly = new OpenMeteoWeatherResponse.HourlyData();
            hourly.setTime(List.of("2026-09-25T08:00", "2026-09-25T09:00", "2026-09-25T10:00"));
            // 80 is rain showers (non-fatal), 95 is thunderstorm (fatal), 61 is slight rain
            hourly.setWeatherCode(List.of(80, 95, 61));
            weatherResp.setHourly(hourly);

            when(mockApiClient.fetchWeather(anyDouble(), anyDouble())).thenReturn(weatherResp);
            when(mockApiClient.fetchMarine(anyDouble(), anyDouble())).thenReturn(null);

            WeatherInfoDto.TimeWindowForecast forecast = adapter.getTimeWindowForecast(
                    16.089, 108.249, LocalDate.of(2026, 9, 25), LocalTime.of(8, 0), LocalTime.of(10, 0)
            );

            assertNotNull(forecast);
            assertEquals(95, forecast.getSevereWeatherCode(), "Fatal code 95 must take precedence over code 80");
        }
    }
}
