package com.samdev.appointments.notification.infrastructure.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.samdev.appointments.notification.application.handler.NotificationDispatcher;

/**
 * Runs the dispatcher on a fixed interval. Safe on several instances at once: each notification is claimed by one.
 */
@Component
@ConditionalOnProperty(name = "appointments.notification.dispatcher-enabled", havingValue = "true",
		matchIfMissing = true)
class DispatcherJob {

	private static final Logger LOG = LoggerFactory.getLogger(DispatcherJob.class);

	private final NotificationDispatcher dispatcher;

	DispatcherJob(NotificationDispatcher dispatcher) {
		this.dispatcher = dispatcher;
	}

	@Scheduled(fixedDelayString = "${appointments.notification.dispatcher-interval:PT1M}", initialDelayString = "PT20S")
	void dispatch() {
		var processed = dispatcher.dispatchDue();
		if (processed > 0) {
			LOG.info("Processed {} due notifications", processed);
		}
	}
}
