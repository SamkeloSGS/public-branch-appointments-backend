package com.samdev.appointments.branchmanagement;

import java.time.Instant;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record ServiceDefined(ServiceId serviceId, String name, ServiceCategoryId categoryId, CustomerSegment segment,
		int defaultDurationMinutes, Instant occurredAt) {
}
