package com.samdev.appointments.branchmanagement;

import java.time.Instant;

import org.jmolecules.event.annotation.DomainEvent;
import org.jspecify.annotations.Nullable;

@DomainEvent
public record ServiceCategoryDefined(ServiceCategoryId categoryId, String name, CustomerSegment segment,
		@Nullable String description, Instant occurredAt) {
}
