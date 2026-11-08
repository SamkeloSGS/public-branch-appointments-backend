package com.samdev.appointments.notification.domain.exception;

/**
 * A notification rule was violated. Mapped to {@code 409}.
 */
public class NotificationRuleViolation extends RuntimeException {

	public NotificationRuleViolation(String message) {
		super(message);
	}
}
