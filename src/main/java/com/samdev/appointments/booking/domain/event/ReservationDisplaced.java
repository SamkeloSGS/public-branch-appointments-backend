package com.samdev.appointments.booking.domain.event;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.jmolecules.event.annotation.DomainEvent;

import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.branchmanagement.CapacityPoolId;

/**
 * Internal: a capacity change left no capacity at some moment of this reservation, so the schedule removed it. A
 * listener in this module turns the appointment into {@code REBOOKING_REQUIRED}, one appointment per transaction.
 */
@DomainEvent
public record ReservationDisplaced(AppointmentId appointmentId, CapacityPoolId capacityPoolId, LocalDate date,
		LocalTime fromTime, LocalTime toTime, Instant occurredAt) {}
