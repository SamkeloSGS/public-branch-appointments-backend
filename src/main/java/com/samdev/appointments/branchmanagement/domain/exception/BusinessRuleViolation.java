package com.samdev.appointments.branchmanagement.domain.exception;

/**
 * An invariant, state transition or uniqueness rule was violated. The web layer maps it to {@code 409}.
 * Malformed values are reported as {@link IllegalArgumentException} instead ({@code 400}).
 */
public class BusinessRuleViolation extends RuntimeException {

	public BusinessRuleViolation(String message) {
		super(message);
	}
}
