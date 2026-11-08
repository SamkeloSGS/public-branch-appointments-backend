package com.samdev.appointments.booking;

/**
 * {@code BOOKED -> CHECKED_IN -> COMPLETED}; {@code BOOKED -> NO_SHOW}; {@code BOOKED -> REBOOKING_REQUIRED} when its
 * time is no longer bookable; {@code BOOKED | REBOOKING_REQUIRED -> CANCELLED}, and back to {@code BOOKED} by
 * rescheduling. {@code COMPLETED}, {@code NO_SHOW} and {@code CANCELLED} are terminal.
 */
public enum AppointmentStatus {

	BOOKED, CHECKED_IN, COMPLETED, NO_SHOW, REBOOKING_REQUIRED, CANCELLED;

	public boolean isTerminal() {
		return this == COMPLETED || this == NO_SHOW || this == CANCELLED;
	}
}
