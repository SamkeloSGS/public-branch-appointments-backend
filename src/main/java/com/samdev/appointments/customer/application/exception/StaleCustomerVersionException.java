package com.samdev.appointments.customer.application.exception;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.customer.CustomerId;

/**
 * The update was based on an outdated version. Mapped to {@code 412}.
 */
public class StaleCustomerVersionException extends RuntimeException {

	public StaleCustomerVersionException(CustomerId id, long expected, long actual) {
		super("Customer " + id + " is at version " + actual + ", but the request was based on version " + expected);
	}

	public static void check(CustomerId id, @Nullable Long expected, Long actual) {
		if (expected != null && !expected.equals(actual)) {
			throw new StaleCustomerVersionException(id, expected, actual);
		}
	}
}
