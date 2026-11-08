package com.samdev.appointments;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Maintenance of the event publication registry ({@code public.event_publication}), see
 * {@link EventPublicationMaintenance}.
 *
 * @param maintenanceEnabled {@code false} stops the scheduled runs (tests call the maintenance themselves)
 * @param retryInterval how often failed publications are resubmitted, and the least age of one before its first retry
 * @param maxAttempts attempts per publication, the first delivery included; then it stays FAILED for a person to
 *     look at, and the outbox alert fires
 * @param retention how long completed publications are kept before they are deleted
 */
@ConfigurationProperties("appointments.events")
public record EventPublicationProperties(@DefaultValue("true") boolean maintenanceEnabled,
		@DefaultValue("PT5M") Duration retryInterval, @DefaultValue("12") int maxAttempts,
		@DefaultValue("P7D") Duration retention) {}
