package com.samdev.appointments.identity.infrastructure.security;

import java.util.Map;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import com.samdev.appointments.identity.infrastructure.config.KeycloakAdminProperties;

/**
 * Keycloak's admin REST API for the realm, signed in as the {@code appointments-backend} service account (client
 * credentials grant, permission {@code manage-users} only). A fresh token per call: these calls are rare.
 */
class KeycloakAdminClient {

	private final RestClient http;
	private final String tokenUrl;
	private final String adminUrl;
	private final KeycloakAdminProperties properties;

	/**
	 * @param realmUrl the realm address the backend calls ({@code appointments.identity.keycloak-url}), such as
	 *        {@code http://localhost:8180/realms/appointments}
	 */
	KeycloakAdminClient(String realmUrl, KeycloakAdminProperties properties, RestClient.Builder http) {

		if (properties.clientSecret() == null || properties.clientSecret().isBlank()) {
			throw new IllegalStateException("appointments.identity.keycloak-admin.client-secret is required while "
					+ "Keycloak admin calls are enabled");
		}

		var realm = realmUrl.endsWith("/") ? realmUrl.substring(0, realmUrl.length() - 1) : realmUrl;

		this.http = http.build();
		this.tokenUrl = realm + "/protocol/openid-connect/token";
		this.adminUrl = realm.replace("/realms/", "/admin/realms/");
		this.properties = properties;
	}

	RestClient http() {
		return http;
	}

	/**
	 * The admin address of something in the realm, such as {@code /users/{id}}.
	 */
	String admin(String path) {
		return adminUrl + path;
	}

	KeycloakAdminProperties properties() {
		return properties;
	}

	@SuppressWarnings("unchecked")
	String serviceAccountToken() {

		var form = new LinkedMultiValueMap<String, String>();
		form.add("grant_type", "client_credentials");
		form.add("client_id", properties.clientId());
		form.add("client_secret", properties.clientSecret());

		var response = http.post()
				.uri(tokenUrl)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.exchange((request, it) -> {
					if (!it.getStatusCode().is2xxSuccessful()) {
						throw refused("the service-account token request", it.getStatusCode());
					}
					return it.bodyTo(Map.class);
				});

		var token = response == null ? null : response.get("access_token");

		if (!(token instanceof String value)) {
			throw new IllegalStateException("Keycloak returned no service-account token");
		}

		return value;
	}

	static IllegalStateException refused(String what, HttpStatusCode status) {
		return new IllegalStateException("Keycloak refused " + what + ": HTTP " + status.value());
	}
}
