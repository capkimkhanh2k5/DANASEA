package com.danasea.backend.modules.weather.infrastructure.api;

import com.danasea.backend.modules.weather.application.dtos.WeatherInfoDto;
import com.danasea.backend.modules.weather.infrastructure.adapters.WeatherForecastCacheService;
import com.danasea.backend.modules.weather.infrastructure.adapters.WeatherProviderAdapter;
import com.danasea.backend.modules.weather.infrastructure.api.dtos.OpenMeteoMarineResponse;
import com.danasea.backend.modules.weather.infrastructure.api.dtos.OpenMeteoWeatherResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.springframework.boot.test.context.TestConfiguration;
import java.util.function.Supplier;

@SpringJUnitConfig(OpenMeteoApiClientAdversarialTest.AdversarialCachingConfig.class)
@DisplayName("OpenMeteoApiClient Adversarial Stress & Resilience Verification Suite")
public class OpenMeteoApiClientAdversarialTest {

    @TestConfiguration
    @EnableCaching
    static class AdversarialCachingConfig {

        @Bean
        public RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        public MockRestServiceServer mockRestServiceServer(RestClient.Builder restClientBuilder) {
            return MockRestServiceServer.bindTo(restClientBuilder).build();
        }

        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("openMeteoWeather", "openMeteoMarine");
        }

