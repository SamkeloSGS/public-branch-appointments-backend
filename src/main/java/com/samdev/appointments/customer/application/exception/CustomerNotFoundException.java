package com.samdev.appointments.customer.application.exception;

import com.samdev.appointments.customer.CustomerId;

/**
 * Mapped to {@code 404}.
 */
public class CustomerNotFoundException extends RuntimeException {

	public CustomerNotFoundException(CustomerId id) {
		super("Customer " + id + " not found");
	}

	public CustomerNotFoundException(String message) {
		super(message);
	}

	/**
	 * The signed-in user has no customer profile.
	 */
	public static CustomerNotFoundException noProfile() {
		return new CustomerNotFoundException("You have no customer profile yet");
	}
}
