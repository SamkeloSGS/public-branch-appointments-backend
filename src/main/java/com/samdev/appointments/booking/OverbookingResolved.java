package com.samdev.appointments.booking;

import java.time.Instant;
import java.time.LocalDate;

import org.jmolecules.event.annotation.DomainEvent;

import com.samdev.appointments.branchmanagement.CapacityPoolId;

/**
 * No moment of the pool's day is overbooked any more.
 */
@DomainEvent
public record OverbookingResolved(CapacityPoolId capacityPoolId, LocalDate date, Instant occurredAt) {}
