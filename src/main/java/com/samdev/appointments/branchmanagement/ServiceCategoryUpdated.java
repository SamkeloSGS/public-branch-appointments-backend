package com.samdev.appointments.branchmanagement;

import java.time.Instant;

import org.jmolecules.event.annotation.DomainEvent;
import org.jspecify.annotations.Nullable;

@DomainEvent
public record ServiceCategoryUpdated(ServiceCategoryId categoryId, String name, @Nullable String description,
		Instant occurredAt) {
}
