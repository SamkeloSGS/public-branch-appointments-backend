package com.samdev.appointments.booking.application.port;

import java.time.Instant;
import java.util.Optional;

import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.customer.CustomerId;

/**
 * Remembers which appointment a booking request with an {@code Idempotency-Key} created, per customer. Written in
 * the booking transaction.
 */
public interface IdempotencyKeys {

	Optional<AppointmentId> find(CustomerId customerId, String key);

	void remember(CustomerId customerId, String key, AppointmentId appointmentId);

	int purgeOlderThan(Instant cutoff);
}
