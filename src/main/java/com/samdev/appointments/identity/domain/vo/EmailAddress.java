package com.samdev.appointments.identity.domain.vo;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A work email address, stored lowercase.
 */
public record EmailAddress(String value) {

	private static final Pattern FORMAT = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

	public EmailAddress {
		if (value == null) {
			throw new IllegalArgumentException("Email is required");
		}
		value = value.strip().toLowerCase(Locale.ROOT);
		if (value.length() > 254 || !FORMAT.matcher(value).matches()) {
			throw new IllegalArgumentException("Email address is not valid");
		}
	}

	@Override
	public String toString() {
		return value;
	}
}
