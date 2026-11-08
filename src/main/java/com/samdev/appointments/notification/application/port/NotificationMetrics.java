package com.samdev.appointments.notification.application.port;

import com.samdev.appointments.notification.domain.vo.Channel;
import com.samdev.appointments.notification.domain.vo.SendOutcome;
import com.samdev.appointments.notification.domain.vo.StatusReason;

/**
 * Counts what the dispatcher did for monitoring. Labels are channels, outcomes and reasons only.
 */
public interface NotificationMetrics {

	/**
	 * One send attempt on one channel, counted when the provider answered (or failed to).
	 */
	void attempted(Channel channel, SendOutcome.Kind outcome);

	/**
	 * A notification not sent because it no longer applied or could not reach the customer.
	 */
	void skipped(StatusReason reason);
}
