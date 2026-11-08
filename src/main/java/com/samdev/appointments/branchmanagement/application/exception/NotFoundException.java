package com.samdev.appointments.branchmanagement.application.exception;

/**
 * The addressed aggregate does not exist (or is not visible to the caller). Mapped to {@code 404}.
 */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String type, Object id) {
		super(type + " " + id + " not found");
	}
}
