package com.samdev.appointments.booking;

import java.time.Instant;
import java.time.LocalDate;

import org.jmolecules.event.annotation.DomainEvent;

import com.samdev.appointments.branchmanagement.CapacityPoolId;

/**
 * More reservations overlap some moment of the pool's day than its (non-zero) capacity. A branch manager
 * resolves it; the system never picks an appointment to move.
 */
@DomainEvent
public record PoolDayOverbooked(CapacityPoolId capacityPoolId, LocalDate date, Instant occurredAt) {}
