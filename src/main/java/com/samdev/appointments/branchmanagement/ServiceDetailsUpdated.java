package com.samdev.appointments.branchmanagement;

import java.time.Instant;

import org.jmolecules.event.annotation.DomainEvent;

/**
 * The service's description or prerequisites changed.
 */
@DomainEvent
public record ServiceDetailsUpdated(ServiceId serviceId, Instant occurredAt) {
}
