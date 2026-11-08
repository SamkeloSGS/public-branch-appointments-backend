package com.samdev.appointments.identity;

/**
 * Keycloak's stable ID for one person's account: the {@code sub} claim of a token.
 */
public record Subject(String value) {

	public Subject {
		if (value == null || value.isBlank() || value.length() > 255) {
			throw new IllegalArgumentException("Subject must be 1 to 255 characters");
		}
	}

	@Override
	public String toString() {
		return value;
	}
}
