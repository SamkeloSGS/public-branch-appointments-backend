package com.samdev.appointments.identity.application.exception;

import com.samdev.appointments.identity.StaffMemberId;

/**
 * Mapped to {@code 404}.
 */
public class StaffNotFoundException extends RuntimeException {

	public StaffNotFoundException(StaffMemberId id) {
		super("Staff member " + id + " not found");
	}
}
