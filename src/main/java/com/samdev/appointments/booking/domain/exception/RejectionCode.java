package com.samdev.appointments.booking.domain.exception;

/**
 * Codes returned with {@code 409} responses, so clients can react without parsing messages.
 */
public enum RejectionCode {

	/** The time is no longer open. */
	SLOT_UNAVAILABLE,

	/** Too soon, too far ahead, or off the slot grid. */
	OUTSIDE_BOOKING_WINDOW,

	/** Branch not active, or service not offered. */
	OFFERING_UNAVAILABLE,

	/** Customer is deactivated or erased. */
	CUSTOMER_INACTIVE,

	/** The signed-in user has no customer profile. */
	PROFILE_REQUIRED,

	/** The customer already has an appointment at that time. */
	OVERLAPPING_APPOINTMENT,

	/** The customer is at the limit of future appointments. */
	TOO_MANY_APPOINTMENTS,

	/** Too late for the customer to cancel or reschedule. */
	PAST_CUTOFF,

	/** The action is not allowed from the current status. */
	INVALID_STATE
}
