package com.samdev.appointments.booking.application.exception;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.booking.AppointmentId;

/**
 * The action was based on an outdated version. Mapped to {@code 412}.
 */
public class StaleAppointmentVersionException extends RuntimeException {

	public StaleAppointmentVersionException(AppointmentId id, long expected, long actual) {
		super("Appointment " + id + " is at version " + actual + ", but the request was based on version " + expected);
	}

	public static void check(AppointmentId id, @Nullable Long expected, Long actual) {
		if (expected != null && !expected.equals(actual)) {
			throw new StaleAppointmentVersionException(id, expected, actual);
		}
	}
}
