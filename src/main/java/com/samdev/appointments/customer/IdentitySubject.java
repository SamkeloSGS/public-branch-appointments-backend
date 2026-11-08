package com.samdev.appointments.customer;

/**
 * The stable ID of a login account in Identity (the token's {@code sub}).
 */
public record IdentitySubject(String value) {

	public IdentitySubject {
		if (value == null || value.isBlank() || value.length() > 255) {
			throw new IllegalArgumentException("Identity subject must be 1 to 255 characters");
		}
	}

	@Override
	public String toString() {
		return value;
	}
}
