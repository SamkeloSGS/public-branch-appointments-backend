package com.samdev.appointments.booking.application.port;

import com.samdev.appointments.booking.domain.exception.RejectionCode;
import com.samdev.appointments.booking.domain.vo.Actor;

/**
 * Counts the outcomes of the booking commands for monitoring. Labels are operations, actors and codes only, never an
 * ID, a reference or personal data.
 */
public interface BookingMetrics {

	/**
	 * A new appointment; not counted again when an idempotency key returns an earlier one.
	 */
	void booked(Actor actor);

	void completed(Operation operation);

	void rejected(Operation operation, RejectionCode code);

	/**
	 * A version conflict, constraint race or deadlock that made the command start again.
	 */
	void retried(Operation operation);

	enum Operation {
		BOOK, RESCHEDULE, CANCEL, DISPLACE
	}
}
