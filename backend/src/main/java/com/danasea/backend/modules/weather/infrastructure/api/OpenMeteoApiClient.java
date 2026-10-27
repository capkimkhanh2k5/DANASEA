package com.danasea.backend.modules.weather.infrastructure.api;

import com.danasea.backend.modules.weather.infrastructure.api.dtos.OpenMeteoMarineResponse;
import com.danasea.backend.modules.weather.infrastructure.api.dtos.OpenMeteoWeatherResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.net.http.HttpClient;
import com.danasea.backend.shared.infrastructure.http.HttpRequestDeadline;

@Slf4j
@Component
public class OpenMeteoApiClient {

    private final RestClient restClient;
    private final String weatherBaseUrl;
    private final String marineBaseUrl;

    @Autowired
    public OpenMeteoApiClient(
            RestClient.Builder restClientBuilder,
            @Value("${open-meteo.base-url:https://api.open-meteo.com}") String weatherBaseUrl,
            @Value("${open-meteo.marine-url:https://marine-api.open-meteo.com}") String marineBaseUrl) {
        this(restClientBuilder, weatherBaseUrl, marineBaseUrl, true);
    }

    public OpenMeteoApiClient(
            RestClient.Builder restClientBuilder,
            String weatherBaseUrl,
            String marineBaseUrl,
            boolean applyTimeouts) {
        if (applyTimeouts) {
            var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5)).build());
            requestFactory.setReadTimeout(Duration.ofSeconds(10));
            this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        } else {
            this.restClient = restClientBuilder.build();
        }
        this.weatherBaseUrl = weatherBaseUrl;
        this.marineBaseUrl = marineBaseUrl;
    }

    public OpenMeteoApiClient(
            RestClient restClient,
            String weatherBaseUrl,
            String marineBaseUrl) {
        this.restClient = restClient;
        this.weatherBaseUrl = weatherBaseUrl;
        this.marineBaseUrl = marineBaseUrl;
    }

    @Cacheable(value = "openMeteoWeather", key = "T(java.lang.String).format(T(java.util.Locale).ROOT, '%.2f:%.2f', #latitude, #longitude)")
    public OpenMeteoWeatherResponse fetchWeather(double latitude, double longitude) {
        String url = UriComponentsBuilder.fromUriString(weatherBaseUrl)
                .path("/v1/forecast")
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("current", "temperature_2m,precipitation,wind_speed_10m,wind_gusts_10m,wind_direction_10m,visibility,cloud_cover,weather_code,uv_index,relative_humidity_2m,dew_point_2m,surface_pressure")
                .queryParam("hourly", "temperature_2m,precipitation,precipitation_probability,wind_speed_10m,wind_gusts_10m,wind_direction_10m,weather_code,visibility,uv_index,relative_humidity_2m,dew_point_2m,surface_pressure,cloud_cover")
                .queryParam("forecast_days", 16)
                .queryParam("timezone", "auto")
                .build(false)
                .toUriString();

        try {
            return HttpRequestDeadline.execute(Duration.ofSeconds(15), () -> restClient.get()
                    .uri(url)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        if (resp.getStatusCode().value() == 429) {
                            log.warn("Open-Meteo Weather API rate limit reached (HTTP 429)");
                        } else {
                            log.error("Open-Meteo Weather API client error: {}", resp.getStatusCode());
                        }
                        throw new HttpClientErrorException(resp.getStatusCode(), resp.getStatusText());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        log.error("Open-Meteo Weather API server error: {}", resp.getStatusCode());
                        throw new HttpServerErrorException(resp.getStatusCode(), resp.getStatusText());
                    })
                    .body(OpenMeteoWeatherResponse.class));
        } catch (RestClientResponseException e) {
            log.error("Open-Meteo Weather API error response [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (ResourceAccessException e) {
            log.error("Open-Meteo Weather API connection or timeout: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error fetching weather from Open-Meteo", e);
            return null;
        }
    }

    @Cacheable(value = "openMeteoMarine", key = "T(java.lang.String).format(T(java.util.Locale).ROOT, '%.2f:%.2f', #latitude, #longitude)")
    public OpenMeteoMarineResponse fetchMarine(double latitude, double longitude) {
        String url = UriComponentsBuilder.fromUriString(marineBaseUrl)
                .path("/v1/marine")
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("current", "wave_height,wave_direction,wave_period,swell_wave_height,swell_wave_direction,swell_wave_period,ocean_current_velocity,ocean_current_direction,sea_level_height_msl,sea_surface_temperature")
                .queryParam("hourly", "wave_height,wave_direction,wave_period,swell_wave_height,swell_wave_direction,swell_wave_period,ocean_current_velocity,ocean_current_direction,sea_level_height_msl,sea_surface_temperature")
                .queryParam("models", "best_match")
                .queryParam("wind_speed_unit", "ms")
                .queryParam("forecast_days", 8)
                .queryParam("timezone", "auto")
                .build(false)
                .toUriString();

        try {
            return HttpRequestDeadline.execute(Duration.ofSeconds(15), () -> restClient.get()
                    .uri(url)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        if (resp.getStatusCode().value() == 429) {
                            log.warn("Open-Meteo Marine API rate limit reached (HTTP 429)");
                        } else {
                            log.error("Open-Meteo Marine API client error: {}", resp.getStatusCode());
                        }
                        throw new HttpClientErrorException(resp.getStatusCode(), resp.getStatusText());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        log.error("Open-Meteo Marine API server error: {}", resp.getStatusCode());
                        throw new HttpServerErrorException(resp.getStatusCode(), resp.getStatusText());
                    })
                    .body(OpenMeteoMarineResponse.class));
        } catch (RestClientResponseException e) {
            log.error("Open-Meteo Marine API error response [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (ResourceAccessException e) {
            log.error("Open-Meteo Marine API connection or timeout: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error fetching marine from Open-Meteo", e);
            return null;
        }
    }
}
