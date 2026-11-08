package com.samdev.appointments.branchmanagement.domain.vo;

import java.time.Duration;

/**
 * Conversions between {@link Duration} and the whole minutes stored by this module.
 */
public final class Durations {

	private Durations() {}

	public static int wholeMinutes(Duration duration, String field) {
		if (duration == null) {
			throw new IllegalArgumentException(field + " is required");
		}
		if (duration.isNegative() || duration.toSecondsPart() != 0 || duration.toNanosPart() != 0) {
			throw new IllegalArgumentException(field + " must be a non-negative number of whole minutes");
		}
		return Math.toIntExact(duration.toMinutes());
	}

	public static int positiveWholeMinutes(Duration duration, String field) {
		int minutes = wholeMinutes(duration, field);
		if (minutes == 0) {
			throw new IllegalArgumentException(field + " must be positive");
		}
		return minutes;
	}
}
