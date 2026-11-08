package com.samdev.appointments.notification.application.exception;

import com.samdev.appointments.notification.domain.vo.NotificationId;

/**
 * Mapped to {@code 404}.
 */
public class NotificationNotFoundException extends RuntimeException {

	public NotificationNotFoundException(NotificationId id) {
		super("Notification " + id + " not found");
	}
}
