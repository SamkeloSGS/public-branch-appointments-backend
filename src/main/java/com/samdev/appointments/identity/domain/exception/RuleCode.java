package com.samdev.appointments.identity.domain.exception;

/**
 * Codes returned with {@code 409} responses, so clients can react without parsing messages.
 */
public enum RuleCode {
	EMAIL_IN_USE, LAST_BRANCH_MANAGER, SELF_CHANGE_NOT_ALLOWED, INVALID_STATE
}
