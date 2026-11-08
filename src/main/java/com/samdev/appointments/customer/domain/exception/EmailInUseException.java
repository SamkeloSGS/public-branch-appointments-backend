package com.samdev.appointments.customer.domain.exception;

import com.samdev.appointments.customer.CustomerId;

/**
 * Staff used an email that belongs to another non-erased customer. Carries that customer's ID so staff can use the
 * existing record. Never shown to customers.
 */
public class EmailInUseException extends CustomerRuleViolation {

	private final CustomerId existingCustomerId;

	public EmailInUseException(CustomerId existingCustomerId) {
		super(RuleCode.EMAIL_IN_USE, "Another customer already has this email");
		this.existingCustomerId = existingCustomerId;
	}

	public CustomerId getExistingCustomerId() {
		return existingCustomerId;
	}
}
