package com.samdev.appointments.booking.application.port;

import java.time.Duration;

/**
 * Limits and timings; placeholders for the business to confirm.
 */
public interface BookingSettings {

	/** How long before the start a customer may still cancel or reschedule. */
	Duration cancelCutoff();

	/** How long after the start before a booked appointment may be marked a no-show. */
	Duration noShowGrace();

	/** Future {@code BOOKED} appointments a customer may hold when booking themselves. */
	int maxFutureAppointments();

	/** Days per availability request. */
	int maxAvailabilityDays();

	/** How long idempotency keys are kept. */
	Duration idempotencyRetention();
}
