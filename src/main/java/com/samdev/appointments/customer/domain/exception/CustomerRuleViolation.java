package com.samdev.appointments.customer.domain.exception;

/**
 * A customer rule was violated. Mapped to {@code 409} with the {@link RuleCode}. Messages never contain names or
 * contact details.
 */
public class CustomerRuleViolation extends RuntimeException {

	private final RuleCode code;

	public CustomerRuleViolation(RuleCode code, String message) {
		super(message);
		this.code = code;
	}

	/**
	 * The self-service answer when details belong to someone else: it does not say why.
	 */
	public static CustomerRuleViolation detailsUnavailable() {
		return new CustomerRuleViolation(RuleCode.CONTACT_DETAILS_UNAVAILABLE,
				"These details could not be used. Check them or contact the branch.");
	}

	public RuleCode getCode() {
		return code;
	}
}
