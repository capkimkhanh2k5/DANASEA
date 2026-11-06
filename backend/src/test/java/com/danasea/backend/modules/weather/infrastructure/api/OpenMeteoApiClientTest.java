package com.danasea.backend.modules.weather.infrastructure.api;

import com.danasea.backend.modules.weather.infrastructure.api.dtos.OpenMeteoMarineResponse;
import com.danasea.backend.modules.weather.infrastructure.api.dtos.OpenMeteoWeatherResponse;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("OpenMeteoApiClient Comprehensive Unit & Integration Tests")
public class OpenMeteoApiClientTest {

    @Nested
    @DisplayName("MockRestServiceServer Unit Tests (Deterministic & Offline)")
    class MockRestServiceServerTests {

        private RestClient.Builder restClientBuilder;
        private MockRestServiceServer mockServer;
        private OpenMeteoApiClient client;

        @BeforeEach
        void setUp() {
            restClientBuilder = RestClient.builder();
            mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
            client = new OpenMeteoApiClient(
                    restClientBuilder,
                    "https://api.open-meteo.com",
                    "https://marine-api.open-meteo.com",
                    false
            );
        }

        @Test
        @DisplayName("16-Day Weather: Parsed all 12 weather fields and 384 hourly records successfully")
        void testFetchWeather_Success_16Days_ParsedAll12Fields() {
            String mockWeatherJson = buildMockWeatherJson(384);

            mockServer.expect(ExpectedCount.once(), requestTo(containsString("/v1/forecast")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(mockWeatherJson, MediaType.APPLICATION_JSON));

            OpenMeteoWeatherResponse response = client.fetchWeather(16.0544, 108.2022);

            assertNotNull(response);
            mockServer.verify();

            // Validate CurrentData (12 fields)
            OpenMeteoWeatherResponse.CurrentData current = response.getCurrent();
            assertNotNull(current);
            assertEquals(28.5, current.getTemperature2m());
            assertEquals(75, current.getRelativeHumidity2m());
            assertEquals(23.2, current.getDewPoint2m());
            assertEquals(0.0, current.getPrecipitation());
            assertEquals(1012.0, current.getSurfacePressure());
            assertEquals(20, current.getCloudCover());
            assertEquals(10000.0, current.getVisibility());
            assertEquals(6.5, current.getUvIndex());
            assertEquals(1, current.getWeatherCode());
            assertEquals(14.2, current.getWindSpeed10m());
            assertEquals(90, current.getWindDirection10m());
            assertEquals(19.8, current.getWindGusts10m());

            // Validate HourlyData (16 days = 384 hours)
            OpenMeteoWeatherResponse.HourlyData hourly = response.getHourly();
            assertNotNull(hourly);
            assertEquals(384, hourly.getTime().size());
            assertEquals(384, hourly.getTemperature2m().size());
            assertEquals(384, hourly.getRelativeHumidity2m().size());
            assertEquals(384, hourly.getDewPoint2m().size());
            assertEquals(384, hourly.getPrecipitation().size());
            assertEquals(384, hourly.getPrecipitationProbability().size());
            assertEquals(384, hourly.getSurfacePressure().size());
            assertEquals(384, hourly.getCloudCover().size());
            assertEquals(384, hourly.getVisibility().size());
            assertEquals(384, hourly.getUvIndex().size());
            assertEquals(384, hourly.getWeatherCode().size());
            assertEquals(384, hourly.getWindSpeed10m().size());
            assertEquals(384, hourly.getWindDirection10m().size());
            assertEquals(384, hourly.getWindGusts10m().size());
        }

        @Test
        @DisplayName("8-Day Marine: Parsed all 9 marine fields + sea level & SST and 192 hourly records successfully")
        void testFetchMarine_Success_8Days_ParsedAll9Fields() {
            String mockMarineJson = buildMockMarineJson(192);

            mockServer.expect(ExpectedCount.once(), requestTo(containsString("/v1/marine")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(mockMarineJson, MediaType.APPLICATION_JSON));

            OpenMeteoMarineResponse response = client.fetchMarine(16.0544, 108.2022);

            assertNotNull(response);
            mockServer.verify();

            // Validate CurrentData (9 marine fields + SST & Sea level)
            OpenMeteoMarineResponse.CurrentData current = response.getCurrent();
            assertNotNull(current);
            assertEquals(0.55, current.getWaveHeight());
            assertEquals(60.0, current.getWaveDirection());
            assertEquals(5.0, current.getWavePeriod());
            assertEquals(0.30, current.getSwellWaveHeight());
            assertEquals(70.0, current.getSwellWaveDirection());
            assertEquals(6.0, current.getSwellWavePeriod());
            assertEquals(0.25, current.getOceanCurrentVelocity());
            assertEquals(120.0, current.getOceanCurrentDirection());
            assertEquals(0.10, current.getSeaLevelHeightMsl());
            assertEquals(29.0, current.getSeaSurfaceTemperature());

            // Validate HourlyData (8 days = 192 hours)
            OpenMeteoMarineResponse.HourlyData hourly = response.getHourly();
            assertNotNull(hourly);
            assertEquals(192, hourly.getTime().size());
            assertEquals(192, hourly.getWaveHeight().size());
            assertEquals(192, hourly.getWaveDirection().size());
            assertEquals(192, hourly.getWavePeriod().size());
            assertEquals(192, hourly.getSwellWaveHeight().size());
            assertEquals(192, hourly.getSwellWaveDirection().size());
            assertEquals(192, hourly.getSwellWavePeriod().size());
            assertEquals(192, hourly.getOceanCurrentVelocity().size());
            assertEquals(192, hourly.getOceanCurrentDirection().size());
            assertEquals(192, hourly.getSeaLevelHeightMsl().size());
            assertEquals(192, hourly.getSeaSurfaceTemperature().size());
        }

        @Test
        @DisplayName("Resilience: 429 Rate Limit on Weather API returns null safely without throwing uncaught exception")
        void testFetchWeather_RateLimit429_ReturnsNullWithoutCrash() {
            mockServer.expect(ExpectedCount.once(), requestTo(containsString("/v1/forecast")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withRawStatus(429)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":true,\"reason\":\"Daily API rate limit exceeded\"}"));

            OpenMeteoWeatherResponse response = client.fetchWeather(16.0544, 108.2022);

            assertNull(response, "Client must handle 429 gracefully and return null");
            mockServer.verify();
        }

        @Test
        @DisplayName("Resilience: 429 Rate Limit on Marine API returns null safely without throwing uncaught exception")
        void testFetchMarine_RateLimit429_ReturnsNullWithoutCrash() {
            mockServer.expect(ExpectedCount.once(), requestTo(containsString("/v1/marine")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withRawStatus(429)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":true,\"reason\":\"Hourly rate limit exceeded\"}"));

            OpenMeteoMarineResponse response = client.fetchMarine(16.0544, 108.2022);

            assertNull(response, "Client must handle 429 gracefully and return null");
            mockServer.verify();
        }

        @Test
        @DisplayName("Resilience: 500 Internal Server Error on Weather API returns null safely")
        void testFetchWeather_ServerError500_ReturnsNullWithoutCrash() {
            mockServer.expect(ExpectedCount.once(), requestTo(containsString("/v1/forecast")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withServerError()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":true,\"reason\":\"Internal service error\"}"));

            OpenMeteoWeatherResponse response = client.fetchWeather(16.0544, 108.2022);

            assertNull(response, "Client must handle 500 gracefully and return null");
            mockServer.verify();
        }

        @Test
        @DisplayName("Resilience: 500 Internal Server Error on Marine API returns null safely")
        void testFetchMarine_ServerError500_ReturnsNullWithoutCrash() {
            mockServer.expect(ExpectedCount.once(), requestTo(containsString("/v1/marine")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withServerError()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":true,\"reason\":\"Marine server failure\"}"));

            OpenMeteoMarineResponse response = client.fetchMarine(16.0544, 108.2022);

            assertNull(response, "Client must handle 500 gracefully and return null");
            mockServer.verify();
        }
    }

    @Nested
    @DisplayName("HTTP Transport Integration Tests (Loopback Server)")
    class HttpTransportIntegrationTests {

        private OpenMeteoApiClient client;
        private HttpServer server;
        private final AtomicReference<String> query = new AtomicReference<>();

        @BeforeEach
        void setUp() throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/forecast", exchange -> {
                query.set(exchange.getRequestURI().getRawQuery());
                byte[] body = buildMockWeatherJson(384).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            server.createContext("/v1/marine", exchange -> {
                query.set(exchange.getRequestURI().getRawQuery());
                byte[] body = buildMockMarineJson(192).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            server.start();
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            client = new OpenMeteoApiClient(RestClient.builder(), base, base);
        }

        @AfterEach
        void stopServer() {
            if (server != null) { server.stop(0); }
        }

        @Test
        @DisplayName("HTTP: Fetch Weather returns 16-day forecast and valid parameters")
        void shouldFetchWeatherSuccessfully() {
            double lat = 16.0544;
            double lon = 108.2022;

            OpenMeteoWeatherResponse response = client.fetchWeather(lat, lon);
            assertNotNull(response);
            assertNotNull(response.getCurrent());
            assertEquals(28.5, response.getCurrent().getTemperature2m());
            assertNotNull(response.getHourly());
            assertEquals(384, response.getHourly().getTime().size());
            assertTrue(query.get().contains("forecast_days=16"));
            assertTrue(query.get().contains("latitude=16.0544"));
            assertTrue(query.get().contains("longitude=108.2022"));
        }

        @Test
        @DisplayName("HTTP: Fetch Marine returns 8-day forecast (192 hours) and marine fields")
        void shouldFetchMarineSuccessfully() {
            double lat = 16.0544;
            double lon = 108.2022;

            OpenMeteoMarineResponse response = client.fetchMarine(lat, lon);
            assertNotNull(response);
            assertNotNull(response.getHourly());
            assertEquals(192, response.getHourly().getTime().size());
            assertTrue(query.get().contains("forecast_days=8"));
            assertTrue(query.get().contains("latitude=16.0544"));
            assertTrue(query.get().contains("longitude=108.2022"));
        }
    }

    private static String buildMockWeatherJson(int hours) {
        StringBuilder sb = new StringBuilder(16384);
        sb.append("{\"latitude\":16.0544,\"longitude\":108.2022,");
        sb.append("\"current\":{");
        sb.append("\"time\":\"2026-09-20T12:00\",");
        sb.append("\"temperature_2m\":28.5,");
        sb.append("\"relative_humidity_2m\":75,");
        sb.append("\"dew_point_2m\":23.2,");
        sb.append("\"precipitation\":0.0,");
        sb.append("\"surface_pressure\":1012.0,");
        sb.append("\"cloud_cover\":20,");
        sb.append("\"visibility\":10000.0,");
        sb.append("\"uv_index\":6.5,");
        sb.append("\"weather_code\":1,");
        sb.append("\"wind_speed_10m\":14.2,");
        sb.append("\"wind_direction_10m\":90,");
        sb.append("\"wind_gusts_10m\":19.8");
        sb.append("},");
        sb.append("\"hourly\":{");

        sb.append("\"time\":[");
        for (int i = 0; i < hours; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format("\"2026-09-20T%02d:00\"", i % 24));
        }
        sb.append("],");

        appendMetricArray(sb, "temperature_2m", hours, 28.0);
        appendMetricArray(sb, "precipitation", hours, 0.0);
        appendMetricArray(sb, "precipitation_probability", hours, 10);
        appendMetricArray(sb, "wind_speed_10m", hours, 12.5);
        appendMetricArray(sb, "wind_gusts_10m", hours, 18.0);
        appendMetricArray(sb, "wind_direction_10m", hours, 90);
        appendMetricArray(sb, "weather_code", hours, 1);
        appendMetricArray(sb, "visibility", hours, 10000.0);
        appendMetricArray(sb, "uv_index", hours, 5.0);
        appendMetricArray(sb, "relative_humidity_2m", hours, 70);
        appendMetricArray(sb, "dew_point_2m", hours, 22.0);
        appendMetricArray(sb, "surface_pressure", hours, 1013.0);
        appendMetricArray(sb, "cloud_cover", hours, 15, true);

        sb.append("}}");
        return sb.toString();
    }

    private static String buildMockMarineJson(int hours) {
        StringBuilder sb = new StringBuilder(16384);
        sb.append("{\"latitude\":16.0544,\"longitude\":108.2022,");
        sb.append("\"current\":{");
        sb.append("\"time\":\"2026-09-20T12:00\",");
        sb.append("\"wave_height\":0.55,");
        sb.append("\"wave_direction\":60.0,");
        sb.append("\"wave_period\":5.0,");
        sb.append("\"swell_wave_height\":0.30,");
        sb.append("\"swell_wave_direction\":70.0,");
        sb.append("\"swell_wave_period\":6.0,");
        sb.append("\"ocean_current_velocity\":0.25,");
        sb.append("\"ocean_current_direction\":120.0,");
        sb.append("\"sea_level_height_msl\":0.10,");
        sb.append("\"sea_surface_temperature\":29.0");
        sb.append("},");
        sb.append("\"hourly\":{");

        sb.append("\"time\":[");
        for (int i = 0; i < hours; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format("\"2026-09-20T%02d:00\"", i % 24));
        }
        sb.append("],");

        appendMetricArray(sb, "wave_height", hours, 0.50);
        appendMetricArray(sb, "wave_direction", hours, 65.0);
        appendMetricArray(sb, "wave_period", hours, 5.2);
        appendMetricArray(sb, "swell_wave_height", hours, 0.35);
        appendMetricArray(sb, "swell_wave_direction", hours, 75.0);
        appendMetricArray(sb, "swell_wave_period", hours, 6.1);
        appendMetricArray(sb, "ocean_current_velocity", hours, 0.20);
        appendMetricArray(sb, "ocean_current_direction", hours, 110.0);
        appendMetricArray(sb, "sea_level_height_msl", hours, 0.05);
        appendMetricArray(sb, "sea_surface_temperature", hours, 28.8, true);

        sb.append("}}");
        return sb.toString();
    }

    private static void appendMetricArray(StringBuilder sb, String name, int count, Object val) {
        appendMetricArray(sb, name, count, val, false);
    }

    private static void appendMetricArray(StringBuilder sb, String name, int count, Object val, boolean isLast) {
        sb.append("\"").append(name).append("\":[");
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(",");
            sb.append(val);
        }
        sb.append("]");
        if (!isLast) sb.append(",");
    }
}
