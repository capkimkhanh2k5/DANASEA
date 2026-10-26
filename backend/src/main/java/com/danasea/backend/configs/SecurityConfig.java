package com.danasea.backend.configs;

import java.util.Arrays;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.danasea.backend.configs.properties.CorsProperties;
import com.danasea.backend.security.authentication.infrastructure.security.CustomAuthenticationEntryPoint;
import com.danasea.backend.security.authentication.infrastructure.security.JwtAuthenticationFilter;
import com.danasea.backend.security.authentication.presentation.filter.CookieOriginValidationFilter;
import com.danasea.backend.security.authentication.presentation.filter.OtpRateLimitFilter;
import com.danasea.backend.security.authentication.presentation.filter.RateLimitFilter;
import com.danasea.backend.security.authorization.infrastructure.security.CustomAccessDeniedHandler;
import com.danasea.backend.shared.i18n.LocaleContextFilter;
import com.danasea.backend.shared.infrastructure.web.RequestIdFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	private final CorsProperties corsProperties;

	public SecurityConfig(CorsProperties corsProperties) {
		this.corsProperties = corsProperties;
	}

	@Bean
	SecurityFilterChain applicationSecurityFilterChain(
			HttpSecurity http,
			JwtAuthenticationFilter jwtAuthenticationFilter,
			LocaleContextFilter localeContextFilter,
			RateLimitFilter rateLimitFilter,
			OtpRateLimitFilter otpRateLimitFilter,
			CookieOriginValidationFilter cookieOriginValidationFilter,
			RequestIdFilter requestIdFilter,
			CustomAccessDeniedHandler accessDeniedHandler,
			CustomAuthenticationEntryPoint authenticationEntryPoint
	) throws Exception {
		return http
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session ->
						session.sessionCreationPolicy(
								SessionCreationPolicy.STATELESS
						))
				.exceptionHandling(exception -> exception
						.accessDeniedHandler(accessDeniedHandler)
						.authenticationEntryPoint(authenticationEntryPoint))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/payments/webhook/internal/**").hasRole("ADMIN")
						.requestMatchers(
								"/api/auth/login",
								"/api/auth/register",
								"/api/auth/refresh",
								"/api/auth/logout",
								"/api/auth/forgot-password",
								"/api/auth/reset-password",
								"/api/auth/oauth2/**",
								"/api/payments/webhook/**",
								"/api/v1/weather/**",
								"/api/weather/**",
								"/actuator/health",
								"/actuator/health/**",
								"/swagger-ui/**",
								"/swagger-ui.html",
								"/v3/api-docs/**",
								"/error"
						).permitAll()
						.requestMatchers(HttpMethod.GET, "/api/categories", "/api/categories/**").permitAll()
						.requestMatchers(HttpMethod.GET,
								"/api/services", "/api/services/**",
								"/api/v1/catalog", "/api/v1/catalog/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/vendors", "/api/vendors/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/flash-sales/active", "/api/flash-sales/*").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/recently-viewed").permitAll()
						.requestMatchers("/api/admin/**").hasRole("ADMIN")
						.anyRequest()
						.authenticated())
				.addFilterBefore(requestIdFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(cookieOriginValidationFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterAfter(localeContextFilter, JwtAuthenticationFilter.class)
				.addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class)
				.addFilterAfter(otpRateLimitFilter, JwtAuthenticationFilter.class)
				.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(corsProperties.allowedOrigins());
		configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(Arrays.asList(
				"Authorization", "Content-Type", "X-Requested-With", "X-Session-Id", "X-Request-ID",
				"Accept-Language",
				"Idempotency-Key", "X-Payment-Signature"));
		configuration.setExposedHeaders(Arrays.asList(
				"Authorization", "X-Session-Id", "X-Request-ID", "Content-Language", "Vary"));
		configuration.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

}
