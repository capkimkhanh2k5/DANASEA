package com.danasea.backend.modules.weather;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaCategoryRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.weather.application.dtos.AdvanceBookingSafetyRequest;
import com.danasea.backend.modules.weather.application.dtos.AdvanceBookingSafetyResponse;
import com.danasea.backend.modules.weather.application.dtos.WeatherInfoDto;
import com.danasea.backend.modules.weather.application.ports.output.WeatherProviderPort;
import com.danasea.backend.modules.weather.application.usecases.CheckAdvanceBookingSafetyUseCase;
import com.danasea.backend.modules.weather.domain.models.CategorySafetyRule;
import com.danasea.backend.modules.weather.domain.services.CategorySafetyRuleService;
import com.danasea.backend.modules.weather.domain.services.WeatherRuleEngine;
import com.danasea.backend.modules.weather.infrastructure.adapters.WeatherForecastCacheService;
import com.danasea.backend.modules.weather.infrastructure.adapters.WeatherProviderAdapter;
import com.danasea.backend.modules.weather.infrastructure.api.OpenMeteoApiClient;
import com.danasea.backend.modules.weather.infrastructure.api.dtos.OpenMeteoMarineResponse;
import com.danasea.backend.modules.weather.infrastructure.api.dtos.OpenMeteoWeatherResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdvanceBookingSafetyChallengerTest - Adversarial Stress & Empirical Challenge Suite")
public class AdvanceBookingSafetyChallengerTest {

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

