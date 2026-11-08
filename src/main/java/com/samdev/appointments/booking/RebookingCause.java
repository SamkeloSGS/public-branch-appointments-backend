package com.samdev.appointments.booking;

/**
 * Why an appointment needs a new time: its time is no longer bookable at all ({@code BRANCH_UNAVAILABLE}: closure,
 * reduced hours or zero capacity), or a branch manager chose it to resolve overbooking
 * ({@code CAPACITY_REDUCED}).
 */
public enum RebookingCause {
	BRANCH_UNAVAILABLE, CAPACITY_REDUCED
}
