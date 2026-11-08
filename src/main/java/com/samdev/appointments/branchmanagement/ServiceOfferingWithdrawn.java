package com.samdev.appointments.branchmanagement;

import java.time.Instant;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record ServiceOfferingWithdrawn(ServiceOfferingId offeringId, ServiceId serviceId, Instant occurredAt) {
}
