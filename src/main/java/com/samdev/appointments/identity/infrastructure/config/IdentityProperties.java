package com.samdev.appointments.identity.infrastructure.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param firstAdminEmail invited as branch manager at start-up when no branch manager exists
 * @param firstAdminName display name of that invitation
 * @param signUpUrl Keycloak's registration address, returned with invitations for the admin to pass on
 */
@ConfigurationProperties("appointments.identity")
public record IdentityProperties(@Nullable String firstAdminEmail, @Nullable String firstAdminName,
		@Nullable String signUpUrl) {

	public String firstAdminDisplayName() {
		return firstAdminName == null || firstAdminName.isBlank() ? "Branch manager" : firstAdminName;
	}

	public String signUpAddress() {
		return signUpUrl == null ? "" : signUpUrl;
	}
}
