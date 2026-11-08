package com.samdev.appointments;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs {@link EventPublicationMaintenance}: retries every {@code appointments.events.retry-interval}, clean-up every
 * night at 02:30. Off with {@code appointments.events.maintenance-enabled=false} (tests).
 */
@Component
@ConditionalOnProperty(name = "appointments.events.maintenance-enabled", havingValue = "true", matchIfMissing = true)
class EventPublicationMaintenanceJob {

	private final EventPublicationMaintenance maintenance;

	EventPublicationMaintenanceJob(EventPublicationMaintenance maintenance) {
		this.maintenance = maintenance;
	}

	@Scheduled(fixedDelayString = "${appointments.events.retry-interval:PT5M}", initialDelayString = "PT1M")
	void retryFailed() {
		maintenance.retryFailed();
	}

	@Scheduled(cron = "0 30 2 * * *")
	void deleteCompleted() {
		maintenance.deleteCompleted();
	}
}
