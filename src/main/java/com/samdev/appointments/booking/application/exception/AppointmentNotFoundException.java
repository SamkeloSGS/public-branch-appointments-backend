package com.samdev.appointments.booking.application.exception;

/**
 * Mapped to {@code 404}.
 */
public class AppointmentNotFoundException extends RuntimeException {

	public AppointmentNotFoundException(String message) {
		super(message);
	}

	public static AppointmentNotFoundException byReference() {
		return new AppointmentNotFoundException("No appointment with this reference");
	}
}
