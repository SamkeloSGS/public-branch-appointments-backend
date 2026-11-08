package com.samdev.appointments.customer.domain.exception;

/**
 * Codes returned with {@code 409} responses, so clients can react without parsing messages.
 */
public enum RuleCode {

	/** The change would leave neither email nor phone. */
	CONTACT_REQUIRED,

	/** A channel was enabled without its contact detail. */
	CHANNEL_WITHOUT_CONTACT,

	/** Both channels would be disabled; service messages cannot be switched off entirely. */
	NO_CHANNEL_ENABLED,

	/** The command is not allowed in the customer's current status. */
	INVALID_STATE,

	/** The signed-in user already has a customer profile. */
	PROFILE_EXISTS,

	/** Staff only: the email belongs to another customer, whose ID is returned. */
	EMAIL_IN_USE,

	/** Self-service: the details could not be used. Deliberately does not say why. */
	CONTACT_DETAILS_UNAVAILABLE
}
