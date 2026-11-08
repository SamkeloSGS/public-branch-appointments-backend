package com.samdev.appointments.notification.application.port;

import java.time.Duration;

import com.samdev.appointments.notification.domain.vo.RetryPolicy;

public interface NotificationSettings {

	/** How long before the start the reminder goes out. */
	Duration reminderLeadTime();

	/** Due notifications processed per dispatcher run. */
	int batchSize();

	RetryPolicy retryPolicy();

	/** The address where the customer manages the appointment with this reference. */
	String manageLink(String reference);
}