        @Bean
        public OpenMeteoApiClient openMeteoApiClient(RestClient.Builder restClientBuilder) {
            return new OpenMeteoApiClient(
                    restClientBuilder,
                    "https://api.open-meteo.com",
                    "https://marine-api.open-meteo.com",
                    false
            );
        }
    }

    @Autowired
    private OpenMeteoApiClient client;

    @Autowired
    private MockRestServiceServer mockServer;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        mockServer.reset();
        Cache weatherCache = cacheManager.getCache("openMeteoWeather");
        if (weatherCache != null) {
            weatherCache.clear();
        }
        Cache marineCache = cacheManager.getCache("openMeteoMarine");
        if (marineCache != null) {
            marineCache.clear();
        }
    }

    @Nested
    @DisplayName("1. Coordinate Extreme Precision & Cache Key Rounding Tests")
    class CoordinatePrecisionAndCachingTests {

        @Test
        @DisplayName("Coordinates with extreme precisions round to %.2f and hit Spring cache")
        void testWeatherCacheKey_ExtremePrecisions_RoundsAndHitsCache() {
            // Coordinate 1: 16.054412345, 108.20229876 -> formatted to "16.05:108.20"
            // Coordinate 2: 16.051111111, 108.20444444 -> formatted to "16.05:108.20" (same key)
            // Coordinate 3: 16.064000000, 108.20229876 -> formatted to "16.06:108.20" (different key)

            String mockWeatherJson = "{\"latitude\":16.05,\"longitude\":108.20,\"current\":{\"temperature_2m\":27.0}}";

            // Expect only 2 HTTP requests, because second call with close coordinates will hit cache
            mockServer.expect(ExpectedCount.times(2), requestTo(containsString("/v1/forecast")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(mockWeatherJson, MediaType.APPLICATION_JSON));

            // Call 1: First coordinate with extreme precision
            OpenMeteoWeatherResponse resp1 = client.fetchWeather(16.054412345, 108.20229876);
            assertNotNull(resp1);
            assertEquals(27.0, resp1.getCurrent().getTemperature2m());

            // Call 2: Second coordinate should evaluate to same cache key "16.05:108.20" -> CACHE HIT
            OpenMeteoWeatherResponse resp2 = client.fetchWeather(16.051111111, 108.20444444);
            assertNotNull(resp2);
            assertSame(resp1, resp2, "Second call must return cached instance from Spring Cache");

            // Verify cache entry existence in CacheManager
            Cache cache = cacheManager.getCache("openMeteoWeather");
            assertNotNull(cache);
            assertNotNull(cache.get("16.05:108.20"));

            // Call 3: Third coordinate rounds to "16.06:108.20" -> CACHE MISS -> triggers 2nd HTTP request
            OpenMeteoWeatherResponse resp3 = client.fetchWeather(16.064000000, 108.20229876);
            assertNotNull(resp3);
            assertNotNull(cache.get("16.06:108.20"));

            mockServer.verify();
        }

        @Test
        @DisplayName("Marine Cache key rounds extreme precisions to %.2f and hits Spring cache")
        void testMarineCacheKey_ExtremePrecisions_RoundsAndHitsCache() {
            String mockMarineJson = "{\"latitude\":16.05,\"longitude\":108.20,\"current\":{\"wave_height\":0.8}}";

            mockServer.expect(ExpectedCount.once(), requestTo(containsString("/v1/marine")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(mockMarineJson, MediaType.APPLICATION_JSON));

            OpenMeteoMarineResponse resp1 = client.fetchMarine(16.054999999, 108.201111111);
            assertNotNull(resp1);
            assertEquals(0.8, resp1.getCurrent().getWaveHeight());

            // Same rounded key "16.05:108.20"
            OpenMeteoMarineResponse resp2 = client.fetchMarine(16.050000001, 108.204999999);
            assertNotNull(resp2);
            assertSame(resp1, resp2, "Must return cached instance");

            Cache cache = cacheManager.getCache("openMeteoMarine");
            assertNotNull(cache);
            assertNotNull(cache.get("16.05:108.20"));

            mockServer.verify();
        }

        @Test
        @DisplayName("Cache key handles negative and zero coordinates correctly")
        void testCacheKey_NegativeAndZeroCoordinates() {
            String mockWeatherJson = "{\"latitude\":0.0,\"longitude\":0.0,\"current\":{\"temperature_2m\":29.0}}";

            mockServer.expect(ExpectedCount.times(2), requestTo(containsString("/v1/forecast")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(mockWeatherJson, MediaType.APPLICATION_JSON));

            // Negative coordinates
            OpenMeteoWeatherResponse negResp = client.fetchWeather(-16.0544, -108.2022);
            assertNotNull(negResp);
            Cache cache = cacheManager.getCache("openMeteoWeather");
            assertNotNull(cache.get("-16.05:-108.20"));

            // Zero coordinates
            OpenMeteoWeatherResponse zeroResp = client.fetchWeather(0.0, 0.0);
            assertNotNull(zeroResp);
            assertNotNull(cache.get("0.00:0.00"));

            mockServer.verify();
        }

        @Test
        @DisplayName("WeatherForecastCacheService 4-decimal cache key rounding")
        void testWeatherForecastCacheService_KeyFormatting() {
            WeatherForecastCacheService service = new WeatherForecastCacheService();

            // First call fetches from supplier
            boolean[] supplierCalled = {false};
            OpenMeteoWeatherResponse mockResp = new OpenMeteoWeatherResponse();
            mockResp.setCurrent(new OpenMeteoWeatherResponse.CurrentData());

            OpenMeteoWeatherResponse r1 = service.getOrFetchWeather(16.0544123, 108.2022987, () -> {
                supplierCalled[0] = true;
                return mockResp;
            });
            assertTrue(supplierCalled[0]);
            assertSame(mockResp, r1);

            // Second call with same 4-decimal rounding (16.0544, 108.2023) should hit local cache
            supplierCalled[0] = false;
            OpenMeteoWeatherResponse r2 = service.getOrFetchWeather(16.0544001, 108.2023001, () -> {
                supplierCalled[0] = true;
                return new OpenMeteoWeatherResponse();
            });
            assertFalse(supplierCalled[0], "Must hit local cache without invoking supplier");
            assertSame(mockResp, r2);
        }
    }

    @Nested
    @DisplayName("2. Malformed Payloads & Corrupted Responses Stress Tests")
    class MalformedPayloadsTests {

        @Test
        @DisplayName("Weather: Truncated JSON returns null safely without throwing exception")
        void testFetchWeather_TruncatedJson_ReturnsNullSafely() {
            mockServer.expect(ExpectedCount.once(), requestTo(containsString("/v1/forecast")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("{\"latitude\":16.05,\"current\":{", MediaType.APPLICATION_JSON));

            assertDoesNotThrow(() -> {
                OpenMeteoWeatherResponse response = client.fetchWeather(16.0544, 108.2022);
                assertNull(response, "Truncated JSON must result in null response");
            });
            mockServer.verify();
        }

        @Test
        @DisplayName("Marine: Truncated JSON returns null safely without throwing exception")
        void testFetchMarine_TruncatedJson_ReturnsNullSafely() {
            mockServer.expect(ExpectedCount.once(), requestTo(containsString("/v1/marine")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("{\"hourly\":{\"wave_height\":[1.2, 1.4,", MediaType.APPLICATION_JSON));

            assertDoesNotThrow(() -> {
                OpenMeteoMarineResponse response = client.fetchMarine(16.0544, 108.2022);
                assertNull(response, "Truncated JSON must result in null response");
            });
            mockServer.verify();
        }

        @Test
        @DisplayName("Weather: Completely invalid gibberish returns null safely")
        void testFetchWeather_GibberishContent_ReturnsNullSafely() {
            mockServer.expect(ExpectedCount.once(), requestTo(containsString("/v1/forecast")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("!@#$%^&*()_NOT_A_VALID_JSON", MediaType.APPLICATION_JSON));

            assertDoesNotThrow(() -> {
                OpenMeteoWeatherResponse response = client.fetchWeather(16.0544, 108.2022);
                assertNull(response, "Gibberish response must result in null");
            });
            mockServer.verify();
        }

        @Test
        @DisplayName("Weather: HTML 502 Bad Gateway page with HTTP 200 returns null safely")
        void testFetchWeather_HtmlErrorPageWith200_ReturnsNullSafely() {
            String htmlPage = "<!DOCTYPE html><html><head><title>502 Bad Gateway</title></head><body>502 Bad Gateway</body></html>";
            mockServer.expect(ExpectedCount.once(), requestTo(containsString("/v1/forecast")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(htmlPage, MediaType.TEXT_HTML));

            assertDoesNotThrow(() -> {
                OpenMeteoWeatherResponse response = client.fetchWeather(16.0544, 108.2022);
                assertNull(response, "HTML error page with 200 OK must result in null");
            });
            mockServer.verify();
        }

        @Test
        @DisplayName("Weather: Empty string body with HTTP 200 OK returns null safely")
        void testFetchWeather_EmptyBodyWith200_ReturnsNullSafely() {
            mockServer.expect(ExpectedCount.once(), requestTo(containsString("/v1/forecast")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

            assertDoesNotThrow(() -> {
                OpenMeteoWeatherResponse response = client.fetchWeather(16.0544, 108.2022);
                assertNull(response, "Empty body must return null");
            });
            mockServer.verify();
        }

        @Test
        @DisplayName("Marine: Empty string body with HTTP 200 OK returns null safely")
        void testFetchMarine_EmptyBodyWith200_ReturnsNullSafely() {
            mockServer.expect(ExpectedCount.once(), requestTo(containsString("/v1/marine")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

            assertDoesNotThrow(() -> {
                OpenMeteoMarineResponse response = client.fetchMarine(16.0544, 108.2022);
                assertNull(response, "Empty body must return null");
            });
            mockServer.verify();
        }

        @Test
        @DisplayName("Weather: Whitespace-only body with HTTP 200 OK returns null safely")
        void testFetchWeather_WhitespaceBodyWith200_ReturnsNullSafely() {
            mockServer.expect(ExpectedCount.once(), requestTo(containsString("/v1/forecast")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("   \n\t   ", MediaType.APPLICATION_JSON));

            assertDoesNotThrow(() -> {
                OpenMeteoWeatherResponse response = client.fetchWeather(16.0544, 108.2022);
                assertNull(response, "Whitespace body must return null");
            });
            mockServer.verify();
        }

        @Test
        @DisplayName("Weather: JSON array instead of object returns null safely")
        void testFetchWeather_JsonArrayInsteadOfObject_ReturnsNullSafely() {
            mockServer.expect(ExpectedCount.once(), requestTo(containsString("/v1/forecast")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("[{\"temperature\": 25.0}]", MediaType.APPLICATION_JSON));

            assertDoesNotThrow(() -> {
                OpenMeteoWeatherResponse response = client.fetchWeather(16.0544, 108.2022);
                assertNull(response, "Array payload where object expected must return null");
            });
            mockServer.verify();
        }

        @Test
        @DisplayName("Weather: Empty JSON object {} returns response without NPE")
        void testFetchWeather_EmptyJsonObject_HandledGracefully() {
            mockServer.expect(ExpectedCount.once(), requestTo(containsString("/v1/forecast")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

            assertDoesNotThrow(() -> {
                OpenMeteoWeatherResponse response = client.fetchWeather(16.0544, 108.2022);
                assertNotNull(response);
                assertNull(response.getCurrent());
                assertNull(response.getHourly());
            });
            mockServer.verify();
        }
    }

    @Nested
    @DisplayName("3. HTTP Status Codes & Fault Variations Stress Tests")
    class HttpStatusFaultsTests {

        @Test
        @DisplayName("Weather: 204 No Content returns null without crash")
        void testFetchWeather_Http204NoContent_ReturnsNull() {
            mockServer.expect(ExpectedCount.once(), requestTo(containsString("/v1/forecast")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withStatus(HttpStatus.NO_CONTENT));

            assertDoesNotThrow(() -> {
                OpenMeteoWeatherResponse response = client.fetchWeather(16.0544, 108.2022);
                assertNull(response, "204 No Content should result in null");
            });
            mockServer.verify();
        }

        @Test
        @DisplayName("Weather: 400 Bad Request returns null without crash")
        void testFetchWeather_Http400BadRequest_ReturnsNull() {
            mockServer.expect(ExpectedCount.once(), requestTo(containsString("/v1/forecast")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":true,\"reason\":\"Latitude out of bounds\"}"));

            assertDoesNotThrow(() -> {
                OpenMeteoWeatherResponse response = client.fetchWeather(999.0, 108.2022);
                assertNull(response, "400 Bad Request must return null");
            });
            mockServer.verify();
        }

        @Test
        @DisplayName("Weather: 403 Forbidden returns null without crash")
        void testFetchWeather_Http403Forbidden_ReturnsNull() {
            mockServer.expect(ExpectedCount.once(), requestTo(containsString("/v1/forecast")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withStatus(HttpStatus.FORBIDDEN)
                            .body("Forbidden"));

            assertDoesNotThrow(() -> {
                OpenMeteoWeatherResponse response = client.fetchWeather(16.0544, 108.2022);
                assertNull(response, "403 Forbidden must return null");
            });
            mockServer.verify();
        }

        @Test
        @DisplayName("Weather: 404 Not Found returns null without crash")
        void testFetchWeather_Http404NotFound_ReturnsNull() {
            mockServer.expect(ExpectedCount.once(), requestTo(containsString("/v1/forecast")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND)
                            .body("Not Found"));

            assertDoesNotThrow(() -> {
                OpenMeteoWeatherResponse response = client.fetchWeather(16.0544, 108.2022);
                assertNull(response, "404 Not Found must return null");
            });
            mockServer.verify();
        }

        @Test
        @DisplayName("Marine: 502 Bad Gateway returns null without crash")
        void testFetchMarine_Http502BadGateway_ReturnsNull() {
            mockServer.expect(ExpectedCount.once(), requestTo(containsString("/v1/marine")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withRawStatus(502)
                            .contentType(MediaType.TEXT_HTML)
                            .body("<html><body>502 Bad Gateway</body></html>"));

            assertDoesNotThrow(() -> {
                OpenMeteoMarineResponse response = client.fetchMarine(16.0544, 108.2022);
                assertNull(response, "502 Bad Gateway must return null");
            });
            mockServer.verify();
        }

        @Test
        @DisplayName("Marine: 503 Service Unavailable returns null without crash")
        void testFetchMarine_Http503ServiceUnavailable_ReturnsNull() {
            mockServer.expect(ExpectedCount.once(), requestTo(containsString("/v1/marine")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                            .body("Service Temporarily Unavailable"));

            assertDoesNotThrow(() -> {
                OpenMeteoMarineResponse response = client.fetchMarine(16.0544, 108.2022);
                assertNull(response, "503 Service Unavailable must return null");
            });
            mockServer.verify();
        }

        @Test
        @DisplayName("Weather: 504 Gateway Timeout returns null without crash")
        void testFetchWeather_Http504GatewayTimeout_ReturnsNull() {
            mockServer.expect(ExpectedCount.once(), requestTo(containsString("/v1/forecast")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withRawStatus(504)
                            .body("Gateway Timeout"));

            assertDoesNotThrow(() -> {
                OpenMeteoWeatherResponse response = client.fetchWeather(16.0544, 108.2022);
                assertNull(response, "504 Gateway Timeout must return null");
            });
            mockServer.verify();
        }
    }

    @Nested
    @DisplayName("4. Downstream Adapter Resilience with Corrupted/Null Responses")
    class DownstreamAdapterResilienceTests {

        @Test
        @DisplayName("WeatherProviderAdapter survives gracefully when OpenMeteoApiClient returns null")
        void testAdapter_WhenApiClientReturnsNull_SurvivesWithoutNpe() {
            // Null client responses
            mockServer.expect(ExpectedCount.once(), requestTo(containsString("/v1/forecast")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
            mockServer.expect(ExpectedCount.once(), requestTo(containsString("/v1/marine")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

            WeatherForecastCacheService directCache = new WeatherForecastCacheService();
            WeatherProviderAdapter adapter = new WeatherProviderAdapter(client, directCache);

            // 1. getWeatherByCoordinates when API fails -> returns null
            WeatherInfoDto.WeatherData weatherData = adapter.getWeatherByCoordinates(16.0544, 108.2022);
            assertNull(weatherData);

            // 2. getMarineByCoordinates when API fails -> returns null
            WeatherInfoDto.MarineData marineData = adapter.getMarineByCoordinates(16.0544, 108.2022);
            assertNull(marineData);

            // 3. getTimeWindowForecast when both fail -> returns TimeWindowForecast with all nulls
            WeatherInfoDto.TimeWindowForecast windowForecast = adapter.getTimeWindowForecast(
                    16.0544, 108.2022,
                    LocalDate.now().plusDays(1),
                    LocalTime.of(8, 0),
                    LocalTime.of(12, 0)
            );
            assertNotNull(windowForecast);
            assertNull(windowForecast.getPeakWaveHeight());
            assertNull(windowForecast.getPeakWindSpeed());
            assertNull(windowForecast.getPeakWindGust());
            assertNull(windowForecast.getPeakOceanCurrent());
            assertNull(windowForecast.getMinVisibility());
            assertNull(windowForecast.getSevereWeatherCode());
            assertEquals(0.0, windowForecast.getTotalPrecipitation());
        }

        @Test
        @DisplayName("WeatherProviderAdapter handles mismatched hourly array lengths without IndexOutOfBoundsException")
        void testAdapter_MismatchedHourlyArrays_HandlesSafely() {
            OpenMeteoWeatherResponse weatherResp = new OpenMeteoWeatherResponse();
            OpenMeteoWeatherResponse.HourlyData hourly = new OpenMeteoWeatherResponse.HourlyData();
            // Time has 3 entries
            LocalDate tomorrow = LocalDate.now().plusDays(1);
            hourly.setTime(List.of(
                    tomorrow.atTime(8, 0).toString(),
                    tomorrow.atTime(9, 0).toString(),
                    tomorrow.atTime(10, 0).toString()
            ));
            // But windSpeed has only 1 entry!
            hourly.setWindSpeed10m(List.of(15.0));
            // And windGusts is null!
            hourly.setWindGusts10m(null);
            weatherResp.setHourly(hourly);

            WeatherForecastCacheService mockCache = new WeatherForecastCacheService() {
                @Override
                public OpenMeteoWeatherResponse getOrFetchWeather(double lat, double lon, Supplier<OpenMeteoWeatherResponse> fetcher) {
                    return weatherResp;
                }
                @Override
                public OpenMeteoMarineResponse getOrFetchMarine(double lat, double lon, Supplier<OpenMeteoMarineResponse> fetcher) {
                    return null;
                }
            };

            WeatherProviderAdapter adapter = new WeatherProviderAdapter(client, mockCache);

            assertDoesNotThrow(() -> {
                WeatherInfoDto.TimeWindowForecast forecast = adapter.getTimeWindowForecast(
                        16.0544, 108.2022,
                        tomorrow,
                        LocalTime.of(8, 0),
                        LocalTime.of(11, 0)
                );
                assertNotNull(forecast);
                assertEquals(15.0, forecast.getPeakWindSpeed());
                assertNull(forecast.getPeakWindGust());
            });
        }
    }
}
