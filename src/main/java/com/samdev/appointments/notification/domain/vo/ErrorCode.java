package com.samdev.appointments.notification.domain.vo;

import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Reduces whatever a provider reports to an error code or category that is safe to store: anything that looks like
 * an email address or a phone number is removed, and the result is short.
 */
public final class ErrorCode {

	public static final int MAX_LENGTH = 64;

	private static final Pattern CODE = Pattern.compile("[A-Za-z0-9_.:-]{1," + MAX_LENGTH + "}");
	private static final Pattern EMAIL = Pattern.compile("[^\\s@<>\"']+@[^\\s@<>\"']+");
	private static final Pattern NUMBER = Pattern.compile("\\+?\\d[\\d\\s().-]{5,}\\d");

	private ErrorCode() {}

	public static @Nullable String reduce(@Nullable String error) {

		if (error == null || error.isBlank()) {
			return null;
		}

		var value = error.strip();

		if (CODE.matcher(value).matches() && !NUMBER.matcher(value).find()) {
			return value;
		}

		value = EMAIL.matcher(value).replaceAll("[address]");
		value = NUMBER.matcher(value).replaceAll("[number]");
		value = value.replaceAll("\\s+", " ");

		return value.length() <= MAX_LENGTH ? value : value.substring(0, MAX_LENGTH);
	}
}
