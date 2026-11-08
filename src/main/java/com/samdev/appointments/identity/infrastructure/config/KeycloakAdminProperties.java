package com.samdev.appointments.identity.infrastructure.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The {@code appointments-backend} service account, used to create and email the logins of invited staff and to
 * delete login accounts on erasure.
 *
 * @param enabled {@code false} logs these calls instead of calling Keycloak (tests, or no Keycloak admin access)
 * @param clientSecret from Vault; never committed for a real environment
 * @param setupClientId the client the staff setup link returns to (the frontend's)
 * @param setupRedirectUrl where the staff setup link returns to once the person is ready; must be one of that
 *        client's redirect URIs
 */
@ConfigurationProperties("appointments.identity.keycloak-admin")
public record KeycloakAdminProperties(@DefaultValue("true") boolean enabled,
		@DefaultValue("appointments-backend") String clientId, @Nullable String clientSecret,
		@DefaultValue("appointments-web") String setupClientId,
		@DefaultValue("http://localhost:4200/admin") String setupRedirectUrl) {}
