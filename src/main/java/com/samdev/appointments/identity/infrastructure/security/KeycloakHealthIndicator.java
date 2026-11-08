package com.samdev.appointments.identity.infrastructure.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * {@code keycloak} in {@code /actuator/health}: UP when the realm's OpenID configuration answers. Without Keycloak
 * nobody can sign in, but requests with a token already issued still work, so it is not part of readiness. Disabled
 * with {@code management.health.keycloak.enabled=false} (tests).
 */
@Component("keycloak")
@ConditionalOnEnabledHealthIndicator("keycloak")
class KeycloakHealthIndicator extends AbstractHealthIndicator {

	private static final Duration TIMEOUT = Duration.ofSeconds(2);

	private final RestClient http;
	private final String discoveryUrl;

	KeycloakHealthIndicator(@Value("${appointments.identity.keycloak-url}") String keycloakUrl) {

		super("Keycloak health check failed");

		var requests = new SimpleClientHttpRequestFactory();
		requests.setConnectTimeout(TIMEOUT);
		requests.setReadTimeout(TIMEOUT);

		this.http = RestClient.builder().requestFactory(requests).build();
		this.discoveryUrl = (keycloakUrl.endsWith("/") ? keycloakUrl : keycloakUrl + "/")
				+ ".well-known/openid-configuration";
	}

	@Override
	protected void doHealthCheck(Health.Builder builder) {

		var status = http.get().uri(discoveryUrl).exchange((request, response) -> response.getStatusCode());

		if (status.is2xxSuccessful()) {
			builder.up();
		} else {
			builder.down().withDetail("status", status.value());
		}
	}
}
