package com.samdev.appointments.booking.domain.vo;

import java.util.Locale;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * A short code that identifies an appointment to the customer: 8 characters from an alphabet without look-alikes
 * (no 0/O, 1/I/L). Unique, and never changes.
 */
public record BookingReference(String value) {

	public static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
	public static final int LENGTH = 8;

	private static final Pattern FORMAT = Pattern.compile("[" + ALPHABET + "]{" + LENGTH + "}");

	public BookingReference {
		if (value == null || !FORMAT.matcher(value).matches()) {
			throw new IllegalArgumentException("A booking reference is " + LENGTH + " characters from " + ALPHABET);
		}
	}

	public static BookingReference random(RandomGenerator random) {

		var chars = new char[LENGTH];
		for (var i = 0; i < LENGTH; i++) {
			chars[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
		}

		return new BookingReference(new String(chars));
	}

	/**
	 * Accepts lowercase and surrounding spaces, as typed by people.
	 */
	public static BookingReference parse(String input) {
		return new BookingReference(input == null ? null : input.strip().toUpperCase(Locale.ROOT));
	}

	@Override
	public String toString() {
		return value;
	}
}
