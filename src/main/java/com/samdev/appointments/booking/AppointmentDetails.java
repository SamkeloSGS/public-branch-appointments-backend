package com.samdev.appointments.booking;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.customer.CustomerId;

/**
 * One appointment as other modules see it. Local date and times are in the branch time zone.
 */
public record AppointmentDetails(AppointmentId appointmentId, String reference, CustomerId customerId,
		ServiceId serviceId, LocalDate date, LocalTime startTime, LocalTime endTime,
		Instant startsAt, Instant endsAt, AppointmentStatus status) {}
