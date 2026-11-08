package com.samdev.appointments.branchmanagement;

import java.time.Instant;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record CapacityPoolBaseCapacityChanged(CapacityPoolId poolId, int baseCapacity, Instant occurredAt) {
}
