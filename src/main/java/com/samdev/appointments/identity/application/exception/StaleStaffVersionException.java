package com.samdev.appointments.identity.application.exception;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.identity.StaffMemberId;

/**
 * The update was based on an outdated version. Mapped to {@code 412}.
 */
public class StaleStaffVersionException extends RuntimeException {

	public StaleStaffVersionException(StaffMemberId id, long expected, long actual) {
		super("Staff member " + id + " is at version " + actual + ", but the request was based on version " + expected);
	}

	public static void check(StaffMemberId id, @Nullable Long expected, Long actual) {
		if (expected != null && !expected.equals(actual)) {
			throw new StaleStaffVersionException(id, expected, actual);
		}
	}
}
