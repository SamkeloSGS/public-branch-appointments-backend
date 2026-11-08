package com.samdev.appointments.branchmanagement;

import java.time.Instant;
import java.time.LocalDate;

import org.jmolecules.event.annotation.DomainEvent;
import org.jspecify.annotations.Nullable;

@DomainEvent
public record CapacityOverrideSet(CapacityOverrideId overrideId, CapacityPoolId poolId, LocalDate date, @Nullable TimeRange window, int capacity, OverrideReason reason, Instant occurredAt) {
}
