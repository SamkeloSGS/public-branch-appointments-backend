package com.samdev.appointments.identity.domain.exception;

/**
 * A staff rule was violated. Mapped to {@code 409} with the {@link RuleCode}.
 */
public class StaffRuleViolation extends RuntimeException {

	private final RuleCode code;

	public StaffRuleViolation(RuleCode code, String message) {
		super(message);
		this.code = code;
	}

	public RuleCode getCode() {
		return code;
	}
}
