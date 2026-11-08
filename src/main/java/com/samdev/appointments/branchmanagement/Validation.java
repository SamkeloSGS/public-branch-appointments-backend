package com.samdev.appointments.branchmanagement;

/**
 * Argument checks for the published value types in this package. Internal to the package: the aggregates use
 * {@code domain.vo.Texts}.
 */
final class Validation {

	private Validation() {}

	/**
	 * Trims a required text and checks its length.
	 */
	static String text(String value, String field, int min, int max) {
		if (value == null) {
			throw new IllegalArgumentException(field + " is required");
		}
		var trimmed = value.strip();
		if (trimmed.length() < min || trimmed.length() > max) {
			throw new IllegalArgumentException(field + " must be " + min + " to " + max + " characters");
		}
		return trimmed;
	}
}
