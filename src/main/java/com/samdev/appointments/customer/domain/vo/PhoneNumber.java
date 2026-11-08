package com.samdev.appointments.customer.domain.vo;

import java.util.regex.Pattern;

/**
 * A phone number in E.164 form: {@code +} followed by 8 to 15 digits. Messages never repeat the value.
 */
public record PhoneNumber(String value) {

	private static final Pattern E164 = Pattern.compile("\\+\\d{8,15}");
	private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-.()]");

	public PhoneNumber {
		if (value == null || !E164.matcher(value).matches()) {
			throw new IllegalArgumentException("Phone number must be + followed by 8 to 15 digits");
		}
	}

	/**
	 * Normalises input before validation: spaces, dashes, dots and parentheses are removed, a leading {@code 00}
	 * becomes {@code +}, and a local number with a leading {@code 0} gets the default country code.
	 *
	 * @param defaultCountryCode digits only, such as {@code 27}
	 */
	public static PhoneNumber parse(String input, String defaultCountryCode) {

		if (input == null) {
			throw new IllegalArgumentException("Phone number is required");
		}

		var value = SEPARATORS.matcher(input.strip()).replaceAll("");

		if (value.startsWith("00")) {
			value = "+" + value.substring(2);
		} else if (value.startsWith("0")) {
			value = "+" + defaultCountryCode + value.substring(1);
		}

		return new PhoneNumber(value);
	}

	@Override
	public String toString() {
		return value;
	}
}
