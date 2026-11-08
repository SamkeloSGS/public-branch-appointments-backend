package com.samdev.appointments.branchmanagement;

import java.time.Instant;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record ServiceMovedToCategory(ServiceId serviceId, ServiceCategoryId categoryId, Instant occurredAt) {
}
