package com.samdev.appointments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.stereotype.Component;

/**
 * Keeps the event publication registry (the outbox, {@code public.event_publication}) healthy.
 * <ul>
 * <li><b>Retry:</b> a listener that threw is marked FAILED by Spring Modulith; one that never finished is marked
 * FAILED after the staleness period ({@code spring.modulith.events.staleness}). Failed publications are resubmitted
 * to their listener every {@code retry-interval}, up to {@code max-attempts}; after that they stay FAILED, the outbox
 * gauges and alert show them, and a restart redelivers them once more. Listeners must be idempotent (restarts already
 * redeliver), so a retry of work that partly happened is safe. On several instances each publication is resubmitted by one only: Spring Modulith
 * claims it with a conditional update.</li>
 * <li><b>Clean-up:</b> completed publications are deleted after {@code retention}.</li>
 * </ul>
 * Scheduled by {@link EventPublicationMaintenanceJob}.
 */
@Component
@EnableConfigurationProperties(EventPublicationProperties.class)
public class EventPublicationMaintenance {

	private static final Logger LOG = LoggerFactory.getLogger(EventPublicationMaintenance.class);

	private final FailedEventPublications failed;
	private final CompletedEventPublications completed;
	private final EventPublicationProperties properties;

	EventPublicationMaintenance(FailedEventPublications failed, CompletedEventPublications completed,
			EventPublicationProperties properties) {

		this.failed = failed;
		this.completed = completed;
		this.properties = properties;
	}

	/**
	 * Resubmits failed publications that are at least one retry interval old and have attempts left.
	 */
	public void retryFailed() {

		var options = ResubmissionOptions.defaults()
				.withMinAge(properties.retryInterval())
				.withBatchSize(100)
				.withMaxInFlight(20)
				.withFilter(it -> it.getCompletionAttempts() < properties.maxAttempts());

		failed.resubmit(options);
	}

	/**
	 * Deletes completed publications older than the retention period.
	 */
	public void deleteCompleted() {
		completed.deletePublicationsOlderThan(properties.retention());
		LOG.debug("Deleted completed event publications older than {}", properties.retention());
	}
}
