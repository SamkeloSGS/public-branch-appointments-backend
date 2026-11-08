package com.samdev.appointments.booking.application.exception;

/**
 * The branch is not active or does not offer the service. Mapped to {@code 404} for availability.
 */
public class OfferingNotFoundException extends RuntimeException {

	public OfferingNotFoundException() {
		super("The branch is not open for bookings or does not offer this service");
	}
}
