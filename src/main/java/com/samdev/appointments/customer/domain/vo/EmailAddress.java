package com.samdev.appointments.customer.domain.vo;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * An email address, stored lowercase. Messages never repeat the value.
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
