package com.samdev.appointments.identity.infrastructure.security;

import java.time.Duration;
import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

import com.samdev.appointments.identity.AccessControl;
import com.samdev.appointments.identity.PublicEndpoints;
import com.samdev.appointments.identity.infrastructure.config.RateLimitProperties;

/**
 * The application's single resource-server configuration.
 * <ul>
 * <li>Tokens are validated against Keycloak's published keys, the issuer, the {@code appointments-api} audience and
 * expiry with 30 seconds of clock tolerance. Keys are re-fetched when a token names an unknown key.</li>
 * <li>Role claims are ignored: authorities come only from this module's staff records.</li>
 * <li>Public paths are contributed by the modules as {@link PublicEndpoints} beans.</li>
 * <li>{@code /api/v1/admin/**} needs an active staff member signed in at level 2.</li>
 * <li>Requests are rate limited per user or client address once the token is validated
 * ({@link RequestRateLimiter}).</li>
 * <li>Stateless, no CSRF protection (no cookies), no CORS (app and API share a domain).</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class IdentitySecurityConfiguration {

	static final Duration CLOCK_TOLERANCE = Duration.ofSeconds(30);

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectProvider<PublicEndpoints> publicEndpoints,
			AccessControl accessControl, JsonMapper json, RateLimitProperties rateLimits, MeterRegistry metrics)
			throws Exception {

		var errors = new SecurityErrorResponses(json);

		return http
				.csrf(it -> it.disable())
				.sessionManagement(it -> it.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(it -> {
					publicEndpoints.orderedStream()
							.flatMap(endpoints -> endpoints.publicEndpoints().stream())
							.forEach(endpoint -> it.requestMatchers(HttpMethod.valueOf(endpoint.method()),
									endpoint.pattern()).permitAll());
					it.requestMatchers("/error").permitAll();
					it.requestMatchers("/api/v1/admin/**").access((authentication, context) -> {
						if (!(authentication.get() instanceof JwtAuthenticationToken)) {
							return new AuthorizationDecision(false);
						}
						var user = accessControl.currentUser();
						if (!user.isStaff()) {
							return new AuthorizationDecision(false);
						}
						if (!user.secondFactor()) {
							throw new InsufficientUserAuthenticationException(
									"Admin requests need a sign-in with a second factor");
						}
						return new AuthorizationDecision(true);
					});
					it.requestMatchers("/api/v1/**").authenticated();
					it.anyRequest().denyAll();
				})
				.oauth2ResourceServer(it -> it
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
						.accessDeniedHandler(errors))
				.exceptionHandling(it -> it.accessDeniedHandler(errors))
				// Created here, not as a bean, so it runs only in this chain and not again as a servlet filter
				.addFilterAfter(new RequestRateLimiter(rateLimits, errors, metrics),
						BearerTokenAuthenticationFilter.class)
				.build();
	}

	/**
	 * Grants no authorities from the token: the application never trusts a role claim.
	 */
	static JwtAuthenticationConverter jwtAuthenticationConverter() {

		var converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(jwt -> List.of());

		return converter;
	}

	@Bean
	JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
			@Value("${appointments.identity.keycloak-url}") String keycloakUrl,
			@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri,
			@Value("${spring.security.oauth2.resourceserver.jwt.audiences}") String audience) {

		var keys = jwkSetUri.isBlank() ? keycloakUrl + "/protocol/openid-connect/certs" : jwkSetUri;
		var decoder = NimbusJwtDecoder.withJwkSetUri(keys).build();

		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				new JwtTimestampValidator(CLOCK_TOLERANCE),
				new JwtIssuerValidator(issuer),
				new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience))));

		return decoder;
	}
}
