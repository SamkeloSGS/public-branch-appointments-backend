package com.samdev.appointments.notification.domain.vo;

/**
 * Which message. The first three supersede pending messages about the same appointment.
 */
public enum NotificationType {

	CONFIRMATION, RESCHEDULED, CANCELLATION, REBOOKING_REQUIRED, REMINDER;

	public boolean supersedes() {
		return this == RESCHEDULED || this == CANCELLATION || this == REBOOKING_REQUIRED;
	}
}
