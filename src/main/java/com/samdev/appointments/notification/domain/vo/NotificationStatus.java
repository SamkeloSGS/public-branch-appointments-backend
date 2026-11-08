package com.samdev.appointments.notification.domain.vo;

/**
 * {@code PENDING} waits for its time or a retry; the others are terminal.
 */
public enum NotificationStatus {

	PENDING, SENT, FAILED, SKIPPED, CANCELLED;

	public boolean isTerminal() {
		return this != PENDING;
	}
}