    // UTC and Vietnam have different dates at this instant.
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-28T17:30:00Z"), ZoneOffset.UTC);
    private final LocalDate today = LocalDate.now(clock.withZone(Booking.VIETNAM_ZONE));
    private CategorySafetyRule supRule;
    private CategorySafetyRule divingRule;
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
                clock
        );

        supRule = CategorySafetyRule.getBySlug("cheo-sup-kayak");
        divingRule = CategorySafetyRule.getBySlug("lan-ngam-san-ho");
        parasailingRule = CategorySafetyRule.getBySlug("cano-du-bay");
    }

    @Nested
    @DisplayName("Dimension 1: Date Range Partitioning & Boundary Testing")
    class DateRangeBoundaryTests {

        @Test
        @DisplayName("1.1 Past Dates: Rejected with IllegalArgumentException in execute and checkSafety")
        void testPastDates_StrictlyRejected() {
            LocalDate yesterday = today.minusDays(1);
            LocalDate lastMonth = today.minusDays(30);

            AdvanceBookingSafetyRequest req1 = AdvanceBookingSafetyRequest.builder()
                    .categorySlug("cheo-sup-kayak")
                    .bookingDate(yesterday)
                    .build();

            AdvanceBookingSafetyRequest req2 = AdvanceBookingSafetyRequest.builder()
                    .categorySlug("cheo-sup-kayak")
                    .bookingDate(lastMonth)
                    .build();

            IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () -> useCase.execute(req1, today));
            assertTrue(ex1.getMessage().contains("past"), "Exception message must state date is in the past");

            IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () -> useCase.execute(req2, today));
            assertTrue(ex2.getMessage().contains("past"));

            assertThrows(IllegalArgumentException.class, () -> useCase.checkSafety(
                    UUID.randomUUID(), UUID.randomUUID(), "cheo-sup-kayak",
                    yesterday, LocalTime.of(8, 0), LocalTime.of(10, 0), 16.089, 108.249
            ));
        }

        @Test
        @DisplayName("1.2 Day 0 (Same-day): Allowed with HIGH confidence and FULL_MARINE_AND_METEOROLOGY")
        void testDay0_SameDay_ProcessedSuccessfully() {
            when(categorySafetyRuleService.getRuleByCategorySlug("cheo-sup-kayak")).thenReturn(supRule);

            WeatherInfoDto.TimeWindowForecast forecast = WeatherInfoDto.TimeWindowForecast.builder().sourceFetchedAt(Instant.now()).validUntil(Instant.now().plusSeconds(1800))
                    .peakWaveHeight(0.35)
                    .peakWindSpeed(10.0)
                    .peakWindGust(14.0)
                    .peakOceanCurrent(0.10)
                    .minVisibility(9000.0)
                    .severeWeatherCode(1)
                    .build();

            when(weatherProviderPort.getTimeWindowForecast(anyDouble(), anyDouble(), eq(today), any(), any()))
                    .thenReturn(forecast);

            AdvanceBookingSafetyRequest req = AdvanceBookingSafetyRequest.builder()
                    .categorySlug("cheo-sup-kayak")
                    .bookingDate(today)
                    .build();

            AdvanceBookingSafetyResponse res = useCase.execute(req, today);

            assertNotNull(res);
            assertEquals(0, res.getDaysInAdvance());
            assertEquals(0, res.getDaysAhead());
            assertTrue(res.isSafe());
            assertEquals("GREEN", res.getSafetyStatus());
            assertFalse(res.isMarineCutoffExceeded());
            assertFalse(res.isProvisional());
            assertEquals("FULL_MARINE_AND_METEOROLOGY", res.getDataCoverage());
            assertEquals("HIGH", res.getConfidenceLevel());
        }

        @Test
        @DisplayName("1.3 Days 1 to 8: Full Marine and Meteorology Coverage across all boundaries")
        void testDays1To8_FullMarineCoverage() {
            when(categorySafetyRuleService.getRuleByCategorySlug("cheo-sup-kayak")).thenReturn(supRule);

            // Test boundaries: Day 1, Day 3, Day 4, Day 7, Day 8
            int[] testDays = {1, 3, 4, 7, 8};
            for (int daysAhead : testDays) {
                LocalDate targetDate = today.plusDays(daysAhead);

                WeatherInfoDto.TimeWindowForecast forecast = WeatherInfoDto.TimeWindowForecast.builder().sourceFetchedAt(Instant.now()).validUntil(Instant.now().plusSeconds(1800))
                        .peakWaveHeight(0.40)
                        .peakWindSpeed(11.0)
                        .peakWindGust(15.0)
                        .peakOceanCurrent(0.12)
                        .minVisibility(8000.0)
                        .severeWeatherCode(1)
                        .build();

                when(weatherProviderPort.getTimeWindowForecast(anyDouble(), anyDouble(), eq(targetDate), any(), any()))
                        .thenReturn(forecast);

                AdvanceBookingSafetyRequest req = AdvanceBookingSafetyRequest.builder()
                        .categorySlug("cheo-sup-kayak")
                        .bookingDate(targetDate)
                        .build();

                AdvanceBookingSafetyResponse res = useCase.execute(req, today);

                assertNotNull(res, "Response must not be null for day " + daysAhead);
                assertEquals(daysAhead, res.getDaysInAdvance());
                assertFalse(res.isMarineCutoffExceeded(), "Day " + daysAhead + " must not exceed marine cutoff");
                assertFalse(res.isProvisional(), "Day " + daysAhead + " must not be provisional");
                assertEquals("FULL_MARINE_AND_METEOROLOGY", res.getDataCoverage());
                assertNotNull(res.getPeakWaveHeightM());
                assertEquals(0.40, res.getPeakWaveHeightM());

                if (daysAhead <= 3) {
                    assertEquals("HIGH", res.getConfidenceLevel(), "Days 1-3 must have HIGH confidence");
                } else {
                    assertEquals("MEDIUM", res.getConfidenceLevel(), "Days 4-8 must have MEDIUM confidence");
                }
            }
        }

        @Test
        @DisplayName("1.4 Day 1 to 8: Hazard violations trigger RED or YELLOW accurately")
        void testDays1To8_HazardViolations() {
            LocalDate day5 = today.plusDays(5);
            when(categorySafetyRuleService.getRuleByCategorySlug("cheo-sup-kayak")).thenReturn(supRule);

            // Scenario A: Wave 1.2m > 0.8m max -> RED
            WeatherInfoDto.TimeWindowForecast highWaveForecast = WeatherInfoDto.TimeWindowForecast.builder().sourceFetchedAt(Instant.now()).validUntil(Instant.now().plusSeconds(1800))
                    .peakWaveHeight(1.20)
                    .peakWindSpeed(10.0)
                    .peakWindGust(15.0)
                    .build();
            when(weatherProviderPort.getTimeWindowForecast(anyDouble(), anyDouble(), eq(day5), any(), any()))
                    .thenReturn(highWaveForecast);

            AdvanceBookingSafetyResponse resA = useCase.execute(AdvanceBookingSafetyRequest.builder()
                    .categorySlug("cheo-sup-kayak")
                    .bookingDate(day5)
                    .build(), today);

            assertFalse(resA.isSafe());
            assertEquals("RED", resA.getSafetyStatus());
            assertTrue(resA.getWarningMessage().contains("Wave height 1.2m exceeds the maximum safe threshold"));

            // Scenario B: Wave 0.6m (caution: 0.5m, max: 0.8m) -> YELLOW
            WeatherInfoDto.TimeWindowForecast cautionWaveForecast = WeatherInfoDto.TimeWindowForecast.builder().sourceFetchedAt(Instant.now()).validUntil(Instant.now().plusSeconds(1800))
                    .peakWaveHeight(0.60)
                    .peakWindSpeed(10.0)
                    .peakWindGust(15.0)
                    .build();
            when(weatherProviderPort.getTimeWindowForecast(anyDouble(), anyDouble(), eq(day5), any(), any()))
                    .thenReturn(cautionWaveForecast);

            AdvanceBookingSafetyResponse resB = useCase.execute(AdvanceBookingSafetyRequest.builder()
                    .categorySlug("cheo-sup-kayak")
                    .bookingDate(day5)
                    .build(), today);

            assertTrue(resB.isSafe());
            assertEquals("YELLOW", resB.getSafetyStatus());
            assertTrue(resB.getWarningMessage().contains("CAUTION ALERT"));
        }

        @Test
        @DisplayName("1.5 Days 9 to 16: Meteorology with Estimated Marine & Provisional Flag")
        void testDays9To16_MeteorologyWithEstimatedMarine() {
            when(categorySafetyRuleService.getRuleByCategorySlug("cano-du-bay")).thenReturn(parasailingRule);

            int[] testDays = {9, 10, 12, 14, 15, 16};
            for (int daysAhead : testDays) {
                LocalDate targetDate = today.plusDays(daysAhead);

                // Beyond day 8, Marine API wave is null; wind speed is 18.0 km/h (safe for parasailing max 28.0)
                WeatherInfoDto.TimeWindowForecast forecast = WeatherInfoDto.TimeWindowForecast.builder().sourceFetchedAt(Instant.now()).validUntil(Instant.now().plusSeconds(1800))
                        .peakWaveHeight(null)
                        .peakWindSpeed(18.0)
                        .peakWindGust(25.0)
                        .peakOceanCurrent(null)
                        .minVisibility(6000.0)
                        .severeWeatherCode(1)
                        .build();

                when(weatherProviderPort.getTimeWindowForecast(anyDouble(), anyDouble(), eq(targetDate), any(), any()))
                        .thenReturn(forecast);

                AdvanceBookingSafetyRequest req = AdvanceBookingSafetyRequest.builder()
                        .categorySlug("cano-du-bay")
                        .bookingDate(targetDate)
                        .build();

                AdvanceBookingSafetyResponse res = useCase.execute(req, today);

                assertNotNull(res);
                assertEquals(daysAhead, res.getDaysInAdvance());
                assertTrue(res.isMarineCutoffExceeded(), "Days 9-16 must set marineCutoffExceeded=true");
                assertTrue(res.isProvisional(), "Days 9-16 must set isProvisional=true");
                assertTrue(res.isEstimatedMarine(), "Days 9-16 must set estimatedMarine=true");
                assertEquals("METEOROLOGY_ESTIMATED_MARINE", res.getDataCoverage());
                assertEquals("ADVISORY", res.getConfidenceLevel());

                // Top-level peakWaveHeightM is masked to null since it is beyond cutoff
                assertNull(res.getPeakWaveHeightM());

                // But internal forecast peakWave was estimated: Hs ≈ 0.024 * (18^1.15) ≈ 0.67m
                assertNotNull(res.getForecast());
                assertNotNull(res.getForecast().getPeakWaveHeight(), "Internal forecast must store estimated wave height");
                assertTrue(res.getForecast().getPeakWaveHeight() > 0.5 && res.getForecast().getPeakWaveHeight() < 0.9);
            }
        }

        @Test
        @DisplayName("1.6 Days 9 to 16: Extreme Wind triggers Wave Estimation exceeding category threshold -> RED")
        void testDays9To16_ExtremeWindTriggersEstimatedWaveViolation() {
            LocalDate day13 = today.plusDays(13);
            when(categorySafetyRuleService.getRuleByCategorySlug("cheo-sup-kayak")).thenReturn(supRule);

            // Wind = 45 km/h -> Estimated wave = 0.024 * (45^1.15) ≈ 1.90m > 0.8m (SUP max)
            WeatherInfoDto.TimeWindowForecast forecast = WeatherInfoDto.TimeWindowForecast.builder().sourceFetchedAt(Instant.now()).validUntil(Instant.now().plusSeconds(1800))
                    .peakWaveHeight(null)
                    .peakWindSpeed(45.0)
                    .peakWindGust(55.0)
                    .minVisibility(5000.0)
                    .build();

            when(weatherProviderPort.getTimeWindowForecast(anyDouble(), anyDouble(), eq(day13), any(), any()))
                    .thenReturn(forecast);

            AdvanceBookingSafetyResponse res = useCase.execute(AdvanceBookingSafetyRequest.builder()
                    .categorySlug("cheo-sup-kayak")
                    .bookingDate(day13)
                    .build(), today);

            assertNotNull(res);
            assertFalse(res.isSafe(), "High wind generating 1.9m wave estimate must mark unsafe");
            assertEquals("RED", res.getSafetyStatus());
            assertTrue(res.isMarineCutoffExceeded());
            assertTrue(res.isProvisional());
        }

        @Test
        @DisplayName("1.7 Day 16 vs Day 17: Exact 16-Day Cutoff Boundary")
        void testDay16Vs17_ExactCutoffBoundary() {
            LocalDate day16 = today.plusDays(16);
            LocalDate day17 = today.plusDays(17);

            when(categorySafetyRuleService.getRuleByCategorySlug("cheo-sup-kayak")).thenReturn(supRule);

            WeatherInfoDto.TimeWindowForecast day16Forecast = WeatherInfoDto.TimeWindowForecast.builder().sourceFetchedAt(Instant.now()).validUntil(Instant.now().plusSeconds(1800))
                    .peakWaveHeight(null)
                    .peakWindSpeed(12.0)
                    .peakWindGust(16.0)
                    .minVisibility(8000.0)
                    .build();

            when(weatherProviderPort.getTimeWindowForecast(anyDouble(), anyDouble(), eq(day16), any(), any()))
                    .thenReturn(day16Forecast);

            // Day 16: Processed under METEOROLOGY_ESTIMATED_MARINE
            AdvanceBookingSafetyResponse res16 = useCase.execute(AdvanceBookingSafetyRequest.builder()
                    .categorySlug("cheo-sup-kayak")
                    .bookingDate(day16)
                    .build(), today);

            assertNotNull(res16);
            assertEquals(16, res16.getDaysInAdvance());
            assertEquals("METEOROLOGY_ESTIMATED_MARINE", res16.getDataCoverage());
            assertTrue(res16.isSafe());

            // Day 17: Processed under OUT_OF_RANGE in execute()
            AdvanceBookingSafetyResponse res17 = useCase.execute(AdvanceBookingSafetyRequest.builder()
                    .categorySlug("cheo-sup-kayak")
                    .bookingDate(day17)
                    .build(), today);

            assertNotNull(res17);
            assertEquals(17, res17.getDaysInAdvance());
            assertEquals("OUT_OF_RANGE", res17.getDataCoverage());
            assertEquals("LOW", res17.getConfidenceLevel());
            assertTrue(res17.isProvisional());
            assertTrue(res17.isMarineCutoffExceeded());
            assertNull(res17.getForecast());
            assertTrue(res17.getWarningMessage().contains("exceeds Open-Meteo's 16-day forecast window"));

            // Day 17 in checkSafety(): throws IllegalArgumentException
            assertThrows(IllegalArgumentException.class, () -> useCase.checkSafety(
                    UUID.randomUUID(), UUID.randomUUID(), "cheo-sup-kayak",
                    day17, LocalTime.of(8, 0), LocalTime.of(10, 0), 16.089, 108.249
            ));
        }

        @Test
        @DisplayName("1.8 Far Future Dates (Day 30, Day 100): Return OUT_OF_RANGE safely without external call")
        void testFarFutureDates_ReturnOutOfRangeSafely() {
            LocalDate day30 = today.plusDays(30);
            LocalDate day100 = today.plusDays(100);

            when(categorySafetyRuleService.getRuleByCategorySlug("cheo-sup-kayak")).thenReturn(supRule);

            AdvanceBookingSafetyResponse res30 = useCase.execute(AdvanceBookingSafetyRequest.builder()
                    .categorySlug("cheo-sup-kayak")
                    .bookingDate(day30)
                    .build(), today);

            assertEquals("OUT_OF_RANGE", res30.getDataCoverage());
            assertEquals(30, res30.getDaysInAdvance());
            assertTrue(res30.isProvisional());

            AdvanceBookingSafetyResponse res100 = useCase.execute(AdvanceBookingSafetyRequest.builder()
                    .categorySlug("cheo-sup-kayak")
                    .bookingDate(day100)
                    .build(), today);

            assertEquals("OUT_OF_RANGE", res100.getDataCoverage());
            assertEquals(100, res100.getDaysInAdvance());

            // Verify weatherProviderPort was NEVER called for out-of-range dates
            verify(weatherProviderPort, never()).getTimeWindowForecast(anyDouble(), anyDouble(), eq(day30), any(), any());
            verify(weatherProviderPort, never()).getTimeWindowForecast(anyDouble(), anyDouble(), eq(day100), any(), any());
        }
    }

    @Nested
    @DisplayName("Dimension 2: Cache Performance & Rate Limit Stress Testing")
    class CachePerformanceStressTests {

        @Test
        @DisplayName("2.1 Cache Hit Performance: Cache returns in sub-millisecond without calling API")
        void testCachePerformance_SubMillisecondRetrieval() {
            WeatherForecastCacheService cacheService = new WeatherForecastCacheService();
            WeatherProviderAdapter adapter = new WeatherProviderAdapter(mockApiClient, cacheService);

            OpenMeteoWeatherResponse weatherResp = new OpenMeteoWeatherResponse();
            OpenMeteoWeatherResponse.HourlyData hourlyWeather = new OpenMeteoWeatherResponse.HourlyData();
            hourlyWeather.setTime(List.of("2026-09-25T08:00"));
            hourlyWeather.setWindSpeed10m(List.of(12.0));
            weatherResp.setHourly(hourlyWeather);

            OpenMeteoMarineResponse marineResp = new OpenMeteoMarineResponse();
            OpenMeteoMarineResponse.HourlyData hourlyMarine = new OpenMeteoMarineResponse.HourlyData();
            hourlyMarine.setTime(List.of("2026-09-25T08:00"));
            hourlyMarine.setWaveHeight(List.of(0.5));
            marineResp.setHourly(hourlyMarine);

            when(mockApiClient.fetchWeather(16.0890, 108.2495)).thenReturn(weatherResp);
            when(mockApiClient.fetchMarine(16.0890, 108.2495)).thenReturn(marineResp);

            // Cold fetch (Call 1)
            adapter.getTimeWindowForecast(16.0890, 108.2495, LocalDate.of(2026, 9, 25), LocalTime.of(8, 0), LocalTime.of(9, 0));
            verify(mockApiClient, times(1)).fetchWeather(16.0890, 108.2495);
            verify(mockApiClient, times(1)).fetchMarine(16.0890, 108.2495);

            // 1,000 Rapid Warm Cache Invocations
            Instant start = Instant.now();
            for (int i = 0; i < 1000; i++) {
                WeatherInfoDto.TimeWindowForecast cached = adapter.getTimeWindowForecast(
                        16.0890, 108.2495, LocalDate.of(2026, 9, 25), LocalTime.of(8, 0), LocalTime.of(9, 0)
                );
                assertNotNull(cached);
                assertEquals(12.0, cached.getPeakWindSpeed());
                assertEquals(0.5, cached.getPeakWaveHeight());
            }
            Duration elapsed = Duration.between(start, Instant.now());

            // Verify API calls remained EXACTLY 1!
            verify(mockApiClient, times(1)).fetchWeather(16.0890, 108.2495);
            verify(mockApiClient, times(1)).fetchMarine(16.0890, 108.2495);

            // 1,000 iterations must complete within 200 milliseconds (avg < 0.2ms per call)
            assertTrue(elapsed.toMillis() < 200, "1000 cache hits took " + elapsed.toMillis() + "ms, expected < 200ms");
        }

        @Test
        @DisplayName("2.2 Multi-Threaded Concurrency: 50 concurrent requests safely hit warm cache with 0 API calls, and cold cache stampede is documented")
        void testConcurrentMultiThreadedCacheAccess() throws InterruptedException, ExecutionException, TimeoutException {
            WeatherForecastCacheService cacheService = new WeatherForecastCacheService();
            WeatherProviderAdapter adapter = new WeatherProviderAdapter(mockApiClient, cacheService);

            OpenMeteoWeatherResponse weatherResp = new OpenMeteoWeatherResponse();
            OpenMeteoWeatherResponse.HourlyData hourlyWeather = new OpenMeteoWeatherResponse.HourlyData();
            hourlyWeather.setTime(List.of("2026-09-25T08:00"));
            hourlyWeather.setWindSpeed10m(List.of(15.0));
            weatherResp.setHourly(hourlyWeather);

            OpenMeteoMarineResponse marineResp = new OpenMeteoMarineResponse();
            OpenMeteoMarineResponse.HourlyData hourlyMarine = new OpenMeteoMarineResponse.HourlyData();
            hourlyMarine.setTime(List.of("2026-09-25T08:00"));
            hourlyMarine.setWaveHeight(List.of(0.6));
            marineResp.setHourly(hourlyMarine);

            AtomicInteger weatherApiCalls = new AtomicInteger(0);
            AtomicInteger marineApiCalls = new AtomicInteger(0);

            when(mockApiClient.fetchWeather(16.0890, 108.2495)).thenAnswer(inv -> {
                weatherApiCalls.incrementAndGet();
                Thread.sleep(10); // Simulate network latency
                return weatherResp;
            });
            when(mockApiClient.fetchMarine(16.0890, 108.2495)).thenAnswer(inv -> {
                marineApiCalls.incrementAndGet();
                Thread.sleep(10);
                return marineResp;
            });

            // 1. First warm the cache with a single request
            adapter.getTimeWindowForecast(
                    16.0890, 108.2495, LocalDate.of(2026, 9, 25), LocalTime.of(8, 0), LocalTime.of(9, 0)
            );
            assertEquals(1, weatherApiCalls.get());
            assertEquals(1, marineApiCalls.get());

            // 2. Now launch 50 concurrent threads against the warm cache
            int threadCount = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            List<Future<WeatherInfoDto.TimeWindowForecast>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await(); // Simultaneous blast
                    return adapter.getTimeWindowForecast(
                            16.0890, 108.2495, LocalDate.of(2026, 9, 25), LocalTime.of(8, 0), LocalTime.of(9, 0)
                    );
                }));
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown(); // Release threads

            for (Future<WeatherInfoDto.TimeWindowForecast> future : futures) {
                WeatherInfoDto.TimeWindowForecast f = future.get(5, TimeUnit.SECONDS);
                assertNotNull(f);
                assertEquals(15.0, f.getPeakWindSpeed());
                assertEquals(0.6, f.getPeakWaveHeight());
            }

            executor.shutdown();

            // When warm, all 50 concurrent requests must hit cache with ZERO new API calls!
            assertEquals(1, weatherApiCalls.get(), "Warm cache must result in 0 additional API calls");
            assertEquals(1, marineApiCalls.get(), "Warm cache must result in 0 additional API calls");
        }

        @Test
        @DisplayName("2.3 Coordinate Normalization Rounding: 4-decimal precision key hashing")
        void testCoordinateNormalizationRounding() {
            WeatherForecastCacheService cacheService = new WeatherForecastCacheService();

            AtomicInteger calls = new AtomicInteger(0);
            OpenMeteoWeatherResponse resp = new OpenMeteoWeatherResponse();

            // Point A
            cacheService.getOrFetchWeather(16.0890123, 108.2495123, () -> {
                calls.incrementAndGet();
                return resp;
            });
            assertEquals(1, calls.get());

            // Point B differs by 0.00002 -> rounds to same 16.0890 : 108.2495 -> CACHE HIT
            cacheService.getOrFetchWeather(16.0890299, 108.2495299, () -> {
                calls.incrementAndGet();
                return resp;
            });
            assertEquals(1, calls.get(), "Sub-meter coordinates difference must hit same cache entry");

            // Point C differs by 0.005 -> rounds to 16.0940 : 108.2495 -> CACHE MISS
            cacheService.getOrFetchWeather(16.0940, 108.2495, () -> {
                calls.incrementAndGet();
                return resp;
            });
            assertEquals(2, calls.get(), "Farther coordinates must trigger new fetch");
        }

        @Test
        @DisplayName("2.4 Invalidation: Cache invalidation successfully forces subsequent fresh fetch")
        void testCacheInvalidation_ForcesFreshFetch() {
            WeatherForecastCacheService cacheService = new WeatherForecastCacheService();
            AtomicInteger fetchCount = new AtomicInteger(0);

            OpenMeteoWeatherResponse resp = new OpenMeteoWeatherResponse();
            cacheService.getOrFetchWeather(16.0890, 108.2495, () -> {
                fetchCount.incrementAndGet();
                return resp;
            });
            assertEquals(1, fetchCount.get());

            // Cached
            assertTrue(cacheService.isWeatherCached(16.0890, 108.2495));

            // Invalidate
            cacheService.invalidate(16.0890, 108.2495);
            assertFalse(cacheService.isWeatherCached(16.0890, 108.2495));

            // Next call must re-fetch
            cacheService.getOrFetchWeather(16.0890, 108.2495, () -> {
                fetchCount.incrementAndGet();
                return resp;
            });
            assertEquals(2, fetchCount.get());
        }
    }

    @Nested
    @DisplayName("Dimension 3: Resilience Under API Failure & Fallback Behavior")
    class ResilienceUnderApiFailureTests {

        @Test
        @DisplayName("3.1 Complete API Down: Weather & Marine return null -> Adapter & UseCase survive without 500")
        void testCompleteApiDown_SurvivesGracefully() {
            when(categorySafetyRuleService.getRuleByCategorySlug("cheo-sup-kayak")).thenReturn(supRule);

            // API client returns null for both
            when(mockApiClient.fetchWeather(anyDouble(), anyDouble())).thenReturn(null);
            when(mockApiClient.fetchMarine(anyDouble(), anyDouble())).thenReturn(null);

            WeatherForecastCacheService cacheService = new WeatherForecastCacheService();
            WeatherProviderAdapter adapter = new WeatherProviderAdapter(mockApiClient, cacheService);

            CheckAdvanceBookingSafetyUseCase robustUseCase = new CheckAdvanceBookingSafetyUseCase(
                    categorySafetyRuleService,
                    adapter,
                    weatherRuleEngine,
                    slotRepository,
                    serviceRepository,
                    categoryRepository
            );

            LocalDate advanceDate = today.plusDays(7);
            AdvanceBookingSafetyRequest req = AdvanceBookingSafetyRequest.builder()
                    .categorySlug("cheo-sup-kayak")
                    .bookingDate(advanceDate)
                    .build();

            // Must NOT throw NullPointerException or 500
            AdvanceBookingSafetyResponse res = assertDoesNotThrow(() -> robustUseCase.execute(req, today));

            assertNotNull(res);
            assertEquals(7, res.getDaysInAdvance());
            // Missing observations cannot certify safety.
            assertFalse(res.isSafe());
            assertEquals("LOW", res.getConfidenceLevel());
            assertEquals("STALE_OR_UNVERIFIED_FORECAST", res.getDataCoverage());
            assertFalse(res.isEstimatedMarine());
        }

        @Test
        @DisplayName("3.2 Partial API Failure: Marine API fails, Weather API succeeds -> Gracefully estimates marine")
        void testMarineFailsWeatherSucceeds_EstimatesWaveFromWind() {
            when(categorySafetyRuleService.getRuleByCategorySlug("cheo-sup-kayak")).thenReturn(supRule);

            LocalDate day4 = today.plusDays(4);
            OpenMeteoWeatherResponse weatherResp = new OpenMeteoWeatherResponse();
            OpenMeteoWeatherResponse.HourlyData hourly = new OpenMeteoWeatherResponse.HourlyData();
            hourly.setTime(List.of(day4.atTime(8, 0).toString(), day4.atTime(9, 0).toString()));
            hourly.setWindSpeed10m(List.of(20.0, 22.0));
            hourly.setWindGusts10m(List.of(25.0, 27.0));
            hourly.setVisibility(List.of(9000.0, 9000.0));
            weatherResp.setHourly(hourly);

            when(mockApiClient.fetchWeather(anyDouble(), anyDouble())).thenReturn(weatherResp);
            when(mockApiClient.fetchMarine(anyDouble(), anyDouble())).thenReturn(null); // Marine fails

            WeatherForecastCacheService cacheService = new WeatherForecastCacheService();
            WeatherProviderAdapter adapter = new WeatherProviderAdapter(mockApiClient, cacheService);

            CheckAdvanceBookingSafetyUseCase robustUseCase = new CheckAdvanceBookingSafetyUseCase(
                    categorySafetyRuleService,
                    adapter,
                    weatherRuleEngine
            );

            AdvanceBookingSafetyResponse res = robustUseCase.execute(AdvanceBookingSafetyRequest.builder()
                    .categorySlug("cheo-sup-kayak")
                    .bookingDate(day4)
                    .build(), today);

            assertNotNull(res);
            assertTrue(res.isEstimatedMarine(), "When marine fails on day <= 8, estimatedMarine must be true");
            assertEquals("METEOROLOGY_ESTIMATED_MARINE", res.getDataCoverage());
            // Estimated wave from 22 km/h wind: 0.024 * (22^1.15) = 0.84m > 0.80m (max SUP wave) -> RED!
            assertEquals(0.84, res.getForecast().getPeakWaveHeight());
            assertEquals("RED", res.getSafetyStatus());
            assertFalse(res.isSafe());
        }

        @Test
        @DisplayName("3.3 Database/Category Registry Fallback: Unknown or Missing Category falls back to DEFAULT_RULE")
        void testCategoryFallback_WhenDbFailsOrUnknown() {
            when(categorySafetyRuleService.getRuleByCategorySlug("corrupted-or-empty"))
                    .thenReturn(CategorySafetyRule.DEFAULT_RULE);

            WeatherInfoDto.TimeWindowForecast forecast = WeatherInfoDto.TimeWindowForecast.builder().sourceFetchedAt(Instant.now()).validUntil(Instant.now().plusSeconds(1800))
                    .peakWaveHeight(0.40)
                    .peakWindSpeed(10.0)
                    .build();

            when(weatherProviderPort.getTimeWindowForecast(anyDouble(), anyDouble(), any(), any(), any()))
                    .thenReturn(forecast);

            AdvanceBookingSafetyResponse res = useCase.execute(AdvanceBookingSafetyRequest.builder()
                    .categorySlug("corrupted-or-empty")
                    .bookingDate(today.plusDays(2))
                    .build(), today);

            assertNotNull(res);
            assertEquals("default", res.getCategorySlug());
            assertEquals(CategorySafetyRule.DEFAULT_RULE.getCategoryName(), res.getCategoryName());
            assertTrue(res.isSafe());
        }

        @Test
        @DisplayName("3.4 Corrupted/Empty Slot or Service ID in checkBySlotId throws IllegalArgumentException gracefully")
        void testCorruptedSlotId_ThrowsDescriptiveException() {
            UUID randomSlotId = UUID.randomUUID();
            when(slotRepository.findById(randomSlotId)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> useCase.checkBySlotId(randomSlotId));
            assertTrue(ex.getMessage().contains("Service slot not found"));
        }

        @Test
        @DisplayName("3.5 Null bookingDate throws IllegalArgumentException")
        void testNullBookingDate_ThrowsIllegalArgumentException() {
            AdvanceBookingSafetyRequest nullDateReq = AdvanceBookingSafetyRequest.builder()
                    .categorySlug("cheo-sup-kayak")
                    .bookingDate(null)
                    .build();

            assertThrows(IllegalArgumentException.class, () -> useCase.execute(nullDateReq, today));
            assertThrows(IllegalArgumentException.class, () -> useCase.execute(null, today));
        }
    }
}
