package com.samdev.appointments.branchmanagement.application.exception;

/**
 * The client updated an aggregate based on an outdated version. Mapped to {@code 412}.
 */
public class StaleVersionException extends RuntimeException {

	public StaleVersionException(String type, Object id, long expected, long actual) {
		super(type + " " + id + " is at version " + actual + ", but the request was based on version " + expected);
	}

	public static void check(String type, Object id, Long expected, Long actual) {
		if (expected != null && !expected.equals(actual)) {
			throw new StaleVersionException(type, id, expected, actual);
		}
	}
}
