package com.samdev.appointments.branchmanagement;

import java.time.Instant;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record ServiceOfferingChanged(ServiceOfferingId offeringId, ServiceId serviceId, CapacityPoolId poolId, int durationMinutes, int slotIntervalMinutes, int minLeadTimeMinutes, int maxAdvanceDays, Instant occurredAt) {
}
