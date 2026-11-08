package com.samdev.appointments.booking.application.command;

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.booking.domain.vo.Actor;
import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.customer.CustomerId;

/**
 * Customer or branch staff.
 *
 * @param start local date and time in the branch time zone
 * @param idempotencyKey a repeat with the same key and customer returns the first appointment
 */
public record BookAppointment(CustomerId customerId, ServiceId serviceId, LocalDateTime start,
		Actor actor, @Nullable String idempotencyKey) {}
