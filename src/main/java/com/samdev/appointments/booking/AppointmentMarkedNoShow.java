package com.samdev.appointments.booking;

import java.time.Instant;

import org.jmolecules.event.annotation.DomainEvent;

import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.customer.CustomerId;

@DomainEvent
public record AppointmentMarkedNoShow(AppointmentId appointmentId, String reference, CustomerId customerId,
		ServiceId serviceId, Instant startsAt, Instant endsAt, Instant occurredAt) {}
