package com.samdev.appointments.branchmanagement.domain.vo;

/**
 * Text checks for the aggregates. Violations are reported as {@link IllegalArgumentException}, which the web layer
 * maps to {@code 400}.
 */
public final class Texts {

	private Texts() {}

	/**
	 * Trims a required text and checks its length.
	 */
	public static String required(String value, String field, int min, int max) {
		if (value == null) {
			throw new IllegalArgumentException(field + " is required");
		}
		var trimmed = value.strip();
		if (trimmed.length() < min || trimmed.length() > max) {
			throw new IllegalArgumentException(field + " must be " + min + " to " + max + " characters");
		}
		return trimmed;
	}

	/**
	 * Trims an optional text; blank becomes {@code null}.
	 */
	public static String optional(String value, String field, int max) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return required(value, field, 1, max);
	}
}
