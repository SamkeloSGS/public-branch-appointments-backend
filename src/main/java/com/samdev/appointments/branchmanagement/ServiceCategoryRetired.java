package com.samdev.appointments.branchmanagement;

import java.time.Instant;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record ServiceCategoryRetired(ServiceCategoryId categoryId, Instant occurredAt) {
}
