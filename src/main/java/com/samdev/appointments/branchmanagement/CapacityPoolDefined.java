package com.samdev.appointments.branchmanagement;

import java.time.Instant;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record CapacityPoolDefined(CapacityPoolId poolId, String name, int baseCapacity, Instant occurredAt) {
}
