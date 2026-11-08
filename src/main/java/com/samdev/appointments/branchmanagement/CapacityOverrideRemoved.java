package com.samdev.appointments.branchmanagement;

import java.time.Instant;
import java.time.LocalDate;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record CapacityOverrideRemoved(CapacityOverrideId overrideId, CapacityPoolId poolId, LocalDate date, Instant occurredAt) {
}
